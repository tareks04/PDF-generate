package com.example.scanner

import com.example.model.CandidateRole
import com.example.model.DetectionErrorReason
import com.example.model.PageNumberCandidate
import com.example.model.UrlDetectionResult
import java.net.URI

object UrlPageDetector {

    private val numberRegex = Regex("""\d+""")
    private val imageExtRegex = Regex("""\.(jpe?g|png|webp|gif)$""", RegexOption.IGNORE_CASE)
    private val pageParamNames = setOf("page", "pg", "p", "page_no", "pageno", "pagenum", "page_number")
    private val chapterParamNames = setOf("ch", "chap", "chapter", "chapter_no", "chapterno")
    private val bookParamNames = setOf("book", "book_id", "bookid", "bk", "vol")

    private val explicitPagePrefixRegex = Regex("""(?:^|[^a-z])(page|pg|p)[-_]?$""")
    private val explicitChapterPrefixRegex = Regex("""(?:^|[^a-z])(ch|chap|chapter)[-_]?$""")
    private val explicitBookPrefixRegex = Regex("""(?:^|[^a-z])(book|bk|vol)[-_]?$""")
    private val otherNonPagePrefixRegex = Regex("""(?:^|[^a-z])(part|unit|sec|id)[-_]?$""")

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
            top.score - second.score <= 15 && top.score < 95
        }

        val chosenPage = if (isAmbiguous) null else top
        val chapterCand = candidates
            .filter { it.role == CandidateRole.CHAPTER && it.id != chosenPage?.id }
            .maxByOrNull { it.startIndex }
        val bookCand = candidates
            .filter { it.role == CandidateRole.BOOK && it.id != chosenPage?.id && it.id != chapterCand?.id }
            .maxByOrNull { it.startIndex }

        return UrlDetectionResult(
            isValidUrl = true,
            normalizedUrl = trimmed,
            candidates = sorted,
            selectedCandidate = chosenPage,
            isAmbiguous = isAmbiguous,
            errorReason = null,
            chapterCandidate = chapterCand,
            bookCandidate = bookCand,
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

    fun buildUrl(
        baseUrl: String,
        pageCandidate: PageNumberCandidate,
        pageNumber: Int,
        chapterCandidate: PageNumberCandidate? = null,
        chapterNumber: Int? = null,
        bookCandidate: PageNumberCandidate? = null,
        bookNumber: Int? = null,
    ): String {
        val replacements = buildList {
            add(pageCandidate to formatPageNumber(pageNumber, pageCandidate.padWidth))
            if (chapterCandidate != null && chapterNumber != null) {
                add(chapterCandidate to formatPageNumber(chapterNumber, chapterCandidate.padWidth))
            }
            if (bookCandidate != null && bookNumber != null) {
                add(bookCandidate to formatPageNumber(bookNumber, bookCandidate.padWidth))
            }
        }.distinctBy { it.first.startIndex }
            .sortedByDescending { it.first.startIndex }

        var result = baseUrl
        for ((cand, replacement) in replacements) {
            result = result.substring(0, cand.startIndex) + replacement + result.substring(cand.endIndex)
        }
        return result
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
            val lastNonEmptyIdx = segments.indexOfLast { it.isNotEmpty() }

            segments.forEachIndexed { idx, segment ->
                if (segment.isNotEmpty()) {
                    val isLastSegment = idx == lastNonEmptyIdx
                    val extMatch = imageExtRegex.find(segment)
                    val stemEnd = extMatch?.range?.first ?: segment.length
                    val stem = segment.substring(0, stemEnd)
                    val matchesInStem = numberRegex.findAll(stem).toList()
                    val prevSegLower = if (idx > 0) segments[idx - 1].lowercase() else ""

                    matchesInStem.forEachIndexed { matchIdx, match ->
                        val rawDigits = match.value
                        val value = rawDigits.toIntOrNull() ?: return@forEachIndexed
                        val absStart = cursor + match.range.first
                        val absEnd = cursor + match.range.last + 1
                        val padWidth = if (rawDigits.length > 1 && rawDigits.startsWith('0')) rawDigits.length else 1

                        val prefixLower = stem.substring(0, match.range.first).lowercase()
                        val hasExplicitPagePrefix = explicitPagePrefixRegex.containsMatchIn(prefixLower)
                        val hasChapterPrefix = explicitChapterPrefixRegex.containsMatchIn(prefixLower) ||
                            (stem == rawDigits && prevSegLower in setOf("ch", "chap", "chapter"))
                        val hasBookPrefix = explicitBookPrefixRegex.containsMatchIn(prefixLower) ||
                            (stem == rawDigits && prevSegLower in setOf("book", "books", "vol"))
                        val hasOtherPrefix = otherNonPagePrefixRegex.containsMatchIn(prefixLower)

                        val parentIsPageHint = prevSegLower in setOf("page", "pages", "image", "images", "img", "scan")

                        val role = when {
                            hasExplicitPagePrefix -> CandidateRole.PAGE
                            hasChapterPrefix -> CandidateRole.CHAPTER
                            hasBookPrefix -> CandidateRole.BOOK
                            else -> CandidateRole.PAGE
                        }

                        val score = when {
                            isLastSegment && hasExplicitPagePrefix -> 98
                            isLastSegment && stem == rawDigits && extMatch != null -> 90
                            isLastSegment && parentIsPageHint && stem == rawDigits -> 88
                            isLastSegment && (hasChapterPrefix || hasBookPrefix || hasOtherPrefix) -> 40
                            isLastSegment && matchesInStem.size == 1 -> 72
                            isLastSegment -> {
                                if (matchIdx == matchesInStem.lastIndex) 70 else 62
                            }
                            hasExplicitPagePrefix -> 60
                            else -> 25
                        }

                        candidates.add(
                            PageNumberCandidate(
                                id = "path_${absStart}_${absEnd}",
                                rawDigits = rawDigits,
                                numericValue = value,
                                padWidth = padWidth,
                                startIndex = absStart,
                                endIndex = absEnd,
                                contextLabel = "/$segment",
                                score = score,
                                role = role,
                            )
                        )
                    }
                }
                cursor += segment.length + 1
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

                        val role = when (keyLower) {
                            in chapterParamNames -> CandidateRole.CHAPTER
                            in bookParamNames -> CandidateRole.BOOK
                            else -> CandidateRole.PAGE
                        }

                        val score = when {
                            keyLower in pageParamNames && valuePart == rawDigits -> 100
                            keyLower in pageParamNames -> 92
                            keyLower in chapterParamNames || keyLower in bookParamNames ||
                                keyLower in setOf("cid", "bid", "id", "v", "ver") -> 25
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
                                role = role,
                            )
                        )
                    }
                }
                cursor += pair.length + 1
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
