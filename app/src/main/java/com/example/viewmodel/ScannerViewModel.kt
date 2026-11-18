package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SaveChapterResult
import com.example.di.AppContainer
import com.example.model.DetectionErrorReason
import com.example.model.PageNumberCandidate
import com.example.model.PageScanStatus
import com.example.model.SaveMode
import com.example.model.ScannedPageResult
import com.example.model.ScannerRunState
import com.example.model.SubjectCatalog
import com.example.scanner.ImageValidator
import com.example.scanner.UrlPageDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AutoMapSaveResult(
    val chaptersUpdated: Int,
    val totalAdded: Int,
    val totalDuplicates: Int,
)

data class ScannerUiState(
    val urlInput: String = "",
    val maxPagesInput: String = "",
    val startPageOverrideInput: String = "",
    val autoAdvanceHierarchy: Boolean = true,
    val detectedHasChapterOrBook: Boolean = false,
    val runState: ScannerRunState = ScannerRunState.IDLE,
    val results: List<ScannedPageResult> = emptyList(),
    val currentBookNumber: Int? = null,
    val currentChapterNumber: Int? = null,
    val currentPageNumber: Int? = null,
    val detectionError: DetectionErrorReason? = null,
    val ambiguousCandidates: List<PageNumberCandidate> = emptyList(),
    val selectedCandidate: PageNumberCandidate? = null,
    val pauseReasonStatus: PageScanStatus? = null,
    val pauseReasonHttpCode: Int? = null,
    val pauseReasonDetail: String? = null,
    val stoppedAtInvalidBook: Int? = null,
    val stoppedAtInvalidChapter: Int? = null,
    val saveBannerMessage: String? = null,
) {
    val totalTested: Int get() = results.size
    val validResults: List<ScannedPageResult> get() = results.filter { it.status == PageScanStatus.VALID }
    val validCount: Int get() = validResults.size
    val missingCount: Int get() = results.count { it.status == PageScanStatus.MISSING }
    val hasFailedRetryable: Boolean
        get() = results.any {
            it.status == PageScanStatus.BLOCKED ||
                it.status == PageScanStatus.RATE_LIMITED ||
                it.status == PageScanStatus.SERVER_ERROR ||
                it.status == PageScanStatus.NETWORK_ERROR ||
                it.status == PageScanStatus.TIMEOUT
        }
}

class ScannerViewModel(
    private val container: AppContainer,
    private val validator: ImageValidator = container.imageValidator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var activeBaseUrl: String = ""
    private var activePageCandidate: PageNumberCandidate? = null
    private var activeChapterCandidate: PageNumberCandidate? = null
    private var activeBookCandidate: PageNumberCandidate? = null

    private var nextBookToScan: Int? = null
    private var nextChapterToScan: Int? = null
    private var nextPageToScan: Int = 1
    private var justRolledChapter: Boolean = false
    private var justRolledBook: Boolean = false
    private var remainingQuota: Int = 200

    fun onUrlInputChange(value: String) {
        val allowLocal = container.latestPreferences.value.allowLocalServers
        val quickDetect = if (value.isNotBlank()) {
            UrlPageDetector.detect(value, allowLocalServers = allowLocal)
        } else {
            null
        }
        val hasHierarchy = quickDetect?.chapterCandidate != null || quickDetect?.bookCandidate != null
        _uiState.update {
            it.copy(
                urlInput = value,
                detectedHasChapterOrBook = hasHierarchy,
                detectionError = null,
                ambiguousCandidates = emptyList(),
                selectedCandidate = null,
            )
        }
    }

    fun onAutoAdvanceChange(enabled: Boolean) {
        _uiState.update { it.copy(autoAdvanceHierarchy = enabled) }
    }

    fun onMaxPagesChange(value: String) {
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { it.copy(maxPagesInput = value) }
        }
    }

    fun onStartPageOverrideChange(value: String) {
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { it.copy(startPageOverrideInput = value) }
        }
    }

    fun clearInputsAndResults() {
        scanJob?.cancel()
        scanJob = null
        container.clearScanPreviewCache()
        _uiState.value = ScannerUiState()
    }

    fun clearResultsOnly() {
        scanJob?.cancel()
        scanJob = null
        container.clearScanPreviewCache()
        _uiState.update {
            it.copy(
                runState = ScannerRunState.IDLE,
                results = emptyList(),
                currentBookNumber = null,
                currentChapterNumber = null,
                currentPageNumber = null,
                pauseReasonStatus = null,
                pauseReasonHttpCode = null,
                pauseReasonDetail = null,
                stoppedAtInvalidBook = null,
                stoppedAtInvalidChapter = null,
                saveBannerMessage = null,
            )
        }
    }

    fun startScan() {
        val state = _uiState.value
        val allowLocal = container.latestPreferences.value.allowLocalServers
        val detection = UrlPageDetector.detect(state.urlInput, allowLocalServers = allowLocal)

        if (!detection.isValidUrl || detection.errorReason != null) {
            _uiState.update {
                it.copy(
                    detectionError = detection.errorReason,
                    ambiguousCandidates = emptyList(),
                )
            }
            return
        }

        if (detection.isAmbiguous && state.selectedCandidate == null) {
            _uiState.update {
                it.copy(
                    ambiguousCandidates = detection.candidates,
                    detectionError = null,
                )
            }
            return
        }

        val candidate = state.selectedCandidate ?: detection.selectedCandidate
        if (candidate == null) {
            _uiState.update { it.copy(detectionError = DetectionErrorReason.NO_NUMERIC_PAGE_FOUND) }
            return
        }

        val chapterCand = detection.chapterCandidate?.takeIf { it.id != candidate.id }
        val bookCand = detection.bookCandidate?.takeIf { it.id != candidate.id && it.id != chapterCand?.id }

        launchFreshScan(
            baseUrl = detection.normalizedUrl,
            pageCandidate = candidate,
            chapterCandidate = chapterCand,
            bookCandidate = bookCand,
        )
    }

    fun selectAmbiguousCandidateAndScan(candidate: PageNumberCandidate) {
        val allowLocal = container.latestPreferences.value.allowLocalServers
        val currentUrl = _uiState.value.urlInput.trim()
        val detection = UrlPageDetector.detect(currentUrl, allowLocalServers = allowLocal)
        val chapterCand = detection.chapterCandidate?.takeIf { it.id != candidate.id }
        val bookCand = detection.bookCandidate?.takeIf { it.id != candidate.id && it.id != chapterCand?.id }

        _uiState.update {
            it.copy(
                selectedCandidate = candidate,
                ambiguousCandidates = emptyList(),
                detectionError = null,
                detectedHasChapterOrBook = chapterCand != null || bookCand != null,
            )
        }
        launchFreshScan(currentUrl, candidate, chapterCand, bookCand)
    }

    private fun launchFreshScan(
        baseUrl: String,
        pageCandidate: PageNumberCandidate,
        chapterCandidate: PageNumberCandidate?,
        bookCandidate: PageNumberCandidate?,
    ) {
        scanJob?.cancel()
        container.clearScanPreviewCache()

        val state = _uiState.value
        val prefs = container.latestPreferences.value
        val isCascade = state.autoAdvanceHierarchy && (chapterCandidate != null || bookCandidate != null)
        val maxPages = state.maxPagesInput.toIntOrNull()?.coerceIn(1, 5000)
            ?: if (isCascade) 2000 else prefs.defaultMaxPages
        val startPage = state.startPageOverrideInput.toIntOrNull()?.coerceAtLeast(0)
            ?: pageCandidate.numericValue

        activeBaseUrl = baseUrl
        activePageCandidate = pageCandidate
        activeChapterCandidate = chapterCandidate
        activeBookCandidate = bookCandidate

        nextBookToScan = bookCandidate?.numericValue
        nextChapterToScan = chapterCandidate?.numericValue
        nextPageToScan = startPage
        justRolledChapter = false
        justRolledBook = false
        remainingQuota = maxPages

        _uiState.update {
            it.copy(
                runState = ScannerRunState.SCANNING,
                results = emptyList(),
                detectedHasChapterOrBook = chapterCandidate != null || bookCandidate != null,
                currentBookNumber = nextBookToScan,
                currentChapterNumber = nextChapterToScan,
                currentPageNumber = startPage,
                pauseReasonStatus = null,
                pauseReasonHttpCode = null,
                pauseReasonDetail = null,
                stoppedAtInvalidBook = null,
                stoppedAtInvalidChapter = null,
                saveBannerMessage = null,
            )
        }
        runScanLoop()
    }

    fun pauseScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update {
            if (it.runState == ScannerRunState.SCANNING) {
                it.copy(runState = ScannerRunState.PAUSED)
            } else {
                it
            }
        }
    }

    fun resumeScan() {
        if (activePageCandidate == null || activeBaseUrl.isEmpty()) return
        if (remainingQuota <= 0) {
            _uiState.update { it.copy(runState = ScannerRunState.COMPLETED) }
            return
        }
        val lastItem = _uiState.value.results.lastOrNull()
        if (lastItem != null && isPausableError(lastItem.status)) {
            nextBookToScan = lastItem.bookNumber
            nextChapterToScan = lastItem.chapterNumber
            nextPageToScan = lastItem.pageNumber
            _uiState.update {
                it.copy(
                    results = it.results.dropLast(1),
                    runState = ScannerRunState.SCANNING,
                    pauseReasonStatus = null,
                    pauseReasonHttpCode = null,
                    pauseReasonDetail = null,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    runState = ScannerRunState.SCANNING,
                    pauseReasonStatus = null,
                    pauseReasonHttpCode = null,
                    pauseReasonDetail = null,
                )
            }
        }
        runScanLoop()
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update {
            it.copy(
                runState = ScannerRunState.STOPPED,
                currentPageNumber = null,
            )
        }
    }

    fun retryFailedUrls() {
        if (activePageCandidate == null) return
        val currentResults = _uiState.value.results
        val retryableIndices = currentResults.mapIndexedNotNull { idx, item ->
            if (isPausableError(item.status) || item.status == PageScanStatus.INVALID_IMAGE) idx else null
        }
        if (retryableIndices.isEmpty()) return

        scanJob?.cancel()
        _uiState.update {
            it.copy(
                runState = ScannerRunState.SCANNING,
                pauseReasonStatus = null,
                pauseReasonHttpCode = null,
                pauseReasonDetail = null,
            )
        }

        scanJob = viewModelScope.launch {
            val rateDelay = container.latestPreferences.value.rateLimitMs.toLong()
            for (idx in retryableIndices) {
                if (!isActive) break
                val item = _uiState.value.results.getOrNull(idx) ?: continue
                _uiState.update {
                    it.copy(
                        currentBookNumber = item.bookNumber,
                        currentChapterNumber = item.chapterNumber,
                        currentPageNumber = item.pageNumber,
                    )
                }

                val outcome = validator.validateAndFetchImage(
                    targetUrl = item.url,
                    cacheFileName = cacheNameFor(item.bookNumber, item.chapterNumber, item.pageNumber),
                )
                val updated = item.copy(
                    status = outcome.status,
                    httpCode = outcome.httpCode,
                    errorDetail = outcome.errorDetail,
                    previewFilePath = outcome.previewFilePath,
                    width = outcome.width,
                    height = outcome.height,
                    byteSize = outcome.byteSize,
                )
                _uiState.update { state ->
                    val mutable = state.results.toMutableList()
                    if (idx < mutable.size) mutable[idx] = updated
                    state.copy(results = mutable)
                }
                if (rateDelay > 0) delay(rateDelay)
            }
            _uiState.update {
                it.copy(
                    runState = if (remainingQuota > 0 && it.results.lastOrNull()?.status != PageScanStatus.MISSING) {
                        ScannerRunState.PAUSED
                    } else {
                        ScannerRunState.COMPLETED
                    },
                    currentPageNumber = null,
                )
            }
        }
    }

    private fun runScanLoop() {
        val pageCandidate = activePageCandidate ?: return
        val chapterCandidate = activeChapterCandidate
        val bookCandidate = activeBookCandidate
        val baseUrl = activeBaseUrl

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val rateDelay = container.latestPreferences.value.rateLimitMs.toLong()

            while (isActive && remainingQuota > 0) {
                val bookNum = nextBookToScan
                val chNum = nextChapterToScan
                val pageNum = nextPageToScan
                val formatted = UrlPageDetector.formatPageNumber(pageNum, pageCandidate.padWidth)
                val targetUrl = UrlPageDetector.buildUrl(
                    baseUrl = baseUrl,
                    pageCandidate = pageCandidate,
                    pageNumber = pageNum,
                    chapterCandidate = chapterCandidate,
                    chapterNumber = chNum,
                    bookCandidate = bookCandidate,
                    bookNumber = bookNum,
                )

                _uiState.update {
                    it.copy(
                        currentBookNumber = bookNum,
                        currentChapterNumber = chNum,
                        currentPageNumber = pageNum,
                    )
                }

                var outcome = validator.validateAndFetchImage(
                    targetUrl = targetUrl,
                    cacheFileName = cacheNameFor(bookNum, chNum, pageNum),
                )

                // Retry HTTP 404 / 410 twice before confirming MISSING
                if (outcome.status == PageScanStatus.MISSING) {
                    for (retryAttempt in 1..2) {
                        if (!isActive) break
                        delay(300L)
                        val recheck = validator.validateAndFetchImage(
                            targetUrl = targetUrl,
                            cacheFileName = cacheNameFor(bookNum, chNum, pageNum),
                        )
                        outcome = recheck
                        if (recheck.status != PageScanStatus.MISSING) break
                    }
                }

                if (!isActive) break

                val resultItem = ScannedPageResult(
                    pageNumber = pageNum,
                    formattedPageNumber = formatted,
                    url = targetUrl,
                    status = outcome.status,
                    httpCode = outcome.httpCode,
                    errorDetail = outcome.errorDetail,
                    previewFilePath = outcome.previewFilePath,
                    width = outcome.width,
                    height = outcome.height,
                    byteSize = outcome.byteSize,
                    bookNumber = bookNum,
                    chapterNumber = chNum,
                )

                _uiState.update { state ->
                    state.copy(results = state.results + resultItem)
                }

                if (isPausableError(outcome.status)) {
                    _uiState.update {
                        it.copy(
                            runState = ScannerRunState.PAUSED,
                            pauseReasonStatus = outcome.status,
                            pauseReasonHttpCode = outcome.httpCode,
                            pauseReasonDetail = outcome.errorDetail,
                            currentPageNumber = pageNum,
                        )
                    }
                    return@launch
                }

                val cascadeEnabled = _uiState.value.autoAdvanceHierarchy

                if (outcome.status == PageScanStatus.MISSING) {
                    if (!cascadeEnabled || (chapterCandidate == null && bookCandidate == null)) {
                        // Single-chapter mode: stop on first confirmed missing page
                        _uiState.update {
                            it.copy(
                                runState = ScannerRunState.COMPLETED,
                                currentPageNumber = null,
                            )
                        }
                        return@launch
                    }

                    // Cascade Mode:
                    // 1) If we were scanning pages inside a chapter, roll ch -> ch + 1 starting at the SAME missing page
                    if (chapterCandidate != null && !justRolledChapter && !justRolledBook) {
                        nextChapterToScan = (chNum ?: 1) + 1
                        // Keep nextPageToScan unchanged (the missing page number!)
                        justRolledChapter = true
                        justRolledBook = false
                        remainingQuota--
                        if (rateDelay > 0) delay(rateDelay)
                        continue
                    }

                    // 2) If ch+1 was also missing on that page (justRolledChapter == true), OR no chapter token exists:
                    //    roll book -> book + 1, reset ch = 1, p = 1
                    if (bookCandidate != null && !justRolledBook) {
                        nextBookToScan = (bookNum ?: 1) + 1
                        if (chapterCandidate != null) {
                            nextChapterToScan = 1
                        }
                        nextPageToScan = 1
                        justRolledBook = true
                        justRolledChapter = false
                        remainingQuota--
                        if (rateDelay > 0) delay(rateDelay)
                        continue
                    }

                    // 3) If justRolledBook == true and book+1 failed on ch1_p1 -> invalid book confirmed! Stop cleanly.
                    _uiState.update {
                        it.copy(
                            runState = ScannerRunState.COMPLETED,
                            currentPageNumber = null,
                            stoppedAtInvalidBook = if (bookCandidate != null) bookNum else null,
                            stoppedAtInvalidChapter = if (bookCandidate == null) chNum else null,
                        )
                    }
                    return@launch
                }

                // VALID (or non-missing invalid image): reset rollover flags and continue next page
                justRolledChapter = false
                justRolledBook = false
                nextPageToScan++
                remainingQuota--

                if (remainingQuota <= 0) {
                    _uiState.update {
                        it.copy(
                            runState = ScannerRunState.COMPLETED,
                            currentPageNumber = null,
                        )
                    }
                    return@launch
                }

                if (rateDelay > 0) {
                    delay(rateDelay)
                }
            }
        }
    }

    private fun cacheNameFor(bookNum: Int?, chNum: Int?, pageNum: Int): String =
        "scan_b${bookNum ?: 0}_c${chNum ?: 0}_p${pageNum}.jpg"

    suspend fun saveValidLinksToChapter(
        chapterId: String,
        pages: List<ScannedPageResult>,
        mode: SaveMode,
    ): SaveChapterResult {
        return container.libraryRepository.saveScannedPagesToChapter(
            chapterId = chapterId,
            validPages = pages,
            mode = mode,
        )
    }

    suspend fun saveDiscoveredChaptersToPaper(
        paperId: String,
        validPages: List<ScannedPageResult>,
        mode: SaveMode,
    ): AutoMapSaveResult {
        val paper = SubjectCatalog.paperById(paperId)
            ?: return AutoMapSaveResult(0, 0, 0)

        val grouped = validPages
            .filter { it.chapterNumber != null && it.chapterNumber in 1..paper.chapterCount }
            .groupBy { it.chapterNumber!! }

        var chaptersUpdated = 0
        var totalAdded = 0
        var totalDuplicates = 0

        for ((chNum, pages) in grouped.toSortedMap()) {
            val chId = SubjectCatalog.chapterId(paperId, chNum)
            val res = container.libraryRepository.saveScannedPagesToChapter(
                chapterId = chId,
                validPages = pages,
                mode = mode,
            )
            chaptersUpdated++
            totalAdded += res.addedCount
            totalDuplicates += res.duplicateCount
        }
        return AutoMapSaveResult(chaptersUpdated, totalAdded, totalDuplicates)
    }

    fun dismissSaveBanner() {
        _uiState.update { it.copy(saveBannerMessage = null) }
    }

    fun setSaveBanner(message: String) {
        _uiState.update { it.copy(saveBannerMessage = message) }
    }

    private fun isPausableError(status: PageScanStatus): Boolean = when (status) {
        PageScanStatus.BLOCKED,
        PageScanStatus.RATE_LIMITED,
        PageScanStatus.SERVER_ERROR,
        PageScanStatus.NETWORK_ERROR,
        PageScanStatus.TIMEOUT -> true
        PageScanStatus.VALID,
        PageScanStatus.MISSING,
        PageScanStatus.INVALID_IMAGE -> false
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScannerViewModel(container) as T
        }
    }
}
