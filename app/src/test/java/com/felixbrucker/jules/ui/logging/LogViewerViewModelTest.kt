package com.felixbrucker.jules.ui.logging

import com.felixbrucker.jules.data.repository.LogRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewerViewModelTest {

    private lateinit var logRepository: LogRepository
    private lateinit var viewModel: LogViewerViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        logRepository = mockk(relaxed = true)
        coEvery { logRepository.getTotalCountFlow() } returns flowOf(0)
        coEvery { logRepository.getFilteredCountFlow(any(), any()) } returns flowOf(0)
        viewModel = LogViewerViewModel(logRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialState() {
        assertEquals("", viewModel.searchQuery.value)
        assertTrue(viewModel.selectedPriorities.value.isEmpty())
    }

    @Test
    fun testSetSearchQueryUpdatesState() {
        viewModel.setSearchQuery("network error")
        assertEquals("network error", viewModel.searchQuery.value)
    }

    @Test
    fun testTogglePriorityMultiSelect() {
        viewModel.togglePriority(3)
        viewModel.togglePriority(4)
        assertEquals(setOf(3, 4), viewModel.selectedPriorities.value)

        viewModel.togglePriority(3)
        assertEquals(setOf(4), viewModel.selectedPriorities.value)

        viewModel.togglePriority(-1)
        assertTrue(viewModel.selectedPriorities.value.isEmpty())
    }

    @Test
    fun testClearLogsCallsRepository() = runTest {
        viewModel.clearLogs()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            logRepository.clearLogs()
        }
    }
}
