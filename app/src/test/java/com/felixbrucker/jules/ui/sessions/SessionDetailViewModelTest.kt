package com.felixbrucker.jules.ui.sessions

import androidx.lifecycle.SavedStateHandle
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.data.database.ActivityBasedStateEntity
import com.felixbrucker.jules.data.database.ComputedDiff
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.network.api.model.Repo
import com.felixbrucker.jules.network.api.model.FileChangeType
import com.felixbrucker.jules.network.api.model.ParsedFile
import com.felixbrucker.jules.network.api.model.SessionState
import kotlinx.coroutines.launch
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: JulesRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private var viewModel: SessionDetailViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to "sess-123"))

        coEvery { repository.getSessionFlow("sess-123") } returns flowOf(
            SessionWithPullRequest(
                session = SessionEntity(
                    id = "sess-123",
                    name = "sessions/sess-123",
                    title = "Test Session",
                    prompt = "Fix bug",
                    state = SessionState.IN_PROGRESS,
                    isArchived = false,
                    createTime = Instant.now(),
                    updateTime = null,
                    url = null,
                    repo = Repo(host = "github", owner = "octocat", name = "Hello-World"),
                    pullRequest = null,
                    changeSet = null
                ),
                pullRequestEntity = null,
                workflows = emptyList()
            )
        )
        coEvery { repository.getChatStateFlow("sess-123") } returns flowOf(null)
        coEvery { repository.getActivityBasedStateFlow("sess-123") } returns flowOf(null)
        coEvery { repository.getChatElementsPagingDataFlow("sess-123") } returns flowOf(androidx.paging.PagingData.empty())
    }

    @After
    fun tearDown() {
        viewModel?.stopPolling()
        Dispatchers.resetMain()
    }

    @Test
    fun testUpdateSplitRatioClampsValues() = runTest(UnconfinedTestDispatcher()) {
        val vm = SessionDetailViewModel(repository, savedStateHandle)
        viewModel = vm
        vm.updateSplitRatio(0.02f)
        assertEquals(0.0f, vm.splitRatio.value, 0.001f)

        vm.updateSplitRatio(0.98f)
        assertEquals(1.0f, vm.splitRatio.value, 0.001f)

        vm.updateSplitRatio(0.6f)
        assertEquals(0.6f, vm.splitRatio.value, 0.001f)
    }

    @Test
    fun testSendMessageTriggersRepositoryMethod() = runTest(UnconfinedTestDispatcher()) {
        val vm = SessionDetailViewModel(repository, savedStateHandle)
        viewModel = vm
        vm.sendMessage("Hello Jules")
        coVerify { repository.sendMessage("sess-123", "Hello Jules") }
    }

    @Test
    fun testApprovePlanTriggersRepositoryMethod() = runTest(UnconfinedTestDispatcher()) {
        val vm = SessionDetailViewModel(repository, savedStateHandle)
        viewModel = vm
        vm.approvePlan()
        coVerify { repository.approvePlan("sess-123") }
    }

    @Test
    fun testUpdateFileSearchQueryUpdatesState() = runTest(UnconfinedTestDispatcher()) {
        val vm = SessionDetailViewModel(repository, savedStateHandle)
        viewModel = vm

        assertEquals("", vm.fileSearchQuery.value)

        vm.updateFileSearchQuery("AppTest")
        assertEquals("AppTest", vm.fileSearchQuery.value)

        vm.updateFileSearchQuery("   ")
        assertEquals("   ", vm.fileSearchQuery.value)
    }

    @Test
    fun testDispatchWorkflowSuccess() = runTest(UnconfinedTestDispatcher()) {
        val vm = SessionDetailViewModel(repository, savedStateHandle)
        viewModel = vm

        val session = SessionEntity(
            id = "sess-123",
            name = "sessions/sess-123",
            title = "Test Session",
            prompt = "Fix bug",
            state = SessionState.IN_PROGRESS,
            isArchived = false,
            createTime = Instant.now(),
            updateTime = null,
            url = null,
            repo = Repo(host = "github", owner = "octocat", name = "Hello-World"),
            pullRequest = null,
            changeSet = null
        )
        val workflow = DispatchableWorkflowEntity(
            id = "github/octocat/Hello-World_123",
            sourceId = "github/octocat/Hello-World",
            workflowId = 123L,
            name = "CI",
            inputs = emptyList()
        )

        coEvery {
            repository.dispatchWorkflow(
                sessionId = "sess-123",
                owner = "octocat",
                repo = "Hello-World",
                workflowId = "123",
                workflowName = "CI",
                ref = "main",
                inputs = emptyMap()
            )
        } returns 98765L

        vm.dispatchWorkflow(session, workflow, "main", emptyMap())

        coVerify {
            repository.dispatchWorkflow(
                sessionId = "sess-123",
                owner = "octocat",
                repo = "Hello-World",
                workflowId = "123",
                workflowName = "CI",
                ref = "main",
                inputs = emptyMap()
            )
        }
    }
}
