package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DispatchedWorkflowRunDao {

    @Upsert
    suspend fun upsertWorkflowRun(run: DispatchedWorkflowRunEntity)

    @Query("SELECT * FROM dispatched_workflow_runs WHERE sessionId = :sessionId")
    suspend fun getWorkflowRunBySessionId(sessionId: String): DispatchedWorkflowRunEntity?

    @Query("SELECT * FROM dispatched_workflow_runs WHERE sessionId = :sessionId")
    fun getWorkflowRunFlowBySessionId(sessionId: String): Flow<DispatchedWorkflowRunEntity?>

    @Query("SELECT * FROM dispatched_workflow_runs WHERE state IN ('QUEUED', 'IN_PROGRESS') AND updatedAt < :cutoffEpochMillis")
    suspend fun getActiveWorkflowRunsNeedingSync(cutoffEpochMillis: Long): List<DispatchedWorkflowRunEntity>

    @Query("DELETE FROM dispatched_workflow_runs WHERE sessionId = :sessionId")
    suspend fun deleteWorkflowRun(sessionId: String)
}
