package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    appSettingsRepository: AppSettingsRepository
) : Interceptor {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _apiKey: StateFlow<String?> = appSettingsRepository.apiKeyFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val apiKey = _apiKey.value
        if (apiKey.isNullOrBlank()) {
            return chain.proceed(request)
        }
        val existingApiKeyHeader = request.header(API_KEY_HEADER)
        if (existingApiKeyHeader != null) {
            return chain.proceed(request)
        }

        val requestBuilder = request.newBuilder()

        requestBuilder.apply {
            addHeader(API_KEY_HEADER, apiKey)
        }

        return chain.proceed(requestBuilder.build())
    }

    companion object {
        const val API_KEY_HEADER = "X-Goog-Api-Key"
    }
}
