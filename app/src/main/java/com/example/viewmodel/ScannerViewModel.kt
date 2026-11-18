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

data class ScannerUiState(
    val urlInput: String = "",
    val maxPagesInput: String = "",
    val startPageOverrideInput: String = "",
    val runState: ScannerRunState = ScannerRunState.IDLE,
    val results: List<ScannedPageResult> = emptyList(),
    val currentPageNumber: Int? = null,
    val detectionError: DetectionErrorReason? = null,
    val ambiguousCandidates: List<PageNumberCandidate> = emptyList(),
    val selectedCandidate: PageNumberCandidate? = null,
    val pauseReasonStatus: PageScanStatus? = null,
    val pauseReasonHttpCode: Int? = null,
    val pauseReasonDetail: String? = null,
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
    private var activeCandidate: PageNumberCandidate? = null
    private var nextPageToScan: Int = 1
    private var remainingQuota: Int = 200

    fun onUrlInputChange(value: String) {
        _uiState.update {
            it.copy(
                urlInput = value,
                detectionError = null,
                ambiguousCandidates = emptyList(),
                selectedCandidate = null,
            )
        }
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
                currentPageNumber = null,
                pauseReasonStatus = null,
                pauseReasonHttpCode = null,
                pauseReasonDetail = null,
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

        launchFreshScan(detection.normalizedUrl, candidate)
    }

    fun selectAmbiguousCandidateAndScan(candidate: PageNumberCandidate) {
        val currentUrl = _uiState.value.urlInput.trim()
        _uiState.update {
            it.copy(
                selectedCandidate = candidate,
                ambiguousCandidates = emptyList(),
                detectionError = null,
            )
        }
        launchFreshScan(currentUrl, candidate)
    }

    private fun launchFreshScan(baseUrl: String, candidate: PageNumberCandidate) {
        scanJob?.cancel()
        container.clearScanPreviewCache()

        val prefs = container.latestPreferences.value
        val maxPages = _uiState.value.maxPagesInput.toIntOrNull()?.coerceIn(1, 2000)
            ?: prefs.defaultMaxPages
        val startPage = _uiState.value.startPageOverrideInput.toIntOrNull()?.coerceAtLeast(0)
            ?: candidate.numericValue

        activeBaseUrl = baseUrl
        activeCandidate = candidate
        nextPageToScan = startPage
        remainingQuota = maxPages

        _uiState.update {
            it.copy(
                runState = ScannerRunState.SCANNING,
                results = emptyList(),
                currentPageNumber = startPage,
                pauseReasonStatus = null,
                pauseReasonHttpCode = null,
                pauseReasonDetail = null,
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
        val candidate = activeCandidate ?: return
        if (activeBaseUrl.isEmpty()) return
        if (remainingQuota <= 0) {
            _uiState.update { it.copy(runState = ScannerRunState.COMPLETED) }
            return
        }
        // If last item was a transient/blocking error, retry from that exact page
        val lastItem = _uiState.value.results.lastOrNull()
        if (lastItem != null && isPausableError(lastItem.status)) {
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
        val candidate = activeCandidate ?: return
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
                _uiState.update { it.copy(currentPageNumber = item.pageNumber) }

                val outcome = validator.validateAndFetchImage(
                    targetUrl = item.url,
                    cacheFileName = "scan_${item.pageNumber}.jpg",
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
        val candidate = activeCandidate ?: return
        val baseUrl = activeBaseUrl
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            val rateDelay = container.latestPreferences.value.rateLimitMs.toLong()

            while (isActive && remainingQuota > 0) {
                val pageNum = nextPageToScan
                val formatted = UrlPageDetector.formatPageNumber(pageNum, candidate.padWidth)
                val targetUrl = UrlPageDetector.buildUrlForPage(baseUrl, candidate, pageNum)

                _uiState.update { it.copy(currentPageNumber = pageNum) }

                var outcome = validator.validateAndFetchImage(
                    targetUrl = targetUrl,
                    cacheFileName = "scan_${pageNum}.jpg",
                )

                // CRITICAL STOPPING RULE: For HTTP 404 or 410, retry the same URL TWICE before confirming MISSING
                if (outcome.status == PageScanStatus.MISSING) {
                    for (retryAttempt in 1..2) {
                        if (!isActive) break
                        delay(350L)
                        val recheck = validator.validateAndFetchImage(
                            targetUrl = targetUrl,
                            cacheFileName = "scan_${pageNum}.jpg",
                        )
                        outcome = recheck
                        if (recheck.status != PageScanStatus.MISSING) {
                            break
                        }
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
                )

                _uiState.update { state ->
                    state.copy(results = state.results + resultItem)
                }

                if (outcome.status == PageScanStatus.MISSING) {
                    // Confirmed missing -> stop sequential scanning
                    _uiState.update {
                        it.copy(
                            runState = ScannerRunState.COMPLETED,
                            currentPageNumber = null,
                        )
                    }
                    return@launch
                }

                if (isPausableError(outcome.status)) {
                    // Pause on 403 / 429 / 5xx / timeout / network error without discarding valid links
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

    suspend fun saveValidLinksToChapter(chapterId: String, mode: SaveMode): SaveChapterResult {
        val valid = _uiState.value.validResults
        return container.libraryRepository.saveScannedPagesToChapter(
            chapterId = chapterId,
            validPages = valid,
            mode = mode,
        )
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
