package com.optix.app.core.calculation

import com.optix.app.domain.model.*
import java.util.Date
import kotlin.math.abs

/**
 * Engine for analyzing Open Interest data and generating insights.
 * Ported from iOS OIAnalysisEngine.swift
 */
object OIAnalysisEngine {

    // MARK: - Constants
    private const val SIGNIFICANT_OI_THRESHOLD = 0.7 // 70% of max OI considered significant
    private const val STRONG_ZONE_THRESHOLD = 0.85
    private const val MODERATE_ZONE_THRESHOLD = 0.6
    private const val SMART_MONEY_CHANGE_THRESHOLD = 0.20 // 20% change considered significant

    // MARK: - Main Analysis

    /**
     * Perform comprehensive OI analysis on option chain data.
     */
    fun analyzeOI(
        optionChain: OptionChain,
        spotPrice: Double
    ): OIAnalysisResult {
        val strikeData = buildStrikeData(optionChain)
        val (totalCallOI, totalPutOI) = calculateTotalOI(strikeData)
        val pcr = if (totalCallOI > 0) totalPutOI.toDouble() / totalCallOI else 0.0
        val maxPain = calculateMaxPain(strikeData, spotPrice)
        val atmStrike = findATMStrike(spotPrice, strikeData.map { it.strikePrice })

        val supportZones = identifySupportZones(strikeData, spotPrice)
        val resistanceZones = identifyResistanceZones(strikeData, spotPrice)
        val heatmapCells = generateHeatmapCells(strikeData, spotPrice, maxPain)
        val ivSurface = generateIVSurface(strikeData, spotPrice)
        val sentiment = determineMarketSentiment(pcr, strikeData, spotPrice)
        val smartMoneyActivities = detectSmartMoneyPatterns(strikeData)
        val interpretation = generateInterpretation(
            strikeData = strikeData,
            spotPrice = spotPrice,
            pcr = pcr,
            maxPain = maxPain,
            supportZones = supportZones,
            resistanceZones = resistanceZones,
            sentiment = sentiment
        )

        return OIAnalysisResult(
            symbol = optionChain.index.displayName,
            spotPrice = spotPrice,
            atmStrike = atmStrike,
            pcr = pcr,
            maxPain = maxPain,
            supportZones = supportZones,
            resistanceZones = resistanceZones,
            heatmapData = heatmapCells,
            smartMoneyActivities = smartMoneyActivities,
            ivSurface = ivSurface,
            interpretation = interpretation
        )
    }

    // MARK: - Strike Data Building

    /**
     * Data class representing OI data for a single strike
     */
    data class StrikeOIData(
        val strikePrice: Double,
        val callOI: Long,
        val putOI: Long,
        val callOIChange: Long,
        val putOIChange: Long,
        val callIV: Double,
        val putIV: Double,
        val callLTP: Double,
        val putLTP: Double
    ) {
        val totalOI: Long get() = callOI + putOI
        val pcr: Double get() = if (callOI > 0) putOI.toDouble() / callOI else 0.0
        val netOIChange: Long get() = callOIChange + putOIChange
        val avgIV: Double get() = (callIV + putIV) / 2
        val ivSkew: Double get() = putIV - callIV // Positive = puts more expensive
        val displayStrike: String get() = String.format("%.0f", strikePrice)
    }

    private fun buildStrikeData(optionChain: OptionChain): List<StrikeOIData> {
        return optionChain.rows.map { row ->
            StrikeOIData(
                strikePrice = row.strikePrice,
                callOI = row.callData?.openInterest ?: 0,
                putOI = row.putData?.openInterest ?: 0,
                callOIChange = row.callData?.oiChange ?: 0,
                putOIChange = row.putData?.oiChange ?: 0,
                callIV = row.callData?.impliedVolatility ?: 0.15,
                putIV = row.putData?.impliedVolatility ?: 0.15,
                callLTP = row.callData?.lastPrice ?: 0.0,
                putLTP = row.putData?.lastPrice ?: 0.0
            )
        }.sortedBy { it.strikePrice }
    }

    private fun calculateTotalOI(strikeData: List<StrikeOIData>): Pair<Long, Long> {
        val totalCall = strikeData.sumOf { it.callOI }
        val totalPut = strikeData.sumOf { it.putOI }
        return Pair(totalCall, totalPut)
    }

    // MARK: - Max Pain Calculation

    private fun calculateMaxPain(strikeData: List<StrikeOIData>, spotPrice: Double): Double {
        var minPain = Double.MAX_VALUE
        var maxPainStrike = spotPrice

        for (strikeRow in strikeData) {
            val strike = strikeRow.strikePrice
            var totalPain = 0.0

            // Calculate pain for all call and put writers at this strike
            for (option in strikeData) {
                // Call pain: max(0, strike - option.strikePrice) * callOI
                val callPain = maxOf(0.0, strike - option.strikePrice) * option.callOI
                totalPain += callPain

                // Put pain: max(0, option.strikePrice - strike) * putOI
                val putPain = maxOf(0.0, option.strikePrice - strike) * option.putOI
                totalPain += putPain
            }

            if (totalPain < minPain) {
                minPain = totalPain
                maxPainStrike = strike
            }
        }

        return maxPainStrike
    }

    // MARK: - Support/Resistance Identification

    private fun identifySupportZones(strikeData: List<StrikeOIData>, spotPrice: Double): List<OIZone> {
        // Support zones are strikes below spot with high PUT OI
        val belowSpot = strikeData.filter { it.strikePrice < spotPrice }
        if (belowSpot.isEmpty()) return emptyList()

        val maxPutOI = belowSpot.maxOfOrNull { it.putOI } ?: 1L

        return belowSpot
            .filter { it.putOI > 0 }
            .map { data ->
                val percentOfMax = data.putOI.toDouble() / maxPutOI
                val strength = when {
                    percentOfMax >= STRONG_ZONE_THRESHOLD -> ZoneStrength.STRONG
                    percentOfMax >= MODERATE_ZONE_THRESHOLD -> ZoneStrength.MODERATE
                    else -> ZoneStrength.WEAK
                }

                OIZone(
                    strikePrice = data.strikePrice,
                    callOI = data.callOI,
                    putOI = data.putOI,
                    netOI = data.totalOI,
                    zoneType = ZoneType.SUPPORT,
                    strength = strength
                )
            }
            .filter { zone ->
                val percentOfMax = zone.putOI.toDouble() / maxPutOI
                percentOfMax >= MODERATE_ZONE_THRESHOLD
            }
            .sortedByDescending { it.strikePrice } // Nearest to spot first
    }

    private fun identifyResistanceZones(strikeData: List<StrikeOIData>, spotPrice: Double): List<OIZone> {
        // Resistance zones are strikes above spot with high CALL OI
        val aboveSpot = strikeData.filter { it.strikePrice > spotPrice }
        if (aboveSpot.isEmpty()) return emptyList()

        val maxCallOI = aboveSpot.maxOfOrNull { it.callOI } ?: 1L

        return aboveSpot
            .filter { it.callOI > 0 }
            .map { data ->
                val percentOfMax = data.callOI.toDouble() / maxCallOI
                val strength = when {
                    percentOfMax >= STRONG_ZONE_THRESHOLD -> ZoneStrength.STRONG
                    percentOfMax >= MODERATE_ZONE_THRESHOLD -> ZoneStrength.MODERATE
                    else -> ZoneStrength.WEAK
                }

                OIZone(
                    strikePrice = data.strikePrice,
                    callOI = data.callOI,
                    putOI = data.putOI,
                    netOI = data.totalOI,
                    zoneType = ZoneType.RESISTANCE,
                    strength = strength
                )
            }
            .filter { zone ->
                val percentOfMax = zone.callOI.toDouble() / maxCallOI
                percentOfMax >= MODERATE_ZONE_THRESHOLD
            }
            .sortedBy { it.strikePrice } // Nearest to spot first
    }

    // MARK: - Heatmap Generation

    private fun generateHeatmapCells(
        strikeData: List<StrikeOIData>,
        spotPrice: Double,
        maxPain: Double
    ): List<OIHeatmapCell> {
        val maxCallOI = strikeData.maxOfOrNull { it.callOI } ?: 1L
        val maxPutOI = strikeData.maxOfOrNull { it.putOI } ?: 1L
        val atmStrike = findATMStrike(spotPrice, strikeData.map { it.strikePrice })

        return strikeData.map { data ->
            val callIntensity = if (maxCallOI > 0) data.callOI.toFloat() / maxCallOI else 0f
            val putIntensity = if (maxPutOI > 0) data.putOI.toFloat() / maxPutOI else 0f
            val combinedIntensity = (callIntensity + putIntensity) / 2

            OIHeatmapCell(
                strikePrice = data.strikePrice,
                callOI = data.callOI,
                putOI = data.putOI,
                callOIChange = data.callOIChange,
                putOIChange = data.putOIChange,
                intensity = combinedIntensity
            )
        }
    }

    /**
     * Generate heatmap cells with zone type classification for visualization
     */
    fun generateHeatmapWithZones(
        strikeData: List<StrikeOIData>,
        spotPrice: Double,
        maxPain: Double
    ): List<HeatmapCell> {
        val maxCallOI = strikeData.maxOfOrNull { it.callOI } ?: 1L
        val maxPutOI = strikeData.maxOfOrNull { it.putOI } ?: 1L

        return strikeData.map { data ->
            val callIntensity = if (maxCallOI > 0) data.callOI.toDouble() / maxCallOI else 0.0
            val putIntensity = if (maxPutOI > 0) data.putOI.toDouble() / maxPutOI else 0.0
            val combinedIntensity = (callIntensity + putIntensity) / 2

            val zoneType = determineZoneType(
                strikePrice = data.strikePrice,
                spotPrice = spotPrice,
                maxPain = maxPain,
                callOI = data.callOI,
                putOI = data.putOI,
                callIntensity = callIntensity,
                putIntensity = putIntensity
            )

            HeatmapCell(
                strikePrice = data.strikePrice,
                callOI = data.callOI,
                putOI = data.putOI,
                callOIChange = data.callOIChange,
                putOIChange = data.putOIChange,
                intensity = combinedIntensity,
                zoneType = zoneType
            )
        }
    }

    private fun determineZoneType(
        strikePrice: Double,
        spotPrice: Double,
        maxPain: Double,
        callOI: Long,
        putOI: Long,
        callIntensity: Double,
        putIntensity: Double
    ): HeatmapZoneType {
        // Check if this is max pain zone
        if (abs(strikePrice - maxPain) < 1) {
            return HeatmapZoneType.MAX_PAIN
        }

        val isBelow = strikePrice < spotPrice
        val isAbove = strikePrice > spotPrice

        return when {
            // Below spot - check for support (high put OI)
            isBelow && putIntensity >= STRONG_ZONE_THRESHOLD -> HeatmapZoneType.STRONG_SUPPORT
            isBelow && putIntensity >= MODERATE_ZONE_THRESHOLD -> HeatmapZoneType.WEAK_SUPPORT

            // Above spot - check for resistance (high call OI)
            isAbove && callIntensity >= STRONG_ZONE_THRESHOLD -> HeatmapZoneType.STRONG_RESISTANCE
            isAbove && callIntensity >= MODERATE_ZONE_THRESHOLD -> HeatmapZoneType.WEAK_RESISTANCE

            else -> HeatmapZoneType.NEUTRAL
        }
    }

    // MARK: - IV Surface Generation

    private fun generateIVSurface(strikeData: List<StrikeOIData>, spotPrice: Double): List<IVSurfacePoint> {
        val atmStrike = findATMStrike(spotPrice, strikeData.map { it.strikePrice })

        return strikeData.map { data ->
            IVSurfacePoint(
                strikePrice = data.strikePrice,
                expiry = "", // Would need expiry from option chain
                callIV = data.callIV * 100, // Convert to percentage
                putIV = data.putIV * 100,
                avgIV = data.avgIV * 100
            )
        }
    }

    // MARK: - Market Sentiment

    private fun determineMarketSentiment(
        pcr: Double,
        strikeData: List<StrikeOIData>,
        spotPrice: Double
    ): MarketBias {
        // Analyze PCR
        val pcrSentiment = when {
            pcr > 1.5 -> 2.0  // Strongly bullish (high put writing = bullish)
            pcr > 1.2 -> 1.0  // Bullish
            pcr > 0.8 -> 0.0  // Neutral
            pcr > 0.5 -> -1.0 // Bearish
            else -> -2.0      // Strongly bearish
        }

        // Analyze OI change
        val totalCallOIChange = strikeData.sumOf { it.callOIChange }
        val totalPutOIChange = strikeData.sumOf { it.putOIChange }

        val oiChangeSentiment = when {
            totalPutOIChange > totalCallOIChange * 2 -> 1.5  // Put writing increasing = bullish
            totalCallOIChange > totalPutOIChange * 2 -> -1.5 // Call writing increasing = bearish
            else -> 0.0
        }

        // Combine signals
        val combinedScore = (pcrSentiment + oiChangeSentiment) / 2

        return when {
            combinedScore >= 1.5 -> MarketBias.STRONG_BULLISH
            combinedScore >= 0.5 -> MarketBias.BULLISH
            combinedScore <= -1.5 -> MarketBias.STRONG_BEARISH
            combinedScore <= -0.5 -> MarketBias.BEARISH
            else -> MarketBias.NEUTRAL
        }
    }

    // MARK: - Interpretation Generation

    private fun generateInterpretation(
        strikeData: List<StrikeOIData>,
        spotPrice: Double,
        pcr: Double,
        maxPain: Double,
        supportZones: List<OIZone>,
        resistanceZones: List<OIZone>,
        sentiment: MarketBias
    ): OIInterpretation {
        val insights = mutableListOf<String>()

        // PCR insight
        val pcrInsight = when {
            pcr > 1.3 -> "High PCR (${String.format("%.2f", pcr)}): Put writers are aggressive. Market expects support. Bullish undertone."
            pcr < 0.7 -> "Low PCR (${String.format("%.2f", pcr)}): Call writers are aggressive. Market expects resistance. Bearish undertone."
            else -> "Neutral PCR (${String.format("%.2f", pcr)}): Balanced put/call writing. Market in wait-and-watch mode."
        }
        insights.add(pcrInsight)

        // Max Pain insight
        val maxPainDistance = abs(maxPain - spotPrice)
        val maxPainDirection = if (maxPain > spotPrice) "above" else "below"
        insights.add("Max Pain at ${String.format("%.0f", maxPain)}: ${String.format("%.0f", maxPainDistance)} points $maxPainDirection spot. Expiry gravitation likely.")

        // Support/Resistance insights
        supportZones.firstOrNull { it.strength == ZoneStrength.STRONG }?.let { support ->
            insights.add("Strong Support at ${String.format("%.0f", support.strikePrice)}: Heavy put OI suggests strong buying interest.")
        }

        resistanceZones.firstOrNull { it.strength == ZoneStrength.STRONG }?.let { resistance ->
            insights.add("Strong Resistance at ${String.format("%.0f", resistance.strikePrice)}: Heavy call OI suggests strong selling pressure.")
        }

        // OI change insight
        val totalCallChange = strikeData.sumOf { it.callOIChange }
        val totalPutChange = strikeData.sumOf { it.putOIChange }

        if (abs(totalPutChange) > abs(totalCallChange) * 1.5) {
            val direction = if (totalPutChange > 0) "building" else "unwinding"
            val interpretation = if (totalPutChange > 0) {
                "Fresh put writing indicates bullish sentiment."
            } else {
                "Put unwinding suggests profit booking or trend reversal."
            }
            insights.add("Put OI ${direction.replaceFirstChar { it.uppercase() }}: $interpretation")
        } else if (abs(totalCallChange) > abs(totalPutChange) * 1.5) {
            val direction = if (totalCallChange > 0) "building" else "unwinding"
            val interpretation = if (totalCallChange > 0) {
                "Fresh call writing indicates bearish sentiment."
            } else {
                "Call unwinding suggests short covering rally possible."
            }
            insights.add("Call OI ${direction.replaceFirstChar { it.uppercase() }}: $interpretation")
        }

        // Calculate expected range
        val immediateSupport = supportZones.firstOrNull()?.strikePrice ?: (spotPrice - 200)
        val immediateResistance = resistanceZones.firstOrNull()?.strikePrice ?: (spotPrice + 200)

        // Generate title based on sentiment
        val title = when (sentiment) {
            MarketBias.STRONG_BULLISH -> "Strong Bullish Setup"
            MarketBias.BULLISH -> "Mildly Bullish Bias"
            MarketBias.NEUTRAL -> "Range-Bound Setup"
            MarketBias.BEARISH -> "Mildly Bearish Bias"
            MarketBias.STRONG_BEARISH -> "Strong Bearish Setup"
            MarketBias.SIDEWAYS -> "Sideways Market"
        }

        val description = "Based on OI analysis, market is showing ${sentiment.displayName.lowercase()} characteristics. " +
                "Expected trading range: ${String.format("%.0f", immediateSupport)} - ${String.format("%.0f", immediateResistance)}."

        val marketSentiment = when (sentiment) {
            MarketBias.STRONG_BULLISH, MarketBias.BULLISH -> MarketSentiment.BULLISH
            MarketBias.STRONG_BEARISH, MarketBias.BEARISH -> MarketSentiment.BEARISH
            else -> MarketSentiment.NEUTRAL
        }

        val confidence = when (sentiment) {
            MarketBias.STRONG_BULLISH, MarketBias.STRONG_BEARISH -> ConfidenceLevel.HIGH
            MarketBias.BULLISH, MarketBias.BEARISH -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }

        return OIInterpretation(
            title = title,
            description = "$description\n\n${insights.joinToString("\n\n")}",
            bias = marketSentiment,
            confidence = confidence
        )
    }

    // MARK: - Smart Money Activity Detection

    private fun detectSmartMoneyPatterns(strikeData: List<StrikeOIData>): List<SmartMoneyActivity> {
        val activities = mutableListOf<SmartMoneyActivity>()

        for (data in strikeData) {
            // Check for significant call activity
            val callOIChangePercent = if (data.callOI > 0) {
                data.callOIChange.toDouble() / data.callOI
            } else 0.0

            if (abs(callOIChangePercent) > SMART_MONEY_CHANGE_THRESHOLD && data.callOI > 10000) {
                val isBullish = data.callOIChange < 0 // Call unwinding is bullish
                activities.add(
                    SmartMoneyActivity(
                        strikePrice = data.strikePrice,
                        optionType = OptionType.CALL,
                        oiChange = data.callOIChange,
                        oiChangePercent = callOIChangePercent * 100,
                        volumeOIRatio = 0.0, // Would need volume data
                        interpretation = if (data.callOIChange > 0) {
                            "Heavy call writing detected - bearish signal"
                        } else {
                            "Call unwinding detected - short covering, bullish signal"
                        },
                        isBullish = isBullish
                    )
                )
            }

            // Check for significant put activity
            val putOIChangePercent = if (data.putOI > 0) {
                data.putOIChange.toDouble() / data.putOI
            } else 0.0

            if (abs(putOIChangePercent) > SMART_MONEY_CHANGE_THRESHOLD && data.putOI > 10000) {
                val isBullish = data.putOIChange > 0 // Put writing is bullish
                activities.add(
                    SmartMoneyActivity(
                        strikePrice = data.strikePrice,
                        optionType = OptionType.PUT,
                        oiChange = data.putOIChange,
                        oiChangePercent = putOIChangePercent * 100,
                        volumeOIRatio = 0.0,
                        interpretation = if (data.putOIChange > 0) {
                            "Heavy put writing detected - bullish signal"
                        } else {
                            "Put unwinding detected - profit booking or bearish signal"
                        },
                        isBullish = isBullish
                    )
                )
            }
        }

        return activities.sortedByDescending { abs(it.oiChangePercent) }
    }

    /**
     * Detect smart money activity by comparing current and previous data
     */
    fun detectSmartMoneyActivity(
        currentData: List<StrikeOIData>,
        previousData: List<StrikeOIData>?
    ): List<SmartMoneyActivity> {
        if (previousData == null) return detectSmartMoneyPatterns(currentData)

        val activities = mutableListOf<SmartMoneyActivity>()

        for (current in currentData) {
            val prev = previousData.find { it.strikePrice == current.strikePrice } ?: continue

            // Check call activity
            val callChange = current.callOI - prev.callOI
            val callPercentChange = if (prev.callOI > 0) {
                callChange.toDouble() / prev.callOI
            } else 0.0

            if (abs(callPercentChange) > SMART_MONEY_CHANGE_THRESHOLD) {
                val isBullish = callChange < 0 // Call unwinding is bullish
                activities.add(
                    SmartMoneyActivity(
                        strikePrice = current.strikePrice,
                        optionType = OptionType.CALL,
                        oiChange = callChange,
                        oiChangePercent = callPercentChange * 100,
                        volumeOIRatio = 0.0,
                        interpretation = if (callChange > 0) {
                            "Heavy call writing - bearish"
                        } else {
                            "Call unwinding - bullish"
                        },
                        isBullish = isBullish
                    )
                )
            }

            // Check put activity
            val putChange = current.putOI - prev.putOI
            val putPercentChange = if (prev.putOI > 0) {
                putChange.toDouble() / prev.putOI
            } else 0.0

            if (abs(putPercentChange) > SMART_MONEY_CHANGE_THRESHOLD) {
                val isBullish = putChange > 0 // Put writing is bullish
                activities.add(
                    SmartMoneyActivity(
                        strikePrice = current.strikePrice,
                        optionType = OptionType.PUT,
                        oiChange = putChange,
                        oiChangePercent = putPercentChange * 100,
                        volumeOIRatio = 0.0,
                        interpretation = if (putChange > 0) {
                            "Heavy put writing - bullish"
                        } else {
                            "Put unwinding - bearish"
                        },
                        isBullish = isBullish
                    )
                )
            }
        }

        return activities.sortedByDescending { abs(it.oiChangePercent) }
    }

    // MARK: - Helper Methods

    private fun findATMStrike(spotPrice: Double, strikes: List<Double>): Double {
        if (strikes.isEmpty()) return spotPrice
        return strikes.minByOrNull { abs(it - spotPrice) } ?: spotPrice
    }

    /**
     * Format OI value for display (e.g., 1.5L, 500K)
     */
    fun formatOI(oi: Long): String {
        return when {
            oi >= 100_000 -> String.format("%.1fL", oi / 100_000.0)
            oi >= 1000 -> String.format("%.0fK", oi / 1000.0)
            else -> oi.toString()
        }
    }

    /**
     * Get the color for an OI change indicator
     */
    fun getOIChangeColor(change: Long): OIChangeType {
        return when {
            change > 0 -> OIChangeType.BUILDING
            change < 0 -> OIChangeType.UNWINDING
            else -> OIChangeType.NEUTRAL
        }
    }

    enum class OIChangeType {
        BUILDING,   // Green - OI increasing
        UNWINDING,  // Red - OI decreasing
        NEUTRAL     // Gray - No change
    }
}
