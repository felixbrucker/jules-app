package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {
    @Upsert
    suspend fun upsertTask(task: ScheduledTaskEntity)

    @Query(
        """
        SELECT t.* FROM scheduled_tasks t
        WHERE :searchQuery = '%%'
           OR t.title LIKE :searchQuery
           OR t.prompt LIKE :searchQuery
           OR t.repos LIKE :searchQuery
        ORDER BY t.updatedAt DESC
        """
    )
    fun getTasks(searchQuery: String): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1")
    suspend fun getAllEnabledTasks(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): ScheduledTaskEntity?

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteTask(id: String)
}
