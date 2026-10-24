package com.felixbrucker.jules.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.felixbrucker.jules.network.api.GitHubPullRequestUrl
import com.felixbrucker.jules.network.api.model.Repo
import com.felixbrucker.jules.network.api.model.Session
import com.felixbrucker.jules.network.api.model.SessionState
import java.time.Duration
import java.time.Instant

data class EmbeddedPullRequest(
    val url: String,
    val title: String,
    val description: String,
    val baseRef: String?,
    val headRef: String?
) {
    val number: Long? by lazy {
        GitHubPullRequestUrl.parse(url)?.pullNumber
    }
}

data class EmbeddedChangeSet(
    val source: String,
    val unidiffPatch: String,
    val totalAdditions: Int,
    val totalDeletions: Int
)

@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["pull_request_url"], unique = true)
    ]
)
data class SessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val title: String?,
    val prompt: String,
    val state: SessionState,
    val isArchived: Boolean,
    val createTime: Instant,
    val updateTime: Instant?,
    val url: String?,
    @Embedded(prefix = "repo_")
    val repo: Repo?,
    @Embedded(prefix = "pull_request_")
    val pullRequest: EmbeddedPullRequest?,
    @Embedded(prefix = "change_set_")
    val changeSet: EmbeddedChangeSet?
) {
    val duration: Duration
        get() = if (updateTime != null) {
            Duration.between(createTime, updateTime)
        } else {
            Duration.between(createTime, Instant.now())
        }
}

fun Session.toEntity(): SessionEntity {
    val pullRequest = pullRequests.firstOrNull()
    val changeSet = changeSets.firstOrNull()
    val isArchived = archived == true

    return SessionEntity(
        id = id,
        name = name,
        title = title,
        prompt = prompt,
        state = state ?: SessionState.UNSPECIFIED,
        isArchived = isArchived,
        createTime = createTime,
        updateTime = updateTime,
        url = url,
        repo = sourceContext.repo,
        pullRequest = pullRequest?.let {
            EmbeddedPullRequest(
                url = it.url,
                title = it.title,
                description = it.description,
                baseRef = it.baseRef,
                headRef = it.headRef
            )
        },
        changeSet = changeSet?.let {
            EmbeddedChangeSet(
                source = it.source,
                unidiffPatch = it.gitPatch.unidiffPatch,
                totalAdditions = it.summary.totalAdditions,
                totalDeletions = it.summary.totalDeletions
            )
        }
    )
}
