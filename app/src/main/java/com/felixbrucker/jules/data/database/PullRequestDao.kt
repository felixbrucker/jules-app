package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PullRequestDao {
    @Upsert
    suspend fun upsertPullRequests(pullRequests: List<PullRequestEntity>)

    @Query("SELECT * FROM pull_requests WHERE sessionId = :sessionId")
    suspend fun getPullRequestForSession(sessionId: String): PullRequestEntity?

    @Query("SELECT * FROM pull_requests WHERE url = :url")
    suspend fun getPullRequestsByUrl(url: String): List<PullRequestEntity>
}
