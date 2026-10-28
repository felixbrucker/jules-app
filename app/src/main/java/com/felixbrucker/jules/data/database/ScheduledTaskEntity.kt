package com.felixbrucker.jules.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.time.Instant
import java.util.UUID

enum class ScheduleType {
    ONCE,
    DAILY,
    WEEKLY,
    MONTHLY
}

@JsonClass(generateAdapter = true)
data class ScheduledTaskRepo(
    val sourceId: String,
    val branch: String
)

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String? = null,
    val prompt: String = "",
    val repos: List<ScheduledTaskRepo> = emptyList(),
    val requirePlanApproval: Boolean = false,
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val timeHour: Int = 9,
    val timeMinute: Int = 0,
    val date: String? = null,
    val dayOfWeek: Int? = null,
    val dayOfMonth: Int? = null,
    val enabled: Boolean = true,
    val lastRunAt: Instant? = null,
    val updatedAt: Instant = Instant.now()
)
