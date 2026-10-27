package com.felixbrucker.jules.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.felixbrucker.jules.data.database.SessionDao
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.util.AppState
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

suspend fun WorkManager.isUniqueTaskEnqueued(uniqueName: String): Boolean {
    val workInfos = getWorkInfosForUniqueWorkFlow(uniqueName).first()
    return workInfos.any { !it.state.isFinished }
}

@HiltWorker
class JulesSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val julesRepository: JulesRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val sessionDao: SessionDao,
    private val appState: AppState
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (appState.isForeground) {
            Timber.tag(TAG).d("App is in foreground, skipping background sync in worker")
            return Result.success()
        }
        Timber.tag(TAG).d("Starting sync in worker")
        return try {
            val settings = appSettingsRepository.appSettingsFlow.first()
            if (settings.apiKey.isNullOrBlank()) {
                return Result.success()
            }

            julesRepository.sync(isBackground = true)

            val hasInProgress = sessionDao.hasActiveInProgressSessionsFlow().first()
            val decision = WorkerScheduler.evaluate(settings, hasInProgress)

            if (decision.shouldEnqueue) {
                enqueueSyncWork(applicationContext, decision.intervalMinutes, isSelfEnqueue = true)
            }

            Timber.tag(TAG).d("Finished sync in worker")
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "JulesSyncWorker failed")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "JulesSyncWorker"
        const val WORK_NAME = "jules_sync_worker"

        fun enqueueSyncWork(context: Context, intervalMinutes: Long, isSelfEnqueue: Boolean = false) {
            Timber.tag(TAG).d("Scheduling sync worker with interval %d minutes (isSelfEnqueue=%b)", intervalMinutes, isSelfEnqueue)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<JulesSyncWorker>()
                .setConstraints(constraints)
                .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
                .build()

            val workManager = WorkManager.getInstance(context)
            if (isSelfEnqueue) {
                workManager.enqueue(workRequest)
            } else {
                workManager.enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
        }

        suspend fun cancelSyncWork(context: Context) {
            val workManager = WorkManager.getInstance(context)
            if (workManager.isUniqueTaskEnqueued(WORK_NAME)) {
                Timber.tag(TAG).d("Canceling sync worker")
                workManager.cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
