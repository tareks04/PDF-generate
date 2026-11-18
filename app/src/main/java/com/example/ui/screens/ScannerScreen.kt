package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ChapterWithStats
import com.example.i18n.AppStrings
import com.example.model.PageScanStatus
import com.example.model.SaveMode
import com.example.model.ScannedPageResult
import com.example.model.ScannerRunState
import com.example.ui.components.ImagePreviewDialog
import com.example.ui.components.ScanStatusBadge
import com.example.ui.components.SelectSubjectChapterModal
import com.example.ui.components.ValidPagesGalleryDialog
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.ScannerUiState
import com.example.viewmodel.ScannerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    uiState: ScannerUiState,
    chaptersWithStats: List<ChapterWithStats>,
    strings: AppStrings,
    viewModel: ScannerViewModel,
    onRenameChapter: (chapterId: String, newTitle: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var previewSinglePage by remember { mutableStateOf<ScannedPageResult?>(null) }
    var showValidGallery by remember { mutableStateOf(false) }
    var showSaveModal by remember { mutableStateOf(false) }

    val isScanning = uiState.runState == ScannerRunState.SCANNING
    val isPaused = uiState.runState == ScannerRunState.PAUSED

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header & URL Input Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = strings.scannerTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = strings.scannerSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedTextField(
                            value = uiState.urlInput,
                            onValueChange = viewModel::onUrlInputChange,
                            label = { Text(strings.urlInputLabel) },
                            placeholder = {
                                Text(
                                    strings.urlInputPlaceholder,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                                )
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                            singleLine = false,
                            maxLines = 3,
                            enabled = !isScanning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scanner_url_input"),
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = uiState.maxPagesInput,
                                onValueChange = viewModel::onMaxPagesChange,
                                label = { Text(strings.maxPagesLabel) },
                                placeholder = { Text(strings.optionalHint) },
                                singleLine = true,
                                enabled = !isScanning,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scanner_max_pages_input"),
                            )
                            OutlinedTextField(
                                value = uiState.startPageOverrideInput,
                                onValueChange = viewModel::onStartPageOverrideChange,
                                label = { Text(strings.startPageOverrideLabel) },
                                singleLine = true,
                                enabled = !isScanning,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scanner_start_page_input"),
                            )
                        }

                        // Detection Error
                        uiState.detectionError?.let { reason ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null)
                                    Text(
                                        text = strings.detectionErrorMessage(reason),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }

                        // Ambiguous Page Number Picker
                        AnimatedVisibility(visible = uiState.ambiguousCandidates.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                        )
                                        Text(
                                            text = strings.ambiguousTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Text(
                                        text = strings.ambiguousSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        uiState.ambiguousCandidates.forEach { cand ->
                                            OutlinedButton(
                                                onClick = { viewModel.selectAmbiguousCandidateAndScan(cand) },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("ambiguous_candidate_${cand.rawDigits}"),
                                            ) {
                                                Text(
                                                    text = "${cand.rawDigits} (${cand.contextLabel})",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Primary Scan / Clear Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = viewModel::startScan,
                                enabled = !isScanning,
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("scanner_scan_button"),
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(strings.scanButton)
                            }
                            OutlinedButton(
                                onClick = viewModel::clearInputsAndResults,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("scanner_clear_button"),
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(strings.clearButton)
                            }
                        }
                    }
                }
            }

            // Save Banner Feedback
            uiState.saveBannerMessage?.let { msg ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = viewModel::dismissSaveBanner) {
                                Text(strings.closeButton)
                            }
                        }
                    }
                }
            }

            // Live Progress, Status & Controls Card
            if (uiState.results.isNotEmpty() || isScanning || isPaused) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (isScanning) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            // Stats row
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                StatPill(strings.scanningStatusLabel, strings.runStateLabel(uiState.runState))
                                StatPill(strings.totalTested, strings.formatNum(uiState.totalTested))
                                StatPill(strings.validPages, strings.formatNum(uiState.validCount))
                                StatPill(strings.missingPages, strings.formatNum(uiState.missingCount))
                                uiState.currentPageNumber?.let { cur ->
                                    StatPill(strings.currentPageLabel, strings.formatNum(cur))
                                }
                            }

                            // Pause reason alert
                            uiState.pauseReasonStatus?.let { pStatus ->
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Default.WarningAmber, contentDescription = null)
                                        Text(
                                            text = "${strings.pauseReasonPrefix} ${
                                                strings.statusReasonMessage(
                                                    pStatus,
                                                    uiState.pauseReasonHttpCode,
                                                    uiState.pauseReasonDetail,
                                                )
                                            }",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }

                            // Scanner control buttons
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (isScanning) {
                                    OutlinedButton(
                                        onClick = viewModel::pauseScan,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("scanner_pause_button"),
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.pauseButton)
                                    }
                                    OutlinedButton(
                                        onClick = viewModel::stopScan,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("scanner_stop_button"),
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.stopButton)
                                    }
                                }
                                if (isPaused) {
                                    Button(
                                        onClick = viewModel::resumeScan,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("scanner_resume_button"),
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.resumeButton)
                                    }
                                    OutlinedButton(
                                        onClick = viewModel::stopScan,
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.stopButton)
                                    }
                                }
                                if (!isScanning && uiState.hasFailedRetryable) {
                                    OutlinedButton(
                                        onClick = viewModel::retryFailedUrls,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("scanner_retry_failed_button"),
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.retryFailedButton)
                                    }
                                }
                                if (!isScanning && uiState.results.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = viewModel::clearResultsOnly,
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.clearResultsButton)
                                    }
                                }
                                if (uiState.validCount > 0) {
                                    OutlinedButton(
                                        onClick = { showValidGallery = true },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("scanner_preview_valid_button"),
                                    ) {
                                        Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.previewValidButton)
                                    }
                                    Button(
                                        onClick = { showSaveModal = true },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("scanner_save_links_button"),
                                    ) {
                                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.saveValidLinksButton)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Results Table Header or Empty State
            if (uiState.results.isEmpty() && !isScanning) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(strings.scanEmptyStateTitle, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = strings.scanEmptyStateDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (uiState.results.isNotEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(strings.colPage, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                            Text(strings.colUrl, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(strings.colStatus, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            Text(strings.colAction, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                items(uiState.results, key = { "${it.pageNumber}_${it.url}" }) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.formattedPageNumber,
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(56.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.url.substringAfterLast('/').ifEmpty { item.url },
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            ScanStatusBadge(status = item.status, strings = strings)
                            Spacer(Modifier.width(6.dp))
                            if (item.status == PageScanStatus.VALID) {
                                TextButton(
                                    onClick = { previewSinglePage = item },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = strings.previewAction, modifier = Modifier.size(18.dp))
                                }
                            } else {
                                Text(
                                    text = "—",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewSinglePage?.let { page ->
        val src: Any = page.previewFilePath?.let { File(it) } ?: page.url
        val dims = if (page.width > 0 && page.height > 0) "${page.width}×${page.height}" else null
        ImagePreviewDialog(
            title = strings.imagePreviewTitle,
            pageLabel = "${strings.pageLabelPrefix} ${page.formattedPageNumber}",
            imageSource = src,
            dimensionsText = dims,
            closeLabel = strings.closeButton,
            onDismiss = { previewSinglePage = null },
        )
    }

    if (showValidGallery) {
        ValidPagesGalleryDialog(
            validPages = uiState.validResults,
            strings = strings,
            onDismiss = { showValidGallery = false },
        )
    }

    if (showSaveModal) {
        SelectSubjectChapterModal(
            validPages = uiState.validResults,
            chaptersWithStats = chaptersWithStats,
            strings = strings,
            onRenameChapter = onRenameChapter,
            onSave = { chapterId, mode ->
                scope.launch {
                    val result = viewModel.saveValidLinksToChapter(chapterId, mode)
                    val msg = when (mode) {
                        SaveMode.MERGE -> strings.saveSuccessMerge(result.addedCount, result.duplicateCount)
                        SaveMode.REPLACE -> strings.saveSuccessReplace(result.addedCount)
                    }
                    viewModel.setSaveBanner(msg)
                    showSaveModal = false
                }
            },
            onDismiss = { showSaveModal = false },
        )
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}
