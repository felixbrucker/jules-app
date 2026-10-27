package com.felixbrucker.jules.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.felixbrucker.jules.data.database.LogDao
import com.felixbrucker.jules.data.database.LogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    private val logDao: LogDao
) {
    fun getLogsPaged(
        priorities: Set<Int>,
        searchQuery: String,
        pageSize: Int = 15
    ): Flow<PagingData<LogEntity>> {
        val priorityList = priorities.toList()
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                logDao.getFilteredLogs(
                    prioritiesEmpty = priorityList.isEmpty(),
                    priorities = priorityList,
                    searchQuery = searchQuery.trim()
                )
            }
        ).flow
    }

    fun getTotalCountFlow(): Flow<Int> = logDao.getTotalCountFlow()

    fun getFilteredCountFlow(priorities: Set<Int>, searchQuery: String): Flow<Int> {
        val priorityList = priorities.toList()
        return logDao.getFilteredCountFlow(
            prioritiesEmpty = priorityList.isEmpty(),
            priorities = priorityList,
            searchQuery = searchQuery.trim()
        )
    }

    suspend fun insertLog(entry: LogEntity) = withContext(Dispatchers.IO) {
        logDao.insertLog(entry)
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        try {
            logDao.clearAllLogs()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear logs")
            throw e
        }
    }

    suspend fun pruneOldLogs(daysToKeep: Long = 7) = withContext(Dispatchers.IO) {
        try {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysToKeep)
            logDao.deleteLogsOlderThan(cutoff)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to prune old logs")
            throw e
        }
    }

    companion object {
        private const val TAG = "LogRepository"
    }
}
