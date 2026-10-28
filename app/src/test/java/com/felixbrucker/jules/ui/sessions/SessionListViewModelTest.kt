package com.felixbrucker.jules.ui.sessions

import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.network.api.model.Repo
import com.felixbrucker.jules.network.api.model.SessionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var julesRepository: JulesRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var viewModel: SessionListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        julesRepository = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)

        coEvery { julesRepository.getDailySessionCountFlow() } returns flowOf(0)
        coEvery { julesRepository.getSessionsFlow(any(), any(), any()) } returns flowOf(emptyList())
        coEvery { julesRepository.getDraftsFlow(any()) } returns flowOf(emptyList())
        coEvery { julesRepository.getSourcesFlow() } returns flowOf(emptyList())
        coEvery { appSettingsRepository.dailySessionLimitFlow } returns flowOf(15)
        coEvery { appSettingsRepository.selectedFilterChipsFlow } returns flowOf(emptySet())
        coEvery { appSettingsRepository.includeDraftsFlow } returns flowOf(true)
        coEvery { appSettingsRepository.includeScheduledFlow } returns flowOf(false)
        coEvery { appSettingsRepository.includeArchivedFlow } returns flowOf(false)
        coEvery { appSettingsRepository.lastSelectedSourceIdFlow } returns flowOf(null)

        viewModel = SessionListViewModel(julesRepository, appSettingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testOpenAndDismissDispatchDialog() = runTest {
        val session = SessionEntity(
            id = "sess-1",
            name = "sessions/sess-1",
            title = "Test",
            prompt = "Prompt",
            state = SessionState.COMPLETED,
            isArchived = false,
            createTime = Instant.now(),
            updateTime = null,
            url = null,
            repo = Repo(host = "github", owner = "owner", name = "repo"),
            pullRequest = null,
            changeSet = null
        )
        val workflow = DispatchableWorkflowEntity(
            id = "github/owner/repo_123",
            sourceId = "github/owner/repo",
            workflowId = 123L,
            name = "CI",
            inputs = emptyList()
        )

        viewModel.openDispatchDialog(session, workflow)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.dispatchDialogState.value
        assertNotNull(state)
        assertEquals(session, state?.session)
        assertEquals(workflow, state?.workflow)

        viewModel.dismissDispatchDialog()
        assertNull(viewModel.dispatchDialogState.value)
    }

    @Test
    fun testDispatchWorkflowSuccess() = runTest {
        val session = SessionEntity(
            id = "sess-1",
            name = "sessions/sess-1",
            title = "Test",
            prompt = "Prompt",
            state = SessionState.COMPLETED,
            isArchived = false,
            createTime = Instant.now(),
            updateTime = null,
            url = null,
            repo = Repo(host = "github", owner = "owner", name = "repo"),
            pullRequest = null,
            changeSet = null
        )
        val workflow = DispatchableWorkflowEntity(
            id = "github/owner/repo_123",
            sourceId = "github/owner/repo",
            workflowId = 123L,
            name = "CI",
            inputs = emptyList()
        )

        coEvery {
            julesRepository.dispatchWorkflow(
                sessionId = "sess-1",
                owner = "owner",
                repo = "repo",
                workflowId = "123",
                workflowName = "CI",
                ref = "main",
                inputs = mapOf("env" to "prod")
            )
        } returns 98765L

        viewModel.openDispatchDialog(session, workflow)
        viewModel.dispatchWorkflow(session, workflow, "main", mapOf("env" to "prod"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            julesRepository.dispatchWorkflow(
                sessionId = "sess-1",
                owner = "owner",
                repo = "repo",
                workflowId = "123",
                workflowName = "CI",
                ref = "main",
                inputs = mapOf("env" to "prod")
            )
        }
        assertNull(viewModel.dispatchDialogState.value)
        assertEquals("Workflow 'CI' dispatched successfully", viewModel.userFeedback.value)
    }

    @Test
    fun testOpenCreateDialogUsesLastSelectedSource() = runTest {
        val source1 = com.felixbrucker.jules.data.database.SourceEntity(id = "repo-1", name = "Repo 1", githubRepo = null)
        val source2 = com.felixbrucker.jules.data.database.SourceEntity(id = "repo-2", name = "Repo 2", githubRepo = null)
        coEvery { julesRepository.getSourcesFlow() } returns flowOf(listOf(source1, source2))
        coEvery { appSettingsRepository.lastSelectedSourceIdFlow } returns flowOf("repo-2")

        val vm = SessionListViewModel(julesRepository, appSettingsRepository)
        val sourcesCollector = launch { vm.sources.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openCreateDialog()
        testDispatcher.scheduler.advanceUntilIdle()
        sourcesCollector.cancel()

        val activeDraft = vm.activeDraft.value
        assertNotNull(activeDraft)
        assertEquals("repo-2", activeDraft?.sourceId)
    }

    @Test
    fun testSyncSessionsErrorSetsUserError() = runTest {
        coEvery { julesRepository.sync() } throws RuntimeException("Network error")

        viewModel.syncSessions()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Network error", viewModel.userFeedback.value)

        viewModel.clearUserFeedback()
        assertNull(viewModel.userFeedback.value)
    }

    @Test
    fun testToggleFilterChipPersistsSelectedChips() = runTest {
        viewModel.toggleFilterChip(SessionFilterChip.COMPLETED)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            appSettingsRepository.saveSelectedFilterChips(setOf("COMPLETED"))
        }
    }

    @Test
    fun testRestoresSelectedFilterChips() = runTest {
        coEvery { appSettingsRepository.selectedFilterChipsFlow } returns flowOf(setOf("IN_PROGRESS", "AWAITING_FEEDBACK"))

        val vm = SessionListViewModel(julesRepository, appSettingsRepository)
        val chipsCollector = launch { vm.selectedChips.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        val chips = vm.selectedChips.value
        assertEquals(setOf(SessionFilterChip.IN_PROGRESS, SessionFilterChip.AWAITING_FEEDBACK), chips)
        chipsCollector.cancel()
    }

    @Test
    fun testInProgressFilterIncludesQueuedPlanningAndInProgressStates() = runTest {
        coEvery { appSettingsRepository.selectedFilterChipsFlow } returns flowOf(setOf("IN_PROGRESS"))

        val vm = SessionListViewModel(julesRepository, appSettingsRepository)
        val sessionsCollector = launch { vm.sessions.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            julesRepository.getSessionsFlow(
                searchQuery = "",
                states = listOf(
                    SessionState.QUEUED,
                    SessionState.PLANNING,
                    SessionState.IN_PROGRESS
                ),
                includeArchived = false
            )
        }
        sessionsCollector.cancel()
    }

    @Test
    fun testToggleIncludeDraftsScheduledAndArchived() = runTest {
        viewModel.toggleIncludeDrafts(false)
        viewModel.toggleIncludeScheduled(true)
        viewModel.toggleIncludeArchived(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appSettingsRepository.setIncludeDrafts(false) }
        coVerify { appSettingsRepository.setIncludeScheduled(true) }
        coVerify { appSettingsRepository.setIncludeArchived(true) }
    }

    @Test
    fun testRestoresIncludeDraftsScheduledAndArchived() = runTest {
        coEvery { appSettingsRepository.includeDraftsFlow } returns flowOf(false)
        coEvery { appSettingsRepository.includeScheduledFlow } returns flowOf(true)
        coEvery { appSettingsRepository.includeArchivedFlow } returns flowOf(true)

        val vm = SessionListViewModel(julesRepository, appSettingsRepository)
        val draftsCollector = launch { vm.includeDrafts.collect {} }
        val scheduledCollector = launch { vm.includeScheduled.collect {} }
        val archivedCollector = launch { vm.includeArchived.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, vm.includeDrafts.value)
        assertEquals(true, vm.includeScheduled.value)
        assertEquals(true, vm.includeArchived.value)

        draftsCollector.cancel()
        scheduledCollector.cancel()
        archivedCollector.cancel()
    }
}
