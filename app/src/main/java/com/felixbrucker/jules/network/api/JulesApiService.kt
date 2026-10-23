package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.network.api.model.ActivitiesListResponse
import com.felixbrucker.jules.network.api.model.ApprovePlanRequest
import com.felixbrucker.jules.network.api.model.ArchiveSessionRequest
import com.felixbrucker.jules.network.api.model.CreateSessionRequest
import com.felixbrucker.jules.network.api.model.SendMessageRequest
import com.felixbrucker.jules.network.api.model.Session
import com.felixbrucker.jules.network.api.model.SessionsListResponse
import com.felixbrucker.jules.network.api.model.Source
import com.felixbrucker.jules.network.api.model.SourcesListResponse
import com.felixbrucker.jules.network.api.model.UnarchiveSessionRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JulesApiService {

    @GET("sources")
    suspend fun getSources(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null
    ): SourcesListResponse

    @GET("sources/{sourceId}")
    suspend fun getSource(
        @Path(value = "sourceId", encoded = true) sourceId: String
    ): Source

    @GET("sources/github/{owner}/{repo}")
    suspend fun getGitHubSource(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Source

    @GET("sessions")
    suspend fun getSessions(
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null
    ): SessionsListResponse

    @GET("sessions")
    suspend fun validateApiKey(@Header("X-Goog-Api-Key") apiKey: String, @Query("pageSize") pageSize: Int = 1): SessionsListResponse

    @GET("sessions/{sessionId}")
    suspend fun getSession(
        @Path("sessionId") sessionId: String
    ): Session

    @POST("sessions")
    suspend fun createSession(
        @Body request: CreateSessionRequest
    ): Session

    @POST("sessions/{sessionId}:sendMessage")
    suspend fun sendMessage(
        @Path("sessionId") sessionId: String,
        @Body request: SendMessageRequest
    )

    @POST("sessions/{sessionId}:approvePlan")
    suspend fun approvePlan(
        @Path("sessionId") sessionId: String,
        @Body request: ApprovePlanRequest = ApprovePlanRequest()
    )

    @POST("sessions/{sessionId}:archive")
    suspend fun archiveSession(
        @Path("sessionId") sessionId: String,
        @Body request: ArchiveSessionRequest = ArchiveSessionRequest()
    )

    @POST("sessions/{sessionId}:unarchive")
    suspend fun unarchiveSession(
        @Path("sessionId") sessionId: String,
        @Body request: UnarchiveSessionRequest = UnarchiveSessionRequest()
    )

    @DELETE("sessions/{sessionId}")
    suspend fun deleteSession(
        @Path("sessionId") sessionId: String
    )

    @GET("sessions/{sessionId}/activities")
    suspend fun getActivities(
        @Path("sessionId") sessionId: String,
        @Query("pageSize") pageSize: Int? = null,
        @Query("pageToken") pageToken: String? = null,
        @Query("filter") filter: String? = null
    ): ActivitiesListResponse
}
