package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.di.NetworkModule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

fun JulesApi.Companion.createForTesting(
    apiKey: String,
    baseUrl: String,
    mockAppSettingsRepository: AppSettingsRepository = mockk(relaxed = true)
): JulesApi {
    every { mockAppSettingsRepository.apiKeyFlow } returns flowOf(apiKey)

    val julesAuthInterceptor = JulesAuthInterceptor(mockAppSettingsRepository)
    val userAgentInterceptor = UserAgentInterceptor()
    val loggingInterceptor = LoggingInterceptor()
    val okHttpClient = NetworkModule.provideJulesOkHttpClient(
        julesAuthInterceptor = julesAuthInterceptor,
        userAgentInterceptor = userAgentInterceptor,
        loggingInterceptor = loggingInterceptor
    )
    val moshi = NetworkModule.provideMoshi()
    val apiService = NetworkModule.provideJulesApiService(
        okHttpClient = okHttpClient,
        moshi = moshi,
        baseUrl = baseUrl
    )

    return JulesApi(apiService = apiService, moshi = moshi)
}
