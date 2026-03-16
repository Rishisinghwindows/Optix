package com.optix.app.data.remote.websocket

import android.util.Log
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp-based WebSocket client with automatic reconnection using exponential backoff.
 *
 * Features:
 * - Automatic reconnection with exponential backoff (1s → 2s → 4s → 8s → 16s)
 * - Maximum 5 reconnection attempts before giving up
 * - Ping interval of 30 seconds to keep connection alive
 * - Connection state tracking via StateFlow
 * - Message emission via SharedFlow
 */
@Singleton
class WebSocketClient @Inject constructor() {

    companion object {
        private const val TAG = "WebSocketClient"
        private const val PING_INTERVAL_SECONDS = 30L
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 16000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val BACKOFF_MULTIPLIER = 2.0
    }

    private var okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private var currentUrl: String? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _connectionState = MutableStateFlow<WebSocketConnectionState>(WebSocketConnectionState.Idle)
    val connectionState: StateFlow<WebSocketConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 100)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _binaryMessages = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 100)
    val binaryMessages: SharedFlow<ByteArray> = _binaryMessages.asSharedFlow()

    private var shouldReconnect = true

    /**
     * Connect to the WebSocket server
     * @param url WebSocket URL (ws:// or wss://)
     */
    fun connect(url: String) {
        if (_connectionState.value is WebSocketConnectionState.Connected ||
            _connectionState.value is WebSocketConnectionState.Connecting) {
            Log.w(TAG, "Already connected or connecting, ignoring connect request")
            return
        }

        currentUrl = url
        shouldReconnect = true
        reconnectAttempt = 0
        performConnect(url)
    }

    /**
     * Internal connect method
     */
    private fun performConnect(url: String) {
        _connectionState.value = WebSocketConnectionState.Connecting
        Log.d(TAG, "Connecting to WebSocket: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    /**
     * Disconnect from the WebSocket server
     * @param code Close code (default: 1000 for normal closure)
     * @param reason Close reason
     */
    fun disconnect(code: Int = 1000, reason: String = "Client requested disconnect") {
        shouldReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null

        webSocket?.close(code, reason)
        webSocket = null
        _connectionState.value = WebSocketConnectionState.Disconnected(code, reason)
        Log.d(TAG, "Disconnected: $reason")
    }

    /**
     * Send a text message through the WebSocket
     * @param message Text message to send
     * @return true if the message was enqueued successfully
     */
    fun send(message: String): Boolean {
        val ws = webSocket
        if (ws == null || _connectionState.value !is WebSocketConnectionState.Connected) {
            Log.w(TAG, "Cannot send message: not connected")
            return false
        }
        return ws.send(message)
    }

    /**
     * Send binary data through the WebSocket
     * @param data Binary data to send
     * @return true if the data was enqueued successfully
     */
    fun send(data: ByteArray): Boolean {
        val ws = webSocket
        if (ws == null || _connectionState.value !is WebSocketConnectionState.Connected) {
            Log.w(TAG, "Cannot send binary data: not connected")
            return false
        }
        return ws.send(okio.ByteString.of(*data))
    }

    /**
     * Force a reconnection attempt
     */
    fun reconnect() {
        disconnect()
        currentUrl?.let {
            shouldReconnect = true
            reconnectAttempt = 0
            connect(it)
        }
    }

    /**
     * Create WebSocket listener
     */
    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                reconnectAttempt = 0
                _connectionState.value = WebSocketConnectionState.Connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.v(TAG, "Received message: ${text.take(100)}...")
                scope.launch {
                    _messages.emit(text)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                Log.v(TAG, "Received binary message: ${bytes.size} bytes")
                scope.launch {
                    _binaryMessages.emit(bytes.toByteArray())
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code - $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                this@WebSocketClient.webSocket = null
                _connectionState.value = WebSocketConnectionState.Disconnected(code, reason)

                if (shouldReconnect && code != 1000) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                this@WebSocketClient.webSocket = null
                _connectionState.value = WebSocketConnectionState.Error(t)

                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }
        }
    }

    /**
     * Schedule a reconnection attempt with exponential backoff
     */
    private fun scheduleReconnect() {
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnection attempts reached")
            _connectionState.value = WebSocketConnectionState.Error(
                message = "Connection failed after $MAX_RECONNECT_ATTEMPTS attempts"
            )
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = calculateBackoff(reconnectAttempt)
            reconnectAttempt++

            Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempt in ${delayMs}ms")
            _connectionState.value = WebSocketConnectionState.Reconnecting(
                attempt = reconnectAttempt,
                maxAttempts = MAX_RECONNECT_ATTEMPTS,
                delayMs = delayMs
            )

            delay(delayMs)

            currentUrl?.let { url ->
                performConnect(url)
            }
        }
    }

    /**
     * Calculate backoff delay using exponential backoff formula
     */
    private fun calculateBackoff(attempt: Int): Long {
        val backoff = INITIAL_BACKOFF_MS * BACKOFF_MULTIPLIER.pow(attempt)
        return minOf(backoff.toLong(), MAX_BACKOFF_MS)
    }
}
