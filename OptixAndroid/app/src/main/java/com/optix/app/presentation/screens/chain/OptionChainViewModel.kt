package com.optix.app.presentation.screens.chain

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.core.util.Resource
import com.optix.app.data.remote.websocket.WebSocketConnectionState
import com.optix.app.domain.model.ExpiryDate
import com.optix.app.domain.model.OptionChain
import com.optix.app.domain.model.OptionData
import com.optix.app.domain.model.TradingIndex
import com.optix.app.domain.repository.OptionChainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OptionChainState(
    val selectedIndex: TradingIndex = TradingIndex.NIFTY50,
    val selectedExpiry: String? = null,
    val availableExpiries: List<ExpiryDate> = emptyList(),
    val spotPrice: Double? = null,
    val priceChange: Double? = null,
    val priceChangePercent: Double? = null,
    val optionChainState: Resource<OptionChain> = Resource.Loading(),
    // WebSocket connection state
    val isLiveConnected: Boolean = false,
    val connectionStatus: String = "Disconnected"
)

@HiltViewModel
class OptionChainViewModel @Inject constructor(
    private val repository: OptionChainRepository
) : ViewModel() {

    companion object {
        private const val TAG = "OptionChainVM"
    }

    private val _state = MutableStateFlow(OptionChainState())
    val state: StateFlow<OptionChainState> = _state.asStateFlow()

    private var spotPriceJob: Job? = null
    private var optionUpdateJob: Job? = null
    private var connectionStateJob: Job? = null

    init {
        loadExpiries()
        observeWebSocketUpdates()
    }

    /**
     * Observe WebSocket updates for live data
     */
    private fun observeWebSocketUpdates() {
        // Observe connection state
        connectionStateJob = viewModelScope.launch {
            repository.connectionState.collect { connectionState ->
                val (isConnected, status) = when (connectionState) {
                    is WebSocketConnectionState.Connected -> true to "Live"
                    is WebSocketConnectionState.Connecting -> false to "Connecting..."
                    is WebSocketConnectionState.Reconnecting -> false to "Reconnecting (${connectionState.attempt}/${connectionState.maxAttempts})..."
                    is WebSocketConnectionState.Disconnected -> false to "Disconnected"
                    is WebSocketConnectionState.Error -> false to "Error"
                    is WebSocketConnectionState.Idle -> false to "Offline"
                }
                _state.update { it.copy(isLiveConnected = isConnected, connectionStatus = status) }
            }
        }

        // Observe spot price updates
        spotPriceJob = viewModelScope.launch {
            repository.spotPriceUpdates.collect { spotUpdate ->
                val currentIndex = _state.value.selectedIndex
                if (spotUpdate.symbol == currentIndex.symbol) {
                    Log.d(TAG, "Live spot update: ${spotUpdate.symbol} = ${spotUpdate.price}, change=${spotUpdate.change}")

                    // Only update change values if they are non-zero
                    // This prevents chain_update messages (which have 0 change) from overwriting good values
                    val newChange = if (spotUpdate.change != 0.0) spotUpdate.change else _state.value.priceChange
                    val newChangePercent = if (spotUpdate.changePercent != 0.0) spotUpdate.changePercent else _state.value.priceChangePercent

                    _state.update {
                        it.copy(
                            spotPrice = spotUpdate.price,
                            priceChange = newChange,
                            priceChangePercent = newChangePercent
                        )
                    }

                    // Also update the option chain with new spot price
                    updateOptionChainSpotPrice(spotUpdate.price, newChange, newChangePercent)
                }
            }
        }

        // Observe option data updates
        optionUpdateJob = viewModelScope.launch {
            repository.subscribeToPriceUpdates(emptyList()).collect { updates ->
                if (updates.isNotEmpty()) {
                    Log.d(TAG, "Live option updates: ${updates.size} options")
                    updateOptionChainData(updates)
                }
            }
        }
    }

    /**
     * Update option chain with new spot price
     */
    private fun updateOptionChainSpotPrice(spotPrice: Double, change: Double?, changePercent: Double?) {
        val currentState = _state.value.optionChainState
        if (currentState is Resource.Success && currentState.data != null) {
            val updatedChain = currentState.data.copy(
                spotPrice = spotPrice,
                priceChange = change,
                priceChangePercent = changePercent,
                timestamp = System.currentTimeMillis()
            )
            _state.update { it.copy(optionChainState = Resource.Success(updatedChain)) }
        }
    }

    /**
     * Update option chain with live option data
     * IMPORTANT: Preserve OI from original data if update doesn't have it
     */
    private fun updateOptionChainData(updates: Map<String, OptionData>) {
        val currentState = _state.value.optionChainState
        if (currentState is Resource.Success && currentState.data != null) {
            val chain = currentState.data

            // Update rows with new option data, preserving OI
            val updatedRows = chain.rows.map { row ->
                var updatedCall = row.callData
                var updatedPut = row.putData

                // Find matching updates for this strike using toLong() for safe comparison
                updates.values.forEach { update ->
                    if (update.strikePrice.toLong() == row.strikePrice.toLong()) {
                        when (update.optionType) {
                            com.optix.app.domain.model.OptionType.CALL -> {
                                // Merge: use new price data but preserve OI if update has 0
                                updatedCall = row.callData?.let { existing ->
                                    update.copy(
                                        openInterest = if (update.openInterest > 0) update.openInterest else existing.openInterest,
                                        oiChange = if (update.oiChange != 0L) update.oiChange else existing.oiChange,
                                        volume = if (update.volume > 0) update.volume else existing.volume,
                                        impliedVolatility = if (update.impliedVolatility > 0) update.impliedVolatility else existing.impliedVolatility
                                    )
                                } ?: update
                            }
                            com.optix.app.domain.model.OptionType.PUT -> {
                                // Merge: use new price data but preserve OI if update has 0
                                updatedPut = row.putData?.let { existing ->
                                    update.copy(
                                        openInterest = if (update.openInterest > 0) update.openInterest else existing.openInterest,
                                        oiChange = if (update.oiChange != 0L) update.oiChange else existing.oiChange,
                                        volume = if (update.volume > 0) update.volume else existing.volume,
                                        impliedVolatility = if (update.impliedVolatility > 0) update.impliedVolatility else existing.impliedVolatility
                                    )
                                } ?: update
                            }
                        }
                    }
                }

                row.copy(callData = updatedCall, putData = updatedPut)
            }

            val updatedChain = chain.copy(
                rows = updatedRows,
                timestamp = System.currentTimeMillis()
            )
            _state.update { it.copy(optionChainState = Resource.Success(updatedChain)) }
        }
    }

    /**
     * Connect to live updates
     */
    fun connectToLiveUpdates() {
        val expiry = _state.value.selectedExpiry ?: return
        repository.subscribeToLiveUpdates(_state.value.selectedIndex, expiry)
    }

    /**
     * Disconnect from live updates
     */
    fun disconnectFromLiveUpdates() {
        repository.disconnectFromLiveUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        spotPriceJob?.cancel()
        optionUpdateJob?.cancel()
        connectionStateJob?.cancel()
        disconnectFromLiveUpdates()
    }

    fun selectIndex(index: TradingIndex) {
        _state.update { it.copy(selectedIndex = index, selectedExpiry = null) }
        loadExpiries()
        loadOptionChain()
    }

    fun selectExpiry(expiry: String) {
        _state.update { it.copy(selectedExpiry = expiry) }
        loadOptionChain()
    }

    fun refresh() {
        loadOptionChain()
    }

    private fun loadExpiries() {
        viewModelScope.launch {
            when (val result = repository.getExpiries(_state.value.selectedIndex)) {
                is Resource.Success -> {
                    result.data?.let { expiries ->
                        val firstExpiry = expiries.firstOrNull()?.value
                        _state.update {
                            it.copy(
                                availableExpiries = expiries,
                                selectedExpiry = firstExpiry
                            )
                        }
                        // Load option chain after expiry is set
                        if (firstExpiry != null) {
                            loadOptionChain()
                        }
                    }
                }
                is Resource.Error -> {
                    // Handle error silently for expiries
                }
                is Resource.Loading -> {
                    // Loading state handled elsewhere
                }
            }
        }
    }

    private fun loadOptionChain() {
        val expiry = _state.value.selectedExpiry ?: return

        viewModelScope.launch {
            _state.update { it.copy(optionChainState = Resource.Loading()) }

            when (val result = repository.getOptionChain(
                index = _state.value.selectedIndex,
                expiry = expiry
            )) {
                is Resource.Success -> {
                    result.data?.let { chain ->
                        _state.update {
                            it.copy(
                                optionChainState = Resource.Success(chain),
                                spotPrice = chain.spotPrice,
                                priceChange = chain.priceChange,
                                priceChangePercent = chain.priceChangePercent
                            )
                        }

                        // Connect to WebSocket for live updates after initial load
                        connectToLiveUpdates()
                    }
                }
                is Resource.Error -> {
                    _state.update {
                        it.copy(optionChainState = Resource.Error(result.message ?: "Unknown error"))
                    }
                }
                is Resource.Loading -> {
                    // Already set above
                }
            }
        }
    }
}
