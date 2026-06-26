package com.payassistai.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Insert
    suspend fun insert(session: ChatSession): Long

    @Update
    suspend fun update(session: ChatSession)

    @Delete
    suspend fun delete(session: ChatSession)

    @Query("""
        SELECT s.* FROM chat_sessions s
        WHERE s.merchantId = :merchantId
        AND EXISTS (SELECT 1 FROM messages m WHERE m.sessionId = s.id)
        ORDER BY s.updatedAt DESC
    """)
    fun getSessionsWithMessagesForMerchant(merchantId: Int): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSessionById(id: Int): ChatSession?
}