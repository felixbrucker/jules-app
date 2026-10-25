package com.felixbrucker.jules.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.felixbrucker.jules.network.api.model.WorkflowInput

@Entity(
    tableName = "workflow_dispatches",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceId"])
    ]
)
data class DispatchableWorkflowEntity(
    @PrimaryKey
    val id: String,
    val sourceId: String,
    val workflowId: Long,
    val name: String,
    val inputs: List<WorkflowInput>
)
