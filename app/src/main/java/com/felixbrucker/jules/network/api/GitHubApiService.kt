package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.network.api.model.GitHubPullRequestResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubApiService {

    @GET("repos/{owner}/{repo}/pulls/{pullNumber}")
    suspend fun getPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pullNumber") pullNumber: Long
    ): GitHubPullRequestResponse
}
