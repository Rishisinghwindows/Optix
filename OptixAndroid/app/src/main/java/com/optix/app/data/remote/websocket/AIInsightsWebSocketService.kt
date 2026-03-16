package com.optix.app.data.remote.websocket

import android.util.Log
import com.optix.app.core.constants.ApiConstants
import com.optix.app.domain.model.AITradeSuggestion
import com.optix.app.domain.model.ConfidenceLevel
import com.optix.app.domain.model.MarketSentiment
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.ScoreReasoning
import com.optix.app.domain.model.TradeDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain-specific WebSocket service for AI Insights feature.
 *
 * This service wraps the generic WebSocketClient and provides:
 * - Typed events for AI insight updates
 * - Market sentiment streaming
 * - Real-time score updates
 * - Price updates for subscribed instruments
 * - Connection state management
 */
@Singleton
class AIInsightsWebSocketService @Inject constructor(
    private val webSocketClient: WebSocketClient,
    private val messageParser: WebSocketMessageParser,
    private val json: Json
) {

    companion object {
        private const val TAG = "AIInsightsWebSocketService"
        private const val WS_ENDPOINT = "wss://api.optix.d23ai.in/ws/ai-insights"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Connection state
    val connectionState: StateFlow<WebSocketConnectionState> = webSocketClient.connectionState

    // Typed message flows
    private val _insightUpdates = MutableSharedFlow<AITradeSuggestion>(extraBufferCapacity = 50)
    val insightUpdates: SharedFlow<AITradeSuggestion> = _insightUpdates.asSharedFlow()

    private val _marketSentiment = MutableStateFlow<MarketSentimentData?>(null)
    val marketSentiment: StateFlow<MarketSentimentData?> = _marketSentiment.asStateFlow()

    private val _suggestionUpdates = MutableSharedFlow<SuggestionPartialUpdate>(extraBufferCapacity = 50)
    val suggestionUpdates: SharedFlow<SuggestionPartialUpdate> = _suggestionUpdates.asSharedFlow()

    private val _scoreUpdates = MutableSharedFlow<ScoreChangeEvent>(extraBufferCapacity = 50)
    val scoreUpdates: SharedFlow<ScoreChangeEvent> = _scoreUpdates.asSharedFlow()

    private val _priceUpdates = MutableSharedFlow<PriceUpdateEvent>(extraBufferCapacity = 100)
    val priceUpdates: SharedFlow<PriceUpdateEvent> = _priceUpdates.asSharedFlow()

    private val _errors = MutableSharedFlow<WebSocketError>(extraBufferCapacity = 10)
    val errors: SharedFlow<WebSocketError> = _errors.asSharedFlow()

    // Subscribed symbols
    private val subscribedSymbols = mutableSetOf<String>()

    init {
        // Listen to raw messages and parse them
        scope.launch {
            webSocketClient.messages.collect { rawMessage ->
                handleRawMessage(rawMessage)
            }
        }
    }

    /**
     * Connect to the AI Insights WebSocket
     * @param symbol Optional symbol to subscribe to immediately
     */
    fun connect(symbol: String? = null) {
        Log.d(TAG, "Connecting to AI Insights WebSocket")
        webSocketClient.connect(WS_ENDPOINT)

        symbol?.let {
            subscribedSymbols.add(it)
        }

        // Send subscription after connection
        scope.launch {
            connectionState.collect { state ->
                if (state is WebSocketConnectionState.Connected && subscribedSymbols.isNotEmpty()) {
                    sendSubscription(subscribedSymbols.toList())
                }
            }
        }
    }

    /**
     * Disconnect from the WebSocket
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from AI Insights WebSocket")
        subscribedSymbols.clear()
        webSocketClient.disconnect()
    }

    /**
     * Subscribe to real-time updates for a symbol
     */
    fun subscribe(symbol: String) {
        subscribedSymbols.add(symbol)
        if (connectionState.value is WebSocketConnectionState.Connected) {
            sendSubscription(listOf(symbol))
        }
    }

    /**
     * Subscribe to multiple symbols
     */
    fun subscribe(symbols: List<String>) {
        subscribedSymbols.addAll(symbols)
        if (connectionState.value is WebSocketConnectionState.Connected) {
            sendSubscription(symbols)
        }
    }

    /**
     * Unsubscribe from a symbol
     */
    fun unsubscribe(symbol: String) {
        subscribedSymbols.remove(symbol)
        if (connectionState.value is WebSocketConnectionState.Connected) {
            sendUnsubscription(listOf(symbol))
        }
    }

    /**
     * Request a refresh of AI insights for a symbol
     */
    fun requestRefresh(symbol: String) {
        val request = mapOf(
            "action" to "refresh",
            "symbol" to symbol,
            "timestamp" to System.currentTimeMillis()
        )
        webSocketClient.send(json.encodeToString(request))
    }

    /**
     * Send subscription message
     */
    private fun sendSubscription(symbols: List<String>) {
        val request = mapOf(
            "action" to "subscribe",
            "symbols" to symbols,
            "timestamp" to System.currentTimeMillis()
        )
        webSocketClient.send(json.encodeToString(request))
        Log.d(TAG, "Subscribed to: $symbols")
    }

    /**
     * Send unsubscription message
     */
    private fun sendUnsubscription(symbols: List<String>) {
        val request = mapOf(
            "action" to "unsubscribe",
            "symbols" to symbols,
            "timestamp" to System.currentTimeMillis()
        )
        webSocketClient.send(json.encodeToString(request))
        Log.d(TAG, "Unsubscribed from: $symbols")
    }

    /**
     * Handle raw message from WebSocket
     */
    private suspend fun handleRawMessage(rawMessage: String) {
        when (val parsed = messageParser.parse(rawMessage)) {
            is ParsedWebSocketMessage.AIInsightUpdate -> {
                handleInsightUpdate(parsed.payload)
            }
            is ParsedWebSocketMessage.MarketSentiment -> {
                handleMarketSentiment(parsed.payload)
            }
            is ParsedWebSocketMessage.SuggestionUpdate -> {
                handleSuggestionUpdate(parsed.payload)
            }
            is ParsedWebSocketMessage.ScoreUpdate -> {
                handleScoreUpdate(parsed.payload)
            }
            is ParsedWebSocketMessage.PriceUpdate -> {
                handlePriceUpdate(parsed.payload)
            }
            is ParsedWebSocketMessage.Error -> {
                _errors.emit(WebSocketError(parsed.code, parsed.message))
            }
            is ParsedWebSocketMessage.ConnectionAck -> {
                Log.d(TAG, "Connection acknowledged: ${parsed.message}")
            }
            is ParsedWebSocketMessage.Heartbeat -> {
                Log.v(TAG, "Heartbeat received: ${parsed.timestamp}")
            }
            is ParsedWebSocketMessage.Unknown -> {
                Log.w(TAG, "Unknown message type: ${parsed.type}")
            }
        }
    }

    /**
     * Handle AI insight update
     */
    private suspend fun handleInsightUpdate(payload: AIInsightUpdatePayload) {
        val suggestion = AITradeSuggestion(
            id = payload.id,
            symbol = payload.symbol,
            strikePrice = payload.strikePrice,
            optionType = parseOptionType(payload.optionType),
            expiry = payload.expiry,
            action = parseTradeDirection(payload.action),
            entryPrice = payload.entryPrice,
            targetPrice = payload.targetPrice,
            stopLoss = payload.stopLoss,
            confidence = parseConfidenceLevel(payload.confidence),
            score = payload.score,
            reasoning = payload.reasoning?.map { r ->
                ScoreReasoning(
                    factor = r.factor,
                    score = r.score,
                    maxScore = r.maxScore,
                    description = r.description,
                    isPositive = r.isPositive
                )
            } ?: emptyList(),
            riskReward = calculateRiskReward(payload.entryPrice, payload.targetPrice, payload.stopLoss),
            maxProfit = calculateMaxProfit(payload.entryPrice, payload.targetPrice, parseTradeDirection(payload.action)),
            maxLoss = calculateMaxLoss(payload.entryPrice, payload.stopLoss, parseTradeDirection(payload.action))
        )
        _insightUpdates.emit(suggestion)
    }

    /**
     * Handle market sentiment update
     */
    private suspend fun handleMarketSentiment(payload: MarketSentimentPayload) {
        _marketSentiment.value = MarketSentimentData(
            symbol = payload.symbol,
            sentiment = parseMarketSentiment(payload.sentiment),
            pcr = payload.pcr,
            ivPercentile = payload.ivPercentile,
            maxPain = payload.maxPain,
            spotPrice = payload.spotPrice,
            timestamp = payload.timestamp
        )
    }

    /**
     * Handle suggestion partial update
     */
    private suspend fun handleSuggestionUpdate(payload: SuggestionUpdatePayload) {
        _suggestionUpdates.emit(
            SuggestionPartialUpdate(
                id = payload.id,
                score = payload.score,
                confidence = payload.confidence?.let { parseConfidenceLevel(it) },
                entryPrice = payload.entryPrice,
                targetPrice = payload.targetPrice,
                stopLoss = payload.stopLoss
            )
        )
    }

    /**
     * Handle score update
     */
    private suspend fun handleScoreUpdate(payload: ScoreUpdatePayload) {
        _scoreUpdates.emit(
            ScoreChangeEvent(
                id = payload.id,
                newScore = payload.newScore,
                previousScore = payload.previousScore,
                changedFactors = payload.changedFactors ?: emptyList()
            )
        )
    }

    /**
     * Handle price update
     */
    private suspend fun handlePriceUpdate(payload: PriceUpdatePayload) {
        _priceUpdates.emit(
            PriceUpdateEvent(
                symbol = payload.symbol,
                strikePrice = payload.strikePrice,
                optionType = parseOptionType(payload.optionType),
                expiry = payload.expiry,
                ltp = payload.ltp,
                change = payload.change,
                changePercent = payload.changePercent,
                volume = payload.volume,
                oi = payload.oi,
                iv = payload.iv,
                timestamp = payload.timestamp
            )
        )
    }

    // Helper parsing functions
    private fun parseOptionType(type: String): OptionType {
        return when (type.uppercase()) {
            "CE", "CALL" -> OptionType.CALL
            "PE", "PUT" -> OptionType.PUT
            else -> OptionType.CALL
        }
    }

    private fun parseTradeDirection(action: String): TradeDirection {
        return when (action.uppercase()) {
            "STRONG BUY", "STRONG_BUY" -> TradeDirection.STRONG_BUY
            "BUY" -> TradeDirection.BUY
            "HOLD" -> TradeDirection.HOLD
            "SELL" -> TradeDirection.SELL
            "STRONG SELL", "STRONG_SELL" -> TradeDirection.STRONG_SELL
            else -> TradeDirection.BUY
        }
    }

    private fun parseConfidenceLevel(confidence: String): ConfidenceLevel {
        return when (confidence.uppercase()) {
            "HIGH" -> ConfidenceLevel.HIGH
            "MEDIUM" -> ConfidenceLevel.MEDIUM
            "LOW" -> ConfidenceLevel.LOW
            else -> ConfidenceLevel.MEDIUM
        }
    }

    private fun parseMarketSentiment(sentiment: String): MarketSentiment {
        return when (sentiment.uppercase()) {
            "BULLISH" -> MarketSentiment.BULLISH
            "BEARISH" -> MarketSentiment.BEARISH
            "NEUTRAL" -> MarketSentiment.NEUTRAL
            else -> MarketSentiment.NEUTRAL
        }
    }

    private fun calculateRiskReward(entry: Double, target: Double, stopLoss: Double): Double {
        val risk = kotlin.math.abs(entry - stopLoss)
        val reward = kotlin.math.abs(target - entry)
        return if (risk > 0) reward / risk else 0.0
    }

    private fun calculateMaxProfit(entry: Double, target: Double, direction: TradeDirection): Double {
        return when (direction) {
            TradeDirection.STRONG_BUY, TradeDirection.BUY -> target - entry
            TradeDirection.STRONG_SELL, TradeDirection.SELL -> entry - target
            TradeDirection.HOLD -> 0.0 // HOLD means no trade, no profit
        }
    }

    private fun calculateMaxLoss(entry: Double, stopLoss: Double, direction: TradeDirection): Double {
        return when (direction) {
            TradeDirection.STRONG_BUY, TradeDirection.BUY -> entry - stopLoss
            TradeDirection.STRONG_SELL, TradeDirection.SELL -> stopLoss - entry
            TradeDirection.HOLD -> 0.0 // HOLD means no trade, no loss
        }
    }
}

/**
 * Market sentiment data class
 */
data class MarketSentimentData(
    val symbol: String,
    val sentiment: MarketSentiment,
    val pcr: Double,
    val ivPercentile: Double,
    val maxPain: Double,
    val spotPrice: Double,
    val timestamp: Long
)

/**
 * Suggestion partial update
 */
data class SuggestionPartialUpdate(
    val id: String,
    val score: Int?,
    val confidence: ConfidenceLevel?,
    val entryPrice: Double?,
    val targetPrice: Double?,
    val stopLoss: Double?
)

/**
 * Score change event
 */
data class ScoreChangeEvent(
    val id: String,
    val newScore: Int,
    val previousScore: Int,
    val changedFactors: List<String>
) {
    val scoreDelta: Int = newScore - previousScore
    val isImprovement: Boolean = scoreDelta > 0
}

/**
 * Price update event
 */
data class PriceUpdateEvent(
    val symbol: String,
    val strikePrice: Double,
    val optionType: OptionType,
    val expiry: String,
    val ltp: Double,
    val change: Double,
    val changePercent: Double,
    val volume: Long,
    val oi: Long,
    val iv: Double,
    val timestamp: Long
)

/**
 * WebSocket error
 */
data class WebSocketError(
    val code: String,
    val message: String
)
