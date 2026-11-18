package com.example.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.example.data.local.PageLinkEntity
import com.example.model.PageFetchFailure
import com.example.model.PageScanStatus
import com.example.model.PdfPageSizeMode
import com.example.scanner.ImageValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

data class VerifiedPageFile(
    val link: PageLinkEntity,
    val file: File,
    val width: Int,
    val height: Int,
)

data class BatchFetchResult(
    val verifiedPages: List<VerifiedPageFile>,
    val failures: List<PageFetchFailure>,
)

interface PdfWriter {
    fun writePdf(
        pages: List<PdfPageSpec>,
        outputFile: File,
        onProgress: (processed: Int, total: Int) -> Unit,
    )
}

data class PdfPageSpec(
    val pageIndex: Int,
    val pageWidth: Int,
    val pageHeight: Int,
    val destRect: RectF,
    val imageFile: File,
    val sampleSize: Int,
)

class AndroidPdfDocumentWriter : PdfWriter {
    override fun writePdf(
        pages: List<PdfPageSpec>,
        outputFile: File,
        onProgress: (processed: Int, total: Int) -> Unit,
    ) {
        val document = PdfDocument()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val total = pages.size

        try {
            pages.forEachIndexed { index, spec ->
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = spec.sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeFile(spec.imageFile.absolutePath, decodeOpts)
                    ?: throw IllegalStateException("Unable to decode image for page ${index + 1}")
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(spec.pageWidth, spec.pageHeight, index + 1).create()
                    val pdfPage = document.startPage(pageInfo)
                    pdfPage.canvas.drawColor(Color.WHITE)
                    pdfPage.canvas.drawBitmap(bitmap, null, spec.destRect, paint)
                    document.finishPage(pdfPage)
                } finally {
                    bitmap.recycle()
                }
                onProgress(index + 1, total)
            }

            FileOutputStream(outputFile).use { fos ->
                document.writeTo(fos)
            }
        } finally {
            document.close()
        }
    }
}

class PdfGenerator(
    private val imageValidator: ImageValidator,
    private val pdfCacheDir: File,
    private val pageCacheDir: File,
    private val pdfWriter: PdfWriter = AndroidPdfDocumentWriter(),
) {
    companion object {
        const val A4_WIDTH_POINTS = 595
        const val A4_HEIGHT_POINTS = 842
        private const val MAX_BITMAP_DIM_PX = 2480

        fun buildSafePdfFilename(paperTitleEn: String, chapterNumber: Int, customTitle: String?): String {
            val base = if (!customTitle.isNullOrBlank()) {
                "${paperTitleEn}_${customTitle}"
            } else {
                "${paperTitleEn}_Chapter_$chapterNumber"
            }
            val sanitized = base
                .replace(Regex("""[^A-Za-z0-9._-]+"""), "_")
                .trim('_')
                .ifEmpty { "SmartPageLibrary_Chapter_$chapterNumber" }
            return "$sanitized.pdf"
        }

        fun calculateAspectFitRect(
            imageWidth: Int,
            imageHeight: Int,
            pageWidth: Float,
            pageHeight: Float,
        ): RectF {
            if (imageWidth <= 0 || imageHeight <= 0) {
                return RectF(0f, 0f, pageWidth, pageHeight)
            }
            val scale = minOf(pageWidth / imageWidth.toFloat(), pageHeight / imageHeight.toFloat())
            val drawW = imageWidth * scale
            val drawH = imageHeight * scale
            val left = (pageWidth - drawW) / 2f
            val top = (pageHeight - drawH) / 2f
            return RectF(left, top, left + drawW, top + drawH)
        }
    }

    suspend fun fetchAndVerifyAllPages(
        links: List<PageLinkEntity>,
        existingVerified: Map<Long, VerifiedPageFile>,
        onProgress: (completed: Int, total: Int, currentLabel: String) -> Unit,
    ): BatchFetchResult = withContext(Dispatchers.IO) {
        if (!pageCacheDir.exists()) pageCacheDir.mkdirs()

        val sortedLinks = links.sortedWith(compareBy({ it.pageNumber }, { it.id }))
        val total = sortedLinks.size
        val semaphore = Semaphore(3)
        val completedCounter = AtomicInteger(0)

        val results = coroutineScope {
            sortedLinks.map { link ->
                async {
                    val already = existingVerified[link.id]
                    if (already != null && already.file.exists() && already.file.length() > 0) {
                        val done = completedCounter.incrementAndGet()
                        onProgress(done, total, link.formattedPageNumber)
                        return@async Result.success(already)
                    }

                    semaphore.withPermit {
                        val cacheName = "pdf_src_${link.chapterId}_${link.id}_${link.pageNumber}.jpg"
                        val outcome = imageValidator.validateAndFetchImage(link.url, cacheName)
                        val done = completedCounter.incrementAndGet()
                        onProgress(done, total, link.formattedPageNumber)

                        if (outcome.status == PageScanStatus.VALID && outcome.previewFilePath != null) {
                            val srcFile = File(outcome.previewFilePath)
                            val destFile = File(pageCacheDir, cacheName)
                            if (srcFile.absolutePath != destFile.absolutePath) {
                                srcFile.copyTo(destFile, overwrite = true)
                            }
                            Result.success(
                                VerifiedPageFile(
                                    link = link,
                                    file = destFile,
                                    width = outcome.width,
                                    height = outcome.height,
                                )
                            )
                        } else {
                            val reason = outcome.errorDetail
                                ?: outcome.httpCode?.let { "HTTP $it (${outcome.status.name})" }
                                ?: outcome.status.name
                            Result.failure(
                                FetchPageException(
                                    PageFetchFailure(
                                        pageNumber = link.pageNumber,
                                        formattedPage = link.formattedPageNumber,
                                        url = link.url,
                                        reason = reason,
                                    )
                                )
                            )
                        }
                    }
                }
            }.awaitAll()
        }

        val verified = mutableListOf<VerifiedPageFile>()
        val failures = mutableListOf<PageFetchFailure>()
        for (res in results) {
            res.onSuccess { verified.add(it) }
            res.onFailure { err ->
                if (err is FetchPageException) {
                    failures.add(err.failure)
                }
            }
        }

        BatchFetchResult(
            verifiedPages = verified.sortedWith(compareBy({ it.link.pageNumber }, { it.link.id })),
            failures = failures.sortedBy { it.pageNumber },
        )
    }

    suspend fun generatePdf(
        verifiedPages: List<VerifiedPageFile>,
        outputFileName: String,
        mode: PdfPageSizeMode,
        onProgress: (processed: Int, total: Int) -> Unit,
    ): File = withContext(Dispatchers.Default) {
        if (!pdfCacheDir.exists()) pdfCacheDir.mkdirs()
        val outputFile = File(pdfCacheDir, outputFileName)

        val sorted = verifiedPages.sortedWith(compareBy({ it.link.pageNumber }, { it.link.id }))
        val specs = sorted.mapIndexed { index, page ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(page.file.absolutePath, bounds)
            val rawW = if (bounds.outWidth > 0) bounds.outWidth else page.width
            val rawH = if (bounds.outHeight > 0) bounds.outHeight else page.height
            require(rawW > 0 && rawH > 0) { "Unable to decode page ${page.link.formattedPageNumber}" }

            var sample = 1
            while ((rawW / sample) > MAX_BITMAP_DIM_PX || (rawH / sample) > MAX_BITMAP_DIM_PX) {
                sample *= 2
            }

            val pageW: Int
            val pageH: Int
            val destRect: RectF
            when (mode) {
                PdfPageSizeMode.A4_FIT -> {
                    pageW = A4_WIDTH_POINTS
                    pageH = A4_HEIGHT_POINTS
                    destRect = calculateAspectFitRect(rawW, rawH, pageW.toFloat(), pageH.toFloat())
                }
                PdfPageSizeMode.ORIGINAL_SIZE -> {
                    pageW = rawW
                    pageH = rawH
                    destRect = RectF(0f, 0f, pageW.toFloat(), pageH.toFloat())
                }
            }

            PdfPageSpec(
                pageIndex = index,
                pageWidth = pageW,
                pageHeight = pageH,
                destRect = destRect,
                imageFile = page.file,
                sampleSize = sample,
            )
        }

        pdfWriter.writePdf(specs, outputFile, onProgress)
        outputFile
    }

    private class FetchPageException(val failure: PageFetchFailure) : Exception(failure.reason)
}
