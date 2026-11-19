package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.i18n.AppLanguage
import com.example.i18n.ThemePreference
import com.example.model.SubjectCatalog
import com.example.model.SubjectGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_page_library_prefs")

data class AppPreferences(
    val language: AppLanguage = AppLanguage.BANGLA,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val requestTimeoutSeconds: Int = 12,
    val rateLimitMs: Int = 200,
    val defaultMaxPages: Int = 200,
    val relayEndpointUrl: String = "",
    val allowLocalServers: Boolean = false,
    val selectedLibraryGroupId: String = SubjectGroup.PHYSICS.id,
    val selectedLibraryPaperId: String = SubjectCatalog.papers.first().id,
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val THEME = stringPreferencesKey("theme_mode")
        val TIMEOUT_SEC = intPreferencesKey("request_timeout_sec")
        val RATE_LIMIT_MS = intPreferencesKey("rate_limit_ms")
        val DEFAULT_MAX_PAGES = intPreferencesKey("default_max_pages")
        val RELAY_URL = stringPreferencesKey("relay_endpoint_url")
        val ALLOW_LOCAL = booleanPreferencesKey("allow_local_servers")
        val LIBRARY_GROUP_ID = stringPreferencesKey("library_group_id")
        val LIBRARY_PAPER_ID = stringPreferencesKey("library_paper_id")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val rawGroupId = prefs[Keys.LIBRARY_GROUP_ID]
        val group = SubjectGroup.entries.find { it.id == rawGroupId } ?: SubjectGroup.PHYSICS
        val papersInGroup = SubjectCatalog.papersForGroup(group)
        val rawPaperId = prefs[Keys.LIBRARY_PAPER_ID]
        val validPaperId = papersInGroup.find { it.id == rawPaperId }?.id ?: papersInGroup.first().id

        AppPreferences(
            language = prefs[Keys.LANGUAGE]?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: AppLanguage.BANGLA,
            theme = prefs[Keys.THEME]?.let {
                runCatching { ThemePreference.valueOf(it) }.getOrNull()
            } ?: ThemePreference.SYSTEM,
            requestTimeoutSeconds = (prefs[Keys.TIMEOUT_SEC] ?: 12).coerceIn(3, 60),
            rateLimitMs = (prefs[Keys.RATE_LIMIT_MS] ?: 200).coerceIn(0, 5000),
            defaultMaxPages = (prefs[Keys.DEFAULT_MAX_PAGES] ?: 200).coerceIn(1, 2000),
            relayEndpointUrl = prefs[Keys.RELAY_URL] ?: "",
            allowLocalServers = prefs[Keys.ALLOW_LOCAL] ?: false,
            selectedLibraryGroupId = group.id,
            selectedLibraryPaperId = validPaperId,
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setLibrarySelection(groupId: String, paperId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LIBRARY_GROUP_ID] = groupId
            prefs[Keys.LIBRARY_PAPER_ID] = paperId
        }
    }

    suspend fun updateScannerSettings(
        timeoutSeconds: Int,
        rateLimitMs: Int,
        defaultMaxPages: Int,
        relayEndpointUrl: String,
        allowLocalServers: Boolean,
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TIMEOUT_SEC] = timeoutSeconds.coerceIn(3, 60)
            prefs[Keys.RATE_LIMIT_MS] = rateLimitMs.coerceIn(0, 5000)
            prefs[Keys.DEFAULT_MAX_PAGES] = defaultMaxPages.coerceIn(1, 2000)
            prefs[Keys.RELAY_URL] = relayEndpointUrl.trim()
            prefs[Keys.ALLOW_LOCAL] = allowLocalServers
        }
    }
}
