package com.felixbrucker.jules.data.database

import androidx.room.TypeConverter
import com.felixbrucker.jules.network.api.model.GitHubBranch
import com.felixbrucker.jules.network.api.model.PullRequestState
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.network.api.model.WorkflowInput
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.time.Instant

class Converters {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
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
}
