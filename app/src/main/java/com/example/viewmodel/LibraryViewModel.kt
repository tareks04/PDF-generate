package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChapterWithStats
import com.example.data.local.PageLinkEntity
import com.example.data.repository.BackupManager
import com.example.data.repository.ParsedBackup
import com.example.data.repository.RestoreSummary
import com.example.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val _showSavedOnly = MutableStateFlow(false)
    val showSavedOnly: StateFlow<Boolean> = _showSavedOnly.asStateFlow()

    fun setShowSavedOnly(savedOnly: Boolean) {
        _showSavedOnly.value = savedOnly
    }

    fun observeChapter(chapterId: String): Flow<ChapterWithStats?> =
        container.libraryRepository.observeChapterWithStats(chapterId)

    fun observeChapterLinks(chapterId: String): Flow<List<PageLinkEntity>> =
        container.libraryRepository.observeLinksForChapter(chapterId)

    fun renameChapter(chapterId: String, newTitle: String?) {
        viewModelScope.launch {
            container.libraryRepository.renameChapter(chapterId, newTitle)
        }
    }

    fun deleteSingleLink(chapterId: String, linkId: Long) {
        viewModelScope.launch {
            container.libraryRepository.deleteSingleLink(chapterId, linkId)
        }
    }

    fun deleteSelectedLinks(chapterId: String, linkIds: List<Long>) {
        viewModelScope.launch {
            container.libraryRepository.deleteSelectedLinks(chapterId, linkIds)
        }
    }

    fun clearChapterLinks(chapterId: String) {
        viewModelScope.launch {
            container.libraryRepository.clearChapterLinks(chapterId)
        }
    }

    suspend fun generateBackupJson(): String =
        container.libraryRepository.exportBackupJson()

    fun parseBackupJson(rawJson: String): Result<ParsedBackup> =
        BackupManager.parseAndValidate(rawJson)

    suspend fun importParsedBackup(parsed: ParsedBackup, replaceExisting: Boolean): RestoreSummary =
        container.libraryRepository.importBackup(parsed, replaceExisting)

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(container) as T
        }
    }
}
