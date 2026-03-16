package com.optix.app.data.remote.websocket

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Message types received from AI Insights WebSocket
 */
enum class AIInsightsMessageType {
    AI_INSIGHT_UPDATE,
    MARKET_SENTIMENT,
    SUGGESTION_UPDATE,
    SCORE_UPDATE,
    PRICE_UPDATE,
    CONNECTION_ACK,
    ERROR,
    HEARTBEAT,
    UNKNOWN
}

/**
 * Base WebSocket message wrapper
 */
@Serializable
data class WebSocketMessage(
    val type: String,
    val data: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AI Insight update message payload
 */
@Serializable
data class AIInsightUpdatePayload(
    val id: String,
    val symbol: String,
    @SerialName("strike_price") val strikePrice: Double,
    @SerialName("option_type") val optionType: String,
    val expiry: String,
    val action: String,
    @SerialName("entry_price") val entryPrice: Double,
    @SerialName("target_price") val targetPrice: Double,
    @SerialName("stop_loss") val stopLoss: Double,
    val confidence: String,
    val score: Int,
    val reasoning: List<ReasoningPayload>? = null
)

@Serializable
data class ReasoningPayload(
    val factor: String,
    val score: Int,
    @SerialName("max_score") val maxScore: Int,
    val description: String,
    @SerialName("is_positive") val isPositive: Boolean
)

/**
 * Market sentiment update payload
 */
@Serializable
data class MarketSentimentPayload(
    val symbol: String,
    val sentiment: String,
    val pcr: Double,
    @SerialName("iv_percentile") val ivPercentile: Double,
    @SerialName("max_pain") val maxPain: Double,
    @SerialName("spot_price") val spotPrice: Double,
    val timestamp: Long
)

/**
 * Suggestion update payload (partial update)
 */
@Serializable
data class SuggestionUpdatePayload(
    val id: String,
    val score: Int? = null,
    val confidence: String? = null,
    @SerialName("entry_price") val entryPrice: Double? = null,
    @SerialName("target_price") val targetPrice: Double? = null,
    @SerialName("stop_loss") val stopLoss: Double? = null
)

/**
 * Score update payload
 */
@Serializable
data class ScoreUpdatePayload(
    val id: String,
    @SerialName("new_score") val newScore: Int,
    @SerialName("previous_score") val previousScore: Int,
    @SerialName("changed_factors") val changedFactors: List<String>? = null
)

/**
 * Price update payload
 */
@Serializable
data class PriceUpdatePayload(
    val symbol: String,
    @SerialName("strike_price") val strikePrice: Double,
    @SerialName("option_type") val optionType: String,
    val expiry: String,
    val ltp: Double,
    val change: Double,
    @SerialName("change_percent") val changePercent: Double,
    val volume: Long,
    val oi: Long,
    val iv: Double,
    val timestamp: Long
)

/**
 * Parsed WebSocket message result
 */
sealed class ParsedWebSocketMessage {
    data class AIInsightUpdate(val payload: AIInsightUpdatePayload) : ParsedWebSocketMessage()
    data class MarketSentiment(val payload: MarketSentimentPayload) : ParsedWebSocketMessage()
    data class SuggestionUpdate(val payload: SuggestionUpdatePayload) : ParsedWebSocketMessage()
    data class ScoreUpdate(val payload: ScoreUpdatePayload) : ParsedWebSocketMessage()
    data class PriceUpdate(val payload: PriceUpdatePayload) : ParsedWebSocketMessage()
    data class ConnectionAck(val message: String) : ParsedWebSocketMessage()
    data class Heartbeat(val timestamp: Long) : ParsedWebSocketMessage()
    data class Error(val code: String, val message: String) : ParsedWebSocketMessage()
    data class Unknown(val type: String, val rawData: String?) : ParsedWebSocketMessage()
}

/**
 * Parser for WebSocket messages related to AI Insights
 */
@Singleton
class WebSocketMessageParser @Inject constructor(
    private val json: Json
) {

    companion object {
        private const val TAG = "WebSocketMessageParser"

        // Message type constants matching server
        private const val TYPE_AI_INSIGHT_UPDATE = "ai_insight_update"
        private const val TYPE_MARKET_SENTIMENT = "market_sentiment"
        private const val TYPE_SUGGESTION_UPDATE = "suggestion_update"
        private const val TYPE_SCORE_UPDATE = "score_update"
        private const val TYPE_PRICE_UPDATE = "price_update"
        private const val TYPE_CONNECTION_ACK = "connection_ack"
        private const val TYPE_ERROR = "error"
        private const val TYPE_HEARTBEAT = "heartbeat"
    }

    /**
     * Parse a raw WebSocket message string into a typed message
     */
    fun parse(rawMessage: String): ParsedWebSocketMessage {
        return try {
            val wrapper = json.decodeFromString<WebSocketMessage>(rawMessage)
            parseByType(wrapper)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WebSocket message: ${e.message}", e)
            ParsedWebSocketMessage.Unknown("parse_error", rawMessage)
        }
    }

    /**
     * Parse message based on type
     */
    private fun parseByType(wrapper: WebSocketMessage): ParsedWebSocketMessage {
        val type = wrapper.type.lowercase()
        val data = wrapper.data

        return when (type) {
            TYPE_AI_INSIGHT_UPDATE -> {
                if (data != null) {
                    try {
                        val payload = json.decodeFromString<AIInsightUpdatePayload>(data)
                        ParsedWebSocketMessage.AIInsightUpdate(payload)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse AI insight update: ${e.message}")
                        ParsedWebSocketMessage.Unknown(type, data)
                    }
                } else {
                    ParsedWebSocketMessage.Unknown(type, null)
                }
            }

            TYPE_MARKET_SENTIMENT -> {
                if (data != null) {
                    try {
                        val payload = json.decodeFromString<MarketSentimentPayload>(data)
                        ParsedWebSocketMessage.MarketSentiment(payload)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse market sentiment: ${e.message}")
                        ParsedWebSocketMessage.Unknown(type, data)
                    }
                } else {
                    ParsedWebSocketMessage.Unknown(type, null)
                }
            }

            TYPE_SUGGESTION_UPDATE -> {
                if (data != null) {
                    try {
                        val payload = json.decodeFromString<SuggestionUpdatePayload>(data)
                        ParsedWebSocketMessage.SuggestionUpdate(payload)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse suggestion update: ${e.message}")
                        ParsedWebSocketMessage.Unknown(type, data)
                    }
                } else {
                    ParsedWebSocketMessage.Unknown(type, null)
                }
            }

            TYPE_SCORE_UPDATE -> {
                if (data != null) {
                    try {
                        val payload = json.decodeFromString<ScoreUpdatePayload>(data)
                        ParsedWebSocketMessage.ScoreUpdate(payload)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse score update: ${e.message}")
                        ParsedWebSocketMessage.Unknown(type, data)
                    }
                } else {
                    ParsedWebSocketMessage.Unknown(type, null)
                }
            }

            TYPE_PRICE_UPDATE -> {
                if (data != null) {
                    try {
                        val payload = json.decodeFromString<PriceUpdatePayload>(data)
                        ParsedWebSocketMessage.PriceUpdate(payload)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse price update: ${e.message}")
                        ParsedWebSocketMessage.Unknown(type, data)
                    }
                } else {
                    ParsedWebSocketMessage.Unknown(type, null)
                }
            }

            TYPE_CONNECTION_ACK -> {
                ParsedWebSocketMessage.ConnectionAck(data ?: "Connected")
            }

            TYPE_HEARTBEAT -> {
                ParsedWebSocketMessage.Heartbeat(wrapper.timestamp)
            }

            TYPE_ERROR -> {
                // Try to parse error details
                val errorData = try {
                    data?.let { json.decodeFromString<ErrorPayload>(it) }
                } catch (e: Exception) {
                    null
                }
                ParsedWebSocketMessage.Error(
                    code = errorData?.code ?: "unknown",
                    message = errorData?.message ?: data ?: "Unknown error"
                )
            }

            else -> {
                Log.w(TAG, "Unknown message type: $type")
                ParsedWebSocketMessage.Unknown(type, data)
            }
        }
    }

    /**
     * Get the message type from raw message without full parsing
     */
    fun getMessageType(rawMessage: String): AIInsightsMessageType {
        return try {
            val wrapper = json.decodeFromString<WebSocketMessage>(rawMessage)
            when (wrapper.type.lowercase()) {
                TYPE_AI_INSIGHT_UPDATE -> AIInsightsMessageType.AI_INSIGHT_UPDATE
                TYPE_MARKET_SENTIMENT -> AIInsightsMessageType.MARKET_SENTIMENT
                TYPE_SUGGESTION_UPDATE -> AIInsightsMessageType.SUGGESTION_UPDATE
                TYPE_SCORE_UPDATE -> AIInsightsMessageType.SCORE_UPDATE
                TYPE_PRICE_UPDATE -> AIInsightsMessageType.PRICE_UPDATE
                TYPE_CONNECTION_ACK -> AIInsightsMessageType.CONNECTION_ACK
                TYPE_ERROR -> AIInsightsMessageType.ERROR
                TYPE_HEARTBEAT -> AIInsightsMessageType.HEARTBEAT
                else -> AIInsightsMessageType.UNKNOWN
            }
        } catch (e: Exception) {
            AIInsightsMessageType.UNKNOWN
        }
    }
}

@Serializable
private data class ErrorPayload(
    val code: String,
    val message: String
)
