package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.felixbrucker.jules.network.api.model.SessionState
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Transaction
    @Query(
        """
        SELECT * FROM sessions 
        WHERE (:includeArchived = 1 OR isArchived = 0)
          AND (state IN (:states) OR :statesIsEmpty = 1)
          AND (:searchQuery = '%%' OR title LIKE :searchQuery OR prompt LIKE :searchQuery OR repo_owner LIKE :searchQuery OR repo_name LIKE :searchQuery)
        ORDER BY updateTime DESC, createTime DESC
    """
    )
    fun getSessions(
        searchQuery: String,
        states: List<SessionState>,
        statesIsEmpty: Boolean,
        includeArchived: Boolean
    ): Flow<List<SessionWithPullRequest>>

    @Query("SELECT COUNT(*) FROM sessions WHERE createTime >= (CAST(strftime('%s', 'now') AS INTEGER) - 86400) * 1000")
    fun getDailySessionCountFlow(): Flow<Int>

    @Query("SELECT * FROM sessions WHERE id IN (:ids)")
    suspend fun getSessionsByIds(ids: List<String>): List<SessionEntity>

    @Query("SELECT id FROM sessions")
    suspend fun getAllSessionIds(): List<String>

    @Query("SELECT id FROM sessions WHERE isArchived = 0")
    suspend fun getAllActiveSessionIds(): List<String>

    @Query("SELECT id FROM sessions WHERE isArchived = 1")
    suspend fun getAllArchivedSessionIds(): List<String>

    @Query(
        """
        SELECT s.* FROM sessions s
        LEFT JOIN sources src ON s.sourceId = src.id
        WHERE s.isArchived = 0
          AND s.pull_request_url IS NOT NULL
          AND (:includePrivate = 1 OR src.github_repo_isPrivate = 0 OR src.github_repo_isPrivate IS NULL)
        """
    )
    suspend fun getActiveSessionsWithPullRequests(includePrivate: Boolean): List<SessionEntity>

    @Insert
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Update
    suspend fun updateSessions(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("UPDATE sessions SET isArchived = :isArchived WHERE id = :sessionId")
    suspend fun updateArchived(sessionId: String, isArchived: Boolean)
}
