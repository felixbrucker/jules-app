package com.felixbrucker.jules.data.worker

import android.content.Context
import com.felixbrucker.jules.data.database.SessionDao
import com.felixbrucker.jules.data.datastore.AppSettings
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkerSchedulerTest {

    private lateinit var context: Context
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var sessionDao: SessionDao
    private lateinit var workerScheduler: WorkerScheduler

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        appSettingsRepository = mockk(relaxed = true)
        sessionDao = mockk(relaxed = true)

        workerScheduler = WorkerScheduler(
            context = context,
            appSettingsRepository = appSettingsRepository,
            sessionDao = sessionDao
        )

        mockkObject(JulesSyncWorker.Companion)
        every { JulesSyncWorker.enqueueSyncWork(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(JulesSyncWorker.Companion)
    }

    @Test
    fun testEvaluateWithValidSettingsAndSmartSyncDisabled() {
        val settings = AppSettings(
            apiKey = "test-key",
            periodicSyncEnabled = true,
            syncIntervalMinutes = 15,
            smartSyncEnabled = false,
            dailySessionLimit = 15,
            feedbackNotificationsEnabled = true,
            completionNotificationsEnabled = true
        )

        val decision = workerScheduler.evaluate(settings, hasInProgress = false)

        assertTrue(decision.shouldEnqueue)
        assertEquals(15L, decision.intervalMinutes)
    }

    @Test
    fun testEvaluateWithSmartSyncEnabledAndNoInProgress() {
        val settings = AppSettings(
            apiKey = "test-key",
            periodicSyncEnabled = true,
            syncIntervalMinutes = 15,
            smartSyncEnabled = true,
            dailySessionLimit = 15,
            feedbackNotificationsEnabled = true,
            completionNotificationsEnabled = true
        )

        val decision = workerScheduler.evaluate(settings, hasInProgress = false)

        assertFalse(decision.shouldEnqueue)
    }

    @Test
    fun testEvaluateWithSmartSyncEnabledAndHasInProgress() {
        val settings = AppSettings(
            apiKey = "test-key",
            periodicSyncEnabled = true,
            syncIntervalMinutes = 15,
            smartSyncEnabled = true,
            dailySessionLimit = 15,
            feedbackNotificationsEnabled = true,
            completionNotificationsEnabled = true
        )

        val decision = workerScheduler.evaluate(settings, hasInProgress = true)

        assertTrue(decision.shouldEnqueue)
    }

    @Test
    fun testEnqueueIfNeededEnqueuesWhenShouldEnqueueIsTrue() = runBlocking {
        val settings = AppSettings(
            apiKey = "test-key",
            periodicSyncEnabled = true,
            syncIntervalMinutes = 30,
            smartSyncEnabled = false,
            dailySessionLimit = 15,
            feedbackNotificationsEnabled = true,
            completionNotificationsEnabled = true
        )

        coEvery { appSettingsRepository.appSettingsFlow } returns flowOf(settings)
        coEvery { sessionDao.hasActiveInProgressSessionsFlow() } returns flowOf(false)

        workerScheduler.enqueueIfNeeded(isSelfEnqueue = true)

        verify {
            JulesSyncWorker.enqueueSyncWork(
                context = context,
                intervalMinutes = 30L,
                isSelfEnqueue = true
            )
        }
    }
}
