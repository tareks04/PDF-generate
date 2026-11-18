package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.local.ChapterWithStats
import com.example.i18n.AppStrings
import com.example.model.PageScanStatus
import com.example.model.SaveMode
import com.example.model.ScannedPageResult
import com.example.model.SubjectCatalog
import com.example.model.SubjectGroup
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.StatusErrorBgDark
import com.example.ui.theme.StatusErrorBgLight
import com.example.ui.theme.StatusErrorColor
import com.example.ui.theme.StatusMissingBgDark
import com.example.ui.theme.StatusMissingBgLight
import com.example.ui.theme.StatusMissingColor
import com.example.ui.theme.StatusValidBgDark
import com.example.ui.theme.StatusValidBgLight
import com.example.ui.theme.StatusValidColor
import com.example.ui.theme.StatusWarningBgDark
import com.example.ui.theme.StatusWarningBgLight
import com.example.ui.theme.StatusWarningColor
import java.io.File

@Composable
fun LanguageTogglePill(
    isBangla: Boolean,
    contentDescription: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("language_toggle_button"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = contentDescription,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = if (isBangla) "B" else "E",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (isBangla) "/E" else "/B",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
fun ScanStatusBadge(
    status: PageScanStatus,
    strings: AppStrings,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val (fg, bg) = when (status) {
        PageScanStatus.VALID -> StatusValidColor to (if (isDark) StatusValidBgDark else StatusValidBgLight)
        PageScanStatus.MISSING -> StatusMissingColor to (if (isDark) StatusMissingBgDark else StatusMissingBgLight)
        PageScanStatus.RATE_LIMITED, PageScanStatus.TIMEOUT, PageScanStatus.NETWORK_ERROR ->
            StatusWarningColor to (if (isDark) StatusWarningBgDark else StatusWarningBgLight)
        PageScanStatus.BLOCKED, PageScanStatus.SERVER_ERROR, PageScanStatus.INVALID_IMAGE ->
            StatusErrorColor to (if (isDark) StatusErrorBgDark else StatusErrorBgLight)
    }

    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = strings.statusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MetricSummaryCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = tint.copy(alpha = 0.14f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ImagePreviewDialog(
    title: String,
    pageLabel: String,
    imageSource: Any,
    dimensionsText: String?,
    closeLabel: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (dimensionsText != null) "$pageLabel • $dimensionsText" else pageLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = closeLabel)
                    }
                }
                HorizontalDivider()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 260.dp, max = 520.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageSource)
                            .crossfade(true)
                            .build(),
                        contentDescription = pageLabel,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        },
                        error = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Unable to load preview image",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(closeLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun ValidPagesGalleryDialog(
    validPages: List<ScannedPageResult>,
    strings: AppStrings,
    onDismiss: () -> Unit,
) {
    if (validPages.isEmpty()) return
    var currentIndex by remember(validPages) { mutableIntStateOf(0) }
    val page = validPages[currentIndex.coerceIn(0, validPages.lastIndex)]
    val context = LocalContext.current
    val imageModel: Any = page.previewFilePath?.let { File(it) } ?: page.url

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.previewValidButton,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${strings.pageLabelPrefix} ${page.formattedPageNumber} (${currentIndex + 1}/${validPages.size})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = strings.closeButton)
                    }
                }
                HorizontalDivider()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = page.formattedPageNumber,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        },
                    )
                }
                Text(
                    text = page.url,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { if (currentIndex > 0) currentIndex-- },
                        enabled = currentIndex > 0,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (strings.isBangla) "আগের" else "Prev")
                    }
                    TextButton(onClick = onDismiss) {
                        Text(strings.closeButton)
                    }
                    OutlinedButton(
                        onClick = { if (currentIndex < validPages.lastIndex) currentIndex++ },
                        enabled = currentIndex < validPages.lastIndex,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(if (strings.isBangla) "পরের" else "Next")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectSubjectChapterModal(
    validPages: List<ScannedPageResult>,
    chaptersWithStats: List<ChapterWithStats>,
    strings: AppStrings,
    onRenameChapter: (chapterId: String, newTitle: String?) -> Unit,
    onSaveSingleChapter: (chapterId: String, pagesToSave: List<ScannedPageResult>, mode: SaveMode) -> Unit,
    onSaveAutoMappedPaper: (paperId: String, pagesToSave: List<ScannedPageResult>, mode: SaveMode) -> Unit,
    onDismiss: () -> Unit,
) {
    if (validPages.isEmpty()) return

    val discoveredGroups = remember(validPages) {
        validPages
            .groupBy { it.bookNumber to it.chapterNumber }
            .entries
            .sortedWith(compareBy({ it.key.first ?: 0 }, { it.key.second ?: 0 }))
    }
    val hasMultipleChapters = remember(discoveredGroups) {
        discoveredGroups.count { it.key.second != null } > 1
    }

    // Group filter: null = All pages, Pair(book, ch) = specific discovered group
    var selectedSourceGroup by remember(validPages) {
        mutableStateOf<Pair<Int?, Int?>?>(null)
    }
    var autoMapAllChapters by remember(hasMultipleChapters) {
        mutableStateOf(hasMultipleChapters)
    }

    val pagesForSave = remember(validPages, selectedSourceGroup, autoMapAllChapters) {
        if (autoMapAllChapters || selectedSourceGroup == null) {
            validPages
        } else {
            validPages.filter { (it.bookNumber to it.chapterNumber) == selectedSourceGroup }
        }
    }

    var selectedGroup by remember { mutableStateOf(SubjectGroup.PHYSICS) }
    val papersInGroup = remember(selectedGroup) { SubjectCatalog.papersForGroup(selectedGroup) }
    var selectedPaper by remember(selectedGroup) { mutableStateOf(papersInGroup.first()) }
    val chaptersInPaper = remember(selectedPaper, chaptersWithStats) {
        val dbMap = chaptersWithStats.filter { it.paperId == selectedPaper.id }.associateBy { it.chapterNumber }
        (1..selectedPaper.chapterCount).map { num ->
            val entity = dbMap[num]
            val id = SubjectCatalog.chapterId(selectedPaper.id, num)
            val title = entity?.customTitle?.takeIf { it.isNotBlank() }
                ?: SubjectCatalog.defaultChapterName(num, strings.isBangla)
            Triple(id, num, title)
        }
    }
    var selectedChapterNumber by remember(selectedPaper) { mutableIntStateOf(1) }
    var saveMode by remember { mutableStateOf(SaveMode.MERGE) }
    var renameDialogChapter by remember { mutableStateOf<Triple<String, Int, String>?>(null) }

    val selectedChapterTriple = chaptersInPaper.find { it.second == selectedChapterNumber }
        ?: chaptersInPaper.first()

    val sortedPages = remember(pagesForSave) { pagesForSave.sortedBy { it.pageNumber } }
    val firstPage = sortedPages.firstOrNull()?.formattedPageNumber ?: "—"
    val lastPage = sortedPages.lastOrNull()?.formattedPageNumber ?: "—"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 780.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = strings.selectSubjectAndChapterTitle,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = strings.cancelButton)
                    }
                }
                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // 0. If multiple chapters/books discovered, offer Auto-Map or specific source selection
                    if (hasMultipleChapters) {
                        val distinctChCount = discoveredGroups.mapNotNull { it.key.second }.distinct().size
                        SaveModeOptionRow(
                            selected = autoMapAllChapters,
                            title = strings.autoMapChaptersOptionTitle,
                            desc = strings.autoMapChaptersOptionDesc(distinctChCount),
                            onClick = { autoMapAllChapters = !autoMapAllChapters },
                            testTag = "save_auto_map_chapters",
                        )

                        if (!autoMapAllChapters) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected = selectedSourceGroup == null,
                                    onClick = { selectedSourceGroup = null },
                                    label = { Text("${strings.filterAllChapters} (${validPages.size})") },
                                )
                                discoveredGroups.forEach { (key, groupPages) ->
                                    val label = buildString {
                                        key.first?.let { append("${strings.currentBookLabel} $it • ") }
                                        key.second?.let { append("${strings.currentChapterLabel} $it ") }
                                        append("(${groupPages.size})")
                                    }
                                    FilterChip(
                                        selected = selectedSourceGroup == key,
                                        onClick = { selectedSourceGroup = key },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }

                    // 1. Subject group selector
                    Text(strings.selectSubjectLabel, style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SubjectGroup.entries.forEach { group ->
                            FilterChip(
                                selected = selectedGroup == group,
                                onClick = {
                                    selectedGroup = group
                                    selectedPaper = SubjectCatalog.papersForGroup(group).first()
                                    selectedChapterNumber = 1
                                },
                                label = { Text(group.title(strings.isBangla)) },
                                modifier = Modifier.testTag("modal_subject_${group.id}"),
                            )
                        }
                    }

                    // 2. Paper selector
                    Text(strings.selectPaperLabel, style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        papersInGroup.forEach { paper ->
                            FilterChip(
                                selected = selectedPaper.id == paper.id,
                                onClick = {
                                    selectedPaper = paper
                                    selectedChapterNumber = 1
                                },
                                label = { Text(paper.title(strings.isBangla)) },
                                modifier = Modifier.testTag("modal_paper_${paper.id}"),
                            )
                        }
                    }

                    // 3. Dynamic chapter selector (hidden when Auto-Mapping all discovered chapters)
                    if (!autoMapAllChapters) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(strings.selectChapterLabel, style = MaterialTheme.typography.titleSmall)
                            TextButton(
                                onClick = { renameDialogChapter = selectedChapterTriple },
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.renameChapterTitle)
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            chaptersInPaper.forEach { (chId, chNum, chTitle) ->
                                FilterChip(
                                    selected = selectedChapterNumber == chNum,
                                    onClick = { selectedChapterNumber = chNum },
                                    label = { Text(chTitle) },
                                    modifier = Modifier.testTag("modal_chapter_$chId"),
                                )
                            }
                        }
                    }

                    // 4. Summary card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(strings.selectedSummaryTitle, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (autoMapAllChapters) {
                                    "${selectedPaper.title(strings.isBangla)} (${strings.autoMapChaptersOptionTitle})"
                                } else {
                                    "${selectedPaper.title(strings.isBangla)} — ${selectedChapterTriple.third}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${strings.totalValidLinksLabel}: ${strings.formatNum(pagesForSave.size)}", style = MaterialTheme.typography.bodySmall)
                                Text("${strings.firstPageLabel}: $firstPage  |  ${strings.lastPageLabel}: $lastPage", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // 5. Save Mode: Merge vs Replace
                    Text(strings.saveModeTitle, style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SaveModeOptionRow(
                            selected = saveMode == SaveMode.MERGE,
                            title = strings.mergeOptionTitle,
                            desc = strings.mergeOptionDesc,
                            onClick = { saveMode = SaveMode.MERGE },
                            testTag = "save_mode_merge",
                        )
                        SaveModeOptionRow(
                            selected = saveMode == SaveMode.REPLACE,
                            title = strings.replaceOptionTitle,
                            desc = strings.replaceOptionDesc,
                            onClick = { saveMode = SaveMode.REPLACE },
                            testTag = "save_mode_replace",
                        )
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(strings.cancelButton)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (autoMapAllChapters && hasMultipleChapters) {
                                onSaveAutoMappedPaper(selectedPaper.id, pagesForSave, saveMode)
                            } else {
                                onSaveSingleChapter(selectedChapterTriple.first, pagesForSave, saveMode)
                            }
                        },
                        enabled = pagesForSave.isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("modal_confirm_save_button"),
                    ) {
                        Text(strings.saveButton)
                    }
                }
            }
        }
    }

    renameDialogChapter?.let { (chId, _, currentTitle) ->
        RenameChapterDialog(
            currentTitle = currentTitle,
            strings = strings,
            onConfirm = { newTitle ->
                onRenameChapter(chId, newTitle)
                renameDialogChapter = null
            },
            onDismiss = { renameDialogChapter = null },
        )
    }
}

@Composable
private fun SaveModeOptionRow(
    selected: Boolean,
    title: String,
    desc: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun RenameChapterDialog(
    currentTitle: String,
    strings: AppStrings,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember(currentTitle) { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.renameChapterTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(strings.chapterTitleInputLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { onConfirm(null) },
                ) {
                    Text(strings.resetDefaultTitle)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(input.trim().takeIf { it.isNotEmpty() }) }) {
                Text(strings.saveTitleButton)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelButton)
            }
        },
    )
}
