package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WorkflowDispatchDao {
    @Upsert
    suspend fun upsertWorkflows(workflows: List<WorkflowDispatchEntity>)

    @Query("SELECT * FROM workflow_dispatches WHERE sourceId = :sourceId")
    suspend fun getWorkflowsForSource(sourceId: String): List<WorkflowDispatchEntity>

    @Query("DELETE FROM workflow_dispatches WHERE id NOT IN (:ids)")
    suspend fun deleteWorkflowsNotIn(ids: List<String>)

    @Query("DELETE FROM workflow_dispatches")
    suspend fun deleteAllWorkflows()
}
