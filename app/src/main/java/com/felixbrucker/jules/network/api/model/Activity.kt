package com.felixbrucker.jules.network.api.model

import com.felixbrucker.jules.network.api.util.UnidiffParser
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import java.time.Instant

enum class Originator {
    @Json(name = "user") USER,
    @Json(name = "agent") AGENT,
    @Json(name = "system") SYSTEM
}

enum class ArtifactType {
    UNSPECIFIED,
    CHANGE_SET,
    MEDIA
}

enum class FileChangeType {
    CREATED,
    MODIFIED,
    DELETED
}

@JsonClass(generateAdapter = true)
data class PlanStep(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String? = null
)

@JsonClass(generateAdapter = true)
data class Plan(
    @Json(name = "id") val id: String,
    @Json(name = "steps") val steps: List<PlanStep>
)

@JsonClass(generateAdapter = true)
data class GitPatch(
    @Json(name = "unidiffPatch") val unidiffPatch: String,
    @Json(name = "baseCommitId") val baseCommitId: String? = null,
    @Json(name = "suggestedCommitMessage") val suggestedCommitMessage: String? = null
)

@JsonClass(generateAdapter = true)
data class ChangeSet(
    @Json(name = "source") val source: String,
    @Json(name = "gitPatch") val gitPatch: GitPatch
) {
    val parsedFiles: List<ParsedFile> by lazy { UnidiffParser.parse(patch = gitPatch.unidiffPatch) }

    val summary: ChangeSetSummary by lazy {
        val files = parsedFiles
        ChangeSetSummary(
            totalFiles = files.size,
            created = files.count { it.changeType == FileChangeType.CREATED },
            modified = files.count { it.changeType == FileChangeType.MODIFIED },
            deleted = files.count { it.changeType == FileChangeType.DELETED },
            totalAdditions = files.sumOf { it.additions },
            totalDeletions = files.sumOf { it.deletions }
        )
    }
}

data class ParsedFile(
    val path: String,
    val changeType: FileChangeType,
    val additions: Int,
    val deletions: Int
)

data class ChangeSetSummary(
    val totalFiles: Int,
    val created: Int,
    val modified: Int,
    val deleted: Int,
    val totalAdditions: Int,
    val totalDeletions: Int
)

@JsonClass(generateAdapter = true)
data class Media(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
) {
    val dataUrl: String get() = "data:$mimeType;base64,$data"
}

@JsonClass(generateAdapter = true)
data class Artifact(
    @Json(name = "changeSet") val changeSet: ChangeSet? = null,
    @Json(name = "media") val media: Media? = null
) {
    val type: ArtifactType
        get() = when {
            changeSet != null -> ArtifactType.CHANGE_SET
            media != null -> ArtifactType.MEDIA
            else -> ArtifactType.UNSPECIFIED
        }
}

sealed interface Activity {
    val name: String
    val id: String
    val originator: Originator
    val createTime: Instant

    val summary: String

    data class AgentMessaged(
        override val name: String,
        override val id: String,
        override val originator: Originator,
        override val createTime: Instant,
        val agentMessage: String
    ) : Activity {
        override val summary: String get() = if (agentMessage.length > 200) "${agentMessage.take(200)}..." else agentMessage
    }

    data class UserMessaged(
        override val name: String,
        override val id: String,
        override val originator: Originator,
        override val createTime: Instant,
        val userMessage: String
    ) : Activity {
        override val summary: String get() = if (userMessage.length > 200) "${userMessage.take(200)}..." else userMessage
    }

    data class PlanGenerated(
        override val name: String,
        override val id: String,
        override val originator: Originator,
        override val createTime: Instant,
        val plan: Plan
    ) : Activity {
        override val summary: String get() = "Plan generated with ${plan.steps.size} steps"
    }

    data class PlanApproved(
        override val name: String,
        override val id: String,
        override val originator: Originator,
        override val createTime: Instant,
        val planId: String
    ) : Activity {
        override val summary: String get() = "Plan approved"
    }

    data class ProgressUpdated(
        override val name: String,
        override val id: String,
        override val originator: Originator,
        override val createTime: Instant,
        val artifacts: List<Artifact>,
        val title: String? = null,
        val description: String? = null
    ) : Activity {
        override val summary: String
            get() = when {
                title != null && description != null -> "$title: $description"
                title != null -> title
                description != null -> description
                artifacts.isNotEmpty() -> "Progress updated with ${artifacts.size} artifacts"
                else -> "Progress updated"
            }
    }

    data class SessionCompleted(
        override val name: String,
        override val id: String,
        override val originator: Originator,
        override val createTime: Instant,
        val artifacts: List<Artifact>
    ) : Activity {
        override val summary: String get() = "Session completed"
    }

    data class SessionFailed(
        override val name: String,
        override val id: String,
        override val originator: Originator,
        override val createTime: Instant,
        val artifacts: List<Artifact>,
        val reason: String
    ) : Activity {
        override val summary: String get() = if (reason.isNotEmpty()) "Session failed: $reason" else "Session failed"
    }
}

@JsonClass(generateAdapter = true)
data class PlanGeneratedDto(
    @Json(name = "plan") val plan: Plan
)

@JsonClass(generateAdapter = true)
data class PlanApprovedDto(
    @Json(name = "planId") val planId: String
)

@JsonClass(generateAdapter = true)
data class UserMessagedDto(
    @Json(name = "userMessage") val userMessage: String
)

@JsonClass(generateAdapter = true)
data class AgentMessagedDto(
    @Json(name = "agentMessage") val agentMessage: String
)

@JsonClass(generateAdapter = true)
data class ProgressUpdatedDto(
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null
)

@JsonClass(generateAdapter = true)
class SessionCompletedDto

@JsonClass(generateAdapter = true)
data class SessionFailedDto(
    @Json(name = "reason") val reason: String
)

@JsonClass(generateAdapter = true)
data class RestActivity(
    @Json(name = "name") val name: String,
    @Json(name = "id") val id: String,
    @Json(name = "originator") val originator: Originator,
    @Json(name = "createTime") val createTime: Instant,
    @Json(name = "artifacts") val artifacts: List<Artifact>? = null,
    @Json(name = "planGenerated") val planGenerated: PlanGeneratedDto? = null,
    @Json(name = "planApproved") val planApproved: PlanApprovedDto? = null,
    @Json(name = "userMessaged") val userMessaged: UserMessagedDto? = null,
    @Json(name = "agentMessaged") val agentMessaged: AgentMessagedDto? = null,
    @Json(name = "progressUpdated") val progressUpdated: ProgressUpdatedDto? = null,
    @Json(name = "sessionCompleted") val sessionCompleted: SessionCompletedDto? = null,
    @Json(name = "sessionFailed") val sessionFailed: SessionFailedDto? = null
) {
    fun toActivity(): Activity = when {
        planGenerated != null -> Activity.PlanGenerated(name, id, originator, createTime, planGenerated.plan)
        planApproved != null -> Activity.PlanApproved(name, id, originator, createTime, planApproved.planId)
        userMessaged != null -> Activity.UserMessaged(name, id, originator, createTime, userMessaged.userMessage)
        agentMessaged != null -> Activity.AgentMessaged(name, id, originator, createTime, agentMessaged.agentMessage)
        progressUpdated != null -> Activity.ProgressUpdated(name, id, originator, createTime, artifacts.orEmpty(), progressUpdated.title, progressUpdated.description)
        sessionCompleted != null -> Activity.SessionCompleted(name, id, originator, createTime, artifacts.orEmpty())
        sessionFailed != null -> Activity.SessionFailed(name, id, originator, createTime, artifacts.orEmpty(), sessionFailed.reason)
        else -> throw JsonDataException("Unknown or missing activity subtype")
    }
}

fun Activity.toRestActivity(): RestActivity = when (this) {
    is Activity.UserMessaged -> RestActivity(name = name, id = id, originator = originator, createTime = createTime, userMessaged = UserMessagedDto(userMessage))
    is Activity.AgentMessaged -> RestActivity(name = name, id = id, originator = originator, createTime = createTime, agentMessaged = AgentMessagedDto(agentMessage))
    is Activity.PlanGenerated -> RestActivity(name = name, id = id, originator = originator, createTime = createTime, planGenerated = PlanGeneratedDto(plan))
    is Activity.PlanApproved -> RestActivity(name = name, id = id, originator = originator, createTime = createTime, planApproved = PlanApprovedDto(planId))
    is Activity.ProgressUpdated -> RestActivity(name = name, id = id, originator = originator, createTime = createTime, artifacts = artifacts, progressUpdated = ProgressUpdatedDto(title, description))
    is Activity.SessionCompleted -> RestActivity(name = name, id = id, originator = originator, createTime = createTime, artifacts = artifacts, sessionCompleted = SessionCompletedDto())
    is Activity.SessionFailed -> RestActivity(name = name, id = id, originator = originator, createTime = createTime, artifacts = artifacts, sessionFailed = SessionFailedDto(reason))
}

@JsonClass(generateAdapter = true)
data class ActivitiesListResponse(
    @Json(name = "activities") val activities: List<Activity>,
    @Json(name = "nextPageToken") val nextPageToken: String? = null
)
