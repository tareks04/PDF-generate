package com.example.scanner

import android.graphics.BitmapFactory
import com.example.model.PageScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class ImageValidationOutcome(
    val status: PageScanStatus,
    val httpCode: Int? = null,
    val errorDetail: String? = null,
    val previewFilePath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val byteSize: Long = 0L,
)

class ImageValidator(
    private val previewCacheDir: File,
    private val timeoutSecondsProvider: () -> Int = { 12 },
    private val allowLocalServersProvider: () -> Boolean = { false },
    private val relayEndpointProvider: () -> String = { "" },
) {
    companion object {
        private const val MAX_IMAGE_BYTES = 25 * 1024 * 1024 // 25 MB safety limit
        private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())

        fun isValidJpegMagicBytes(bytes: ByteArray): Boolean {
            if (bytes.size < 3) return false
            return bytes[0] == JPEG_MAGIC[0] &&
                bytes[1] == JPEG_MAGIC[1] &&
                bytes[2] == JPEG_MAGIC[2]
        }
    }

    private fun buildClient(timeoutSeconds: Int, allowLocal: Boolean): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .dns(UrlSecurityValidator.SsrfSafeDns(allowLocal))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun validateAndFetchImage(
        targetUrl: String,
        cacheFileName: String,
    ): ImageValidationOutcome = withContext(Dispatchers.IO) {
        val allowLocal = allowLocalServersProvider()
        if (!UrlSecurityValidator.isSafeHttpUrl(targetUrl, allowLocal)) {
            return@withContext ImageValidationOutcome(
                status = PageScanStatus.BLOCKED,
                errorDetail = "URL blocked by security policy",
            )
        }

        val relayBase = relayEndpointProvider().trim()
        val effectiveUrl = if (relayBase.isNotEmpty()) {
            val baseHttp = relayBase.toHttpUrlOrNull()
            if (baseHttp == null || !UrlSecurityValidator.isSafeHttpUrl(relayBase, allowLocal)) {
                return@withContext ImageValidationOutcome(
                    status = PageScanStatus.NETWORK_ERROR,
                    errorDetail = "Invalid relay endpoint URL",
                )
            }
            baseHttp.newBuilder()
                .addQueryParameter("url", targetUrl)
                .build()
                .toString()
        } else {
            targetUrl
        }

        val client = buildClient(timeoutSecondsProvider(), allowLocal)
        val request = Request.Builder()
            .url(effectiveUrl)
            .get()
            .header("Accept", "image/jpeg,image/*;q=0.8")
            .header("User-Agent", "SmartPageLibrary/1.0 (Android)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val code = response.code

                if (code == 404 || code == 410) {
                    return@withContext ImageValidationOutcome(
                        status = PageScanStatus.MISSING,
                        httpCode = code,
                    )
                }
                if (code == 403 || code == 401) {
                    return@withContext ImageValidationOutcome(
                        status = PageScanStatus.BLOCKED,
                        httpCode = code,
                    )
                }
                if (code == 429) {
                    return@withContext ImageValidationOutcome(
                        status = PageScanStatus.RATE_LIMITED,
                        httpCode = code,
                    )
                }
                if (code in 500..599) {
                    return@withContext ImageValidationOutcome(
                        status = PageScanStatus.SERVER_ERROR,
                        httpCode = code,
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext ImageValidationOutcome(
                        status = PageScanStatus.NETWORK_ERROR,
                        httpCode = code,
                        errorDetail = "HTTP $code",
                    )
                }

                val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                if (contentType.contains("text/html") ||
                    contentType.contains("application/json") ||
                    contentType.contains("text/plain")
                ) {
                    return@withContext ImageValidationOutcome(
                        status = PageScanStatus.INVALID_IMAGE,
                        httpCode = code,
                        errorDetail = "Non-image Content-Type: $contentType",
                    )
                }

                val body = response.body ?: return@withContext ImageValidationOutcome(
                    status = PageScanStatus.INVALID_IMAGE,
                    httpCode = code,
                    errorDetail = "Empty response body",
                )

                val contentLength = body.contentLength()
                if (contentLength > MAX_IMAGE_BYTES) {
                    return@withContext ImageValidationOutcome(
                        status = PageScanStatus.INVALID_IMAGE,
                        httpCode = code,
                        errorDetail = "Image exceeds maximum allowed size",
                    )
                }

                val bytes = readBytesBounded(body.byteStream(), MAX_IMAGE_BYTES)
                    ?: return@withContext ImageValidationOutcome(
                        status = PageScanStatus.INVALID_IMAGE,
                        httpCode = code,
                        errorDetail = "Response stream exceeded max size",
                    )

                if (!isValidJpegMagicBytes(bytes)) {
                    return@withContext ImageValidationOutcome(
                        status = PageScanStatus.INVALID_IMAGE,
                        httpCode = code,
                        errorDetail = "Missing JPEG magic bytes (FF D8 FF)",
                    )
                }

                // Verify actual image dimensions & decodability
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
                if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                    return@withContext ImageValidationOutcome(
                        status = PageScanStatus.INVALID_IMAGE,
                        httpCode = code,
                        errorDetail = "Unreadable JPEG dimensions",
                    )
                }

                // Perform actual pixel decode check using safe downsampling to verify no corrupt bitstream
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, 512, 512)
                }
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                if (decoded == null) {
                    return@withContext ImageValidationOutcome(
                        status = PageScanStatus.INVALID_IMAGE,
                        httpCode = code,
                        errorDetail = "JPEG decode failed",
                    )
                }
                decoded.recycle()

                if (!previewCacheDir.exists()) {
                    previewCacheDir.mkdirs()
                }
                val outFile = File(previewCacheDir, cacheFileName)
                outFile.writeBytes(bytes)

                ImageValidationOutcome(
                    status = PageScanStatus.VALID,
                    httpCode = code,
                    previewFilePath = outFile.absolutePath,
                    width = boundsOpts.outWidth,
                    height = boundsOpts.outHeight,
                    byteSize = bytes.size.toLong(),
                )
            }
        } catch (e: SocketTimeoutException) {
            ImageValidationOutcome(
                status = PageScanStatus.TIMEOUT,
                errorDetail = e.message ?: "Timeout",
            )
        } catch (e: IOException) {
            ImageValidationOutcome(
                status = PageScanStatus.NETWORK_ERROR,
                errorDetail = e.message ?: "Connection error",
            )
        }
    }

    private fun readBytesBounded(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val buffer = ByteArray(8192)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
