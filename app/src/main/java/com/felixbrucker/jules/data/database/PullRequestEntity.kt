package com.felixbrucker.jules.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.felixbrucker.jules.network.api.model.PullRequestState
import java.time.Instant

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
        Index(value = ["url"])
    ]
)
data class PullRequestEntity(
    @PrimaryKey
    val sessionId: String,
    val url: String,
    val state: PullRequestState,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Instant = Instant.now()
)
