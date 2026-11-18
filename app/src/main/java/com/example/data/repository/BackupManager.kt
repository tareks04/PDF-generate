package com.example.data.repository

import com.example.data.local.ChapterEntity
import com.example.data.local.PageLinkEntity
import com.example.model.SubjectCatalog
import org.json.JSONArray
import org.json.JSONObject

data class ParsedBackup(
    val exportedAt: Long,
    val chapters: List<BackupChapter>,
)

data class BackupChapter(
    val chapterId: String,
    val subjectGroupId: String,
    val paperId: String,
    val chapterNumber: Int,
    val customTitle: String?,
    val lastUpdatedAt: Long,
    val links: List<BackupLink>,
) {
    val totalLinks: Int get() = links.size
}

data class BackupLink(
    val pageNumber: Int,
    val formattedPageNumber: String,
    val url: String,
    val createdAt: Long,
    val lastModifiedAt: Long,
)

object BackupManager {
    private const val FORMAT_NAME = "smart_page_library_backup"
    private const val FORMAT_VERSION = 1

    fun serializeToJson(
        chapters: List<ChapterEntity>,
        linksByChapter: Map<String, List<PageLinkEntity>>,
        exportedAt: Long = System.currentTimeMillis(),
    ): String {
        val root = JSONObject()
        root.put("format", FORMAT_NAME)
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", exportedAt)

        val chaptersArray = JSONArray()
        for (chapter in chapters) {
            val links = linksByChapter[chapter.chapterId].orEmpty()
            // Include chapters that either have links or have a custom title
            if (links.isEmpty() && chapter.customTitle.isNullOrBlank()) continue

            val chObj = JSONObject()
            chObj.put("chapterId", chapter.chapterId)
            chObj.put("subjectGroupId", chapter.subjectGroupId)
            chObj.put("paperId", chapter.paperId)
            chObj.put("chapterNumber", chapter.chapterNumber)
            if (chapter.customTitle != null) {
                chObj.put("customTitle", chapter.customTitle)
            } else {
                chObj.put("customTitle", JSONObject.NULL)
            }
            chObj.put("lastUpdatedAt", chapter.lastUpdatedAt)

            val linksArray = JSONArray()
            for (link in links.sortedWith(compareBy({ it.pageNumber }, { it.id }))) {
                val linkObj = JSONObject()
                linkObj.put("pageNumber", link.pageNumber)
                linkObj.put("formattedPageNumber", link.formattedPageNumber)
                linkObj.put("url", link.url)
                linkObj.put("createdAt", link.createdAt)
                linkObj.put("lastModifiedAt", link.lastModifiedAt)
                linksArray.put(linkObj)
            }
            chObj.put("links", linksArray)
            chaptersArray.put(chObj)
        }
        root.put("chapters", chaptersArray)
        return root.toString(2)
    }

    fun parseAndValidate(jsonString: String): Result<ParsedBackup> {
        return runCatching {
            val trimmed = jsonString.trim()
            require(trimmed.startsWith("{")) { "JSON root must be an object" }

            val root = JSONObject(trimmed)
            val format = root.optString("format", "")
            require(format == FORMAT_NAME) { "Unsupported backup format" }

            val version = root.optInt("version", -1)
            require(version == FORMAT_VERSION) { "Unsupported backup version: $version" }

            val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())
            val chaptersArray = root.getJSONArray("chapters")

            val validPaperIds = SubjectCatalog.papers.associateBy { it.id }
            val parsedChapters = mutableListOf<BackupChapter>()

            for (i in 0 until chaptersArray.length()) {
                val chObj = chaptersArray.getJSONObject(i)
                val chapterId = chObj.getString("chapterId").trim()
                val paperId = chObj.getString("paperId").trim()
                val paperSpec = validPaperIds[paperId]
                    ?: throw IllegalArgumentException("Unknown paperId: $paperId")
                val chapterNumber = chObj.getInt("chapterNumber")
                require(chapterNumber in 1..paperSpec.chapterCount) {
                    "Invalid chapterNumber $chapterNumber for $paperId"
                }
                val expectedChapterId = SubjectCatalog.chapterId(paperId, chapterNumber)
                require(chapterId == expectedChapterId) {
                    "Mismatched chapterId $chapterId"
                }

                val customTitleRaw = if (chObj.isNull("customTitle")) null else chObj.optString("customTitle", "").trim()
                val customTitle = customTitleRaw?.takeIf { it.isNotEmpty() }
                val lastUpdatedAt = chObj.optLong("lastUpdatedAt", System.currentTimeMillis())

                val linksArray = chObj.getJSONArray("links")
                val parsedLinks = mutableListOf<BackupLink>()
                for (j in 0 until linksArray.length()) {
                    val lObj = linksArray.getJSONObject(j)
                    val url = lObj.getString("url").trim()
                    require(url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                        "Invalid link URL in backup: $url"
                    }
                    val pageNumber = lObj.getInt("pageNumber")
                    require(pageNumber >= 0) { "Invalid page number: $pageNumber" }
                    val formatted = lObj.optString("formattedPageNumber", pageNumber.toString())
                    val createdAt = lObj.optLong("createdAt", System.currentTimeMillis())
                    val modifiedAt = lObj.optLong("lastModifiedAt", createdAt)
                    parsedLinks.add(
                        BackupLink(
                            pageNumber = pageNumber,
                            formattedPageNumber = formatted,
                            url = url,
                            createdAt = createdAt,
                            lastModifiedAt = modifiedAt,
                        )
                    )
                }

                parsedChapters.add(
                    BackupChapter(
                        chapterId = chapterId,
                        subjectGroupId = paperSpec.group.id,
                        paperId = paperId,
                        chapterNumber = chapterNumber,
                        customTitle = customTitle,
                        lastUpdatedAt = lastUpdatedAt,
                        links = parsedLinks,
                    )
                )
            }

            ParsedBackup(exportedAt = exportedAt, chapters = parsedChapters)
        }
    }
}
