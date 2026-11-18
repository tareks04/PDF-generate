package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.i18n.AppStrings
import com.example.model.PdfPageSizeMode
import com.example.model.PdfWorkflowState
import com.example.pdf.PdfPrintHelper
import com.example.ui.theme.MonoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    chapterId: String,
    chapterTitle: String,
    state: PdfWorkflowState,
    pageSizeMode: PdfPageSizeMode,
    strings: AppStrings,
    onLoadChapter: (String) -> Unit,
    onRetryFailed: () -> Unit,
    onChangePageSizeMode: (PdfPageSizeMode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(chapterId) {
        onLoadChapter(chapterId)
    }

    val readyState = state as? PdfWorkflowState.Ready

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = chapterTitle.ifEmpty { strings.pdfPreviewTitle },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        readyState?.let { ready ->
                            Text(
                                text = "${strings.totalPagesShort}: ${strings.formatNum(ready.totalPages)} • ${ready.pdfFileName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pdf_back_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.backToLibrary)
                    }
                },
                actions = {
                    if (readyState != null) {
                        Button(
                            onClick = {
                                val file = File(readyState.pdfFilePath)
                                if (file.exists()) {
                                    PdfPrintHelper.openSystemPrintDialog(
                                        context = context,
                                        pdfFile = file,
                                        jobName = readyState.pdfFileName,
                                    )
                                    scope.launch {
                                        snackbarHostState.showSnackbar(strings.printJobLaunchedHint)
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("pdf_print_button"),
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.printSavePdfButton)
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = strings.closePreviewButton)
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            when (state) {
                PdfWorkflowState.Idle -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is PdfWorkflowState.FetchingImages -> {
                    val progress = if (state.total > 0) state.completed.toFloat() / state.total else 0f
                    CenterStatusCard {
                        CircularProgressIndicator()
                        Text(
                            text = strings.fetchingImagesProgress(state.completed, state.total),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.currentPageLabel.isNotEmpty()) {
                            Text(
                                text = "${strings.pageLabelPrefix} ${state.currentPageLabel}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                is PdfWorkflowState.GeneratingPdf -> {
                    val progress = if (state.totalPages > 0) state.processedPages.toFloat() / state.totalPages else 0f
                    CenterStatusCard {
                        CircularProgressIndicator()
                        Text(
                            text = strings.generatingPdfProgress(state.processedPages, state.totalPages),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is PdfWorkflowState.FetchFailed -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 700.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text(
                                        text = strings.pdfFetchFailuresTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = strings.pdfFetchFailuresSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(260.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(state.failures, key = { "${it.pageNumber}_${it.url}" }) { failure ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "${strings.pageLabelPrefix} ${failure.formattedPage} — ${failure.reason}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.error,
                                                )
                                                Text(
                                                    text = failure.url,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = onClose) {
                                        Text(strings.closePreviewButton)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = onRetryFailed,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("pdf_retry_failed_button"),
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(strings.retryFailedPagesButton)
                                    }
                                }
                            }
                        }
                    }
                }

                is PdfWorkflowState.Error -> {
                    val displayError = if (state.message == "NO_LINKS") strings.noLinksForPdf else "${strings.pdfGenerationFailed}: ${state.message}"
                    CenterStatusCard {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                        Text(displayError, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onClose) {
                            Text(strings.backToLibrary)
                        }
                    }
                }

                is PdfWorkflowState.Ready -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Viewer controls toolbar (Page Mode + Zoom Controls)
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = pageSizeMode == PdfPageSizeMode.A4_FIT,
                                        onClick = { onChangePageSizeMode(PdfPageSizeMode.A4_FIT) },
                                        label = { Text(strings.pageSizeA4) },
                                        modifier = Modifier.testTag("pdf_mode_a4"),
                                    )
                                    FilterChip(
                                        selected = pageSizeMode == PdfPageSizeMode.ORIGINAL_SIZE,
                                        onClick = { onChangePageSizeMode(PdfPageSizeMode.ORIGINAL_SIZE) },
                                        label = { Text(strings.pageSizeOriginal) },
                                        modifier = Modifier.testTag("pdf_mode_original"),
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.75f) },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(Icons.Default.ZoomOut, contentDescription = strings.zoomOutDesc)
                                    }
                                    Text(
                                        text = "${(zoomScale * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    IconButton(
                                        onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(2.5f) },
                                        modifier = Modifier.minimumInteractiveComponentSize(),
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = strings.zoomInDesc)
                                    }
                                }
                            }
                        }

                        EmbeddedPdfPagesViewer(
                            pdfFile = File(state.pdfFilePath),
                            zoomScale = zoomScale,
                            strings = strings,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterStatusCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 460.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun EmbeddedPdfPagesViewer(
    pdfFile: File,
    zoomScale: Float,
    strings: AppStrings,
    modifier: Modifier = Modifier,
) {
    val renderMutex = remember { Mutex() }
    var rendererHolder by remember(pdfFile.absolutePath, pdfFile.lastModified()) {
        mutableStateOf<Pair<ParcelFileDescriptor, PdfRenderer>?>(null)
    }

    DisposableEffect(pdfFile.absolutePath, pdfFile.lastModified()) {
        val pfd = runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull()
        val renderer = pfd?.let { runCatching { PdfRenderer(it) }.getOrNull() }
        if (pfd != null && renderer != null) {
            rendererHolder = pfd to renderer
        }
        onDispose {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
            rendererHolder = null
        }
    }

    val activeRenderer = rendererHolder?.second
    if (activeRenderer == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val pageCount = activeRenderer.pageCount
    val pageIndices = remember(pageCount) { (0 until pageCount).toList() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val baseWidthPx = with(density) { (maxWidth - 24.dp).coerceAtMost(820.dp).toPx() }.toInt().coerceAtLeast(320)
        val zoomedWidthDp = (maxWidth - 24.dp).coerceAtMost(820.dp) * zoomScale

        val horizontalScrollState = rememberScrollState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (zoomScale > 1.01f) Modifier.horizontalScroll(horizontalScrollState) else Modifier
                ),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(pageIndices, key = { it }) { pageIdx ->
                PdfRenderedPageItem(
                    renderer = activeRenderer,
                    mutex = renderMutex,
                    pageIndex = pageIdx,
                    totalPages = pageCount,
                    targetPixelWidth = (baseWidthPx * zoomScale).toInt().coerceIn(320, 2200),
                    pageCardWidthDp = zoomedWidthDp,
                    strings = strings,
                )
            }
        }
    }
}

@Composable
private fun PdfRenderedPageItem(
    renderer: PdfRenderer,
    mutex: Mutex,
    pageIndex: Int,
    totalPages: Int,
    targetPixelWidth: Int,
    pageCardWidthDp: androidx.compose.ui.unit.Dp,
    strings: AppStrings,
) {
    var renderedBitmap by remember(renderer, pageIndex, targetPixelWidth) { mutableStateOf<Bitmap?>(null) }
    var aspectRatio by remember(renderer, pageIndex) { mutableFloatStateOf(595f / 842f) }

    LaunchedEffect(renderer, pageIndex, targetPixelWidth) {
        val bmp = withContext(Dispatchers.IO) {
            mutex.withLock {
                runCatching {
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = page.width.toFloat() / page.height.toFloat().coerceAtLeast(1f)
                        aspectRatio = ratio
                        val renderW = targetPixelWidth
                        val renderH = (renderW / ratio).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }.getOrNull()
            }
        }
        renderedBitmap = bmp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            modifier = Modifier
                .width(pageCardWidthDp)
                .aspectRatio(aspectRatio),
            color = Color.White,
            shape = RoundedCornerShape(4.dp),
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            val bmp = renderedBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = strings.pageIndicator(pageIndex + 1, totalPages),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
        Text(
            text = strings.pageIndicator(pageIndex + 1, totalPages),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
