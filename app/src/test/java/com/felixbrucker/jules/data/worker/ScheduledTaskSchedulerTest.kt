package com.felixbrucker.jules.data.worker

import com.felixbrucker.jules.data.database.ScheduleType
import com.felixbrucker.jules.data.database.ScheduledTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduledTaskSchedulerTest {

    @Test
    fun testCalculateNextRunMillisDaily() {
        val now = Instant.parse("2026-03-30T10:00:00Z")
        val task = ScheduledTaskEntity(
            scheduleType = ScheduleType.DAILY,
            timeHour = 14,
            timeMinute = 30
        )

        val nextRunMillis = ScheduledTaskScheduler.calculateNextRunMillis(task, now)
        assertNotNull(nextRunMillis)

        val nextRunZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextRunMillis!!), ZoneId.systemDefault())
        assertEquals(14, nextRunZoned.hour)
        assertEquals(30, nextRunZoned.minute)
    }

    @Test
    fun testCalculateNextRunMillisOnceInPastReturnsNull() {
        val now = Instant.parse("2026-03-30T10:00:00Z")
        val task = ScheduledTaskEntity(
            scheduleType = ScheduleType.ONCE,
            timeHour = 9,
            timeMinute = 0,
            date = "2026-03-29"
        )

        val nextRunMillis = ScheduledTaskScheduler.calculateNextRunMillis(task, now)
        assertNull(nextRunMillis)
    }

    @Test
    fun testCalculateNextRunMillisDailyEarlyAlarmHasRunToday() {
        val now = Instant.parse("2026-03-30T08:58:00Z") // 8:58 AM (early alarm before 9:00 AM)
        val lastRunAt = Instant.parse("2026-03-30T08:58:01Z") // Just executed today
        val task = ScheduledTaskEntity(
            scheduleType = ScheduleType.DAILY,
            timeHour = 9,
            timeMinute = 0,
            lastRunAt = lastRunAt
        )

        val nextRunMillis = ScheduledTaskScheduler.calculateNextRunMillis(task, now)
        assertNotNull(nextRunMillis)

        val nextRunZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextRunMillis!!), ZoneId.systemDefault())
        // Should schedule for tomorrow (March 31st), not March 30th
        val nowLocalDate = now.atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(nowLocalDate.plusDays(1), nextRunZoned.toLocalDate())
        assertEquals(9, nextRunZoned.hour)
        assertEquals(0, nextRunZoned.minute)
    }

    @Test
    fun testCalculateNextRunMillisOnceInFutureReturnsTimestamp() {
        val now = Instant.parse("2026-03-30T10:00:00Z")
        val task = ScheduledTaskEntity(
            scheduleType = ScheduleType.ONCE,
            timeHour = 15,
            timeMinute = 0,
            date = "2026-04-01"
        )

        val nextRunMillis = ScheduledTaskScheduler.calculateNextRunMillis(task, now)
        assertNotNull(nextRunMillis)

        val nextRunZoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextRunMillis!!), ZoneId.systemDefault())
        assertEquals(LocalDate.of(2026, 4, 1), nextRunZoned.toLocalDate())
        assertEquals(15, nextRunZoned.hour)
        assertEquals(0, nextRunZoned.minute)
    }
}
