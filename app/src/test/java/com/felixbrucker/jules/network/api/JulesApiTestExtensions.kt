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

    val authInterceptor = AuthInterceptor(mockAppSettingsRepository)
    val userAgentInterceptor = UserAgentInterceptor()
    val okHttpClient = NetworkModule.provideJulesOkHttpClient(authInterceptor, userAgentInterceptor)
    val moshi = NetworkModule.provideMoshi()
    val apiService = NetworkModule.provideJulesApiService(
        okHttpClient = okHttpClient,
        moshi = moshi,
        baseUrl = baseUrl
    )

    return JulesApi(apiService = apiService, moshi = moshi)
}
