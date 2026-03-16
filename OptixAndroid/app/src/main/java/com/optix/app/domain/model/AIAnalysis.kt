package com.optix.app.domain.model

/**
 * Confidence level for AI suggestions
 */
enum class ConfidenceLevel(val displayName: String, val color: Long) {
    HIGH("High", 0xFF22C55E),      // Green
    MEDIUM("Medium", 0xFFF59E0B),  // Amber
    LOW("Low", 0xFFEF4444)         // Red
}

/**
 * Market sentiment
 */
enum class MarketSentiment(val displayName: String, val emoji: String) {
    BULLISH("Bullish", "🐂"),
    BEARISH("Bearish", "🐻"),
    NEUTRAL("Neutral", "➖")
}

/**
 * Market bias based on analysis - More granular than sentiment
 */
enum class MarketBias(val displayName: String) {
    STRONG_BULLISH("Strong Bullish"),
    BULLISH("Bullish"),
    NEUTRAL("Neutral"),
    BEARISH("Bearish"),
    STRONG_BEARISH("Strong Bearish"),
    SIDEWAYS("Sideways")
}

/**
 * Trade direction for suggestions (matching iOS signal types)
 */
enum class TradeDirection(val displayText: String) {
    STRONG_BUY("STRONG BUY"),
    BUY("BUY"),
    HOLD("HOLD"),
    SELL("SELL"),
    STRONG_SELL("STRONG SELL")
}

/**
 * AI trade suggestion
 */
data class AITradeSuggestion(
    val id: String,
    val symbol: String,
    val strikePrice: Double,
    val optionType: OptionType,
    val expiry: String,
    val action: TradeDirection,
    val entryPrice: Double,
    val targetPrice: Double,
    val stopLoss: Double,
    val confidence: ConfidenceLevel,
    val score: Int, // 0-100
    val reasoning: List<ScoreReasoning>,
    val riskReward: Double,
    val maxProfit: Double,
    val maxLoss: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    val actionText: String
        get() = "${action.name} ${optionType.displayName}"

    val potentialReturn: Double
        get() = if (entryPrice > 0) {
            ((targetPrice - entryPrice) / entryPrice) * 100
        } else 0.0
}

/**
 * Individual score reasoning factor
 */
data class ScoreReasoning(
    val factor: String,
    val score: Int,
    val maxScore: Int,
    val description: String,
    val isPositive: Boolean
) {
    val percentage: Float
        get() = if (maxScore > 0) score.toFloat() / maxScore else 0f
}

/**
 * Option score with breakdown
 */
data class OptionScore(
    val totalScore: Int,
    val maxScore: Int = 100,
    val reasoning: List<ScoreReasoning>,
    val confidence: ConfidenceLevel
) {
    val percentage: Float
        get() = totalScore.toFloat() / maxScore

    companion object {
        fun fromScore(score: Int, reasoning: List<ScoreReasoning>): OptionScore {
            val confidence = when {
                score >= 70 -> ConfidenceLevel.HIGH
                score >= 50 -> ConfidenceLevel.MEDIUM
                else -> ConfidenceLevel.LOW
            }
            return OptionScore(score, 100, reasoning, confidence)
        }
    }
}

/**
 * Market insight
 */
data class MarketInsight(
    val title: String,
    val description: String,
    val sentiment: MarketSentiment,
    val importance: ConfidenceLevel,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Complete AI analysis result
 */
data class AIAnalysisResult(
    val symbol: String,
    val spotPrice: Double,
    val marketSentiment: MarketSentiment,
    val suggestions: List<AITradeSuggestion>,
    val insights: List<MarketInsight>,
    val pcr: Double,
    val ivPercentile: Double,
    val maxPain: Double,
    val indiaVix: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// ========================================
// Enhanced AI Analysis Models (Phase 2)
// ========================================

/**
 * Greeks-based recommendation type
 */
enum class GreeksRecommendationType(val displayName: String) {
    HIGH_GAMMA_OPPORTUNITY("High Gamma Opportunity"),
    THETA_DECAY_WARNING("Theta Decay Warning"),
    VEGA_EXPANSION_ALERT("Vega Expansion Alert"),
    VEGA_CRUSH_WARNING("Vega Crush Warning"),
    DELTA_NEUTRAL_OPPORTUNITY("Delta Neutral Opportunity"),
    HIGH_DELTA_EXPOSURE("High Delta Exposure"),
    GAMMA_RISK_ALERT("Gamma Risk Alert")
}

/**
 * Severity level for recommendations
 */
enum class RecommendationSeverity(val displayName: String, val color: Long) {
    INFO("Info", 0xFF3B82F6),       // Blue
    WARNING("Warning", 0xFFF59E0B),  // Amber
    CRITICAL("Critical", 0xFFEF4444) // Red
}

/**
 * Greeks-based recommendation
 */
data class GreeksRecommendation(
    val type: GreeksRecommendationType,
    val severity: RecommendationSeverity,
    val title: String,
    val description: String,
    val affectedGreek: String,
    val currentValue: Double,
    val thresholdValue: Double,
    val actionSuggested: String,
    val timestamp: Long = System.currentTimeMillis()
)

// Note: StrategyType and LegPosition are defined in Strategy.kt to avoid duplication

/**
 * Risk level classification
 */
enum class RiskLevel(val displayName: String, val color: Long) {
    LOW("Low Risk", 0xFF22C55E),
    MODERATE("Moderate Risk", 0xFFF59E0B),
    HIGH("High Risk", 0xFFEF4444)
}

/**
 * A single leg in a multi-leg strategy
 */
data class RecommendedLeg(
    val optionType: OptionType,
    val strikePrice: Double,
    val position: LegPosition,
    val quantity: Int,
    val premium: Double,
    val delta: Double = 0.0,
    val gamma: Double = 0.0,
    val theta: Double = 0.0,
    val vega: Double = 0.0
) {
    val greeksDisplay: String
        get() = "Δ: ${String.format("%.2f", delta)}, Γ: ${String.format("%.4f", gamma)}"
}

/**
 * Net Greeks for a strategy
 */
data class NetGreeks(
    val delta: Double,
    val gamma: Double,
    val theta: Double,
    val vega: Double,
    val rho: Double = 0.0
) {
    val isDeltaNeutral: Boolean get() = kotlin.math.abs(delta) < 0.1
    val isPositiveTheta: Boolean get() = theta > 0
    val isNegativeVega: Boolean get() = vega < 0
}

/**
 * Strategy recommendation with full analysis
 */
data class StrategyRecommendation(
    val id: String,
    val strategyType: StrategyType,
    val symbol: String,
    val expiry: String,
    val legs: List<RecommendedLeg>,
    val netPremium: Double, // Positive = credit, Negative = debit
    val maxProfit: Double,
    val maxLoss: Double,
    val breakevens: List<Double>,
    val probabilityOfProfit: Double, // 0.0 to 1.0
    val netGreeks: NetGreeks,
    val score: Int, // 0-100
    val marketConditionFit: String,
    val riskLevel: RiskLevel,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isCredit: Boolean get() = netPremium > 0
    val isDebit: Boolean get() = netPremium < 0
    val riskRewardRatio: Double get() = if (maxLoss > 0) maxProfit / maxLoss else 0.0

    val breakevenDisplay: String
        get() = breakevens.joinToString(", ") { String.format("%.2f", it) }
}

/**
 * IV Skew type
 */
enum class SkewType(val displayName: String) {
    PUT_SKEW("Put Skew"),
    CALL_SKEW("Call Skew"),
    FLAT("Flat"),
    SMILE("Volatility Smile")
}

/**
 * IV Skew analysis
 */
data class IVSkewAnalysis(
    val symbol: String,
    val expiry: String,
    val atmIV: Double,
    val callSkew: Double, // Difference from ATM for OTM calls
    val putSkew: Double,  // Difference from ATM for OTM puts
    val skewType: SkewType,
    val interpretation: String,
    val tradingImplication: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val skewMagnitude: Double get() = kotlin.math.abs(putSkew - callSkew)
    val isSignificant: Boolean get() = skewMagnitude > 5.0 // More than 5% difference
}

/**
 * Smart money signal types
 */
enum class SmartMoneySignalType(val displayName: String, val icon: String) {
    LARGE_OI_BUILD("Large OI Build-up", "📈"),
    LARGE_OI_UNWIND("Large OI Unwind", "📉"),
    VOLUME_SPIKE("Volume Spike", "⚡"),
    BLOCK_TRADE("Block Trade", "🏦"),
    PUT_WRITING("Significant Put Writing", "✍️"),
    CALL_WRITING("Significant Call Writing", "✍️"),
    ACCUMULATION("Accumulation Pattern", "🎯"),
    DISTRIBUTION("Distribution Pattern", "📤")
}

/**
 * Smart money activity signal
 */
data class SmartMoneySignal(
    val id: String,
    val strikePrice: Double,
    val optionType: OptionType,
    val signalType: SmartMoneySignalType,
    val oiChange: Long,
    val volumeSpike: Double, // Multiplier vs average
    val interpretation: String,
    val confidence: ConfidenceLevel,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isStrongSignal: Boolean get() = confidence == ConfidenceLevel.HIGH && volumeSpike > 3.0
}

/**
 * Heatmap zone type
 */
enum class HeatmapZoneType(val displayName: String, val color: Long) {
    STRONG_SUPPORT("Strong Support", 0xFF22C55E),
    WEAK_SUPPORT("Weak Support", 0xFF86EFAC),
    NEUTRAL("Neutral", 0xFF6B7280),
    WEAK_RESISTANCE("Weak Resistance", 0xFFFCA5A5),
    STRONG_RESISTANCE("Strong Resistance", 0xFFEF4444),
    MAX_PAIN("Max Pain Zone", 0xFF8B5CF6)
}

/**
 * Heatmap cell data
 */
data class HeatmapCell(
    val strikePrice: Double,
    val callOI: Long,
    val putOI: Long,
    val callOIChange: Long,
    val putOIChange: Long,
    val intensity: Double, // 0.0 to 1.0
    val zoneType: HeatmapZoneType
) {
    val totalOI: Long get() = callOI + putOI
    val netOIChange: Long get() = callOIChange + putOIChange
    val pcrAtStrike: Double get() = if (callOI > 0) putOI.toDouble() / callOI else 0.0
}

/**
 * Options heatmap data
 */
data class OptionsHeatmapData(
    val symbol: String,
    val spotPrice: Double,
    val cells: List<HeatmapCell>,
    val strongSupports: List<Double>,
    val strongResistances: List<Double>,
    val maxPainStrike: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    val immediateSupportLevel: Double?
        get() = strongSupports.filter { it < spotPrice }.maxOrNull()

    val immediateResistanceLevel: Double?
        get() = strongResistances.filter { it > spotPrice }.minOrNull()
}

/**
 * Market regime classification
 */
enum class MarketRegime(val displayName: String, val description: String) {
    TRENDING_BULLISH("Bullish Trend", "Market shows sustained upward momentum"),
    TRENDING_BEARISH("Bearish Trend", "Market shows sustained downward momentum"),
    RANGE_BOUND("Range Bound", "Market trading within defined support/resistance"),
    HIGH_VOLATILITY("High Volatility", "Elevated volatility, expect large swings"),
    LOW_VOLATILITY("Low Volatility", "Compressed volatility, potential breakout ahead")
}

/**
 * Enhanced AI analysis result with all new features
 */
data class EnhancedAIAnalysisResult(
    val baseAnalysis: AIAnalysisResult,
    val greeksRecommendations: List<GreeksRecommendation>,
    val strategyRecommendations: List<StrategyRecommendation>,
    val ivSkewAnalysis: IVSkewAnalysis?,
    val smartMoneySignals: List<SmartMoneySignal>,
    val heatmapData: OptionsHeatmapData?,
    val marketRegime: MarketRegime,
    val timestamp: Long = System.currentTimeMillis()
) {
    val hasActionableSignals: Boolean
        get() = smartMoneySignals.any { it.isStrongSignal } ||
                greeksRecommendations.any { it.severity == RecommendationSeverity.CRITICAL }

    val topStrategy: StrategyRecommendation?
        get() = strategyRecommendations.maxByOrNull { it.score }

    val highPriorityRecommendations: List<GreeksRecommendation>
        get() = greeksRecommendations.filter { it.severity != RecommendationSeverity.INFO }
}
