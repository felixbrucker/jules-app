package com.felixbrucker.jules.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatStateDao {
    @Upsert
    suspend fun upsertChatState(entity: ChatStateEntity)

    @Query("SELECT * FROM chat_states WHERE sessionId = :sessionId")
    fun getChatStateFlow(sessionId: String): Flow<ChatStateEntity?>

    @Query("SELECT * FROM chat_states WHERE sessionId = :sessionId")
    suspend fun getChatState(sessionId: String): ChatStateEntity?
}
