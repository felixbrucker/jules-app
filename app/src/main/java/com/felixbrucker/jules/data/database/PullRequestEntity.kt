package com.felixbrucker.jules.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.felixbrucker.jules.network.api.model.PullRequestState

@Entity(
    tableName = "pull_requests",
    indices = [
        Index(value = ["url"])
    ]
)
data class PullRequestEntity(
    @PrimaryKey
    val url: String,
    val state: PullRequestState
)
