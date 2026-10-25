package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DispatchableWorkflowDao {
    @Upsert
    suspend fun upsertWorkflows(workflows: List<DispatchableWorkflowEntity>)

    @Query("SELECT * FROM workflow_dispatches WHERE sourceId = :sourceId")
    suspend fun getWorkflowsForSource(sourceId: String): List<DispatchableWorkflowEntity>

    @Query("DELETE FROM workflow_dispatches")
    suspend fun deleteAllWorkflows()
}
