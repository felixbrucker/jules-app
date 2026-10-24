package com.felixbrucker.jules.data.logging

import android.util.Log
import com.felixbrucker.jules.data.repository.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class AppLogTree(
    private val logRepository: LogRepository,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val stackTrace = t?.let { Log.getStackTraceString(it) }
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            priority = priority,
            tag = tag,
            message = message,
            throwableStackTrace = if (stackTrace.isNull_or_blank_safe()) null else stackTrace
        )

        externalScope.launch {
            try {
                logRepository.insertLog(entry)
            } catch (_: Exception) {
            }
        }
    }

    private fun String?.isNull_or_blank_safe(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
