package com.optix.app.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.core.util.Resource
import com.optix.app.domain.model.ChatContext
import com.optix.app.domain.model.ChatError
import com.optix.app.domain.model.ChatSession
import com.optix.app.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val error: String? = null,
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String? = null,
    val isLoadingSessions: Boolean = false,
    val marketContext: ChatContext? = null,
    val suggestedQuestions: List<String> = listOf(
        "What is PCR and how to use it?",
        "Explain Greeks in options",
        "Best strategy for sideways market?",
        "How to identify support/resistance?",
        "What is Max Pain theory?"
    )
)

sealed class ChatEvent {
    data class ShowSnackbar(val message: String) : ChatEvent()
    object ScrollToBottom : ChatEvent()
    object SessionDeleted : ChatEvent()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>()
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    init {
        addWelcomeMessage()
    }

    private fun addWelcomeMessage() {
        _state.update {
            it.copy(
                messages = listOf(
                    ChatMessage(
                        content = "Hi! I'm Optixia, your AI trading assistant. I can help you with:\n\n" +
                                "- Options trading concepts\n" +
                                "- Greeks explanation\n" +
                                "- Strategy suggestions\n" +
                                "- Market analysis\n" +
                                "- Risk management\n\n" +
                                "How can I help you today?",
                        isFromUser = false
                    )
                )
            )
        }
    }

    /**
     * Update market context for contextual AI responses
     */
    fun updateMarketContext(context: ChatContext) {
        _state.update { it.copy(marketContext = context) }
    }

    /**
     * Send a message to the AI
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        // Add user message
        val userMessage = ChatMessage(content = message, isFromUser = true)
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                isTyping = true,
                error = null
            )
        }

        viewModelScope.launch {
            _events.emit(ChatEvent.ScrollToBottom)

            // Use quickChat API (stateless) for simplicity
            chatRepository.quickChat(
                message = message,
                context = _state.value.marketContext
            ).collect { result ->
                when (result) {
                    is Resource.Loading -> {
                        // Already showing typing indicator
                    }
                    is Resource.Success -> {
                        val aiMessage = ChatMessage(
                            content = result.data ?: "I couldn't generate a response. Please try again.",
                            isFromUser = false
                        )
                        _state.update {
                            it.copy(
                                messages = it.messages + aiMessage,
                                isTyping = false
                            )
                        }
                        _events.emit(ChatEvent.ScrollToBottom)
                    }
                    is Resource.Error -> {
                        val error = ChatError.fromException(Exception(result.message))
                        val errorMessage = ChatMessage(
                            content = error.message,
                            isFromUser = false,
                            isError = true
                        )
                        _state.update {
                            it.copy(
                                messages = it.messages + errorMessage,
                                isTyping = false,
                                error = error.message
                            )
                        }
                        _events.emit(ChatEvent.ShowSnackbar(error.message))
                    }
                }
            }
        }
    }

    /**
     * Send message with session management
     */
    fun sendMessageWithSession(message: String) {
        if (message.isBlank()) return

        val userMessage = ChatMessage(content = message, isFromUser = true)
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                isTyping = true,
                error = null
            )
        }

        viewModelScope.launch {
            _events.emit(ChatEvent.ScrollToBottom)

            chatRepository.sendMessage(
                message = message,
                sessionId = _state.value.currentSessionId,
                context = _state.value.marketContext
            ).collect { result ->
                when (result) {
                    is Resource.Loading -> { }
                    is Resource.Success -> {
                        val (reply, sessionId) = result.data ?: Pair("", null)
                        val aiMessage = ChatMessage(
                            content = reply.ifBlank { "I couldn't generate a response." },
                            isFromUser = false
                        )
                        _state.update {
                            it.copy(
                                messages = it.messages + aiMessage,
                                isTyping = false,
                                currentSessionId = sessionId
                            )
                        }
                        _events.emit(ChatEvent.ScrollToBottom)
                    }
                    is Resource.Error -> {
                        val error = ChatError.fromException(Exception(result.message))
                        _state.update {
                            it.copy(
                                isTyping = false,
                                error = error.message
                            )
                        }
                        _events.emit(ChatEvent.ShowSnackbar(error.message))
                    }
                }
            }
        }
    }

    /**
     * Ask a suggested question
     */
    fun askSuggestedQuestion(question: String) {
        sendMessage(question)
    }

    /**
     * Load chat sessions
     */
    fun loadSessions() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingSessions = true) }

            chatRepository.getSessions().collect { result ->
                when (result) {
                    is Resource.Loading -> { }
                    is Resource.Success -> {
                        _state.update {
                            it.copy(
                                sessions = result.data ?: emptyList(),
                                isLoadingSessions = false
                            )
                        }
                    }
                    is Resource.Error -> {
                        _state.update { it.copy(isLoadingSessions = false) }
                        _events.emit(ChatEvent.ShowSnackbar("Failed to load sessions"))
                    }
                }
            }
        }
    }

    /**
     * Delete a session
     */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _state.update {
                            it.copy(
                                sessions = it.sessions.filter { s -> s.id != sessionId }
                            )
                        }
                        // If deleted current session, start new chat
                        if (_state.value.currentSessionId == sessionId) {
                            startNewChat()
                        }
                        _events.emit(ChatEvent.SessionDeleted)
                    }
                    is Resource.Error -> {
                        _events.emit(ChatEvent.ShowSnackbar("Failed to delete session"))
                    }
                    is Resource.Loading -> { }
                }
            }
        }
    }

    /**
     * Start a new chat session
     */
    fun startNewChat() {
        _state.update {
            it.copy(
                messages = emptyList(),
                currentSessionId = null,
                error = null
            )
        }
        addWelcomeMessage()
    }

    /**
     * Clear current chat
     */
    fun clearChat() {
        startNewChat()
    }

    /**
     * Dismiss error
     */
    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Retry last message after error
     */
    fun retryLastMessage() {
        val lastUserMessage = _state.value.messages
            .lastOrNull { it.isFromUser }
            ?.content

        if (lastUserMessage != null) {
            // Remove the error message
            _state.update {
                it.copy(
                    messages = it.messages.dropLast(1).filter { msg -> !msg.isError },
                    error = null
                )
            }
            sendMessage(lastUserMessage)
        }
    }
}
