package com.felixbrucker.jules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.felixbrucker.jules.data.worker.ScheduledTaskWorker
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class ScheduledTaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_EXECUTE_TASK) {
            val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
            Timber.tag(TAG).i("ScheduledTaskReceiver received trigger for task $taskId, enqueuing ScheduledTaskWorker")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ScheduledTaskWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(ScheduledTaskWorker.KEY_TASK_ID to taskId))
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }

    companion object {
        private const val TAG = "ScheduledTaskReceiver"
        const val ACTION_EXECUTE_TASK = "com.felixbrucker.jules.EXECUTE_SCHEDULED_TASK"
        const val EXTRA_TASK_ID = "task_id"
    }
}
