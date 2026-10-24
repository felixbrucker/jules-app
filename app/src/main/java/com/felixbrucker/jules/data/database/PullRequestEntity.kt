package com.felixbrucker.jules.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.felixbrucker.jules.network.api.model.PullRequestState

@Entity(
    tableName = "pull_requests",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"])
    ]
)
data class PullRequestEntity(
    @PrimaryKey
    val url: String,
    @ColumnInfo(defaultValue = "")
    val sessionId: String,
    val state: PullRequestState
)
