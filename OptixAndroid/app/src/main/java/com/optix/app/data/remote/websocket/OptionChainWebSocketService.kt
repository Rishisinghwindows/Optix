package com.optix.app.data.remote.websocket

import android.util.Log
import com.optix.app.core.constants.ApiConstants
import com.optix.app.domain.model.OptionData
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.TradingIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * WebSocket service specifically for Option Chain real-time price updates.
 *
 * This service provides:
 * - Real-time spot price updates for indices (NIFTY, BANKNIFTY, etc.)
 * - Live option chain data updates (LTP, OI, Volume, IV)
 * - Auto-reconnection with exponential backoff
 * - Subscription management for specific instruments
 */
@Singleton
class OptionChainWebSocketService @Inject constructor(
    private val json: Json
) {
    companion object {
        private const val TAG = "OptionChainWS"
        private const val WS_ENDPOINT = ApiConstants.WEBSOCKET_URL
        private const val PING_INTERVAL_SECONDS = 30L
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 16000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val BACKOFF_MULTIPLIER = 2.0
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var reconnectAttempt = 0
    private var shouldReconnect = true

    // Connection state
    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Idle)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    // Spot price updates (index -> price data)
    private val _spotPriceUpdates = MutableSharedFlow<SpotPriceUpdate>(extraBufferCapacity = 100)
    val spotPriceUpdates: SharedFlow<SpotPriceUpdate> = _spotPriceUpdates.asSharedFlow()

    // Option data updates (instrumentKey -> OptionData)
    private val _optionDataUpdates = MutableSharedFlow<Map<String, OptionData>>(extraBufferCapacity = 100)
    val optionDataUpdates: SharedFlow<Map<String, OptionData>> = _optionDataUpdates.asSharedFlow()

    // Full chain updates
    private val _chainUpdates = MutableSharedFlow<OptionChainUpdate>(extraBufferCapacity = 50)
    val chainUpdates: SharedFlow<OptionChainUpdate> = _chainUpdates.asSharedFlow()

    // Subscribed symbols
    private val subscribedIndices = mutableSetOf<String>()
    private val subscribedInstruments = mutableSetOf<String>()
    private var pendingExpiry: String? = null

    /**
     * Connect to the market WebSocket
     */
    fun connect() {
        if (_connectionState.value is WebSocketConnectionState.Connected ||
            _connectionState.value is WebSocketConnectionState.Connecting) {
            Log.w(TAG, "Already connected or connecting")
            return
        }

        shouldReconnect = true
        reconnectAttempt = 0
        performConnect()
    }

    private fun performConnect() {
        _connectionState.value = WebSocketConnectionState.Connecting
        Log.d(TAG, "Connecting to Option Chain WebSocket: $WS_ENDPOINT")

        val request = Request.Builder()
            .url(WS_ENDPOINT)
            .build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    /**
     * Disconnect from the WebSocket
     */
    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        subscribedIndices.clear()
        subscribedInstruments.clear()
        _connectionState.value = WebSocketConnectionState.Disconnected(1000, "Disconnected")
        Log.d(TAG, "Disconnected from Option Chain WebSocket")
    }

    /**
     * Subscribe to spot price updates for an index
     */
    fun subscribeToIndex(index: TradingIndex) {
        val symbol = index.symbol
        subscribedIndices.add(symbol)

        if (_connectionState.value is WebSocketConnectionState.Connected) {
            sendSubscription(SubscriptionRequest(
                action = "subscribe",
                type = "spot",
                symbols = listOf(symbol)
            ))
        }
        Log.d(TAG, "Subscribed to index: $symbol")
    }

    /**
     * Subscribe to option chain updates
     */
    fun subscribeToOptionChain(index: TradingIndex, expiry: String) {
        val symbol = index.symbol
        subscribedIndices.add(symbol)
        pendingExpiry = expiry

        if (_connectionState.value is WebSocketConnectionState.Connected) {
            sendOptionChainSubscriptions(symbol, expiry)
        }
        Log.d(TAG, "Subscribed to option chain: $symbol, expiry: $expiry")
    }

    private fun sendOptionChainSubscriptions(symbol: String, expiry: String) {
        // Subscribe to option_chain for full data updates (5s interval)
        sendSubscription(SubscriptionRequest(
            action = "subscribe",
            type = "option_chain",
            symbols = listOf(symbol),
            expiry = expiry
        ))
        // Also subscribe to ticker for fast LTP updates (500ms interval)
        sendSubscription(SubscriptionRequest(
            action = "subscribe",
            type = "ticker",
            symbols = listOf(symbol),
            expiry = expiry
        ))
    }

    /**
     * Subscribe to specific instrument updates
     */
    fun subscribeToInstruments(instrumentKeys: List<String>) {
        subscribedInstruments.addAll(instrumentKeys)

        if (_connectionState.value is WebSocketConnectionState.Connected) {
            sendSubscription(SubscriptionRequest(
                action = "subscribe",
                type = "instruments",
                instrumentKeys = instrumentKeys
            ))
        }
        Log.d(TAG, "Subscribed to ${instrumentKeys.size} instruments")
    }

    /**
     * Unsubscribe from index updates
     */
    fun unsubscribeFromIndex(index: TradingIndex) {
        val symbol = index.symbol
        subscribedIndices.remove(symbol)

        if (_connectionState.value is WebSocketConnectionState.Connected) {
            sendSubscription(SubscriptionRequest(
                action = "unsubscribe",
                type = "spot",
                symbols = listOf(symbol)
            ))
        }
    }

    /**
     * Unsubscribe from all
     */
    fun unsubscribeAll() {
        if (_connectionState.value is WebSocketConnectionState.Connected) {
            sendSubscription(SubscriptionRequest(
                action = "unsubscribe",
                type = "all"
            ))
        }
        subscribedIndices.clear()
        subscribedInstruments.clear()
    }

    private fun sendSubscription(request: SubscriptionRequest) {
        try {
            val message = json.encodeToString(request)
            webSocket?.send(message)
            Log.d(TAG, "Sent subscription: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send subscription: ${e.message}")
        }
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                reconnectAttempt = 0
                _connectionState.value = WebSocketConnectionState.Connected

                // Re-subscribe to all previously subscribed items
                resubscribeAll()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.v(TAG, "Received: ${text.take(200)}...")
                scope.launch {
                    handleMessage(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                this@OptionChainWebSocketService.webSocket = null
                _connectionState.value = WebSocketConnectionState.Disconnected(code, reason)

                if (shouldReconnect && code != 1000) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                this@OptionChainWebSocketService.webSocket = null
                _connectionState.value = WebSocketConnectionState.Error(t)

                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
        }
    }

    private fun resubscribeAll() {
        // Resubscribe to indices (spot prices)
        if (subscribedIndices.isNotEmpty()) {
            sendSubscription(SubscriptionRequest(
                action = "subscribe",
                type = "spot",
                symbols = subscribedIndices.toList()
            ))

            // Also subscribe to option_chain and ticker if we have an expiry
            pendingExpiry?.let { expiry ->
                subscribedIndices.forEach { symbol ->
                    sendOptionChainSubscriptions(symbol, expiry)
                }
            }
        }

        // Resubscribe to instruments
        if (subscribedInstruments.isNotEmpty()) {
            sendSubscription(SubscriptionRequest(
                action = "subscribe",
                type = "instruments",
                instrumentKeys = subscribedInstruments.toList()
            ))
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnection attempts reached")
            _connectionState.value = WebSocketConnectionState.Error(
                message = "Connection failed after $MAX_RECONNECT_ATTEMPTS attempts"
            )
            return
        }

        scope.launch {
            val delayMs = calculateBackoff(reconnectAttempt)
            reconnectAttempt++

            Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempt in ${delayMs}ms")
            _connectionState.value = WebSocketConnectionState.Reconnecting(
                attempt = reconnectAttempt,
                maxAttempts = MAX_RECONNECT_ATTEMPTS,
                delayMs = delayMs
            )

            delay(delayMs)
            performConnect()
        }
    }

    private fun calculateBackoff(attempt: Int): Long {
        val backoff = INITIAL_BACKOFF_MS * BACKOFF_MULTIPLIER.pow(attempt)
        return minOf(backoff.toLong(), MAX_BACKOFF_MS)
    }

    private suspend fun handleMessage(rawMessage: String) {
        try {
            // Parse as JsonObject for flexible handling
            val jsonObj = json.parseToJsonElement(rawMessage).jsonObject
            val type = jsonObj["type"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: return
            val dataObj = jsonObj["data"]?.jsonObject

            when (type) {
                "spot_price", "spot_update", "index_update" -> {
                    // Handle data either as nested object or at root level
                    val data = dataObj ?: jsonObj
                    val symbol = data["symbol"]?.jsonPrimitive?.contentOrNull ?: return
                    val price = data["price"]?.jsonPrimitive?.doubleOrNull
                        ?: data["lastPrice"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val change = data["change"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val changePercent = data["pChange"]?.jsonPrimitive?.doubleOrNull
                        ?: data["change_percent"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val high = data["high"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val low = data["low"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val open = data["open"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val previousClose = data["previousClose"]?.jsonPrimitive?.doubleOrNull
                        ?: data["previous_close"]?.jsonPrimitive?.doubleOrNull ?: 0.0

                    _spotPriceUpdates.emit(SpotPriceUpdate(
                        symbol = symbol,
                        price = price,
                        change = change,
                        changePercent = changePercent,
                        high = high,
                        low = low,
                        open = open,
                        previousClose = previousClose,
                        timestamp = System.currentTimeMillis()
                    ))
                    Log.d(TAG, "Spot update: $symbol = $price (change: $change, $changePercent%)")
                }

                "option_update", "option_tick" -> {
                    dataObj?.let { data ->
                        val instrumentKey = data["instrument_key"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                        val strikePrice = data["strike_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val optionType = data["option_type"]?.jsonPrimitive?.contentOrNull ?: "CE"
                        val expiry = data["expiry"]?.jsonPrimitive?.contentOrNull ?: ""
                        val ltp = data["ltp"]?.jsonPrimitive?.doubleOrNull
                            ?: data["lastPrice"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val change = data["change"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val changePercent = data["change_percent"]?.jsonPrimitive?.doubleOrNull
                            ?: data["pChange"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val oi = data["oi"]?.jsonPrimitive?.longOrNull
                            ?: data["openInterest"]?.jsonPrimitive?.longOrNull ?: 0L
                        val oiChange = data["oi_change"]?.jsonPrimitive?.longOrNull
                            ?: data["changeinOpenInterest"]?.jsonPrimitive?.longOrNull ?: 0L
                        val volume = data["volume"]?.jsonPrimitive?.longOrNull
                            ?: data["totalTradedVolume"]?.jsonPrimitive?.longOrNull ?: 0L
                        val iv = data["iv"]?.jsonPrimitive?.doubleOrNull
                            ?: data["impliedVolatility"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val underlyingSpot = data["underlying_spot"]?.jsonPrimitive?.doubleOrNull
                            ?: data["underlyingValue"]?.jsonPrimitive?.doubleOrNull ?: 0.0

                        val optionData = OptionData(
                            strikePrice = strikePrice,
                            optionType = if (optionType.uppercase() in listOf("CE", "CALL"))
                                OptionType.CALL else OptionType.PUT,
                            expiry = expiry,
                            lastPrice = ltp,
                            change = change,
                            changePercent = changePercent,
                            openInterest = oi,
                            oiChange = oiChange,
                            volume = volume,
                            impliedVolatility = iv,
                            instrumentKey = instrumentKey,
                            underlyingSpot = underlyingSpot
                        )
                        _optionDataUpdates.emit(mapOf(instrumentKey to optionData))
                        Log.d(TAG, "Option update: $instrumentKey strike=$strikePrice ltp=$ltp")
                    }
                }

                "chain_update", "option_chain_update" -> {
                    // Handle full chain updates
                    val data = dataObj ?: return
                    val symbol = data["symbol"]?.jsonPrimitive?.contentOrNull
                        ?: jsonObj["symbol"]?.jsonPrimitive?.contentOrNull ?: return
                    val spotPrice = data["underlyingValue"]?.jsonPrimitive?.doubleOrNull
                        ?: data["spot_price"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val expiry = jsonObj["expiry"]?.jsonPrimitive?.contentOrNull
                        ?: data["expiry"]?.jsonPrimitive?.contentOrNull ?: ""

                    // Emit spot price update
                    _spotPriceUpdates.emit(SpotPriceUpdate(
                        symbol = symbol,
                        price = spotPrice,
                        timestamp = System.currentTimeMillis()
                    ))

                    // Parse option chain rows if available
                    val optionUpdates = mutableMapOf<String, OptionData>()

                    // Log available keys for debugging
                    Log.d(TAG, "Option chain data keys: ${data.keys.take(10)}")

                    // Parse calls and puts separately if provided (as arrays)
                    data["calls"]?.let { callsElement ->
                        parseOptionList(callsElement, OptionType.CALL, expiry, spotPrice, optionUpdates)
                    }
                    data["puts"]?.let { putsElement ->
                        parseOptionList(putsElement, OptionType.PUT, expiry, spotPrice, optionUpdates)
                    }

                    // Parse rows if available (combined call/put data as array)
                    data["rows"]?.let { rowsElement ->
                        parseOptionRows(rowsElement, expiry, spotPrice, optionUpdates)
                    }

                    // Also try to parse option chain data format - common key names
                    data["data"]?.let { innerData ->
                        if (innerData is kotlinx.serialization.json.JsonArray) {
                            parseOptionRows(innerData, expiry, spotPrice, optionUpdates)
                        } else if (innerData is JsonObject) {
                            innerData["rows"]?.let { rows ->
                                parseOptionRows(rows, expiry, spotPrice, optionUpdates)
                            }
                        }
                    }
                    data["optionChainData"]?.let { chainData ->
                        if (chainData is kotlinx.serialization.json.JsonArray) {
                            parseOptionRows(chainData, expiry, spotPrice, optionUpdates)
                        }
                    }
                    data["options"]?.let { optionsData ->
                        if (optionsData is kotlinx.serialization.json.JsonArray) {
                            parseOptionRows(optionsData, expiry, spotPrice, optionUpdates)
                        }
                    }

                    if (optionUpdates.isNotEmpty()) {
                        _optionDataUpdates.emit(optionUpdates)
                        Log.d(TAG, "Option chain update: $symbol with ${optionUpdates.size} options, spot=$spotPrice")
                    } else {
                        Log.d(TAG, "Chain update: $symbol spot=$spotPrice expiry=$expiry (no option data parsed)")
                    }
                }

                "connected", "connection_ack" -> {
                    val message = jsonObj["message"]?.jsonPrimitive?.contentOrNull ?: "Connected"
                    Log.d(TAG, "Connection acknowledged: $message")
                    // Log available subscriptions
                    jsonObj["available_subscriptions"]?.let { subs ->
                        Log.d(TAG, "Available subscriptions: $subs")
                    }
                }

                "subscribed" -> {
                    val subscription = jsonObj["subscription"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                    val symbols = jsonObj["symbols"]?.toString() ?: "[]"
                    Log.d(TAG, "Subscribed to $subscription: $symbols")
                }

                "ticker", "ticker_update" -> {
                    // Handle ticker updates for option data
                    dataObj?.let { data ->
                        val symbol = data["symbol"]?.jsonPrimitive?.contentOrNull ?: return@let
                        val strikePrice = data["strike_price"]?.jsonPrimitive?.doubleOrNull
                            ?: data["strikePrice"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val optionType = data["option_type"]?.jsonPrimitive?.contentOrNull
                            ?: data["optionType"]?.jsonPrimitive?.contentOrNull ?: ""
                        val ltp = data["ltp"]?.jsonPrimitive?.doubleOrNull
                            ?: data["lastPrice"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val change = data["change"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val changePercent = data["pChange"]?.jsonPrimitive?.doubleOrNull
                            ?: data["change_percent"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val oi = data["oi"]?.jsonPrimitive?.longOrNull
                            ?: data["openInterest"]?.jsonPrimitive?.longOrNull ?: 0L
                        val volume = data["volume"]?.jsonPrimitive?.longOrNull
                            ?: data["totalTradedVolume"]?.jsonPrimitive?.longOrNull ?: 0L
                        val iv = data["iv"]?.jsonPrimitive?.doubleOrNull
                            ?: data["impliedVolatility"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        val expiry = data["expiry"]?.jsonPrimitive?.contentOrNull ?: ""
                        val underlyingSpot = data["underlying_spot"]?.jsonPrimitive?.doubleOrNull
                            ?: data["underlyingValue"]?.jsonPrimitive?.doubleOrNull ?: 0.0

                        if (strikePrice > 0 && optionType.isNotEmpty()) {
                            val instrumentKey = "${symbol}_${strikePrice.toLong()}_$optionType"
                            val optionData = OptionData(
                                strikePrice = strikePrice,
                                optionType = if (optionType.uppercase() in listOf("CE", "CALL"))
                                    OptionType.CALL else OptionType.PUT,
                                expiry = expiry,
                                lastPrice = ltp,
                                change = change,
                                changePercent = changePercent,
                                openInterest = oi,
                                volume = volume,
                                impliedVolatility = iv,
                                instrumentKey = instrumentKey,
                                underlyingSpot = underlyingSpot
                            )
                            _optionDataUpdates.emit(mapOf(instrumentKey to optionData))
                            Log.d(TAG, "Ticker update: $symbol strike=$strikePrice $optionType ltp=$ltp")
                        }
                    }
                }

                "heartbeat", "ping" -> {
                    // Respond to heartbeat
                    webSocket?.send(json.encodeToString(mapOf("type" to "pong")))
                }

                "error" -> {
                    val errorMsg = dataObj?.get("message")?.jsonPrimitive?.contentOrNull
                        ?: jsonObj["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
                    Log.e(TAG, "Server error: $errorMsg")
                }

                else -> {
                    Log.w(TAG, "Unknown message type: $type, message: ${rawMessage.take(200)}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message}", e)
        }
    }

    private fun parseOptionList(
        element: JsonElement,
        optionType: OptionType,
        expiry: String,
        underlyingSpot: Double,
        updates: MutableMap<String, OptionData>
    ) {
        try {
            val options = element as? kotlinx.serialization.json.JsonArray ?: return
            options.forEach { optElement ->
                val opt = optElement.jsonObject
                val strikePrice = opt["strikePrice"]?.jsonPrimitive?.doubleOrNull
                    ?: opt["strike_price"]?.jsonPrimitive?.doubleOrNull ?: return@forEach
                val ltp = opt["lastPrice"]?.jsonPrimitive?.doubleOrNull
                    ?: opt["ltp"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val change = opt["change"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val changePercent = opt["pChange"]?.jsonPrimitive?.doubleOrNull
                    ?: opt["change_percent"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val oi = opt["openInterest"]?.jsonPrimitive?.longOrNull
                    ?: opt["oi"]?.jsonPrimitive?.longOrNull ?: 0L
                val oiChange = opt["changeinOpenInterest"]?.jsonPrimitive?.longOrNull
                    ?: opt["oi_change"]?.jsonPrimitive?.longOrNull ?: 0L
                val volume = opt["totalTradedVolume"]?.jsonPrimitive?.longOrNull
                    ?: opt["volume"]?.jsonPrimitive?.longOrNull ?: 0L
                val iv = opt["impliedVolatility"]?.jsonPrimitive?.doubleOrNull
                    ?: opt["iv"]?.jsonPrimitive?.doubleOrNull ?: 0.0

                val instrumentKey = "NIFTY_${strikePrice.toLong()}_${optionType.code}"
                updates[instrumentKey] = OptionData(
                    strikePrice = strikePrice,
                    optionType = optionType,
                    expiry = expiry,
                    lastPrice = ltp,
                    change = change,
                    changePercent = changePercent,
                    openInterest = oi,
                    oiChange = oiChange,
                    volume = volume,
                    impliedVolatility = iv,
                    instrumentKey = instrumentKey,
                    underlyingSpot = underlyingSpot
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing option list: ${e.message}")
        }
    }

    private fun parseOptionRows(
        element: JsonElement,
        expiry: String,
        underlyingSpot: Double,
        updates: MutableMap<String, OptionData>
    ) {
        try {
            val rows = element as? kotlinx.serialization.json.JsonArray ?: return
            rows.forEach { rowElement ->
                val row = rowElement.jsonObject
                val strikePrice = row["strikePrice"]?.jsonPrimitive?.doubleOrNull
                    ?: row["strike_price"]?.jsonPrimitive?.doubleOrNull ?: return@forEach

                // Parse call data
                row["CE"]?.jsonObject?.let { call ->
                    parseAndAddOption(call, strikePrice, OptionType.CALL, expiry, underlyingSpot, updates)
                }
                row["callData"]?.jsonObject?.let { call ->
                    parseAndAddOption(call, strikePrice, OptionType.CALL, expiry, underlyingSpot, updates)
                }

                // Parse put data
                row["PE"]?.jsonObject?.let { put ->
                    parseAndAddOption(put, strikePrice, OptionType.PUT, expiry, underlyingSpot, updates)
                }
                row["putData"]?.jsonObject?.let { put ->
                    parseAndAddOption(put, strikePrice, OptionType.PUT, expiry, underlyingSpot, updates)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing option rows: ${e.message}")
        }
    }

    private fun parseAndAddOption(
        opt: JsonObject,
        strikePrice: Double,
        optionType: OptionType,
        expiry: String,
        underlyingSpot: Double,
        updates: MutableMap<String, OptionData>
    ) {
        val ltp = opt["lastPrice"]?.jsonPrimitive?.doubleOrNull
            ?: opt["ltp"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val change = opt["change"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val changePercent = opt["pChange"]?.jsonPrimitive?.doubleOrNull
            ?: opt["change_percent"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val oi = opt["openInterest"]?.jsonPrimitive?.longOrNull
            ?: opt["oi"]?.jsonPrimitive?.longOrNull ?: 0L
        val oiChange = opt["changeinOpenInterest"]?.jsonPrimitive?.longOrNull
            ?: opt["oi_change"]?.jsonPrimitive?.longOrNull ?: 0L
        val volume = opt["totalTradedVolume"]?.jsonPrimitive?.longOrNull
            ?: opt["volume"]?.jsonPrimitive?.longOrNull ?: 0L
        val iv = opt["impliedVolatility"]?.jsonPrimitive?.doubleOrNull
            ?: opt["iv"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val bidPrice = opt["bidprice"]?.jsonPrimitive?.doubleOrNull
            ?: opt["bidPrice"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val askPrice = opt["askPrice"]?.jsonPrimitive?.doubleOrNull ?: 0.0

        val instrumentKey = "NIFTY_${strikePrice.toLong()}_${optionType.code}"
        updates[instrumentKey] = OptionData(
            strikePrice = strikePrice,
            optionType = optionType,
            expiry = expiry,
            lastPrice = ltp,
            change = change,
            changePercent = changePercent,
            openInterest = oi,
            oiChange = oiChange,
            volume = volume,
            impliedVolatility = iv,
            bidPrice = bidPrice,
            askPrice = askPrice,
            instrumentKey = instrumentKey,
            underlyingSpot = underlyingSpot
        )
    }
}

// Data classes for WebSocket messages

@Serializable
data class MarketWebSocketMessage(
    val type: String,
    val data: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SubscriptionRequest(
    val action: String,
    val type: String,
    val symbols: List<String>? = null,
    val expiry: String? = null,
    val instrumentKeys: List<String>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SpotPricePayload(
    val symbol: String,
    val price: Double,
    val change: Double = 0.0,
    @SerialName("change_percent") val changePercent: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val open: Double = 0.0,
    @SerialName("previous_close") val previousClose: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class OptionUpdatePayload(
    @SerialName("instrument_key") val instrumentKey: String,
    val symbol: String,
    @SerialName("strike_price") val strikePrice: Double,
    @SerialName("option_type") val optionType: String,
    val expiry: String,
    val ltp: Double,
    val change: Double = 0.0,
    @SerialName("change_percent") val changePercent: Double = 0.0,
    val oi: Long = 0,
    @SerialName("oi_change") val oiChange: Long = 0,
    val volume: Long = 0,
    val iv: Double = 0.0,
    @SerialName("bid_price") val bidPrice: Double = 0.0,
    @SerialName("ask_price") val askPrice: Double = 0.0,
    @SerialName("bid_qty") val bidQty: Long = 0,
    @SerialName("ask_qty") val askQty: Long = 0,
    @SerialName("underlying_spot") val underlyingSpot: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class OptionChainUpdatePayload(
    val symbol: String,
    val expiry: String,
    @SerialName("spot_price") val spotPrice: Double,
    @SerialName("spot_change") val spotChange: Double = 0.0,
    @SerialName("spot_change_percent") val spotChangePercent: Double = 0.0,
    val options: List<OptionUpdatePayload>,
    val timestamp: Long = System.currentTimeMillis()
)

// Domain data classes

data class SpotPriceUpdate(
    val symbol: String,
    val price: Double,
    val change: Double = 0.0,
    val changePercent: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val open: Double = 0.0,
    val previousClose: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

data class OptionChainUpdate(
    val symbol: String,
    val expiry: String,
    val spotPrice: Double,
    val spotChange: Double = 0.0,
    val spotChangePercent: Double = 0.0,
    val options: Map<String, OptionData>,
    val timestamp: Long = System.currentTimeMillis()
)
