package com.felixbrucker.jules.ui.sessions

import androidx.lifecycle.SavedStateHandle
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.network.api.model.SessionState
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
                    repo = null,
                    pullRequest = null,
                    changeSet = null
                ),
                pullRequestEntity = null,
                workflows = emptyList()
            )
        )
        coEvery { repository.getLatestActivityWithChangesetFlow("sess-123") } returns flowOf(null)
        coEvery { repository.getActivitiesPagingDataFlow("sess-123") } returns flowOf(androidx.paging.PagingData.empty())
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
}
