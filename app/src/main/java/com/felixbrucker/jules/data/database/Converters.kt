package com.felixbrucker.jules.data.database

import androidx.room.TypeConverter
import com.felixbrucker.jules.network.api.model.GitHubBranch
import com.felixbrucker.jules.network.api.model.PullRequestState
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.network.api.model.WorkflowInput
import com.felixbrucker.jules.network.api.adapter.InstantAdapter
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant

class Converters {
    private val moshi = Moshi.Builder()
        .add(InstantAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val chatElementAdapter: JsonAdapter<ChatElement> = Moshi.Builder()
        .add(
            PolymorphicJsonAdapterFactory.of(ChatElement::class.java, "type")
                .withSubtype(ChatElement.UserMessageElement::class.java, "UserMessage")
                .withSubtype(ChatElement.AgentMessageBlockElement::class.java, "AgentMessageBlock")
                .withSubtype(ChatElement.AgentThoughtElement::class.java, "AgentThought")
                .withSubtype(ChatElement.ChangedFilesElement::class.java, "ChangedFiles")
                .withSubtype(ChatElement.PlanElement::class.java, "Plan")
                .withSubtype(ChatElement.PlanApprovedElement::class.java, "PlanApproved")
                .withSubtype(ChatElement.SessionFailedElement::class.java, "SessionFailed")
                .withSubtype(ChatElement.SessionCompletedElement::class.java, "SessionCompleted")
        )
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter(ChatElement::class.java)

    private val computedDiffAdapter: JsonAdapter<ComputedDiff> = moshi.adapter(ComputedDiff::class.java)
    private val branchListType = Types.newParameterizedType(List::class.java, GitHubBranch::class.java)
    private val branchListAdapter: JsonAdapter<List<GitHubBranch>> = moshi.adapter(branchListType)

    private val workflowInputListType = Types.newParameterizedType(List::class.java, WorkflowInput::class.java)
    private val workflowInputListAdapter: JsonAdapter<List<WorkflowInput>> = moshi.adapter(workflowInputListType)
    @TypeConverter
    fun fromSessionState(state: SessionState): String = state.name

    @TypeConverter
    fun toSessionState(value: String): SessionState = SessionState.valueOf(value)

    @TypeConverter
    fun fromPullRequestState(state: PullRequestState): String = state.name

    @TypeConverter
    fun toPullRequestState(value: String): PullRequestState = PullRequestState.valueOf(value)

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromGitHubBranchList(branches: List<GitHubBranch>?): String? {
        if (branches == null) return null
        return branchListAdapter.toJson(branches)
    }

    @TypeConverter
    fun toGitHubBranchList(value: String?): List<GitHubBranch>? {
        if (value.isNullOrBlank()) return null
        return branchListAdapter.fromJson(value)
    }

    @TypeConverter
    fun fromWorkflowInputList(inputs: List<WorkflowInput>?): String? {
        if (inputs == null) return null
        return workflowInputListAdapter.toJson(inputs)
    }

    @TypeConverter
    fun toWorkflowInputList(value: String?): List<WorkflowInput>? {
        if (value.isNullOrBlank()) return null
        return workflowInputListAdapter.fromJson(value)
    }

    @TypeConverter
    fun fromAgentState(state: AgentState): String = state.name

    @TypeConverter
    fun toAgentState(value: String): AgentState = AgentState.valueOf(value)

    @TypeConverter
    fun fromChatElement(element: ChatElement?): String? {
        if (element == null) return null
        return chatElementAdapter.toJson(element)
    }

    @TypeConverter
    fun toChatElement(value: String?): ChatElement? {
        if (value.isNullOrBlank()) return null
        return chatElementAdapter.fromJson(value)
    }

    @TypeConverter
    fun fromComputedDiff(diff: ComputedDiff?): String? {
        if (diff == null) return null
        return computedDiffAdapter.toJson(diff)
    }

    @TypeConverter
    fun toComputedDiff(value: String?): ComputedDiff? {
        if (value.isNullOrBlank()) return null
        return computedDiffAdapter.fromJson(value)
    }
}
