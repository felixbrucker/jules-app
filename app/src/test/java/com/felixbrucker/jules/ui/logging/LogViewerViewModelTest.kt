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
        assertEquals(-1, viewModel.selectedPriority.value)
    }

    @Test
    fun testSetSearchQueryUpdatesState() {
        viewModel.setSearchQuery("network error")
        assertEquals("network error", viewModel.searchQuery.value)
    }

    @Test
    fun testSetSelectedPriorityUpdatesState() {
        viewModel.setSelectedPriority(6)
        assertEquals(6, viewModel.selectedPriority.value)
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
