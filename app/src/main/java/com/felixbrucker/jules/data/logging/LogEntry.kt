package com.felixbrucker.jules.data.logging

import android.util.Log

data class LogEntry(
    val id: Long = 0,
    val timestamp: Long,
    val priority: Int,
    val tag: String?,
    val message: String,
    val throwableStackTrace: String? = null
) {
    fun priorityLabel(): String = when (priority) {
        Log.VERBOSE -> "VERBOSE"
        Log.DEBUG -> "DEBUG"
        Log.INFO -> "INFO"
        Log.WARN -> "WARN"
        Log.ERROR -> "ERROR"
        Log.ASSERT -> "ASSERT"
        else -> "UNKNOWN"
    }
}
