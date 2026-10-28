package com.felixbrucker.jules.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.felixbrucker.jules.network.api.model.WorkflowRunState
import java.time.Instant

@Entity(
    tableName = "dispatched_workflow_runs",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("sessionId")
    ]
)
data class DispatchedWorkflowRunEntity(
    @PrimaryKey val sessionId: String,
    val workflowRunId: Long,
    val workflowName: String,
    val state: WorkflowRunState,
    val updatedAt: Instant
)
