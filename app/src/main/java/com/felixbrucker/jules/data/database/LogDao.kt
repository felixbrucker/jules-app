package com.felixbrucker.jules.data.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<LogEntity>)

    @Query(
        """
        SELECT * FROM logs
        WHERE (:prioritiesEmpty = 1 OR priority IN (:priorities))
          AND (
            :searchQuery IS NULL OR :searchQuery = ''
            OR tag LIKE '%' || :searchQuery || '%'
            OR message LIKE '%' || :searchQuery || '%'
            OR throwableStackTrace LIKE '%' || :searchQuery || '%'
          )
        ORDER BY timestamp DESC, id DESC
        """
    )
    fun getFilteredLogs(
        prioritiesEmpty: Boolean,
        priorities: List<Int>,
        searchQuery: String?
    ): PagingSource<Int, LogEntity>

    @Query("SELECT COUNT(*) FROM logs")
    suspend fun getTotalCount(): Int

    @Query(
        """
        SELECT COUNT(*) FROM logs
        WHERE (:prioritiesEmpty = 1 OR priority IN (:priorities))
          AND (
            :searchQuery IS NULL OR :searchQuery = ''
            OR tag LIKE '%' || :searchQuery || '%'
            OR message LIKE '%' || :searchQuery || '%'
            OR throwableStackTrace LIKE '%' || :searchQuery || '%'
          )
        """
    )
    suspend fun getFilteredCount(
        prioritiesEmpty: Boolean,
        priorities: List<Int>,
        searchQuery: String?
    ): Int

    @Query("DELETE FROM logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM logs WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteLogsOlderThan(cutoffTimestamp: Long): Int
}
