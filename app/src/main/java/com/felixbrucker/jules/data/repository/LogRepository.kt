package com.felixbrucker.jules.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.felixbrucker.jules.data.database.LogDao
import com.felixbrucker.jules.data.database.LogEntity
import com.felixbrucker.jules.data.logging.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
        pageSize: Int = 30
    ): Flow<PagingData<LogEntry>> {
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
        ).flow.map { pagingData ->
            pagingData.map { it.toDomainModel() }
        }
    }

    suspend fun getCounts(priorities: Set<Int>, searchQuery: String): Pair<Int, Int> =
        withContext(Dispatchers.IO) {
            val priorityList = priorities.toList()
            val total = logDao.getTotalCount()
            val filtered = logDao.getFilteredCount(
                prioritiesEmpty = priorityList.isEmpty(),
                priorities = priorityList,
                searchQuery = searchQuery.trim()
            )
            Pair(filtered, total)
        }

    suspend fun insertLog(entry: LogEntry) = withContext(Dispatchers.IO) {
        logDao.insertLog(LogEntity.fromDomainModel(entry))
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        logDao.clearAllLogs()
    }

    suspend fun pruneOldLogs(daysToKeep: Long = 7) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysToKeep)
        logDao.deleteLogsOlderThan(cutoff)
    }

    companion object {
        private const val TAG = "LogRepository"
    }
}
