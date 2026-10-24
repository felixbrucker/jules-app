package com.felixbrucker.jules.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubPullRequestResponse(
    @Json(name = "state") val state: String,
    @Json(name = "merged") val merged: Boolean
) {
    val pullRequestState: PullRequestState
        get() = when {
            merged -> PullRequestState.MERGED
            state == "closed" -> PullRequestState.CLOSED
            else -> PullRequestState.OPEN
        }
}
