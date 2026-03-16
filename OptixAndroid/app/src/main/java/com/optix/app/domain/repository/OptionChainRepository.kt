package com.optix.app.domain.repository

import com.optix.app.core.util.Resource
import com.optix.app.data.remote.websocket.SpotPriceUpdate
import com.optix.app.data.remote.websocket.WebSocketConnectionState
import com.optix.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository interface for option chain data
 */
interface OptionChainRepository {
    /**
     * Get option chain for an index and expiry
     */
    suspend fun getOptionChain(
        index: TradingIndex,
        expiry: String
    ): Resource<OptionChain>

    /**
     * Get available expiry dates for an index
     */
    suspend fun getExpiries(index: TradingIndex): Resource<List<ExpiryDate>>

    /**
     * Get spot price for a symbol
     */
    suspend fun getSpotPrice(symbol: String): Resource<Double>

    /**
     * Subscribe to live price updates
     */
    fun subscribeToPriceUpdates(instrumentKeys: List<String>): Flow<Map<String, OptionData>>

    /**
     * Unsubscribe from price updates
     */
    fun unsubscribeFromPriceUpdates()

    /**
     * WebSocket connection state
     */
    val connectionState: StateFlow<WebSocketConnectionState>

    /**
     * Spot price updates flow
     */
    val spotPriceUpdates: SharedFlow<SpotPriceUpdate>

    /**
     * Connect to WebSocket for live updates
     */
    fun connectToLiveUpdates()

    /**
     * Disconnect from WebSocket
     */
    fun disconnectFromLiveUpdates()

    /**
     * Subscribe to live updates for an index and expiry
     */
    fun subscribeToLiveUpdates(index: TradingIndex, expiry: String)
}
