package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PullRequestDao {
    @Upsert
    suspend fun upsertPullRequest(pullRequest: PullRequestEntity)

    @Upsert
    suspend fun upsertPullRequests(pullRequests: List<PullRequestEntity>)

    @Query("SELECT * FROM pull_requests WHERE url = :url")
    suspend fun getPullRequest(url: String): PullRequestEntity?

    @Query("SELECT * FROM pull_requests")
    fun getAllPullRequests(): Flow<List<PullRequestEntity>>
}
