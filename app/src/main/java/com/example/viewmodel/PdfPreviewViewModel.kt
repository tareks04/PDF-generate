package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PageLinkEntity
import com.example.di.AppContainer
import com.example.model.PdfPageSizeMode
import com.example.model.PdfWorkflowState
import com.example.model.SubjectCatalog
import com.example.pdf.PdfGenerator
import com.example.pdf.VerifiedPageFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PdfPreviewViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow<PdfWorkflowState>(PdfWorkflowState.Idle)
    val state: StateFlow<PdfWorkflowState> = _state.asStateFlow()

    private val _chapterDisplayTitle = MutableStateFlow("")
    val chapterDisplayTitle: StateFlow<String> = _chapterDisplayTitle.asStateFlow()

    private val _pageSizeMode = MutableStateFlow(PdfPageSizeMode.A4_FIT)
    val pageSizeMode: StateFlow<PdfPageSizeMode> = _pageSizeMode.asStateFlow()

    private var currentChapterId: String = ""
    private var currentLinks: List<PageLinkEntity> = emptyList()
    private val verifiedCache = mutableMapOf<Long, VerifiedPageFile>()
    private var outputFileName: String = "SmartPageLibrary.pdf"
    private var activeJob: Job? = null

    fun loadChapterAndBuildPdf(chapterId: String, isBangla: Boolean) {
        if (currentChapterId == chapterId && _state.value is PdfWorkflowState.Ready) {
            return
        }
        currentChapterId = chapterId
        verifiedCache.clear()
        activeJob?.cancel()

        activeJob = viewModelScope.launch {
            val chapter = container.libraryRepository.getChapterByIdSync(chapterId)
            val paper = chapter?.let { SubjectCatalog.paperById(it.paperId) }
            val links = container.libraryRepository.getLinksForChapterSync(chapterId)
                .sortedWith(compareBy({ it.pageNumber }, { it.id }))

            currentLinks = links

            if (chapter != null && paper != null) {
                val chLabel = chapter.customTitle?.takeIf { it.isNotBlank() }
                    ?: SubjectCatalog.defaultChapterName(chapter.chapterNumber, isBangla)
                _chapterDisplayTitle.value = "${paper.title(isBangla)} — $chLabel"
                outputFileName = PdfGenerator.buildSafePdfFilename(
                    paperTitleEn = paper.titleEn,
                    chapterNumber = chapter.chapterNumber,
                    customTitle = chapter.customTitle,
                )
            } else {
                _chapterDisplayTitle.value = chapterId
                outputFileName = "SmartPageLibrary.pdf"
            }

            if (links.isEmpty()) {
                _state.value = PdfWorkflowState.Error("NO_LINKS")
                return@launch
            }

            fetchAndBuild()
        }
    }

    fun retryFailedPages() {
        if (currentLinks.isEmpty()) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            fetchAndBuild()
        }
    }

    fun switchPageSizeMode(mode: PdfPageSizeMode) {
        if (_pageSizeMode.value == mode) return
        _pageSizeMode.value = mode
        if (verifiedCache.size == currentLinks.size && currentLinks.isNotEmpty()) {
            activeJob?.cancel()
            activeJob = viewModelScope.launch {
                buildPdfOnly()
            }
        }
    }

    private suspend fun fetchAndBuild() {
        val total = currentLinks.size
        _state.value = PdfWorkflowState.FetchingImages(
            completed = verifiedCache.size,
            total = total,
            currentPageLabel = currentLinks.firstOrNull()?.formattedPageNumber ?: "",
        )

        val batchResult = container.pdfGenerator.fetchAndVerifyAllPages(
            links = currentLinks,
            existingVerified = verifiedCache,
            onProgress = { done, totalCount, pageLabel ->
                _state.value = PdfWorkflowState.FetchingImages(
                    completed = done,
                    total = totalCount,
                    currentPageLabel = pageLabel,
                )
            },
        )

        batchResult.verifiedPages.forEach { verifiedCache[it.link.id] = it }

        if (batchResult.failures.isNotEmpty()) {
            _state.value = PdfWorkflowState.FetchFailed(
                retrievedCount = verifiedCache.size,
                totalCount = total,
                failures = batchResult.failures,
            )
            return
        }

        buildPdfOnly()
    }

    private suspend fun buildPdfOnly() {
        val orderedVerified = currentLinks.mapNotNull { verifiedCache[it.id] }
        val total = orderedVerified.size
        _state.value = PdfWorkflowState.GeneratingPdf(processedPages = 0, totalPages = total)

        try {
            val file = container.pdfGenerator.generatePdf(
                verifiedPages = orderedVerified,
                outputFileName = outputFileName,
                mode = _pageSizeMode.value,
                onProgress = { done, totalCount ->
                    _state.value = PdfWorkflowState.GeneratingPdf(
                        processedPages = done,
                        totalPages = totalCount,
                    )
                },
            )
            _state.value = PdfWorkflowState.Ready(
                pdfFilePath = file.absolutePath,
                pdfFileName = outputFileName,
                totalPages = total,
                pageSizeMode = _pageSizeMode.value,
            )
        } catch (e: Exception) {
            _state.value = PdfWorkflowState.Error(e.message ?: "PDF generation error")
        }
    }

    override fun onCleared() {
        activeJob?.cancel()
        container.clearPdfCache()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PdfPreviewViewModel(container) as T
        }
    }
}
