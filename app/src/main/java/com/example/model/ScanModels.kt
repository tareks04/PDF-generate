package com.example.model

enum class PageScanStatus {
    VALID,
    MISSING,
    BLOCKED,
    RATE_LIMITED,
    SERVER_ERROR,
    NETWORK_ERROR,
    TIMEOUT,
    INVALID_IMAGE,
}

enum class ScannerRunState {
    IDLE,
    SCANNING,
    PAUSED,
    COMPLETED,
    STOPPED,
}

enum class CandidateRole {
    PAGE,
    CHAPTER,
    BOOK,
}

data class PageNumberCandidate(
    val id: String,
    val rawDigits: String,
    val numericValue: Int,
    val padWidth: Int,
    val startIndex: Int,
    val endIndex: Int,
    val contextLabel: String,
    val score: Int,
    val role: CandidateRole = CandidateRole.PAGE,
)

data class UrlDetectionResult(
    val isValidUrl: Boolean,
    val normalizedUrl: String,
    val candidates: List<PageNumberCandidate>,
    val selectedCandidate: PageNumberCandidate?,
    val isAmbiguous: Boolean,
    val errorReason: DetectionErrorReason? = null,
    val chapterCandidate: PageNumberCandidate? = null,
    val bookCandidate: PageNumberCandidate? = null,
)

enum class DetectionErrorReason {
    EMPTY_URL,
    INVALID_URL_FORMAT,
    UNSAFE_OR_LOCAL_URL,
    NO_NUMERIC_PAGE_FOUND,
}

data class ScannedPageResult(
    val pageNumber: Int,
    val formattedPageNumber: String,
    val url: String,
    val status: PageScanStatus,
    val httpCode: Int? = null,
    val errorDetail: String? = null,
    val previewFilePath: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val byteSize: Long = 0L,
    val bookNumber: Int? = null,
    val chapterNumber: Int? = null,
)

enum class SaveMode {
    MERGE,
    REPLACE,
}
