package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoggingInterceptor @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        val (apiKey, githubToken) = getCredentials()

        val logBuffer = StringBuilder()
        val loggingInterceptor = HttpLoggingInterceptor { line ->
            if (logBuffer.isNotEmpty()) {
                logBuffer.append("\n")
            }
            logBuffer.append(line)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val response = try {
            loggingInterceptor.intercept(chain)
        } catch (e: Exception) {
            if (e !is CancellationException) {
                val sanitizedLog = sanitizeLog(logBuffer.toString(), apiKey, githubToken)
                if (sanitizedLog.isNotEmpty()) {
                    Timber.tag(TAG).e(e, sanitizedLog)
                } else {
                    Timber.tag(TAG).e(e, "Network request failed: ${request.method} $url")
                }
            }
            throw e
        }

        val sanitizedLog = sanitizeLog(logBuffer.toString(), apiKey, githubToken)
        if (sanitizedLog.isNotEmpty()) {
            if (response.isSuccessful) {
                Timber.tag(TAG).d(sanitizedLog)
            } else {
                Timber.tag(TAG).e(sanitizedLog)
            }
        }

        return response
    }

    private fun getCredentials(): Pair<String?, String?> {
        return try {
            runBlocking {
                val key = appSettingsRepository.apiKeyFlow.first()
                val token = appSettingsRepository.githubAuthTokenFlow.first()
                Pair(key, token)
            }
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    private fun sanitizeLog(rawLog: String, apiKey: String?, githubToken: String?): String {
        if (rawLog.isEmpty()) return ""
        var sanitized = rawLog
        if (!apiKey.isNullOrBlank() && apiKey.length >= 4) {
            sanitized = sanitized.replace(apiKey, "********")
        }
        if (!githubToken.isNullOrBlank() && githubToken.length >= 4) {
            sanitized = sanitized.replace(githubToken, "********")
        }
        return sanitized
    }

    companion object {
        private const val TAG = "OkHttp"
    }
}
