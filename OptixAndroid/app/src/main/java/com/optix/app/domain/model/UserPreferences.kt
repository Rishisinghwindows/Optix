package com.optix.app.domain.model

/**
 * User's risk profile for trading
 */
enum class RiskProfile(val displayName: String, val description: String) {
    CONSERVATIVE(
        displayName = "Conservative",
        description = "Prefer defined risk strategies, lower returns acceptable"
    ),
    MODERATE(
        displayName = "Moderate",
        description = "Balance between risk and reward, mix of strategies"
    ),
    AGGRESSIVE(
        displayName = "Aggressive",
        description = "Higher risk tolerance, directional plays, naked options acceptable"
    )
}

/**
 * AI analysis preferences for personalization
 */
data class AIPreferences(
    val riskProfile: RiskProfile = RiskProfile.MODERATE,
    val minConfidenceThreshold: Int = 50, // Minimum confidence score to show (0-100) - tightened from 60
    val personalizedConfidenceBoost: Boolean = true, // Boost confidence for matching patterns
    val enableWatchlistPrioritization: Boolean = true, // Prioritize watchlist symbols
    val enableHistoryBasedFiltering: Boolean = true, // Filter based on trading history
    val notifyOnHighConfidence: Boolean = true, // Push notification for high confidence
    val highConfidenceThreshold: Int = 80, // Threshold for high confidence notification
    val preferredSymbols: Set<String> = setOf("NIFTY", "BANKNIFTY"), // User's preferred symbols
    val preferredExpiries: Set<String> = emptySet(), // Preferred expiry types (weekly/monthly)
    val maxDaysToExpiry: Int = 30, // Max DTE for suggestions
    val minDaysToExpiry: Int = 0, // Min DTE - 0 allows 0DTE options for intraday trading
    val preferredStrategies: Set<StrategyType> = emptySet(), // Preferred strategy types
    val excludedStrategies: Set<StrategyType> = emptySet(), // Strategies to exclude

    // Professional Trading Thresholds
    val minRiskRewardRatio: Double = 1.5, // Minimum 1.5:1 R:R for quality trades
    val minDelta: Double = 0.20, // Avoid deep OTM (delta < 0.20)
    val maxDelta: Double = 0.80, // Avoid deep ITM (delta > 0.80)
    val minLiquidity: Int = 500, // Minimum volume for liquidity
    val maxBidAskSpreadPercent: Double = 5.0, // Max 5% bid-ask spread
    val minOpenInterest: Int = 1000 // Minimum OI for reliable data
)

/**
 * Personalized AI trade suggestion with additional context
 */
data class PersonalizedAITradeSuggestion(
    val baseSuggestion: AITradeSuggestion,
    val personalizationScore: Int, // Additional score boost (0-20)
    val matchesWatchlist: Boolean,
    val matchesRiskProfile: Boolean,
    val historyBasedConfidence: Double, // 0.0 to 1.0 based on similar past trades
    val recommendedPositionSize: Int, // Number of lots recommended
    val hedgeRecommendation: String?, // Hedge suggestion if applicable
    val relevanceReasons: List<String> // Why this is relevant to user
) {
    val totalScore: Int
        get() = (baseSuggestion.score + personalizationScore).coerceAtMost(100)

    val isHighlyPersonalized: Boolean
        get() = personalizationScore >= 10
}

/**
 * Trading pattern analysis from user's history
 */
data class TradingPatternAnalysis(
    val preferredOptionType: OptionType?, // Most traded option type
    val preferredDirection: TradeDirection?, // Most common direction
    val averageHoldingPeriodHours: Double, // Average time positions are held
    val profitableTimeOfDay: List<Int>, // Hours that have been profitable
    val preferredStrikeDistance: Int, // Preferred strikes from ATM
    val winRateByConfidence: Map<ConfidenceLevel, Double>, // Win rate grouped by confidence
    val totalTrades: Int,
    val winningTrades: Int,
    val averageProfitPercent: Double,
    val averageLossPercent: Double
) {
    val overallWinRate: Double
        get() = if (totalTrades > 0) winningTrades.toDouble() / totalTrades else 0.0

    val profitFactor: Double
        get() = if (averageLossPercent != 0.0) averageProfitPercent / kotlin.math.abs(averageLossPercent) else 0.0

    val hasEnoughData: Boolean
        get() = totalTrades >= 10
}

/**
 * Watchlist item
 */
data class WatchlistItem(
    val id: String,
    val symbol: String,
    val strikePrice: Double? = null,
    val optionType: OptionType? = null,
    val expiry: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val alertPriceTarget: Double? = null
) {
    val isFullOption: Boolean
        get() = strikePrice != null && optionType != null && expiry != null

    val displayName: String
        get() = if (isFullOption) {
            "$symbol $strikePrice ${optionType?.displayName} ($expiry)"
        } else {
            symbol
        }
}

/**
 * Hedge recommendation
 */
data class HedgeRecommendation(
    val hedgeType: HedgeType,
    val description: String,
    val suggestedStrike: Double?,
    val suggestedExpiry: String?,
    val estimatedCost: Double?
)

enum class HedgeType(val displayName: String) {
    PROTECTIVE_PUT("Protective Put"),
    COVERED_CALL("Covered Call"),
    COLLAR("Collar"),
    SPREAD_CONVERSION("Convert to Spread"),
    NONE("No Hedge Needed")
}

/**
 * Position size recommendation
 */
data class PositionSizeRecommendation(
    val recommendedLots: Int,
    val maxLots: Int,
    val riskPerTrade: Double, // Percentage of capital at risk
    val reasoning: String
)
