package com.felixbrucker.jules.ui.sessions

import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.WorkflowDispatchEntity
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
        val workflow = WorkflowDispatchEntity(
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
        val workflow = WorkflowDispatchEntity(
            id = "github/owner/repo_123",
            sourceId = "github/owner/repo",
            workflowId = 123L,
            name = "CI",
            inputs = emptyList()
        )

        viewModel.openDispatchDialog(session, workflow)
        viewModel.dispatchWorkflow(session, workflow, "main", mapOf("env" to "prod"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            julesRepository.dispatchWorkflow(
                owner = "owner",
                repo = "repo",
                workflowId = "123",
                ref = "main",
                inputs = mapOf("env" to "prod")
            )
        }
        assertNull(viewModel.dispatchDialogState.value)
    }
}
