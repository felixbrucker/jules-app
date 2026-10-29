package com.felixbrucker.jules.data.repository

import com.felixbrucker.jules.data.database.ActivityBasedStateDao
import com.felixbrucker.jules.data.database.ChatElementDao
import com.felixbrucker.jules.data.database.ChatStateDao
import com.felixbrucker.jules.data.database.DispatchableWorkflowDao
import com.felixbrucker.jules.data.database.DispatchedWorkflowRunDao
import com.felixbrucker.jules.data.database.DispatchedWorkflowRunEntity
import com.felixbrucker.jules.data.database.EmbeddedPullRequest
import com.felixbrucker.jules.data.database.PullRequestDao
import com.felixbrucker.jules.data.database.ScheduledTaskDao
import com.felixbrucker.jules.data.database.ScheduledTaskEntity
import com.felixbrucker.jules.data.database.ScheduledTaskRepo
import com.felixbrucker.jules.data.database.SessionDao
import com.felixbrucker.jules.data.database.SessionDraftDao
import com.felixbrucker.jules.data.worker.ScheduledTaskScheduler
import com.felixbrucker.jules.data.worker.WorkerScheduler
import com.felixbrucker.jules.data.database.SessionDraftEntity
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.data.database.SourceDao
import com.felixbrucker.jules.data.database.SourceSyncMetadataDao
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.datastore.SyncMetadataRepository
import com.felixbrucker.jules.network.api.GitHubApi
import com.felixbrucker.jules.network.api.JulesApi
import com.felixbrucker.jules.network.api.model.AutomationMode
import com.felixbrucker.jules.network.api.model.GitHubRepo
import com.felixbrucker.jules.network.api.model.GitHubWorkflowRunResponse
import com.felixbrucker.jules.network.api.model.PullRequestState
import com.felixbrucker.jules.network.api.model.Repo
import com.felixbrucker.jules.network.api.model.Session
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.network.api.model.SessionsListResponse
import com.felixbrucker.jules.network.api.model.Source
import com.felixbrucker.jules.network.api.model.SourceContext
import com.felixbrucker.jules.network.api.model.SourcesListResponse
import com.felixbrucker.jules.network.api.model.WorkflowRunState
import com.felixbrucker.jules.notification.JulesNotificationManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import com.felixbrucker.jules.ui.sessions.DiffLineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class JulesRepositoryTest {

    private lateinit var sessionDao: SessionDao
    private lateinit var pullRequestDao: PullRequestDao
    private lateinit var sourceDao: SourceDao
    private lateinit var sessionDraftDao: SessionDraftDao
    private lateinit var scheduledTaskDao: ScheduledTaskDao
    private lateinit var scheduledTaskScheduler: ScheduledTaskScheduler
    private lateinit var dispatchableWorkflowDao: DispatchableWorkflowDao
    private lateinit var dispatchedWorkflowRunDao: DispatchedWorkflowRunDao
    private lateinit var sourceSyncMetadataDao: SourceSyncMetadataDao
    private lateinit var chatElementDao: ChatElementDao
    private lateinit var chatStateDao: ChatStateDao
    private lateinit var activityBasedStateDao: ActivityBasedStateDao
    private lateinit var syncMetadataRepository: SyncMetadataRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var notificationManager: JulesNotificationManager
    private lateinit var workerScheduler: WorkerScheduler
    private lateinit var julesApi: JulesApi
    private lateinit var gitHubApi: GitHubApi
    private lateinit var repository: JulesRepository

    @Before
    fun setUp() {
        sessionDao = mockk(relaxed = true)
        pullRequestDao = mockk(relaxed = true)
        sourceDao = mockk(relaxed = true)
        sessionDraftDao = mockk(relaxed = true)
        scheduledTaskDao = mockk(relaxed = true)
        scheduledTaskScheduler = mockk(relaxed = true)
        dispatchableWorkflowDao = mockk(relaxed = true)
        dispatchedWorkflowRunDao = mockk(relaxed = true)
        sourceSyncMetadataDao = mockk(relaxed = true)
        chatElementDao = mockk(relaxed = true)
        chatStateDao = mockk(relaxed = true)
        activityBasedStateDao = mockk(relaxed = true)
        syncMetadataRepository = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        workerScheduler = mockk(relaxed = true)
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
            scheduledTaskDao = scheduledTaskDao,
            scheduledTaskScheduler = scheduledTaskScheduler,
            dispatchableWorkflowDao = dispatchableWorkflowDao,
            dispatchedWorkflowRunDao = dispatchedWorkflowRunDao,
            sourceSyncMetadataDao = sourceSyncMetadataDao,
            chatElementDao = chatElementDao,
            chatStateDao = chatStateDao,
            activityBasedStateDao = activityBasedStateDao,
            syncMetadataRepository = syncMetadataRepository,
            appSettingsRepository = appSettingsRepository,
            notificationManager = notificationManager,
            workerScheduler = workerScheduler,
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
            sourceContext = SourceContext(sourceName = "sources/github/owner/repo")
        )

        coEvery { julesApi.createSession(any()) } returns apiSession
        coEvery { julesApi.getSession("sess-100") } returns apiSession

        repository.createSessionFromDraft(draft)

        coVerify {
            julesApi.createSession(match { it.automationMode == AutomationMode.AUTO_CREATE_PR })
            julesApi.getSession("sess-100")
            sessionDao.insertSessions(any())
            sessionDraftDao.deleteDraft("draft-10")
        }
    }

    @Test
    fun testExecuteScheduledTaskCreatesSessionsAndEnqueuesSyncWorker() = runBlocking {
        val task = ScheduledTaskEntity(
            id = "task-1",
            title = "Scheduled Task",
            prompt = "Do work",
            repos = listOf(ScheduledTaskRepo(sourceId = "github/owner/repo", branch = "main"))
        )

        val apiSession = Session(
            name = "sessions/sess-1",
            id = "sess-1",
            title = "Scheduled Task",
            prompt = "Do work",
            state = SessionState.PLANNING,
            createTime = Instant.now(),
            sourceContext = SourceContext(sourceName = "sources/github/owner/repo")
        )

        coEvery { scheduledTaskDao.getTaskById("task-1") } returns task
        coEvery { julesApi.createSession(any()) } returns apiSession
        coEvery { julesApi.getSession("sess-1") } returns apiSession

        repository.executeScheduledTask("task-1")

        coVerify {
            julesApi.createSession(any())
            julesApi.getSession("sess-1")
            scheduledTaskDao.upsertTask(any())
            scheduledTaskScheduler.scheduleTask(any())
            workerScheduler.enqueueIfNeeded()
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
        coEvery { sessionDao.getActiveSessionsWithPullRequests(includePrivate = false, cutoffEpochMillis = any()) } returns emptyList()

        repository.sync()

        coVerifyOrder {
            julesApi.getSources(pageSize = 100, pageToken = null)
            julesApi.getSessions(pageSize = 30, pageToken = null)
            sessionDao.getActiveSessionsWithPullRequests(includePrivate = false, cutoffEpochMillis = any())
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

        coEvery { sessionDao.getActiveSessionsWithPullRequests(includePrivate = false, cutoffEpochMillis = any()) } returns listOf(activeSession)
        coEvery { gitHubApi.getPullRequestState(prUrl) } returns PullRequestState.MERGED

        repository.syncPullRequests()

        coVerify {
            sessionDao.getActiveSessionsWithPullRequests(includePrivate = false, cutoffEpochMillis = any())
            gitHubApi.getPullRequestState(prUrl)
            pullRequestDao.upsertPullRequests(match { it.first().sessionId == "sess-1" && it.first().url == prUrl && it.first().state == PullRequestState.MERGED })
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
    fun testSyncSourceGatedWhenSyncedWithin5Minutes() = runBlocking {
        val sourceId = "sources/github/owner/repo"
        coEvery { sourceSyncMetadataDao.isSourceSyncedRecently(sourceId, any()) } returns true

        repository.syncSource(sourceId = sourceId, force = false)

        coVerify(exactly = 0) {
            julesApi.getSource(any())
        }
    }

    @Test
    fun testSyncSourceRunsWhenNotSyncedRecentlyOrForced() = runBlocking {
        val sourceId = "sources/github/owner/repo"
        val apiSource = Source(
            name = sourceId,
            id = "github/owner/repo",
            githubRepo = GitHubRepo(owner = "owner", repo = "repo")
        )

        coEvery { sourceSyncMetadataDao.isSourceSyncedRecently(sourceId, any()) } returns false
        coEvery { julesApi.getSource(sourceId) } returns apiSource

        repository.syncSource(sourceId = sourceId, force = false)

        coVerify {
            julesApi.getSource(sourceId)
            sourceDao.upsertSources(any())
            sourceSyncMetadataDao.updateLastSourceSyncedAtTargeted(sourceId, any())
        }
    }

    @Test
    fun testDispatchWorkflow() = runBlocking {
        coEvery {
            gitHubApi.dispatchWorkflow(
                owner = "octocat",
                repo = "Hello-World",
                workflowId = "12345",
                ref = "main",
                inputs = mapOf("param" to "value")
            )
        } returns 98765L

        val runId = repository.dispatchWorkflow(
            sessionId = "sess-1",
            owner = "octocat",
            repo = "Hello-World",
            workflowId = "12345",
            workflowName = "CI",
            ref = "main",
            inputs = mapOf("param" to "value")
        )

        assertEquals(98765L, runId)
        coVerify {
            dispatchedWorkflowRunDao.upsertWorkflowRun(
                match { it.sessionId == "sess-1" && it.workflowRunId == 98765L && it.workflowName == "CI" && it.state == WorkflowRunState.QUEUED }
            )
        }
    }

    @Test
    fun testSyncDispatchedWorkflowRunsUpdatesState() = runBlocking {
        coEvery { appSettingsRepository.githubAuthTokenFlow } returns flowOf("ghp_token")

        val queuedRun = DispatchedWorkflowRunEntity(
            sessionId = "sess-1",
            workflowRunId = 98765L,
            workflowName = "CI",
            state = WorkflowRunState.QUEUED,
            updatedAt = Instant.now().minusSeconds(120)
        )

        val sessionEntity = SessionEntity(
            id = "sess-1",
            name = "sessions/sess-1",
            title = "Test Session",
            prompt = "Prompt",
            state = SessionState.IN_PROGRESS,
            isArchived = false,
            createTime = Instant.now(),
            updateTime = null,
            url = null,
            repo = Repo(host = "github", owner = "octocat", name = "Hello-World"),
            pullRequest = null,
            changeSet = null
        )

        val sessionWithPr = SessionWithPullRequest(
            session = sessionEntity,
            pullRequestEntity = null,
            workflows = emptyList(),
            dispatchedWorkflowRun = queuedRun
        )

        coEvery { dispatchedWorkflowRunDao.getActiveWorkflowRunsNeedingSync(any()) } returns listOf(queuedRun)
        coEvery { sessionDao.getSessionById("sess-1") } returns sessionWithPr
        coEvery { gitHubApi.getWorkflowRun("octocat", "Hello-World", 98765L) } returns GitHubWorkflowRunResponse(
            status = "completed",
            conclusion = "success"
        )

        repository.syncDispatchedWorkflowRuns()

        coVerify {
            dispatchedWorkflowRunDao.upsertWorkflowRun(
                match { it.sessionId == "sess-1" && it.state == WorkflowRunState.SUCCESS }
            )
        }
    }

    @Test
    fun testExtractAllFileDiffLines() {
        val patch = """
            diff --git a/MainActivity.kt b/MainActivity.kt
            --- a/MainActivity.kt
            +++ b/MainActivity.kt
            @@ -10,3 +10,4 @@
             package com.example
            -val x = 1
            +val x = 2
            +val y = 3
            diff --git a/DeletedFile.kt b/DeletedFile.kt
            --- a/DeletedFile.kt
            +++ /dev/null
            @@ -1,2 +0,0 @@
            -class Deleted
            -
            diff --git a/NewFile.kt b/NewFile.kt
            --- /dev/null
            +++ b/NewFile.kt
            @@ -0,0 +1,2 @@
            +class NewFile {
            +}
        """.trimIndent()

        val result = repository.extractAllFileDiffLines(patch)

        assertEquals(3, result.size)
        assertTrue(result.containsKey("MainActivity.kt"))
        assertTrue(result.containsKey("DeletedFile.kt"))
        assertTrue(result.containsKey("NewFile.kt"))

        val mainActivityLines = result["MainActivity.kt"]!!
        assertEquals(4, mainActivityLines.size)
        assertEquals(DiffLineType.CONTEXT, mainActivityLines[0].type)
        assertEquals(10, mainActivityLines[0].oldLineNumber)
        assertEquals(10, mainActivityLines[0].newLineNumber)

        assertEquals(DiffLineType.DELETION, mainActivityLines[1].type)
        assertEquals(11, mainActivityLines[1].oldLineNumber)
        assertEquals(null, mainActivityLines[1].newLineNumber)

        assertEquals(DiffLineType.ADDITION, mainActivityLines[2].type)
        assertEquals(null, mainActivityLines[2].oldLineNumber)
        assertEquals(11, mainActivityLines[2].newLineNumber)

        assertEquals(DiffLineType.ADDITION, mainActivityLines[3].type)
        assertEquals(null, mainActivityLines[3].oldLineNumber)
        assertEquals(12, mainActivityLines[3].newLineNumber)
    }

    @Test
    fun testExtractAllFileDiffLinesEmptyOrBlank() {
        assertTrue(repository.extractAllFileDiffLines("").isEmpty())
        assertTrue(repository.extractAllFileDiffLines("   ").isEmpty())
    }
}
