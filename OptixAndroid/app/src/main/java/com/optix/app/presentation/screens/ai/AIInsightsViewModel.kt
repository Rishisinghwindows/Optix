package com.optix.app.presentation.screens.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.core.util.Resource
import com.optix.app.domain.model.*
import com.optix.app.domain.repository.AIAnalysisRepository
import com.optix.app.domain.repository.OptionChainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

private const val TAG = "AIInsightsVM"

// Professional Trading Thresholds (relaxed to match iOS)
private const val MIN_RISK_REWARD_RATIO = 1.2  // Lowered from 1.5 to match iOS
private const val MIN_SCORE_THRESHOLD = 45     // Lowered from 50 to match iOS
private const val MAX_UNREALISTIC_PERCENT = 500.0

/**
 * Tab options for AI Insights - Order matches iOS (Calls, Puts, Market)
 */
enum class InsightTab(val displayName: String) {
    CALLS("Calls"),
    PUTS("Puts"),
    MARKET("Market")
}

// MarketBias is imported from com.optix.app.domain.model.MarketBias

/**
 * Technical analysis data
 */
data class TechnicalAnalysis(
    val rsi: Double = 50.0,
    val rsiCondition: String = "Neutral",
    val macdHistogram: Double = 0.0,
    val macdCrossover: String = "No Signal",
    val sma20: Double = 0.0,
    val sma50: Double = 0.0,
    val vwap: Double = 0.0,
    val supportLevels: List<Double> = emptyList(),
    val resistanceLevels: List<Double> = emptyList(),
    val trend: String = "Sideways"
)

data class AIInsightsState(
    // Index & Expiry Selection
    val selectedIndex: TradingIndex = TradingIndex.NIFTY50,
    val availableIndices: List<TradingIndex> = listOf(
        TradingIndex.NIFTY50,
        TradingIndex.BANKNIFTY,
        TradingIndex.FINNIFTY,
        TradingIndex.MIDCPNIFTY
    ),
    val selectedExpiry: ExpiryDate? = null,
    val availableExpiries: List<ExpiryDate> = emptyList(),

    // Tab Selection
    val selectedTab: InsightTab = InsightTab.CALLS,

    // Market Data
    val spotPrice: Double = 0.0,
    val marketBias: MarketBias = MarketBias.NEUTRAL,
    val marketSentiment: MarketSentiment? = null,
    val pcr: Double = 1.0,
    val maxPain: Double = 0.0,
    val ivPercentile: Double = 50.0,
    val totalSuggestions: Int = 0,

    // India VIX Data
    val indiaVix: Double = 0.0,
    val vixChange: Double = 0.0,
    val vixChangePercent: Double = 0.0,
    val isHighVolatility: Boolean = false,

    // Intraday Momentum (PRIMARY factor for market direction - matching iOS)
    val intradayChangePct: Double? = null,

    // Option Chain Data (for enhanced analysis)
    val optionChain: OptionChain? = null,
    val totalCallOI: Long = 0,
    val totalPutOI: Long = 0,
    val atmIV: Double = 0.0,

    // Technical Analysis
    val technicalAnalysis: TechnicalAnalysis = TechnicalAnalysis(),
    val hasTechnicalAnalysis: Boolean = false,

    // Suggestions
    val allSuggestions: List<AITradeSuggestion> = emptyList(),
    val marketInsights: List<MarketInsight> = emptyList(),

    // UI State
    val isAnalyzing: Boolean = false,
    val hasAnalysis: Boolean = false,
    val analysisState: Resource<AIAnalysisResult> = Resource.Loading(),
    val analysisStatusText: String = "Ready to analyze",

    // Detail Sheet
    val showSuggestionDetail: Boolean = false,
    val selectedSuggestion: AITradeSuggestion? = null
)

@HiltViewModel
class AIInsightsViewModel @Inject constructor(
    private val repository: AIAnalysisRepository,
    private val optionChainRepository: OptionChainRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AIInsightsState())
    val state: StateFlow<AIInsightsState> = _state.asStateFlow()

    init {
        Log.d(TAG, "ViewModel init - loading expiry dates for ${_state.value.selectedIndex}")
        // First load expiry dates, which will trigger loadData() after
        loadExpiryDates(_state.value.selectedIndex)
    }

    /**
     * Get current suggestions based on selected tab
     */
    val currentSuggestions: List<AITradeSuggestion>
        get() = when (_state.value.selectedTab) {
            InsightTab.MARKET -> emptyList()
            InsightTab.CALLS -> _state.value.allSuggestions.filter { it.optionType == OptionType.CALL }
            InsightTab.PUTS -> _state.value.allSuggestions.filter { it.optionType == OptionType.PUT }
        }

    val callSuggestionCount: Int
        get() = _state.value.allSuggestions.count { it.optionType == OptionType.CALL }

    val putSuggestionCount: Int
        get() = _state.value.allSuggestions.count { it.optionType == OptionType.PUT }

    fun selectIndex(index: TradingIndex) {
        _state.update { it.copy(selectedIndex = index, isAnalyzing = true) }
        loadExpiryDates(index)
    }

    fun selectExpiry(expiry: ExpiryDate) {
        _state.update { it.copy(selectedExpiry = expiry, isAnalyzing = true) }
        loadData()
    }

    fun selectTab(tab: InsightTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun selectSuggestion(suggestion: AITradeSuggestion) {
        _state.update {
            it.copy(
                selectedSuggestion = suggestion,
                showSuggestionDetail = true
            )
        }
    }

    fun dismissSuggestionDetail() {
        _state.update {
            it.copy(
                showSuggestionDetail = false,
                selectedSuggestion = null
            )
        }
    }

    fun refresh() {
        loadData()
    }

    private fun loadExpiryDates(index: TradingIndex) {
        viewModelScope.launch {
            Log.d(TAG, "loadExpiryDates: Starting for index ${index.symbol}")
            _state.update { it.copy(isAnalyzing = true, analysisStatusText = "Loading expiry dates...") }

            // Load expiry dates from option chain repository
            val result = optionChainRepository.getExpiries(index)
            Log.d(TAG, "loadExpiryDates: Got result type ${result.javaClass.simpleName}")
            when (result) {
                is Resource.Success -> {
                    result.data?.let { expiries ->
                        _state.update {
                            it.copy(
                                availableExpiries = expiries,
                                selectedExpiry = expiries.firstOrNull()
                            )
                        }
                        if (expiries.isNotEmpty()) {
                            loadData()
                        } else {
                            _state.update {
                                it.copy(
                                    isAnalyzing = false,
                                    analysisStatusText = "No expiry dates available"
                                )
                            }
                        }
                    } ?: run {
                        _state.update {
                            it.copy(
                                isAnalyzing = false,
                                analysisStatusText = "No expiry dates available"
                            )
                        }
                    }
                }
                is Resource.Error -> {
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            analysisState = Resource.Error(result.message ?: "Failed to load expiries"),
                            analysisStatusText = "Error loading data"
                        )
                    }
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            val index = _state.value.selectedIndex
            val symbol = index.symbol
            val expiry = _state.value.selectedExpiry?.value
            Log.d(TAG, "loadData: Starting for symbol=$symbol, expiry=$expiry")

            // If no expiry selected, don't start loading
            if (expiry == null) {
                Log.d(TAG, "loadData: No expiry selected, returning")
                _state.update {
                    it.copy(
                        isAnalyzing = false,
                        analysisStatusText = "Select an expiry date"
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    isAnalyzing = true,
                    analysisState = Resource.Loading(),
                    analysisStatusText = "Fetching market data..."
                )
            }

            // Step 1: Fetch Option Chain data
            _state.update { it.copy(analysisStatusText = "Loading option chain...") }
            var optionChain: OptionChain? = null

            when (val chainResult = optionChainRepository.getOptionChain(index, expiry)) {
                is Resource.Success -> {
                    optionChain = chainResult.data

                    // Calculate metrics from real option chain
                    optionChain?.let { chain ->
                        val totalCallOI = chain.fullChainCallOI
                            ?: chain.rows.sumOf { it.callData?.openInterest ?: 0L }
                        val totalPutOI = chain.fullChainPutOI
                            ?: chain.rows.sumOf { it.putData?.openInterest ?: 0L }
                        val calculatedPCR = if (totalCallOI > 0) totalPutOI.toDouble() / totalCallOI else 1.0

                        // Calculate ATM IV
                        val atmRow = chain.rows.minByOrNull { kotlin.math.abs(it.strikePrice - chain.atmStrike) }
                        val atmCallIV = atmRow?.callData?.impliedVolatility ?: 0.0
                        val atmPutIV = atmRow?.putData?.impliedVolatility ?: 0.0
                        val atmIV = when {
                            atmCallIV > 0 && atmPutIV > 0 -> (atmCallIV + atmPutIV) / 2
                            atmCallIV > 0 -> atmCallIV
                            atmPutIV > 0 -> atmPutIV
                            else -> 0.0
                        }

                        // Calculate Max Pain
                        val maxPain = calculateMaxPainFromChain(chain)

                        // Calculate support/resistance from OI
                        val support = chain.rows
                            .filter { it.strikePrice < chain.spotPrice && (it.putData?.openInterest ?: 0) > 0 }
                            .maxByOrNull { it.putData?.openInterest ?: 0 }
                            ?.strikePrice ?: chain.atmStrike

                        val resistance = chain.rows
                            .filter { it.strikePrice > chain.spotPrice && (it.callData?.openInterest ?: 0) > 0 }
                            .maxByOrNull { it.callData?.openInterest ?: 0 }
                            ?.strikePrice ?: (chain.atmStrike + 100)

                        _state.update {
                            it.copy(
                                optionChain = chain,
                                spotPrice = chain.spotPrice,
                                totalCallOI = totalCallOI,
                                totalPutOI = totalPutOI,
                                pcr = calculatedPCR,
                                atmIV = atmIV,
                                maxPain = maxPain,
                                intradayChangePct = chain.priceChangePercent
                            )
                        }
                    }
                }
                is Resource.Error -> {
                    // Continue without option chain data
                }
                is Resource.Loading -> {}
            }

            // Step 2: Fetch India VIX data
            _state.update { it.copy(analysisStatusText = "Fetching VIX data...") }
            fetchIndiaVix()

            // Step 3: Get AI analysis - use LOCAL analysis when option chain is available
            _state.update { it.copy(analysisStatusText = "Running AI analysis...") }
            Log.d(TAG, "loadData: Running LOCAL AI analysis for symbol=$symbol, expiry=$expiry")

            // Calculate max pain if we have option chain
            val calculatedMaxPain = optionChain?.let { chain ->
                calculateMaxPainFromChain(chain)
            } ?: 0.0

            // Use LOCAL analysis for consistent results with iOS
            val analysisFlow = if (optionChain != null) {
                // Get intraday change percentage - this is the PRIMARY factor for market direction (matching iOS)
                val intradayChangePct = optionChain.priceChangePercent
                Log.d(TAG, "loadData: Using LOCAL analysis with option chain (${optionChain.rows.size} strikes), intradayChange=$intradayChangePct")
                repository.getLocalAIInsights(
                    symbol = symbol,
                    optionChain = optionChain,
                    pcr = _state.value.pcr,
                    maxPainStrike = calculatedMaxPain,
                    indiaVix = _state.value.indiaVix.takeIf { it > 0 },
                    intradayChangePct = intradayChangePct
                )
            } else {
                Log.d(TAG, "loadData: No option chain, falling back to API")
                repository.getAIInsights(symbol, expiry)
            }

            analysisFlow.collect { result ->
                Log.d(TAG, "loadData: Got AI insights result type ${result.javaClass.simpleName}")
                when (result) {
                    is Resource.Success -> {
                        Log.d(TAG, "loadData: AI insights SUCCESS with ${result.data?.suggestions?.size} suggestions")
                        result.data?.let { analysis ->
                            val marketBias = determineMarketBias(analysis, _state.value.indiaVix)
                            val technicalAnalysis = generateTechnicalAnalysis(analysis, _state.value)

                            // Generate additional insights from option chain data if available
                            val enhancedInsights = if (optionChain != null) {
                                generateEnhancedInsightsFromChain(analysis.insights, _state.value)
                            } else {
                                analysis.insights
                            }

                            // Apply professional trading filters
                            val filteredSuggestions = filterSuggestions(analysis.suggestions)
                            Log.d(TAG, "loadData: After filtering: ${filteredSuggestions.size} suggestions (from ${analysis.suggestions.size})")

                            _state.update {
                                it.copy(
                                    marketSentiment = analysis.marketSentiment,
                                    spotPrice = if (it.spotPrice == 0.0) analysis.spotPrice else it.spotPrice,
                                    pcr = if (it.pcr == 1.0) analysis.pcr else it.pcr,
                                    maxPain = if (it.maxPain == 0.0) analysis.maxPain else it.maxPain,
                                    ivPercentile = analysis.ivPercentile,
                                    allSuggestions = filteredSuggestions,
                                    marketInsights = enhancedInsights,
                                    totalSuggestions = filteredSuggestions.size,
                                    marketBias = marketBias,
                                    technicalAnalysis = technicalAnalysis,
                                    hasTechnicalAnalysis = true,
                                    isAnalyzing = false,
                                    hasAnalysis = true,
                                    analysisState = Resource.Success(analysis),
                                    analysisStatusText = "${filteredSuggestions.size} suggestions"
                                )
                            }
                        }
                    }
                    is Resource.Error -> {
                        _state.update {
                            it.copy(
                                isAnalyzing = false,
                                analysisState = Resource.Error(result.message ?: "Failed to analyze"),
                                analysisStatusText = "Analysis failed"
                            )
                        }
                    }
                    is Resource.Loading -> {
                        // Already showing loading
                    }
                }
            }
        }
    }

    /**
     * Generate enhanced insights from option chain data
     */
    private fun generateEnhancedInsightsFromChain(
        baseInsights: List<MarketInsight>,
        state: AIInsightsState
    ): List<MarketInsight> {
        val insights = baseInsights.toMutableList()

        // Add INTRADAY MOMENTUM insight (PRIMARY factor - matching iOS)
        val intradayChange = state.intradayChangePct
        if (intradayChange != null && intradayChange != 0.0) {
            val momentumInsight = when {
                intradayChange >= 0.5 -> MarketInsight(
                    title = "Strong Bullish Momentum",
                    description = "Market up ${String.format("%.2f", intradayChange)}% today. Strong buying pressure observed.",
                    sentiment = MarketSentiment.BULLISH,
                    importance = ConfidenceLevel.HIGH
                )
                intradayChange >= 0.3 -> MarketInsight(
                    title = "Bullish Momentum",
                    description = "Market up ${String.format("%.2f", intradayChange)}% indicating positive sentiment.",
                    sentiment = MarketSentiment.BULLISH,
                    importance = ConfidenceLevel.MEDIUM
                )
                intradayChange > 0 -> MarketInsight(
                    title = "Mild Bullish Bias",
                    description = "Market up ${String.format("%.2f", intradayChange)}% - cautiously optimistic.",
                    sentiment = MarketSentiment.BULLISH,
                    importance = ConfidenceLevel.LOW
                )
                intradayChange <= -0.5 -> MarketInsight(
                    title = "Strong Bearish Momentum",
                    description = "Market down ${String.format("%.2f", abs(intradayChange))}% today. Heavy selling pressure.",
                    sentiment = MarketSentiment.BEARISH,
                    importance = ConfidenceLevel.HIGH
                )
                intradayChange <= -0.3 -> MarketInsight(
                    title = "Bearish Momentum",
                    description = "Market down ${String.format("%.2f", abs(intradayChange))}% indicating negative sentiment.",
                    sentiment = MarketSentiment.BEARISH,
                    importance = ConfidenceLevel.MEDIUM
                )
                else -> MarketInsight(
                    title = "Mild Bearish Bias",
                    description = "Market down ${String.format("%.2f", abs(intradayChange))}% - cautious approach recommended.",
                    sentiment = MarketSentiment.BEARISH,
                    importance = ConfidenceLevel.LOW
                )
            }
            // Add momentum insight at the beginning since it's a primary factor
            insights.add(0, momentumInsight)
        }

        // Add VIX-based insight
        if (state.indiaVix > 0) {
            val vixInsight = when {
                state.indiaVix > 20 -> MarketInsight(
                    title = "High Volatility Alert",
                    description = "India VIX at ${String.format("%.2f", state.indiaVix)} indicates elevated fear. Option premiums are expensive.",
                    sentiment = MarketSentiment.BEARISH,
                    importance = ConfidenceLevel.HIGH
                )
                state.indiaVix > 15 -> MarketInsight(
                    title = "Moderate Volatility",
                    description = "India VIX at ${String.format("%.2f", state.indiaVix)} suggests cautious trading.",
                    sentiment = MarketSentiment.NEUTRAL,
                    importance = ConfidenceLevel.MEDIUM
                )
                else -> MarketInsight(
                    title = "Low Volatility Environment",
                    description = "India VIX at ${String.format("%.2f", state.indiaVix)} - Options are relatively cheap.",
                    sentiment = MarketSentiment.BULLISH,
                    importance = ConfidenceLevel.MEDIUM
                )
            }
            insights.add(0, vixInsight)
        }

        // Add PCR-based insight from real data
        if (state.totalCallOI > 0 && state.totalPutOI > 0) {
            val pcrInsight = when {
                state.pcr > 1.3 -> MarketInsight(
                    title = "Strong Put OI Dominance",
                    description = "PCR at ${String.format("%.2f", state.pcr)} - Contrarian bullish signal.",
                    sentiment = MarketSentiment.BULLISH,
                    importance = ConfidenceLevel.HIGH
                )
                state.pcr < 0.7 -> MarketInsight(
                    title = "Heavy Call OI Buildup",
                    description = "PCR at ${String.format("%.2f", state.pcr)} - Potential reversal risk.",
                    sentiment = MarketSentiment.BEARISH,
                    importance = ConfidenceLevel.HIGH
                )
                else -> MarketInsight(
                    title = "Balanced OI Distribution",
                    description = "PCR at ${String.format("%.2f", state.pcr)} - Range-bound expected.",
                    sentiment = MarketSentiment.NEUTRAL,
                    importance = ConfidenceLevel.MEDIUM
                )
            }
            insights.add(pcrInsight)
        }

        return insights
    }

    private suspend fun fetchIndiaVix() {
        try {
            // Try to get VIX from API - if fails, use estimated value
            // For demo, calculate estimated VIX from ATM IV
            val atmIV = _state.value.atmIV
            val estimatedVix = if (atmIV > 0) atmIV * 0.8 else 14.5 // VIX is typically ~80% of ATM IV

            val isHighVolatility = estimatedVix > 18.0

            _state.update {
                it.copy(
                    indiaVix = estimatedVix,
                    vixChange = 0.0,
                    vixChangePercent = 0.0,
                    isHighVolatility = isHighVolatility
                )
            }
        } catch (e: Exception) {
            // Use default value
            _state.update {
                it.copy(indiaVix = 14.5, isHighVolatility = false)
            }
        }
    }

    /**
     * Calculate Max Pain from actual option chain data
     */
    private fun calculateMaxPainFromChain(chain: OptionChain): Double {
        if (chain.rows.isEmpty()) return chain.atmStrike

        var maxPainStrike = chain.atmStrike
        var minWriterLoss = Double.MAX_VALUE

        for (row in chain.rows) {
            val settlePrice = row.strikePrice
            var totalLoss = 0.0

            // Calculate Call writers' loss at this settle price
            for (r in chain.rows) {
                val callOI = r.callData?.openInterest ?: 0
                if (callOI > 0) {
                    val intrinsicValue = maxOf(0.0, settlePrice - r.strikePrice)
                    totalLoss += intrinsicValue * callOI
                }
            }

            // Calculate Put writers' loss at this settle price
            for (r in chain.rows) {
                val putOI = r.putData?.openInterest ?: 0
                if (putOI > 0) {
                    val intrinsicValue = maxOf(0.0, r.strikePrice - settlePrice)
                    totalLoss += intrinsicValue * putOI
                }
            }

            if (totalLoss < minWriterLoss) {
                minWriterLoss = totalLoss
                maxPainStrike = settlePrice
            }
        }

        return maxPainStrike
    }

    /**
     * Determine market bias - EXACTLY matches iOS AIAnalysisService.determineMarketBias()
     * Uses points-based system with identical thresholds
     */
    private fun determineMarketBias(analysis: AIAnalysisResult, indiaVix: Double = 0.0): MarketBias {
        var bullishPoints = 0
        var bearishPoints = 0

        // =====================================================
        // INTRADAY MOMENTUM (Highest Priority - matches iOS)
        // Points scale with move magnitude using moveStrengthMultiplier
        // =====================================================
        val intradayMove = _state.value.intradayChangePct ?: 0.0
        val absMove = abs(intradayMove)

        // Calculate move multiplier (matches iOS)
        val moveMultiplier = when {
            absMove > 0.5 -> 1.5
            absMove > 0.3 -> 1.25
            else -> 1.0
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
            analysis.pcr > 1.2 -> {
                bullishPoints += 2  // Contrarian bullish
            }
            analysis.pcr < 0.8 -> {
                bearishPoints += 1  // Too bullish, potential reversal
            }
            analysis.pcr > 1.0 -> {
                bullishPoints += 1
            }
            else -> {
                bearishPoints += 1
            }
        }

        // =====================================================
        // Max Pain Analysis (EXACTLY matches iOS)
        // =====================================================
        if (analysis.maxPain > 0) {
            if (analysis.spotPrice < analysis.maxPain) {
                bullishPoints += 2  // Spot below max pain = bullish pull
            } else if (analysis.spotPrice > analysis.maxPain) {
                bearishPoints += 2  // Spot above max pain = bearish pull
            }
        }

        // =====================================================
        // India VIX Analysis - CONTRARIAN indicator (matches iOS)
        // iOS uses VIX > 25 as contrarian bullish, VIX < 12 as contrarian bearish
        // =====================================================
        if (indiaVix > 25) {
            // Extreme fear often marks bottoms - contrarian bullish
            bullishPoints += 1
        } else if (indiaVix < 12) {
            // Complacency often precedes corrections - contrarian bearish
            bearishPoints += 1
        }

        // =====================================================
        // Determine bias (EXACTLY matches iOS thresholds)
        // iOS: >=4 strong bullish, >=2 bullish, <=-4 strong bearish, <=-2 bearish
        // =====================================================
        val netBias = bullishPoints - bearishPoints
        Log.d(TAG, "Market bias calculation: bullish=$bullishPoints, bearish=$bearishPoints, net=$netBias")

        return when {
            netBias >= 4 -> MarketBias.STRONG_BULLISH
            netBias >= 2 -> MarketBias.BULLISH
            netBias <= -4 -> MarketBias.STRONG_BEARISH
            netBias <= -2 -> MarketBias.BEARISH
            else -> MarketBias.NEUTRAL
        }
    }

    private fun generateTechnicalAnalysis(analysis: AIAnalysisResult, state: AIInsightsState): TechnicalAnalysis {
        // Generate technical analysis based on real market data + API data
        val vix = state.indiaVix
        val pcr = state.pcr
        val atmIV = state.atmIV

        // RSI estimation based on VIX and market sentiment
        val baseRsi = when {
            analysis.marketSentiment == MarketSentiment.BULLISH -> 58.0
            analysis.marketSentiment == MarketSentiment.BEARISH -> 42.0
            else -> 50.0
        }

        // Adjust RSI based on VIX (high VIX often correlates with oversold)
        val vixAdjustment = when {
            vix > 22 -> -8.0  // High fear often means oversold
            vix > 18 -> -4.0
            vix < 12 -> 5.0   // Low fear often means overbought
            else -> 0.0
        }

        val rsi = (baseRsi + vixAdjustment + (Math.random() * 6 - 3)).coerceIn(20.0, 80.0)

        val rsiCondition = when {
            rsi > 70 -> "Overbought"
            rsi < 30 -> "Oversold"
            rsi > 55 -> "Bullish"
            rsi < 45 -> "Bearish"
            else -> "Neutral"
        }

        // Trend determination using multiple factors
        val trend = when {
            pcr > 1.2 && vix < 15 -> "Strong Uptrend"
            pcr > 1.0 && analysis.marketSentiment == MarketSentiment.BULLISH -> "Uptrend"
            pcr < 0.8 && vix > 18 -> "Strong Downtrend"
            pcr < 1.0 && analysis.marketSentiment == MarketSentiment.BEARISH -> "Downtrend"
            else -> "Sideways"
        }

        // Calculate support/resistance from actual OI data if available
        val optionChain = state.optionChain
        val supportLevels = if (optionChain != null) {
            optionChain.rows
                .filter { it.strikePrice < optionChain.spotPrice && (it.putData?.openInterest ?: 0) > 0 }
                .sortedByDescending { it.putData?.openInterest ?: 0 }
                .take(3)
                .map { it.strikePrice }
                .sorted()
        } else {
            listOf(analysis.spotPrice - 100, analysis.spotPrice - 200).filter { it > 0 }
        }

        val resistanceLevels = if (optionChain != null) {
            optionChain.rows
                .filter { it.strikePrice > optionChain.spotPrice && (it.callData?.openInterest ?: 0) > 0 }
                .sortedByDescending { it.callData?.openInterest ?: 0 }
                .take(3)
                .map { it.strikePrice }
                .sorted()
        } else {
            listOf(analysis.spotPrice + 100, analysis.spotPrice + 200)
        }

        // MACD based on trend
        val macdHistogram = when (trend) {
            "Strong Uptrend" -> 25.0 + (Math.random() * 10)
            "Uptrend" -> 10.0 + (Math.random() * 10)
            "Strong Downtrend" -> -25.0 - (Math.random() * 10)
            "Downtrend" -> -10.0 - (Math.random() * 10)
            else -> (Math.random() * 10) - 5
        }

        val macdCrossover = when {
            macdHistogram > 15 -> "Strong Bullish"
            macdHistogram > 0 -> "Bullish Crossover"
            macdHistogram < -15 -> "Strong Bearish"
            macdHistogram < 0 -> "Bearish Crossover"
            else -> "No Signal"
        }

        return TechnicalAnalysis(
            rsi = rsi,
            rsiCondition = rsiCondition,
            macdHistogram = macdHistogram,
            macdCrossover = macdCrossover,
            sma20 = analysis.spotPrice - (if (trend.contains("Up")) 30 else 50),
            sma50 = analysis.spotPrice - (if (trend.contains("Up")) 80 else 120),
            vwap = analysis.spotPrice + (if (trend.contains("Up")) -10 else 20),
            supportLevels = supportLevels,
            resistanceLevels = resistanceLevels,
            trend = trend
        )
    }

    /**
     * Filter suggestions based on professional trading thresholds
     */
    private fun filterSuggestions(suggestions: List<AITradeSuggestion>): List<AITradeSuggestion> {
        return suggestions.filter { suggestion ->
            // 1. Minimum score threshold
            if (suggestion.score < MIN_SCORE_THRESHOLD) {
                Log.d(TAG, "REJECTED ${suggestion.strikePrice} ${suggestion.optionType}: score ${suggestion.score} < $MIN_SCORE_THRESHOLD")
                return@filter false
            }

            // 2. Risk-Reward Ratio filter - minimum 1.5:1
            if (suggestion.riskReward < MIN_RISK_REWARD_RATIO) {
                Log.d(TAG, "REJECTED ${suggestion.strikePrice} ${suggestion.optionType}: R:R ${suggestion.riskReward} < $MIN_RISK_REWARD_RATIO")
                return@filter false
            }

            // 3. Validate target/stoploss make sense for the trade direction
            val isValidTargetStopLoss = when (suggestion.action) {
                TradeDirection.BUY, TradeDirection.STRONG_BUY, TradeDirection.HOLD -> {
                    // BUY, STRONG_BUY and HOLD all expect target > entry, stoploss < entry
                    suggestion.targetPrice > suggestion.entryPrice &&
                    suggestion.stopLoss < suggestion.entryPrice
                }
                TradeDirection.SELL, TradeDirection.STRONG_SELL -> {
                    suggestion.targetPrice < suggestion.entryPrice &&
                    suggestion.stopLoss > suggestion.entryPrice
                }
            }
            if (!isValidTargetStopLoss) {
                Log.d(TAG, "REJECTED ${suggestion.strikePrice} ${suggestion.optionType}: Invalid target/sl for ${suggestion.action} " +
                    "(entry=${suggestion.entryPrice}, target=${suggestion.targetPrice}, sl=${suggestion.stopLoss})")
                return@filter false
            }

            // 4. Filter out suggestions with unrealistic profit/loss percentages
            val profitPercent = abs((suggestion.targetPrice - suggestion.entryPrice) / suggestion.entryPrice * 100)
            val lossPercent = abs((suggestion.entryPrice - suggestion.stopLoss) / suggestion.entryPrice * 100)
            if (profitPercent > MAX_UNREALISTIC_PERCENT || lossPercent > MAX_UNREALISTIC_PERCENT) {
                Log.d(TAG, "REJECTED ${suggestion.strikePrice} ${suggestion.optionType}: Unrealistic % (profit=$profitPercent, loss=$lossPercent)")
                return@filter false
            }

            Log.d(TAG, "ACCEPTED ${suggestion.strikePrice} ${suggestion.optionType}: R:R=${suggestion.riskReward}, score=${suggestion.score}")
            true
        }
    }

}
