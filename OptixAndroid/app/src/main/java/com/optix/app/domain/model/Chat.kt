package com.optix.app.domain.model

import java.time.LocalDateTime

/**
 * Message role in chat
 */
enum class MessageRole {
    USER, ASSISTANT, SYSTEM
}

/**
 * Chat message
 */
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val isStreaming: Boolean = false
) {
    val isUser: Boolean get() = role == MessageRole.USER
    val isAssistant: Boolean get() = role == MessageRole.ASSISTANT
}

/**
 * Chat session
 */
data class ChatSession(
    val id: String,
    val userId: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    val lastMessage: ChatMessage?
        get() = messages.lastOrNull()

    val messageCount: Int
        get() = messages.size
}

/**
 * Chat context for AI with market data
 */
data class ChatContext(
    val symbol: String? = null,
    val spotPrice: Double? = null,
    val selectedOption: OptionData? = null,
    val marketSentiment: MarketSentiment? = null,
    val pcr: Double? = null,
    val selectedExpiry: String? = null,
    val selectedStrike: Double? = null,
    val ivPercentile: Double? = null,
    val marketTrend: String? = null
) {
    companion object {
        /**
         * Create context from market sentiment PCR value
         */
        fun sentimentFromPcr(pcr: Double?): MarketSentiment {
            return when {
                pcr == null -> MarketSentiment.NEUTRAL
                pcr > 1.2 -> MarketSentiment.BULLISH  // High PCR = contrarian bullish
                pcr < 0.7 -> MarketSentiment.BEARISH  // Low PCR = contrarian bearish
                else -> MarketSentiment.NEUTRAL
            }
        }
    }
}

/**
 * Chat error types for user-friendly error handling
 */
sealed class ChatError(val message: String) {
    object NetworkError : ChatError("Unable to connect. Please check your internet connection.")
    object ServerError : ChatError("Something went wrong. Please try again.")
    object Unauthorized : ChatError("Please sign in to continue.")
    object RateLimited : ChatError("Too many requests. Please wait a moment.")
    object SessionExpired : ChatError("Your session has expired. Starting a new chat.")
    data class Unknown(val errorMessage: String) : ChatError(errorMessage)

    companion object {
        fun from(code: Int?, message: String?): ChatError {
            return when (code) {
                401 -> Unauthorized
                429 -> RateLimited
                in 500..599 -> ServerError
                else -> Unknown(message ?: "An unexpected error occurred.")
            }
        }

        fun fromException(e: Exception): ChatError {
            return when {
                e is java.net.UnknownHostException -> NetworkError
                e is java.net.SocketTimeoutException -> NetworkError
                e is java.io.IOException -> NetworkError
                else -> Unknown(e.message ?: "An unexpected error occurred.")
            }
        }
    }
}

/**
 * Suggested question for chat
 */
data class SuggestedQuestion(
    val text: String,
    val category: String
)
