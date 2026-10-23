package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.felixbrucker.jules.network.api.model.SessionState
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
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
    ): Flow<List<SessionEntity>>

    @Query("SELECT id FROM sessions WHERE isArchived = 0")
    suspend fun getAllActiveSessionIds(): List<String>

    @Query("SELECT id FROM sessions WHERE isArchived = 1")
    suspend fun getAllArchivedSessionIds(): List<String>

    @Upsert
    suspend fun upsertSessions(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("UPDATE sessions SET isArchived = :isArchived WHERE id = :sessionId")
    suspend fun updateArchived(sessionId: String, isArchived: Boolean)
}
