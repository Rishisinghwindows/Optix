package com.optix.app.core.calculation

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Technical Analysis Engine for calculating technical indicators and detecting patterns
 */
@Singleton
class TechnicalAnalysisEngine @Inject constructor() {

    companion object {
        // RSI thresholds
        private const val RSI_OVERBOUGHT = 70.0
        private const val RSI_SLIGHTLY_OVERBOUGHT = 60.0
        private const val RSI_OVERSOLD = 30.0
        private const val RSI_SLIGHTLY_OVERSOLD = 40.0

        // Default periods
        const val DEFAULT_RSI_PERIOD = 14
        const val DEFAULT_SMA_SHORT = 20
        const val DEFAULT_SMA_MEDIUM = 50
        const val DEFAULT_SMA_LONG = 200
        const val DEFAULT_EMA_SHORT = 9
        const val DEFAULT_EMA_MEDIUM = 21
        const val DEFAULT_MACD_FAST = 12
        const val DEFAULT_MACD_SLOW = 26
        const val DEFAULT_MACD_SIGNAL = 9
        const val DEFAULT_BOLLINGER_PERIOD = 20
        const val DEFAULT_BOLLINGER_STD_DEV = 2.0
        const val DEFAULT_ATR_PERIOD = 14

        // Pattern detection thresholds
        private const val DOJI_BODY_RATIO = 0.1
        private const val HAMMER_BODY_RATIO = 0.35
        private const val MARUBOZU_BODY_RATIO = 0.85
        private const val SPINNING_TOP_BODY_RATIO = 0.3
    }

    // ============================================
    // Main Analysis Entry Point
    // ============================================

    /**
     * Perform complete technical analysis on candle data
     */
    fun analyze(candles: List<OHLCData>): TechnicalAnalysisResult {
        if (candles.size < 20) {
            return TechnicalAnalysisResult.empty()
        }

        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val volumes = candles.map { it.volume.toDouble() }

        // Calculate all indicators
        val sma20 = calculateSMA(closes, DEFAULT_SMA_SHORT)
        val sma50 = calculateSMA(closes, DEFAULT_SMA_MEDIUM)
        val sma200 = calculateSMA(closes, DEFAULT_SMA_LONG)
        val ema9 = calculateEMA(closes, DEFAULT_EMA_SHORT)
        val ema21 = calculateEMA(closes, DEFAULT_EMA_MEDIUM)

        val rsi = calculateRSI(closes, DEFAULT_RSI_PERIOD)
        val macd = calculateMACD(closes)
        val atr = calculateATR(highs, lows, closes, DEFAULT_ATR_PERIOD)
        val bollingerBands = calculateBollingerBands(closes, DEFAULT_BOLLINGER_PERIOD)
        val vwap = calculateVWAP(candles)
        val volumeAnalysis = analyzeVolume(volumes)

        // Determine trend
        val trend = determineTrend(
            currentPrice = closes.last(),
            sma20 = sma20,
            sma50 = sma50,
            sma200 = sma200,
            ema9 = ema9,
            ema21 = ema21
        )

        // Calculate support and resistance from price action
        val supportResistance = calculateSupportResistance(highs, lows, closes)

        // Generate overall signal
        val signal = generateOverallSignal(
            rsi = rsi,
            macd = macd,
            trend = trend,
            currentPrice = closes.last(),
            bollingerBands = bollingerBands
        )

        // Generate technical insights
        val insights = generateInsights(
            rsi = rsi,
            macd = macd,
            trend = trend,
            bollingerBands = bollingerBands,
            currentPrice = closes.last(),
            sma20 = sma20,
            sma50 = sma50,
            volumeAnalysis = volumeAnalysis
        )

        // Detect candlestick patterns
        val candlestickPatterns = detectPatterns(candles)

        return TechnicalAnalysisResult(
            trend = trend,
            signal = signal,
            rsi = rsi,
            macd = macd,
            sma20 = sma20,
            sma50 = sma50,
            sma200 = sma200,
            ema9 = ema9,
            ema21 = ema21,
            atr = atr,
            bollingerBands = bollingerBands,
            vwap = vwap,
            supportLevels = supportResistance.first,
            resistanceLevels = supportResistance.second,
            volumeAnalysis = volumeAnalysis,
            insights = insights,
            candlestickPatterns = candlestickPatterns
        )
    }

    // ============================================
    // Simple Moving Average (SMA)
    // ============================================

    /**
     * Calculate Simple Moving Average
     */
    fun calculateSMA(prices: List<Double>, period: Int): Double? {
        if (prices.size < period) return null
        return prices.takeLast(period).average()
    }

    /**
     * Calculate SMA series for charting
     */
    fun calculateSMASeries(prices: List<Double>, period: Int): List<Double?> {
        return prices.indices.map { i ->
            if (i < period - 1) null
            else prices.subList(i - period + 1, i + 1).average()
        }
    }

    // ============================================
    // Exponential Moving Average (EMA)
    // ============================================

    /**
     * Calculate Exponential Moving Average
     */
    fun calculateEMA(prices: List<Double>, period: Int): Double? {
        if (prices.size < period) return null

        val multiplier = 2.0 / (period + 1)

        // Start with SMA for first EMA value
        var ema = prices.take(period).average()

        // Calculate EMA for remaining prices
        for (i in period until prices.size) {
            ema = (prices[i] - ema) * multiplier + ema
        }

        return ema
    }

    /**
     * Calculate EMA series for charting
     */
    fun calculateEMASeries(prices: List<Double>, period: Int): List<Double?> {
        if (prices.size < period) return prices.map { null }

        val multiplier = 2.0 / (period + 1)
        val result = mutableListOf<Double?>()

        // Fill with null for initial period
        for (i in 0 until period - 1) {
            result.add(null)
        }

        // Initial EMA is SMA
        var ema = prices.take(period).average()
        result.add(ema)

        // Calculate remaining EMAs
        for (i in period until prices.size) {
            ema = (prices[i] - ema) * multiplier + ema
            result.add(ema)
        }

        return result
    }

    // ============================================
    // Relative Strength Index (RSI)
    // ============================================

    /**
     * Calculate RSI with condition assessment
     */
    fun calculateRSI(prices: List<Double>, period: Int = DEFAULT_RSI_PERIOD): RSIResult {
        if (prices.size <= period) {
            return RSIResult(value = 50.0, condition = RSICondition.NEUTRAL)
        }

        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()

        for (i in 1 until prices.size) {
            val change = prices[i] - prices[i - 1]
            if (change > 0) {
                gains.add(change)
                losses.add(0.0)
            } else {
                gains.add(0.0)
                losses.add(abs(change))
            }
        }

        // Calculate average gain and loss
        val avgGain = gains.takeLast(period).average()
        val avgLoss = losses.takeLast(period).average()

        if (avgLoss == 0.0) {
            return RSIResult(value = 100.0, condition = RSICondition.OVERBOUGHT)
        }

        val rs = avgGain / avgLoss
        val rsi = 100 - (100 / (1 + rs))

        val condition = when {
            rsi >= RSI_OVERBOUGHT -> RSICondition.OVERBOUGHT
            rsi >= RSI_SLIGHTLY_OVERBOUGHT -> RSICondition.SLIGHTLY_OVERBOUGHT
            rsi <= RSI_OVERSOLD -> RSICondition.OVERSOLD
            rsi <= RSI_SLIGHTLY_OVERSOLD -> RSICondition.SLIGHTLY_OVERSOLD
            else -> RSICondition.NEUTRAL
        }

        return RSIResult(value = rsi, condition = condition)
    }

    /**
     * Calculate RSI series for charting
     */
    fun calculateRSISeries(prices: List<Double>, period: Int = DEFAULT_RSI_PERIOD): List<Double?> {
        if (prices.size <= period) return prices.map { null }

        val result = mutableListOf<Double?>()

        // Fill with null for initial period
        for (i in 0 until period) {
            result.add(null)
        }

        // Calculate RSI for each point
        for (i in period until prices.size) {
            val slice = prices.subList(0, i + 1)
            result.add(calculateRSI(slice, period).value)
        }

        return result
    }

    // ============================================
    // MACD (Moving Average Convergence Divergence)
    // ============================================

    /**
     * Calculate MACD with signal line and histogram
     */
    fun calculateMACD(
        prices: List<Double>,
        fastPeriod: Int = DEFAULT_MACD_FAST,
        slowPeriod: Int = DEFAULT_MACD_SLOW,
        signalPeriod: Int = DEFAULT_MACD_SIGNAL
    ): MACDResult {
        if (prices.size < slowPeriod + signalPeriod) {
            return MACDResult(
                macdLine = 0.0,
                signalLine = 0.0,
                histogram = 0.0,
                crossover = MACDCrossover.NONE
            )
        }

        // Calculate fast and slow EMAs
        val fastEMA = calculateEMA(prices, fastPeriod) ?: 0.0
        val slowEMA = calculateEMA(prices, slowPeriod) ?: 0.0

        val macdLine = fastEMA - slowEMA

        // Calculate MACD values for signal line
        val macdValues = mutableListOf<Double>()
        for (i in slowPeriod until prices.size) {
            val slice = prices.subList(0, i + 1)
            val fast = calculateEMA(slice, fastPeriod) ?: 0.0
            val slow = calculateEMA(slice, slowPeriod) ?: 0.0
            macdValues.add(fast - slow)
        }

        // Signal line is EMA of MACD
        val signalLine = calculateEMA(macdValues, signalPeriod) ?: 0.0
        val histogram = macdLine - signalLine

        // Determine crossover
        val crossover = if (macdValues.size >= 2) {
            val prevMACD = macdValues[macdValues.size - 2]
            val prevSignal = calculateEMA(macdValues.dropLast(1), signalPeriod) ?: 0.0

            when {
                prevMACD <= prevSignal && macdLine > signalLine -> MACDCrossover.BULLISH
                prevMACD >= prevSignal && macdLine < signalLine -> MACDCrossover.BEARISH
                else -> MACDCrossover.NONE
            }
        } else {
            MACDCrossover.NONE
        }

        return MACDResult(
            macdLine = macdLine,
            signalLine = signalLine,
            histogram = histogram,
            crossover = crossover
        )
    }

    // ============================================
    // Average True Range (ATR)
    // ============================================

    /**
     * Calculate Average True Range
     */
    fun calculateATR(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int = DEFAULT_ATR_PERIOD
    ): Double {
        if (highs.size < period + 1 || lows.size < period + 1 || closes.size < period + 1) {
            return 0.0
        }

        val trueRanges = mutableListOf<Double>()

        for (i in 1 until highs.size) {
            val highLow = highs[i] - lows[i]
            val highClose = abs(highs[i] - closes[i - 1])
            val lowClose = abs(lows[i] - closes[i - 1])
            val tr = maxOf(highLow, highClose, lowClose)
            trueRanges.add(tr)
        }

        return trueRanges.takeLast(period).average()
    }

    // ============================================
    // Bollinger Bands
    // ============================================

    /**
     * Calculate Bollinger Bands
     */
    fun calculateBollingerBands(
        prices: List<Double>,
        period: Int = DEFAULT_BOLLINGER_PERIOD,
        standardDeviations: Double = DEFAULT_BOLLINGER_STD_DEV
    ): BollingerBands {
        if (prices.size < period) {
            val current = prices.lastOrNull() ?: 0.0
            return BollingerBands(
                upper = current,
                middle = current,
                lower = current,
                bandwidth = 0.0,
                position = BollingerPosition.MIDDLE
            )
        }

        val slice = prices.takeLast(period)
        val middle = slice.average()

        // Calculate standard deviation
        val variance = slice.map { (it - middle).pow(2) }.average()
        val stdDev = sqrt(variance)

        val upper = middle + (standardDeviations * stdDev)
        val lower = middle - (standardDeviations * stdDev)
        val bandwidth = if (middle > 0) (upper - lower) / middle * 100 else 0.0

        val currentPrice = prices.last()
        val position = when {
            currentPrice >= upper -> BollingerPosition.ABOVE_UPPER
            currentPrice <= lower -> BollingerPosition.BELOW_LOWER
            currentPrice > middle -> BollingerPosition.UPPER_HALF
            else -> BollingerPosition.LOWER_HALF
        }

        return BollingerBands(
            upper = upper,
            middle = middle,
            lower = lower,
            bandwidth = bandwidth,
            position = position
        )
    }

    // ============================================
    // VWAP (Volume Weighted Average Price)
    // ============================================

    /**
     * Calculate VWAP
     */
    fun calculateVWAP(candles: List<OHLCData>): Double {
        if (candles.isEmpty()) return 0.0

        var cumulativeTPV = 0.0 // Typical Price * Volume
        var cumulativeVolume = 0.0

        for (candle in candles) {
            val typicalPrice = (candle.high + candle.low + candle.close) / 3
            cumulativeTPV += typicalPrice * candle.volume
            cumulativeVolume += candle.volume
        }

        return if (cumulativeVolume > 0) cumulativeTPV / cumulativeVolume else 0.0
    }

    /**
     * Calculate running VWAP series for charting
     */
    fun calculateVWAPSeries(candles: List<OHLCData>): List<Double> {
        if (candles.isEmpty()) return emptyList()

        val result = mutableListOf<Double>()
        var cumulativeTPV = 0.0
        var cumulativeVolume = 0.0

        for (candle in candles) {
            val typicalPrice = (candle.high + candle.low + candle.close) / 3
            cumulativeTPV += typicalPrice * candle.volume
            cumulativeVolume += candle.volume
            result.add(if (cumulativeVolume > 0) cumulativeTPV / cumulativeVolume else 0.0)
        }

        return result
    }

    // ============================================
    // Volume Analysis
    // ============================================

    /**
     * Analyze volume trend
     */
    fun analyzeVolume(volumes: List<Double>): VolumeAnalysis {
        if (volumes.size < 20) {
            return VolumeAnalysis(
                trend = VolumeTrend.NORMAL,
                relativeVolume = 1.0,
                averageVolume = 0.0
            )
        }

        val avgVolume = volumes.takeLast(20).average()
        val currentVolume = volumes.last()
        val relativeVolume = if (avgVolume > 0) currentVolume / avgVolume else 1.0

        val trend = when {
            relativeVolume >= 2.0 -> VolumeTrend.VERY_HIGH
            relativeVolume >= 1.5 -> VolumeTrend.HIGH
            relativeVolume <= 0.5 -> VolumeTrend.LOW
            else -> VolumeTrend.NORMAL
        }

        return VolumeAnalysis(
            trend = trend,
            relativeVolume = relativeVolume,
            averageVolume = avgVolume
        )
    }

    // ============================================
    // Trend Determination
    // ============================================

    /**
     * Determine overall market trend
     */
    private fun determineTrend(
        currentPrice: Double,
        sma20: Double?,
        sma50: Double?,
        sma200: Double?,
        ema9: Double?,
        ema21: Double?
    ): TrendDirection {
        var bullishPoints = 0
        var bearishPoints = 0

        // Price vs Moving Averages
        sma20?.let {
            if (currentPrice > it) bullishPoints += 1 else bearishPoints += 1
        }

        sma50?.let {
            if (currentPrice > it) bullishPoints += 2 else bearishPoints += 2
        }

        sma200?.let {
            if (currentPrice > it) bullishPoints += 3 else bearishPoints += 3
        }

        // EMA Crossovers
        if (ema9 != null && ema21 != null) {
            if (ema9 > ema21) bullishPoints += 2 else bearishPoints += 2
        }

        // Golden/Death Cross
        if (sma50 != null && sma200 != null) {
            if (sma50 > sma200) bullishPoints += 3 else bearishPoints += 3
        }

        val netScore = bullishPoints - bearishPoints

        return when {
            netScore >= 8 -> TrendDirection.STRONG_UPTREND
            netScore >= 4 -> TrendDirection.UPTREND
            netScore <= -8 -> TrendDirection.STRONG_DOWNTREND
            netScore <= -4 -> TrendDirection.DOWNTREND
            else -> TrendDirection.SIDEWAYS
        }
    }

    // ============================================
    // Support & Resistance
    // ============================================

    /**
     * Calculate support and resistance levels from price action
     */
    private fun calculateSupportResistance(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>
    ): Pair<List<SupportResistanceLevel>, List<SupportResistanceLevel>> {
        if (highs.isEmpty()) return Pair(emptyList(), emptyList())

        // Find local highs and lows (pivot points)
        val pivotHighs = mutableListOf<Double>()
        val pivotLows = mutableListOf<Double>()

        for (i in 2 until (highs.size - 2)) {
            // Local high
            if (highs[i] > highs[i - 1] && highs[i] > highs[i - 2] &&
                highs[i] > highs[i + 1] && highs[i] > highs[i + 2]
            ) {
                pivotHighs.add(highs[i])
            }

            // Local low
            if (lows[i] < lows[i - 1] && lows[i] < lows[i - 2] &&
                lows[i] < lows[i + 1] && lows[i] < lows[i + 2]
            ) {
                pivotLows.add(lows[i])
            }
        }

        // Cluster nearby levels
        val resistances = clusterLevels(pivotHighs, 0.005)
        val supports = clusterLevels(pivotLows, 0.005)

        // Return top 3 of each, sorted by proximity to current price
        val currentPrice = closes.last()

        val topResistances = resistances
            .filter { it > currentPrice }
            .sortedBy { abs(it - currentPrice) }
            .take(3)
            .mapIndexed { index, price ->
                SupportResistanceLevel(
                    price = price,
                    strength = when (index) {
                        0 -> LevelStrength.STRONG
                        1 -> LevelStrength.MODERATE
                        else -> LevelStrength.WEAK
                    },
                    touches = 1, // Simplified - could be enhanced with actual touch counting
                    type = LevelType.RESISTANCE
                )
            }

        val topSupports = supports
            .filter { it < currentPrice }
            .sortedBy { abs(it - currentPrice) }
            .take(3)
            .mapIndexed { index, price ->
                SupportResistanceLevel(
                    price = price,
                    strength = when (index) {
                        0 -> LevelStrength.STRONG
                        1 -> LevelStrength.MODERATE
                        else -> LevelStrength.WEAK
                    },
                    touches = 1,
                    type = LevelType.SUPPORT
                )
            }

        return Pair(topSupports, topResistances)
    }

    /**
     * Cluster nearby price levels
     */
    private fun clusterLevels(levels: List<Double>, threshold: Double): List<Double> {
        if (levels.isEmpty()) return emptyList()

        val clustered = mutableListOf<Double>()
        val sorted = levels.sorted()

        var currentCluster = mutableListOf(sorted[0])

        for (i in 1 until sorted.size) {
            val diff = (sorted[i] - sorted[i - 1]) / sorted[i - 1]
            if (diff < threshold) {
                currentCluster.add(sorted[i])
            } else {
                clustered.add(currentCluster.average())
                currentCluster = mutableListOf(sorted[i])
            }
        }
        clustered.add(currentCluster.average())

        return clustered
    }

    // ============================================
    // Overall Signal Generation
    // ============================================

    /**
     * Generate overall technical signal
     */
    private fun generateOverallSignal(
        rsi: RSIResult,
        macd: MACDResult,
        trend: TrendDirection,
        currentPrice: Double,
        bollingerBands: BollingerBands
    ): TechnicalSignal {
        var bullishPoints = 0
        var bearishPoints = 0

        // RSI
        when (rsi.condition) {
            RSICondition.OVERSOLD -> bullishPoints += 3
            RSICondition.SLIGHTLY_OVERSOLD -> bullishPoints += 1
            RSICondition.OVERBOUGHT -> bearishPoints += 3
            RSICondition.SLIGHTLY_OVERBOUGHT -> bearishPoints += 1
            RSICondition.NEUTRAL -> {}
        }

        // MACD
        if (macd.histogram > 0) bullishPoints += 1 else bearishPoints += 1

        when (macd.crossover) {
            MACDCrossover.BULLISH -> bullishPoints += 3
            MACDCrossover.BEARISH -> bearishPoints += 3
            MACDCrossover.NONE -> {}
        }

        // Trend
        when (trend) {
            TrendDirection.STRONG_UPTREND -> bullishPoints += 4
            TrendDirection.UPTREND -> bullishPoints += 2
            TrendDirection.STRONG_DOWNTREND -> bearishPoints += 4
            TrendDirection.DOWNTREND -> bearishPoints += 2
            TrendDirection.SIDEWAYS -> {}
        }

        // Bollinger Bands
        when (bollingerBands.position) {
            BollingerPosition.BELOW_LOWER -> bullishPoints += 2 // Potential reversal
            BollingerPosition.ABOVE_UPPER -> bearishPoints += 2 // Potential reversal
            else -> {}
        }

        val netScore = bullishPoints - bearishPoints
        val confidence = minOf(100.0, abs(netScore) * 10.0)

        val direction = when {
            netScore >= 6 -> SignalDirection.STRONG_BUY
            netScore >= 3 -> SignalDirection.BUY
            netScore <= -6 -> SignalDirection.STRONG_SELL
            netScore <= -3 -> SignalDirection.SELL
            else -> SignalDirection.NEUTRAL
        }

        return TechnicalSignal(direction = direction, confidence = confidence)
    }

    // ============================================
    // Insights Generation
    // ============================================

    /**
     * Generate technical insights
     */
    private fun generateInsights(
        rsi: RSIResult,
        macd: MACDResult,
        trend: TrendDirection,
        bollingerBands: BollingerBands,
        currentPrice: Double,
        sma20: Double?,
        sma50: Double?,
        volumeAnalysis: VolumeAnalysis
    ): List<TechnicalInsight> {
        val insights = mutableListOf<TechnicalInsight>()

        // Trend insight
        val trendInsight = when (trend) {
            TrendDirection.STRONG_UPTREND -> TechnicalInsight(
                type = InsightType.TREND,
                title = "Strong Uptrend",
                description = "Price is above all major moving averages with bullish alignment",
                signal = InsightSignal.BULLISH,
                importance = InsightImportance.HIGH
            )
            TrendDirection.UPTREND -> TechnicalInsight(
                type = InsightType.TREND,
                title = "Uptrend",
                description = "Price showing bullish momentum above key moving averages",
                signal = InsightSignal.BULLISH,
                importance = InsightImportance.MEDIUM
            )
            TrendDirection.STRONG_DOWNTREND -> TechnicalInsight(
                type = InsightType.TREND,
                title = "Strong Downtrend",
                description = "Price is below all major moving averages with bearish alignment",
                signal = InsightSignal.BEARISH,
                importance = InsightImportance.HIGH
            )
            TrendDirection.DOWNTREND -> TechnicalInsight(
                type = InsightType.TREND,
                title = "Downtrend",
                description = "Price showing bearish momentum below key moving averages",
                signal = InsightSignal.BEARISH,
                importance = InsightImportance.MEDIUM
            )
            TrendDirection.SIDEWAYS -> TechnicalInsight(
                type = InsightType.TREND,
                title = "Sideways/Consolidation",
                description = "No clear trend direction, price moving in a range",
                signal = InsightSignal.NEUTRAL,
                importance = InsightImportance.LOW
            )
        }
        insights.add(trendInsight)

        // RSI insight
        when (rsi.condition) {
            RSICondition.OVERBOUGHT -> insights.add(
                TechnicalInsight(
                    type = InsightType.RSI,
                    title = "RSI Overbought (${String.format("%.0f", rsi.value)})",
                    description = "Market may be overextended, watch for potential pullback",
                    signal = InsightSignal.BEARISH,
                    importance = InsightImportance.HIGH
                )
            )
            RSICondition.OVERSOLD -> insights.add(
                TechnicalInsight(
                    type = InsightType.RSI,
                    title = "RSI Oversold (${String.format("%.0f", rsi.value)})",
                    description = "Market may be oversold, watch for potential bounce",
                    signal = InsightSignal.BULLISH,
                    importance = InsightImportance.HIGH
                )
            )
            RSICondition.SLIGHTLY_OVERBOUGHT -> insights.add(
                TechnicalInsight(
                    type = InsightType.RSI,
                    title = "RSI Near Overbought (${String.format("%.0f", rsi.value)})",
                    description = "Approaching overbought territory",
                    signal = InsightSignal.BEARISH,
                    importance = InsightImportance.MEDIUM
                )
            )
            RSICondition.SLIGHTLY_OVERSOLD -> insights.add(
                TechnicalInsight(
                    type = InsightType.RSI,
                    title = "RSI Near Oversold (${String.format("%.0f", rsi.value)})",
                    description = "Approaching oversold territory",
                    signal = InsightSignal.BULLISH,
                    importance = InsightImportance.MEDIUM
                )
            )
            RSICondition.NEUTRAL -> {}
        }

        // MACD insight
        when (macd.crossover) {
            MACDCrossover.BULLISH -> insights.add(
                TechnicalInsight(
                    type = InsightType.MACD,
                    title = "MACD Bullish Crossover",
                    description = "MACD line crossed above signal line - bullish momentum",
                    signal = InsightSignal.BULLISH,
                    importance = InsightImportance.HIGH
                )
            )
            MACDCrossover.BEARISH -> insights.add(
                TechnicalInsight(
                    type = InsightType.MACD,
                    title = "MACD Bearish Crossover",
                    description = "MACD line crossed below signal line - bearish momentum",
                    signal = InsightSignal.BEARISH,
                    importance = InsightImportance.HIGH
                )
            )
            MACDCrossover.NONE -> {
                if (macd.histogram > 0) {
                    insights.add(
                        TechnicalInsight(
                            type = InsightType.MACD,
                            title = "MACD Positive",
                            description = "Histogram positive indicating bullish momentum",
                            signal = InsightSignal.BULLISH,
                            importance = InsightImportance.LOW
                        )
                    )
                } else if (macd.histogram < 0) {
                    insights.add(
                        TechnicalInsight(
                            type = InsightType.MACD,
                            title = "MACD Negative",
                            description = "Histogram negative indicating bearish momentum",
                            signal = InsightSignal.BEARISH,
                            importance = InsightImportance.LOW
                        )
                    )
                }
            }
        }

        // Bollinger Band insight
        when (bollingerBands.position) {
            BollingerPosition.ABOVE_UPPER -> insights.add(
                TechnicalInsight(
                    type = InsightType.BOLLINGER,
                    title = "Above Bollinger Upper Band",
                    description = "Price stretched above upper band, may revert to mean",
                    signal = InsightSignal.BEARISH,
                    importance = InsightImportance.MEDIUM
                )
            )
            BollingerPosition.BELOW_LOWER -> insights.add(
                TechnicalInsight(
                    type = InsightType.BOLLINGER,
                    title = "Below Bollinger Lower Band",
                    description = "Price stretched below lower band, may bounce",
                    signal = InsightSignal.BULLISH,
                    importance = InsightImportance.MEDIUM
                )
            )
            else -> {}
        }

        // Volume insight
        when (volumeAnalysis.trend) {
            VolumeTrend.VERY_HIGH -> insights.add(
                TechnicalInsight(
                    type = InsightType.VOLUME,
                    title = "Very High Volume",
                    description = "Volume ${String.format("%.1f", volumeAnalysis.relativeVolume)}x above average - strong conviction",
                    signal = InsightSignal.NEUTRAL,
                    importance = InsightImportance.HIGH
                )
            )
            VolumeTrend.HIGH -> insights.add(
                TechnicalInsight(
                    type = InsightType.VOLUME,
                    title = "Above Average Volume",
                    description = "Volume ${String.format("%.1f", volumeAnalysis.relativeVolume)}x above average",
                    signal = InsightSignal.NEUTRAL,
                    importance = InsightImportance.MEDIUM
                )
            )
            VolumeTrend.LOW -> insights.add(
                TechnicalInsight(
                    type = InsightType.VOLUME,
                    title = "Low Volume",
                    description = "Below average volume - weak conviction in current move",
                    signal = InsightSignal.NEUTRAL,
                    importance = InsightImportance.LOW
                )
            )
            VolumeTrend.NORMAL -> {}
        }

        // Moving Average crossover
        if (sma20 != null && sma50 != null) {
            if (sma20 > sma50 && abs(sma20 - sma50) / sma50 < 0.005) {
                insights.add(
                    TechnicalInsight(
                        type = InsightType.MOVING_AVERAGE,
                        title = "Golden Cross Forming",
                        description = "20 SMA crossing above 50 SMA - bullish signal",
                        signal = InsightSignal.BULLISH,
                        importance = InsightImportance.HIGH
                    )
                )
            } else if (sma20 < sma50 && abs(sma20 - sma50) / sma50 < 0.005) {
                insights.add(
                    TechnicalInsight(
                        type = InsightType.MOVING_AVERAGE,
                        title = "Death Cross Forming",
                        description = "20 SMA crossing below 50 SMA - bearish signal",
                        signal = InsightSignal.BEARISH,
                        importance = InsightImportance.HIGH
                    )
                )
            }
        }

        return insights.sortedByDescending { it.importance.ordinal }
    }

    // ============================================
    // Candlestick Pattern Detection
    // ============================================

    /**
     * Detect all candlestick patterns
     */
    fun detectPatterns(candles: List<OHLCData>): List<CandlestickPattern> {
        if (candles.size < 5) return emptyList()

        val patterns = mutableListOf<CandlestickPattern>()

        // Single candle patterns (check last 3 candles)
        for (i in maxOf(0, candles.size - 3) until candles.size) {
            val candle = candles[i]
            val prevCandles = if (i > 0) candles.subList(maxOf(0, i - 5), i) else emptyList()

            detectSingleCandlePattern(candle, prevCandles, i)?.let {
                patterns.add(it)
            }
        }

        // Two candle patterns (check last 2 pairs)
        for (i in maxOf(1, candles.size - 2) until candles.size) {
            val current = candles[i]
            val previous = candles[i - 1]
            val trend = detectTrend(candles.subList(maxOf(0, i - 10), i))

            detectTwoCandlePattern(current, previous, trend, i)?.let {
                patterns.add(it)
            }
        }

        // Three candle patterns (check last pattern)
        if (candles.size >= 3) {
            val lastThree = candles.takeLast(3)
            val trend = detectTrend(candles.dropLast(3).takeLast(10))

            detectThreeCandlePattern(lastThree, trend, candles.size - 1)?.let {
                patterns.add(it)
            }
        }

        return patterns
            .sortedByDescending { it.importance.ordinal }
            .take(5)
    }

    /**
     * Detect single candle patterns (Doji, Hammer, etc.)
     */
    private fun detectSingleCandlePattern(
        candle: OHLCData,
        previousCandles: List<OHLCData>,
        index: Int
    ): CandlestickPattern? {
        val bodySize = abs(candle.close - candle.open)
        val totalRange = candle.high - candle.low
        val upperWick = candle.high - maxOf(candle.open, candle.close)
        val lowerWick = minOf(candle.open, candle.close) - candle.low

        if (totalRange <= 0) return null

        val bodyRatio = bodySize / totalRange
        val upperWickRatio = upperWick / totalRange
        val lowerWickRatio = lowerWick / totalRange

        val trend = detectTrend(previousCandles)

        // DOJI - Very small body (< 10% of range)
        if (bodyRatio < DOJI_BODY_RATIO) {
            // Dragonfly Doji - Long lower wick, no upper wick
            if (lowerWickRatio > 0.6 && upperWickRatio < 0.1) {
                return CandlestickPattern(
                    type = PatternType.DRAGONFLY_DOJI,
                    signal = if (trend == TrendType.DOWN) PatternSignal.BULLISH else PatternSignal.NEUTRAL,
                    importance = PatternImportance.HIGH,
                    description = "Dragonfly Doji - Potential bullish reversal",
                    candleIndex = index
                )
            }

            // Gravestone Doji - Long upper wick, no lower wick
            if (upperWickRatio > 0.6 && lowerWickRatio < 0.1) {
                return CandlestickPattern(
                    type = PatternType.GRAVESTONE_DOJI,
                    signal = if (trend == TrendType.UP) PatternSignal.BEARISH else PatternSignal.NEUTRAL,
                    importance = PatternImportance.HIGH,
                    description = "Gravestone Doji - Potential bearish reversal",
                    candleIndex = index
                )
            }

            // Regular Doji
            return CandlestickPattern(
                type = PatternType.DOJI,
                signal = PatternSignal.NEUTRAL,
                importance = PatternImportance.MEDIUM,
                description = "Doji - Market indecision, potential reversal",
                candleIndex = index
            )
        }

        // HAMMER - Small body at top, long lower wick (in downtrend)
        if (trend == TrendType.DOWN && bodyRatio < HAMMER_BODY_RATIO &&
            lowerWickRatio > 0.5 && upperWickRatio < 0.15
        ) {
            return CandlestickPattern(
                type = PatternType.HAMMER,
                signal = PatternSignal.BULLISH,
                importance = PatternImportance.HIGH,
                description = "Hammer - Strong bullish reversal signal after downtrend",
                candleIndex = index
            )
        }

        // INVERTED HAMMER - Small body at bottom, long upper wick (in downtrend)
        if (trend == TrendType.DOWN && bodyRatio < HAMMER_BODY_RATIO &&
            upperWickRatio > 0.5 && lowerWickRatio < 0.15
        ) {
            return CandlestickPattern(
                type = PatternType.INVERTED_HAMMER,
                signal = PatternSignal.BULLISH,
                importance = PatternImportance.MEDIUM,
                description = "Inverted Hammer - Potential bullish reversal",
                candleIndex = index
            )
        }

        // SHOOTING STAR - Small body at bottom, long upper wick (in uptrend)
        if (trend == TrendType.UP && bodyRatio < HAMMER_BODY_RATIO &&
            upperWickRatio > 0.5 && lowerWickRatio < 0.15
        ) {
            return CandlestickPattern(
                type = PatternType.SHOOTING_STAR,
                signal = PatternSignal.BEARISH,
                importance = PatternImportance.HIGH,
                description = "Shooting Star - Strong bearish reversal signal after uptrend",
                candleIndex = index
            )
        }

        // HANGING MAN - Small body at top, long lower wick (in uptrend)
        if (trend == TrendType.UP && bodyRatio < HAMMER_BODY_RATIO &&
            lowerWickRatio > 0.5 && upperWickRatio < 0.15
        ) {
            return CandlestickPattern(
                type = PatternType.HANGING_MAN,
                signal = PatternSignal.BEARISH,
                importance = PatternImportance.HIGH,
                description = "Hanging Man - Bearish reversal warning after uptrend",
                candleIndex = index
            )
        }

        // MARUBOZU - Large body with no/tiny wicks
        if (bodyRatio > MARUBOZU_BODY_RATIO) {
            return if (candle.close > candle.open) {
                CandlestickPattern(
                    type = PatternType.BULLISH_MARUBOZU,
                    signal = PatternSignal.BULLISH,
                    importance = PatternImportance.MEDIUM,
                    description = "Bullish Marubozu - Strong buying pressure",
                    candleIndex = index
                )
            } else {
                CandlestickPattern(
                    type = PatternType.BEARISH_MARUBOZU,
                    signal = PatternSignal.BEARISH,
                    importance = PatternImportance.MEDIUM,
                    description = "Bearish Marubozu - Strong selling pressure",
                    candleIndex = index
                )
            }
        }

        // SPINNING TOP - Small body with equal wicks
        if (bodyRatio < SPINNING_TOP_BODY_RATIO && abs(upperWickRatio - lowerWickRatio) < 0.15) {
            return CandlestickPattern(
                type = PatternType.SPINNING_TOP,
                signal = PatternSignal.NEUTRAL,
                importance = PatternImportance.LOW,
                description = "Spinning Top - Market indecision",
                candleIndex = index
            )
        }

        return null
    }

    /**
     * Detect two candle patterns (Engulfing, etc.)
     */
    private fun detectTwoCandlePattern(
        current: OHLCData,
        previous: OHLCData,
        trend: TrendType,
        index: Int
    ): CandlestickPattern? {
        val prevBodySize = abs(previous.close - previous.open)
        val currBodySize = abs(current.close - current.open)
        val prevIsGreen = previous.close > previous.open
        val currIsGreen = current.close > current.open

        // BULLISH ENGULFING - Green candle completely engulfs previous red candle
        if (trend == TrendType.DOWN && !prevIsGreen && currIsGreen) {
            if (current.open <= previous.close && current.close >= previous.open) {
                if (currBodySize > prevBodySize * 1.1) {
                    return CandlestickPattern(
                        type = PatternType.BULLISH_ENGULFING,
                        signal = PatternSignal.BULLISH,
                        importance = PatternImportance.HIGH,
                        description = "Bullish Engulfing - Strong reversal signal, buyers taking control",
                        candleIndex = index
                    )
                }
            }
        }

        // BEARISH ENGULFING - Red candle completely engulfs previous green candle
        if (trend == TrendType.UP && prevIsGreen && !currIsGreen) {
            if (current.open >= previous.close && current.close <= previous.open) {
                if (currBodySize > prevBodySize * 1.1) {
                    return CandlestickPattern(
                        type = PatternType.BEARISH_ENGULFING,
                        signal = PatternSignal.BEARISH,
                        importance = PatternImportance.HIGH,
                        description = "Bearish Engulfing - Strong reversal signal, sellers taking control",
                        candleIndex = index
                    )
                }
            }
        }

        // PIERCING LINE - Green candle opens below prev low, closes above prev midpoint
        if (trend == TrendType.DOWN && !prevIsGreen && currIsGreen) {
            val prevMid = (previous.open + previous.close) / 2
            if (current.open < previous.low && current.close > prevMid && current.close < previous.open) {
                return CandlestickPattern(
                    type = PatternType.PIERCING_LINE,
                    signal = PatternSignal.BULLISH,
                    importance = PatternImportance.MEDIUM,
                    description = "Piercing Line - Bullish reversal, buyers pushing back",
                    candleIndex = index
                )
            }
        }

        // DARK CLOUD COVER - Red candle opens above prev high, closes below prev midpoint
        if (trend == TrendType.UP && prevIsGreen && !currIsGreen) {
            val prevMid = (previous.open + previous.close) / 2
            if (current.open > previous.high && current.close < prevMid && current.close > previous.open) {
                return CandlestickPattern(
                    type = PatternType.DARK_CLOUD_COVER,
                    signal = PatternSignal.BEARISH,
                    importance = PatternImportance.MEDIUM,
                    description = "Dark Cloud Cover - Bearish reversal, sellers pushing back",
                    candleIndex = index
                )
            }
        }

        // TWEEZER BOTTOM - Two candles with same low in downtrend
        if (trend == TrendType.DOWN) {
            val lowDiff = abs(previous.low - current.low) / previous.low
            if (lowDiff < 0.002 && !prevIsGreen && currIsGreen) {
                return CandlestickPattern(
                    type = PatternType.TWEEZER_BOTTOM,
                    signal = PatternSignal.BULLISH,
                    importance = PatternImportance.MEDIUM,
                    description = "Tweezer Bottom - Support found, potential reversal",
                    candleIndex = index
                )
            }
        }

        // TWEEZER TOP - Two candles with same high in uptrend
        if (trend == TrendType.UP) {
            val highDiff = abs(previous.high - current.high) / previous.high
            if (highDiff < 0.002 && prevIsGreen && !currIsGreen) {
                return CandlestickPattern(
                    type = PatternType.TWEEZER_TOP,
                    signal = PatternSignal.BEARISH,
                    importance = PatternImportance.MEDIUM,
                    description = "Tweezer Top - Resistance found, potential reversal",
                    candleIndex = index
                )
            }
        }

        // HARAMI (Bullish) - Small green candle inside previous large red candle
        if (trend == TrendType.DOWN && !prevIsGreen && currIsGreen) {
            if (current.open > previous.close && current.close < previous.open) {
                if (currBodySize < prevBodySize * 0.5) {
                    return CandlestickPattern(
                        type = PatternType.BULLISH_HARAMI,
                        signal = PatternSignal.BULLISH,
                        importance = PatternImportance.MEDIUM,
                        description = "Bullish Harami - Selling pressure weakening",
                        candleIndex = index
                    )
                }
            }
        }

        // HARAMI (Bearish) - Small red candle inside previous large green candle
        if (trend == TrendType.UP && prevIsGreen && !currIsGreen) {
            if (current.open < previous.close && current.close > previous.open) {
                if (currBodySize < prevBodySize * 0.5) {
                    return CandlestickPattern(
                        type = PatternType.BEARISH_HARAMI,
                        signal = PatternSignal.BEARISH,
                        importance = PatternImportance.MEDIUM,
                        description = "Bearish Harami - Buying pressure weakening",
                        candleIndex = index
                    )
                }
            }
        }

        return null
    }

    /**
     * Detect three candle patterns (Morning Star, Three White Soldiers, etc.)
     */
    private fun detectThreeCandlePattern(
        candles: List<OHLCData>,
        trend: TrendType,
        index: Int
    ): CandlestickPattern? {
        if (candles.size != 3) return null

        val first = candles[0]
        val second = candles[1]
        val third = candles[2]

        val firstIsGreen = first.close > first.open
        val secondIsGreen = second.close > second.open
        val thirdIsGreen = third.close > third.open

        val firstBody = abs(first.close - first.open)
        val secondBody = abs(second.close - second.open)
        val thirdBody = abs(third.close - third.open)

        // MORNING STAR - Bullish reversal (Red, Small/Doji, Green)
        if (trend == TrendType.DOWN) {
            val secondIsDoji = secondBody < firstBody * 0.3
            if (!firstIsGreen && secondIsDoji && thirdIsGreen) {
                if (second.high < first.close && third.close > (first.open + first.close) / 2) {
                    return CandlestickPattern(
                        type = PatternType.MORNING_STAR,
                        signal = PatternSignal.BULLISH,
                        importance = PatternImportance.HIGH,
                        description = "Morning Star - Strong bullish reversal pattern",
                        candleIndex = index
                    )
                }
            }
        }

        // EVENING STAR - Bearish reversal (Green, Small/Doji, Red)
        if (trend == TrendType.UP) {
            val secondIsDoji = secondBody < firstBody * 0.3
            if (firstIsGreen && secondIsDoji && !thirdIsGreen) {
                if (second.low > first.close && third.close < (first.open + first.close) / 2) {
                    return CandlestickPattern(
                        type = PatternType.EVENING_STAR,
                        signal = PatternSignal.BEARISH,
                        importance = PatternImportance.HIGH,
                        description = "Evening Star - Strong bearish reversal pattern",
                        candleIndex = index
                    )
                }
            }
        }

        // THREE WHITE SOLDIERS - Three consecutive green candles with higher closes
        if (firstIsGreen && secondIsGreen && thirdIsGreen) {
            if (second.close > first.close && third.close > second.close) {
                if (secondBody > firstBody * 0.7 && thirdBody > secondBody * 0.7) {
                    return CandlestickPattern(
                        type = PatternType.THREE_WHITE_SOLDIERS,
                        signal = PatternSignal.BULLISH,
                        importance = PatternImportance.HIGH,
                        description = "Three White Soldiers - Strong bullish continuation",
                        candleIndex = index
                    )
                }
            }
        }

        // THREE BLACK CROWS - Three consecutive red candles with lower closes
        if (!firstIsGreen && !secondIsGreen && !thirdIsGreen) {
            if (second.close < first.close && third.close < second.close) {
                if (secondBody > firstBody * 0.7 && thirdBody > secondBody * 0.7) {
                    return CandlestickPattern(
                        type = PatternType.THREE_BLACK_CROWS,
                        signal = PatternSignal.BEARISH,
                        importance = PatternImportance.HIGH,
                        description = "Three Black Crows - Strong bearish continuation",
                        candleIndex = index
                    )
                }
            }
        }

        // THREE INSIDE UP - Bullish Harami followed by confirmation
        if (trend == TrendType.DOWN && !firstIsGreen && secondIsGreen && thirdIsGreen) {
            // Second candle inside first
            if (second.open > first.close && second.close < first.open) {
                // Third candle closes above first's open
                if (third.close > first.open) {
                    return CandlestickPattern(
                        type = PatternType.THREE_INSIDE_UP,
                        signal = PatternSignal.BULLISH,
                        importance = PatternImportance.HIGH,
                        description = "Three Inside Up - Confirmed bullish reversal",
                        candleIndex = index
                    )
                }
            }
        }

        // THREE INSIDE DOWN - Bearish Harami followed by confirmation
        if (trend == TrendType.UP && firstIsGreen && !secondIsGreen && !thirdIsGreen) {
            // Second candle inside first
            if (second.open < first.close && second.close > first.open) {
                // Third candle closes below first's open
                if (third.close < first.open) {
                    return CandlestickPattern(
                        type = PatternType.THREE_INSIDE_DOWN,
                        signal = PatternSignal.BEARISH,
                        importance = PatternImportance.HIGH,
                        description = "Three Inside Down - Confirmed bearish reversal",
                        candleIndex = index
                    )
                }
            }
        }

        return null
    }

    /**
     * Detect short-term trend for pattern context
     */
    private fun detectTrend(candles: List<OHLCData>): TrendType {
        if (candles.size < 3) return TrendType.SIDEWAYS

        val closes = candles.map { it.close }
        var upCount = 0
        var downCount = 0

        for (i in 1 until closes.size) {
            if (closes[i] > closes[i - 1]) {
                upCount++
            } else if (closes[i] < closes[i - 1]) {
                downCount++
            }
        }

        val total = upCount + downCount
        if (total == 0) return TrendType.SIDEWAYS

        val upRatio = upCount.toDouble() / total

        return when {
            upRatio > 0.6 -> TrendType.UP
            upRatio < 0.4 -> TrendType.DOWN
            else -> TrendType.SIDEWAYS
        }
    }
}

// ============================================
// Data Classes
// ============================================

/**
 * OHLC (Open, High, Low, Close) candle data
 */
data class OHLCData(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
) {
    val isGreen: Boolean get() = close > open
    val isRed: Boolean get() = close < open
    val bodySize: Double get() = abs(close - open)
    val range: Double get() = high - low
    val upperWick: Double get() = high - maxOf(open, close)
    val lowerWick: Double get() = minOf(open, close) - low
}

/**
 * Complete technical analysis result
 */
data class TechnicalAnalysisResult(
    val trend: TrendDirection,
    val signal: TechnicalSignal,
    val rsi: RSIResult,
    val macd: MACDResult,
    val sma20: Double?,
    val sma50: Double?,
    val sma200: Double?,
    val ema9: Double?,
    val ema21: Double?,
    val atr: Double,
    val bollingerBands: BollingerBands,
    val vwap: Double,
    val supportLevels: List<SupportResistanceLevel>,
    val resistanceLevels: List<SupportResistanceLevel>,
    val volumeAnalysis: VolumeAnalysis,
    val insights: List<TechnicalInsight>,
    val candlestickPatterns: List<CandlestickPattern>,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun empty(): TechnicalAnalysisResult = TechnicalAnalysisResult(
            trend = TrendDirection.SIDEWAYS,
            signal = TechnicalSignal(direction = SignalDirection.NEUTRAL, confidence = 0.0),
            rsi = RSIResult(value = 50.0, condition = RSICondition.NEUTRAL),
            macd = MACDResult(macdLine = 0.0, signalLine = 0.0, histogram = 0.0, crossover = MACDCrossover.NONE),
            sma20 = null,
            sma50 = null,
            sma200 = null,
            ema9 = null,
            ema21 = null,
            atr = 0.0,
            bollingerBands = BollingerBands(upper = 0.0, middle = 0.0, lower = 0.0, bandwidth = 0.0, position = BollingerPosition.MIDDLE),
            vwap = 0.0,
            supportLevels = emptyList(),
            resistanceLevels = emptyList(),
            volumeAnalysis = VolumeAnalysis(trend = VolumeTrend.NORMAL, relativeVolume = 1.0, averageVolume = 0.0),
            insights = emptyList(),
            candlestickPatterns = emptyList()
        )
    }
}

/**
 * Trend direction
 */
enum class TrendDirection(val displayName: String) {
    STRONG_UPTREND("Strong Uptrend"),
    UPTREND("Uptrend"),
    SIDEWAYS("Sideways"),
    DOWNTREND("Downtrend"),
    STRONG_DOWNTREND("Strong Downtrend");

    val isBullish: Boolean get() = this == STRONG_UPTREND || this == UPTREND
    val isBearish: Boolean get() = this == STRONG_DOWNTREND || this == DOWNTREND
}

/**
 * Technical signal
 */
data class TechnicalSignal(
    val direction: SignalDirection,
    val confidence: Double
)

/**
 * Signal direction
 */
enum class SignalDirection(val displayName: String, val color: Long) {
    STRONG_BUY("Strong Buy", 0xFF22C55E),
    BUY("Buy", 0xFF4ADE80),
    NEUTRAL("Neutral", 0xFFF59E0B),
    SELL("Sell", 0xFFF87171),
    STRONG_SELL("Strong Sell", 0xFFEF4444)
}

/**
 * RSI result with condition
 */
data class RSIResult(
    val value: Double,
    val condition: RSICondition
)

/**
 * RSI condition
 */
enum class RSICondition(val displayName: String, val color: Long) {
    OVERBOUGHT("Overbought", 0xFFEF4444),
    SLIGHTLY_OVERBOUGHT("Slightly Overbought", 0xFFF59E0B),
    NEUTRAL("Neutral", 0xFF6B7280),
    SLIGHTLY_OVERSOLD("Slightly Oversold", 0xFF3B82F6),
    OVERSOLD("Oversold", 0xFF22C55E)
}

/**
 * MACD result with crossover detection
 */
data class MACDResult(
    val macdLine: Double,
    val signalLine: Double,
    val histogram: Double,
    val crossover: MACDCrossover
) {
    val isPositive: Boolean get() = histogram > 0
    val isNegative: Boolean get() = histogram < 0
}

/**
 * MACD crossover type
 */
enum class MACDCrossover {
    BULLISH,
    BEARISH,
    NONE
}

/**
 * Bollinger Bands
 */
data class BollingerBands(
    val upper: Double,
    val middle: Double,
    val lower: Double,
    val bandwidth: Double,
    val position: BollingerPosition
) {
    val percentB: Double
        get() = if ((upper - lower) > 0) {
            (middle - lower) / (upper - lower) * 100
        } else 0.0
}

/**
 * Bollinger position
 */
enum class BollingerPosition {
    ABOVE_UPPER,
    UPPER_HALF,
    MIDDLE,
    LOWER_HALF,
    BELOW_LOWER
}

/**
 * Volume analysis result
 */
data class VolumeAnalysis(
    val trend: VolumeTrend,
    val relativeVolume: Double,
    val averageVolume: Double
)

/**
 * Volume trend
 */
enum class VolumeTrend(val displayName: String) {
    VERY_HIGH("Very High"),
    HIGH("High"),
    NORMAL("Normal"),
    LOW("Low")
}

/**
 * Support/Resistance level
 */
data class SupportResistanceLevel(
    val price: Double,
    val strength: LevelStrength,
    val touches: Int,
    val type: LevelType
) {
    val displayPrice: String get() = String.format("%.2f", price)
}

/**
 * Level strength
 */
enum class LevelStrength(val displayName: String) {
    STRONG("Strong"),
    MODERATE("Moderate"),
    WEAK("Weak")
}

/**
 * Level type
 */
enum class LevelType {
    SUPPORT,
    RESISTANCE
}

/**
 * Technical insight
 */
data class TechnicalInsight(
    val type: InsightType,
    val title: String,
    val description: String,
    val signal: InsightSignal,
    val importance: InsightImportance
)

/**
 * Insight type
 */
enum class InsightType(val displayName: String) {
    TREND("Trend"),
    RSI("RSI"),
    MACD("MACD"),
    BOLLINGER("Bollinger Bands"),
    VOLUME("Volume"),
    MOVING_AVERAGE("Moving Average"),
    SUPPORT("Support"),
    RESISTANCE("Resistance")
}

/**
 * Insight signal
 */
enum class InsightSignal(val displayName: String, val color: Long) {
    BULLISH("Bullish", 0xFF22C55E),
    BEARISH("Bearish", 0xFFEF4444),
    NEUTRAL("Neutral", 0xFFF59E0B)
}

/**
 * Insight importance
 */
enum class InsightImportance(val displayName: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}

/**
 * Candlestick pattern
 */
data class CandlestickPattern(
    val type: PatternType,
    val signal: PatternSignal,
    val importance: PatternImportance,
    val description: String,
    val candleIndex: Int
) {
    val displayName: String get() = type.displayName
    val isBullish: Boolean get() = signal == PatternSignal.BULLISH
    val isBearish: Boolean get() = signal == PatternSignal.BEARISH
}

/**
 * Pattern type - covers single, double, and triple candlestick patterns
 */
enum class PatternType(val displayName: String) {
    // Single Candle
    DOJI("Doji"),
    DRAGONFLY_DOJI("Dragonfly Doji"),
    GRAVESTONE_DOJI("Gravestone Doji"),
    HAMMER("Hammer"),
    INVERTED_HAMMER("Inverted Hammer"),
    SHOOTING_STAR("Shooting Star"),
    HANGING_MAN("Hanging Man"),
    BULLISH_MARUBOZU("Bullish Marubozu"),
    BEARISH_MARUBOZU("Bearish Marubozu"),
    SPINNING_TOP("Spinning Top"),

    // Two Candle
    BULLISH_ENGULFING("Bullish Engulfing"),
    BEARISH_ENGULFING("Bearish Engulfing"),
    PIERCING_LINE("Piercing Line"),
    DARK_CLOUD_COVER("Dark Cloud Cover"),
    TWEEZER_TOP("Tweezer Top"),
    TWEEZER_BOTTOM("Tweezer Bottom"),
    BULLISH_HARAMI("Bullish Harami"),
    BEARISH_HARAMI("Bearish Harami"),

    // Three Candle
    MORNING_STAR("Morning Star"),
    EVENING_STAR("Evening Star"),
    THREE_WHITE_SOLDIERS("Three White Soldiers"),
    THREE_BLACK_CROWS("Three Black Crows"),
    THREE_INSIDE_UP("Three Inside Up"),
    THREE_INSIDE_DOWN("Three Inside Down")
}

/**
 * Pattern signal
 */
enum class PatternSignal(val displayName: String, val color: Long) {
    BULLISH("Bullish", 0xFF22C55E),
    BEARISH("Bearish", 0xFFEF4444),
    NEUTRAL("Neutral", 0xFFF59E0B)
}

/**
 * Pattern importance
 */
enum class PatternImportance(val displayName: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}

/**
 * Internal trend type for pattern detection
 */
private enum class TrendType {
    UP,
    DOWN,
    SIDEWAYS
}
