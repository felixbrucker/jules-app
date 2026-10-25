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
    val smartSyncEnabled: Boolean,
    val dailySessionLimit: Int,
    val feedbackNotificationsEnabled: Boolean,
    val completionNotificationsEnabled: Boolean
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
        val SMART_SYNC_ENABLED = booleanPreferencesKey("smart_sync_enabled")
        val DAILY_SESSION_LIMIT = intPreferencesKey("daily_session_limit")
        val FEEDBACK_NOTIFICATIONS_ENABLED = booleanPreferencesKey("feedback_notifications_enabled")
        val COMPLETION_NOTIFICATIONS_ENABLED = booleanPreferencesKey("completion_notifications_enabled")
        val LAST_SELECTED_SOURCE_ID = stringPreferencesKey("last_selected_source_id")
    }

    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            AppSettings(
                apiKey = preferences[PreferencesKeys.API_KEY],
                periodicSyncEnabled = preferences[PreferencesKeys.PERIODIC_SYNC_ENABLED] ?: false,
                syncIntervalMinutes = preferences[PreferencesKeys.SYNC_INTERVAL_MINUTES] ?: 15,
                smartSyncEnabled = preferences[PreferencesKeys.SMART_SYNC_ENABLED] ?: true,
                dailySessionLimit = preferences[PreferencesKeys.DAILY_SESSION_LIMIT] ?: 15,
                feedbackNotificationsEnabled = preferences[PreferencesKeys.FEEDBACK_NOTIFICATIONS_ENABLED] ?: true,
                completionNotificationsEnabled = preferences[PreferencesKeys.COMPLETION_NOTIFICATIONS_ENABLED] ?: true
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

    val smartSyncEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.SMART_SYNC_ENABLED] ?: true }

    val dailySessionLimitFlow: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.DAILY_SESSION_LIMIT] ?: 15 }

    val feedbackNotificationsEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.FEEDBACK_NOTIFICATIONS_ENABLED] ?: true }

    val completionNotificationsEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.COMPLETION_NOTIFICATIONS_ENABLED] ?: true }

    val lastSelectedSourceIdFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.LAST_SELECTED_SOURCE_ID] }

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

    suspend fun setSmartSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMART_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setDailySessionLimit(limit: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_SESSION_LIMIT] = limit
        }
    }

    suspend fun setFeedbackNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FEEDBACK_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setCompletionNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COMPLETION_NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun saveLastSelectedSourceId(sourceId: String?) {
        context.dataStore.edit { preferences ->
            if (sourceId.isNullOrBlank()) {
                preferences.remove(PreferencesKeys.LAST_SELECTED_SOURCE_ID)
            } else {
                preferences[PreferencesKeys.LAST_SELECTED_SOURCE_ID] = sourceId
            }
        }
    }
}
