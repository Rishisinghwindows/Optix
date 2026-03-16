package com.optix.app.core.calculation

import com.optix.app.domain.model.ConfidenceLevel
import com.optix.app.domain.model.GreeksRecommendation
import com.optix.app.domain.model.GreeksRecommendationType
import com.optix.app.domain.model.HeatmapCell
import com.optix.app.domain.model.HeatmapZoneType
import com.optix.app.domain.model.IVSkewAnalysis
import com.optix.app.domain.model.MarketRegime
import com.optix.app.domain.model.OptionChain
import com.optix.app.domain.model.OptionChainRow
import com.optix.app.domain.model.OptionData
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.OptionsHeatmapData
import com.optix.app.domain.model.RecommendationSeverity
import com.optix.app.domain.model.SkewType
import com.optix.app.domain.model.SmartMoneySignal
import com.optix.app.domain.model.SmartMoneySignalType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Advanced analysis engine for generating Greeks recommendations,
 * IV skew analysis, smart money detection, and heatmap data.
 */
@Singleton
class AdvancedAnalysisEngine @Inject constructor() {

    companion object {
        // Greeks thresholds
        private const val HIGH_GAMMA_THRESHOLD = 0.05
        private const val HIGH_DELTA_THRESHOLD = 0.8
        private const val THETA_DECAY_DAYS_THRESHOLD = 7
        private const val HIGH_THETA_DECAY_PERCENT = 0.05 // 5% daily decay
        private const val HIGH_VEGA_THRESHOLD = 15.0
        private const val VEGA_CRUSH_IV_PERCENTILE = 80.0

        // Smart money thresholds
        private const val LARGE_OI_CHANGE_THRESHOLD = 0.2 // 20% change
        private const val VOLUME_SPIKE_MULTIPLIER = 3.0
        private const val BLOCK_TRADE_VOLUME_THRESHOLD = 10000L

        // Heatmap thresholds
        private const val STRONG_SUPPORT_OI_PERCENTILE = 0.9
        private const val STRONG_RESISTANCE_OI_PERCENTILE = 0.9
    }

    /**
     * Analyze Greeks for a single option and generate recommendations
     */
    fun analyzeGreeks(
        optionData: OptionData,
        spotPrice: Double,
        daysToExpiry: Int
    ): List<GreeksRecommendation> {
        val recommendations = mutableListOf<GreeksRecommendation>()

        // High Gamma Opportunity
        if (optionData.gamma > HIGH_GAMMA_THRESHOLD && optionData.isATM) {
            recommendations.add(
                GreeksRecommendation(
                    type = GreeksRecommendationType.HIGH_GAMMA_OPPORTUNITY,
                    severity = RecommendationSeverity.INFO,
                    title = "High Gamma at ATM",
                    description = "Option has elevated gamma (${String.format("%.4f", optionData.gamma)}), " +
                            "offering accelerated delta changes near spot price.",
                    affectedGreek = "Gamma",
                    currentValue = optionData.gamma,
                    thresholdValue = HIGH_GAMMA_THRESHOLD,
                    actionSuggested = "Consider for directional plays or gamma scalping strategies"
                )
            )
        }

        // Gamma Risk Alert for short positions
        if (optionData.gamma > HIGH_GAMMA_THRESHOLD * 1.5 && daysToExpiry <= 3) {
            recommendations.add(
                GreeksRecommendation(
                    type = GreeksRecommendationType.GAMMA_RISK_ALERT,
                    severity = RecommendationSeverity.CRITICAL,
                    title = "Elevated Gamma Risk",
                    description = "High gamma (${String.format("%.4f", optionData.gamma)}) near expiry. " +
                            "Short positions face significant pin risk.",
                    affectedGreek = "Gamma",
                    currentValue = optionData.gamma,
                    thresholdValue = HIGH_GAMMA_THRESHOLD * 1.5,
                    actionSuggested = "Consider closing or hedging short gamma positions"
                )
            )
        }

        // Theta Decay Warning / 0DTE Alert
        if (daysToExpiry <= THETA_DECAY_DAYS_THRESHOLD) {
            val dailyDecayPercent = if (optionData.lastPrice > 0) {
                abs(optionData.theta) / optionData.lastPrice
            } else 0.0

            if (daysToExpiry == 0) {
                // 0DTE specific recommendation for intraday trading
                recommendations.add(
                    GreeksRecommendation(
                        type = GreeksRecommendationType.THETA_DECAY_WARNING,
                        severity = RecommendationSeverity.INFO,
                        title = "0DTE - Intraday Trading",
                        description = "Expiring today. Suitable for intraday scalping with quick exits. " +
                                "Gamma is elevated, providing rapid delta changes.",
                        affectedGreek = "Theta",
                        currentValue = optionData.theta,
                        thresholdValue = 0.0,
                        actionSuggested = "Use tight stop-losses; exit before 3:00 PM to avoid settlement risk"
                    )
                )
            } else if (dailyDecayPercent >= HIGH_THETA_DECAY_PERCENT) {
                recommendations.add(
                    GreeksRecommendation(
                        type = GreeksRecommendationType.THETA_DECAY_WARNING,
                        severity = RecommendationSeverity.WARNING,
                        title = "Accelerated Time Decay",
                        description = "Option losing ${String.format("%.1f", dailyDecayPercent * 100)}% " +
                                "daily to theta with $daysToExpiry days to expiry.",
                        affectedGreek = "Theta",
                        currentValue = optionData.theta,
                        thresholdValue = optionData.lastPrice * HIGH_THETA_DECAY_PERCENT,
                        actionSuggested = "Long holders should consider exiting; short sellers benefit"
                    )
                )
            }
        }

        // High Delta Exposure
        if (abs(optionData.delta) > HIGH_DELTA_THRESHOLD) {
            recommendations.add(
                GreeksRecommendation(
                    type = GreeksRecommendationType.HIGH_DELTA_EXPOSURE,
                    severity = RecommendationSeverity.INFO,
                    title = "Deep ${if (optionData.isITM) "ITM" else "OTM"} Option",
                    description = "Delta of ${String.format("%.2f", optionData.delta)} indicates " +
                            "high directional exposure.",
                    affectedGreek = "Delta",
                    currentValue = optionData.delta,
                    thresholdValue = HIGH_DELTA_THRESHOLD,
                    actionSuggested = if (optionData.isITM)
                        "Consider exercise or roll if near expiry"
                    else
                        "Low probability of profit; consider alternative strikes"
                )
            )
        }

        // Vega Expansion Alert
        if (optionData.vega > HIGH_VEGA_THRESHOLD && optionData.impliedVolatility < 20) {
            recommendations.add(
                GreeksRecommendation(
                    type = GreeksRecommendationType.VEGA_EXPANSION_ALERT,
                    severity = RecommendationSeverity.INFO,
                    title = "High Vega, Low IV Environment",
                    description = "Vega of ${String.format("%.2f", optionData.vega)} with IV at " +
                            "${String.format("%.1f", optionData.impliedVolatility)}%. Potential for IV expansion.",
                    affectedGreek = "Vega",
                    currentValue = optionData.vega,
                    thresholdValue = HIGH_VEGA_THRESHOLD,
                    actionSuggested = "Long vega positions may benefit from volatility expansion"
                )
            )
        }

        return recommendations
    }

    /**
     * Analyze IV skew across the option chain
     */
    fun analyzeIVSkew(optionChain: OptionChain): IVSkewAnalysis {
        val atmStrike = optionChain.atmStrike
        val rows = optionChain.rows

        // Find ATM row
        val atmRow = rows.minByOrNull { abs(it.strikePrice - atmStrike) }
        val atmIV = atmRow?.callData?.impliedVolatility
            ?: atmRow?.putData?.impliedVolatility
            ?: 20.0

        // Calculate OTM call skew (average IV of calls 2-4 strikes OTM)
        val otmCallRows = rows.filter { it.strikePrice > atmStrike }
            .take(4)
            .drop(1)
        val avgOtmCallIV = otmCallRows
            .mapNotNull { it.callData?.impliedVolatility }
            .takeIf { it.isNotEmpty() }
            ?.average() ?: atmIV
        val callSkew = avgOtmCallIV - atmIV

        // Calculate OTM put skew (average IV of puts 2-4 strikes OTM)
        val otmPutRows = rows.filter { it.strikePrice < atmStrike }
            .takeLast(4)
            .dropLast(1)
        val avgOtmPutIV = otmPutRows
            .mapNotNull { it.putData?.impliedVolatility }
            .takeIf { it.isNotEmpty() }
            ?.average() ?: atmIV
        val putSkew = avgOtmPutIV - atmIV

        // Determine skew type
        val skewType = when {
            putSkew > 3 && callSkew < 2 -> SkewType.PUT_SKEW
            callSkew > 3 && putSkew < 2 -> SkewType.CALL_SKEW
            putSkew > 3 && callSkew > 3 -> SkewType.SMILE
            else -> SkewType.FLAT
        }

        val interpretation = when (skewType) {
            SkewType.PUT_SKEW -> "OTM puts are trading at a premium, indicating fear of downside. " +
                    "Institutions may be hedging or expecting a pullback."
            SkewType.CALL_SKEW -> "OTM calls are trading at a premium, indicating bullish speculation " +
                    "or expectation of upside move."
            SkewType.SMILE -> "Both OTM puts and calls are elevated, suggesting expected large move " +
                    "in either direction (event-driven)."
            SkewType.FLAT -> "IV is relatively uniform across strikes, suggesting balanced expectations."
        }

        val tradingImplication = when (skewType) {
            SkewType.PUT_SKEW -> "Consider put credit spreads or ratio spreads to capture rich put premium. " +
                    "Avoid naked long puts."
            SkewType.CALL_SKEW -> "Consider call credit spreads. Calls are expensive for long positions."
            SkewType.SMILE -> "Iron condors or straddles may be attractive. High IV across strikes."
            SkewType.FLAT -> "No significant edge from skew. Focus on directional or theta strategies."
        }

        return IVSkewAnalysis(
            symbol = optionChain.index.symbol,
            expiry = optionChain.expiry,
            atmIV = atmIV,
            callSkew = callSkew,
            putSkew = putSkew,
            skewType = skewType,
            interpretation = interpretation,
            tradingImplication = tradingImplication
        )
    }

    /**
     * Detect smart money activity by comparing current and previous option chain data
     */
    fun detectSmartMoney(
        currentChain: OptionChain,
        previousChain: OptionChain?
    ): List<SmartMoneySignal> {
        val signals = mutableListOf<SmartMoneySignal>()

        if (previousChain == null) {
            // No previous data, use current data's OI change fields
            currentChain.rows.forEach { row ->
                signals.addAll(detectSignalsFromRow(row))
            }
        } else {
            // Compare current and previous chains
            currentChain.rows.forEach { currentRow ->
                val previousRow = previousChain.rows.find { it.strikePrice == currentRow.strikePrice }
                if (previousRow != null) {
                    signals.addAll(detectSignalsFromComparison(currentRow, previousRow))
                }
            }
        }

        return signals.sortedByDescending { it.confidence.ordinal * 10 + it.volumeSpike }
            .take(10) // Return top 10 signals
    }

    private fun detectSignalsFromRow(row: OptionChainRow): List<SmartMoneySignal> {
        val signals = mutableListOf<SmartMoneySignal>()

        // Check call data
        row.callData?.let { call ->
            if (call.oiChange > 0 && call.oiChangePercent > LARGE_OI_CHANGE_THRESHOLD * 100) {
                signals.add(
                    SmartMoneySignal(
                        id = UUID.randomUUID().toString(),
                        strikePrice = row.strikePrice,
                        optionType = OptionType.CALL,
                        signalType = SmartMoneySignalType.LARGE_OI_BUILD,
                        oiChange = call.oiChange,
                        volumeSpike = if (call.volume > 0 && call.openInterest > 0)
                            call.volume.toDouble() / (call.openInterest / 10).coerceAtLeast(1) else 1.0,
                        interpretation = "Large call OI build-up at ${row.strikePrice}. " +
                                "Suggests bullish positioning or resistance.",
                        confidence = when {
                            call.oiChangePercent > 50 -> ConfidenceLevel.HIGH
                            call.oiChangePercent > 30 -> ConfidenceLevel.MEDIUM
                            else -> ConfidenceLevel.LOW
                        }
                    )
                )
            } else if (call.oiChange < 0 && abs(call.oiChangePercent) > LARGE_OI_CHANGE_THRESHOLD * 100) {
                signals.add(
                    SmartMoneySignal(
                        id = UUID.randomUUID().toString(),
                        strikePrice = row.strikePrice,
                        optionType = OptionType.CALL,
                        signalType = SmartMoneySignalType.LARGE_OI_UNWIND,
                        oiChange = call.oiChange,
                        volumeSpike = 1.0,
                        interpretation = "Large call OI unwinding at ${row.strikePrice}. " +
                                "Positions being closed, possible profit booking.",
                        confidence = when {
                            abs(call.oiChangePercent) > 50 -> ConfidenceLevel.HIGH
                            abs(call.oiChangePercent) > 30 -> ConfidenceLevel.MEDIUM
                            else -> ConfidenceLevel.LOW
                        }
                    )
                )
            }

            if (call.volume > BLOCK_TRADE_VOLUME_THRESHOLD) {
                val avgVolume = call.openInterest / 5 // Rough estimate
                val spike = if (avgVolume > 0) call.volume.toDouble() / avgVolume else 1.0
                if (spike > VOLUME_SPIKE_MULTIPLIER) {
                    signals.add(
                        SmartMoneySignal(
                            id = UUID.randomUUID().toString(),
                            strikePrice = row.strikePrice,
                            optionType = OptionType.CALL,
                            signalType = SmartMoneySignalType.VOLUME_SPIKE,
                            oiChange = call.oiChange,
                            volumeSpike = spike,
                            interpretation = "Volume spike ${String.format("%.1f", spike)}x at " +
                                    "${row.strikePrice} CE. Unusual activity detected.",
                            confidence = when {
                                spike > 5 -> ConfidenceLevel.HIGH
                                spike > 3 -> ConfidenceLevel.MEDIUM
                                else -> ConfidenceLevel.LOW
                            }
                        )
                    )
                }
            }
        }

        // Check put data
        row.putData?.let { put ->
            if (put.oiChange > 0 && put.oiChangePercent > LARGE_OI_CHANGE_THRESHOLD * 100) {
                signals.add(
                    SmartMoneySignal(
                        id = UUID.randomUUID().toString(),
                        strikePrice = row.strikePrice,
                        optionType = OptionType.PUT,
                        signalType = SmartMoneySignalType.PUT_WRITING,
                        oiChange = put.oiChange,
                        volumeSpike = if (put.volume > 0 && put.openInterest > 0)
                            put.volume.toDouble() / (put.openInterest / 10).coerceAtLeast(1) else 1.0,
                        interpretation = "Large put OI build-up at ${row.strikePrice}. " +
                                "May indicate support level or hedging activity.",
                        confidence = when {
                            put.oiChangePercent > 50 -> ConfidenceLevel.HIGH
                            put.oiChangePercent > 30 -> ConfidenceLevel.MEDIUM
                            else -> ConfidenceLevel.LOW
                        }
                    )
                )
            }

            if (put.volume > BLOCK_TRADE_VOLUME_THRESHOLD) {
                val avgVolume = put.openInterest / 5
                val spike = if (avgVolume > 0) put.volume.toDouble() / avgVolume else 1.0
                if (spike > VOLUME_SPIKE_MULTIPLIER) {
                    signals.add(
                        SmartMoneySignal(
                            id = UUID.randomUUID().toString(),
                            strikePrice = row.strikePrice,
                            optionType = OptionType.PUT,
                            signalType = SmartMoneySignalType.VOLUME_SPIKE,
                            oiChange = put.oiChange,
                            volumeSpike = spike,
                            interpretation = "Volume spike ${String.format("%.1f", spike)}x at " +
                                    "${row.strikePrice} PE. Unusual activity detected.",
                            confidence = when {
                                spike > 5 -> ConfidenceLevel.HIGH
                                spike > 3 -> ConfidenceLevel.MEDIUM
                                else -> ConfidenceLevel.LOW
                            }
                        )
                    )
                }
            }
        }

        return signals
    }

    private fun detectSignalsFromComparison(
        current: OptionChainRow,
        previous: OptionChainRow
    ): List<SmartMoneySignal> {
        val signals = mutableListOf<SmartMoneySignal>()

        // Compare call OI
        if (current.callData != null && previous.callData != null) {
            val oiChange = current.callData.openInterest - previous.callData.openInterest
            val oiChangePercent = if (previous.callData.openInterest > 0) {
                (oiChange.toDouble() / previous.callData.openInterest) * 100
            } else 0.0

            if (oiChangePercent > LARGE_OI_CHANGE_THRESHOLD * 100) {
                signals.add(
                    SmartMoneySignal(
                        id = UUID.randomUUID().toString(),
                        strikePrice = current.strikePrice,
                        optionType = OptionType.CALL,
                        signalType = SmartMoneySignalType.LARGE_OI_BUILD,
                        oiChange = oiChange,
                        volumeSpike = current.callData.volume.toDouble() /
                            previous.callData.volume.coerceAtLeast(1),
                        interpretation = "Call OI increased by ${String.format("%.1f", oiChangePercent)}% " +
                                "at strike ${current.strikePrice}.",
                        confidence = when {
                            oiChangePercent > 50 -> ConfidenceLevel.HIGH
                            oiChangePercent > 30 -> ConfidenceLevel.MEDIUM
                            else -> ConfidenceLevel.LOW
                        }
                    )
                )
            }
        }

        // Compare put OI
        if (current.putData != null && previous.putData != null) {
            val oiChange = current.putData.openInterest - previous.putData.openInterest
            val oiChangePercent = if (previous.putData.openInterest > 0) {
                (oiChange.toDouble() / previous.putData.openInterest) * 100
            } else 0.0

            if (oiChangePercent > LARGE_OI_CHANGE_THRESHOLD * 100) {
                signals.add(
                    SmartMoneySignal(
                        id = UUID.randomUUID().toString(),
                        strikePrice = current.strikePrice,
                        optionType = OptionType.PUT,
                        signalType = SmartMoneySignalType.LARGE_OI_BUILD,
                        oiChange = oiChange,
                        volumeSpike = current.putData.volume.toDouble() /
                            previous.putData.volume.coerceAtLeast(1),
                        interpretation = "Put OI increased by ${String.format("%.1f", oiChangePercent)}% " +
                                "at strike ${current.strikePrice}.",
                        confidence = when {
                            oiChangePercent > 50 -> ConfidenceLevel.HIGH
                            oiChangePercent > 30 -> ConfidenceLevel.MEDIUM
                            else -> ConfidenceLevel.LOW
                        }
                    )
                )
            }
        }

        return signals
    }

    /**
     * Generate heatmap data from option chain
     */
    fun generateHeatmap(optionChain: OptionChain): OptionsHeatmapData {
        val cells = mutableListOf<HeatmapCell>()

        // Find max OI for intensity calculation
        val maxCallOI = optionChain.rows.mapNotNull { it.callData?.openInterest }.maxOrNull() ?: 1L
        val maxPutOI = optionChain.rows.mapNotNull { it.putData?.openInterest }.maxOrNull() ?: 1L
        val maxTotalOI = maxOf(maxCallOI, maxPutOI)

        // Calculate max pain
        val maxPainStrike = calculateMaxPain(optionChain)

        optionChain.rows.forEach { row ->
            val callOI = row.callData?.openInterest ?: 0L
            val putOI = row.putData?.openInterest ?: 0L
            val totalOI = callOI + putOI

            val intensity = if (maxTotalOI > 0) {
                totalOI.toDouble() / maxTotalOI
            } else 0.0

            val zoneType = determineZoneType(
                strikePrice = row.strikePrice,
                spotPrice = optionChain.spotPrice,
                callOI = callOI,
                putOI = putOI,
                maxPainStrike = maxPainStrike,
                maxCallOI = maxCallOI,
                maxPutOI = maxPutOI
            )

            cells.add(
                HeatmapCell(
                    strikePrice = row.strikePrice,
                    callOI = callOI,
                    putOI = putOI,
                    callOIChange = row.callData?.oiChange ?: 0L,
                    putOIChange = row.putData?.oiChange ?: 0L,
                    intensity = intensity,
                    zoneType = zoneType
                )
            )
        }

        // Find strong support levels (high put OI below spot)
        val strongSupports = cells
            .filter { it.strikePrice < optionChain.spotPrice && it.putOI > maxPutOI * STRONG_SUPPORT_OI_PERCENTILE }
            .map { it.strikePrice }

        // Find strong resistance levels (high call OI above spot)
        val strongResistances = cells
            .filter { it.strikePrice > optionChain.spotPrice && it.callOI > maxCallOI * STRONG_RESISTANCE_OI_PERCENTILE }
            .map { it.strikePrice }

        return OptionsHeatmapData(
            symbol = optionChain.index.symbol,
            spotPrice = optionChain.spotPrice,
            cells = cells,
            strongSupports = strongSupports,
            strongResistances = strongResistances,
            maxPainStrike = maxPainStrike
        )
    }

    /**
     * Calculate max pain strike
     */
    private fun calculateMaxPain(optionChain: OptionChain): Double {
        var minPain = Double.MAX_VALUE
        var maxPainStrike = optionChain.atmStrike

        optionChain.rows.forEach { row ->
            var totalPain = 0.0

            // Calculate pain for all call holders
            optionChain.rows.forEach { callRow ->
                callRow.callData?.let { call ->
                    if (row.strikePrice < callRow.strikePrice) {
                        // Call expires worthless, writers keep premium
                    } else {
                        // Calls are ITM, calculate loss for writers
                        totalPain += (row.strikePrice - callRow.strikePrice) * call.openInterest
                    }
                }
            }

            // Calculate pain for all put holders
            optionChain.rows.forEach { putRow ->
                putRow.putData?.let { put ->
                    if (row.strikePrice > putRow.strikePrice) {
                        // Put expires worthless
                    } else {
                        // Puts are ITM
                        totalPain += (putRow.strikePrice - row.strikePrice) * put.openInterest
                    }
                }
            }

            if (totalPain < minPain) {
                minPain = totalPain
                maxPainStrike = row.strikePrice
            }
        }

        return maxPainStrike
    }

    private fun determineZoneType(
        strikePrice: Double,
        spotPrice: Double,
        callOI: Long,
        putOI: Long,
        maxPainStrike: Double,
        maxCallOI: Long,
        maxPutOI: Long
    ): HeatmapZoneType {
        // Max pain zone
        if (abs(strikePrice - maxPainStrike) < spotPrice * 0.005) {
            return HeatmapZoneType.MAX_PAIN
        }

        // Below spot - potential support
        if (strikePrice < spotPrice) {
            return when {
                putOI > maxPutOI * STRONG_SUPPORT_OI_PERCENTILE -> HeatmapZoneType.STRONG_SUPPORT
                putOI > maxPutOI * 0.6 -> HeatmapZoneType.WEAK_SUPPORT
                else -> HeatmapZoneType.NEUTRAL
            }
        }

        // Above spot - potential resistance
        return when {
            callOI > maxCallOI * STRONG_RESISTANCE_OI_PERCENTILE -> HeatmapZoneType.STRONG_RESISTANCE
            callOI > maxCallOI * 0.6 -> HeatmapZoneType.WEAK_RESISTANCE
            else -> HeatmapZoneType.NEUTRAL
        }
    }

    /**
     * Determine market regime based on multiple factors
     */
    fun determineMarketRegime(
        spotPrice: Double,
        ivPercentile: Double,
        pcr: Double,
        recentPriceChange: Double, // Percentage change over recent period
        atr: Double? = null // Average True Range if available
    ): MarketRegime {
        // Volatility-based classification
        val isHighVolatility = ivPercentile > 70
        val isLowVolatility = ivPercentile < 30

        // Trend-based classification
        val isBullish = recentPriceChange > 2.0 && pcr > 0.7
        val isBearish = recentPriceChange < -2.0 && pcr < 0.5

        return when {
            isHighVolatility -> MarketRegime.HIGH_VOLATILITY
            isLowVolatility -> MarketRegime.LOW_VOLATILITY
            isBullish -> MarketRegime.TRENDING_BULLISH
            isBearish -> MarketRegime.TRENDING_BEARISH
            else -> MarketRegime.RANGE_BOUND
        }
    }
}
