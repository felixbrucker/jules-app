package com.felixbrucker.jules.data.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.felixbrucker.jules.data.database.ScheduleType
import com.felixbrucker.jules.data.database.ScheduledTaskDao
import com.felixbrucker.jules.data.database.ScheduledTaskEntity
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.receiver.ScheduledTaskReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledTaskScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduledTaskDao: ScheduledTaskDao,
    private val appSettingsRepository: AppSettingsRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun scheduleTask(task: ScheduledTaskEntity) {
        val pendingIntent = createPendingIntent(task.id)
        if (!task.enabled) {
            Timber.tag(TAG).i("Task ${task.id} ('${task.title ?: "Untitled"}') is disabled. Cancelling alarm.")
            alarmManager.cancel(pendingIntent)
            return
        }

        val nextRunMillis = calculateNextRunMillis(task, Instant.now())
        if (nextRunMillis == null) {
            Timber.tag(TAG).i("No future run time for task ${task.id} ('${task.title ?: "Untitled"}'). Cancelling alarm.")
            alarmManager.cancel(pendingIntent)
            return
        }

        val useExact = appSettingsRepository.useExactAlarmsFlow.first()
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (useExact && canExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextRunMillis,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextRunMillis,
                pendingIntent
            )
        }
        val nextRunFormatted = Instant.ofEpochMilli(nextRunMillis)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))
        Timber.tag(TAG).i("Scheduled task ${task.id} ('${task.title ?: "Untitled"}', ${task.scheduleType}) for $nextRunFormatted (exact=$useExact, canExact=$canExact)")
    }

    fun cancelTask(taskId: String) {
        val pendingIntent = createPendingIntent(taskId)
        alarmManager.cancel(pendingIntent)
        Timber.tag(TAG).i("Cancelled alarm for task $taskId")
    }

    suspend fun rescheduleAllTasks() {
        val tasks = scheduledTaskDao.getAllEnabledTasks()
        Timber.tag(TAG).i("Rescheduling ${tasks.size} enabled tasks...")
        for (task in tasks) {
            scheduleTask(task)
        }
    }

    private fun createPendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, ScheduledTaskReceiver::class.java).apply {
            action = ScheduledTaskReceiver.ACTION_EXECUTE_TASK
            putExtra(ScheduledTaskReceiver.EXTRA_TASK_ID, taskId)
        }
        val requestCode = taskId.hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "ScheduledTaskScheduler"
        fun calculateNextRunMillis(task: ScheduledTaskEntity, now: Instant): Long? {
            val zoneId = ZoneId.systemDefault()
            val nowZoned = ZonedDateTime.ofInstant(now, zoneId)
            val time = LocalTime.of(task.timeHour, task.timeMinute)

            val nextDateTime: ZonedDateTime = when (task.scheduleType) {
                ScheduleType.ONCE -> {
                    if (task.lastRunAt != null) return null
                    val dateStr = task.date ?: return null
                    val localDate = try {
                        LocalDate.parse(dateStr)
                    } catch (_: Exception) {
                        return null
                    }
                    val targetZoned = ZonedDateTime.of(LocalDateTime.of(localDate, time), zoneId)
                    if (targetZoned.isBefore(nowZoned)) return null
                    targetZoned
                }
                ScheduleType.DAILY -> {
                    var target = ZonedDateTime.of(LocalDateTime.of(nowZoned.toLocalDate(), time), zoneId)
                    if (!target.isAfter(nowZoned)) {
                        target = target.plusDays(1)
                    }
                    target
                }
                ScheduleType.WEEKLY -> {
                    val targetDay = task.dayOfWeek?.let { DayOfWeek.of(it.coerceIn(1, 7)) } ?: DayOfWeek.MONDAY
                    var targetDate = nowZoned.toLocalDate()
                    var target = ZonedDateTime.of(LocalDateTime.of(targetDate, time), zoneId)
                    while (targetDate.dayOfWeek != targetDay || !target.isAfter(nowZoned)) {
                        targetDate = targetDate.plusDays(1)
                        target = ZonedDateTime.of(LocalDateTime.of(targetDate, time), zoneId)
                    }
                    target
                }
                ScheduleType.MONTHLY -> {
                    val targetDayNum = task.dayOfMonth ?: 1
                    var targetYearMonth = nowZoned.toLocalDate().year to nowZoned.toLocalDate().monthValue

                    fun getTargetDate(year: Int, month: Int): LocalDate {
                        val maxDays = LocalDate.of(year, month, 1).lengthOfMonth()
                        val day = targetDayNum.coerceIn(1, maxDays)
                        return LocalDate.of(year, month, day)
                    }

                    var targetDate = getTargetDate(targetYearMonth.first, targetYearMonth.second)
                    var target = ZonedDateTime.of(LocalDateTime.of(targetDate, time), zoneId)
                    if (!target.isAfter(nowZoned)) {
                        val nextMonthDate = nowZoned.toLocalDate().plusMonths(1)
                        targetDate = getTargetDate(nextMonthDate.year, nextMonthDate.monthValue)
                        target = ZonedDateTime.of(LocalDateTime.of(targetDate, time), zoneId)
                    }
                    target
                }
            }

            return nextDateTime.toInstant().toEpochMilli()
        }
    }
}
