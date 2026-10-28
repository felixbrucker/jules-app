package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.di.NetworkModule
import com.felixbrucker.jules.network.api.model.GitHubApiException
import com.felixbrucker.jules.network.api.model.PullRequestState
import com.felixbrucker.jules.network.api.model.WorkflowRunState
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
        val gitHubApiVersionInterceptor = GitHubApiVersionInterceptor()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(gitHubApiVersionInterceptor)
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
    fun testGetPullRequestStateInvalidUrlThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                gitHubApi.getPullRequestState("https://github.com/octocat/Hello-World")
            }
        }
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
        assertEquals("2026-03-10", recordedRequest.getHeader("X-GitHub-Api-Version"))
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
    fun testValidateAuthToken() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"login": "octocat"}"""))

        gitHubApi.validateAuthToken("ghp_test123")

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/user", recordedRequest.path)
        assertEquals("Bearer ghp_test123", recordedRequest.getHeader("Authorization"))
        assertEquals("2026-03-10", recordedRequest.getHeader("X-GitHub-Api-Version"))
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
            assertEquals("Not Found", e.apiError.message)
            assertEquals("https://docs.github.com/rest", e.apiError.documentationUrl)
        }

        assertTrue("Expected GitHubApiException", exceptionThrown)
    }

    @Test
    fun testGetWorkflows() = runBlocking {
        val responseBody = """
            {
              "total_count": 1,
              "workflows": [
                {
                  "id": 12345,
                  "name": "CI",
                  "path": ".github/workflows/ci.yml",
                  "state": "active"
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val workflows = gitHubApi.getWorkflows("octocat", "Hello-World")
        assertEquals(1, workflows.size)
        assertEquals(12345L, workflows[0].id)
        assertEquals("CI", workflows[0].name)
        assertEquals(".github/workflows/ci.yml", workflows[0].path)
        assertEquals("active", workflows[0].state)
    }

    @Test
    fun testGetWorkflowContent() = runBlocking {
        val base64Content = java.util.Base64.getEncoder().encodeToString("name: CI\non: workflow_dispatch".toByteArray())
        val responseBody = """
            {
              "name": "ci.yml",
              "path": ".github/workflows/ci.yml",
              "content": "$base64Content\n",
              "encoding": "base64"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val content = gitHubApi.getWorkflowContent("octocat", "Hello-World", ".github/workflows/ci.yml")
        assertEquals("name: CI\non: workflow_dispatch", content)
    }

    @Test
    fun testDispatchWorkflow() = runBlocking {
        val responseBody = """
            {
              "workflow_run_id": 98765
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val runId = gitHubApi.dispatchWorkflow(
            owner = "octocat",
            repo = "Hello-World",
            workflowId = "12345",
            ref = "main",
            inputs = mapOf("logLevel" to "info")
        )

        assertEquals(98765L, runId)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/repos/octocat/Hello-World/actions/workflows/12345/dispatches", recordedRequest.path)
        assertEquals("POST", recordedRequest.method)
        val body = recordedRequest.body.readUtf8()
        assertTrue(body.contains("\"ref\":\"main\""))
        assertTrue(body.contains("\"inputs\":{\"logLevel\":\"info\"}"))
        assertEquals("2026-03-10", recordedRequest.getHeader("X-GitHub-Api-Version"))
    }

    @Test
    fun testGetWorkflowRun() = runBlocking {
        val responseBody = """
            {
              "status": "completed",
              "conclusion": "success"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val runResponse = gitHubApi.getWorkflowRun("octocat", "Hello-World", 98765L)
        assertEquals("completed", runResponse.status)
        assertEquals("success", runResponse.conclusion)
        assertEquals(WorkflowRunState.SUCCESS, WorkflowRunState.from(runResponse.status, runResponse.conclusion))
    }
}
