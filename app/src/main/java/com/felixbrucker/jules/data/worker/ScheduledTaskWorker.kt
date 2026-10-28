package com.felixbrucker.jules.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.felixbrucker.jules.data.repository.JulesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class ScheduledTaskWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val julesRepository: JulesRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)
        if (taskId.isNullOrBlank()) {
            Timber.tag(TAG).e("ScheduledTaskWorker missing task_id in input data")
            return Result.failure()
        }

        return try {
            Timber.tag(TAG).i("ScheduledTaskWorker executing task $taskId")
            julesRepository.executeScheduledTask(taskId)
            Result.success()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in ScheduledTaskWorker for task $taskId")
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "ScheduledTaskWorker"
        const val KEY_TASK_ID = "task_id"
    }
}
