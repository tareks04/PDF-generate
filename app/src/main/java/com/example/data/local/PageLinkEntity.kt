package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_links",
    foreignKeys = [
        ForeignKey(
            entity = ChapterEntity::class,
            parentColumns = ["chapterId"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["chapterId"]),
        Index(value = ["chapterId", "url"], unique = true),
    ],
)
data class PageLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val chapterId: String,
    val pageNumber: Int,
    val formattedPageNumber: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
)
