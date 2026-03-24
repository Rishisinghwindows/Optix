package com.optix.app.presentation.screens.aiinsights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.core.util.Resource
import com.optix.app.data.local.AIScorecardDataStore
import com.optix.app.data.local.datastore.AIPreferencesDataStore
import com.optix.app.data.remote.api.OptixApiService
import com.optix.app.data.remote.dto.AnalyzeTradeRequest
import com.optix.app.data.remote.dto.MarketContextRequest
import com.optix.app.data.remote.dto.OptionChainRowRequest
import com.optix.app.data.remote.dto.OptionDataRequest
import com.optix.app.data.remote.dto.SuggestionRequest
import com.optix.app.data.remote.websocket.AIInsightsWebSocketService
import com.optix.app.data.remote.websocket.WebSocketConnectionState
import com.optix.app.domain.model.AIAnalysisResult
import com.optix.app.domain.model.AIPreferences
import com.optix.app.domain.model.AITradeSuggestion
import com.optix.app.domain.model.EnhancedAIAnalysisResult
import com.optix.app.domain.model.GreeksRecommendation
import com.optix.app.domain.model.IVSkewAnalysis
import com.optix.app.domain.model.MarketRegime
import com.optix.app.domain.model.OptionChain
import com.optix.app.domain.model.OptionsHeatmapData
import com.optix.app.domain.model.PersonalizedAITradeSuggestion
import com.optix.app.domain.model.ScorecardStats
import com.optix.app.domain.model.SmartMoneySignal
import com.optix.app.domain.model.StrategyRecommendation
import com.optix.app.domain.model.TradeDirection
import com.optix.app.domain.model.TradingIndex
import com.optix.app.domain.model.WatchlistItem
import com.optix.app.domain.repository.AIAnalysisRepository
import com.optix.app.domain.repository.OptionChainRepository
import com.optix.app.domain.repository.PaperTradingRepository
import com.optix.app.domain.repository.WatchlistRepository
import com.optix.app.domain.usecase.ai.AINotificationService
import com.optix.app.domain.usecase.ai.PersonalizationEngine
import android.util.Log
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * AI Explanation result from Ask AI
 */
data class AIExplanation(
    val verdict: String = "WAIT",  // "YES", "NO", "WAIT"
    val winProbability: Int = 50,
    val keyReason: String = "",
    val riskWarning: String = "",
    val betterAlternative: String? = null,
    val aiPowered: Boolean = false
)

/**
 * UI State for AI Insights Screen
 */
data class AIInsightsUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,

    // Base analysis
    val analysisResult: AIAnalysisResult? = null,

    // Enhanced analysis
    val enhancedResult: EnhancedAIAnalysisResult? = null,

    // Personalized suggestions
    val personalizedSuggestions: List<PersonalizedAITradeSuggestion> = emptyList(),

    // Selected symbol
    val selectedSymbol: String = "NIFTY",
    val selectedExpiry: String? = null,
    val availableExpiries: List<String> = emptyList(),

    // UI state
    val selectedSuggestion: PersonalizedAITradeSuggestion? = null,
    val showDetailSheet: Boolean = false,

    // Preferences
    val preferences: AIPreferences = AIPreferences(),

    // Filters
    val minScoreFilter: Int = 0,
    val showOnlyWatchlist: Boolean = false,
    val showOnlyPersonalized: Boolean = false,

    // Ask AI state
    val isAskingAI: Boolean = false,
    val aiExplanation: AIExplanation? = null,
    val askAIError: String? = null,

    // Scorecard
    val scorecardStats: ScorecardStats? = null
)

/**
 * Events emitted by the ViewModel
 */
sealed class AIInsightsEvent {
    data class ShowSnackbar(val message: String) : AIInsightsEvent()
    data class NavigateToTrade(val suggestion: AITradeSuggestion) : AIInsightsEvent()
    data class NavigateToChat(val context: String) : AIInsightsEvent()
    data object NavigateToSettings : AIInsightsEvent()
}

@HiltViewModel
class AIInsightsViewModel @Inject constructor(
    private val aiAnalysisRepository: AIAnalysisRepository,
    private val optionChainRepository: OptionChainRepository,
    private val watchlistRepository: WatchlistRepository,
    private val paperTradingRepository: PaperTradingRepository,
    private val personalizationEngine: PersonalizationEngine,
    private val webSocketService: AIInsightsWebSocketService,
    private val notificationService: AINotificationService,
    private val preferencesDataStore: AIPreferencesDataStore,
    private val apiService: OptixApiService,
    private val scorecardDataStore: AIScorecardDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIInsightsUiState())
    val uiState: StateFlow<AIInsightsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AIInsightsEvent>()
    val events = _events.asSharedFlow()

    // WebSocket connection state
    val connectionState: StateFlow<WebSocketConnectionState> = webSocketService.connectionState

    // Watchlist items
    val watchlistItems: StateFlow<List<WatchlistItem>> = watchlistRepository.getWatchlistItems()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Cache for option chain
    private var currentOptionChain: OptionChain? = null

    init {
        // Load preferences
        viewModelScope.launch {
            preferencesDataStore.aiPreferences.collect { preferences ->
                _uiState.value = _uiState.value.copy(preferences = preferences)
            }
        }

        // Listen to WebSocket updates
        setupWebSocketListeners()

        // Load initial scorecard stats
        _uiState.value = _uiState.value.copy(scorecardStats = scorecardDataStore.getStats())

        // Initial load
        loadInsights()
    }

    /**
     * Setup WebSocket listeners for real-time updates
     */
    private fun setupWebSocketListeners() {
        viewModelScope.launch {
            // Listen to new insight updates
            webSocketService.insightUpdates.collect { suggestion ->
                handleNewSuggestion(suggestion)
            }
        }

        viewModelScope.launch {
            // Listen to market sentiment updates
            webSocketService.marketSentiment.collect { sentiment ->
                sentiment?.let {
                    // Update analysis result with new sentiment
                    _uiState.value.analysisResult?.let { result ->
                        _uiState.value = _uiState.value.copy(
                            analysisResult = result.copy(
                                pcr = sentiment.pcr,
                                ivPercentile = sentiment.ivPercentile,
                                maxPain = sentiment.maxPain,
                                spotPrice = sentiment.spotPrice
                            )
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            // Listen to score updates
            webSocketService.scoreUpdates.collect { scoreUpdate ->
                updateSuggestionScore(scoreUpdate.id, scoreUpdate.newScore)
            }
        }
    }

    /**
     * Load AI insights for the selected symbol
     */
    fun loadInsights(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val symbol = _uiState.value.selectedSymbol
            val expiry = _uiState.value.selectedExpiry

            _uiState.value = _uiState.value.copy(
                isLoading = !forceRefresh,
                isRefreshing = forceRefresh,
                error = null
            )

            try {
                // First load option chain if needed
                loadOptionChainIfNeeded(symbol)

                // Fetch India VIX in parallel
                val vixBody = try {
                    val vixResponse = apiService.getIndiaVix()
                    if (vixResponse.isSuccessful) vixResponse.body() else null
                } catch (e: Exception) {
                    null
                }
                val indiaVix = vixBody?.value
                val vixChange = vixBody?.changePercent

                // Get enhanced AI insights
                aiAnalysisRepository.getEnhancedAIInsights(
                    symbol = symbol,
                    expiry = expiry,
                    optionChain = currentOptionChain
                ).collect { result ->
                    when (result) {
                        is Resource.Success -> {
                            result.data?.let { enhanced ->
                                // Merge India VIX into analysis result
                                val analysisWithVix = enhanced.baseAnalysis.copy(indiaVix = indiaVix, vixChange = vixChange)

                                // Personalize suggestions
                                val personalized = personalizeSuggestions(analysisWithVix.suggestions)

                                // Resolve existing picks against current prices
                                currentOptionChain?.let { chain ->
                                    val priceMap = mutableMapOf<String, Double>()
                                    for (row in chain.rows) {
                                        val strike = row.strikePrice
                                        row.callData?.let { cd ->
                                            if (cd.lastPrice > 0) {
                                                val key = "${analysisWithVix.symbol}_${strike.toInt()}_CE_${cd.expiry}"
                                                priceMap[key] = cd.lastPrice
                                            }
                                        }
                                        row.putData?.let { pd ->
                                            if (pd.lastPrice > 0) {
                                                val key = "${analysisWithVix.symbol}_${strike.toInt()}_PE_${pd.expiry}"
                                                priceMap[key] = pd.lastPrice
                                            }
                                        }
                                    }
                                    if (priceMap.isNotEmpty()) {
                                        scorecardDataStore.resolveOutcomes(priceMap)
                                    }
                                }
                                // Track new suggestions in scorecard
                                scorecardDataStore.trackSuggestions(analysisWithVix.suggestions, analysisWithVix.symbol)
                                val stats = scorecardDataStore.getStats()

                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    isRefreshing = false,
                                    analysisResult = analysisWithVix,
                                    enhancedResult = enhanced,
                                    personalizedSuggestions = personalized,
                                    scorecardStats = stats
                                )

                                // Connect to WebSocket for real-time updates
                                connectWebSocket(symbol)

                                // Check for high confidence notifications
                                checkForNotifications(personalized)
                            }
                        }
                        is Resource.Error -> {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                isRefreshing = false,
                                error = result.message
                            )
                        }
                        is Resource.Loading -> {
                            // Already handled above
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.message ?: "Failed to load insights"
                )
            }
        }
    }

    /**
     * Load option chain for analysis
     */
    private suspend fun loadOptionChainIfNeeded(symbol: String) {
        try {
            val index = TradingIndex.entries.find {
                it.symbol.equals(symbol, ignoreCase = true)
            } ?: TradingIndex.NIFTY50

            // Get expiries first
            val expiriesResult = optionChainRepository.getExpiries(index)
            if (expiriesResult is Resource.Success) {
                val expiries = expiriesResult.data ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    availableExpiries = expiries.map { it.value }
                )

                // Load option chain for first expiry
                val expiry = _uiState.value.selectedExpiry ?: expiries.firstOrNull()?.value
                if (expiry != null) {
                    val chainResult = optionChainRepository.getOptionChain(index, expiry)
                    if (chainResult is Resource.Success) {
                        currentOptionChain = chainResult.data
                    }
                }
            }
        } catch (e: Exception) {
            // Continue without option chain - will use basic analysis
        }
    }

    /**
     * Personalize suggestions using PersonalizationEngine
     */
    private suspend fun personalizeSuggestions(
        suggestions: List<AITradeSuggestion>
    ): List<PersonalizedAITradeSuggestion> {
        return try {
            val watchlist = watchlistItems.value
            val openPositionsResult = paperTradingRepository.getOpenPositions()
            val openPositions = if (openPositionsResult is Resource.Success) {
                openPositionsResult.data ?: emptyList()
            } else {
                emptyList()
            }

            personalizationEngine.personalize(
                suggestions = suggestions,
                watchlist = watchlist,
                openPositions = openPositions
            )
        } catch (e: Exception) {
            Log.e("AIInsightsViewModel", "Personalization failed, applying basic filters", e)
            // Fallback: apply basic filters and return non-personalized
            suggestions
                .filter { suggestion ->
                    // Apply basic quality filters even in fallback
                    val isValidRR = suggestion.riskReward >= 1.5
                    val isValidTarget = when (suggestion.action) {
                        TradeDirection.BUY, TradeDirection.STRONG_BUY -> suggestion.targetPrice > suggestion.entryPrice
                        TradeDirection.SELL, TradeDirection.STRONG_SELL -> suggestion.targetPrice < suggestion.entryPrice
                        TradeDirection.HOLD -> true
                    }
                    val isValidStopLoss = when (suggestion.action) {
                        TradeDirection.BUY, TradeDirection.STRONG_BUY -> suggestion.stopLoss < suggestion.entryPrice
                        TradeDirection.SELL, TradeDirection.STRONG_SELL -> suggestion.stopLoss > suggestion.entryPrice
                        TradeDirection.HOLD -> true
                    }
                    isValidRR && isValidTarget && isValidStopLoss
                }
                .map { suggestion ->
                    PersonalizedAITradeSuggestion(
                        baseSuggestion = suggestion,
                        personalizationScore = 0,
                        matchesWatchlist = false,
                        matchesRiskProfile = true,
                        historyBasedConfidence = 0.5,
                        recommendedPositionSize = 1,
                        hedgeRecommendation = null,
                        relevanceReasons = emptyList()
                    )
                }
        }
    }

    /**
     * Connect WebSocket for real-time updates
     */
    private fun connectWebSocket(symbol: String) {
        webSocketService.connect(symbol)
    }

    /**
     * Handle new suggestion from WebSocket
     */
    private suspend fun handleNewSuggestion(suggestion: AITradeSuggestion) {
        // Personalize the new suggestion
        val watchlist = watchlistItems.value
        val openPositions = try {
            val result = paperTradingRepository.getOpenPositions()
            if (result is Resource.Success) result.data ?: emptyList() else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val personalized = personalizationEngine.personalize(
            listOf(suggestion), watchlist, openPositions
        ).firstOrNull() ?: return

        // Add to existing suggestions
        val currentSuggestions = _uiState.value.personalizedSuggestions.toMutableList()

        // Check if suggestion already exists (update) or is new (add)
        val existingIndex = currentSuggestions.indexOfFirst {
            it.baseSuggestion.id == suggestion.id
        }

        if (existingIndex >= 0) {
            currentSuggestions[existingIndex] = personalized
        } else {
            currentSuggestions.add(0, personalized) // Add to top
        }

        _uiState.value = _uiState.value.copy(
            personalizedSuggestions = currentSuggestions.sortedByDescending { it.totalScore }
        )

        // Show notification for high confidence
        checkForNotifications(listOf(personalized))
    }

    /**
     * Update suggestion score
     */
    private fun updateSuggestionScore(suggestionId: String, newScore: Int) {
        val currentSuggestions = _uiState.value.personalizedSuggestions.toMutableList()
        val index = currentSuggestions.indexOfFirst { it.baseSuggestion.id == suggestionId }

        if (index >= 0) {
            val existing = currentSuggestions[index]
            val updatedBase = existing.baseSuggestion.copy(score = newScore)
            currentSuggestions[index] = existing.copy(baseSuggestion = updatedBase)

            _uiState.value = _uiState.value.copy(
                personalizedSuggestions = currentSuggestions.sortedByDescending { it.totalScore }
            )
        }
    }

    /**
     * Check for high confidence notifications
     */
    private suspend fun checkForNotifications(suggestions: List<PersonalizedAITradeSuggestion>) {
        val preferences = _uiState.value.preferences
        if (!preferences.notifyOnHighConfidence) return

        suggestions
            .filter { it.totalScore >= preferences.highConfidenceThreshold }
            .forEach { suggestion ->
                notificationService.notifyPersonalizedSuggestion(suggestion)
            }
    }

    // ============================
    // User Actions
    // ============================

    /**
     * Change selected symbol
     */
    fun selectSymbol(symbol: String) {
        if (symbol != _uiState.value.selectedSymbol) {
            _uiState.value = _uiState.value.copy(
                selectedSymbol = symbol,
                selectedExpiry = null
            )
            loadInsights()
        }
    }

    /**
     * Change selected expiry
     */
    fun selectExpiry(expiry: String) {
        _uiState.value = _uiState.value.copy(selectedExpiry = expiry)
        loadInsights()
    }

    /**
     * Refresh insights
     */
    fun refresh() {
        loadInsights(forceRefresh = true)
    }

    /**
     * Select a suggestion to view details
     */
    fun selectSuggestion(suggestion: PersonalizedAITradeSuggestion) {
        _uiState.value = _uiState.value.copy(
            selectedSuggestion = suggestion,
            showDetailSheet = true
        )
    }

    /**
     * Close detail sheet
     */
    fun closeDetailSheet() {
        _uiState.value = _uiState.value.copy(
            showDetailSheet = false,
            selectedSuggestion = null
        )
    }

    /**
     * Add suggestion to watchlist
     */
    fun addToWatchlist(suggestion: AITradeSuggestion) {
        viewModelScope.launch {
            val item = WatchlistItem(
                id = UUID.randomUUID().toString(),
                symbol = suggestion.symbol,
                strikePrice = suggestion.strikePrice,
                optionType = suggestion.optionType,
                expiry = suggestion.expiry
            )

            val result = watchlistRepository.addToWatchlist(item)
            when (result) {
                is Resource.Success -> {
                    _events.emit(AIInsightsEvent.ShowSnackbar("Added to watchlist"))

                    // Re-personalize to update watchlist flags
                    _uiState.value.analysisResult?.let { analysis ->
                        val personalized = personalizeSuggestions(analysis.suggestions)
                        _uiState.value = _uiState.value.copy(personalizedSuggestions = personalized)
                    }
                }
                is Resource.Error -> {
                    _events.emit(AIInsightsEvent.ShowSnackbar(result.message ?: "Failed to add"))
                }
                else -> {}
            }
        }
    }

    /**
     * Remove from watchlist
     */
    fun removeFromWatchlist(suggestionId: String) {
        viewModelScope.launch {
            // Find the watchlist item for this suggestion
            val suggestion = _uiState.value.personalizedSuggestions
                .find { it.baseSuggestion.id == suggestionId }?.baseSuggestion ?: return@launch

            val watchlistItem = watchlistItems.value.find {
                it.symbol == suggestion.symbol &&
                        it.strikePrice == suggestion.strikePrice &&
                        it.optionType == suggestion.optionType &&
                        it.expiry == suggestion.expiry
            } ?: return@launch

            val result = watchlistRepository.removeFromWatchlist(watchlistItem.id)
            when (result) {
                is Resource.Success -> {
                    _events.emit(AIInsightsEvent.ShowSnackbar("Removed from watchlist"))

                    // Re-personalize
                    _uiState.value.analysisResult?.let { analysis ->
                        val personalized = personalizeSuggestions(analysis.suggestions)
                        _uiState.value = _uiState.value.copy(personalizedSuggestions = personalized)
                    }
                }
                is Resource.Error -> {
                    _events.emit(AIInsightsEvent.ShowSnackbar(result.message ?: "Failed to remove"))
                }
                else -> {}
            }
        }
    }

    /**
     * Check if suggestion is in watchlist
     */
    fun isInWatchlist(suggestion: AITradeSuggestion): Boolean {
        return watchlistItems.value.any {
            it.symbol == suggestion.symbol &&
                    it.strikePrice == suggestion.strikePrice &&
                    it.optionType == suggestion.optionType &&
                    it.expiry == suggestion.expiry
        }
    }

    /**
     * Navigate to trade screen
     */
    fun openTrade(suggestion: AITradeSuggestion) {
        viewModelScope.launch {
            _events.emit(AIInsightsEvent.NavigateToTrade(suggestion))
        }
    }

    /**
     * Open chat with context
     */
    fun openChat() {
        viewModelScope.launch {
            val context = _uiState.value.analysisResult?.let {
                "Current market: ${it.symbol} at ${it.spotPrice}, " +
                        "Sentiment: ${it.marketSentiment.displayName}, " +
                        "PCR: ${String.format("%.2f", it.pcr)}, " +
                        "IV Percentile: ${String.format("%.0f", it.ivPercentile)}%"
            } ?: ""
            _events.emit(AIInsightsEvent.NavigateToChat(context))
        }
    }

    /**
     * Open settings
     */
    fun openSettings() {
        viewModelScope.launch {
            _events.emit(AIInsightsEvent.NavigateToSettings)
        }
    }

    /**
     * Ask AI about a trade suggestion
     */
    fun askAIAboutTrade(suggestion: PersonalizedAITradeSuggestion) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isAskingAI = true,
                aiExplanation = null,
                askAIError = null
            )

            try {
                val base = suggestion.baseSuggestion
                val analysisResult = _uiState.value.analysisResult
                val enhancedResult = _uiState.value.enhancedResult
                val optionChain = currentOptionChain

                // Find the actual option data from the chain
                val optionData = optionChain?.rows?.find { row ->
                    row.strikePrice == base.strikePrice
                }?.let { row ->
                    if (base.optionType == com.optix.app.domain.model.OptionType.CALL) {
                        row.callData
                    } else {
                        row.putData
                    }
                }

                // Calculate days to expiry from expiry string
                val daysToExpiry = try {
                    val expiryDate = java.time.LocalDate.parse(
                        base.expiry,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    )
                    java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(),
                        expiryDate
                    ).toInt().coerceAtLeast(0)
                } catch (e: Exception) {
                    7 // Default fallback
                }

                // Get support/resistance from heatmap
                val heatmap = enhancedResult?.heatmapData
                val support = heatmap?.immediateSupportLevel
                val resistance = heatmap?.immediateResistanceLevel

                // Calculate total OI from option chain
                var totalCallOi = 0L
                var totalPutOi = 0L
                optionChain?.rows?.forEach { row ->
                    totalCallOi += row.callData?.openInterest ?: 0
                    totalPutOi += row.putData?.openInterest ?: 0
                }

                // Build option chain rows for the request (limit to 40 strikes around ATM)
                val atmStrike = optionChain?.atmStrike ?: (analysisResult?.spotPrice ?: base.strikePrice)
                val chainRows = optionChain?.rows
                    ?.sortedBy { kotlin.math.abs(it.strikePrice - atmStrike) }
                    ?.take(40)
                    ?.map { row ->
                        OptionChainRowRequest(
                            strike = row.strikePrice,
                            callOi = row.callData?.openInterest ?: 0,
                            callOiChange = row.callData?.oiChange ?: 0,
                            callLtp = row.callData?.lastPrice ?: 0.0,
                            callIv = row.callData?.impliedVolatility ?: 0.0,
                            callVolume = row.callData?.volume ?: 0,
                            callDelta = row.callData?.delta?.takeIf { it != 0.0 },
                            callTheta = row.callData?.theta?.takeIf { it != 0.0 },
                            putOi = row.putData?.openInterest ?: 0,
                            putOiChange = row.putData?.oiChange ?: 0,
                            putLtp = row.putData?.lastPrice ?: 0.0,
                            putIv = row.putData?.impliedVolatility ?: 0.0,
                            putVolume = row.putData?.volume ?: 0,
                            putDelta = row.putData?.delta?.takeIf { it != 0.0 },
                            putTheta = row.putData?.theta?.takeIf { it != 0.0 }
                        )
                    }

                // Fetch India VIX
                val indiaVix = try {
                    val vixResponse = apiService.getIndiaVix()
                    if (vixResponse.isSuccessful) {
                        val vix = vixResponse.body()?.value
                        Log.d("AskAI", "India VIX fetched: $vix")
                        vix
                    } else {
                        Log.w("AskAI", "Failed to fetch India VIX: ${vixResponse.code()}")
                        null
                    }
                } catch (e: Exception) {
                    Log.e("AskAI", "Error fetching India VIX: ${e.message}")
                    null // Continue without VIX if fetch fails
                }

                Log.d("AskAI", "=== ASK AI REQUEST DATA ===")
                Log.d("AskAI", "Strike: ${base.strikePrice}, Type: ${base.optionType}")
                Log.d("AskAI", "Option Data - IV: ${optionData?.impliedVolatility}, Delta: ${optionData?.delta}, Theta: ${optionData?.theta}")
                Log.d("AskAI", "Option Data - OI: ${optionData?.openInterest}, OI Change: ${optionData?.oiChange}, Volume: ${optionData?.volume}")
                Log.d("AskAI", "Market - Spot: ${analysisResult?.spotPrice}, PCR: ${analysisResult?.pcr}, MaxPain: ${analysisResult?.maxPain}")
                Log.d("AskAI", "Market - Support: $support, Resistance: $resistance, VIX: $indiaVix")
                Log.d("AskAI", "Total OI - Calls: $totalCallOi, Puts: $totalPutOi")
                Log.d("AskAI", "Days to Expiry: $daysToExpiry")
                Log.d("AskAI", "Option Chain rows: ${chainRows?.size ?: 0}")

                // Build the request with all available data
                val request = AnalyzeTradeRequest(
                    option = OptionDataRequest(
                        strikePrice = base.strikePrice,
                        optionType = if (base.optionType == com.optix.app.domain.model.OptionType.CALL) "CE" else "PE",
                        ltp = base.entryPrice,
                        iv = optionData?.impliedVolatility ?: 0.15,
                        delta = optionData?.delta?.takeIf { it != 0.0 },
                        theta = optionData?.theta?.takeIf { it != 0.0 },
                        openInterest = optionData?.openInterest?.toInt() ?: 0,
                        oiChange = optionData?.oiChange?.toInt() ?: 0,
                        volume = optionData?.volume?.toInt() ?: 0,
                        daysToExpiry = daysToExpiry
                    ),
                    marketContext = MarketContextRequest(
                        spotPrice = analysisResult?.spotPrice ?: optionChain?.spotPrice ?: base.strikePrice,
                        pcr = analysisResult?.pcr ?: (if (totalCallOi > 0) totalPutOi.toDouble() / totalCallOi else 1.0),
                        maxPain = analysisResult?.maxPain ?: heatmap?.maxPainStrike,
                        atmStrike = optionChain?.atmStrike ?: analysisResult?.spotPrice?.let {
                            val step = if (_uiState.value.selectedSymbol.contains("BANK")) 100.0 else 50.0
                            (it / step).toInt() * step
                        },
                        indexName = _uiState.value.selectedSymbol,
                        support = support,
                        resistance = resistance,
                        indiaVix = indiaVix,
                        totalCallOi = totalCallOi.takeIf { it > 0 },
                        totalPutOi = totalPutOi.takeIf { it > 0 }
                    ),
                    suggestion = SuggestionRequest(
                        entry = base.entryPrice,
                        target = base.targetPrice,
                        stopLoss = base.stopLoss,
                        riskReward = "1:${String.format("%.1f", base.riskReward)}",
                        score = base.score
                    ),
                    optionChain = chainRows
                )

                Log.d("AskAI", "Sending request to API...")
                val response = apiService.analyzeTradeWithAI(request)

                Log.d("AskAI", "Response code: ${response.code()}")
                if (response.isSuccessful && response.body() != null) {
                    val result = response.body()!!
                    Log.d("AskAI", "=== AI RESPONSE ===")
                    Log.d("AskAI", "Verdict: ${result.verdict}, Win Probability: ${result.winProbability}%")
                    Log.d("AskAI", "Key Reason: ${result.keyReason}")
                    Log.d("AskAI", "AI Powered: ${result.aiPowered}")
                    _uiState.value = _uiState.value.copy(
                        isAskingAI = false,
                        aiExplanation = AIExplanation(
                            verdict = result.verdict,
                            winProbability = result.winProbability,
                            keyReason = result.keyReason,
                            riskWarning = result.riskWarning,
                            betterAlternative = result.betterAlternative,
                            aiPowered = result.aiPowered
                        )
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isAskingAI = false,
                        askAIError = "Failed to get AI analysis"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAskingAI = false,
                    askAIError = e.message ?: "Network error"
                )
            }
        }
    }

    /**
     * Clear AI explanation when sheet closes
     */
    fun clearAIExplanation() {
        _uiState.value = _uiState.value.copy(
            aiExplanation = null,
            askAIError = null
        )
    }

    /**
     * Update filter - minimum score
     */
    fun setMinScoreFilter(minScore: Int) {
        _uiState.value = _uiState.value.copy(minScoreFilter = minScore)
    }

    /**
     * Toggle watchlist-only filter
     */
    fun toggleWatchlistFilter() {
        _uiState.value = _uiState.value.copy(
            showOnlyWatchlist = !_uiState.value.showOnlyWatchlist
        )
    }

    /**
     * Toggle personalized-only filter
     */
    fun togglePersonalizedFilter() {
        _uiState.value = _uiState.value.copy(
            showOnlyPersonalized = !_uiState.value.showOnlyPersonalized
        )
    }

    /**
     * Set option type filter (Calls/Puts/null for all)
     */
    fun setOptionTypeFilter(filter: String?) {
        // This is handled in the UI composable for simplicity
        // The filter is applied directly in AIInsightsContent
    }

    /**
     * Get filtered suggestions based on current filters
     */
    fun getFilteredSuggestions(): List<PersonalizedAITradeSuggestion> {
        var suggestions = _uiState.value.personalizedSuggestions

        // Apply score filter
        if (_uiState.value.minScoreFilter > 0) {
            suggestions = suggestions.filter { it.totalScore >= _uiState.value.minScoreFilter }
        }

        // Apply watchlist filter
        if (_uiState.value.showOnlyWatchlist) {
            suggestions = suggestions.filter { it.matchesWatchlist }
        }

        // Apply personalized filter
        if (_uiState.value.showOnlyPersonalized) {
            suggestions = suggestions.filter { it.isHighlyPersonalized }
        }

        return suggestions
    }

    override fun onCleared() {
        super.onCleared()
        webSocketService.disconnect()
    }
}
