package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoggingInterceptor @Inject constructor(
    appSettingsRepository: AppSettingsRepository
) : Interceptor {

    private val externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var apiKey: String? = null

    @Volatile
    private var githubToken: String? = null

    init {
        externalScope.launch {
            appSettingsRepository.apiKeyFlow.collect { key ->
                apiKey = key
            }
        }
        externalScope.launch {
            appSettingsRepository.githubAuthTokenFlow.collect { token ->
                githubToken = token
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        val currentApiKey = apiKey
        val currentGithubToken = githubToken

        val logBuffer = StringBuilder()
        var truncated = false
        val loggingInterceptor = HttpLoggingInterceptor { line ->
            if (logBuffer.length < MAX_LOG_LENGTH) {
                if (logBuffer.isNotEmpty()) {
                    logBuffer.append("\n")
                }
                logBuffer.append(line)
            } else if (!truncated) {
                logBuffer.append("\n... [truncated]")
                truncated = true
            }
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val response = try {
            loggingInterceptor.intercept(chain)
        } catch (e: Exception) {
            if (e !is CancellationException) {
                val sanitizedLog = sanitizeLog(logBuffer.toString(), currentApiKey, currentGithubToken)
                if (sanitizedLog.isNotEmpty()) {
                    Timber.tag(TAG).e(e, sanitizedLog)
                } else {
                    Timber.tag(TAG).e(e, "Network request failed: ${request.method} $url")
                }
            }
            throw e
        }

        val sanitizedLog = sanitizeLog(logBuffer.toString(), currentApiKey, currentGithubToken)
        if (sanitizedLog.isNotEmpty()) {
            if (response.isSuccessful) {
                Timber.tag(TAG).d(sanitizedLog)
            } else {
                Timber.tag(TAG).e(sanitizedLog)
            }
        }

        return response
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
        private const val MAX_LOG_LENGTH = 2000
    }
}
