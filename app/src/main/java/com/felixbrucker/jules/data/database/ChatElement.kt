package com.felixbrucker.jules.data.database

import com.felixbrucker.jules.network.api.model.PlanStep
import com.squareup.moshi.JsonClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class AgentState {
    Thinking,
    Working,
    Idle
}

data class ComputedDiff(
    val fileDiffLinesMap: Map<String, List<com.felixbrucker.jules.ui.sessions.ParsedDiffLine>>,
    val parsedFilesMap: Map<String, com.felixbrucker.jules.network.api.model.ParsedFile>,
    val updatedAt: java.time.Instant
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
    class SessionCompletedElement : ChatElement() {
        override fun equals(other: Any?): Boolean = other is SessionCompletedElement
        override fun hashCode(): Int = javaClass.hashCode()
    }
}
