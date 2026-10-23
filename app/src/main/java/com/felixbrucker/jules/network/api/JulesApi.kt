package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.network.api.filter.ActivityFilter
import com.felixbrucker.jules.network.api.filter.Filter
import com.felixbrucker.jules.network.api.filter.SessionFilter
import com.felixbrucker.jules.network.api.filter.SourceFilter
import com.felixbrucker.jules.network.api.model.ActivitiesListResponse
import com.felixbrucker.jules.network.api.model.ApiException
import com.felixbrucker.jules.network.api.model.CreateSessionRequest
import com.felixbrucker.jules.network.api.model.SendMessageRequest
import com.felixbrucker.jules.network.api.model.Session
import com.felixbrucker.jules.network.api.model.SessionsListResponse
import com.felixbrucker.jules.network.api.model.Source
import com.felixbrucker.jules.network.api.model.SourcesListResponse
import com.felixbrucker.jules.network.api.model.parseError
import com.squareup.moshi.Moshi
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JulesApi @Inject constructor(
    private val apiService: JulesApiService,
    private val moshi: Moshi,
) {
    suspend fun getSources(
        filter: Filter<SourceFilter>? = null,
        pageSize: Int? = null,
        pageToken: String? = null
    ): SourcesListResponse {
        pageSize?.let { require(it in 1..100) { "pageSize must be between 1 and 100 inclusive" } }
        return executeApiCall {
            apiService.getSources(
                pageSize = pageSize,
                pageToken = pageToken,
                filter = filter?.toAip160String()
            )
        }
    }

    suspend fun getSource(sourceId: String): Source = executeApiCall {
        apiService.getSource(sourceId = sourceId)
    }

    suspend fun getGitHubSource(owner: String, repo: String): Source = executeApiCall {
        apiService.getGitHubSource(owner = owner, repo = repo)
    }

    suspend fun getSessions(
        filter: Filter<SessionFilter>? = null,
        pageSize: Int? = null,
        pageToken: String? = null
    ): SessionsListResponse {
        pageSize?.let { require(it in 1..100) { "pageSize must be between 1 and 100 inclusive" } }
        return executeApiCall {
            apiService.getSessions(
                pageSize = pageSize,
                pageToken = pageToken,
                filter = filter?.toAip160String()
            )
        }
    }

    suspend fun getArchivedSessions(
        pageSize: Int? = null,
        pageToken: String? = null
    ): SessionsListResponse {
        pageSize?.let { require(it in 1..100) { "pageSize must be between 1 and 100 inclusive" } }
        return getSessions(
            filter = SessionFilter.ByArchived(isArchived = true),
            pageSize = pageSize,
            pageToken = pageToken
        )
    }

    suspend fun validateApiKey(apiKey: String) = executeApiCall {
        apiService.validateApiKey(apiKey = apiKey, pageSize = 1)
    }

    suspend fun getSession(sessionId: String): Session = executeApiCall {
        apiService.getSession(sessionId = sessionId)
    }

    suspend fun createSession(request: CreateSessionRequest): Session = executeApiCall {
        apiService.createSession(request = request)
    }

    suspend fun sendMessage(sessionId: String, prompt: String) {
        executeApiCall {
            apiService.sendMessage(
                sessionId = sessionId,
                request = SendMessageRequest(prompt = prompt)
            )
        }
    }

    suspend fun approvePlan(sessionId: String) {
        executeApiCall {
            apiService.approvePlan(sessionId = sessionId)
        }
    }

    suspend fun archiveSession(sessionId: String) {
        executeApiCall {
            apiService.archiveSession(sessionId = sessionId)
        }
    }

    suspend fun unarchiveSession(sessionId: String) {
        executeApiCall {
            apiService.unarchiveSession(sessionId = sessionId)
        }
    }

    suspend fun deleteSession(sessionId: String) {
        try {
            executeApiCall { apiService.deleteSession(sessionId = sessionId) }
        } catch (e: ApiException) {
            // 404, already deleted, nothing to do
            if (e.httpStatusCode != 404) {
                throw e
            }
        }
    }

    suspend fun getActivities(
        sessionId: String,
        filter: Filter<ActivityFilter>? = null,
        pageSize: Int? = null,
        pageToken: String? = null
    ): ActivitiesListResponse {
        pageSize?.let { require(it in 1..100) { "pageSize must be between 1 and 100 inclusive" } }
        return executeApiCall {
            apiService.getActivities(
                sessionId = sessionId,
                pageSize = pageSize,
                pageToken = pageToken,
                filter = filter?.toAip160String()
            )
        }
    }

    private inline fun <T> executeApiCall(block: () -> T): T {
        return try {
            block()
        } catch (e: HttpException) {
            val errorEnvelope = e.parseError(moshi = moshi) ?: throw e

            throw ApiException(
                apiError = errorEnvelope.error,
                httpStatusCode = e.code(),
                cause = e
            )
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://jules.googleapis.com/v1alpha/"
    }
}
