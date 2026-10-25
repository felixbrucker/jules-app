package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SourceDao {
    @Upsert
    suspend fun upsertSources(sources: List<SourceEntity>)

    @Insert
    suspend fun insertSources(sources: List<SourceEntity>)

    @Query("SELECT * FROM sources")
    suspend fun getAllSources(): List<SourceEntity>

    @Query("SELECT * FROM sources")
    fun getAllSourcesFlow(): kotlinx.coroutines.flow.Flow<List<SourceEntity>>

    @Query(
        """
        SELECT DISTINCT src.* FROM sources src
        INNER JOIN sessions sess ON sess.sourceId = src.id
        LEFT JOIN source_sync_metadata meta ON meta.sourceId = src.id
        WHERE sess.isArchived = 0
          AND src.github_repo_owner IS NOT NULL AND src.github_repo_owner != ''
          AND src.github_repo_repo IS NOT NULL AND src.github_repo_repo != ''
          AND (:includePrivate = 1 OR src.github_repo_isPrivate = 0 OR src.github_repo_isPrivate IS NULL)
          AND (:force = 1 OR meta.lastWorkflowSyncedAt IS NULL OR meta.lastWorkflowSyncedAt < :thresholdEpochMillis)
        """
    )
    suspend fun getSourcesNeedingWorkflowSync(
        includePrivate: Boolean,
        thresholdEpochMillis: Long,
        force: Boolean
    ): List<SourceEntity>

    @Query("SELECT id FROM sources")
    suspend fun getAllSourceIds(): List<String>

    @Query("DELETE FROM sources WHERE id NOT IN (:ids)")
    suspend fun deleteSourcesNotIn(ids: List<String>)

    @Query("DELETE FROM sources")
    suspend fun deleteAllSources()
}
