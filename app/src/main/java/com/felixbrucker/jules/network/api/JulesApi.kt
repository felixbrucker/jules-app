package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.network.api.adapter.ActivityAdapter
import com.felixbrucker.jules.network.api.adapter.InstantAdapter
import com.felixbrucker.jules.network.api.filter.ActivityFilter
import com.felixbrucker.jules.network.api.filter.Filter
import com.felixbrucker.jules.network.api.filter.SessionFilter
import com.felixbrucker.jules.network.api.filter.SourceFilter
import com.felixbrucker.jules.network.api.model.ActivitiesListResponse
import com.felixbrucker.jules.network.api.model.ApprovePlanRequest
import com.felixbrucker.jules.network.api.model.ArchiveSessionRequest
import com.felixbrucker.jules.network.api.model.ArtifactType
import com.felixbrucker.jules.network.api.model.AutomationMode
import com.felixbrucker.jules.network.api.model.CreateSessionRequest
import com.felixbrucker.jules.network.api.model.Originator
import com.felixbrucker.jules.network.api.model.SendMessageRequest
import com.felixbrucker.jules.network.api.model.Session
import com.felixbrucker.jules.network.api.model.SessionsListResponse
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.network.api.model.Source
import com.felixbrucker.jules.network.api.model.SourcesListResponse
import com.felixbrucker.jules.network.api.model.SourceType
import com.felixbrucker.jules.network.api.model.UnarchiveSessionRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.EnumJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class JulesApiException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

class JulesApi internal constructor(
    private val apiService: JulesApiService
) {
    constructor(apiKey: String) : this(
        apiService = createApiService(
            apiKey = apiKey,
            baseUrl = DEFAULT_BASE_URL
        )
    )

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

    suspend fun getSession(sessionId: String): Session = executeApiCall {
        apiService.getSession(sessionId = sessionId)
    }

    suspend fun createSession(request: CreateSessionRequest): Session = executeApiCall {
        apiService.createSession(request = request)
    }

    suspend fun sendMessage(sessionId: String, prompt: String) {
        executeApiCall {
            apiService.sendMessage(sessionId = sessionId, request = SendMessageRequest(prompt = prompt))
        }
    }

    suspend fun approvePlan(sessionId: String) {
        executeApiCall {
            apiService.approvePlan(sessionId = sessionId, request = ApprovePlanRequest())
        }
    }

    suspend fun archiveSession(sessionId: String) {
        executeApiCall {
            apiService.archiveSession(sessionId = sessionId, request = ArchiveSessionRequest())
        }
    }

    suspend fun unarchiveSession(sessionId: String) {
        executeApiCall {
            apiService.unarchiveSession(sessionId = sessionId, request = UnarchiveSessionRequest())
        }
    }

    suspend fun deleteSession(sessionId: String) {
        executeApiCall {
            apiService.deleteSession(sessionId = sessionId)
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
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val message = if (!errorBody.isNullOrBlank()) {
                "Jules API error (${e.code()}): $errorBody"
            } else {
                "Jules API error (${e.code()}): ${e.message()}"
            }
            throw JulesApiException(message = message, statusCode = e.code(), cause = e)
        } catch (e: Exception) {
            throw JulesApiException(message = e.message ?: "Network error during Jules API call", cause = e)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://jules.googleapis.com/v1alpha/"
        private const val API_KEY_HEADER = "X-Goog-Api-Key"

        internal fun createApiService(
            apiKey: String,
            baseUrl: String
        ): JulesApiService {
            require(apiKey.isNotBlank()) { "API Key must not be blank" }

            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            val authInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(API_KEY_HEADER, apiKey)
                    .header("Content-Type", "application/json")
                    .build()
                chain.proceed(request)
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .build()

            val moshi = Moshi.Builder()
                .add(InstantAdapter())
                .add(ActivityAdapter())
                .add(SessionState::class.java, EnumJsonAdapter.create(SessionState::class.java).withUnknownFallback(SessionState.UNSPECIFIED))
                .add(AutomationMode::class.java, EnumJsonAdapter.create(AutomationMode::class.java).withUnknownFallback(AutomationMode.AUTOMATION_MODE_UNSPECIFIED))
                .add(Originator::class.java, EnumJsonAdapter.create(Originator::class.java).withUnknownFallback(Originator.SYSTEM))
                .add(ArtifactType::class.java, EnumJsonAdapter.create(ArtifactType::class.java).withUnknownFallback(ArtifactType.UNSPECIFIED))
                .add(SourceType::class.java, EnumJsonAdapter.create(SourceType::class.java).withUnknownFallback(SourceType.UNSPECIFIED))
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(JulesApiService::class.java)
        }
    }
}
