package com.optix.app.presentation.screens.charts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.domain.model.Candlestick
import com.optix.app.domain.model.ChartData
import com.optix.app.domain.model.ChartTimeframe
import com.optix.app.domain.model.TechnicalIndicators
import com.optix.app.domain.model.TradingIndex
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

data class ChartsState(
    val selectedIndex: TradingIndex = TradingIndex.NIFTY50,
    val selectedTimeframe: ChartTimeframe = ChartTimeframe.FIVE_MINUTES,
    val chartData: ChartData? = null,
    val technicalIndicators: TechnicalIndicators = TechnicalIndicators(),
    val spotPrice: Double = 0.0,
    val priceChange: Double = 0.0,
    val priceChangePercent: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val showVolume: Boolean = true,
    val showMA: Boolean = true,
    val selectedMAType: MADisplayType = MADisplayType.SMA_20_50
)

enum class MADisplayType(val displayName: String) {
    SMA_20_50("SMA 20/50"),
    EMA_9_21("EMA 9/21"),
    ALL("All MAs"),
    NONE("None")
}

@HiltViewModel
class ChartsViewModel @Inject constructor() : ViewModel() {

    companion object {
        private const val TAG = "ChartsVM"
    }

    private val _state = MutableStateFlow(ChartsState())
    val state: StateFlow<ChartsState> = _state.asStateFlow()

    private var priceUpdateJob: Job? = null
    private var chartUpdateJob: Job? = null

    init {
        loadChartData()
        startPriceUpdates()
    }

    fun selectIndex(index: TradingIndex) {
        _state.update { it.copy(selectedIndex = index) }
        loadChartData()
    }

    fun selectTimeframe(timeframe: ChartTimeframe) {
        _state.update { it.copy(selectedTimeframe = timeframe) }
        loadChartData()
    }

    fun toggleVolume() {
        _state.update { it.copy(showVolume = !it.showVolume) }
    }

    fun toggleMA() {
        _state.update { it.copy(showMA = !it.showMA) }
    }

    fun setMADisplayType(type: MADisplayType) {
        _state.update { it.copy(selectedMAType = type) }
    }

    fun refresh() {
        loadChartData()
    }

    private fun loadChartData() {
        chartUpdateJob?.cancel()
        chartUpdateJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            try {
                // Simulate API call delay
                delay(500)

                val index = _state.value.selectedIndex
                val timeframe = _state.value.selectedTimeframe

                // Generate sample chart data (in production, this would come from API)
                val chartData = generateSampleChartData(index, timeframe)
                val indicators = calculateTechnicalIndicators(chartData.candlesticks)

                val lastCandle = chartData.candlesticks.lastOrNull()
                val firstCandle = chartData.candlesticks.firstOrNull()

                val spotPrice = lastCandle?.close ?: getBasePrice(index)
                val priceChange = if (firstCandle != null && lastCandle != null) {
                    lastCandle.close - firstCandle.open
                } else 0.0
                val priceChangePercent = if (firstCandle != null && firstCandle.open != 0.0) {
                    (priceChange / firstCandle.open) * 100
                } else 0.0

                _state.update {
                    it.copy(
                        chartData = chartData,
                        technicalIndicators = indicators,
                        spotPrice = spotPrice,
                        priceChange = priceChange,
                        priceChangePercent = priceChangePercent,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chart data", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load chart data"
                    )
                }
            }
        }
    }

    private fun startPriceUpdates() {
        priceUpdateJob?.cancel()
        priceUpdateJob = viewModelScope.launch {
            while (true) {
                delay(3000) // Update every 3 seconds

                val currentState = _state.value
                if (currentState.chartData != null && currentState.chartData.candlesticks.isNotEmpty()) {
                    // Simulate small price movement
                    val lastPrice = currentState.spotPrice
                    val change = lastPrice * Random.nextDouble(-0.0005, 0.0005)
                    val newPrice = lastPrice + change

                    // Update last candle
                    val updatedCandles = currentState.chartData.candlesticks.toMutableList()
                    val lastCandle = updatedCandles.last()
                    updatedCandles[updatedCandles.lastIndex] = lastCandle.copy(
                        close = newPrice,
                        high = maxOf(lastCandle.high, newPrice),
                        low = minOf(lastCandle.low, newPrice)
                    )

                    val firstCandle = updatedCandles.first()
                    val priceChange = newPrice - firstCandle.open
                    val priceChangePercent = if (firstCandle.open != 0.0) {
                        (priceChange / firstCandle.open) * 100
                    } else 0.0

                    _state.update {
                        it.copy(
                            chartData = it.chartData?.copy(
                                candlesticks = updatedCandles,
                                lastUpdated = System.currentTimeMillis()
                            ),
                            spotPrice = newPrice,
                            priceChange = priceChange,
                            priceChangePercent = priceChangePercent
                        )
                    }
                }
            }
        }
    }

    private fun getBasePrice(index: TradingIndex): Double {
        return when (index) {
            TradingIndex.NIFTY50 -> 24500.0
            TradingIndex.BANKNIFTY -> 51500.0
            TradingIndex.FINNIFTY -> 23800.0
            TradingIndex.MIDCPNIFTY -> 12500.0
            TradingIndex.SENSEX -> 81000.0
            TradingIndex.BANKEX -> 56000.0
        }
    }

    private fun generateSampleChartData(index: TradingIndex, timeframe: ChartTimeframe): ChartData {
        val basePrice = getBasePrice(index)
        val candleCount = timeframe.candleCount
        val intervalMs = timeframe.intervalMinutes * 60 * 1000L

        val now = System.currentTimeMillis()
        val startTime = now - (candleCount * intervalMs)

        var currentPrice = basePrice * (1 + Random.nextDouble(-0.02, 0.02))
        val candlesticks = mutableListOf<Candlestick>()

        for (i in 0 until candleCount) {
            val timestamp = startTime + (i * intervalMs)

            // Generate OHLC with realistic movement
            val volatility = when (index) {
                TradingIndex.BANKNIFTY -> 0.003
                TradingIndex.SENSEX -> 0.002
                else -> 0.0025
            }

            val change = currentPrice * Random.nextDouble(-volatility, volatility)
            val open = currentPrice
            val close = currentPrice + change

            val wickRange = abs(change) * Random.nextDouble(0.5, 2.0)
            val high = maxOf(open, close) + wickRange * Random.nextDouble(0.0, 1.0)
            val low = minOf(open, close) - wickRange * Random.nextDouble(0.0, 1.0)

            // Generate volume with some correlation to price movement
            val baseVolume = when (index) {
                TradingIndex.NIFTY50 -> 500000L
                TradingIndex.BANKNIFTY -> 800000L
                else -> 300000L
            }
            val volumeMultiplier = 1 + abs(change / currentPrice) * 10
            val volume = (baseVolume * volumeMultiplier * Random.nextDouble(0.5, 1.5)).toLong()

            candlesticks.add(
                Candlestick(
                    timestamp = timestamp,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume
                )
            )

            currentPrice = close
        }

        return ChartData(
            symbol = index.symbol,
            timeframe = timeframe,
            candlesticks = candlesticks
        )
    }

    private fun calculateTechnicalIndicators(candlesticks: List<Candlestick>): TechnicalIndicators {
        if (candlesticks.isEmpty()) return TechnicalIndicators()

        val closes = candlesticks.map { it.close }

        return TechnicalIndicators(
            sma20 = calculateSMA(closes, 20),
            sma50 = calculateSMA(closes, 50),
            ema9 = calculateEMA(closes, 9),
            ema21 = calculateEMA(closes, 21),
            vwap = calculateVWAP(candlesticks)
        )
    }

    private fun calculateSMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period) return emptyList()

        val sma = mutableListOf<Double>()
        // Pad with nulls for indices before we have enough data
        repeat(period - 1) { sma.add(0.0) }

        for (i in (period - 1) until prices.size) {
            val sum = prices.subList(i - period + 1, i + 1).sum()
            sma.add(sum / period)
        }

        return sma
    }

    private fun calculateEMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period) return emptyList()

        val multiplier = 2.0 / (period + 1)
        val ema = mutableListOf<Double>()

        // First EMA value is SMA
        val firstSMA = prices.take(period).average()
        repeat(period - 1) { ema.add(0.0) }
        ema.add(firstSMA)

        var previousEMA = firstSMA
        for (i in period until prices.size) {
            val currentEMA = (prices[i] - previousEMA) * multiplier + previousEMA
            ema.add(currentEMA)
            previousEMA = currentEMA
        }

        return ema
    }

    private fun calculateVWAP(candlesticks: List<Candlestick>): Double {
        if (candlesticks.isEmpty()) return 0.0

        var cumulativeTPV = 0.0 // Typical Price * Volume
        var cumulativeVolume = 0L

        for (candle in candlesticks) {
            val typicalPrice = (candle.high + candle.low + candle.close) / 3
            cumulativeTPV += typicalPrice * candle.volume
            cumulativeVolume += candle.volume
        }

        return if (cumulativeVolume > 0) cumulativeTPV / cumulativeVolume else 0.0
    }

    override fun onCleared() {
        super.onCleared()
        priceUpdateJob?.cancel()
        chartUpdateJob?.cancel()
    }
}
