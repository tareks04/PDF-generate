package com.example.scanner

import com.example.model.DetectionErrorReason
import com.example.model.PageNumberCandidate
import com.example.model.UrlDetectionResult
import java.net.URI

object UrlPageDetector {

    private val numberRegex = Regex("""\d+""")
    private val imageExtRegex = Regex("""\.(jpe?g|png|webp|gif)$""", RegexOption.IGNORE_CASE)
    private val pageParamNames = setOf("page", "pg", "p", "page_no", "pageno", "pagenum", "page_number")

    fun detect(rawInput: String, allowLocalServers: Boolean = false): UrlDetectionResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return invalidResult(trimmed, DetectionErrorReason.EMPTY_URL)
        }
        val uri = runCatching { URI(trimmed) }.getOrNull()
        val scheme = uri?.scheme?.lowercase()
        val host = uri?.host
        if (uri == null || (scheme != "http" && scheme != "https") || host.isNullOrBlank()) {
            return invalidResult(trimmed, DetectionErrorReason.INVALID_URL_FORMAT)
        }
        if (!UrlSecurityValidator.isSafeHttpUrl(trimmed, allowLocalServers)) {
            return invalidResult(trimmed, DetectionErrorReason.UNSAFE_OR_LOCAL_URL)
        }

        val candidates = extractCandidates(trimmed)
        if (candidates.isEmpty()) {
            return UrlDetectionResult(
                isValidUrl = true,
                normalizedUrl = trimmed,
                candidates = emptyList(),
                selectedCandidate = null,
                isAmbiguous = false,
                errorReason = DetectionErrorReason.NO_NUMERIC_PAGE_FOUND,
            )
        }

        val sorted = candidates.sortedWith(
            compareByDescending<PageNumberCandidate> { it.score }
                .thenByDescending { it.startIndex }
        )

        val top = sorted.first()
        val second = sorted.getOrNull(1)
        val isAmbiguous = if (second == null) {
            false
        } else {
            // Ambiguous if two candidates are in the same high-priority context or close in score
            // (e.g., two numbers inside filename like ch02_005.jpg, or two non-page query params)
            top.score - second.score <= 15 && top.score < 95
        }

        return UrlDetectionResult(
            isValidUrl = true,
            normalizedUrl = trimmed,
            candidates = sorted,
            selectedCandidate = if (isAmbiguous) null else top,
            isAmbiguous = isAmbiguous,
            errorReason = null,
        )
    }

    fun buildUrlForPage(
        baseUrl: String,
        candidate: PageNumberCandidate,
        pageNumber: Int,
    ): String {
        val formatted = formatPageNumber(pageNumber, candidate.padWidth)
        return buildString {
            append(baseUrl.substring(0, candidate.startIndex))
            append(formatted)
            append(baseUrl.substring(candidate.endIndex))
        }
    }

    fun formatPageNumber(pageNumber: Int, padWidth: Int): String {
        return if (padWidth > 1) {
            pageNumber.toString().padStart(padWidth, '0')
        } else {
            pageNumber.toString()
        }
    }

    private fun extractCandidates(url: String): List<PageNumberCandidate> {
        val candidates = mutableListOf<PageNumberCandidate>()

        val schemeEnd = url.indexOf("://").let { if (it >= 0) it + 3 else 0 }
        val pathStart = url.indexOf('/', schemeEnd).takeIf { it >= 0 } ?: url.length
        val queryStart = url.indexOf('?', pathStart).takeIf { it >= 0 } ?: url.length
        val fragmentStart = url.indexOf('#', pathStart).takeIf { it >= 0 } ?: url.length
        val pathEnd = minOf(queryStart, fragmentStart)

        // 1. Scan path segments (excluding authority/host/port)
        if (pathStart < pathEnd) {
            val path = url.substring(pathStart, pathEnd)
            val segments = path.split('/')
            var cursor = pathStart
            // Identify the last non-empty segment index
            val lastNonEmptyIdx = segments.indexOfLast { it.isNotEmpty() }

            segments.forEachIndexed { idx, segment ->
                if (segment.isNotEmpty()) {
                    val isLastSegment = idx == lastNonEmptyIdx
                    val extMatch = imageExtRegex.find(segment)
                    val stemEnd = extMatch?.range?.first ?: segment.length
                    val stem = segment.substring(0, stemEnd)
                    val matchesInStem = numberRegex.findAll(stem).toList()

                    matchesInStem.forEachIndexed { matchIdx, match ->
                        val rawDigits = match.value
                        val value = rawDigits.toIntOrNull() ?: return@forEachIndexed
                        val absStart = cursor + match.range.first
                        val absEnd = cursor + match.range.last + 1
                        val padWidth = if (rawDigits.length > 1 && rawDigits.startsWith('0')) rawDigits.length else 1

                        val prefixLower = stem.substring(0, match.range.first).lowercase()
                        val hasExplicitPagePrefix =
                            Regex("""(?:^|[^a-z])(page|pg|p)[-_]?$""").containsMatchIn(prefixLower)

                        val hasChapterPrefix =
                            Regex("""(?:^|[^a-z])(ch|chap|chapter|book|vol|part|unit|sec|id)[-_]?$""")
                                .containsMatchIn(prefixLower)

                        val prevSegLower = if (idx > 0) segments[idx - 1].lowercase() else ""
                        val parentIsPageHint = prevSegLower in setOf("page", "pages", "image", "images", "img", "scan")

                        val score = when {
                            isLastSegment && hasExplicitPagePrefix -> 98
                            isLastSegment && stem == rawDigits && extMatch != null -> 90
                            isLastSegment && parentIsPageHint && stem == rawDigits -> 88
                            isLastSegment && hasChapterPrefix -> 40
                            isLastSegment && matchesInStem.size == 1 -> 72
                            isLastSegment -> {
                                // Multiple numbers in the final filename (e.g., ch02_005.jpg or 12_34.jpg)
                                // Give trailing number slight preference if not explicitly prefixed,
                                // but keep scores within ambiguity threshold (<= 15) when uncertain
                                if (matchIdx == matchesInStem.lastIndex) 70 else 62
                            }
                            hasExplicitPagePrefix -> 60
                            else -> 25
                        }

                        val contextLabel = "/$segment"
                        candidates.add(
                            PageNumberCandidate(
                                id = "path_${absStart}_${absEnd}",
                                rawDigits = rawDigits,
                                numericValue = value,
                                padWidth = padWidth,
                                startIndex = absStart,
                                endIndex = absEnd,
                                contextLabel = contextLabel,
                                score = score,
                            )
                        )
                    }
                }
                cursor += segment.length + 1 // +1 for '/'
            }
        }

        // 2. Scan query parameters
        if (queryStart < fragmentStart) {
            val query = url.substring(queryStart + 1, fragmentStart)
            var cursor = queryStart + 1
            val pairs = query.split('&')
            for (pair in pairs) {
                val eqIdx = pair.indexOf('=')
                if (eqIdx > 0) {
                    val key = pair.substring(0, eqIdx)
                    val valuePart = pair.substring(eqIdx + 1)
                    val keyLower = key.lowercase()
                    val numMatches = numberRegex.findAll(valuePart).toList()
                    for (match in numMatches) {
                        val rawDigits = match.value
                        val numVal = rawDigits.toIntOrNull() ?: continue
                        val absStart = cursor + eqIdx + 1 + match.range.first
                        val absEnd = cursor + eqIdx + 1 + match.range.last + 1
                        val padWidth = if (rawDigits.length > 1 && rawDigits.startsWith('0')) rawDigits.length else 1

                        val score = when {
                            keyLower in pageParamNames && valuePart == rawDigits -> 100
                            keyLower in pageParamNames -> 92
                            keyLower in setOf("book", "bookid", "book_id", "chapter", "ch", "cid", "bid", "id", "v", "ver") -> 25
                            else -> 68
                        }

                        candidates.add(
                            PageNumberCandidate(
                                id = "query_${absStart}_${absEnd}",
                                rawDigits = rawDigits,
                                numericValue = numVal,
                                padWidth = padWidth,
                                startIndex = absStart,
                                endIndex = absEnd,
                                contextLabel = "?$key=$valuePart",
                                score = score,
                            )
                        )
                    }
                }
                cursor += pair.length + 1 // +1 for '&'
            }
        }

        return candidates
    }

    private fun invalidResult(url: String, reason: DetectionErrorReason): UrlDetectionResult =
        UrlDetectionResult(
            isValidUrl = false,
            normalizedUrl = url,
            candidates = emptyList(),
            selectedCandidate = null,
            isAmbiguous = false,
            errorReason = reason,
        )
}
