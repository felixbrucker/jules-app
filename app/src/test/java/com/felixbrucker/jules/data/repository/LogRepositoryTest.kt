package com.felixbrucker.jules.data.repository

import com.felixbrucker.jules.data.database.LogDao
import com.felixbrucker.jules.data.database.LogEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LogRepositoryTest {

    private lateinit var logDao: LogDao
    private lateinit var repository: LogRepository

    @Before
    fun setUp() {
        logDao = mockk(relaxed = true)
        repository = LogRepository(logDao)
    }

    @Test
    fun testInsertLogDelegatesToDao() = runBlocking {
        val entry = LogEntity(
            timestamp = 1000L,
            priority = 3,
            tag = "TestTag",
            message = "Test Message",
            throwableStackTrace = null
        )

        repository.insertLog(entry)

        coVerify {
            logDao.insertLog(match {
                it.timestamp == 1000L && it.priority == 3 && it.tag == "TestTag" && it.message == "Test Message"
            })
        }
    }

    @Test
    fun testClearLogsDelegatesToDao() = runBlocking {
        repository.clearLogs()

        coVerify {
            logDao.clearAllLogs()
        }
    }

    @Test
    fun testPruneOldLogsDeletesWithCutoffTimestamp() = runBlocking {
        val before = System.currentTimeMillis()
        repository.pruneOldLogs(daysToKeep = 7)

        coVerify {
            logDao.deleteLogsOlderThan(match { cutoff ->
                cutoff <= before - 7 * 24 * 60 * 60 * 1000L + 1000L
            })
        }
    }

    @Test
    fun testGetCountsFlowReturnsFilteredAndTotal() = runBlocking {
        coEvery { logDao.getTotalCountFlow() } returns flowOf(10)
        coEvery { logDao.getFilteredCountFlow(any(), any(), any()) } returns flowOf(5)

        val total = repository.getTotalCountFlow().first()
        val filtered = repository.getFilteredCountFlow(setOf(3, 4), "search").first()

        assertEquals(5, filtered)
        assertEquals(10, total)
        coVerify {
            logDao.getTotalCountFlow()
            logDao.getFilteredCountFlow(
                prioritiesEmpty = false,
                priorities = listOf(3, 4),
                searchQuery = "search"
            )
        }
    }
}
