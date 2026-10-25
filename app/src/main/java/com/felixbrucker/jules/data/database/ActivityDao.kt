package com.felixbrucker.jules.data.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Upsert
    suspend fun upsertActivities(entities: List<ActivityEntity>)

    @Query("SELECT * FROM activities WHERE sessionId = :sessionId ORDER BY createTime DESC")
    fun getActivitiesPagingSource(sessionId: String): PagingSource<Int, ActivityEntity>

    @Query("SELECT * FROM activities WHERE sessionId = :sessionId ORDER BY createTime ASC")
    fun getActivitiesFlow(sessionId: String): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE sessionId = :sessionId ORDER BY createTime DESC LIMIT 1")
    suspend fun getLatestActivityForSession(sessionId: String): ActivityEntity?

    @Query("SELECT * FROM activities WHERE sessionId = :sessionId AND unidiffPatch IS NOT NULL ORDER BY createTime DESC LIMIT 1")
    fun getLatestActivityWithChangesetFlow(sessionId: String): Flow<ActivityEntity?>

    @Query("DELETE FROM activities WHERE sessionId = :sessionId")
    suspend fun deleteActivitiesForSession(sessionId: String)
}
