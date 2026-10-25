package com.felixbrucker.jules.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubWorkflow(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "path") val path: String,
    @Json(name = "state") val state: String
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowsResponse(
    @Json(name = "total_count") val totalCount: Int,
    @Json(name = "workflows") val workflows: List<GitHubWorkflow>
)

@JsonClass(generateAdapter = true)
data class GitHubContentResponse(
    @Json(name = "name") val name: String,
    @Json(name = "path") val path: String,
    @Json(name = "content") val content: String? = null,
    @Json(name = "encoding") val encoding: String? = null
)

@JsonClass(generateAdapter = true)
data class GitHubWorkflowDispatchRequest(
    @Json(name = "ref") val ref: String,
    @Json(name = "inputs") val inputs: Map<String, String>? = null
)
