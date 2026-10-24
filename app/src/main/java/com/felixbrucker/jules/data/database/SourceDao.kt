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

    @Query("SELECT id FROM sources")
    suspend fun getAllSourceIds(): List<String>

    @Query("SELECT COUNT(*) FROM sources")
    suspend fun getSourceCount(): Int

    @Query("DELETE FROM sources WHERE id NOT IN (:ids)")
    suspend fun deleteSourcesNotIn(ids: List<String>)

    @Query("DELETE FROM sources")
    suspend fun deleteAllSources()
}
