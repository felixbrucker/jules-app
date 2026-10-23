package com.felixbrucker.jules.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import retrofit2.HttpException

@JsonClass(generateAdapter = true)
data class ApiError(
    @Json(name = "message") val message: String,
    @Json(name = "code") val code: Int,
    @Json(name = "status") val status: String,
    @Json(name = "details") val details: List<Map<String, Any>>,
)

@JsonClass(generateAdapter = true)
data class ErrorEnvelope(
    @Json(name = "error") val error: ApiError
)

class ApiException(
    val apiError: ApiError,
    val httpStatusCode: Int,
    cause: Throwable? = null
) : Exception(apiError.message, cause)

fun HttpException.parseError(moshi: Moshi): ErrorEnvelope? {
    val errorBody = response()?.errorBody() ?: return null
    return try {
        val adapter = moshi.adapter(ErrorEnvelope::class.java)
        // Parse directly from the source to avoid loading the whole string into memory twice
        adapter.fromJson(errorBody.source())
    } catch (_: Exception) {
        // Fallback if the error payload isn't valid JSON
        null
    } finally {
        errorBody.close()
    }
}
