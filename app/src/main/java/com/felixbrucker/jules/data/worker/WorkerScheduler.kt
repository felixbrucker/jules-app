package com.felixbrucker.jules.data.worker

import android.content.Context
import com.felixbrucker.jules.data.database.SessionDao
import com.felixbrucker.jules.data.datastore.AppSettings
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class WorkerScheduleDecision(
    val shouldEnqueue: Boolean,
    val intervalMinutes: Long
)

@Singleton
class WorkerScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettingsRepository: AppSettingsRepository,
    private val sessionDao: SessionDao
) {
    fun evaluate(
        settings: AppSettings,
        hasInProgress: Boolean
    ): WorkerScheduleDecision {
        val isValid = settings.periodicSyncEnabled && !settings.apiKey.isNullOrBlank()
        val shouldEnqueue = isValid && (!settings.smartSyncEnabled || hasInProgress)
        return WorkerScheduleDecision(shouldEnqueue, settings.syncIntervalMinutes.toLong())
    }

    suspend fun enqueueIfNeeded(isSelfEnqueue: Boolean = false) {
        val settings = appSettingsRepository.appSettingsFlow.first()
        val hasInProgress = sessionDao.hasActiveInProgressSessionsFlow().first()
        val decision = evaluate(settings, hasInProgress)
        if (decision.shouldEnqueue) {
            JulesSyncWorker.enqueueSyncWork(
                context = context,
                intervalMinutes = decision.intervalMinutes,
                isSelfEnqueue = isSelfEnqueue
            )
        }
    }
}
