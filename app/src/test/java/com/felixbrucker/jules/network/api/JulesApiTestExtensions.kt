package com.felixbrucker.jules.network.api

fun JulesApi.Companion.createForTesting(
    apiKey: String,
    baseUrl: String
): JulesApi {
    val service = createApiService(
        apiKey = apiKey,
        baseUrl = baseUrl
    )
    return JulesApi(apiService = service)
}
