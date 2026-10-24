package com.felixbrucker.jules.data.repository

import com.felixbrucker.jules.data.database.EmbeddedPullRequest
import com.felixbrucker.jules.data.database.PullRequestDao
import com.felixbrucker.jules.data.database.PullRequestEntity
import com.felixbrucker.jules.data.database.SessionDao
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.network.api.GitHubApi
import com.felixbrucker.jules.network.api.JulesApi
import com.felixbrucker.jules.network.api.model.PullRequestState
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.network.api.model.SessionsListResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.time.Instant

class JulesRepositoryTest {

    private lateinit var sessionDao: SessionDao
    private lateinit var pullRequestDao: PullRequestDao
    private lateinit var julesApi: JulesApi
    private lateinit var gitHubApi: GitHubApi
    private lateinit var repository: JulesRepository

    @Before
    fun setUp() {
        sessionDao = mockk(relaxed = true)
        pullRequestDao = mockk(relaxed = true)
        julesApi = mockk(relaxed = true)
        gitHubApi = mockk(relaxed = true)
        repository = JulesRepository(
            sessionDao = sessionDao,
            pullRequestDao = pullRequestDao,
            julesApi = julesApi,
            gitHubApi = gitHubApi
        )
    }

    @Test
    fun testSyncCallsSessionsAndPullRequestsSequentially() = runBlocking {
        coEvery { julesApi.getSessions(pageSize = 30, pageToken = null) } returns SessionsListResponse(
            sessions = emptyList(),
            nextPageToken = null
        )
        coEvery { sessionDao.getAllActiveSessionIds() } returns emptyList()
        coEvery { sessionDao.getActiveSessionsWithPullRequests() } returns emptyList()

        repository.sync()

        coVerifyOrder {
            julesApi.getSessions(pageSize = 30, pageToken = null)
            sessionDao.getActiveSessionsWithPullRequests()
        }
    }

    @Test
    fun testSyncPullRequestsForActiveSessions() = runBlocking {
        val prUrl = "https://github.com/octocat/Hello-World/pull/1"
        val activeSession = SessionEntity(
            id = "sess-1",
            name = "sessions/sess-1",
            title = "Test Session",
            prompt = "Fix bug",
            state = SessionState.COMPLETED,
            isArchived = false,
            createTime = Instant.now(),
            updateTime = null,
            url = null,
            repo = null,
            pullRequest = EmbeddedPullRequest(
                url = prUrl,
                title = "PR Title",
                description = "PR Desc",
                baseRef = "main",
                headRef = "feature"
            ),
            changeSet = null
        )

        coEvery { sessionDao.getActiveSessionsWithPullRequests() } returns listOf(activeSession)
        coEvery { gitHubApi.getPullRequestState(prUrl) } returns PullRequestState.MERGED

        repository.syncPullRequests()

        coVerify {
            gitHubApi.getPullRequestState(prUrl)
            pullRequestDao.upsertPullRequests(listOf(PullRequestEntity(url = prUrl, state = PullRequestState.MERGED)))
        }
    }
}
