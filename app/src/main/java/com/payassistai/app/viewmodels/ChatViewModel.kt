package com.payassistai.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.payassistai.app.data.ChatMessage
import com.payassistai.app.data.ChatRepository
import com.payassistai.app.data.ChatSession
import com.payassistai.app.data.MerchantRepository
import com.payassistai.app.models.QuestionRequest
import com.payassistai.app.network.ApiService
import com.payassistai.app.util.PdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val merchantRepository: MerchantRepository,
    private val apiService: ApiService
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Int?>(null)
    val currentSessionId: StateFlow<Int?> = _currentSessionId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var sessionsJob: Job? = null
    private var messagesJob: Job? = null

    init {
        // Observe merchant changes
        viewModelScope.launch {
            merchantRepository.currentMerchant.collect { merchant ->
                if (merchant != null) {
                    loadSessionsForMerchant(merchant.id)
                    // If no session, create one
                    if (_currentSessionId.value == null) {
                        createNewSession()
                    }
                } else {
                    _sessions.value = emptyList()
                    _messages.value = emptyList()
                    _currentSessionId.value = null
                }
            }
        }
    }

    private fun loadSessionsForMerchant(merchantId: Int) {
        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch {
            chatRepository.getSessionsWithMessagesForMerchant(merchantId).collect { sessionList ->
                _sessions.value = sessionList
            }
        }
    }

    fun createNewSession() {
        val merchantId = merchantRepository.currentMerchant.value?.id ?: return
        viewModelScope.launch {
            try {
                val session = ChatSession(title = "New Chat", merchantId = merchantId)
                val id = chatRepository.insertSession(session)
                val newSession = chatRepository.getSessionById(id.toInt())
                if (newSession != null) {
                    _currentSessionId.value = newSession.id
                    _messages.value = emptyList()
                    loadMessagesForSession(newSession.id)
                }
            } catch (e: Exception) {
                _error.value = "Failed to create new session: ${e.message}"
            }
        }
    }

    fun switchToSession(sessionId: Int) {
        viewModelScope.launch {
            val merchant = merchantRepository.currentMerchant.value
            val session = chatRepository.getSessionById(sessionId)
            if (session != null && session.merchantId == merchant?.id) {
                _currentSessionId.value = sessionId
                loadMessagesForSession(sessionId)
            } else {
                _error.value = "You don't have permission to view this session"
            }
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            val merchant = merchantRepository.currentMerchant.value
            val session = chatRepository.getSessionById(sessionId)
            if (session != null && session.merchantId == merchant?.id) {
                chatRepository.deleteSession(session)
                chatRepository.deleteMessagesForSession(sessionId)
                if (_currentSessionId.value == sessionId) {
                    createNewSession()
                }
            } else {
                _error.value = "Cannot delete session"
            }
        }
    }

    fun updateSessionTitle(sessionId: Int, newTitle: String) {
        viewModelScope.launch {
            val session = chatRepository.getSessionById(sessionId)
            if (session != null) {
                chatRepository.updateSession(
                    session.copy(
                        title = newTitle,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun loadMessagesForSession(sessionId: Int) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessagesForSession(sessionId).collect { messageList ->
                _messages.value = messageList
            }
        }
    }

    fun sendMessage(text: String) {
        val currentId = _currentSessionId.value
        if (currentId == null) {
            createNewSession()
            return
        }

        viewModelScope.launch {
            try {
                val userMsg = ChatMessage(text = text, sender = "user", sessionId = currentId)
                chatRepository.insertMessage(userMsg)

                val session = chatRepository.getSessionById(currentId)
                session?.let { s ->
                    if (s.title == "New Chat" || s.title.isEmpty()) {
                        val newTitle = text.take(30) + if (text.length > 30) "…" else ""
                        chatRepository.updateSession(
                            s.copy(
                                title = newTitle,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }

                _isLoading.value = true

                var attempts = 0
                var success = false
                var lastException: Exception? = null

                while (attempts < 2 && !success) {
                    try {
                        val response = apiService.askQuestion(QuestionRequest(question = text))
                        val botText = buildString {
                            append(response.answer)
                            if (response.sources.isNotEmpty()) {
                                append("\n\n📎 Sources: ")
                                append(response.sources.joinToString(", "))
                            }
                            response.relevance?.let {
                                append("\n📊 Relevance: ${it.toInt()}%")
                            }
                        }
                        val botMsg =
                            ChatMessage(text = botText, sender = "bot", sessionId = currentId)
                        chatRepository.insertMessage(botMsg)

                        session?.let { s ->
                            chatRepository.updateSession(s.copy(updatedAt = System.currentTimeMillis()))
                        }
                        success = true
                    } catch (e: IOException) {
                        lastException = e
                        attempts++
                        if (attempts < 2) kotlinx.coroutines.delay(2000)
                    } catch (e: Exception) {
                        lastException = e
                        break
                    }
                }

                if (!success) {
                    val errorMsg = if (lastException is IOException) {
                        ChatMessage(
                            text = "⚠️ Network error. Make sure the backend is running.\n\nRun: python main.py",
                            sender = "bot",
                            sessionId = currentId
                        )
                    } else {
                        ChatMessage(
                            text = "⚠️ Error: ${lastException?.message}",
                            sender = "bot",
                            sessionId = currentId
                        )
                    }
                    chatRepository.insertMessage(errorMsg)
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Failed to send message: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    fun exportChatAsPDF(context: Context) {
        val merchantName = merchantRepository.currentMerchant.value?.name ?: "Unknown"
        PdfExporter.exportChatAsPdf(context, _messages.value, merchantName)
    }
}