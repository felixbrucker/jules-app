package com.felixbrucker.jules.data.database

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
            parentColumns = ["pull_request_url"],
            childColumns = ["url"],
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
    val url: String,
    val state: PullRequestState
)
