package com.example.model

enum class PdfPageSizeMode {
    A4_FIT,
    ORIGINAL_SIZE,
}

data class PageFetchFailure(
    val pageNumber: Int,
    val formattedPage: String,
    val url: String,
    val reason: String,
)

sealed class PdfWorkflowState {
    data object Idle : PdfWorkflowState()
    data class FetchingImages(
        val completed: Int,
        val total: Int,
        val currentPageLabel: String,
    ) : PdfWorkflowState()

    data class FetchFailed(
        val retrievedCount: Int,
        val totalCount: Int,
        val failures: List<PageFetchFailure>,
    ) : PdfWorkflowState()

    data class GeneratingPdf(
        val processedPages: Int,
        val totalPages: Int,
    ) : PdfWorkflowState()

    data class Ready(
        val pdfFilePath: String,
        val pdfFileName: String,
        val totalPages: Int,
        val pageSizeMode: PdfPageSizeMode,
    ) : PdfWorkflowState()

    data class Error(
        val message: String,
    ) : PdfWorkflowState()
}
