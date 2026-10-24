package com.felixbrucker.jules.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.felixbrucker.jules.data.logging.LogEntry

@Entity(tableName = "logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val priority: Int,
    val tag: String?,
    val message: String,
    val throwableStackTrace: String?
) {
    fun toDomainModel(): LogEntry = LogEntry(
        id = id,
        timestamp = timestamp,
        priority = priority,
        tag = tag,
        message = message,
        throwableStackTrace = throwableStackTrace
    )

    companion object {
        fun fromDomainModel(entry: LogEntry): LogEntity = LogEntity(
            id = entry.id,
            timestamp = entry.timestamp,
            priority = entry.priority,
            tag = entry.tag,
            message = entry.message,
            throwableStackTrace = entry.throwableStackTrace
        )
    }
}
