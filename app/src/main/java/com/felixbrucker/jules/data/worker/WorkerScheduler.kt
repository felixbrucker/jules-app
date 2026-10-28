package com.felixbrucker.jules.data.worker

import com.felixbrucker.jules.data.datastore.AppSettings

data class WorkerScheduleDecision(
    val shouldEnqueue: Boolean,
    val intervalMinutes: Long
)

object WorkerScheduler {
    fun evaluate(
        settings: AppSettings,
        hasInProgress: Boolean
    ): WorkerScheduleDecision {
        val isValid = settings.periodicSyncEnabled && !settings.apiKey.isNullOrBlank()
        val shouldEnqueue = isValid && (!settings.smartSyncEnabled || hasInProgress)
        return WorkerScheduleDecision(shouldEnqueue, settings.syncIntervalMinutes.toLong())
    }
}
