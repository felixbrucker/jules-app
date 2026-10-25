package com.felixbrucker.jules

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.felixbrucker.jules.data.database.SessionDao
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.logging.AppLogTree
import com.felixbrucker.jules.data.repository.LogRepository
import com.felixbrucker.jules.data.worker.JulesSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class JulesApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var logRepository: LogRepository

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository

    @Inject
    lateinit var sessionDao: SessionDao

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Timber.plant(AppLogTree(logRepository, applicationScope))
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        applicationScope.launch {
            try {
                logRepository.pruneOldLogs(daysToKeep = 7)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to prune old logs on app startup")
            }
        }

        applicationScope.launch {
            combine(
                appSettingsRepository.appSettingsFlow,
                sessionDao.hasActiveInProgressSessionsFlow()
            ) { settings, hasInProgress ->
                val isValid = settings.periodicSyncEnabled && !settings.apiKey.isNullOrBlank()
                val shouldEnqueue = if (!isValid) {
                    false
                } else if (!settings.smartSyncEnabled) {
                    true
                } else {
                    hasInProgress
                }
                WorkerScheduleDecision(shouldEnqueue, settings.syncIntervalMinutes.toLong())
            }
                .distinctUntilChanged()
                .collect { decision ->
                    if (decision.shouldEnqueue) {
                        JulesSyncWorker.enqueuePeriodicSync(applicationContext, decision.intervalMinutes)
                    } else {
                        JulesSyncWorker.cancelPeriodicSync(applicationContext)
                    }
                }
        }
    }

    companion object {
        private const val TAG = "JulesApplication"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

private data class WorkerScheduleDecision(
    val shouldEnqueue: Boolean,
    val intervalMinutes: Long
)
