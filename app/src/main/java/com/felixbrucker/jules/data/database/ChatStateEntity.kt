package com.felixbrucker.jules.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_states",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class ChatStateEntity(
    @PrimaryKey val sessionId: String,
    val agentState: AgentState = AgentState.Idle,
    @ColumnInfo(defaultValue = "0") val hasApprovedPlan: Boolean = false
)
