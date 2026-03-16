package com.optix.app.data.repository

import android.util.Log
import com.optix.app.core.util.Resource
import com.optix.app.data.remote.api.OptixApiService
import com.optix.app.data.remote.websocket.OptionChainWebSocketService
import com.optix.app.data.remote.websocket.SpotPriceUpdate
import com.optix.app.data.remote.websocket.WebSocketConnectionState
import com.optix.app.domain.model.*
import com.optix.app.domain.repository.OptionChainRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OptionChainRepositoryImpl @Inject constructor(
    private val api: OptixApiService,
    private val webSocketService: OptionChainWebSocketService
) : OptionChainRepository {

    companion object {
        private const val TAG = "OptionChainRepo"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val priceUpdatesFlow = MutableSharedFlow<Map<String, OptionData>>(extraBufferCapacity = 100)

    // Expose spot price updates
    private val _spotPriceUpdates = MutableSharedFlow<SpotPriceUpdate>(extraBufferCapacity = 100)
    override val spotPriceUpdates: SharedFlow<SpotPriceUpdate> = _spotPriceUpdates.asSharedFlow()

    // Connection state
    override val connectionState: StateFlow<WebSocketConnectionState> = webSocketService.connectionState

    // Current subscription state
    private var currentIndex: TradingIndex? = null
    private var currentExpiry: String? = null

    init {
        // Forward WebSocket updates to the repository flows
        scope.launch {
            webSocketService.optionDataUpdates.collect { updates ->
                priceUpdatesFlow.emit(updates)
            }
        }

        scope.launch {
            webSocketService.spotPriceUpdates.collect { update ->
                _spotPriceUpdates.emit(update)
                Log.d(TAG, "Spot price update: ${update.symbol} = ${update.price}")
            }
        }

        scope.launch {
            webSocketService.chainUpdates.collect { chainUpdate ->
                priceUpdatesFlow.emit(chainUpdate.options)
                Log.d(TAG, "Chain update: ${chainUpdate.symbol} with ${chainUpdate.options.size} options")
            }
        }
    }

    /**
     * Connect to WebSocket for live updates
     */
    override fun connectToLiveUpdates() {
        webSocketService.connect()
    }

    /**
     * Disconnect from WebSocket
     */
    override fun disconnectFromLiveUpdates() {
        webSocketService.disconnect()
    }

    /**
     * Subscribe to live updates for an index and expiry
     */
    override fun subscribeToLiveUpdates(index: TradingIndex, expiry: String) {
        currentIndex = index
        currentExpiry = expiry

        // Connect if not already connected
        if (connectionState.value !is WebSocketConnectionState.Connected) {
            webSocketService.connect()
        }

        // Subscribe to index spot price
        webSocketService.subscribeToIndex(index)

        // Subscribe to option chain for the selected expiry
        webSocketService.subscribeToOptionChain(index, expiry)

        Log.d(TAG, "Subscribed to live updates: ${index.symbol}, $expiry")
    }

    /**
     * Unsubscribe from live updates
     */
    fun unsubscribeFromLiveUpdates() {
        currentIndex?.let { webSocketService.unsubscribeFromIndex(it) }
        currentIndex = null
        currentExpiry = null
    }

    override suspend fun getOptionChain(
        index: TradingIndex,
        expiry: String
    ): Resource<OptionChain> = withContext(Dispatchers.IO) {
        try {
            val response = api.getOptionChain(
                index = index.apiSymbol,
                expiry = expiry
            )

            // Also fetch spot price to get intraday change percentage
            var spotPriceChange: Double? = null
            var spotPriceChangePercent: Double? = null
            try {
                val spotResponse = api.getSpotPrice(index.apiSymbol)
                if (spotResponse.isSuccessful && spotResponse.body() != null) {
                    val spotDto = spotResponse.body()!!
                    spotPriceChange = spotDto.change
                    spotPriceChangePercent = spotDto.changePercent
                    Log.d(TAG, "Spot price change for ${index.symbol}: change=${spotDto.change}, changePercent=${spotDto.changePercent}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not fetch spot price for intraday change: ${e.message}")
            }

            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                // Debug: Log strikes near ATM to check OI data
                val atmStrike = dto.atmStrike
                Log.d(TAG, "ATM Strike: $atmStrike, Total rows: ${dto.data.size}")
                dto.data.filter { kotlin.math.abs(it.strikePrice - atmStrike) <= 300 }.forEach { rowDto ->
                    Log.d(TAG, "Strike ${rowDto.strikePrice}: Call OI=${rowDto.callData?.effectiveOpenInterest?.toLong()}, Put OI=${rowDto.putData?.effectiveOpenInterest?.toLong()}")
                }
                val rows = dto.data.map { rowDto ->
                    OptionChainRow(
                        strikePrice = rowDto.strikePrice,
                        callData = rowDto.callData?.let { call ->
                            OptionData(
                                strikePrice = rowDto.strikePrice,
                                optionType = OptionType.CALL,
                                expiry = expiry,
                                lastPrice = call.lastPrice,
                                change = call.change,
                                changePercent = call.pChange,
                                openInterest = call.effectiveOpenInterest.toLong(),
                                oiChange = call.changeinOpenInterest.toLong(),
                                volume = call.totalTradedVolume,
                                impliedVolatility = call.impliedVolatility,
                                bidPrice = call.bidprice,
                                askPrice = call.askPrice,
                                bidQty = call.bidQty,
                                askQty = call.askQty,
                                delta = 0.0,
                                gamma = 0.0,
                                theta = 0.0,
                                vega = 0.0,
                                underlyingSpot = dto.underlyingValue
                            )
                        },
                        putData = rowDto.putData?.let { put ->
                            OptionData(
                                strikePrice = rowDto.strikePrice,
                                optionType = OptionType.PUT,
                                expiry = expiry,
                                lastPrice = put.lastPrice,
                                change = put.change,
                                changePercent = put.pChange,
                                openInterest = put.effectiveOpenInterest.toLong(),
                                oiChange = put.changeinOpenInterest.toLong(),
                                volume = put.totalTradedVolume,
                                impliedVolatility = put.impliedVolatility,
                                bidPrice = put.bidprice,
                                askPrice = put.askPrice,
                                bidQty = put.bidQty,
                                askQty = put.askQty,
                                delta = 0.0,
                                gamma = 0.0,
                                theta = 0.0,
                                vega = 0.0,
                                underlyingSpot = dto.underlyingValue
                            )
                        }
                    )
                }

                Resource.Success(
                    OptionChain(
                        index = index,
                        expiry = expiry,
                        spotPrice = dto.underlyingValue,
                        priceChange = spotPriceChange,
                        priceChangePercent = spotPriceChangePercent,
                        atmStrike = dto.atmStrike,
                        rows = rows,
                        timestamp = System.currentTimeMillis(),
                        fullChainCallOI = dto.totals?.CE?.totalOI?.toLong(),
                        fullChainPutOI = dto.totals?.PE?.totalOI?.toLong()
                    )
                )
            } else {
                Resource.Error("Failed to fetch option chain")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error occurred")
        }
    }

    override suspend fun getExpiries(index: TradingIndex): Resource<List<ExpiryDate>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getExpiries(index.apiSymbol)

                if (response.isSuccessful && response.body() != null) {
                    val dateStrings = response.body()!!.expiryDates
                    val today = java.time.LocalDate.now()
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH)

                    val expiries = dateStrings.mapIndexed { idx, dateStr ->
                        val expiryDate = try {
                            java.time.LocalDate.parse(dateStr, formatter)
                        } catch (e: Exception) {
                            today.plusDays(idx.toLong() * 7)
                        }
                        val daysToExpiry = java.time.temporal.ChronoUnit.DAYS.between(today, expiryDate).toInt()

                        ExpiryDate(
                            value = dateStr,
                            displayDate = dateStr,
                            daysToExpiry = daysToExpiry,
                            isWeekly = daysToExpiry <= 7,
                            isMonthly = expiryDate.dayOfMonth > 24
                        )
                    }
                    Resource.Success(expiries)
                } else {
                    Resource.Error("Failed to fetch expiries")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override suspend fun getSpotPrice(symbol: String): Resource<Double> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getSpotPrice(symbol)

                if (response.isSuccessful && response.body() != null) {
                    Resource.Success(response.body()!!.price)
                } else {
                    Resource.Error("Failed to fetch spot price")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override fun subscribeToPriceUpdates(instrumentKeys: List<String>): Flow<Map<String, OptionData>> {
        // Subscribe to specific instruments via WebSocket
        if (instrumentKeys.isNotEmpty()) {
            webSocketService.subscribeToInstruments(instrumentKeys)
        }
        return priceUpdatesFlow
    }

    override fun unsubscribeFromPriceUpdates() {
        webSocketService.unsubscribeAll()
    }
}
