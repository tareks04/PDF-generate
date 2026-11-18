package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey val chapterId: String,
    val subjectGroupId: String,
    val paperId: String,
    val chapterNumber: Int,
    val customTitle: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = 0L,
)
