package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDraftDao {
    @Upsert
    suspend fun upsertDraft(draft: SessionDraftEntity)

    @Transaction
    @Query(
        """
        SELECT d.* FROM session_drafts d
        LEFT JOIN sources s ON d.sourceId = s.id
        WHERE :searchQuery = '%%'
           OR d.title LIKE :searchQuery
           OR d.prompt LIKE :searchQuery
           OR d.sourceId LIKE :searchQuery
           OR s.name LIKE :searchQuery
           OR (s.github_repo_owner || '/' || s.github_repo_repo) LIKE :searchQuery
        ORDER BY d.updatedAt DESC
        """
    )
    fun getDrafts(searchQuery: String): Flow<List<SessionDraftWithSource>>

    @Query("SELECT * FROM session_drafts WHERE id = :id")
    suspend fun getDraftById(id: String): SessionDraftEntity?

    @Query("DELETE FROM session_drafts WHERE id = :id")
    suspend fun deleteDraft(id: String)
}
