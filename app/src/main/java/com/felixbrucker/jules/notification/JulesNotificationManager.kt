package com.felixbrucker.jules.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.felixbrucker.jules.MainActivity
import com.felixbrucker.jules.R
import com.felixbrucker.jules.data.database.SessionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.zip.CRC32
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JulesNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Jules Task Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for task state updates"
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun sendTaskFeedbackNotification(session: SessionEntity) {
        val notificationId = getNotificationId(session.id)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = session.title ?: "Session Feedback Required"
        val body = "Task state updated: ${session.state.label}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_feedback)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun sendScheduledTaskExecutedNotification(
        taskTitle: String?,
        repoTarget: String,
        sessionTitle: String?
    ) {
        val notificationId = getNotificationId("scheduled_exec_${taskTitle}_${repoTarget}_${System.currentTimeMillis()}")
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Scheduled Task Executed"
        val body = "Created session '${sessionTitle ?: taskTitle ?: "Untitled"}' for $repoTarget"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_completed)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun sendScheduledTaskErrorNotification(
        taskTitle: String?,
        repoTarget: String,
        errorMessage: String
    ) {
        val notificationId = getNotificationId("scheduled_err_${taskTitle}_${repoTarget}_${System.currentTimeMillis()}")
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Scheduled Task Failed (${taskTitle ?: "Task"})"
        val contentText = "Failed for $repoTarget: $errorMessage"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_feedback)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun sendTaskCompletedNotification(session: SessionEntity) {
        val notificationId = getNotificationId(session.id)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = session.title ?: "Session Completed"
        val body = "Task completed successfully"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_completed)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun getNotificationId(sessionId: String): Int {
        val crc = CRC32()
        crc.update(sessionId.toByteArray())
        return (crc.value and 0x7FFFFFFF).toInt()
    }

    companion object {
        const val CHANNEL_ID = "jules_task_notifications"
    }
}
