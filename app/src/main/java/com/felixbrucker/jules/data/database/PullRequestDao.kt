package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Upsert

@Dao
interface PullRequestDao {
    @Upsert
    suspend fun upsertPullRequests(pullRequests: List<PullRequestEntity>)
}
