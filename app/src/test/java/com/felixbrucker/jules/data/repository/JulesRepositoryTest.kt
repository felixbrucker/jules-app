package com.felixbrucker.jules.data.repository

import com.felixbrucker.jules.data.database.EmbeddedPullRequest
import com.felixbrucker.jules.data.database.PullRequestDao
import com.felixbrucker.jules.data.database.PullRequestEntity
import com.felixbrucker.jules.data.database.SessionDao
import com.felixbrucker.jules.data.database.SessionDraftDao
import com.felixbrucker.jules.data.database.SessionDraftEntity
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SourceDao
import com.felixbrucker.jules.data.database.SourceSyncMetadataDao
import com.felixbrucker.jules.data.database.WorkflowDispatchDao
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.datastore.SyncMetadataRepository
import com.felixbrucker.jules.notification.JulesNotificationManager
import com.felixbrucker.jules.network.api.GitHubApi
import com.felixbrucker.jules.network.api.JulesApi
import com.felixbrucker.jules.network.api.model.GitHubRepo
import com.felixbrucker.jules.network.api.model.PullRequestState
import com.felixbrucker.jules.network.api.model.Session
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.network.api.model.SessionsListResponse
import com.felixbrucker.jules.network.api.model.Source
import com.felixbrucker.jules.network.api.model.SourceContext
import com.felixbrucker.jules.network.api.model.SourcesListResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.time.Instant

class JulesRepositoryTest {

    private lateinit var sessionDao: SessionDao
    private lateinit var pullRequestDao: PullRequestDao
    private lateinit var sourceDao: SourceDao
    private lateinit var sessionDraftDao: SessionDraftDao
    private lateinit var workflowDispatchDao: WorkflowDispatchDao
    private lateinit var sourceSyncMetadataDao: SourceSyncMetadataDao
    private lateinit var syncMetadataRepository: SyncMetadataRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var notificationManager: JulesNotificationManager
    private lateinit var julesApi: JulesApi
    private lateinit var gitHubApi: GitHubApi
    private lateinit var repository: JulesRepository

    @Before
    fun setUp() {
        sessionDao = mockk(relaxed = true)
        pullRequestDao = mockk(relaxed = true)
        sourceDao = mockk(relaxed = true)
        sessionDraftDao = mockk(relaxed = true)
        workflowDispatchDao = mockk(relaxed = true)
        sourceSyncMetadataDao = mockk(relaxed = true)
        syncMetadataRepository = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        julesApi = mockk(relaxed = true)
        gitHubApi = mockk(relaxed = true)

        coEvery { appSettingsRepository.githubAuthTokenFlow } returns flowOf(null)
        coEvery { appSettingsRepository.periodicSyncEnabledFlow } returns flowOf(false)
        coEvery { appSettingsRepository.feedbackNotificationsEnabledFlow } returns flowOf(true)
        coEvery { appSettingsRepository.completionNotificationsEnabledFlow } returns flowOf(true)
        coEvery { syncMetadataRepository.lastSourcesSyncedAtFlow } returns flowOf(null)
        coEvery { syncMetadataRepository.lastArchivedSessionsSyncedAtFlow } returns flowOf(null)

        repository = JulesRepository(
            sessionDao = sessionDao,
            pullRequestDao = pullRequestDao,
            sourceDao = sourceDao,
            sessionDraftDao = sessionDraftDao,
            workflowDispatchDao = workflowDispatchDao,
            sourceSyncMetadataDao = sourceSyncMetadataDao,
            syncMetadataRepository = syncMetadataRepository,
            appSettingsRepository = appSettingsRepository,
            notificationManager = notificationManager,
            julesApi = julesApi,
            gitHubApi = gitHubApi
        )
    }

    @Test
    fun testSaveDraftAndDeleteDraft() = runBlocking {
        val draft = SessionDraftEntity(id = "draft-1", title = "Draft 1", prompt = "Fix bug")

        repository.saveDraft(draft)
        coVerify { sessionDraftDao.upsertDraft(draft) }

        repository.deleteDraft("draft-1")
        coVerify { sessionDraftDao.deleteDraft("draft-1") }
    }

    @Test
    fun testCreateSessionFromDraftCallsApiAndDeletesDraft() = runBlocking {
        val draft = SessionDraftEntity(
            id = "draft-10",
            title = "New Session",
            prompt = "Refactor code",
            sourceId = "github/owner/repo",
            branch = "main",
            requirePlanApproval = true
        )

        val apiSession = Session(
            name = "sessions/sess-100",
            id = "sess-100",
            title = "New Session",
            prompt = "Refactor code",
            state = SessionState.PLANNING,
            createTime = Instant.now(),
            sourceContext = SourceContext(source = "sources/github/owner/repo")
        )

        coEvery { julesApi.createSession(any()) } returns apiSession
        coEvery { julesApi.getSession("sess-100") } returns apiSession

        repository.createSessionFromDraft(draft)

        coVerify {
            julesApi.createSession(any())
            julesApi.getSession("sess-100")
            sessionDao.insertSessions(any())
            sessionDraftDao.deleteDraft("draft-10")
        }
    }

    @Test
    fun testSyncCallsSessionsAndPullRequestsSequentially() = runBlocking {
        coEvery { julesApi.getSessions(pageSize = 30, pageToken = null) } returns SessionsListResponse(
            sessions = emptyList(),
            nextPageToken = null
        )
        coEvery { julesApi.getSources(pageSize = 100, pageToken = null) } returns SourcesListResponse(
            sources = emptyList(),
            nextPageToken = null
        )
        coEvery { sessionDao.getAllActiveSessionIds() } returns emptyList()
        coEvery { sessionDao.getActiveSessionsWithPullRequests(includePrivate = false) } returns emptyList()

        repository.sync()

        coVerifyOrder {
            julesApi.getSources(pageSize = 100, pageToken = null)
            julesApi.getSessions(pageSize = 30, pageToken = null)
            sessionDao.getActiveSessionsWithPullRequests(includePrivate = false)
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

        coEvery { sessionDao.getActiveSessionsWithPullRequests(includePrivate = false) } returns listOf(activeSession)
        coEvery { gitHubApi.getPullRequestState(prUrl) } returns PullRequestState.MERGED

        repository.syncPullRequests()

        coVerify {
            sessionDao.getActiveSessionsWithPullRequests(includePrivate = false)
            gitHubApi.getPullRequestState(prUrl)
            pullRequestDao.upsertPullRequests(listOf(PullRequestEntity(sessionId = "sess-1", url = prUrl, state = PullRequestState.MERGED)))
        }
    }

    @Test
    fun testSyncSourcesGatedWhenSyncedRecently() = runBlocking {
        coEvery { syncMetadataRepository.lastSourcesSyncedAtFlow } returns flowOf(Instant.now())

        repository.syncSources(force = false)

        coVerify(exactly = 0) {
            julesApi.getSources(any(), any(), any())
        }
    }

    @Test
    fun testSyncSourcesRunsWhenForced() = runBlocking {
        coEvery { syncMetadataRepository.lastSourcesSyncedAtFlow } returns flowOf(Instant.now())
        coEvery { julesApi.getSources(pageSize = 100, pageToken = null) } returns SourcesListResponse(
            sources = listOf(
                Source(
                    name = "sources/github/owner/repo",
                    id = "github/owner/repo",
                    githubRepo = GitHubRepo(owner = "owner", repo = "repo")
                )
            ),
            nextPageToken = null
        )

        repository.syncSources(force = true)

        coVerify {
            julesApi.getSources(pageSize = 100, pageToken = null)
            sourceDao.upsertSources(any())
            syncMetadataRepository.saveLastSourcesSyncedAt(any())
        }
    }

    @Test
    fun testSyncSessionsPartitionsInsertsAndFetchesMissingSources() = runBlocking {
        val session1 = Session(
            name = "sessions/sess-1",
            id = "sess-1",
            prompt = "Prompt 1",
            createTime = Instant.now(),
            sourceContext = SourceContext(source = "sources/github/owner/repo1")
        )

        coEvery { julesApi.getSessions(pageSize = 30, pageToken = null) } returns SessionsListResponse(
            sessions = listOf(session1),
            nextPageToken = null
        )
        coEvery { sessionDao.getAllSessionIds() } returns emptyList()
        coEvery { sourceDao.getAllSourceIds() } returns emptyList()
        coEvery { julesApi.getSource("github/owner/repo1") } returns Source(
            name = "sources/github/owner/repo1",
            id = "github/owner/repo1",
            githubRepo = GitHubRepo(owner = "owner", repo = "repo1")
        )

        repository.syncSessions()

        coVerify {
            julesApi.getSource("github/owner/repo1")
            sourceDao.upsertSources(any())
            sessionDao.insertSessions(any())
        }
    }

    @Test
    fun testBackgroundSyncTriggersNotificationsWhenStateRequiresFeedbackOrCompleted() = runBlocking {
        coEvery { appSettingsRepository.periodicSyncEnabledFlow } returns flowOf(true)
        coEvery { appSettingsRepository.feedbackNotificationsEnabledFlow } returns flowOf(true)
        coEvery { appSettingsRepository.completionNotificationsEnabledFlow } returns flowOf(true)

        val session1 = Session(
            name = "sessions/sess-1",
            id = "sess-1",
            prompt = "Prompt 1",
            state = SessionState.AWAITING_USER_FEEDBACK,
            createTime = Instant.now(),
            sourceContext = SourceContext(source = "sources/github/owner/repo1")
        )

        val session2 = Session(
            name = "sessions/sess-2",
            id = "sess-2",
            prompt = "Prompt 2",
            state = SessionState.COMPLETED,
            createTime = Instant.now(),
            sourceContext = SourceContext(source = "sources/github/owner/repo2")
        )

        coEvery { julesApi.getSessions(pageSize = 30, pageToken = null) } returns SessionsListResponse(
            sessions = listOf(session1, session2),
            nextPageToken = null
        )
        coEvery { sessionDao.getSessionsByIds(listOf("sess-1", "sess-2")) } returns emptyList()

        repository.syncSessions(isBackground = true)

        coVerify {
            notificationManager.sendTaskFeedbackNotification(any())
            notificationManager.sendTaskCompletedNotification(any())
        }
    }

    @Test
    fun testSyncWorkflowsQueriesSourcesNeedingSync() = runBlocking {
        coEvery { sourceDao.getSourcesNeedingWorkflowSync(any(), any(), any()) } returns emptyList()

        repository.syncWorkflows(force = false)

        coVerify {
            sourceDao.getSourcesNeedingWorkflowSync(includePrivate = false, thresholdEpochMillis = any(), force = false)
        }
    }

    @Test
    fun testDispatchWorkflow() = runBlocking {
        repository.dispatchWorkflow(
            owner = "octocat",
            repo = "Hello-World",
            workflowId = "12345",
            ref = "main",
            inputs = mapOf("param" to "value")
        )

        coVerify {
            gitHubApi.dispatchWorkflow(
                owner = "octocat",
                repo = "Hello-World",
                workflowId = "12345",
                ref = "main",
                inputs = mapOf("param" to "value")
            )
        }
    }
}
