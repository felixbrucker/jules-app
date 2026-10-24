package com.felixbrucker.jules.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubPullRequestResponse(
    @Json(name = "state") val state: String,
    @Json(name = "merged") val merged: Boolean? = false
) {
    val pullRequestState: PullRequestState
        get() = when {
            merged == true -> PullRequestState.MERGED
            state.equals("closed", ignoreCase = true) -> PullRequestState.CLOSED
            else -> PullRequestState.OPEN
        }
}
