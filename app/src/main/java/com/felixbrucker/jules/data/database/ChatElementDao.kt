package com.felixbrucker.jules.data.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatElementDao {
    @Upsert
    suspend fun upsertElements(entities: List<ChatElementEntity>)

    @Query("SELECT * FROM chat_elements WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun getChatElementsPagingSource(sessionId: String): PagingSource<Int, ChatElementEntity>

    @Query("SELECT * FROM chat_elements WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun getChatElementsFlow(sessionId: String): Flow<List<ChatElementEntity>>

    @Query("DELETE FROM chat_elements WHERE sessionId = :sessionId")
    suspend fun deleteElementsForSession(sessionId: String)
}
