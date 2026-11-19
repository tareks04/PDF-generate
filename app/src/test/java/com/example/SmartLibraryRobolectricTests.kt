package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.AppPreferences
import com.example.data.repository.BackupManager
import com.example.data.repository.LibraryRepository
import com.example.di.AppContainer
import com.example.model.CandidateRole
import com.example.model.DetectionErrorReason
import com.example.model.PageScanStatus
import com.example.model.PdfPageSizeMode
import com.example.model.SaveMode
import com.example.model.ScannedPageResult
import com.example.model.ScannerRunState
import com.example.model.SubjectCatalog
import com.example.pdf.PdfGenerator
import com.example.scanner.ImageValidator
import com.example.scanner.UrlPageDetector
import com.example.scanner.UrlSecurityValidator
import com.example.viewmodel.ScannerViewModel
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartLibraryRobolectricTests {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: LibraryRepository
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LibraryRepository(db.libraryDao())
        tempDir = File(context.cacheDir, "test_artifacts_${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    // --- 1. SCANNER URL DETECTION TESTS ---

    @Test
    fun `detect three digit page number and preserve zero padding`() {
        val result = UrlPageDetector.detect("https://example.com/book/page-001.jpg")
        assertTrue(result.isValidUrl)
        assertFalse(result.isAmbiguous)
        assertNotNull(result.selectedCandidate)
        val cand = result.selectedCandidate!!
        assertEquals(1, cand.numericValue)
        assertEquals(3, cand.padWidth)
        assertEquals(
            "https://example.com/book/page-002.jpg",
            UrlPageDetector.buildUrlForPage(result.normalizedUrl, cand, 2),
        )
        assertEquals(
            "https://example.com/book/page-042.jpg",
            UrlPageDetector.buildUrlForPage(result.normalizedUrl, cand, 42),
        )
    }

    @Test
    fun `detect one digit path segment page number`() {
        val result = UrlPageDetector.detect("https://example.com/image/1/")
        assertTrue(result.isValidUrl)
        assertFalse(result.isAmbiguous)
        val cand = result.selectedCandidate!!
        assertEquals(1, cand.numericValue)
        assertEquals(1, cand.padWidth)
        assertEquals(
            "https://example.com/image/5/",
            UrlPageDetector.buildUrlForPage(result.normalizedUrl, cand, 5),
        )
    }

    @Test
    fun `detect query string page parameter without touching book id`() {
        val result = UrlPageDetector.detect("https://example.com/view?book=404&page=001&fmt=jpg")
        assertTrue(result.isValidUrl)
        assertFalse(result.isAmbiguous)
        val cand = result.selectedCandidate!!
        assertEquals("001", cand.rawDigits)
        assertEquals(
            "https://example.com/view?book=404&page=002&fmt=jpg",
            UrlPageDetector.buildUrlForPage(result.normalizedUrl, cand, 2),
        )
    }

    @Test
    fun `multiple competing numbers in filename flags ambiguity`() {
        val result = UrlPageDetector.detect("https://example.com/book/12_34.jpg")
        assertTrue(result.isValidUrl)
        assertTrue(result.isAmbiguous)
        assertTrue(result.candidates.size >= 2)
    }

    @Test
    fun `detect compound chapter and page url ch1_p139 without touching book id or chapter number`() {
        val url = "https://ai-data.udvash-unmesh.com/bangla-ai/assets/book-479/pages/ch1_p139.jpg"
        val result = UrlPageDetector.detect(url)
        assertTrue(result.isValidUrl)
        assertFalse(result.isAmbiguous)
        val cand = result.selectedCandidate
        assertNotNull(cand)
        assertEquals("139", cand!!.rawDigits)
        assertEquals(139, cand.numericValue)
        assertEquals(98, cand.score)
        assertEquals(
            "https://ai-data.udvash-unmesh.com/bangla-ai/assets/book-479/pages/ch1_p140.jpg",
            UrlPageDetector.buildUrlForPage(result.normalizedUrl, cand, 140),
        )
    }

    @Test
    fun `detect book chapter and page roles on udvash style url and build cascade urls`() {
        val url = "https://ai-data.udvash-unmesh.com/bangla-ai/assets/book-478/pages/ch1_p1.jpg"
        val result = UrlPageDetector.detect(url)
        assertTrue(result.isValidUrl)
        assertFalse(result.isAmbiguous)

        val pageCand = result.selectedCandidate!!
        val chCand = result.chapterCandidate!!
        val bookCand = result.bookCandidate!!

        assertEquals(CandidateRole.PAGE, pageCand.role)
        assertEquals(1, pageCand.numericValue)
        assertEquals(CandidateRole.CHAPTER, chCand.role)
        assertEquals(1, chCand.numericValue)
        assertEquals(CandidateRole.BOOK, bookCand.role)
        assertEquals(478, bookCand.numericValue)

        assertEquals(
            "https://ai-data.udvash-unmesh.com/bangla-ai/assets/book-478/pages/ch2_p16.jpg",
            UrlPageDetector.buildUrl(
                baseUrl = result.normalizedUrl,
                pageCandidate = pageCand,
                pageNumber = 16,
                chapterCandidate = chCand,
                chapterNumber = 2,
                bookCandidate = bookCand,
                bookNumber = 478,
            ),
        )
        assertEquals(
            "https://ai-data.udvash-unmesh.com/bangla-ai/assets/book-479/pages/ch1_p1.jpg",
            UrlPageDetector.buildUrl(
                baseUrl = result.normalizedUrl,
                pageCandidate = pageCand,
                pageNumber = 1,
                chapterCandidate = chCand,
                chapterNumber = 1,
                bookCandidate = bookCand,
                bookNumber = 479,
            ),
        )
    }

    @Test
    fun `ssrf validator blocks localhost and private ip ranges by default`() {
        assertFalse(UrlSecurityValidator.isSafeHttpUrl("http://localhost/page-001.jpg", allowLocalServers = false))
        assertFalse(UrlSecurityValidator.isSafeHttpUrl("http://127.0.0.1/page-001.jpg", allowLocalServers = false))
        assertFalse(UrlSecurityValidator.isSafeHttpUrl("http://192.168.1.50/page-001.jpg", allowLocalServers = false))
        assertFalse(UrlSecurityValidator.isSafeHttpUrl("http://10.0.0.5/page-001.jpg", allowLocalServers = false))
        assertFalse(UrlSecurityValidator.isSafeHttpUrl("http://169.254.169.254/latest/meta-data", allowLocalServers = false))
        assertTrue(UrlSecurityValidator.isSafeHttpUrl("https://cdn.example.com/page-001.jpg", allowLocalServers = false))

        val blocked = UrlPageDetector.detect("http://127.0.0.1/page-001.jpg", allowLocalServers = false)
        assertFalse(blocked.isValidUrl)
        assertEquals(DetectionErrorReason.UNSAFE_OR_LOCAL_URL, blocked.errorReason)
    }

    // --- 2. HTTP & JPEG CONTENT VALIDATION TESTS ---

    @Test
    fun `image validator verifies real jpeg and rejects html 200 and classifies 403 429 404 503`() = runBlocking {
        val jpegBytes = createSampleJpegBytes(120, 160, Color.BLUE)
        val page3Hits = AtomicInteger(0)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/book/page-001.jpg") { ex ->
            ex.responseHeaders.add("Content-Type", "image/jpeg")
            ex.sendResponseHeaders(200, jpegBytes.size.toLong())
            ex.responseBody.use { it.write(jpegBytes) }
        }
        server.createContext("/book/page-002.jpg") { ex ->
            val html = "<html><body>Not Found</body></html>".toByteArray()
            ex.responseHeaders.add("Content-Type", "text/html")
            ex.sendResponseHeaders(200, html.size.toLong())
            ex.responseBody.use { it.write(html) }
        }
        server.createContext("/book/page-003.jpg") { ex ->
            page3Hits.incrementAndGet()
            ex.sendResponseHeaders(404, -1)
        }
        server.createContext("/book/page-403.jpg") { ex -> ex.sendResponseHeaders(403, -1) }
        server.createContext("/book/page-429.jpg") { ex -> ex.sendResponseHeaders(429, -1) }
        server.createContext("/book/page-503.jpg") { ex -> ex.sendResponseHeaders(503, -1) }
        server.start()

        try {
            val port = server.address.port
            val validator = ImageValidator(
                previewCacheDir = File(tempDir, "previews"),
                allowLocalServersProvider = { true },
            )

            val ok = validator.validateAndFetchImage("http://127.0.0.1:$port/book/page-001.jpg", "p1.jpg")
            assertEquals(PageScanStatus.VALID, ok.status)
            assertEquals(120, ok.width)
            assertEquals(160, ok.height)

            val html200 = validator.validateAndFetchImage("http://127.0.0.1:$port/book/page-002.jpg", "p2.jpg")
            assertEquals(PageScanStatus.INVALID_IMAGE, html200.status)

            val missing = validator.validateAndFetchImage("http://127.0.0.1:$port/book/page-003.jpg", "p3.jpg")
            assertEquals(PageScanStatus.MISSING, missing.status)
            assertEquals(1, page3Hits.get())

            assertEquals(
                PageScanStatus.BLOCKED,
                validator.validateAndFetchImage("http://127.0.0.1:$port/book/page-403.jpg", "p403.jpg").status,
            )
            assertEquals(
                PageScanStatus.RATE_LIMITED,
                validator.validateAndFetchImage("http://127.0.0.1:$port/book/page-429.jpg", "p429.jpg").status,
            )
            assertEquals(
                PageScanStatus.SERVER_ERROR,
                validator.validateAndFetchImage("http://127.0.0.1:$port/book/page-503.jpg", "p503.jpg").status,
            )
        } finally {
            server.stop(0)
        }
    }

    // --- 3. LIBRARY & BACKUP TESTS ---

    @Test
    fun `seeded catalog matches exact subject paper chapter counts and merge replace backup work`() = runBlocking {
        repository.ensureSeeded()
        val chapters = repository.allChaptersWithStats.first()
        // 11 + 11 + 5 + 5 + 12 + 12 = 56 chapters
        assertEquals(56, chapters.size)
        assertEquals(11, chapters.count { it.paperId == "physics_1" })
        assertEquals(11, chapters.count { it.paperId == "physics_2" })
        assertEquals(5, chapters.count { it.paperId == "chemistry_1" })
        assertEquals(5, chapters.count { it.paperId == "chemistry_2" })
        assertEquals(12, chapters.count { it.paperId == "botany" })
        assertEquals(12, chapters.count { it.paperId == "zoology" })

        val chId = SubjectCatalog.chapterId("physics_1", 1)
        val initialPages = listOf(
            ScannedPageResult(10, "010", "https://example.com/010.jpg", PageScanStatus.VALID),
            ScannedPageResult(2, "002", "https://example.com/002.jpg", PageScanStatus.VALID),
            ScannedPageResult(1, "001", "https://example.com/001.jpg", PageScanStatus.VALID),
        )
        val res1 = repository.saveScannedPagesToChapter(chId, initialPages, SaveMode.MERGE)
        assertEquals(3, res1.addedCount)

        // Numeric sort check (1, 2, 10)
        val savedLinks = repository.getLinksForChapterSync(chId)
        assertEquals(listOf(1, 2, 10), savedLinks.map { it.pageNumber })

        // Duplicate avoidance during merge
        val mergeMore = listOf(
            ScannedPageResult(2, "002", "https://example.com/002.jpg", PageScanStatus.VALID),
            ScannedPageResult(100, "100", "https://example.com/100.jpg", PageScanStatus.VALID),
        )
        val res2 = repository.saveScannedPagesToChapter(chId, mergeMore, SaveMode.MERGE)
        assertEquals(1, res2.addedCount)
        assertEquals(1, res2.duplicateCount)

        // Export JSON backup, clear chapter, restore from JSON
        val json = repository.exportBackupJson()
        val parsed = BackupManager.parseAndValidate(json).getOrThrow()
        repository.clearChapterLinks(chId)
        assertEquals(0, repository.getLinksForChapterSync(chId).size)

        val restored = repository.importBackup(parsed, replaceExisting = true)
        assertEquals(1, restored.chaptersAffected)
        assertEquals(4, restored.linksImported)
        assertEquals(listOf(1, 2, 10, 100), repository.getLinksForChapterSync(chId).map { it.pageNumber })
    }

    // --- 4. PDF GENERATOR & ASPECT RATIO TESTS ---

    @Test
    fun `pdf generator builds valid multi page pdf and fails explicitly if any page is missing`() = runBlocking {
        val jpeg1 = createSampleJpegBytes(800, 1200, Color.RED)
        val jpeg2 = createSampleJpegBytes(1200, 800, Color.GREEN)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/p/001.jpg") { ex ->
            ex.responseHeaders.add("Content-Type", "image/jpeg")
            ex.sendResponseHeaders(200, jpeg1.size.toLong())
            ex.responseBody.use { it.write(jpeg1) }
        }
        server.createContext("/p/002.jpg") { ex ->
            ex.responseHeaders.add("Content-Type", "image/jpeg")
            ex.sendResponseHeaders(200, jpeg2.size.toLong())
            ex.responseBody.use { it.write(jpeg2) }
        }
        server.createContext("/p/003.jpg") { ex ->
            ex.sendResponseHeaders(404, -1)
        }
        server.start()

        try {
            val port = server.address.port
            val chId = SubjectCatalog.chapterId("botany", 12)
            repository.ensureSeeded()
            repository.saveScannedPagesToChapter(
                chId,
                listOf(
                    ScannedPageResult(1, "001", "http://127.0.0.1:$port/p/001.jpg", PageScanStatus.VALID),
                    ScannedPageResult(2, "002", "http://127.0.0.1:$port/p/002.jpg", PageScanStatus.VALID),
                ),
                SaveMode.REPLACE,
            )

            val capturedSpecs = mutableListOf<com.example.pdf.PdfPageSpec>()
            val testPdfWriter = object : com.example.pdf.PdfWriter {
                override fun writePdf(
                    pages: List<com.example.pdf.PdfPageSpec>,
                    outputFile: File,
                    onProgress: (Int, Int) -> Unit,
                ) {
                    capturedSpecs.addAll(pages)
                    outputFile.writeText("%PDF-1.4\n%Test\n")
                    onProgress(pages.size, pages.size)
                }
            }

            val validator = ImageValidator(
                previewCacheDir = File(tempDir, "pdf_previews"),
                allowLocalServersProvider = { true },
            )
            val pdfGen = PdfGenerator(
                imageValidator = validator,
                pdfCacheDir = File(tempDir, "pdfs"),
                pageCacheDir = File(tempDir, "pdf_pages"),
                pdfWriter = testPdfWriter,
            )

            val links = repository.getLinksForChapterSync(chId)
            val batchOk = pdfGen.fetchAndVerifyAllPages(links, emptyMap()) { _, _, _ -> }
            assertEquals(2, batchOk.verifiedPages.size)
            assertTrue(batchOk.failures.isEmpty())

            val pdfFile = pdfGen.generatePdf(
                verifiedPages = batchOk.verifiedPages,
                outputFileName = PdfGenerator.buildSafePdfFilename("Botany", 12, null),
                mode = PdfPageSizeMode.A4_FIT,
            ) { _, _ -> }

            assertTrue(pdfFile.exists())
            assertEquals("Botany_Chapter_12.pdf", pdfFile.name)
            assertEquals(2, capturedSpecs.size)
            assertEquals(595, capturedSpecs[0].pageWidth)
            assertEquals(842, capturedSpecs[0].pageHeight)
            // Portrait 800x1200 (0.666) and Landscape 1200x800 (1.5) preserve exact aspect ratios without cropping
            assertEquals(800f / 1200f, capturedSpecs[0].destRect.width() / capturedSpecs[0].destRect.height(), 0.01f)
            assertEquals(1200f / 800f, capturedSpecs[1].destRect.width() / capturedSpecs[1].destRect.height(), 0.01f)

            // Failing page must be explicitly reported, never omitted
            repository.saveScannedPagesToChapter(
                chId,
                listOf(ScannedPageResult(3, "003", "http://127.0.0.1:$port/p/003.jpg", PageScanStatus.VALID)),
                SaveMode.MERGE,
            )
            val batchFail = pdfGen.fetchAndVerifyAllPages(
                repository.getLinksForChapterSync(chId),
                emptyMap(),
            ) { _, _, _ -> }
            assertEquals(1, batchFail.failures.size)
            assertEquals(3, batchFail.failures.first().pageNumber)
        } finally {
            server.stop(0)
        }
    }

    // --- 5. SINGLE-BOOK CHAPTER CASCADE (STOP AT END OF BOOK, NEVER ADVANCE BOOK+1) ---

    @Test
    fun `scanner viewmodel advances chapters inside one book and stops without touching book+1 even if book+1 exists`() {
        val jpegBytes = createSampleJpegBytes(100, 140, Color.DKGRAY)
        val validPaths = setOf(
            "/bangla-ai/assets/book-480/pages/ch1_p1.jpg",
            "/bangla-ai/assets/book-480/pages/ch1_p2.jpg",
            "/bangla-ai/assets/book-480/pages/ch1_p3.jpg",
            "/bangla-ai/assets/book-480/pages/ch2_p4.jpg",
            "/bangla-ai/assets/book-480/pages/ch2_p5.jpg",
            "/bangla-ai/assets/book-480/pages/ch3_p6.jpg",
            // book-481 exists on server, but scanner MUST stop at end of book-480 and never request book-481
            "/bangla-ai/assets/book-481/pages/ch1_p1.jpg",
        )
        val requestedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/bangla-ai/assets/") { ex ->
            val reqPath = ex.requestURI.path
            requestedPaths.add(reqPath)
            if (reqPath in validPaths) {
                ex.responseHeaders.add("Content-Type", "image/jpeg")
                ex.sendResponseHeaders(200, jpegBytes.size.toLong())
                ex.responseBody.use { it.write(jpegBytes) }
            } else {
                ex.sendResponseHeaders(404, -1)
            }
        }
        server.start()

        try {
            val port = server.address.port
            val container = AppContainer(context)
            container.latestPreferences.value = AppPreferences(
                allowLocalServers = true,
                rateLimitMs = 0,
                requestTimeoutSeconds = 5,
            )
            runBlocking { container.libraryRepository.ensureSeeded() }

            val vm = ScannerViewModel(container)
            vm.onUrlInputChange("http://127.0.0.1:$port/bangla-ai/assets/book-480/pages/ch1_p1.jpg")
            assertTrue(vm.uiState.value.detectedHasChapterOrBook)

            vm.startScan()
            val deadline = System.currentTimeMillis() + 15_000L
            while (vm.uiState.value.runState == ScannerRunState.SCANNING && System.currentTimeMillis() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(150, TimeUnit.MILLISECONDS)
                Thread.sleep(15)
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val finalState = vm.uiState.value
            assertEquals(
                "Expected COMPLETED, got ${finalState.runState} (pause=${finalState.pauseReasonStatus}, detail=${finalState.pauseReasonDetail})",
                ScannerRunState.COMPLETED,
                finalState.runState,
            )
            assertNull(finalState.stoppedAtInvalidBook)
            assertEquals(4, finalState.stoppedAtInvalidChapter)
            assertTrue("Must never request book-481", requestedPaths.none { it.contains("book-481") })

            val validTriples = finalState.validResults.map {
                Triple(it.bookNumber, it.chapterNumber, it.pageNumber)
            }
            assertEquals(
                listOf(
                    Triple(480, 1, 1),
                    Triple(480, 1, 2),
                    Triple(480, 1, 3),
                    Triple(480, 2, 4),
                    Triple(480, 2, 5),
                    Triple(480, 3, 6),
                ),
                validTriples,
            )

            // Verify auto-map saving book 480 chapters into chemistry_1
            runBlocking {
                val saveRes = vm.saveDiscoveredChaptersToPaper("chemistry_1", finalState.validResults, SaveMode.REPLACE)
                assertEquals(3, saveRes.chaptersUpdated)
                assertEquals(6, saveRes.totalAdded)
                assertEquals(
                    listOf(1, 2, 3),
                    container.libraryRepository.getLinksForChapterSync(SubjectCatalog.chapterId("chemistry_1", 1)).map { it.pageNumber },
                )
                assertEquals(
                    listOf(4, 5),
                    container.libraryRepository.getLinksForChapterSync(SubjectCatalog.chapterId("chemistry_1", 2)).map { it.pageNumber },
                )
                assertEquals(
                    listOf(6),
                    container.libraryRepository.getLinksForChapterSync(SubjectCatalog.chapterId("chemistry_1", 3)).map { it.pageNumber },
                )
            }
        } finally {
            server.stop(0)
        }
    }

    // --- 6. FULL-PAPER SERIAL CHAPTER-WISE COMBINED PDF TEST ---

    @Test
    fun `paper serial pdf combines all chapters top to bottom in exact chapter and page order`() = runBlocking {
        val jpegBytes = createSampleJpegBytes(200, 300, Color.MAGENTA)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/bot/") { ex ->
            ex.responseHeaders.add("Content-Type", "image/jpeg")
            ex.sendResponseHeaders(200, jpegBytes.size.toLong())
            ex.responseBody.use { it.write(jpegBytes) }
        }
        server.start()

        try {
            val port = server.address.port
            repository.ensureSeeded()
            // Intentionally insert Chapter 3 BEFORE Chapter 1 and Chapter 2 so SQL/PDF ordering must honor chapterNumber ASC
            repository.saveScannedPagesToChapter(
                SubjectCatalog.chapterId("botany", 3),
                listOf(
                    ScannedPageResult(1, "001", "http://127.0.0.1:$port/bot/c3_p1.jpg", PageScanStatus.VALID),
                    ScannedPageResult(2, "002", "http://127.0.0.1:$port/bot/c3_p2.jpg", PageScanStatus.VALID),
                ),
                SaveMode.REPLACE,
            )
            repository.saveScannedPagesToChapter(
                SubjectCatalog.chapterId("botany", 1),
                listOf(
                    ScannedPageResult(10, "010", "http://127.0.0.1:$port/bot/c1_p10.jpg", PageScanStatus.VALID),
                    ScannedPageResult(11, "011", "http://127.0.0.1:$port/bot/c1_p11.jpg", PageScanStatus.VALID),
                ),
                SaveMode.REPLACE,
            )
            repository.saveScannedPagesToChapter(
                SubjectCatalog.chapterId("botany", 2),
                listOf(
                    ScannedPageResult(5, "005", "http://127.0.0.1:$port/bot/c2_p5.jpg", PageScanStatus.VALID),
                ),
                SaveMode.REPLACE,
            )

            val serialLinks = repository.getLinksForPaperSerialSync("botany")
            assertEquals(
                listOf(
                    "botany_ch_1:10",
                    "botany_ch_1:11",
                    "botany_ch_2:5",
                    "botany_ch_3:1",
                    "botany_ch_3:2",
                ),
                serialLinks.map { "${it.chapterId}:${it.pageNumber}" },
            )

            val writtenOrder = mutableListOf<String>()
            val testPdfWriter = object : com.example.pdf.PdfWriter {
                override fun writePdf(
                    pages: List<com.example.pdf.PdfPageSpec>,
                    outputFile: File,
                    onProgress: (Int, Int) -> Unit,
                ) {
                    pages.forEach { writtenOrder.add(it.imageFile.name) }
                    outputFile.writeText("%PDF-1.4\n%BotanySerial\n")
                    onProgress(pages.size, pages.size)
                }
            }
            val validator = ImageValidator(
                previewCacheDir = File(tempDir, "bot_previews"),
                allowLocalServersProvider = { true },
            )
            val pdfGen = PdfGenerator(
                imageValidator = validator,
                pdfCacheDir = File(tempDir, "bot_pdfs"),
                pageCacheDir = File(tempDir, "bot_pages"),
                pdfWriter = testPdfWriter,
            )

            val batch = pdfGen.fetchAndVerifyAllPages(
                links = serialLinks,
                existingVerified = emptyMap(),
                preserveInputOrder = true,
            ) { _, _, _ -> }
            assertEquals(5, batch.verifiedPages.size)
            assertEquals(
                listOf(10, 11, 5, 1, 2),
                batch.verifiedPages.map { it.link.pageNumber },
            )

            val file = pdfGen.generatePdf(
                verifiedPages = batch.verifiedPages,
                outputFileName = PdfGenerator.buildSafePaperPdfFilename("Botany"),
                mode = PdfPageSizeMode.A4_FIT,
                preserveInputOrder = true,
            ) { _, _ -> }
            assertEquals("Botany_All_Chapters.pdf", file.name)
            assertEquals(5, writtenOrder.size)
            assertTrue(writtenOrder[0].contains("botany_ch_1") && writtenOrder[0].endsWith("_10.jpg"))
            assertTrue(writtenOrder[1].contains("botany_ch_1") && writtenOrder[1].endsWith("_11.jpg"))
            assertTrue(writtenOrder[2].contains("botany_ch_2") && writtenOrder[2].endsWith("_5.jpg"))
            assertTrue(writtenOrder[3].contains("botany_ch_3") && writtenOrder[3].endsWith("_1.jpg"))
            assertTrue(writtenOrder[4].contains("botany_ch_3") && writtenOrder[4].endsWith("_2.jpg"))
        } finally {
            server.stop(0)
        }
    }

    // --- 7. COMPOSE UI TESTS: BILINGUAL TOGGLE & LIBRARY SELECTION PERSISTENCE ---

    @Test
    fun `library preserves selected chemistry subject and paper after navigating to pdf preview and returning`() {
        composeRule.waitForIdle()
        val appContainer = (composeRule.activity.application as SmartLibraryApp).container
        runBlocking {
            appContainer.libraryRepository.ensureSeeded()
            appContainer.libraryRepository.saveScannedPagesToChapter(
                SubjectCatalog.chapterId("chemistry_2", 1),
                listOf(ScannedPageResult(1, "001", "https://example.com/chem2/001.jpg", PageScanStatus.VALID)),
                SaveMode.REPLACE,
            )
        }

        // Open Library tab
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("library_tab_chemistry").fetchSemanticsNodes().isNotEmpty()
        }

        // Select Chemistry -> Chemistry 2nd Paper
        composeRule.onNodeWithTag("library_tab_chemistry").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("library_paper_chemistry_2").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("library_paper_chemistry_2").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_paper_chemistry_2").assertIsSelected()

        // Open PDF / Print preview (full paper or chapter)
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("library_download_all_paper_pdf").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("library_download_all_paper_pdf").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("pdf_back_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Return to Library and assert Chemistry + Chemistry 2nd Paper remain selected!
        composeRule.onNodeWithTag("pdf_back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithTag("library_tab_chemistry").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("library_tab_chemistry").assertIsSelected()
        composeRule.onNodeWithTag("library_paper_chemistry_2").assertIsSelected()
    }

    @Test
    fun `ui defaults to bangla and toggles to english via B E button`() {
        composeRule.waitForIdle()
        // Default language is Bangla
        composeRule.onNodeWithText("স্মার্ট পেজ লাইব্রেরি").assertIsDisplayed()
        composeRule.onNodeWithTag("language_toggle_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("Smart Page Library").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Smart Page Library").assertIsDisplayed()
        // Toggle back to Bangla
        composeRule.onNodeWithTag("language_toggle_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodesWithText("স্মার্ট পেজ লাইব্রেরি").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("স্মার্ট পেজ লাইব্রেরি").assertIsDisplayed()
    }

    private fun createSampleJpegBytes(width: Int, height: Int, color: Int): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
