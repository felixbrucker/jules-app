package com.felixbrucker.jules.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class SourceType {
    UNSPECIFIED,
    GITHUB_REPO
}

@JsonClass(generateAdapter = true)
data class GitHubBranch(
    @Json(name = "displayName") val displayName: String
)

@JsonClass(generateAdapter = true)
data class GitHubRepo(
    @Json(name = "owner") val owner: String,
    @Json(name = "repo") val repo: String,
    @Json(name = "isPrivate") val isPrivate: Boolean = false,
    @Json(name = "defaultBranch") val defaultBranch: GitHubBranch? = null,
    @Json(name = "branches") val branches: List<GitHubBranch>? = null
)

@JsonClass(generateAdapter = true)
data class Source(
    @Json(name = "name") val name: String,
    @Json(name = "id") val id: String,
    @Json(name = "githubRepo") val githubRepo: GitHubRepo? = null
) {
    val type: SourceType
        get() = if (githubRepo != null) SourceType.GITHUB_REPO else SourceType.UNSPECIFIED

    val displayName: String
        get() = githubRepo?.let { "${it.owner}/${it.repo}" } ?: id
}

@JsonClass(generateAdapter = true)
data class SourcesListResponse(
    @Json(name = "sources") val sources: List<Source>? = emptyList(),
    @Json(name = "nextPageToken") val nextPageToken: String? = null
) {
    val sourcesList: List<Source>
        get() = sources.orEmpty()
}
