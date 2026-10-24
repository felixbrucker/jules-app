package com.felixbrucker.jules.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import retrofit2.HttpException

@JsonClass(generateAdapter = true)
data class JulesApiError(
    @Json(name = "message") val message: String,
    @Json(name = "code") val code: Int,
    @Json(name = "status") val status: String,
    @Json(name = "details") val details: List<Map<String, Any>> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class JulesErrorEnvelope(
    @Json(name = "error") val error: JulesApiError
)

class JulesApiException(
    val apiError: JulesApiError,
    val httpStatusCode: Int,
    cause: Throwable? = null
) : Exception(apiError.message, cause)

fun HttpException.parseJulesError(moshi: Moshi): JulesErrorEnvelope? {
    val errorBody = response()?.errorBody() ?: return null
    return try {
        val adapter = moshi.adapter(JulesErrorEnvelope::class.java)
        adapter.fromJson(errorBody.source())
    } catch (_: Exception) {
        null
    } finally {
        errorBody.close()
    }
}

@JsonClass(generateAdapter = true)
data class GitHubApiError(
    @Json(name = "message") val message: String,
    @Json(name = "documentation_url") val documentationUrl: String? = null
)

class GitHubApiException(
    val apiError: GitHubApiError,
    val httpStatusCode: Int,
    cause: Throwable? = null
) : Exception(apiError.message, cause)

fun HttpException.parseGitHubError(moshi: Moshi): GitHubApiError? {
    val errorBody = response()?.errorBody() ?: return null
    return try {
        val adapter = moshi.adapter(GitHubApiError::class.java)
        adapter.fromJson(errorBody.source())
    } catch (_: Exception) {
        null
    } finally {
        errorBody.close()
    }
}
