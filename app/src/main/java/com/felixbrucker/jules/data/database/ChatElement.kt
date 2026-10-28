package com.felixbrucker.jules.data.database

import com.felixbrucker.jules.network.api.model.ParsedFile
import com.felixbrucker.jules.network.api.model.PlanStep
import com.felixbrucker.jules.ui.sessions.ParsedDiffLine
import com.squareup.moshi.JsonClass
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class AgentState {
    Thinking,
    Working,
    Idle
}

@JsonClass(generateAdapter = true)
data class ComputedDiff(
    val fileDiffLinesMap: Map<String, List<ParsedDiffLine>>,
    val parsedFilesMap: Map<String, ParsedFile>,
    val updatedAt: Instant
)

sealed class ChatElement {
    @JsonClass(generateAdapter = true)
    data class UserMessageElement(val message: String) : ChatElement()

    @JsonClass(generateAdapter = true)
    data class AgentMessageBlockElement(val titleMarkdown: String, val detailMarkdown: String?) : ChatElement()

    @JsonClass(generateAdapter = true)
    data class AgentThoughtElement(val thoughts: String, val thoughtDurationMillis: Long) : ChatElement() {
        val thoughtDuration: Duration get() = thoughtDurationMillis.milliseconds
    }

    @JsonClass(generateAdapter = true)
    data class ChangedFilesElement(val changedFiles: List<String>) : ChatElement()

    @JsonClass(generateAdapter = true)
    data class PlanElement(val steps: List<PlanStep>) : ChatElement()

    @JsonClass(generateAdapter = true)
    class PlanApprovedElement : ChatElement() {
        override fun equals(other: Any?): Boolean = other is PlanApprovedElement
        override fun hashCode(): Int = javaClass.hashCode()
    }

    @JsonClass(generateAdapter = true)
    data class SessionFailedElement(val reason: String) : ChatElement()

    @JsonClass(generateAdapter = true)
    data class SessionCompletedElement(
        val totalAdditions: Int,
        val totalDeletions: Int,
        val suggestedCommitMessage: String?
    ) : ChatElement()
}
