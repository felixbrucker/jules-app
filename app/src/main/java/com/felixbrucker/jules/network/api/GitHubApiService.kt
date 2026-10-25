package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.network.api.model.GitHubContentResponse
import com.felixbrucker.jules.network.api.model.GitHubPullRequestResponse
import com.felixbrucker.jules.network.api.model.GitHubWorkflowDispatchRequest
import com.felixbrucker.jules.network.api.model.GitHubWorkflowsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GitHubApiService {

    @GET("user")
    suspend fun getUser(
        @Header("Authorization") authHeader: String? = null
    ): Map<String, Any?>

    @GET("repos/{owner}/{repo}/pulls/{pullNumber}")
    suspend fun getPullRequest(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("pullNumber") pullNumber: Long
    ): GitHubPullRequestResponse

    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun getWorkflows(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubWorkflowsResponse

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getRepoContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String
    ): GitHubContentResponse

    @POST("repos/{owner}/{repo}/actions/workflows/{workflowId}/dispatches")
    suspend fun dispatchWorkflow(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflowId") workflowId: String,
        @Body request: GitHubWorkflowDispatchRequest
    ): Response<Unit>
}
