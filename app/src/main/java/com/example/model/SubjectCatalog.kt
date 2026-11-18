package com.example.model

enum class SubjectGroup(
    val id: String,
    val titleBn: String,
    val titleEn: String,
) {
    PHYSICS("physics", "পদার্থবিজ্ঞান", "Physics"),
    CHEMISTRY("chemistry", "রসায়ন", "Chemistry"),
    BIOLOGY("biology", "জীববিজ্ঞান", "Biology");

    fun title(isBangla: Boolean): String = if (isBangla) titleBn else titleEn
}

data class PaperSpec(
    val id: String,
    val group: SubjectGroup,
    val titleBn: String,
    val titleEn: String,
    val chapterCount: Int,
) {
    fun title(isBangla: Boolean): String = if (isBangla) titleBn else titleEn
}

object SubjectCatalog {
    val papers: List<PaperSpec> = listOf(
        PaperSpec(
            id = "physics_1",
            group = SubjectGroup.PHYSICS,
            titleBn = "পদার্থবিজ্ঞান ১ম পত্র",
            titleEn = "Physics 1st Paper",
            chapterCount = 11,
        ),
        PaperSpec(
            id = "physics_2",
            group = SubjectGroup.PHYSICS,
            titleBn = "পদার্থবিজ্ঞান ২য় পত্র",
            titleEn = "Physics 2nd Paper",
            chapterCount = 11,
        ),
        PaperSpec(
            id = "chemistry_1",
            group = SubjectGroup.CHEMISTRY,
            titleBn = "রসায়ন ১ম পত্র",
            titleEn = "Chemistry 1st Paper",
            chapterCount = 5,
        ),
        PaperSpec(
            id = "chemistry_2",
            group = SubjectGroup.CHEMISTRY,
            titleBn = "রসায়ন ২য় পত্র",
            titleEn = "Chemistry 2nd Paper",
            chapterCount = 5,
        ),
        PaperSpec(
            id = "botany",
            group = SubjectGroup.BIOLOGY,
            titleBn = "উদ্ভিদবিজ্ঞান (Botany)",
            titleEn = "Botany",
            chapterCount = 12,
        ),
        PaperSpec(
            id = "zoology",
            group = SubjectGroup.BIOLOGY,
            titleBn = "প্রাণিবিজ্ঞান (Zoology)",
            titleEn = "Zoology",
            chapterCount = 12,
        ),
    )

    fun paperById(paperId: String): PaperSpec? = papers.find { it.id == paperId }

    fun papersForGroup(group: SubjectGroup): List<PaperSpec> = papers.filter { it.group == group }

    fun chapterId(paperId: String, chapterNumber: Int): String = "${paperId}_ch_${chapterNumber}"

    fun defaultChapterName(chapterNumber: Int, isBangla: Boolean): String =
        if (isBangla) "অধ্যায় ${toBanglaDigits(chapterNumber)}" else "Chapter $chapterNumber"

    fun toBanglaDigits(number: Int): String = toBanglaDigits(number.toString())

    fun toBanglaDigits(raw: String): String {
        val map = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        return buildString {
            for (ch in raw) {
                if (ch in '0'..'9') {
                    append(map[ch - '0'])
                } else {
                    append(ch)
                }
            }
        }
    }
}
