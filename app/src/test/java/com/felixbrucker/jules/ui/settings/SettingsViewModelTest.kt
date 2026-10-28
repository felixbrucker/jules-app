package com.felixbrucker.jules.ui.settings

import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.data.worker.ScheduledTaskScheduler
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var julesRepository: JulesRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var scheduledTaskScheduler: ScheduledTaskScheduler
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        julesRepository = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        scheduledTaskScheduler = mockk(relaxed = true)

        coEvery { appSettingsRepository.periodicSyncEnabledFlow } returns flowOf(false)
        coEvery { appSettingsRepository.syncIntervalMinutesFlow } returns flowOf(15)
        coEvery { appSettingsRepository.smartSyncEnabledFlow } returns flowOf(true)
        coEvery { appSettingsRepository.feedbackNotificationsEnabledFlow } returns flowOf(true)
        coEvery { appSettingsRepository.completionNotificationsEnabledFlow } returns flowOf(true)
        coEvery { appSettingsRepository.workflowDispatchingEnabledFlow } returns flowOf(false)
        coEvery { appSettingsRepository.dailySessionLimitFlow } returns flowOf(15)
        coEvery { appSettingsRepository.apiKeyFlow } returns flowOf("test-key")
        coEvery { appSettingsRepository.githubAuthTokenFlow } returns flowOf("test-token")

        viewModel = SettingsViewModel(julesRepository, appSettingsRepository, scheduledTaskScheduler)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testWorkflowDispatchingEnabledDefaultsToFalse() = runTest {
        val collector = launch { viewModel.workflowDispatchingEnabled.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.workflowDispatchingEnabled.value)
        collector.cancel()
    }

    @Test
    fun testSetWorkflowDispatchingEnabledCallsRepository() = runTest {
        viewModel.setWorkflowDispatchingEnabled(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { appSettingsRepository.setWorkflowDispatchingEnabled(true) }
    }
}
