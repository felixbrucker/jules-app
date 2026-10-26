package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityBasedStateDao {
    @Upsert
    suspend fun upsertState(entity: ActivityBasedStateEntity)

    @Query("SELECT * FROM activity_based_states WHERE sessionId = :sessionId")
    fun getActivityBasedStateFlow(sessionId: String): Flow<ActivityBasedStateEntity?>

    @Query("SELECT * FROM activity_based_states WHERE sessionId = :sessionId")
    suspend fun getActivityBasedState(sessionId: String): ActivityBasedStateEntity?
}
