package com.optix.app.core.calculation

import android.util.Log
import com.optix.app.domain.model.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Local Option Scoring Engine - Analyzes option chain data and generates
 * trade suggestions based on professional trading criteria.
 *
 * This uses the same ML-like scoring logic as iOS to ensure consistent
 * suggestions across both platforms.
 */
@Singleton
class OptionScoringEngine @Inject constructor(
    private val blackScholesEngine: BlackScholesEngine
) {
    companion object {
        private const val TAG = "OptionScoringEngine"

        // Professional Trading Thresholds (matching iOS)
        private const val MIN_RISK_REWARD_RATIO = 1.5
        private const val MIN_SCORE_THRESHOLD = 50
        private const val STRICT_MIN_SCORE_THRESHOLD = 60
        private const val MAX_SUGGESTIONS = 5
        private const val MIN_DELTA = 0.20  // Avoid deep OTM
        private const val MAX_DELTA = 0.80  // Avoid deep ITM
        private const val MIN_LIQUIDITY = 100L // Minimum volume (lowered to match iOS)
        private const val MIN_OPEN_INTEREST = 500L // Minimum OI (lowered to match iOS)

        // ML Signal thresholds (matching iOS exactly)
        private const val STRONG_BUY_THRESHOLD = 0.35
        private const val BUY_THRESHOLD = 0.15
        private const val SELL_THRESHOLD = -0.15
        private const val STRONG_SELL_THRESHOLD = -0.35
    }

    private enum class TermStructure {
        CONTANGO,
        FLAT,
        INVERTED
    }

    /**
     * Analyze option chain and generate trade suggestions
     */
    fun analyzeOptionChain(
        optionChain: OptionChain,
        pcr: Double,
        maxPainStrike: Double,
        indiaVix: Double? = null,
        intradayChangePct: Double? = null  // Percentage change from previous close (e.g., 0.5 for +0.5%)
    ): LocalAIAnalysisResult {
        if (optionChain.rows.isEmpty()) {
            return LocalAIAnalysisResult(
                marketBias = MarketBias.NEUTRAL,
                callSuggestions = emptyList(),
                putSuggestions = emptyList(),
                marketInsights = emptyList()
            )
        }

        val context = buildAnalysisContext(optionChain, pcr, maxPainStrike, indiaVix, intradayChangePct)

        // Determine market direction bias FIRST (like iOS)
        // calculateMarketBullishScore returns normalized score (for option scoring)
        // but determineMarketBias needs raw points (multiply back by 10)
        val marketBullishScore = calculateMarketBullishScore(context)
        val rawBiasPoints = (marketBullishScore * 10).toInt()  // Convert back to points for bias
        val marketBias = determineMarketBias(rawBiasPoints, context)

        // Score all options with market direction consideration
        val callScores = mutableListOf<ScoredOption>()
        val putScores = mutableListOf<ScoredOption>()

        optionChain.rows.forEach { row ->
            row.callData?.let { call ->
                val score = scoreOption(call, context, isCall = true, marketBullishScore = marketBullishScore)
                if (score != null) {
                    callScores.add(score)
                }
            }
            row.putData?.let { put ->
                val score = scoreOption(put, context, isCall = false, marketBullishScore = marketBullishScore)
                if (score != null) {
                    putScores.add(score)
                }
            }
        }

        // Sort by overall score (matching iOS - iOS sorts by overallScore)
        callScores.sortByDescending { it.totalScore }
        putScores.sortByDescending { it.totalScore }

        // Generate suggestions
        val callSuggestions = generateSuggestions(
            callScores.take(MAX_SUGGESTIONS),
            context,
            marketBias,
            isCall = true
        )

        val putSuggestions = generateSuggestions(
            putScores.take(MAX_SUGGESTIONS),
            context,
            marketBias,
            isCall = false
        )

        // Generate market insights
        val marketInsights = generateMarketInsights(context, marketBias)

        return LocalAIAnalysisResult(
            marketBias = marketBias,
            callSuggestions = callSuggestions,
            putSuggestions = putSuggestions,
            marketInsights = marketInsights
        )
    }

    /**
     * Calculate market bullish score using iOS points-based logic
     * This exactly matches iOS AIAnalysisService.determineMarketBias()
     * Returns net points (bullish - bearish) for compatibility
     */
    private fun calculateMarketBullishScore(context: AnalysisContext): Double {
        var bullishPoints = 0
        var bearishPoints = 0

        // =====================================================
        // INTRADAY MOMENTUM (Highest Priority - matches iOS)
        // Points scale with move magnitude using moveStrengthMultiplier
        // =====================================================
        val intradayMove = context.intradayChangePct ?: 0.0
        val absMove = abs(intradayMove)

        // Calculate move multiplier (matches iOS)
        val moveMultiplier = when {
            absMove > 0.5 -> 1.5   // Strong move
            absMove > 0.3 -> 1.25  // Moderate move
            else -> 1.0            // Normal move
        }

        // Determine if bullish or bearish move (matches iOS thresholds)
        val isBullishMove = intradayMove >= 0.15
        val isBearishMove = intradayMove <= -0.15

        if (isBullishMove) {
            val points = (2.0 * moveMultiplier).toInt()
            bullishPoints += points
            Log.d(TAG, "Bullish intraday move: +${String.format("%.2f", intradayMove)}% (strength: ${moveMultiplier}x, +$points points)")
        } else if (isBearishMove) {
            val points = (2.0 * moveMultiplier).toInt()
            bearishPoints += points
            Log.d(TAG, "Bearish intraday move: ${String.format("%.2f", intradayMove)}% (strength: ${moveMultiplier}x, +$points points)")
        }

        // =====================================================
        // PCR Analysis (EXACTLY matches iOS)
        // =====================================================
        when {
            context.pcr > 1.2 -> {
                bullishPoints += 2  // Contrarian bullish
            }
            context.pcr < 0.8 -> {
                bearishPoints += 1  // Too bullish, potential reversal
            }
            context.pcr > 1.0 -> {
                bullishPoints += 1
            }
            else -> {
                bearishPoints += 1
            }
        }

        // =====================================================
        // Max Pain Analysis (EXACTLY matches iOS)
        // =====================================================
        val maxPain = context.maxPainStrike
        if (context.spotPrice < maxPain) {
            bullishPoints += 2  // Spot below max pain = bullish pull
        } else if (context.spotPrice > maxPain) {
            bearishPoints += 2  // Spot above max pain = bearish pull
        }

        // =====================================================
        // OI Change Analysis (matches iOS)
        // =====================================================
        val totalCallOIChange = context.totalCallOIChange
        val totalPutOIChange = context.totalPutOIChange
        if (totalCallOIChange > totalPutOIChange) {
            bullishPoints += 1
        } else if (totalPutOIChange > totalCallOIChange) {
            bearishPoints += 1
        }

        // =====================================================
        // India VIX Analysis - CONTRARIAN indicator (matches iOS)
        // =====================================================
        val vix = context.indiaVix
        if (vix > 25) {
            // Extreme fear often marks bottoms - contrarian bullish
            bullishPoints += 1
        } else if (vix < 12) {
            // Complacency often precedes corrections - contrarian bearish
            bearishPoints += 1
        }

        // Return net bias as double (for compatibility with existing scoring)
        val netBias = bullishPoints - bearishPoints
        Log.d(TAG, "Market bias calculation: bullish=$bullishPoints, bearish=$bearishPoints, net=$netBias")

        // IMPORTANT: Normalize to iOS decimal range (±0.7 max)
        // Android points can range from -8 to +8, iOS values range from -0.7 to +0.7
        // Scale factor: divide by ~10 to match iOS thresholds (0.35 for STRONG_BUY, etc.)
        val normalizedScore = netBias.toDouble() / 10.0
        Log.d(TAG, "Normalized market bullish score: $normalizedScore (from points: $netBias)")

        return normalizedScore
    }

    /**
     * Build analysis context from option chain data
     */
    private fun buildAnalysisContext(
        optionChain: OptionChain,
        pcr: Double,
        maxPainStrike: Double,
        indiaVix: Double?,
        intradayChangePct: Double?
    ): AnalysisContext {
        val rows = optionChain.rows

        // Calculate max OI and change in OI for normalization
        var maxOI = 0L
        var maxChangeInOI = 0L
        var totalVolume = 0L
        var optionCount = 0

        rows.forEach { row ->
            row.callData?.let { call ->
                maxOI = max(maxOI, call.openInterest)
                maxChangeInOI = max(maxChangeInOI, abs(call.oiChange))
                totalVolume += call.volume
                optionCount++
            }
            row.putData?.let { put ->
                maxOI = max(maxOI, put.openInterest)
                maxChangeInOI = max(maxChangeInOI, abs(put.oiChange))
                totalVolume += put.volume
                optionCount++
            }
        }

        val avgVolume = if (optionCount > 0) totalVolume.toDouble() / optionCount else 0.0

        // Calculate total OI
        val totalCallOI = rows.sumOf { it.callData?.openInterest ?: 0L }
        val totalPutOI = rows.sumOf { it.putData?.openInterest ?: 0L }

        // Calculate total OI changes (for market bias analysis - matches iOS)
        val totalCallOIChange = rows.sumOf { it.callData?.oiChange ?: 0L }
        val totalPutOIChange = rows.sumOf { it.putData?.oiChange ?: 0L }

        // Find support level (highest put OI below spot)
        val supportLevel = rows
            .filter { it.strikePrice < optionChain.spotPrice && (it.putData?.openInterest ?: 0) > 0 }
            .maxByOrNull { it.putData?.openInterest ?: 0 }
            ?.strikePrice ?: (optionChain.spotPrice - 100)

        // Find resistance level (highest call OI above spot)
        val resistanceLevel = rows
            .filter { it.strikePrice > optionChain.spotPrice && (it.callData?.openInterest ?: 0) > 0 }
            .maxByOrNull { it.callData?.openInterest ?: 0 }
            ?.strikePrice ?: (optionChain.spotPrice + 100)

        // Calculate ATM IV
        val atmRow = rows.minByOrNull { abs(it.strikePrice - optionChain.atmStrike) }
        val atmIV = atmRow?.callData?.impliedVolatility
            ?: atmRow?.putData?.impliedVolatility
            ?: 15.0

        val ivValues = rows.flatMap { row ->
            listOfNotNull(
                row.callData?.impliedVolatility?.takeIf { it > 0 },
                row.putData?.impliedVolatility?.takeIf { it > 0 }
            )
        }

        val skew = calculateSkew(rows, optionChain.atmStrike)
        val termStructure = determineTermStructure(atmIV, indiaVix)

        return AnalysisContext(
            spotPrice = optionChain.spotPrice,
            atmStrike = optionChain.atmStrike,
            pcr = pcr,
            maxPainStrike = maxPainStrike,
            indiaVix = indiaVix ?: (atmIV * 0.8),
            maxOI = maxOI,
            maxChangeInOI = maxChangeInOI,
            avgVolume = avgVolume,
            totalCallOI = totalCallOI,
            totalPutOI = totalPutOI,
            totalCallOIChange = totalCallOIChange,
            totalPutOIChange = totalPutOIChange,
            supportLevel = supportLevel,
            resistanceLevel = resistanceLevel,
            atmIV = atmIV,
            intradayChangePct = intradayChangePct,
            ivValues = ivValues,
            skew = skew,
            termStructure = termStructure
        )
    }

    private fun calculateIVPercentile(option: OptionData, context: AnalysisContext): Double? {
        val values = context.ivValues.filter { it > 0 }.sorted()
        if (values.isEmpty()) return null
        val belowOrEqual = values.count { it <= option.impliedVolatility }
        val percentile = (belowOrEqual.toDouble() / values.size.toDouble()) * 100.0
        return percentile.coerceIn(0.0, 100.0)
    }

    private fun calculateSkew(rows: List<OptionChainRow>, atmStrike: Double): Double? {
        val puts = rows.filter { it.strikePrice < atmStrike && (it.putData?.impliedVolatility ?: 0.0) > 0 }
        val calls = rows.filter { it.strikePrice > atmStrike && (it.callData?.impliedVolatility ?: 0.0) > 0 }

        val putSlice = puts.takeLast(3)
        val callSlice = calls.take(3)

        if (putSlice.isEmpty() || callSlice.isEmpty()) return null

        val avgPutIV = putSlice.mapNotNull { it.putData?.impliedVolatility }.average()
        val avgCallIV = callSlice.mapNotNull { it.callData?.impliedVolatility }.average()

        return avgPutIV - avgCallIV
    }

    private fun determineTermStructure(atmIV: Double, indiaVix: Double?): TermStructure? {
        if (indiaVix == null || indiaVix <= 0) return null
        val diff = atmIV - indiaVix
        return when {
            diff >= 3 -> TermStructure.INVERTED
            diff <= -3 -> TermStructure.CONTANGO
            else -> TermStructure.FLAT
        }
    }

    private fun calculateIVRank(option: OptionData, context: AnalysisContext): Double {
        val atmIV = context.atmIV
        val currentIV = option.impliedVolatility

        if (context.indiaVix > 0) {
            val vixRank = ((context.indiaVix - 10) / 25 * 100).coerceIn(0.0, 100.0)
            val ivRatioToATM = if (atmIV > 0) currentIV / atmIV else 1.0
            val ivVsATMRank = ((ivRatioToATM - 0.8) / 0.4 * 100).coerceIn(0.0, 100.0)
            return (vixRank * 0.6 + ivVsATMRank * 0.4).coerceIn(0.0, 100.0)
        }

        val ivRatio = if (atmIV > 0) currentIV / atmIV else 1.0
        return ((ivRatio - 0.8) / 0.4 * 100).coerceIn(0.0, 100.0)
    }

    private fun calculateProbabilityOfProfit(option: OptionData, context: AnalysisContext, isCall: Boolean): Double? {
        val spot = context.spotPrice
        val strike = option.strikePrice
        val iv = option.impliedVolatility / 100.0
        val days = getDaysToExpiry(option.expiry)
        val timeToExpiry = max(0.5, days.toDouble()) / 365.0
        val r = 0.065

        if (iv <= 0 || timeToExpiry <= 0) return null

        val sqrtT = kotlin.math.sqrt(timeToExpiry)
        val d2 = (kotlin.math.ln(spot / strike) + (r - 0.5 * iv * iv) * timeToExpiry) / (iv * sqrtT)
        val pop = if (isCall) normalCDF(d2) * 100.0 else normalCDF(-d2) * 100.0

        return pop.coerceIn(5.0, 95.0)
    }

    private fun getDaysToExpiry(expiry: String): Int {
        val patterns = listOf("yyyy-MM-dd", "dd-MMM-yyyy", "dd MMM yyyy")
        val date = patterns.asSequence().mapNotNull { pattern ->
            try {
                LocalDate.parse(expiry, DateTimeFormatter.ofPattern(pattern))
            } catch (_: DateTimeParseException) {
                null
            }
        }.firstOrNull()

        val resolved = date ?: run {
            if (expiry.length >= 10) {
                try {
                    LocalDate.parse(expiry.substring(0, 10), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                } catch (_: Exception) {
                    null
                }
            } else null
        }

        return if (resolved != null) {
            max(0, ChronoUnit.DAYS.between(LocalDate.now(), resolved).toInt())
        } else {
            7
        }
    }

    private fun normalCDF(x: Double): Double {
        val a1 = 0.254829592
        val a2 = -0.284496736
        val a3 = 1.421413741
        val a4 = -1.453152027
        val a5 = 1.061405429
        val p = 0.3275911

        val sign = if (x < 0) -1.0 else 1.0
        val absX = kotlin.math.abs(x)
        val t = 1.0 / (1.0 + p * absX)
        val y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * kotlin.math.exp(-absX * absX / 2)

        return 0.5 * (1.0 + sign * y)
    }

    /**
     * Score a single option using iOS ML-like logic
     * Returns null if option doesn't meet basic criteria
     */
    private fun scoreOption(
        option: OptionData,
        context: AnalysisContext,
        isCall: Boolean,
        marketBullishScore: Double
    ): ScoredOption? {
        // Filter out options that don't meet minimum criteria
        if (option.volume < MIN_LIQUIDITY && option.openInterest < MIN_OPEN_INTEREST) {
            return null
        }

        // Calculate moneyness
        val moneyness = (option.strikePrice - context.spotPrice) / context.spotPrice

        // Skip deep OTM/ITM options (more than 8% from spot)
        if (abs(moneyness) > 0.08) {
            return null
        }

        // ========================================
        // ML-LIKE SCORING (matching iOS)
        // ========================================

        // CE profits when market is BULLISH (positive marketBullishScore)
        // PE profits when market is BEARISH (negative marketBullishScore)
        val optionScore = if (isCall) {
            marketBullishScore  // CE aligns with bullish market
        } else {
            -marketBullishScore  // PE aligns with bearish market
        }

        var finalScore = optionScore

        // Factor: Volume ratio
        val volumeRatio = if (context.avgVolume > 0) option.volume / context.avgVolume else 1.0
        if (volumeRatio > 1.5) {
            finalScore += 0.1
        } else if (volumeRatio > 0.8) {
            finalScore += 0.05
        }

        // Factor: IV relative to ATM
        val ivRatio = if (context.atmIV > 0) option.impliedVolatility / context.atmIV else 1.0
        if (ivRatio < 0.9) {
            finalScore += 0.1  // Potentially undervalued
        } else if (ivRatio > 1.2) {
            finalScore -= 0.1  // Potentially overvalued
        }

        // Factor: Delta consideration (matching iOS exactly)
        // iOS prefers 0.3-0.7 delta range
        val estimatedDelta = estimateDelta(option, context, isCall)
        val absDelta = abs(estimatedDelta)

        // Delta scoring - matching iOS MLOptionPredictor exactly
        when {
            absDelta >= 0.3 && absDelta <= 0.7 -> finalScore += 0.1  // Good delta range
            absDelta < 0.15 -> finalScore -= 0.15  // Too far OTM, low probability
            absDelta > 0.85 -> finalScore -= 0.05  // Deep ITM, expensive
        }

        // Factor: OI change
        // OI buildup in THIS specific option is a positive signal for that option
        // (CE buildup = bullish for CE, PE buildup = bullish for PE)
        val oiChangeNormalized = if (context.maxChangeInOI > 0) {
            option.oiChange.toDouble() / context.maxChangeInOI
        } else 0.0

        if (oiChangeNormalized > 0.3) {
            finalScore += 0.15  // OI buildup is good for this option (both CE and PE)
        } else if (oiChangeNormalized < -0.3) {
            finalScore -= 0.1  // OI unwinding is bad for this option
        }

        // Determine ML signal based on finalScore (matching iOS MLOptionPredictor exactly)
        // iOS formula: confidence = min(0.95, 0.7 + finalScore) for STRONG_BUY
        val (mlSignal, mlConfidence) = when {
            finalScore > STRONG_BUY_THRESHOLD -> Pair(MLSignal.STRONG_BUY, min(0.95, 0.7 + finalScore))
            finalScore > BUY_THRESHOLD -> Pair(MLSignal.BUY, min(0.85, 0.6 + finalScore))
            finalScore < STRONG_SELL_THRESHOLD -> Pair(MLSignal.STRONG_SELL, min(0.95, 0.7 + abs(finalScore)))
            finalScore < SELL_THRESHOLD -> Pair(MLSignal.SELL, min(0.85, 0.6 + abs(finalScore)))
            else -> Pair(MLSignal.HOLD, 0.5 + abs(finalScore))  // HOLD
        }

        val ivRank = calculateIVRank(option, context)
        val ivPercentile = calculateIVPercentile(option, context)
        val skew = context.skew
        val termStructure = context.termStructure
        val pop = calculateProbabilityOfProfit(option, context, isCall)

        // Calculate overall score (0-100) for display
        val metrics = calculateOverallScore(
            option = option,
            context = context,
            isCall = isCall,
            ivRank = ivRank,
            ivPercentile = ivPercentile,
            skew = skew,
            termStructure = termStructure,
            pop = pop,
            estimatedDelta = estimatedDelta
        )

        val reasoning = buildReasoning(option, context, isCall, mlSignal, finalScore, metrics)

        return ScoredOption(
            optionData = option,
            totalScore = metrics.totalScore,
            mlScore = finalScore,
            mlSignal = mlSignal,
            mlConfidence = mlConfidence,
            reasoning = reasoning,
            estimatedDelta = estimatedDelta,
            ivRank = ivRank,
            ivPercentile = ivPercentile,
            skew = skew,
            termStructure = termStructure,
            pop = pop
        )
    }

    /**
     * Get delta - use actual delta if available (non-zero), otherwise estimate
     * iOS uses option.delta ?? 0.5, we do the same
     */
    private fun estimateDelta(option: OptionData, context: AnalysisContext, isCall: Boolean): Double {
        // If actual delta is available and non-zero, use it
        if (option.delta != 0.0 && option.delta != null) {
            return option.delta
        }

        // Estimate delta based on moneyness (matching iOS fallback)
        val moneyness = (option.strikePrice - context.spotPrice) / context.spotPrice

        // iOS-style delta estimation
        return if (isCall) {
            when {
                abs(moneyness) < 0.01 -> 0.50  // ATM
                moneyness > 0 -> max(0.05, 0.5 - moneyness * 3)  // OTM call
                else -> min(0.95, 0.5 + abs(moneyness) * 3)  // ITM call
            }
        } else {
            when {
                abs(moneyness) < 0.01 -> -0.50  // ATM
                moneyness < 0 -> max(-0.95, -0.5 - abs(moneyness) * 3)  // OTM put
                else -> min(-0.05, -0.5 + moneyness * 3)  // ITM put
            }
        }
    }

    /**
     * Calculate overall score (0-100) for UI display
     * EXACTLY MATCHES iOS AIAnalysisService scoring formula
     * iOS uses weighted average of component scores with continuous formulas
     */
    private fun calculateOverallScore(
        option: OptionData,
        context: AnalysisContext,
        isCall: Boolean,
        ivRank: Double,
        ivPercentile: Double?,
        skew: Double?,
        termStructure: TermStructure?,
        pop: Double?,
        estimatedDelta: Double
    ): ScoringMetrics {
        var baseScore = 0.0

        val ivRankScore = 100 - ivRank
        baseScore += ivRankScore * 0.18

        val ivPercentileScore = ivPercentile?.let { 100 - it }
        if (ivPercentileScore != null) {
            baseScore += ivPercentileScore * 0.06
        }

        val oiChangeNormalized = if (context.maxChangeInOI > 0) {
            option.oiChange.toDouble() / context.maxChangeInOI
        } else 0.0

        val oiScore = when {
            oiChangeNormalized > 0.3 -> 50 + oiChangeNormalized * 50
            oiChangeNormalized > 0 -> 50 + oiChangeNormalized * 30
            oiChangeNormalized > -0.3 -> 40 + (0.3 + oiChangeNormalized) * 33
            else -> 20 + (0.7 + oiChangeNormalized) * 29
        }
        baseScore += oiScore * 0.18

        val absDelta = abs(estimatedDelta)
        val greeksScore = when {
            absDelta >= 0.4 && absDelta <= 0.6 -> 80.0
            absDelta >= 0.3 && absDelta <= 0.7 -> 65.0
            absDelta > 0.8 -> 45.0
            absDelta < 0.2 -> 35.0
            else -> 55.0
        }

        val moneyness = abs((option.strikePrice - context.spotPrice) / context.spotPrice) * 100
        val moneynessScore = when {
            moneyness < 0.5 -> 95.0
            moneyness < 1.5 -> 85.0
            moneyness < 3.0 -> 65.0
            moneyness < 5.0 -> 45.0
            moneyness < 8.0 -> 30.0
            else -> 15.0
        }
        val adjustedGreeksScore = (moneynessScore * 0.67) + (greeksScore * 0.33)

        if (pop != null) {
            baseScore += pop * 0.14
        } else {
            baseScore += adjustedGreeksScore * 0.07 + ivRankScore * 0.07
        }

        baseScore += adjustedGreeksScore * 0.14

        val volumeRatio = if (context.avgVolume > 0) option.volume / context.avgVolume else 1.0
        val volumeOIRatio = if (option.openInterest > 0) option.volume.toDouble() / option.openInterest else 0.0

        val volumeScore = when {
            volumeOIRatio > 1.0 -> 70 + min(30.0, volumeOIRatio * 10)
            volumeRatio > 2.0 -> 65 + min(25.0, (volumeRatio - 2) * 5)
            volumeRatio > 1.0 -> 55 + (volumeRatio - 1) * 10
            option.volume < 100 -> 30.0
            else -> 45.0
        }
        baseScore += volumeScore * 0.09

        val pcrScore = when {
            context.pcr > 1.2 -> if (isCall) 70.0 else 55.0
            context.pcr > 1.0 -> if (isCall) 60.0 else 50.0
            context.pcr < 0.8 -> if (isCall) 55.0 else 65.0
            else -> 50.0
        }
        baseScore += pcrScore * 0.09

        val distanceToMaxPain = abs(context.spotPrice - context.maxPainStrike) / context.maxPainStrike * 100
        val spotVsMaxPain = (context.spotPrice - context.maxPainStrike) / context.maxPainStrike

        var maxPainScore = when {
            distanceToMaxPain < 1 -> 70.0
            distanceToMaxPain < 2 -> 60.0
            else -> 50.0
        }
        if (isCall && spotVsMaxPain < 0) maxPainScore += 15
        else if (!isCall && spotVsMaxPain > 0) maxPainScore += 15
        baseScore += min(85.0, maxPainScore) * 0.04

        val liquidityScore = when {
            volumeOIRatio > 0.5 -> 90.0
            volumeOIRatio > 0.2 -> 70.0
            volumeOIRatio > 0.1 -> 50.0
            volumeOIRatio > 0.05 -> 35.0
            else -> 20.0
        }
        baseScore += liquidityScore * 0.04

        val skewScore = skew?.let {
            if (isCall) {
                when {
                    it <= 2 -> 65.0
                    it >= 8 -> 35.0
                    else -> 52.0
                }
            } else {
                when {
                    it >= 6 -> 70.0
                    it <= 0 -> 35.0
                    else -> 52.0
                }
            }
        }
        if (skewScore != null) {
            baseScore += skewScore * 0.02
        }

        val termStructureScore = termStructure?.let {
            when (it) {
                TermStructure.CONTANGO -> 70.0
                TermStructure.FLAT -> 55.0
                TermStructure.INVERTED -> 35.0
            }
        }
        if (termStructureScore != null) {
            baseScore += termStructureScore * 0.02
        }

        var penalties = 0
        if (option.volume < 100) penalties++
        if (absDelta < 0.15 || absDelta > 0.85) penalties++
        if (option.impliedVolatility / context.atmIV > 1.3) penalties++

        baseScore -= penalties * 5.0

        val totalScore = baseScore.toInt().coerceIn(0, 100)

        return ScoringMetrics(
            totalScore = totalScore,
            ivRank = ivRank,
            ivPercentile = ivPercentile,
            skew = skew,
            termStructure = termStructure,
            pop = pop,
            ivRankScore = ivRankScore,
            ivPercentileScore = ivPercentileScore,
            skewScore = skewScore,
            termStructureScore = termStructureScore,
            oiScore = oiScore,
            volumeScore = volumeScore,
            greeksScore = adjustedGreeksScore,
            pcrScore = pcrScore,
            maxPainScore = maxPainScore,
            liquidityScore = liquidityScore
        )
    }

    /**
     * Build reasoning list for the suggestion
     */
    private fun buildReasoning(
        option: OptionData,
        context: AnalysisContext,
        isCall: Boolean,
        mlSignal: MLSignal,
        finalScore: Double,
        metrics: ScoringMetrics
    ): List<ScoreReasoning> {
        val reasoning = mutableListOf<ScoreReasoning>()

        // Market direction reasoning
        val marketDirection = if (finalScore > 0) "bullish" else if (finalScore < 0) "bearish" else "neutral"
        val optionAlignment = if ((isCall && finalScore > 0) || (!isCall && finalScore < 0)) {
            "Aligned with $marketDirection market"
        } else {
            "Against $marketDirection market"
        }
        reasoning.add(ScoreReasoning(
            factor = "Market Alignment",
            score = if (finalScore > 0.1 == isCall) 15 else 5,
            maxScore = 20,
            description = optionAlignment,
            isPositive = (finalScore > 0.1 == isCall) || (finalScore < -0.1 == !isCall)
        ))

        // IV Rank reasoning
        val ivRankDescription = when {
            metrics.ivRank < 30 -> "IV Rank ${metrics.ivRank.toInt()}: Cheap premiums"
            metrics.ivRank > 70 -> "IV Rank ${metrics.ivRank.toInt()}: Expensive premiums"
            else -> "IV Rank ${metrics.ivRank.toInt()}: Fairly priced"
        }
        reasoning.add(ScoreReasoning(
            factor = "IV Rank",
            score = ((metrics.ivRankScore / 100) * 20).toInt(),
            maxScore = 20,
            description = ivRankDescription,
            isPositive = metrics.ivRank < 50
        ))

        // IV Percentile reasoning
        if (metrics.ivPercentile != null && metrics.ivPercentileScore != null) {
            reasoning.add(ScoreReasoning(
                factor = "IV Percentile",
                score = ((metrics.ivPercentileScore / 100) * 10).toInt(),
                maxScore = 10,
                description = "IV at ${metrics.ivPercentile.toInt()}th percentile",
                isPositive = metrics.ivPercentile < 50
            ))
        }

        // PCR reasoning
        val pcrDescription = when {
            context.pcr > 1.2 -> "High PCR (${String.format("%.2f", context.pcr)}) - Contrarian bullish"
            context.pcr < 0.7 -> "Low PCR (${String.format("%.2f", context.pcr)}) - Contrarian bearish"
            else -> "PCR at ${String.format("%.2f", context.pcr)}"
        }
        reasoning.add(ScoreReasoning(
            factor = "PCR Analysis",
            score = ((metrics.pcrScore / 100) * 20).toInt(),
            maxScore = 20,
            description = pcrDescription,
            isPositive = (context.pcr > 1.0 && isCall) || (context.pcr < 1.0 && !isCall)
        ))

        // OI reasoning
        val oiDescription = if (option.oiChange > 0) {
            "OI buildup of ${formatNumber(option.oiChange)}"
        } else if (option.oiChange < 0) {
            "OI unwinding of ${formatNumber(option.oiChange)}"
        } else {
            "Stable OI"
        }
        reasoning.add(ScoreReasoning(
            factor = "OI Analysis",
            score = ((metrics.oiScore / 100) * 20).toInt(),
            maxScore = 20,
            description = oiDescription,
            isPositive = option.oiChange > 0
        ))

        // Volume reasoning
        val volumeRatio = if (context.avgVolume > 0) option.volume / context.avgVolume else 1.0
        reasoning.add(ScoreReasoning(
            factor = "Volume",
            score = ((metrics.volumeScore / 100) * 15).toInt(),
            maxScore = 15,
            description = if (volumeRatio > 1.5) "High volume (${String.format("%.1f", volumeRatio)}x avg)"
                          else "Volume ${String.format("%.1f", volumeRatio)}x average",
            isPositive = volumeRatio > 1.0
        ))

        // POP reasoning
        if (metrics.pop != null) {
            reasoning.add(ScoreReasoning(
                factor = "POP",
                score = ((metrics.pop / 100) * 15).toInt(),
                maxScore = 15,
                description = "POP ${metrics.pop.toInt()}%",
                isPositive = metrics.pop >= 45
            ))
        }

        // Skew reasoning
        if (metrics.skew != null && metrics.skewScore != null) {
            val skewDesc = if (isCall) {
                if (metrics.skew <= 2) "Low put skew (${String.format("%.1f", metrics.skew)}) favors calls"
                else "Put skew ${String.format("%.1f", metrics.skew)}"
            } else {
                if (metrics.skew >= 6) "High put skew (${String.format("%.1f", metrics.skew)}) favors puts"
                else "Put skew ${String.format("%.1f", metrics.skew)}"
            }
            reasoning.add(ScoreReasoning(
                factor = "Skew",
                score = ((metrics.skewScore / 100) * 10).toInt(),
                maxScore = 10,
                description = skewDesc,
                isPositive = (isCall && metrics.skew <= 2) || (!isCall && metrics.skew >= 6)
            ))
        }

        // Term structure reasoning
        if (metrics.termStructure != null && metrics.termStructureScore != null) {
            val termDesc = when (metrics.termStructure) {
                TermStructure.CONTANGO -> "Term structure contango"
                TermStructure.FLAT -> "Term structure flat"
                TermStructure.INVERTED -> "Term structure inverted"
            }
            reasoning.add(ScoreReasoning(
                factor = "Term Structure",
                score = ((metrics.termStructureScore / 100) * 10).toInt(),
                maxScore = 10,
                description = termDesc,
                isPositive = metrics.termStructure == TermStructure.CONTANGO
            ))
        }

        return reasoning
    }

    private fun formatNumber(num: Long): String {
        return when {
            abs(num) >= 10000000 -> "${num / 10000000}Cr"
            abs(num) >= 100000 -> "${num / 100000}L"
            abs(num) >= 1000 -> "${num / 1000}K"
            else -> num.toString()
        }
    }

    /**
     * Generate trade suggestions from scored options (matching iOS filtering)
     */
    private fun generateSuggestions(
        scoredOptions: List<ScoredOption>,
        context: AnalysisContext,
        marketBias: MarketBias,
        isCall: Boolean
    ): List<AITradeSuggestion> {
        return scoredOptions.mapNotNull { scored ->
            val option = scored.optionData

            // Delta filter - matching iOS (avoid deep ITM/OTM)
            val absDelta = abs(scored.estimatedDelta)
            if (absDelta < MIN_DELTA || absDelta > MAX_DELTA) {
                return@mapNotNull null
            }

            // Strict no-trade gate (must pass all filters)
            val daysToExpiry = getDaysToExpiry(option.expiry)
            if (scored.totalScore < STRICT_MIN_SCORE_THRESHOLD) return@mapNotNull null
            if (scored.ivRank > 75) return@mapNotNull null
            if (scored.ivPercentile != null && scored.ivPercentile > 75) return@mapNotNull null
            if (scored.termStructure == TermStructure.INVERTED && daysToExpiry <= 7) return@mapNotNull null
            if (scored.skew != null) {
                if (isCall && scored.skew > 8) return@mapNotNull null
                if (!isCall && scored.skew < 0) return@mapNotNull null
            }

            // Convert ML signal to trade direction (matching iOS signal types)
            val action = when (scored.mlSignal) {
                MLSignal.STRONG_BUY -> TradeDirection.STRONG_BUY
                MLSignal.BUY -> TradeDirection.BUY
                MLSignal.HOLD -> TradeDirection.HOLD
                MLSignal.SELL -> TradeDirection.SELL
                MLSignal.STRONG_SELL -> TradeDirection.STRONG_SELL
            }

            // Calculate entry, target, and stop-loss (matching iOS logic)
            val entryPrice = option.lastPrice
            val (targetPrice, stopLoss) = calculateTargetAndStopLoss(
                entryPrice = entryPrice,
                action = action,
                score = scored.totalScore,
                iv = option.impliedVolatility,
                delta = scored.estimatedDelta
            )

            // Validate target/stoploss for the action
            val isValid = when (action) {
                TradeDirection.STRONG_BUY, TradeDirection.BUY, TradeDirection.HOLD -> targetPrice > entryPrice && stopLoss < entryPrice
                TradeDirection.SELL, TradeDirection.STRONG_SELL -> targetPrice < entryPrice && stopLoss > entryPrice
            }

            if (!isValid) return@mapNotNull null

            // Calculate risk-reward ratio
            val potentialProfit = abs(targetPrice - entryPrice)
            val potentialLoss = abs(entryPrice - stopLoss)
            val riskReward = if (potentialLoss > 0) potentialProfit / potentialLoss else 0.0

            // Note: R:R filter is applied in ViewModel's filterSuggestions()
            // to allow more suggestions through and let ViewModel handle filtering

            // Calculate max profit/loss
            val lotSize = 75
            val maxProfit = potentialProfit * lotSize
            val maxLoss = potentialLoss * lotSize

            // Determine confidence level based on ML signal (matching iOS)
            val confidence = when (scored.mlSignal) {
                MLSignal.STRONG_BUY, MLSignal.STRONG_SELL -> ConfidenceLevel.HIGH
                MLSignal.BUY, MLSignal.SELL -> ConfidenceLevel.MEDIUM
                MLSignal.HOLD -> ConfidenceLevel.LOW
            }

            if (confidence == ConfidenceLevel.LOW) return@mapNotNull null

            // iOS shows overallScore in the gauge (0-100), not ML confidence
            val displayScore = scored.totalScore

            AITradeSuggestion(
                id = "${option.strikePrice.toLong()}_${option.optionType.name}",
                symbol = "",
                strikePrice = option.strikePrice,
                optionType = option.optionType,
                expiry = option.expiry,
                action = action,
                entryPrice = entryPrice,
                targetPrice = targetPrice,
                stopLoss = stopLoss,
                confidence = confidence,
                score = displayScore,  // Display ML confidence as score
                reasoning = scored.reasoning,
                riskReward = riskReward,
                maxProfit = maxProfit,
                maxLoss = maxLoss
            )
        }
    }

    /**
     * Calculate target and stop-loss prices (matching iOS volatility-adjusted logic)
     */
    private fun calculateTargetAndStopLoss(
        entryPrice: Double,
        action: TradeDirection,
        score: Int,
        iv: Double,
        delta: Double
    ): Pair<Double, Double> {
        // Volatility multiplier (base IV is 15%)
        val baseIV = 15.0
        val ivMultiplier = max(1.0, min(2.5, iv / baseIV))

        // Base percentages based on entry price (matching iOS)
        val (baseTargetPercent, baseSLPercent) = when {
            entryPrice >= 200 -> Pair(0.20, 0.15)
            entryPrice >= 100 -> Pair(0.25, 0.18)
            entryPrice >= 50 -> Pair(0.30, 0.20)
            entryPrice >= 20 -> Pair(0.40, 0.25)
            entryPrice >= 10 -> Pair(0.50, 0.30)
            entryPrice >= 5 -> Pair(0.60, 0.35)
            else -> Pair(1.00, 0.40)
        }

        // Apply volatility adjustment
        val adjustedTargetPercent = max(0.20, min(1.50, baseTargetPercent * ivMultiplier))
        val adjustedSLPercent = max(0.15, min(0.50, baseSLPercent * ivMultiplier))

        // Calculate prices based on action
        // Cap target at 3x entry (max +200%) to prevent unrealistic targets for deep OTM
        return when (action) {
            TradeDirection.STRONG_BUY, TradeDirection.BUY, TradeDirection.HOLD -> {
                val target = min(entryPrice * (1 + adjustedTargetPercent), entryPrice * 3.0)
                val sl = entryPrice * (1 - adjustedSLPercent)
                Pair(target, sl)
            }
            TradeDirection.SELL, TradeDirection.STRONG_SELL -> {
                val target = max(entryPrice * (1 - adjustedTargetPercent), entryPrice * 0.01)
                val sl = entryPrice * (1 + adjustedSLPercent)
                Pair(target, sl)
            }
        }
    }

    /**
     * Determine overall market bias
     */
    /**
     * Determine market bias from net points (EXACTLY matches iOS thresholds)
     * iOS uses: strongBullish >= 4, bullish >= 2, strongBearish <= -4, bearish <= -2
     */
    private fun determineMarketBias(netBiasPoints: Int, context: AnalysisContext): MarketBias {
        return when {
            netBiasPoints >= 4 -> MarketBias.STRONG_BULLISH
            netBiasPoints >= 2 -> MarketBias.BULLISH
            netBiasPoints <= -4 -> MarketBias.STRONG_BEARISH
            netBiasPoints <= -2 -> MarketBias.BEARISH
            else -> MarketBias.NEUTRAL
        }
    }

    /**
     * Generate market insights
     */
    private fun generateMarketInsights(
        context: AnalysisContext,
        marketBias: MarketBias
    ): List<MarketInsight> {
        val insights = mutableListOf<MarketInsight>()

        // PCR Insight
        insights.add(when {
            context.pcr > 1.3 -> MarketInsight(
                title = "High Put/Call Ratio",
                description = "PCR at ${String.format("%.2f", context.pcr)} indicates bearish hedging, often a contrarian bullish signal.",
                sentiment = MarketSentiment.BULLISH,
                importance = ConfidenceLevel.HIGH
            )
            context.pcr < 0.7 -> MarketInsight(
                title = "Low Put/Call Ratio",
                description = "PCR at ${String.format("%.2f", context.pcr)} shows excessive bullishness, potential reversal risk.",
                sentiment = MarketSentiment.BEARISH,
                importance = ConfidenceLevel.HIGH
            )
            else -> MarketInsight(
                title = "Balanced PCR",
                description = "PCR at ${String.format("%.2f", context.pcr)} indicates range-bound expectation.",
                sentiment = MarketSentiment.NEUTRAL,
                importance = ConfidenceLevel.MEDIUM
            )
        })

        // Max Pain Insight
        val maxPainDiff = context.spotPrice - context.maxPainStrike
        insights.add(when {
            maxPainDiff > context.spotPrice * 0.01 -> MarketInsight(
                title = "Spot Above Max Pain",
                description = "Spot at ${context.spotPrice.toLong()} is above max pain ${context.maxPainStrike.toLong()}. Pullback expected.",
                sentiment = MarketSentiment.BEARISH,
                importance = ConfidenceLevel.MEDIUM
            )
            maxPainDiff < -context.spotPrice * 0.01 -> MarketInsight(
                title = "Spot Below Max Pain",
                description = "Spot at ${context.spotPrice.toLong()} is below max pain ${context.maxPainStrike.toLong()}. Rally expected.",
                sentiment = MarketSentiment.BULLISH,
                importance = ConfidenceLevel.MEDIUM
            )
            else -> MarketInsight(
                title = "Near Max Pain",
                description = "Spot is near max pain ${context.maxPainStrike.toLong()}. Consolidation likely.",
                sentiment = MarketSentiment.NEUTRAL,
                importance = ConfidenceLevel.MEDIUM
            )
        })

        // VIX Insight
        insights.add(when {
            context.indiaVix > 22 -> MarketInsight(
                title = "High Volatility Alert",
                description = "VIX at ${String.format("%.1f", context.indiaVix)} indicates elevated fear. Premiums expensive.",
                sentiment = MarketSentiment.BEARISH,
                importance = ConfidenceLevel.HIGH
            )
            context.indiaVix > 18 -> MarketInsight(
                title = "Elevated Volatility",
                description = "VIX at ${String.format("%.1f", context.indiaVix)} suggests cautious trading.",
                sentiment = MarketSentiment.NEUTRAL,
                importance = ConfidenceLevel.MEDIUM
            )
            else -> MarketInsight(
                title = "Low Volatility",
                description = "VIX at ${String.format("%.1f", context.indiaVix)} - Options are relatively cheap.",
                sentiment = MarketSentiment.BULLISH,
                importance = ConfidenceLevel.LOW
            )
        })

        return insights
    }

    // Data classes
    private data class AnalysisContext(
        val spotPrice: Double,
        val atmStrike: Double,
        val pcr: Double,
        val maxPainStrike: Double,
        val indiaVix: Double,
        val maxOI: Long,
        val maxChangeInOI: Long,
        val avgVolume: Double,
        val totalCallOI: Long,
        val totalPutOI: Long,
        val totalCallOIChange: Long,  // Total call OI change for OI analysis
        val totalPutOIChange: Long,   // Total put OI change for OI analysis
        val supportLevel: Double,
        val resistanceLevel: Double,
        val atmIV: Double,
        val intradayChangePct: Double? = null,  // Percentage change from previous close
        val ivValues: List<Double> = emptyList(),
        val skew: Double? = null,
        val termStructure: TermStructure? = null
    )

    private data class ScoredOption(
        val optionData: OptionData,
        val totalScore: Int,
        val mlScore: Double,
        val mlSignal: MLSignal,
        val mlConfidence: Double,
        val reasoning: List<ScoreReasoning>,
        val estimatedDelta: Double,
        val ivRank: Double,
        val ivPercentile: Double?,
        val skew: Double?,
        val termStructure: TermStructure?,
        val pop: Double?
    )

    private data class ScoringMetrics(
        val totalScore: Int,
        val ivRank: Double,
        val ivPercentile: Double?,
        val skew: Double?,
        val termStructure: TermStructure?,
        val pop: Double?,
        val ivRankScore: Double,
        val ivPercentileScore: Double?,
        val skewScore: Double?,
        val termStructureScore: Double?,
        val oiScore: Double,
        val volumeScore: Double,
        val greeksScore: Double,
        val pcrScore: Double,
        val maxPainScore: Double,
        val liquidityScore: Double
    )

    enum class MLSignal {
        STRONG_BUY,
        BUY,
        HOLD,
        SELL,
        STRONG_SELL
    }
}

/**
 * Result of local AI analysis
 */
data class LocalAIAnalysisResult(
    val marketBias: MarketBias,
    val callSuggestions: List<AITradeSuggestion>,
    val putSuggestions: List<AITradeSuggestion>,
    val marketInsights: List<MarketInsight>
)
