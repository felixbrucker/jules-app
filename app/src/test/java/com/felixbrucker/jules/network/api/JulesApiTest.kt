package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.network.api.filter.ActivityFilter
import com.felixbrucker.jules.network.api.filter.SourceFilter
import com.felixbrucker.jules.network.api.filter.and
import com.felixbrucker.jules.network.api.filter.or
import com.felixbrucker.jules.network.api.model.Activity
import com.felixbrucker.jules.network.api.model.ArtifactType
import com.felixbrucker.jules.network.api.model.AutomationMode
import com.felixbrucker.jules.network.api.model.CreateSessionRequest
import com.felixbrucker.jules.network.api.model.FileChangeType
import com.felixbrucker.jules.network.api.model.GitHubRepoContext
import com.felixbrucker.jules.network.api.model.Originator
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.network.api.model.SourceContext
import com.felixbrucker.jules.network.api.model.SourceType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder
import java.time.Instant

class JulesApiTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var julesApi: JulesApi

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/v1alpha/").toString()
        julesApi = JulesApi.createForTesting(apiKey = "test-api-key-123", baseUrl = baseUrl)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testHeadersAndGetSourcesWithFilter() = runBlocking {
        val responseBody = """
            {
              "sources": [
                {
                  "name": "sources/github/owner/repo",
                  "id": "github/owner/repo",
                  "githubRepo": {
                    "owner": "owner",
                    "repo": "repo",
                    "isPrivate": true,
                    "defaultBranch": { "displayName": "main" },
                    "branches": [{ "displayName": "main" }, { "displayName": "dev" }]
                  }
                }
              ],
              "nextPageToken": "token-123"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val result = julesApi.getSources(
            filter = SourceFilter.ByName(name = "sources/github/owner/repo"),
            pageSize = 10
        )

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("X-Goog-Api-Key header mismatch", "test-api-key-123", recordedRequest.getHeader("X-Goog-Api-Key"))
        assertTrue("URL path mismatch", recordedRequest.path?.contains("/v1alpha/sources") == true)
        assertTrue("Query param pageSize mismatch", recordedRequest.path?.contains("pageSize=10") == true)
        assertTrue("Query param filter name mismatch", recordedRequest.path?.contains("filter=name") == true)

        assertEquals("Sources size mismatch", 1, result.sources.size)
        val source = result.sources.first()
        assertEquals("github/owner/repo", source.id)
        assertEquals(SourceType.GITHUB_REPO, source.type)
        assertEquals("owner/repo", source.displayName)
        assertNotNull("GitHub repo null", source.githubRepo)
        assertEquals("owner", source.githubRepo?.owner)
        assertEquals("main", source.githubRepo?.defaultBranch?.displayName)
        assertEquals(listOf("main", "dev"), source.githubRepo?.branches?.map { it.displayName })
        assertEquals("token-123", result.nextPageToken)
    }

    @Test
    fun testChainedFilterCompositionWithoutParentheses() = runBlocking {
        val responseBody = """{ "sources": [] }"""
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val orFilter = SourceFilter.ByName("repo1") or SourceFilter.ByName("repo2")
        julesApi.getSources(filter = orFilter)
        val orRequest = mockWebServer.takeRequest()
        val decodedOrPath = URLDecoder.decode(orRequest.path.orEmpty(), "UTF-8")
        assertEquals("/v1alpha/sources?filter=name=\"repo1\" OR name=\"repo2\"", decodedOrPath)

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))
        val andFilter = SourceFilter.ByName("repo1") and SourceFilter.ByName("repo2")
        julesApi.getSources(filter = andFilter)
        val andRequest = mockWebServer.takeRequest()
        val decodedAndPath = URLDecoder.decode(andRequest.path.orEmpty(), "UTF-8")
        assertEquals("/v1alpha/sources?filter=name=\"repo1\" AND name=\"repo2\"", decodedAndPath)
    }

    @Test
    fun testGetArchivedSessionsFilter() = runBlocking {
        val responseBody = """
            {
              "sessions": [
                {
                  "name": "sessions/archived-123",
                  "id": "archived-123",
                  "prompt": "Test prompt",
                  "state": "COMPLETED",
                  "createTime": "2025-01-01T12:00:00Z",
                  "sourceContext": {
                    "source": "sources/github/owner/repo"
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val response = julesApi.getArchivedSessions(pageSize = 5)

        val recordedRequest = mockWebServer.takeRequest()
        assertTrue("Path mismatch", recordedRequest.path?.contains("/v1alpha/sessions") == true)
        assertTrue("Archived filter missing", recordedRequest.path?.contains("filter=archived%3Dtrue") == true || recordedRequest.path?.contains("filter=archived=true") == true)
        assertEquals(1, response.sessions.size)
        assertEquals("archived-123", response.sessions.first().id)
    }

    @Test
    fun testArchiveSessionSerializesEmptyJsonObject() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(""))

        julesApi.archiveSession(sessionId = "session-789")

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertTrue("URL path mismatch", recordedRequest.path?.endsWith("/sessions/session-789:archive") == true)
        val bodyString = recordedRequest.body.readUtf8()
        assertEquals("{}", bodyString.trim())
    }

    @Test
    fun testCreateSessionAndSendMessageWithInstantAndEnums() = runBlocking {
        val createSessionJson = """
            {
              "name": "sessions/session-456",
              "id": "session-456",
              "title": "Fix bug in app",
              "prompt": "Fix visibility issue",
              "state": "IN_PROGRESS",
              "createTime": "2025-01-01T12:00:00Z",
              "updateTime": "2025-01-01T12:05:00Z",
              "sourceContext": {
                "source": "sources/github/owner/repo",
                "githubRepoContext": {
                  "startingBranch": "main"
                }
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(createSessionJson))

        val request = CreateSessionRequest(
            prompt = "Fix visibility issue",
            title = "Fix bug in app",
            sourceContext = SourceContext(
                source = "sources/github/owner/repo",
                githubRepoContext = GitHubRepoContext(startingBranch = "main")
            ),
            requirePlanApproval = true,
            automationMode = AutomationMode.AUTO_CREATE_PR
        )

        val session = julesApi.createSession(request = request)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("session-456", session.id)
        assertEquals(SessionState.IN_PROGRESS, session.state)
        assertEquals(Instant.parse("2025-01-01T12:00:00Z"), session.createTime)
        assertEquals(Instant.parse("2025-01-01T12:05:00Z"), session.updateTime)
        assertEquals(java.time.Duration.ofMinutes(5), session.duration)
        assertEquals("sources/github/owner/repo", session.sourceContext.source)
        assertEquals("main", session.sourceContext.githubRepoContext?.startingBranch)

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(""))

        julesApi.sendMessage(sessionId = "session-456", prompt = "Please hurry up")
        val messageRequest = mockWebServer.takeRequest()
        assertTrue("Message URL mismatch", messageRequest.path?.endsWith("/sessions/session-456:sendMessage") == true)
    }

    @Test
    fun testGetActivitiesWithCreatedAfterFilter() = runBlocking {
        val responseBody = """
            {
              "activities": [
                {
                  "id": "act-1",
                  "name": "activities/act-1",
                  "originator": "agent",
                  "createTime": "2025-01-01T12:01:00Z",
                  "progressUpdated": {
                    "title": "Making progress"
                  },
                  "artifacts": [
                    {
                      "changeSet": {
                        "source": "sources/github/owner/repo",
                        "gitPatch": {
                          "unidiffPatch": "diff --git a/File.kt b/File.kt\n--- a/File.kt\n+++ b/File.kt\n@@ -1 +1 @@\n-val x = 1\n+val x = 2",
                          "baseCommitId": "commit-123",
                          "suggestedCommitMessage": "Update x"
                        }
                      }
                    },
                    {
                      "media": {
                        "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBASyW",
                        "mimeType": "image/png"
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        val createdAfter = Instant.parse("2025-01-01T12:00:00Z")
        val activitiesResponse = julesApi.getActivities(
            sessionId = "session-456",
            filter = ActivityFilter.CreatedAfter(createdAfter = createdAfter)
        )

        val recordedRequest = mockWebServer.takeRequest()
        assertTrue("Activities path mismatch", recordedRequest.path?.contains("/sessions/session-456/activities") == true)
        assertTrue("Filter parameter mismatch", recordedRequest.path?.contains("filter=create_time") == true)

        assertEquals(1, activitiesResponse.activities.size)
        val activity = activitiesResponse.activities.first() as Activity.ProgressUpdated
        assertEquals("act-1", activity.id)
        assertEquals(Originator.AGENT, activity.originator)
        assertEquals("Making progress", activity.title)
        assertEquals(Instant.parse("2025-01-01T12:01:00Z"), activity.createTime)
        assertEquals(2, activity.artifacts.size)

        val changeSetArtifact = activity.artifacts[0]
        assertEquals(ArtifactType.CHANGE_SET, changeSetArtifact.type)
        val changeSet = changeSetArtifact.changeSet
        assertNotNull("ChangeSet should not be null", changeSet)
        assertEquals(1, changeSet?.parsedFiles?.size)
        val parsedFile = changeSet?.parsedFiles?.first()
        assertEquals("File.kt", parsedFile?.path)
        assertEquals(FileChangeType.MODIFIED, parsedFile?.changeType)
        assertEquals(1, parsedFile?.additions)
        assertEquals(1, parsedFile?.deletions)

        val changeSetSummary = changeSet?.summary
        assertEquals(1, changeSetSummary?.totalFiles)
        assertEquals(1, changeSetSummary?.modified)
        assertEquals(1, changeSetSummary?.totalAdditions)
        assertEquals(1, changeSetSummary?.totalDeletions)

        val mediaArtifact = activity.artifacts[1]
        assertEquals(ArtifactType.MEDIA, mediaArtifact.type)
        assertEquals("image/png", mediaArtifact.media?.mimeType)
        assertTrue("Data URL mismatch", mediaArtifact.media?.dataUrl?.startsWith("data:image/png;base64,") == true)
    }

    @Test
    fun testAllActivitySubtypesDeserialization() = runBlocking {
        val responseBody = """
            {
              "activities": [
                {
                  "id": "act-plan",
                  "name": "activities/act-plan",
                  "originator": "system",
                  "createTime": "2025-01-01T12:01:00Z",
                  "planGenerated": {
                    "plan": {
                      "id": "plan-1",
                      "createTime": "2025-01-01T12:00:00Z",
                      "steps": [
                        { "id": "step-1", "index": 0, "title": "Step 1" }
                      ]
                    }
                  }
                },
                {
                  "id": "act-failed",
                  "name": "activities/act-failed",
                  "originator": "agent",
                  "createTime": "2025-01-01T12:02:00Z",
                  "sessionFailed": {
                    "reason": "Out of memory"
                  }
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))
        val response = julesApi.getActivities(sessionId = "session-1")
        assertEquals(2, response.activities.size)

        val planActivity = response.activities[0] as Activity.PlanGenerated
        assertEquals("plan-1", planActivity.plan.id)
        assertEquals(1, planActivity.plan.steps.size)
        assertEquals("Step 1", planActivity.plan.steps.first().title)

        val failedActivity = response.activities[1] as Activity.SessionFailed
        assertEquals("Out of memory", failedActivity.reason)
    }

    @Test
    fun testApiErrorHandling() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden: Invalid API key"))

        var exceptionThrown = false
        try {
            julesApi.getSession(sessionId = "invalid-session")
        } catch (e: JulesApiException) {
            exceptionThrown = true
            assertEquals(403, e.statusCode)
            assertTrue("Error message expected", e.message?.contains("Forbidden") == true)
        }

        assertTrue("Expected JulesApiException to be thrown", exceptionThrown)
    }

    @Test
    fun testInvalidTimestampThrowsException() = runBlocking {
        val responseBody = """
            {
              "name": "sessions/session-invalid",
              "id": "session-invalid",
              "prompt": "Test prompt",
              "sourceContext": {
                "source": "sources/github/owner/repo"
              },
              "createTime": "invalid-timestamp-string"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(responseBody))

        var exceptionThrown = false
        try {
            julesApi.getSession(sessionId = "session-invalid")
        } catch (e: JulesApiException) {
            exceptionThrown = true
            assertNotNull("Expected non-null cause in JulesApiException", e.cause)
        }

        assertTrue("Expected JulesApiException due to invalid timestamp", exceptionThrown)
    }

    @Test
    fun testPageSizeValidationThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                julesApi.getSources(pageSize = 0)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                julesApi.getSources(pageSize = 101)
            }
        }
    }
}
