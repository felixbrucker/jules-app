package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DispatchedWorkflowRunDao {

    @Upsert
    suspend fun upsertWorkflowRun(run: DispatchedWorkflowRunEntity)

    @Query("SELECT * FROM dispatched_workflow_runs WHERE state IN ('QUEUED', 'IN_PROGRESS') AND updatedAt < :cutoffEpochMillis")
    suspend fun getActiveWorkflowRunsNeedingSync(cutoffEpochMillis: Long): List<DispatchedWorkflowRunEntity>
}
