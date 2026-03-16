package com.optix.app.domain.repository

import com.optix.app.core.util.Resource
import com.optix.app.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for AI analysis
 */
interface AIAnalysisRepository {
    /**
     * Get AI insights for a symbol
     */
    suspend fun getAIInsights(
        symbol: String,
        expiry: String? = null
    ): Flow<Resource<AIAnalysisResult>>

    /**
     * Analyze a specific option
     */
    suspend fun analyzeOption(
        symbol: String,
        strikePrice: Double,
        optionType: OptionType,
        expiry: String,
        spotPrice: Double
    ): Flow<Resource<OptionScore>>

    /**
     * Get OI analysis
     */
    suspend fun getOIAnalysis(
        symbol: String,
        expiry: String? = null
    ): Flow<Resource<OIAnalysisResult>>

    // ============================
    // Enhanced AI Analysis Methods
    // ============================

    /**
     * Get enhanced AI analysis with Greeks recommendations, strategy suggestions,
     * IV skew analysis, smart money signals, and heatmap data
     */
    suspend fun getEnhancedAIInsights(
        symbol: String,
        expiry: String? = null,
        optionChain: OptionChain? = null
    ): Flow<Resource<EnhancedAIAnalysisResult>>

    /**
     * Get strategy recommendations based on current market conditions
     */
    suspend fun getStrategyRecommendations(
        symbol: String,
        expiry: String,
        optionChain: OptionChain,
        marketSentiment: MarketSentiment,
        ivPercentile: Double
    ): Flow<Resource<List<StrategyRecommendation>>>

    /**
     * Get Greeks-based recommendations for an option
     */
    suspend fun getGreeksRecommendations(
        optionData: OptionData,
        spotPrice: Double,
        daysToExpiry: Int
    ): Flow<Resource<List<GreeksRecommendation>>>

    /**
     * Get smart money signals from option chain analysis
     */
    suspend fun getSmartMoneySignals(
        optionChain: OptionChain,
        previousChain: OptionChain? = null
    ): Flow<Resource<List<SmartMoneySignal>>>

    /**
     * Get IV skew analysis
     */
    suspend fun getIVSkewAnalysis(
        optionChain: OptionChain
    ): Flow<Resource<IVSkewAnalysis>>

    /**
     * Get AI insights using LOCAL analysis on the option chain data.
     * This ensures consistent results with iOS which also does local analysis.
     *
     * @param intradayChangePct Percentage change from previous close (e.g., 0.5 for +0.5%)
     *                          This is the PRIMARY factor for determining market direction,
     *                          matching iOS behavior.
     */
    suspend fun getLocalAIInsights(
        symbol: String,
        optionChain: OptionChain,
        pcr: Double,
        maxPainStrike: Double,
        indiaVix: Double? = null,
        intradayChangePct: Double? = null
    ): Flow<Resource<AIAnalysisResult>>
}
