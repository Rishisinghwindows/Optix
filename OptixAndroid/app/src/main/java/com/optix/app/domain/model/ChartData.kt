package com.optix.app.domain.model

/**
 * Candlestick data model for charting
 * OHLCV structure for price charts
 */
data class Candlestick(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
) {
    val isBullish: Boolean get() = close >= open
    val isBearish: Boolean get() = close < open
    val bodyHeight: Double get() = kotlin.math.abs(close - open)
    val wickHigh: Double get() = high - maxOf(open, close)
    val wickLow: Double get() = minOf(open, close) - low
    val range: Double get() = high - low
}

/**
 * Chart data wrapper containing candlesticks and metadata
 */
data class ChartData(
    val symbol: String,
    val timeframe: ChartTimeframe,
    val candlesticks: List<Candlestick>,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isEmpty: Boolean get() = candlesticks.isEmpty()

    val highestPrice: Double get() = candlesticks.maxOfOrNull { it.high } ?: 0.0
    val lowestPrice: Double get() = candlesticks.minOfOrNull { it.low } ?: 0.0
    val maxVolume: Long get() = candlesticks.maxOfOrNull { it.volume } ?: 0L

    val latestPrice: Double get() = candlesticks.lastOrNull()?.close ?: 0.0
    val priceChange: Double get() {
        if (candlesticks.size < 2) return 0.0
        val firstCandle = candlesticks.first()
        val lastCandle = candlesticks.last()
        return lastCandle.close - firstCandle.open
    }

    val priceChangePercent: Double get() {
        if (candlesticks.size < 2) return 0.0
        val firstCandle = candlesticks.first()
        if (firstCandle.open == 0.0) return 0.0
        return (priceChange / firstCandle.open) * 100
    }
}

/**
 * Chart timeframe options
 */
enum class ChartTimeframe(
    val displayName: String,
    val shortName: String,
    val intervalMinutes: Int,
    val candleCount: Int
) {
    ONE_MINUTE("1 Minute", "1m", 1, 60),
    FIVE_MINUTES("5 Minutes", "5m", 5, 78),
    FIFTEEN_MINUTES("15 Minutes", "15m", 15, 52),
    ONE_HOUR("1 Hour", "1H", 60, 24),
    ONE_DAY("1 Day", "1D", 1440, 30);

    companion object {
        fun fromShortName(name: String): ChartTimeframe? {
            return entries.find { it.shortName.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * Moving average data
 */
data class MovingAverage(
    val period: Int,
    val values: List<Double>,
    val type: MAType = MAType.SMA
)

enum class MAType {
    SMA,  // Simple Moving Average
    EMA   // Exponential Moving Average
}

/**
 * Technical indicators bundle
 */
data class TechnicalIndicators(
    val sma20: List<Double> = emptyList(),
    val sma50: List<Double> = emptyList(),
    val ema9: List<Double> = emptyList(),
    val ema21: List<Double> = emptyList(),
    val vwap: Double? = null
)

/**
 * Price crosshair data for touch interaction
 */
data class CrosshairData(
    val candlestick: Candlestick,
    val index: Int,
    val x: Float,
    val y: Float
)
