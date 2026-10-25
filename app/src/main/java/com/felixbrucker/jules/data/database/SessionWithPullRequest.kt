package com.felixbrucker.jules.data.database

import androidx.room.Embedded
import androidx.room.Relation
import com.felixbrucker.jules.network.api.model.PullRequestState

data class SessionWithPullRequest(
    @Embedded
    val session: SessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val pullRequestEntity: PullRequestEntity?,
    @Relation(
        parentColumn = "sourceId",
        entityColumn = "sourceId"
    )
    val workflows: List<WorkflowDispatchEntity> = emptyList()
) {
    val pullRequestState: PullRequestState?
        get() = pullRequestEntity?.state
}
