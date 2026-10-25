package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SourceSyncMetadataDao {
    @Upsert
    suspend fun upsertMetadata(metadata: SourceSyncMetadataEntity)

    @Query("SELECT * FROM source_sync_metadata WHERE sourceId = :sourceId")
    suspend fun getMetadata(sourceId: String): SourceSyncMetadataEntity?
}
