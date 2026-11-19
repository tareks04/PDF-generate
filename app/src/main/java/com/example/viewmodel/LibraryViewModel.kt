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
import com.example.model.SubjectCatalog
import com.example.model.SubjectGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val _showSavedOnly = MutableStateFlow(false)
    val showSavedOnly: StateFlow<Boolean> = _showSavedOnly.asStateFlow()

    private var userOverrodeSelection = false

    private val initialPrefs = container.latestPreferences.value
    private val initialGroup = SubjectGroup.entries.find { it.id == initialPrefs.selectedLibraryGroupId }
        ?: SubjectGroup.PHYSICS
    private val initialPaperId = SubjectCatalog.papersForGroup(initialGroup)
        .find { it.id == initialPrefs.selectedLibraryPaperId }?.id
        ?: SubjectCatalog.papersForGroup(initialGroup).first().id

    private val _selectedGroup = MutableStateFlow(initialGroup)
    val selectedGroup: StateFlow<SubjectGroup> = _selectedGroup.asStateFlow()

    private val _selectedPaperId = MutableStateFlow(initialPaperId)
    val selectedPaperId: StateFlow<String> = _selectedPaperId.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = container.preferencesRepository.preferences.first()
            if (!userOverrodeSelection) {
                val restoredGroup = SubjectGroup.entries.find { it.id == prefs.selectedLibraryGroupId }
                    ?: SubjectGroup.PHYSICS
                val restoredPaperId = SubjectCatalog.papersForGroup(restoredGroup)
                    .find { it.id == prefs.selectedLibraryPaperId }?.id
                    ?: SubjectCatalog.papersForGroup(restoredGroup).first().id
                _selectedGroup.value = restoredGroup
                _selectedPaperId.value = restoredPaperId
            }
        }
    }

    fun setShowSavedOnly(savedOnly: Boolean) {
        _showSavedOnly.value = savedOnly
    }

    fun selectGroup(group: SubjectGroup) {
        userOverrodeSelection = true
        val firstPaperId = SubjectCatalog.papersForGroup(group).first().id
        _selectedGroup.value = group
        _selectedPaperId.value = firstPaperId
        viewModelScope.launch {
            container.preferencesRepository.setLibrarySelection(group.id, firstPaperId)
        }
    }

    fun selectPaper(paperId: String) {
        userOverrodeSelection = true
        val paper = SubjectCatalog.paperById(paperId) ?: return
        _selectedGroup.value = paper.group
        _selectedPaperId.value = paper.id
        viewModelScope.launch {
            container.preferencesRepository.setLibrarySelection(paper.group.id, paper.id)
        }
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
