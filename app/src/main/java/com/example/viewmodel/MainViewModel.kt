package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChapterWithStats
import com.example.data.repository.AppPreferences
import com.example.di.AppContainer
import com.example.i18n.AppLanguage
import com.example.i18n.AppStrings
import com.example.i18n.StringsProvider
import com.example.i18n.ThemePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardMetrics(
    val savedChaptersCount: Int = 0,
    val totalSavedPages: Int = 0,
    val activeSubjectsCount: Int = 0,
    val recentChapters: List<ChapterWithStats> = emptyList(),
)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    init {
        viewModelScope.launch {
            container.libraryRepository.ensureSeeded()
        }
    }

    val preferences: StateFlow<AppPreferences> = container.preferencesRepository.preferences
        .onEach { container.latestPreferences.value = it }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppPreferences(),
        )

    val strings: StateFlow<AppStrings> = preferences
        .map { StringsProvider.get(it.language) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = StringsProvider.get(AppLanguage.BANGLA),
        )

    val allChapters: StateFlow<List<ChapterWithStats>> =
        container.libraryRepository.allChaptersWithStats.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    val dashboardMetrics: StateFlow<DashboardMetrics> = allChapters
        .map { chapters ->
            val populated = chapters.filter { it.pageCount > 0 }
            DashboardMetrics(
                savedChaptersCount = populated.size,
                totalSavedPages = populated.sumOf { it.pageCount },
                activeSubjectsCount = populated.map { it.subjectGroupId }.distinct().size,
                recentChapters = populated
                    .sortedByDescending { it.lastUpdatedAt }
                    .take(5),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardMetrics(),
        )

    fun toggleLanguage() {
        val next = if (preferences.value.language == AppLanguage.BANGLA) {
            AppLanguage.ENGLISH
        } else {
            AppLanguage.BANGLA
        }
        viewModelScope.launch {
            container.preferencesRepository.setLanguage(next)
        }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            container.preferencesRepository.setLanguage(language)
        }
    }

    fun toggleQuickTheme(isCurrentlyDark: Boolean) {
        val next = if (isCurrentlyDark) ThemePreference.LIGHT else ThemePreference.DARK
        viewModelScope.launch {
            container.preferencesRepository.setTheme(next)
        }
    }

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch {
            container.preferencesRepository.setTheme(theme)
        }
    }

    fun saveScannerSettings(
        timeoutSeconds: Int,
        rateLimitMs: Int,
        defaultMaxPages: Int,
        relayEndpointUrl: String,
        allowLocalServers: Boolean,
    ) {
        viewModelScope.launch {
            container.preferencesRepository.updateScannerSettings(
                timeoutSeconds = timeoutSeconds,
                rateLimitMs = rateLimitMs,
                defaultMaxPages = defaultMaxPages,
                relayEndpointUrl = relayEndpointUrl,
                allowLocalServers = allowLocalServers,
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}
