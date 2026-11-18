package com.example.data.local

data class ChapterWithStats(
    val chapterId: String,
    val subjectGroupId: String,
    val paperId: String,
    val chapterNumber: Int,
    val customTitle: String?,
    val createdAt: Long,
    val lastUpdatedAt: Long,
    val pageCount: Int,
    val minPage: Int?,
    val maxPage: Int?,
)
