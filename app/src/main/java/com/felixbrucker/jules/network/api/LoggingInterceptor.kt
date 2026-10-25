package com.felixbrucker.jules.network.api

import kotlinx.coroutines.CancellationException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoggingInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

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
                if (logBuffer.isNotEmpty()) {
                    Timber.tag(TAG).e(e, logBuffer.toString())
                } else {
                    Timber.tag(TAG).e(e, "Network request failed: ${request.method} $url")
                }
            }
            throw e
        }

        if (logBuffer.isNotEmpty()) {
            if (response.isSuccessful) {
                Timber.tag(TAG).d(logBuffer.toString())
            } else {
                Timber.tag(TAG).e(logBuffer.toString())
            }
        }

        return response
    }

    companion object {
        private const val TAG = "OkHttp"
    }
}
