package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class SaveChapterResult(
    val addedCount: Int,
    val duplicateCount: Int,
)

@Dao
interface LibraryDao {
    @Query(
        """
        SELECT c.chapterId, c.subjectGroupId, c.paperId, c.chapterNumber, c.customTitle,
               c.createdAt, c.lastUpdatedAt,
               COUNT(p.id) AS pageCount,
               MIN(p.pageNumber) AS minPage,
               MAX(p.pageNumber) AS maxPage
        FROM chapters c
        LEFT JOIN page_links p ON c.chapterId = p.chapterId
        GROUP BY c.chapterId
        ORDER BY c.paperId ASC, c.chapterNumber ASC
        """
    )
    fun observeAllChaptersWithStats(): Flow<List<ChapterWithStats>>

    @Query(
        """
        SELECT c.chapterId, c.subjectGroupId, c.paperId, c.chapterNumber, c.customTitle,
               c.createdAt, c.lastUpdatedAt,
               COUNT(p.id) AS pageCount,
               MIN(p.pageNumber) AS minPage,
               MAX(p.pageNumber) AS maxPage
        FROM chapters c
        LEFT JOIN page_links p ON c.chapterId = p.chapterId
        WHERE c.chapterId = :chapterId
        GROUP BY c.chapterId
        """
    )
    fun observeChapterWithStats(chapterId: String): Flow<ChapterWithStats?>

    @Query("SELECT COUNT(*) FROM chapters")
    suspend fun getChapterCount(): Int

    @Query("SELECT * FROM chapters")
    suspend fun getAllChaptersSync(): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE chapterId = :chapterId LIMIT 1")
    suspend fun getChapterByIdSync(chapterId: String): ChapterEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChaptersIgnore(chapters: List<ChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapters(chapters: List<ChapterEntity>)

    @Query("UPDATE chapters SET customTitle = :customTitle WHERE chapterId = :chapterId")
    suspend fun updateChapterTitle(chapterId: String, customTitle: String?)

    @Query("UPDATE chapters SET lastUpdatedAt = :timestamp WHERE chapterId = :chapterId")
    suspend fun updateChapterLastUpdated(chapterId: String, timestamp: Long)

    @Query("SELECT * FROM page_links WHERE chapterId = :chapterId ORDER BY pageNumber ASC, id ASC")
    fun observeLinksForChapter(chapterId: String): Flow<List<PageLinkEntity>>

    @Query("SELECT * FROM page_links WHERE chapterId = :chapterId ORDER BY pageNumber ASC, id ASC")
    suspend fun getLinksForChapterSync(chapterId: String): List<PageLinkEntity>

    @Query(
        """
        SELECT p.* FROM page_links p
        INNER JOIN chapters c ON p.chapterId = c.chapterId
        WHERE c.paperId = :paperId
        ORDER BY c.chapterNumber ASC, p.pageNumber ASC, p.id ASC
        """
    )
    suspend fun getLinksForPaperSerialSync(paperId: String): List<PageLinkEntity>

    @Query("SELECT * FROM page_links ORDER BY chapterId ASC, pageNumber ASC, id ASC")
    suspend fun getAllLinksSync(): List<PageLinkEntity>

    @Query("SELECT url FROM page_links WHERE chapterId = :chapterId")
    suspend fun getUrlsInChapterSync(chapterId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPageLinksIgnore(links: List<PageLinkEntity>): List<Long>

    @Query("DELETE FROM page_links WHERE id = :linkId")
    suspend fun deleteLinkById(linkId: Long)

    @Query("DELETE FROM page_links WHERE id IN (:linkIds)")
    suspend fun deleteLinksByIds(linkIds: List<Long>)

    @Query("DELETE FROM page_links WHERE chapterId = :chapterId")
    suspend fun deleteAllLinksInChapter(chapterId: String)

    @Query("DELETE FROM page_links")
    suspend fun deleteAllPageLinks()

    @Transaction
    suspend fun mergeLinksIntoChapter(chapterId: String, newLinks: List<PageLinkEntity>): SaveChapterResult {
        val existingUrls = getUrlsInChapterSync(chapterId).toHashSet()
        val deduped = newLinks.distinctBy { it.url }.filter { it.url !in existingUrls }
        val duplicates = newLinks.size - deduped.size
        if (deduped.isNotEmpty()) {
            insertPageLinksIgnore(deduped)
            updateChapterLastUpdated(chapterId, System.currentTimeMillis())
        }
        return SaveChapterResult(addedCount = deduped.size, duplicateCount = duplicates)
    }

    @Transaction
    suspend fun replaceLinksInChapter(chapterId: String, newLinks: List<PageLinkEntity>): Int {
        deleteAllLinksInChapter(chapterId)
        val deduped = newLinks.distinctBy { it.url }
        if (deduped.isNotEmpty()) {
            insertPageLinksIgnore(deduped)
        }
        updateChapterLastUpdated(chapterId, if (deduped.isNotEmpty()) System.currentTimeMillis() else 0L)
        return deduped.size
    }

    @Transaction
    suspend fun removeLinksAndTouchChapter(chapterId: String, linkIds: List<Long>) {
        if (linkIds.isNotEmpty()) {
            deleteLinksByIds(linkIds)
            val remaining = getLinksForChapterSync(chapterId)
            updateChapterLastUpdated(chapterId, if (remaining.isEmpty()) 0L else System.currentTimeMillis())
        }
    }

    @Transaction
    suspend fun clearChapterLinks(chapterId: String) {
        deleteAllLinksInChapter(chapterId)
        updateChapterLastUpdated(chapterId, 0L)
    }
}
