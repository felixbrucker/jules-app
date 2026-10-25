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
        SELECT * FROM sources
        WHERE github_repo_owner IS NOT NULL AND github_repo_owner != ''
          AND github_repo_repo IS NOT NULL AND github_repo_repo != ''
          AND (:includePrivate = 1 OR github_repo_isPrivate = 0 OR github_repo_isPrivate IS NULL)
        """
    )
    suspend fun getUsableGithubSources(includePrivate: Boolean): List<SourceEntity>

    @Query("SELECT id FROM sources")
    suspend fun getAllSourceIds(): List<String>

    @Query("DELETE FROM sources WHERE id NOT IN (:ids)")
    suspend fun deleteSourcesNotIn(ids: List<String>)

    @Query("DELETE FROM sources")
    suspend fun deleteAllSources()
}
