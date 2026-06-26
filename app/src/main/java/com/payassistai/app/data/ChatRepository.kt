package com.payassistai.app.data

class ChatRepository(
    private val messageDao: ChatMessageDao,
    private val sessionDao: ChatSessionDao
) {
    // ---- Messages ----
    suspend fun insertMessage(message: ChatMessage) = messageDao.insert(message)
    suspend fun deleteMessage(message: ChatMessage) = messageDao.delete(message)

    fun getMessagesForSession(sessionId: Int) =
        messageDao.getMessagesForSession(sessionId)

    suspend fun deleteMessagesForSession(sessionId: Int) =
        messageDao.deleteMessagesForSession(sessionId)

    // ---- Sessions ----
    suspend fun insertSession(session: ChatSession) = sessionDao.insert(session)
    suspend fun updateSession(session: ChatSession) = sessionDao.update(session)
    suspend fun deleteSession(session: ChatSession) = sessionDao.delete(session)

    // Merchant‑specific sessions with messages (for history)
    fun getSessionsWithMessagesForMerchant(merchantId: Int) =
        sessionDao.getSessionsWithMessagesForMerchant(merchantId)

    suspend fun getSessionById(id: Int): ChatSession? = sessionDao.getSessionById(id)
}