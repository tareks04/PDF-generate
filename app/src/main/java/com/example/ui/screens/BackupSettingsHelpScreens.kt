package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.repository.AppPreferences
import com.example.data.repository.ParsedBackup
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.i18n.ThemePreference
import com.example.model.PageScanStatus
import com.example.ui.components.ScanStatusBadge
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(
    strings: AppStrings,
    libraryViewModel: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var statusMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var pendingParsedBackup by remember { mutableStateOf<ParsedBackup?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = libraryViewModel.generateBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }.onSuccess {
                    statusMessage = true to strings.backupExportSuccess
                }.onFailure {
                    statusMessage = false to (it.message ?: strings.backupImportFailed)
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val raw = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("Cannot read selected file")
                }
                raw.fold(
                    onSuccess = { text ->
                        libraryViewModel.parseBackupJson(text).fold(
                            onSuccess = { parsed ->
                                pendingParsedBackup = parsed
                            },
                            onFailure = {
                                statusMessage = false to strings.backupImportFailed
                            },
                        )
                    },
                    onFailure = {
                        statusMessage = false to strings.backupImportFailed
                    },
                )
            }
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = strings.backupTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(strings.localStorageNoticeTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(strings.localStorageNoticeBody, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            statusMessage?.let { (isSuccess, msg) ->
                item {
                    Surface(
                        color = if (isSuccess) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (isSuccess) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(strings.exportBackupJsonTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(strings.exportBackupJsonDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                exportLauncher.launch("smart_page_library_backup_$stamp.json")
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("export_backup_button"),
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.exportBackupButton)
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(strings.importBackupJsonTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(strings.importBackupJsonDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("import_backup_button"),
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.importBackupButton)
                        }
                    }
                }
            }
        }
    }

    pendingParsedBackup?.let { parsed ->
        val totalLinks = parsed.chapters.sumOf { it.totalLinks }
        AlertDialog(
            onDismissRequest = { pendingParsedBackup = null },
            title = { Text(strings.importConflictTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.importConfirmBody(parsed.chapters.size, totalLinks))
                    Button(
                        onClick = {
                            scope.launch {
                                val summary = libraryViewModel.importParsedBackup(parsed, replaceExisting = false)
                                statusMessage = true to strings.backupImportSuccess(summary.chaptersAffected, summary.linksImported)
                                pendingParsedBackup = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.importConflictMerge)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val summary = libraryViewModel.importParsedBackup(parsed, replaceExisting = true)
                                statusMessage = true to strings.backupImportSuccess(summary.chaptersAffected, summary.linksImported)
                                pendingParsedBackup = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.importConflictReplace)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingParsedBackup = null }) {
                    Text(strings.cancelButton)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: AppPreferences,
    strings: AppStrings,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onSaveScannerSettings: (Int, Int, Int, String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var timeoutInput by remember(preferences.requestTimeoutSeconds) { mutableStateOf(preferences.requestTimeoutSeconds.toString()) }
    var rateLimitInput by remember(preferences.rateLimitMs) { mutableStateOf(preferences.rateLimitMs.toString()) }
    var maxPagesInput by remember(preferences.defaultMaxPages) { mutableStateOf(preferences.defaultMaxPages.toString()) }
    var relayInput by remember(preferences.relayEndpointUrl) { mutableStateOf(preferences.relayEndpointUrl) }
    var allowLocal by remember(preferences.allowLocalServers) { mutableStateOf(preferences.allowLocalServers) }
    var savedBanner by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (savedBanner) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.settingsSavedMessage, modifier = Modifier.padding(12.dp))
                    }
                }
            }

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
                        Text(strings.languageSettingTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = preferences.language == AppLanguage.BANGLA,
                                onClick = { onSelectLanguage(AppLanguage.BANGLA) },
                                label = { Text("বাংলা (Default)") },
                            )
                            FilterChip(
                                selected = preferences.language == AppLanguage.ENGLISH,
                                onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
                                label = { Text("English") },
                            )
                        }

                        Text(strings.themeSettingTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = preferences.theme == ThemePreference.SYSTEM,
                                onClick = { onSelectTheme(ThemePreference.SYSTEM) },
                                label = { Text(strings.themeSystem) },
                            )
                            FilterChip(
                                selected = preferences.theme == ThemePreference.LIGHT,
                                onClick = { onSelectTheme(ThemePreference.LIGHT) },
                                label = { Text(strings.themeLight) },
                            )
                            FilterChip(
                                selected = preferences.theme == ThemePreference.DARK,
                                onClick = { onSelectTheme(ThemePreference.DARK) },
                                label = { Text(strings.themeDark) },
                            )
                        }
                    }
                }
            }

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
                        OutlinedTextField(
                            value = timeoutInput,
                            onValueChange = { if (it.all(Char::isDigit)) timeoutInput = it },
                            label = { Text(strings.timeoutSettingLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = rateLimitInput,
                            onValueChange = { if (it.all(Char::isDigit)) rateLimitInput = it },
                            label = { Text(strings.rateLimitSettingLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = maxPagesInput,
                            onValueChange = { if (it.all(Char::isDigit)) maxPagesInput = it },
                            label = { Text(strings.defaultMaxPagesSettingLabel) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = relayInput,
                            onValueChange = { relayInput = it },
                            label = { Text(strings.relayEndpointLabel) },
                            placeholder = { Text(strings.relayEndpointHint) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.allowLocalServersTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(strings.allowLocalServersDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = allowLocal, onCheckedChange = { allowLocal = it })
                        }
                        Button(
                            onClick = {
                                onSaveScannerSettings(
                                    timeoutInput.toIntOrNull() ?: 12,
                                    rateLimitInput.toIntOrNull() ?: 200,
                                    maxPagesInput.toIntOrNull() ?: 200,
                                    relayInput,
                                    allowLocal,
                                )
                                savedBanner = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("save_settings_button"),
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.saveSettingsButton)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HelpScreen(
    strings: AppStrings,
    modifier: Modifier = Modifier,
) {
    val samplePatterns = listOf(
        "https://example.com/book/page-001.jpg",
        "https://example.com/book/page_001.jpg",
        "https://example.com/book/page001.jpg",
        "https://example.com/book/001.jpg",
        "https://example.com/viewer?page=001",
        "https://example.com/image/1/",
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = strings.helpTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            item {
                HelpSectionCard(title = strings.helpWorkflowTitle) {
                    strings.helpWorkflowSteps.forEach { step ->
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                HelpSectionCard(title = strings.helpPatternsTitle) {
                    samplePatterns.forEach { pattern ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = pattern,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            item {
                HelpSectionCard(title = strings.helpStatusTitle) {
                    PageScanStatus.entries.forEach { status ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ScanStatusBadge(status = status, strings = strings)
                            Text(
                                text = strings.statusReasonMessage(status, null, null),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                HelpSectionCard(title = strings.helpStorageTitle) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(strings.helpStorageBody, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                HelpSectionCard(title = strings.helpPdfTitle) {
                    Text(strings.helpPdfBody, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun HelpSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
