package com.optix.app.data.remote.websocket

/**
 * Represents the current state of a WebSocket connection
 */
sealed class WebSocketConnectionState {
    /**
     * Initial state before connection attempt
     */
    data object Idle : WebSocketConnectionState()

    /**
     * Actively attempting to connect to the server
     */
    data object Connecting : WebSocketConnectionState()

    /**
     * Successfully connected and ready to send/receive messages
     */
    data object Connected : WebSocketConnectionState()

    /**
     * Connection was closed normally
     * @param code WebSocket close code
     * @param reason Reason for closure
     */
    data class Disconnected(
        val code: Int = 1000,
        val reason: String = "Normal closure"
    ) : WebSocketConnectionState()

    /**
     * Attempting to reconnect after connection loss
     * @param attempt Current reconnection attempt number
     * @param maxAttempts Maximum reconnection attempts allowed
     * @param delayMs Delay before next attempt in milliseconds
     */
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int,
        val delayMs: Long
    ) : WebSocketConnectionState()

    /**
     * Connection failed with an error
     * @param throwable The exception that caused the failure
     * @param message Human-readable error message
     */
    data class Error(
        val throwable: Throwable? = null,
        val message: String = throwable?.message ?: "Unknown error"
    ) : WebSocketConnectionState()

    /**
     * Helper property to check if connected
     */
    val isConnected: Boolean
        get() = this is Connected

    /**
     * Helper property to check if trying to connect
     */
    val isConnecting: Boolean
        get() = this is Connecting || this is Reconnecting

    /**
     * Helper property for display text
     */
    val displayText: String
        get() = when (this) {
            is Idle -> "Offline"
            is Connecting -> "Connecting..."
            is Connected -> "Live"
            is Disconnected -> "Disconnected"
            is Reconnecting -> "Reconnecting ($attempt/$maxAttempts)..."
            is Error -> "Error: $message"
        }
}
