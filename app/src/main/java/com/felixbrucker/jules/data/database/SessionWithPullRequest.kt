package com.felixbrucker.jules.data.database

import androidx.room.Embedded
import androidx.room.Relation
import com.felixbrucker.jules.network.api.model.PullRequestState

data class SessionWithPullRequest(
    @Embedded
    val session: SessionEntity,
    @Relation(
        parentColumn = "pull_request_url",
        entityColumn = "url"
    )
    val pullRequestEntity: PullRequestEntity?
) {
    val pullRequestState: PullRequestState?
        get() = pullRequestEntity?.state
}
