package com.felixbrucker.jules.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.repository.JulesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class JulesSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val julesRepository: JulesRepository,
    private val appSettingsRepository: AppSettingsRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val apiKey = appSettingsRepository.apiKeyFlow.first()
            if (apiKey.isNullOrBlank()) {
                return Result.success()
            }

            julesRepository.syncSessions()

            val archivedSyncEnabled = appSettingsRepository.archivedSyncEnabledFlow.first()
            if (archivedSyncEnabled) {
                julesRepository.syncArchivedSessions()
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "JulesSyncWorker failed")
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "jules_periodic_sync"

        fun enqueuePeriodicSync(context: Context, intervalMinutes: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<JulesSyncWorker>(
                intervalMinutes,
                TimeUnit.MINUTES
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
