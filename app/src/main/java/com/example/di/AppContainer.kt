package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.repository.AppPreferences
import com.example.data.repository.LibraryRepository
import com.example.data.repository.PreferencesRepository
import com.example.pdf.PdfGenerator
import com.example.scanner.ImageValidator
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val libraryRepository: LibraryRepository = LibraryRepository(database.libraryDao())
    val preferencesRepository: PreferencesRepository = PreferencesRepository(appContext)

    val latestPreferences = MutableStateFlow(AppPreferences())

    private val scanPreviewDir = File(appContext.cacheDir, "scan_previews")
    private val pdfOutputDir = File(appContext.cacheDir, "generated_pdfs")
    private val pdfPageCacheDir = File(appContext.cacheDir, "pdf_pages")

    val imageValidator: ImageValidator = ImageValidator(
        previewCacheDir = scanPreviewDir,
        timeoutSecondsProvider = { latestPreferences.value.requestTimeoutSeconds },
        allowLocalServersProvider = { latestPreferences.value.allowLocalServers },
        relayEndpointProvider = { latestPreferences.value.relayEndpointUrl },
    )

    val pdfGenerator: PdfGenerator = PdfGenerator(
        imageValidator = imageValidator,
        pdfCacheDir = pdfOutputDir,
        pageCacheDir = pdfPageCacheDir,
    )

    fun clearScanPreviewCache() {
        scanPreviewDir.listFiles()?.forEach { it.delete() }
    }

    fun clearPdfCache() {
        pdfPageCacheDir.listFiles()?.forEach { it.delete() }
        pdfOutputDir.listFiles()?.forEach { it.delete() }
    }
}
