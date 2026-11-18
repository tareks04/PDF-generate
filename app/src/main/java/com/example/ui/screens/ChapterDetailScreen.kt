package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ChapterWithStats
import com.example.data.local.PageLinkEntity
import com.example.i18n.AppStrings
import com.example.model.SubjectCatalog
import com.example.pdf.PdfGenerator
import com.example.ui.components.ImagePreviewDialog
import com.example.ui.components.RenameChapterDialog
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChapterDetailScreen(
    chapter: ChapterWithStats?,
    links: List<PageLinkEntity>,
    strings: AppStrings,
    onBack: () -> Unit,
    onOpenPdfPreview: () -> Unit,
    onRenameChapter: (String?) -> Unit,
    onDeleteSingleLink: (Long) -> Unit,
    onDeleteSelectedLinks: (List<Long>) -> Unit,
    onDeleteAllLinks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var previewLink by remember { mutableStateOf<PageLinkEntity?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var confirmSingleDelete by remember { mutableStateOf<PageLinkEntity?>(null) }
    var confirmSelectedDelete by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val paper = chapter?.let { SubjectCatalog.paperById(it.paperId) }
    val paperTitle = paper?.title(strings.isBangla) ?: ""
    val chTitle = chapter?.let {
        it.customTitle?.takeIf { t -> t.isNotBlank() }
            ?: SubjectCatalog.defaultChapterName(it.chapterNumber, strings.isBangla)
    } ?: ""

    val txtExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val content = links
                    .sortedWith(compareBy({ it.pageNumber }, { it.id }))
                    .joinToString("\n") { "${it.formattedPageNumber}\t${it.url}" }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                scope.launch { snackbarHostState.showSnackbar(strings.exportedTxtSuccess) }
            }.onFailure {
                scope.launch { snackbarHostState.showSnackbar(strings.exportTxtFailed) }
            }
        }
    }

    fun copyText(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("SmartPageLibrary", text))
        scope.launch { snackbarHostState.showSnackbar(strings.copiedToClipboard) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "$paperTitle — $chTitle",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${strings.pagesLabel}: ${strings.formatNum(links.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("chapter_detail_back_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backToLibrary)
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = strings.renameChapterTitle)
                    }
                    if (links.isNotEmpty()) {
                        IconButton(onClick = onOpenPdfPreview) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = strings.pdfPrintButton)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 900.dp),
            ) {
                if (links.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val allUrls = links.sortedBy { it.pageNumber }.joinToString("\n") { it.url }
                                copyText(allUrls)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.copyAllLinks)
                        }
                        OutlinedButton(
                            onClick = {
                                val name = PdfGenerator.buildSafePdfFilename(
                                    paperTitleEn = paper?.titleEn ?: "Chapter",
                                    chapterNumber = chapter?.chapterNumber ?: 1,
                                    customTitle = chapter?.customTitle,
                                ).replace(".pdf", ".txt")
                                txtExportLauncher.launch(name)
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.exportTxt)
                        }
                        OutlinedButton(
                            onClick = {
                                selectionMode = !selectionMode
                                if (!selectionMode) selectedIds = emptySet()
                            },
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Default.CheckBox, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (selectionMode) strings.cancelSelection else strings.selectMultiple)
                        }
                        if (selectionMode && selectedIds.isNotEmpty()) {
                            Button(
                                onClick = { confirmSelectedDelete = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${strings.deleteSelected} (${strings.formatNum(selectedIds.size)})")
                            }
                        }
                        OutlinedButton(
                            onClick = { confirmDeleteAll = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.deleteAllInChapter)
                        }
                    }
                }

                if (links.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = strings.emptyChapterLinks,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(links, key = { it.id }) { link ->
                            val isSelected = link.id in selectedIds
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (selectionMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked) selectedIds + link.id else selectedIds - link.id
                                            },
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ) {
                                        Text(
                                            text = link.formattedPageNumber,
                                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = link.url,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = { previewLink = link },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = strings.previewAction)
                                    }
                                    IconButton(
                                        onClick = { copyText(link.url) },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = strings.copyAction)
                                    }
                                    IconButton(
                                        onClick = { confirmSingleDelete = link },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = strings.removeAction,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewLink?.let { link ->
        ImagePreviewDialog(
            title = strings.imagePreviewTitle,
            pageLabel = "${strings.pageLabelPrefix} ${link.formattedPageNumber}",
            imageSource = link.url,
            dimensionsText = null,
            closeLabel = strings.closeButton,
            onDismiss = { previewLink = null },
        )
    }

    if (showRenameDialog) {
        RenameChapterDialog(
            currentTitle = chTitle,
            strings = strings,
            onConfirm = { newTitle ->
                onRenameChapter(newTitle)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false },
        )
    }

    confirmSingleDelete?.let { link ->
        AlertDialog(
            onDismissRequest = { confirmSingleDelete = null },
            title = { Text(strings.removeAction) },
            text = { Text("${strings.deleteSingleLinkConfirm}\n\n${link.url}") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSingleLink(link.id)
                        confirmSingleDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(strings.confirmDeleteButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSingleDelete = null }) { Text(strings.cancelButton) }
            },
        )
    }

    if (confirmSelectedDelete) {
        AlertDialog(
            onDismissRequest = { confirmSelectedDelete = false },
            title = { Text(strings.deleteSelected) },
            text = { Text(strings.deleteSelectedConfirm(selectedIds.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSelectedLinks(selectedIds.toList())
                        selectedIds = emptySet()
                        selectionMode = false
                        confirmSelectedDelete = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(strings.confirmDeleteButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSelectedDelete = false }) { Text(strings.cancelButton) }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(strings.deleteAllInChapter) },
            text = { Text(strings.deleteAllLinksConfirm) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllLinks()
                        confirmDeleteAll = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(strings.confirmDeleteButton)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(strings.cancelButton) }
            },
        )
    }
}
