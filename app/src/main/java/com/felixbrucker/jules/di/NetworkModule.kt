package com.felixbrucker.jules.di

import com.felixbrucker.jules.network.api.JulesAuthInterceptor
import com.felixbrucker.jules.network.api.GitHubApi
import com.felixbrucker.jules.network.api.GitHubApiService
import com.felixbrucker.jules.network.api.GitHubAuthInterceptor
import com.felixbrucker.jules.network.api.GitHubApiVersionInterceptor
import com.felixbrucker.jules.network.api.JulesApi
import com.felixbrucker.jules.network.api.JulesApiService
import com.felixbrucker.jules.network.api.LoggingInterceptor
import com.felixbrucker.jules.network.api.UserAgentInterceptor
import com.felixbrucker.jules.network.api.adapter.ActivityAdapter
import com.felixbrucker.jules.network.api.adapter.InstantAdapter
import com.felixbrucker.jules.network.api.model.ArtifactType
import com.felixbrucker.jules.network.api.model.AutomationMode
import com.felixbrucker.jules.network.api.model.Originator
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.network.api.model.SourceType
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.EnumJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JulesBaseUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubBaseUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JulesClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(InstantAdapter())
            .add(ActivityAdapter())
            .add(SessionState::class.java, EnumJsonAdapter.create(SessionState::class.java).withUnknownFallback(SessionState.UNSPECIFIED))
            .add(AutomationMode::class.java, EnumJsonAdapter.create(AutomationMode::class.java).withUnknownFallback(AutomationMode.AUTOMATION_MODE_UNSPECIFIED))
            .add(Originator::class.java, EnumJsonAdapter.create(Originator::class.java).withUnknownFallback(Originator.SYSTEM))
            .add(ArtifactType::class.java, EnumJsonAdapter.create(ArtifactType::class.java).withUnknownFallback(ArtifactType.UNSPECIFIED))
            .add(SourceType::class.java, EnumJsonAdapter.create(SourceType::class.java).withUnknownFallback(SourceType.UNSPECIFIED))
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    @JulesClient
    fun provideJulesOkHttpClient(
        julesAuthInterceptor: JulesAuthInterceptor,
        userAgentInterceptor: UserAgentInterceptor,
        loggingInterceptor: LoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(julesAuthInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    @GitHubClient
    fun provideGitHubOkHttpClient(
        gitHubAuthInterceptor: GitHubAuthInterceptor,
        gitHubApiVersionInterceptor: GitHubApiVersionInterceptor,
        userAgentInterceptor: UserAgentInterceptor,
        loggingInterceptor: LoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(gitHubAuthInterceptor)
            .addInterceptor(gitHubApiVersionInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideJulesApiService(
        @JulesClient okHttpClient: OkHttpClient,
        moshi: Moshi,
        @JulesBaseUrl baseUrl: String
    ): JulesApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(JulesApiService::class.java)
    }

    @Suppress("SameReturnValue")
    @Provides
    @JulesBaseUrl
    fun provideJulesBaseUrl(): String {
        return JulesApi.DEFAULT_BASE_URL
    }

    @Provides
    @Singleton
    fun provideGitHubApiService(
        @GitHubClient okHttpClient: OkHttpClient,
        moshi: Moshi,
        @GitHubBaseUrl baseUrl: String
    ): GitHubApiService {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubApiService::class.java)
    }

    @Suppress("SameReturnValue")
    @Provides
    @GitHubBaseUrl
    fun provideGitHubBaseUrl(): String {
        return GitHubApi.DEFAULT_BASE_URL
    }
}
