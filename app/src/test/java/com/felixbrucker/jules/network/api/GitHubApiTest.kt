package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.di.NetworkModule
import com.felixbrucker.jules.network.api.model.GitHubApiException
import com.felixbrucker.jules.network.api.model.PullRequestState
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var gitHubApi: GitHubApi

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/").toString()

        val userAgentInterceptor = UserAgentInterceptor()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .build()
        val moshi = NetworkModule.provideMoshi()
        val apiService = NetworkModule.provideGitHubApiService(
            okHttpClient = okHttpClient,
            moshi = moshi,
            baseUrl = baseUrl
        )

        gitHubApi = GitHubApi(apiService = apiService, moshi = moshi)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testParseGitHubPullRequestUrl() {
        val validUrl = "https://github.com/octocat/Hello-World/pull/1347"
        val parsed = GitHubPullRequestUrl.parse(validUrl)
        assertNotNull(parsed)
        assertEquals("octocat", parsed?.owner)
        assertEquals("Hello-World", parsed?.repo)
        assertEquals(1347L, parsed?.pullNumber)

        val invalidUrl = "https://github.com/octocat/Hello-World"
        assertNull(GitHubPullRequestUrl.parse(invalidUrl))
    }

    @Test
    fun testGetPullRequestStateOpen() = runBlocking {
        val responseBody = """
            {
              "state": "open",
              "merged": false
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val state = gitHubApi.getPullRequestState(
            owner = "octocat",
            repo = "Hello-World",
            pullNumber = 1347
        )

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/repos/octocat/Hello-World/pulls/1347", recordedRequest.path)
        assertTrue(
            "User-Agent header missing or incorrect",
            recordedRequest.getHeader("User-Agent")?.startsWith("Jules/") == true
        )
        assertEquals(PullRequestState.OPEN, state)
    }

    @Test
    fun testGetPullRequestStateMerged() = runBlocking {
        val responseBody = """
            {
              "state": "closed",
              "merged": true
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val state = gitHubApi.getPullRequestState("https://github.com/octocat/Hello-World/pull/1347")
        assertEquals(PullRequestState.MERGED, state)
    }

    @Test
    fun testGetPullRequestStateClosed() = runBlocking {
        val responseBody = """
            {
              "state": "closed",
              "merged": false
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val state = gitHubApi.getPullRequestState("https://github.com/octocat/Hello-World/pull/1347")
        assertEquals(PullRequestState.CLOSED, state)
    }

    @Test
    fun testGitHubApiExceptionHandling() = runBlocking {
        val errorBody = """
            {
              "message": "Not Found",
              "documentation_url": "https://docs.github.com/rest"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody(errorBody))

        var exceptionThrown = false
        try {
            gitHubApi.getPullRequestState("https://github.com/octocat/Hello-World/pull/9999")
        } catch (e: GitHubApiException) {
            exceptionThrown = true
            assertEquals(404, e.httpStatusCode)
            assertEquals("Not Found", e.apiError?.message)
            assertEquals("https://docs.github.com/rest", e.apiError?.documentationUrl)
        }

        assertTrue("Expected GitHubApiException", exceptionThrown)
    }
}
