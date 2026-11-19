package com.example.data.repository

import com.example.data.local.ChapterEntity
import com.example.data.local.ChapterWithStats
import com.example.data.local.LibraryDao
import com.example.data.local.PageLinkEntity
import com.example.data.local.SaveChapterResult
import com.example.model.SaveMode
import com.example.model.ScannedPageResult
import com.example.model.SubjectCatalog
import kotlinx.coroutines.flow.Flow

data class RestoreSummary(
    val chaptersAffected: Int,
    val linksImported: Int,
)

class LibraryRepository(private val dao: LibraryDao) {

    val allChaptersWithStats: Flow<List<ChapterWithStats>> = dao.observeAllChaptersWithStats()

    fun observeChapterWithStats(chapterId: String): Flow<ChapterWithStats?> =
        dao.observeChapterWithStats(chapterId)

    fun observeLinksForChapter(chapterId: String): Flow<List<PageLinkEntity>> =
        dao.observeLinksForChapter(chapterId)

    suspend fun getLinksForChapterSync(chapterId: String): List<PageLinkEntity> =
        dao.getLinksForChapterSync(chapterId)

    suspend fun getLinksForPaperSerialSync(paperId: String): List<PageLinkEntity> =
        dao.getLinksForPaperSerialSync(paperId)

    suspend fun getChapterByIdSync(chapterId: String): ChapterEntity? =
        dao.getChapterByIdSync(chapterId)

    suspend fun ensureSeeded() {
        val now = System.currentTimeMillis()
        val expectedChapters = buildList {
            for (paper in SubjectCatalog.papers) {
                for (chNum in 1..paper.chapterCount) {
                    add(
                        ChapterEntity(
                            chapterId = SubjectCatalog.chapterId(paper.id, chNum),
                            subjectGroupId = paper.group.id,
                            paperId = paper.id,
                            chapterNumber = chNum,
                            customTitle = null,
                            createdAt = now,
                            lastUpdatedAt = 0L,
                        )
                    )
                }
            }
        }
        dao.insertChaptersIgnore(expectedChapters)
    }

    suspend fun saveScannedPagesToChapter(
        chapterId: String,
        validPages: List<ScannedPageResult>,
        mode: SaveMode,
    ): SaveChapterResult {
        ensureSeeded()
        val now = System.currentTimeMillis()
        val entities = validPages
            .sortedBy { it.pageNumber }
            .map { page ->
                PageLinkEntity(
                    chapterId = chapterId,
                    pageNumber = page.pageNumber,
                    formattedPageNumber = page.formattedPageNumber,
                    url = page.url,
                    createdAt = now,
                    lastModifiedAt = now,
                )
            }
        return when (mode) {
            SaveMode.MERGE -> dao.mergeLinksIntoChapter(chapterId, entities)
            SaveMode.REPLACE -> {
                val count = dao.replaceLinksInChapter(chapterId, entities)
                SaveChapterResult(addedCount = count, duplicateCount = 0)
            }
        }
    }

    suspend fun renameChapter(chapterId: String, newTitle: String?) {
        val clean = newTitle?.trim()?.takeIf { it.isNotEmpty() }
        dao.updateChapterTitle(chapterId, clean)
    }

    suspend fun deleteSingleLink(chapterId: String, linkId: Long) {
        dao.removeLinksAndTouchChapter(chapterId, listOf(linkId))
    }

    suspend fun deleteSelectedLinks(chapterId: String, linkIds: List<Long>) {
        dao.removeLinksAndTouchChapter(chapterId, linkIds)
    }

    suspend fun clearChapterLinks(chapterId: String) {
        dao.clearChapterLinks(chapterId)
    }

    suspend fun exportBackupJson(): String {
        ensureSeeded()
        val chapters = dao.getAllChaptersSync()
        val allLinks = dao.getAllLinksSync().groupBy { it.chapterId }
        return BackupManager.serializeToJson(chapters, allLinks)
    }

    suspend fun importBackup(parsed: ParsedBackup, replaceExisting: Boolean): RestoreSummary {
        ensureSeeded()
        if (replaceExisting) {
            dao.deleteAllPageLinks()
            // Reset all custom titles and lastUpdatedAt before restoring
            val currentChapters = dao.getAllChaptersSync()
            dao.upsertChapters(
                currentChapters.map { it.copy(customTitle = null, lastUpdatedAt = 0L) }
            )
        }

        var chaptersAffected = 0
        var linksImported = 0

        for (backupChapter in parsed.chapters) {
            val existing = dao.getChapterByIdSync(backupChapter.chapterId)
            if (existing != null) {
                if (replaceExisting || (!backupChapter.customTitle.isNullOrBlank() && existing.customTitle.isNullOrBlank())) {
                    dao.updateChapterTitle(backupChapter.chapterId, backupChapter.customTitle)
                }
            }
            val entities = backupChapter.links.map { link ->
                PageLinkEntity(
                    chapterId = backupChapter.chapterId,
                    pageNumber = link.pageNumber,
                    formattedPageNumber = link.formattedPageNumber,
                    url = link.url,
                    createdAt = link.createdAt,
                    lastModifiedAt = link.lastModifiedAt,
                )
            }
            if (entities.isNotEmpty()) {
                val res = if (replaceExisting) {
                    val inserted = dao.replaceLinksInChapter(backupChapter.chapterId, entities)
                    SaveChapterResult(addedCount = inserted, duplicateCount = 0)
                } else {
                    dao.mergeLinksIntoChapter(backupChapter.chapterId, entities)
                }
                if (res.addedCount > 0) {
                    chaptersAffected++
                    linksImported += res.addedCount
                }
                if (backupChapter.lastUpdatedAt > 0L) {
                    dao.updateChapterLastUpdated(backupChapter.chapterId, backupChapter.lastUpdatedAt)
                }
            } else if (!backupChapter.customTitle.isNullOrBlank()) {
                chaptersAffected++
            }
        }
        return RestoreSummary(chaptersAffected = chaptersAffected, linksImported = linksImported)
    }
}
