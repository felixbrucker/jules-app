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
class GitHubAuthInterceptor @Inject constructor(
    appSettingsRepository: AppSettingsRepository
) : Interceptor {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _githubAuthToken: StateFlow<String?> = appSettingsRepository.githubAuthTokenFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = _githubAuthToken.value
        if (token.isNullOrBlank()) {
            return chain.proceed(request)
        }
        val existingAuthHeader = request.header("Authorization")
        if (existingAuthHeader != null) {
            return chain.proceed(request)
        }

        val requestBuilder = request.newBuilder()
            .header("Authorization", "Bearer $token")

        return chain.proceed(requestBuilder.build())
    }
}
