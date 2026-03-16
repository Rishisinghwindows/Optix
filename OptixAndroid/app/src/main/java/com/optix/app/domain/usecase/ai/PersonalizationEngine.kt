package com.optix.app.domain.usecase.ai

import com.optix.app.data.local.datastore.AIPreferencesDataStore
import com.optix.app.domain.model.AIPreferences
import com.optix.app.domain.model.AITradeSuggestion
import com.optix.app.domain.model.ConfidenceLevel
import com.optix.app.domain.model.HedgeRecommendation
import com.optix.app.domain.model.HedgeType
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.PaperPosition
import com.optix.app.domain.model.PersonalizedAITradeSuggestion
import com.optix.app.domain.model.PositionSizeRecommendation
import com.optix.app.domain.model.RiskProfile
import com.optix.app.domain.model.TradeDirection
import com.optix.app.domain.model.TradingPatternAnalysis
import com.optix.app.domain.model.WatchlistItem
import com.optix.app.domain.repository.PaperTradingRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Engine for personalizing AI trade suggestions based on user preferences,
 * trading history, and watchlist.
 */
@Singleton
class PersonalizationEngine @Inject constructor(
    private val preferencesDataStore: AIPreferencesDataStore,
    private val paperTradingRepository: PaperTradingRepository
) {

    companion object {
        // Scoring weights
        private const val WATCHLIST_MATCH_BOOST = 5
        private const val RISK_PROFILE_MATCH_BOOST = 5
        private const val HISTORY_PATTERN_BOOST = 10
        private const val PREFERRED_SYMBOL_BOOST = 3
        private const val WIN_RATE_CONFIDENCE_BOOST = 5

        // Position sizing constants
        private const val CONSERVATIVE_RISK_PERCENT = 0.01 // 1% per trade
        private const val MODERATE_RISK_PERCENT = 0.02 // 2% per trade
        private const val AGGRESSIVE_RISK_PERCENT = 0.05 // 5% per trade

        // Cache duration
        private const val PATTERN_CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour

        // Professional Trading Thresholds (hardcoded to ensure they always apply)
        private const val MIN_RISK_REWARD_RATIO = 1.5
        private const val MIN_SCORE_THRESHOLD = 50
        private const val MAX_UNREALISTIC_PERCENT = 500.0
    }

    private var cachedPatternAnalysis: TradingPatternAnalysis? = null
    private var patternCacheTimestamp: Long = 0

    /**
     * Personalize a list of AI suggestions
     */
    suspend fun personalize(
        suggestions: List<AITradeSuggestion>,
        watchlist: List<WatchlistItem>,
        openPositions: List<PaperPosition>
    ): List<PersonalizedAITradeSuggestion> {
        val preferences = preferencesDataStore.aiPreferences.first()
        val patternAnalysis = getOrUpdatePatternAnalysis()

        return suggestions
            .filter { filterByPreferences(it, preferences) }
            .map { suggestion ->
                personalizeSuggestion(
                    suggestion = suggestion,
                    preferences = preferences,
                    watchlist = watchlist,
                    openPositions = openPositions,
                    patternAnalysis = patternAnalysis
                )
            }
            .sortedByDescending { it.totalScore }
    }

    /**
     * Filter suggestions based on user preferences and professional trading thresholds
     */
    private fun filterByPreferences(
        suggestion: AITradeSuggestion,
        preferences: AIPreferences
    ): Boolean {
        // Log for debugging
        android.util.Log.d("PersonalizationEngine", "Filtering suggestion: strike=${suggestion.strikePrice}, " +
            "entry=${suggestion.entryPrice}, target=${suggestion.targetPrice}, sl=${suggestion.stopLoss}, " +
            "rr=${suggestion.riskReward}, action=${suggestion.action}, score=${suggestion.score}")

        // Filter by minimum confidence/score (use hardcoded threshold)
        if (suggestion.score < MIN_SCORE_THRESHOLD) {
            android.util.Log.d("PersonalizationEngine", "REJECTED: score ${suggestion.score} < $MIN_SCORE_THRESHOLD")
            return false
        }

        // Filter by preferred symbols if set
        if (preferences.preferredSymbols.isNotEmpty() &&
            suggestion.symbol !in preferences.preferredSymbols) {
            android.util.Log.d("PersonalizationEngine", "REJECTED: symbol not in preferred list")
            return false
        }

        // Professional Trading Filters (HARDCODED - cannot be bypassed)

        // 1. Risk-Reward Ratio filter - minimum 1.5:1
        if (suggestion.riskReward < MIN_RISK_REWARD_RATIO) {
            android.util.Log.d("PersonalizationEngine", "REJECTED: R:R ${suggestion.riskReward} < $MIN_RISK_REWARD_RATIO")
            return false
        }

        // 2. Validate target/stoploss make sense for the trade direction
        // For BUY: target should be > entry, stoploss should be < entry
        // For SELL: target should be < entry, stoploss should be > entry
        val isValidTargetStopLoss = when (suggestion.action) {
            TradeDirection.STRONG_BUY, TradeDirection.BUY -> {
                val valid = suggestion.targetPrice > suggestion.entryPrice &&
                    suggestion.stopLoss < suggestion.entryPrice
                if (!valid) {
                    android.util.Log.d("PersonalizationEngine", "REJECTED: Invalid BUY target/sl - " +
                        "target(${suggestion.targetPrice}) should be > entry(${suggestion.entryPrice}), " +
                        "sl(${suggestion.stopLoss}) should be < entry")
                }
                valid
            }
            TradeDirection.STRONG_SELL, TradeDirection.SELL -> {
                val valid = suggestion.targetPrice < suggestion.entryPrice &&
                    suggestion.stopLoss > suggestion.entryPrice
                if (!valid) {
                    android.util.Log.d("PersonalizationEngine", "REJECTED: Invalid SELL target/sl")
                }
                valid
            }
            TradeDirection.HOLD -> {
                android.util.Log.d("PersonalizationEngine", "REJECTED: action is HOLD")
                false // Reject HOLD suggestions - they shouldn't be trade suggestions
            }
        }
        if (!isValidTargetStopLoss) {
            return false
        }

        // 3. Filter out suggestions with unrealistic profit/loss percentages
        val profitPercent = abs((suggestion.targetPrice - suggestion.entryPrice) / suggestion.entryPrice * 100)
        val lossPercent = abs((suggestion.entryPrice - suggestion.stopLoss) / suggestion.entryPrice * 100)
        if (profitPercent > MAX_UNREALISTIC_PERCENT || lossPercent > MAX_UNREALISTIC_PERCENT) {
            android.util.Log.d("PersonalizationEngine", "REJECTED: unrealistic profit($profitPercent%) or loss($lossPercent%)")
            return false
        }

        android.util.Log.d("PersonalizationEngine", "ACCEPTED: suggestion passed all filters")
        return true
    }

    /**
     * Personalize a single suggestion
     */
    private suspend fun personalizeSuggestion(
        suggestion: AITradeSuggestion,
        preferences: AIPreferences,
        watchlist: List<WatchlistItem>,
        openPositions: List<PaperPosition>,
        patternAnalysis: TradingPatternAnalysis?
    ): PersonalizedAITradeSuggestion {
        val relevanceReasons = mutableListOf<String>()
        var personalizationScore = 0

        // Check watchlist match
        val matchesWatchlist = if (preferences.enableWatchlistPrioritization) {
            val isMatch = watchlist.any { item ->
                item.symbol == suggestion.symbol &&
                        (item.strikePrice == null || item.strikePrice == suggestion.strikePrice) &&
                        (item.optionType == null || item.optionType == suggestion.optionType)
            }
            if (isMatch) {
                personalizationScore += WATCHLIST_MATCH_BOOST
                relevanceReasons.add("Matches your watchlist")
            }
            isMatch
        } else false

        // Check risk profile match
        val matchesRiskProfile = checkRiskProfileMatch(suggestion, preferences.riskProfile)
        if (matchesRiskProfile) {
            personalizationScore += RISK_PROFILE_MATCH_BOOST
            relevanceReasons.add("Fits your ${preferences.riskProfile.displayName} risk profile")
        }

        // Check preferred symbol
        if (suggestion.symbol in preferences.preferredSymbols) {
            personalizationScore += PREFERRED_SYMBOL_BOOST
            relevanceReasons.add("Preferred symbol")
        }

        // History-based confidence boost
        var historyBasedConfidence = 0.5
        if (preferences.enableHistoryBasedFiltering && patternAnalysis?.hasEnoughData == true) {
            val (historyBoost, historyConfidence) = calculateHistoryBasedBoost(
                suggestion, patternAnalysis
            )
            personalizationScore += historyBoost
            historyBasedConfidence = historyConfidence

            if (historyBoost > 5) {
                relevanceReasons.add("Similar to your profitable trades")
            }
        }

        // Calculate position size
        val positionSize = calculatePositionSize(
            suggestion = suggestion,
            preferences = preferences,
            openPositions = openPositions
        )

        // Generate hedge recommendation
        val hedgeRecommendation = generateHedgeRecommendation(
            suggestion = suggestion,
            openPositions = openPositions,
            preferences = preferences
        )

        return PersonalizedAITradeSuggestion(
            baseSuggestion = suggestion,
            personalizationScore = personalizationScore,
            matchesWatchlist = matchesWatchlist,
            matchesRiskProfile = matchesRiskProfile,
            historyBasedConfidence = historyBasedConfidence,
            recommendedPositionSize = positionSize.recommendedLots,
            hedgeRecommendation = hedgeRecommendation?.description,
            relevanceReasons = relevanceReasons
        )
    }

    /**
     * Check if suggestion matches user's risk profile
     */
    private fun checkRiskProfileMatch(
        suggestion: AITradeSuggestion,
        riskProfile: RiskProfile
    ): Boolean {
        return when (riskProfile) {
            RiskProfile.CONSERVATIVE -> {
                // Conservative prefers lower risk, higher confidence
                suggestion.confidence == ConfidenceLevel.HIGH &&
                        suggestion.riskReward >= 1.5
            }
            RiskProfile.MODERATE -> {
                // Moderate accepts medium-high confidence
                suggestion.confidence != ConfidenceLevel.LOW &&
                        suggestion.riskReward >= 1.0
            }
            RiskProfile.AGGRESSIVE -> {
                // Aggressive accepts all, prefers higher potential return
                suggestion.potentialReturn > 10
            }
        }
    }

    /**
     * Calculate boost based on trading history patterns
     */
    private fun calculateHistoryBasedBoost(
        suggestion: AITradeSuggestion,
        patternAnalysis: TradingPatternAnalysis
    ): Pair<Int, Double> {
        var boost = 0
        var confidenceMultiplier = 1.0

        // Match preferred option type
        if (patternAnalysis.preferredOptionType == suggestion.optionType) {
            boost += 3
            confidenceMultiplier *= 1.1
        }

        // Match preferred direction
        if (patternAnalysis.preferredDirection == suggestion.action) {
            boost += 2
            confidenceMultiplier *= 1.05
        }

        // Boost based on historical win rate for this confidence level
        val historicalWinRate = patternAnalysis.winRateByConfidence[suggestion.confidence] ?: 0.5
        if (historicalWinRate > 0.6) {
            boost += WIN_RATE_CONFIDENCE_BOOST
            confidenceMultiplier *= (1 + (historicalWinRate - 0.5))
        }

        val finalConfidence = (0.5 * confidenceMultiplier).coerceIn(0.0, 1.0)
        return Pair(boost.coerceAtMost(HISTORY_PATTERN_BOOST), finalConfidence)
    }

    /**
     * Calculate recommended position size
     */
    private fun calculatePositionSize(
        suggestion: AITradeSuggestion,
        preferences: AIPreferences,
        openPositions: List<PaperPosition>
    ): PositionSizeRecommendation {
        val riskPercent = when (preferences.riskProfile) {
            RiskProfile.CONSERVATIVE -> CONSERVATIVE_RISK_PERCENT
            RiskProfile.MODERATE -> MODERATE_RISK_PERCENT
            RiskProfile.AGGRESSIVE -> AGGRESSIVE_RISK_PERCENT
        }

        // Get estimated account size from open positions
        // In a real implementation, this would come from the account
        val accountSize = 1000000.0 // Default 10L INR

        val riskAmount = accountSize * riskPercent
        val maxLossPerLot = suggestion.maxLoss.coerceAtLeast(1.0)

        val recommendedLots = (riskAmount / maxLossPerLot).toInt().coerceAtLeast(1)

        // Reduce if already have many open positions
        val positionPenalty = (openPositions.size / 5).coerceAtMost(recommendedLots - 1)
        val adjustedLots = (recommendedLots - positionPenalty).coerceAtLeast(1)

        val maxLots = when (preferences.riskProfile) {
            RiskProfile.CONSERVATIVE -> 2
            RiskProfile.MODERATE -> 5
            RiskProfile.AGGRESSIVE -> 10
        }

        return PositionSizeRecommendation(
            recommendedLots = adjustedLots.coerceAtMost(maxLots),
            maxLots = maxLots,
            riskPerTrade = riskPercent * 100,
            reasoning = "Based on ${preferences.riskProfile.displayName} profile, " +
                    "risking ${String.format("%.1f", riskPercent * 100)}% per trade"
        )
    }

    /**
     * Generate hedge recommendation based on existing positions
     */
    private fun generateHedgeRecommendation(
        suggestion: AITradeSuggestion,
        openPositions: List<PaperPosition>,
        preferences: AIPreferences
    ): HedgeRecommendation? {
        // Find related positions
        val relatedPositions = openPositions.filter {
            it.symbol == suggestion.symbol
        }

        if (relatedPositions.isEmpty()) {
            return null
        }

        // Check if this trade would create a hedge naturally
        val hasLongCalls = relatedPositions.any {
            it.optionType.uppercase() == "CALL" && it.quantity > 0
        }
        val hasLongPuts = relatedPositions.any {
            it.optionType.uppercase() == "PUT" && it.quantity > 0
        }
        val hasShortCalls = relatedPositions.any {
            it.optionType.uppercase() == "CALL" && it.quantity < 0
        }
        val hasShortPuts = relatedPositions.any {
            it.optionType.uppercase() == "PUT" && it.quantity < 0
        }

        // Suggest hedge based on existing exposure
        return when {
            hasLongCalls && suggestion.optionType == OptionType.PUT && suggestion.action == TradeDirection.BUY -> {
                HedgeRecommendation(
                    hedgeType = HedgeType.PROTECTIVE_PUT,
                    description = "This creates a protective put for your long call position",
                    suggestedStrike = suggestion.strikePrice,
                    suggestedExpiry = suggestion.expiry,
                    estimatedCost = suggestion.entryPrice
                )
            }
            hasLongPuts && suggestion.optionType == OptionType.CALL && suggestion.action == TradeDirection.SELL -> {
                HedgeRecommendation(
                    hedgeType = HedgeType.COLLAR,
                    description = "Combined with your long puts, this creates a collar strategy",
                    suggestedStrike = suggestion.strikePrice,
                    suggestedExpiry = suggestion.expiry,
                    estimatedCost = null
                )
            }
            preferences.riskProfile == RiskProfile.CONSERVATIVE && suggestion.action == TradeDirection.BUY -> {
                // Suggest converting to spread for conservative traders
                HedgeRecommendation(
                    hedgeType = HedgeType.SPREAD_CONVERSION,
                    description = "Consider selling a further OTM option to create a spread and reduce cost",
                    suggestedStrike = null,
                    suggestedExpiry = suggestion.expiry,
                    estimatedCost = null
                )
            }
            else -> null
        }
    }

    /**
     * Analyze trading patterns from paper trading history
     */
    suspend fun analyzeTradingPatterns(): TradingPatternAnalysis {
        val closedPositionsResult = paperTradingRepository.getClosedPositions()

        val closedPositions = when {
            closedPositionsResult is com.optix.app.core.util.Resource.Success ->
                closedPositionsResult.data ?: emptyList()
            else -> emptyList()
        }

        if (closedPositions.isEmpty()) {
            return TradingPatternAnalysis(
                preferredOptionType = null,
                preferredDirection = null,
                averageHoldingPeriodHours = 0.0,
                profitableTimeOfDay = emptyList(),
                preferredStrikeDistance = 0,
                winRateByConfidence = emptyMap(),
                totalTrades = 0,
                winningTrades = 0,
                averageProfitPercent = 0.0,
                averageLossPercent = 0.0
            )
        }

        // Analyze option type preference
        val callCount = closedPositions.count { it.optionType.uppercase() == "CALL" || it.optionType.uppercase() == "CE" }
        val putCount = closedPositions.count { it.optionType.uppercase() == "PUT" || it.optionType.uppercase() == "PE" }
        val preferredOptionType = if (callCount > putCount) OptionType.CALL else if (putCount > callCount) OptionType.PUT else null

        // Analyze direction preference
        val buyCount = closedPositions.count { it.quantity > 0 }
        val sellCount = closedPositions.count { it.quantity < 0 }
        val preferredDirection = if (buyCount > sellCount) TradeDirection.BUY else if (sellCount > buyCount) TradeDirection.SELL else null

        // Calculate win rate
        val winningTrades = closedPositions.count { it.pnl > 0 }
        val totalTrades = closedPositions.size

        // Calculate average profit/loss
        val profits = closedPositions.filter { it.pnl > 0 }.map {
            if (it.entryPrice > 0) (it.pnl / (it.entryPrice * abs(it.quantity))) * 100 else 0.0
        }
        val losses = closedPositions.filter { it.pnl < 0 }.map {
            if (it.entryPrice > 0) (it.pnl / (it.entryPrice * abs(it.quantity))) * 100 else 0.0
        }

        return TradingPatternAnalysis(
            preferredOptionType = preferredOptionType,
            preferredDirection = preferredDirection,
            averageHoldingPeriodHours = 24.0, // Simplified
            profitableTimeOfDay = listOf(10, 11, 14, 15), // Market hours
            preferredStrikeDistance = 1,
            winRateByConfidence = mapOf(
                ConfidenceLevel.HIGH to if (totalTrades > 0) winningTrades.toDouble() / totalTrades else 0.5,
                ConfidenceLevel.MEDIUM to if (totalTrades > 0) winningTrades.toDouble() / totalTrades else 0.5,
                ConfidenceLevel.LOW to if (totalTrades > 0) winningTrades.toDouble() / totalTrades else 0.5
            ),
            totalTrades = totalTrades,
            winningTrades = winningTrades,
            averageProfitPercent = profits.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            averageLossPercent = losses.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        )
    }

    /**
     * Get cached pattern analysis or update if stale
     */
    private suspend fun getOrUpdatePatternAnalysis(): TradingPatternAnalysis? {
        val now = System.currentTimeMillis()
        if (cachedPatternAnalysis != null && now - patternCacheTimestamp < PATTERN_CACHE_DURATION_MS) {
            return cachedPatternAnalysis
        }

        cachedPatternAnalysis = analyzeTradingPatterns()
        patternCacheTimestamp = now
        return cachedPatternAnalysis
    }

    /**
     * Clear pattern analysis cache
     */
    fun clearCache() {
        cachedPatternAnalysis = null
        patternCacheTimestamp = 0
    }
}
