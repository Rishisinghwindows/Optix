package com.optix.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.optix.app.domain.model.AIPreferences
import com.optix.app.domain.model.RiskProfile
import com.optix.app.domain.model.StrategyType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_preferences"
)

/**
 * DataStore for AI-related user preferences
 */
@Singleton
class AIPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // Preference keys
        private val RISK_PROFILE = stringPreferencesKey("risk_profile")
        private val MIN_CONFIDENCE_THRESHOLD = intPreferencesKey("min_confidence_threshold")
        private val PERSONALIZED_CONFIDENCE_BOOST = booleanPreferencesKey("personalized_confidence_boost")
        private val ENABLE_WATCHLIST_PRIORITIZATION = booleanPreferencesKey("enable_watchlist_prioritization")
        private val ENABLE_HISTORY_BASED_FILTERING = booleanPreferencesKey("enable_history_based_filtering")
        private val NOTIFY_ON_HIGH_CONFIDENCE = booleanPreferencesKey("notify_on_high_confidence")
        private val HIGH_CONFIDENCE_THRESHOLD = intPreferencesKey("high_confidence_threshold")
        private val PREFERRED_SYMBOLS = stringSetPreferencesKey("preferred_symbols")
        private val PREFERRED_EXPIRIES = stringSetPreferencesKey("preferred_expiries")
        private val MAX_DAYS_TO_EXPIRY = intPreferencesKey("max_days_to_expiry")
        private val MIN_DAYS_TO_EXPIRY = intPreferencesKey("min_days_to_expiry")
        private val PREFERRED_STRATEGIES = stringSetPreferencesKey("preferred_strategies")
        private val EXCLUDED_STRATEGIES = stringSetPreferencesKey("excluded_strategies")

        // Professional Trading Threshold keys
        private val MIN_RISK_REWARD_RATIO = doublePreferencesKey("min_risk_reward_ratio")
        private val MIN_DELTA = doublePreferencesKey("min_delta")
        private val MAX_DELTA = doublePreferencesKey("max_delta")
        private val MIN_LIQUIDITY = intPreferencesKey("min_liquidity")
        private val MAX_BID_ASK_SPREAD_PERCENT = doublePreferencesKey("max_bid_ask_spread_percent")
        private val MIN_OPEN_INTEREST = intPreferencesKey("min_open_interest")

        // Defaults
        private val DEFAULT_RISK_PROFILE = RiskProfile.MODERATE
        private const val DEFAULT_MIN_CONFIDENCE = 50 // Tightened from 60
        private const val DEFAULT_HIGH_CONFIDENCE = 80
        private val DEFAULT_SYMBOLS = setOf("NIFTY", "BANKNIFTY")
        private const val DEFAULT_MAX_DTE = 30
        private const val DEFAULT_MIN_DTE = 0 // Allow 0DTE for intraday trading

        // Professional Trading Defaults
        private const val DEFAULT_MIN_RISK_REWARD = 1.5
        private const val DEFAULT_MIN_DELTA = 0.20
        private const val DEFAULT_MAX_DELTA = 0.80
        private const val DEFAULT_MIN_LIQUIDITY = 500
        private const val DEFAULT_MAX_BID_ASK_SPREAD = 5.0
        private const val DEFAULT_MIN_OPEN_INTEREST = 1000
    }

    /**
     * Get AI preferences as a Flow
     */
    val aiPreferences: Flow<AIPreferences> = context.aiPreferencesDataStore.data.map { prefs ->
        AIPreferences(
            riskProfile = prefs[RISK_PROFILE]?.let { RiskProfile.valueOf(it) } ?: DEFAULT_RISK_PROFILE,
            minConfidenceThreshold = prefs[MIN_CONFIDENCE_THRESHOLD] ?: DEFAULT_MIN_CONFIDENCE,
            personalizedConfidenceBoost = prefs[PERSONALIZED_CONFIDENCE_BOOST] ?: true,
            enableWatchlistPrioritization = prefs[ENABLE_WATCHLIST_PRIORITIZATION] ?: true,
            enableHistoryBasedFiltering = prefs[ENABLE_HISTORY_BASED_FILTERING] ?: true,
            notifyOnHighConfidence = prefs[NOTIFY_ON_HIGH_CONFIDENCE] ?: true,
            highConfidenceThreshold = prefs[HIGH_CONFIDENCE_THRESHOLD] ?: DEFAULT_HIGH_CONFIDENCE,
            preferredSymbols = prefs[PREFERRED_SYMBOLS] ?: DEFAULT_SYMBOLS,
            preferredExpiries = prefs[PREFERRED_EXPIRIES] ?: emptySet(),
            maxDaysToExpiry = prefs[MAX_DAYS_TO_EXPIRY] ?: DEFAULT_MAX_DTE,
            minDaysToExpiry = prefs[MIN_DAYS_TO_EXPIRY] ?: DEFAULT_MIN_DTE,
            preferredStrategies = prefs[PREFERRED_STRATEGIES]
                ?.mapNotNull { runCatching { StrategyType.valueOf(it) }.getOrNull() }
                ?.toSet() ?: emptySet(),
            excludedStrategies = prefs[EXCLUDED_STRATEGIES]
                ?.mapNotNull { runCatching { StrategyType.valueOf(it) }.getOrNull() }
                ?.toSet() ?: emptySet(),
            // Professional Trading Thresholds
            minRiskRewardRatio = prefs[MIN_RISK_REWARD_RATIO] ?: DEFAULT_MIN_RISK_REWARD,
            minDelta = prefs[MIN_DELTA] ?: DEFAULT_MIN_DELTA,
            maxDelta = prefs[MAX_DELTA] ?: DEFAULT_MAX_DELTA,
            minLiquidity = prefs[MIN_LIQUIDITY] ?: DEFAULT_MIN_LIQUIDITY,
            maxBidAskSpreadPercent = prefs[MAX_BID_ASK_SPREAD_PERCENT] ?: DEFAULT_MAX_BID_ASK_SPREAD,
            minOpenInterest = prefs[MIN_OPEN_INTEREST] ?: DEFAULT_MIN_OPEN_INTEREST
        )
    }

    /**
     * Update all AI preferences
     */
    suspend fun updatePreferences(preferences: AIPreferences) {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs[RISK_PROFILE] = preferences.riskProfile.name
            prefs[MIN_CONFIDENCE_THRESHOLD] = preferences.minConfidenceThreshold
            prefs[PERSONALIZED_CONFIDENCE_BOOST] = preferences.personalizedConfidenceBoost
            prefs[ENABLE_WATCHLIST_PRIORITIZATION] = preferences.enableWatchlistPrioritization
            prefs[ENABLE_HISTORY_BASED_FILTERING] = preferences.enableHistoryBasedFiltering
            prefs[NOTIFY_ON_HIGH_CONFIDENCE] = preferences.notifyOnHighConfidence
            prefs[HIGH_CONFIDENCE_THRESHOLD] = preferences.highConfidenceThreshold
            prefs[PREFERRED_SYMBOLS] = preferences.preferredSymbols
            prefs[PREFERRED_EXPIRIES] = preferences.preferredExpiries
            prefs[MAX_DAYS_TO_EXPIRY] = preferences.maxDaysToExpiry
            prefs[MIN_DAYS_TO_EXPIRY] = preferences.minDaysToExpiry
            prefs[PREFERRED_STRATEGIES] = preferences.preferredStrategies.map { it.name }.toSet()
            prefs[EXCLUDED_STRATEGIES] = preferences.excludedStrategies.map { it.name }.toSet()
            // Professional Trading Thresholds
            prefs[MIN_RISK_REWARD_RATIO] = preferences.minRiskRewardRatio
            prefs[MIN_DELTA] = preferences.minDelta
            prefs[MAX_DELTA] = preferences.maxDelta
            prefs[MIN_LIQUIDITY] = preferences.minLiquidity
            prefs[MAX_BID_ASK_SPREAD_PERCENT] = preferences.maxBidAskSpreadPercent
            prefs[MIN_OPEN_INTEREST] = preferences.minOpenInterest
        }
    }

    /**
     * Update risk profile
     */
    suspend fun updateRiskProfile(riskProfile: RiskProfile) {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs[RISK_PROFILE] = riskProfile.name
        }
    }

    /**
     * Update minimum confidence threshold
     */
    suspend fun updateMinConfidenceThreshold(threshold: Int) {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs[MIN_CONFIDENCE_THRESHOLD] = threshold.coerceIn(0, 100)
        }
    }

    /**
     * Toggle personalized confidence boost
     */
    suspend fun togglePersonalizedBoost(enabled: Boolean) {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs[PERSONALIZED_CONFIDENCE_BOOST] = enabled
        }
    }

    /**
     * Toggle watchlist prioritization
     */
    suspend fun toggleWatchlistPrioritization(enabled: Boolean) {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs[ENABLE_WATCHLIST_PRIORITIZATION] = enabled
        }
    }

    /**
     * Toggle history-based filtering
     */
    suspend fun toggleHistoryBasedFiltering(enabled: Boolean) {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs[ENABLE_HISTORY_BASED_FILTERING] = enabled
        }
    }

    /**
     * Toggle high confidence notifications
     */
    suspend fun toggleHighConfidenceNotifications(enabled: Boolean) {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs[NOTIFY_ON_HIGH_CONFIDENCE] = enabled
        }
    }

    /**
     * Update preferred symbols
     */
    suspend fun updatePreferredSymbols(symbols: Set<String>) {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs[PREFERRED_SYMBOLS] = symbols
        }
    }

    /**
     * Add symbol to preferred list
     */
    suspend fun addPreferredSymbol(symbol: String) {
        context.aiPreferencesDataStore.edit { prefs ->
            val current = prefs[PREFERRED_SYMBOLS] ?: DEFAULT_SYMBOLS
            prefs[PREFERRED_SYMBOLS] = current + symbol
        }
    }

    /**
     * Remove symbol from preferred list
     */
    suspend fun removePreferredSymbol(symbol: String) {
        context.aiPreferencesDataStore.edit { prefs ->
            val current = prefs[PREFERRED_SYMBOLS] ?: DEFAULT_SYMBOLS
            prefs[PREFERRED_SYMBOLS] = current - symbol
        }
    }

    /**
     * Update max days to expiry
     */
    suspend fun updateMaxDaysToExpiry(days: Int) {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs[MAX_DAYS_TO_EXPIRY] = days.coerceIn(1, 90)
        }
    }

    /**
     * Update preferred strategies
     */
    suspend fun updatePreferredStrategies(strategies: Set<StrategyType>) {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs[PREFERRED_STRATEGIES] = strategies.map { it.name }.toSet()
        }
    }

    /**
     * Update excluded strategies
     */
    suspend fun updateExcludedStrategies(strategies: Set<StrategyType>) {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs[EXCLUDED_STRATEGIES] = strategies.map { it.name }.toSet()
        }
    }

    /**
     * Reset all preferences to defaults
     */
    suspend fun resetToDefaults() {
        context.aiPreferencesDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
