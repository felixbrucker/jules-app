package com.felixbrucker.jules.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.felixbrucker.jules.data.repository.JulesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScheduledTaskReceiver : BroadcastReceiver() {

    @Inject
    lateinit var julesRepository: JulesRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_EXECUTE_TASK) {
            val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    julesRepository.executeScheduledTask(taskId)
                } catch (_: Exception) {
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_EXECUTE_TASK = "com.felixbrucker.jules.EXECUTE_SCHEDULED_TASK"
        const val EXTRA_TASK_ID = "task_id"
    }
}
