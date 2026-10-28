package com.felixbrucker.jules.network.api

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubApiVersionInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER_NAME) != null) {
            return chain.proceed(request)
        }
        val newRequest = request.newBuilder()
            .header(HEADER_NAME, API_VERSION)
            .build()
        return chain.proceed(newRequest)
    }

    companion object {
        const val HEADER_NAME = "X-GitHub-Api-Version"
        const val API_VERSION = "2026-03-10"
    }
}
