package com.felixbrucker.jules.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val apiKey: String?,
    val periodicSyncEnabled: Boolean,
    val syncIntervalMinutes: Int,
    val archivedSyncEnabled: Boolean,
    val dailySessionLimit: Int
)

@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val API_KEY = stringPreferencesKey("api_key")
        val GITHUB_AUTH_TOKEN = stringPreferencesKey("github_auth_token")
        val PERIODIC_SYNC_ENABLED = booleanPreferencesKey("periodic_sync_enabled")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val ARCHIVED_SYNC_ENABLED = booleanPreferencesKey("archived_sync_enabled")
        val DAILY_SESSION_LIMIT = intPreferencesKey("daily_session_limit")
    }

    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            AppSettings(
                apiKey = preferences[PreferencesKeys.API_KEY],
                periodicSyncEnabled = preferences[PreferencesKeys.PERIODIC_SYNC_ENABLED] ?: false,
                syncIntervalMinutes = preferences[PreferencesKeys.SYNC_INTERVAL_MINUTES] ?: 15,
                archivedSyncEnabled = preferences[PreferencesKeys.ARCHIVED_SYNC_ENABLED] ?: false,
                dailySessionLimit = preferences[PreferencesKeys.DAILY_SESSION_LIMIT] ?: 15
            )
        }

    val apiKeyFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.API_KEY] }

    val githubAuthTokenFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.GITHUB_AUTH_TOKEN] }

    val periodicSyncEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.PERIODIC_SYNC_ENABLED] ?: false }

    val syncIntervalMinutesFlow: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.SYNC_INTERVAL_MINUTES] ?: 15 }

    val archivedSyncEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.ARCHIVED_SYNC_ENABLED] ?: false }

    val dailySessionLimitFlow: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.DAILY_SESSION_LIMIT] ?: 15 }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.API_KEY] = apiKey
        }
    }

    suspend fun saveGithubAuthToken(token: String) {
        context.dataStore.edit { preferences ->
            if (token.isBlank()) {
                preferences.remove(PreferencesKeys.GITHUB_AUTH_TOKEN)
            } else {
                preferences[PreferencesKeys.GITHUB_AUTH_TOKEN] = token
            }
        }
    }

    suspend fun setPeriodicSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PERIODIC_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setSyncIntervalMinutes(interval: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_INTERVAL_MINUTES] = interval
        }
    }

    suspend fun setArchivedSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ARCHIVED_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setDailySessionLimit(limit: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_SESSION_LIMIT] = limit
        }
    }
}
