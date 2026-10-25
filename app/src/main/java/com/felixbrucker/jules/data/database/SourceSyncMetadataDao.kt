package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import java.time.Instant

@Dao
interface SourceSyncMetadataDao {
    @Upsert
    suspend fun upsertMetadata(metadata: SourceSyncMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(metadata: SourceSyncMetadataEntity)

    @Query("UPDATE source_sync_metadata SET lastWorkflowSyncedAt = :timestamp WHERE sourceId = :sourceId")
    suspend fun updateLastWorkflowSyncedAt(sourceId: String, timestamp: Instant)

    @Transaction
    suspend fun updateLastWorkflowSyncedAtTargeted(sourceId: String, timestamp: Instant) {
        insertIgnore(SourceSyncMetadataEntity(sourceId = sourceId))
        updateLastWorkflowSyncedAt(sourceId = sourceId, timestamp = timestamp)
    }

    @Query("SELECT * FROM source_sync_metadata WHERE sourceId = :sourceId")
    suspend fun getMetadata(sourceId: String): SourceSyncMetadataEntity?
}
