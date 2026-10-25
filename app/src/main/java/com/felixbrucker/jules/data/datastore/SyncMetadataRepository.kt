package com.felixbrucker.jules.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "sync_metadata")

@Singleton
class SyncMetadataRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val LAST_SOURCES_SYNCED_AT = longPreferencesKey("last_sources_synced_at")
        val LAST_ARCHIVED_SESSIONS_SYNCED_AT = longPreferencesKey("last_archived_sessions_synced_at")
        val LAST_WORKFLOWS_SYNCED_AT = longPreferencesKey("last_workflows_synced_at")
    }

    val lastSourcesSyncedAtFlow: Flow<Instant?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_SOURCES_SYNCED_AT]?.let { Instant.ofEpochMilli(it) }
        }

    val lastArchivedSessionsSyncedAtFlow: Flow<Instant?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_ARCHIVED_SESSIONS_SYNCED_AT]?.let { Instant.ofEpochMilli(it) }
        }

    val lastWorkflowsSyncedAtFlow: Flow<Instant?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_WORKFLOWS_SYNCED_AT]?.let { Instant.ofEpochMilli(it) }
        }

    suspend fun saveLastSourcesSyncedAt(instant: Instant) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SOURCES_SYNCED_AT] = instant.toEpochMilli()
        }
    }

    suspend fun saveLastArchivedSessionsSyncedAt(instant: Instant) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_ARCHIVED_SESSIONS_SYNCED_AT] = instant.toEpochMilli()
        }
    }

    suspend fun saveLastWorkflowsSyncedAt(instant: Instant) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_WORKFLOWS_SYNCED_AT] = instant.toEpochMilli()
        }
    }
}
