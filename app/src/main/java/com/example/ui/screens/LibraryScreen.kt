package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ChapterWithStats
import com.example.i18n.AppStrings
import com.example.model.SubjectCatalog
import com.example.model.SubjectGroup
import com.example.ui.components.RenameChapterDialog
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    chaptersWithStats: List<ChapterWithStats>,
    showSavedOnly: Boolean,
    strings: AppStrings,
    onToggleSavedOnly: (Boolean) -> Unit,
    onOpenChapter: (String) -> Unit,
    onOpenPdfPreview: (String) -> Unit,
    onRenameChapter: (chapterId: String, newTitle: String?) -> Unit,
    onDeleteChapterLinks: (chapterId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedGroup by remember { mutableStateOf(SubjectGroup.PHYSICS) }
    val papers = remember(selectedGroup) { SubjectCatalog.papersForGroup(selectedGroup) }
    var selectedPaperId by remember(selectedGroup) { mutableStateOf(papers.first().id) }

    var renameTarget by remember { mutableStateOf<ChapterWithStats?>(null) }
    var deleteTarget by remember { mutableStateOf<ChapterWithStats?>(null) }

    val filteredChapters = remember(chaptersWithStats, selectedPaperId, showSavedOnly) {
        chaptersWithStats
            .filter { it.paperId == selectedPaperId }
            .filter { !showSavedOnly || it.pageCount > 0 }
            .sortedBy { it.chapterNumber }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp),
        ) {
            // Subject Group Tabs
            TabRow(selectedTabIndex = SubjectGroup.entries.indexOf(selectedGroup)) {
                SubjectGroup.entries.forEach { group ->
                    Tab(
                        selected = selectedGroup == group,
                        onClick = {
                            selectedGroup = group
                            selectedPaperId = SubjectCatalog.papersForGroup(group).first().id
                        },
                        text = { Text(group.title(strings.isBangla)) },
                        modifier = Modifier.testTag("library_tab_${group.id}"),
                    )
                }
            }

            // Paper Chips + Saved-only Filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    papers.forEach { paper ->
                        FilterChip(
                            selected = selectedPaperId == paper.id,
                            onClick = { selectedPaperId = paper.id },
                            label = { Text(paper.title(strings.isBangla)) },
                            modifier = Modifier.testTag("library_paper_${paper.id}"),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = showSavedOnly,
                    onClick = { onToggleSavedOnly(!showSavedOnly) },
                    leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    label = { Text(if (showSavedOnly) strings.filterSavedOnly else strings.filterAllChapters) },
                    modifier = Modifier.testTag("library_filter_saved_chip"),
                )
            }

            // Chapter Cards List
            if (filteredChapters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = strings.noSavedChaptersYet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filteredChapters, key = { it.chapterId }) { chapter ->
                        ChapterCard(
                            chapter = chapter,
                            strings = strings,
                            onPreview = { onOpenChapter(chapter.chapterId) },
                            onPdfPrint = { onOpenPdfPreview(chapter.chapterId) },
                            onEdit = { onOpenChapter(chapter.chapterId) },
                            onRename = { renameTarget = chapter },
                            onDelete = { deleteTarget = chapter },
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { ch ->
        val currentName = ch.customTitle?.takeIf { it.isNotBlank() }
            ?: SubjectCatalog.defaultChapterName(ch.chapterNumber, strings.isBangla)
        RenameChapterDialog(
            currentTitle = currentName,
            strings = strings,
            onConfirm = { newTitle ->
                onRenameChapter(ch.chapterId, newTitle)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { ch ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(strings.deleteChapterConfirmTitle) },
            text = { Text(strings.deleteChapterConfirmBody) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteChapterLinks(ch.chapterId)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(strings.confirmDeleteButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(strings.cancelButton)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChapterCard(
    chapter: ChapterWithStats,
    strings: AppStrings,
    onPreview: () -> Unit,
    onPdfPrint: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val paper = SubjectCatalog.paperById(chapter.paperId)
    val paperTitle = paper?.title(strings.isBangla) ?: chapter.paperId
    val chTitle = chapter.customTitle?.takeIf { it.isNotBlank() }
        ?: SubjectCatalog.defaultChapterName(chapter.chapterNumber, strings.isBangla)

    val pageRangeText = if (chapter.pageCount > 0 && chapter.minPage != null && chapter.maxPage != null) {
        "${chapter.minPage.toString().padStart(3, '0')}–${chapter.maxPage.toString().padStart(3, '0')}"
    } else {
        "—"
    }

    val updatedText = if (chapter.lastUpdatedAt > 0L) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(chapter.lastUpdatedAt))
    } else {
        strings.notUpdatedYet
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chapter_card_${chapter.chapterId}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = paperTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = chTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = strings.renameChapterTitle)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "${strings.pagesLabel}: ${strings.formatNum(chapter.pageCount)}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "${strings.pageRangeLabel}: $pageRangeText",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = "${strings.lastUpdatedLabel}: $updatedText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    enabled = chapter.pageCount > 0,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.previewAction)
                }
                Button(
                    onClick = onPdfPrint,
                    enabled = chapter.pageCount > 0,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("chapter_pdf_button_${chapter.chapterId}"),
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.pdfPrintButton)
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.editLinksButton)
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = chapter.pageCount > 0,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(strings.deleteButton)
                }
            }
        }
    }
}
