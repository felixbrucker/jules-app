package com.felixbrucker.jules.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Duration
import java.time.Instant

enum class SessionState {
    @Json(name = "UNSPECIFIED") UNSPECIFIED,
    @Json(name = "QUEUED") QUEUED,
    @Json(name = "PLANNING") PLANNING,
    @Json(name = "AWAITING_PLAN_APPROVAL") AWAITING_PLAN_APPROVAL,
    @Json(name = "AWAITING_USER_FEEDBACK") AWAITING_USER_FEEDBACK,
    @Json(name = "IN_PROGRESS") IN_PROGRESS,
    @Json(name = "PAUSED") PAUSED,
    @Json(name = "FAILED") FAILED,
    @Json(name = "COMPLETED") COMPLETED
}

enum class AutomationMode {
    @Json(name = "AUTOMATION_MODE_UNSPECIFIED") AUTOMATION_MODE_UNSPECIFIED,
    @Json(name = "AUTO_CREATE_PR") AUTO_CREATE_PR
}

enum class SessionOutputType {
    UNSPECIFIED,
    PULL_REQUEST,
    CHANGE_SET
}

@JsonClass(generateAdapter = true)
data class GitHubRepoContext(
    @Json(name = "startingBranch") val startingBranch: String
)

@JsonClass(generateAdapter = true)
data class SourceContext(
    @Json(name = "source") val source: String,
    @Json(name = "githubRepoContext") val githubRepoContext: GitHubRepoContext? = null
)

@JsonClass(generateAdapter = true)
data class PullRequest(
    @Json(name = "url") val url: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "baseRef") val baseRef: String? = null,
    @Json(name = "headRef") val headRef: String? = null
)

@JsonClass(generateAdapter = true)
data class SessionOutput(
    @Json(name = "pullRequest") val pullRequest: PullRequest? = null,
    @Json(name = "changeSet") val changeSet: ChangeSet? = null
) {
    val type: SessionOutputType
        get() = when {
            pullRequest != null -> SessionOutputType.PULL_REQUEST
            changeSet != null -> SessionOutputType.CHANGE_SET
            else -> SessionOutputType.UNSPECIFIED
        }
}

@JsonClass(generateAdapter = true)
data class Session(
    @Json(name = "name") val name: String,
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String? = null,
    @Json(name = "prompt") val prompt: String,
    @Json(name = "state") val state: SessionState? = SessionState.UNSPECIFIED,
    @Json(name = "archived") val archived: Boolean? = null,
    @Json(name = "createTime") val createTime: Instant,
    @Json(name = "updateTime") val updateTime: Instant? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "sourceContext") val sourceContext: SourceContext,
    @Json(name = "outputs") val outputs: List<SessionOutput>? = emptyList()
) {
    val duration: Duration
        get() = if (updateTime != null) {
            Duration.between(createTime, updateTime)
        } else {
            Duration.between(createTime, Instant.now())
        }

    val pullRequests: List<PullRequest>
        get() = outputs?.mapNotNull { it.pullRequest } ?: emptyList()

    val changeSets: List<ChangeSet>
        get() = outputs?.mapNotNull { it.changeSet } ?: emptyList()
}

@JsonClass(generateAdapter = true)
data class CreateSessionRequest(
    @Json(name = "prompt") val prompt: String,
    @Json(name = "title") val title: String? = null,
    @Json(name = "sourceContext") val sourceContext: SourceContext? = null,
    @Json(name = "requirePlanApproval") val requirePlanApproval: Boolean? = null,
    @Json(name = "automationMode") val automationMode: AutomationMode? = AutomationMode.AUTOMATION_MODE_UNSPECIFIED
)

@JsonClass(generateAdapter = true)
data class SendMessageRequest(
    @Json(name = "prompt") val prompt: String
)

@JsonClass(generateAdapter = true)
class ApprovePlanRequest

@JsonClass(generateAdapter = true)
class ArchiveSessionRequest

@JsonClass(generateAdapter = true)
class UnarchiveSessionRequest

@JsonClass(generateAdapter = true)
data class SessionsListResponse(
    @Json(name = "sessions") val sessions: List<Session>,
    @Json(name = "nextPageToken") val nextPageToken: String? = null
)
