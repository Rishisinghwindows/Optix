package com.optix.app.data.repository

import android.util.Log
import com.optix.app.core.calculation.AdvancedAnalysisEngine
import com.optix.app.core.calculation.OptionScoringEngine
import com.optix.app.core.calculation.StrategySuggestionEngine
import com.optix.app.core.util.Resource
import com.optix.app.data.remote.api.OptixApiService
import com.optix.app.domain.model.*
import com.optix.app.domain.repository.AIAnalysisRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIAnalysisRepositoryImpl @Inject constructor(
    private val api: OptixApiService,
    private val advancedAnalysisEngine: AdvancedAnalysisEngine,
    private val strategySuggestionEngine: StrategySuggestionEngine,
    private val optionScoringEngine: OptionScoringEngine
) : AIAnalysisRepository {

    companion object {
        private const val TAG = "AIAnalysisRepo"
    }

    override suspend fun getAIInsights(
        symbol: String,
        expiry: String?
    ): Flow<Resource<AIAnalysisResult>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                try {
                    val response = api.getAIInsights(symbol, expiry)
                    if (response.isSuccessful && response.body() != null) {
                        val dto = response.body()!!
                        Resource.Success(
                            AIAnalysisResult(
                                symbol = symbol,
                                spotPrice = dto.spotPrice ?: 0.0,
                                marketSentiment = when (dto.sentiment?.lowercase()) {
                                    "bullish" -> MarketSentiment.BULLISH
                                    "bearish" -> MarketSentiment.BEARISH
                                    else -> MarketSentiment.NEUTRAL
                                },
                                suggestions = dto.suggestions?.map { sugg ->
                                    AITradeSuggestion(
                                        id = sugg.id ?: "",
                                        symbol = symbol,
                                        strikePrice = sugg.strikePrice ?: 0.0,
                                        optionType = if (sugg.optionType?.uppercase() == "CE") OptionType.CALL else OptionType.PUT,
                                        expiry = sugg.expiry ?: "",
                                        action = if (sugg.action?.uppercase() == "BUY") TradeDirection.BUY else TradeDirection.SELL,
                                        entryPrice = sugg.entryPrice ?: 0.0,
                                        targetPrice = sugg.targetPrice ?: 0.0,
                                        stopLoss = sugg.stopLoss ?: 0.0,
                                        confidence = when (sugg.confidence?.lowercase()) {
                                            "high" -> ConfidenceLevel.HIGH
                                            "medium" -> ConfidenceLevel.MEDIUM
                                            else -> ConfidenceLevel.LOW
                                        },
                                        score = sugg.score ?: 0,
                                        reasoning = sugg.reasoning?.map { r ->
                                            ScoreReasoning(
                                                factor = r.factor ?: "",
                                                score = r.score ?: 0,
                                                maxScore = r.maxScore ?: 100,
                                                description = r.description ?: "",
                                                isPositive = r.isPositive ?: true
                                            )
                                        } ?: emptyList(),
                                        riskReward = sugg.riskReward ?: 0.0,
                                        maxProfit = sugg.maxProfit ?: 0.0,
                                        maxLoss = sugg.maxLoss ?: 0.0
                                    )
                                } ?: emptyList(),
                                insights = dto.insights?.map { insight ->
                                    MarketInsight(
                                        title = insight.title ?: "",
                                        description = insight.description ?: "",
                                        sentiment = when (insight.sentiment?.lowercase()) {
                                            "bullish" -> MarketSentiment.BULLISH
                                            "bearish" -> MarketSentiment.BEARISH
                                            else -> MarketSentiment.NEUTRAL
                                        },
                                        importance = when (insight.importance?.lowercase()) {
                                            "high" -> ConfidenceLevel.HIGH
                                            "medium" -> ConfidenceLevel.MEDIUM
                                            else -> ConfidenceLevel.LOW
                                        }
                                    )
                                } ?: emptyList(),
                                pcr = dto.pcr ?: 0.0,
                                ivPercentile = dto.ivPercentile ?: 0.0,
                                maxPain = dto.maxPain ?: 0.0
                            )
                        )
                    } else {
                        // API failed, return demo data
                        getDemoAIInsights(symbol)
                    }
                } catch (e: Exception) {
                    // API unavailable, return demo data
                    getDemoAIInsights(symbol)
                }
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    private fun getDemoAIInsights(symbol: String): Resource<AIAnalysisResult> {
        val spotPrice = when (symbol.uppercase()) {
            "NIFTY", "NIFTY50" -> 22850.0
            "BANKNIFTY" -> 48500.0
            "FINNIFTY" -> 23200.0
            else -> 22000.0
        }
        val atmStrike = (spotPrice / 50).toLong() * 50.0

        // Max pain is typically 100-200 points below spot in bearish conditions
        // Using spot - 150 to simulate bearish market (spot > maxPain)
        val maxPain = atmStrike - 150.0

        return Resource.Success(
            AIAnalysisResult(
                symbol = symbol,
                spotPrice = spotPrice,
                marketSentiment = MarketSentiment.BEARISH,
                suggestions = listOf(
                    // In bearish market: BUY PUT options to profit from downward movement
                    AITradeSuggestion(
                        id = "1",
                        symbol = symbol,
                        strikePrice = atmStrike,
                        optionType = OptionType.PUT,
                        expiry = "13-Feb-2025",
                        action = TradeDirection.BUY,
                        entryPrice = 165.50,
                        targetPrice = 280.00,
                        stopLoss = 120.00,
                        confidence = ConfidenceLevel.HIGH,
                        score = 85,
                        reasoning = listOf(
                            ScoreReasoning("Market Trend", 18, 20, "Strong bearish momentum confirmed", true),
                            ScoreReasoning("OI Buildup", 17, 20, "Heavy put OI buildup at this strike", true),
                            ScoreReasoning("PCR Analysis", 16, 20, "Low PCR of 0.75 confirms bearish bias", true),
                            ScoreReasoning("Greeks", 17, 20, "Favorable delta (-0.52) for downside", true),
                            ScoreReasoning("Technical", 17, 20, "Price below VWAP, RSI bearish", true)
                        ),
                        riskReward = 2.5,
                        maxProfit = 2862.50,
                        maxLoss = 1137.50
                    ),
                    AITradeSuggestion(
                        id = "2",
                        symbol = symbol,
                        strikePrice = atmStrike - 100,
                        optionType = OptionType.PUT,
                        expiry = "13-Feb-2025",
                        action = TradeDirection.BUY,
                        entryPrice = 92.50,
                        targetPrice = 165.00,
                        stopLoss = 65.00,
                        confidence = ConfidenceLevel.HIGH,
                        score = 78,
                        reasoning = listOf(
                            ScoreReasoning("Support Break", 16, 20, "Key support level likely to break", true),
                            ScoreReasoning("IV Percentile", 15, 20, "IV at 42nd percentile - fairly priced", true),
                            ScoreReasoning("Volume Surge", 16, 20, "Increasing put volume confirms selling", true),
                            ScoreReasoning("Max Pain", 15, 20, "Spot above max pain, pullback expected", true),
                            ScoreReasoning("Risk Profile", 16, 20, "Good R:R ratio at 2.6x", true)
                        ),
                        riskReward = 2.6,
                        maxProfit = 1812.50,
                        maxLoss = 687.50
                    ),
                    AITradeSuggestion(
                        id = "3",
                        symbol = symbol,
                        strikePrice = atmStrike + 100,
                        optionType = OptionType.CALL,
                        expiry = "13-Feb-2025",
                        action = TradeDirection.SELL,
                        entryPrice = 125.00,
                        targetPrice = 45.00,
                        stopLoss = 185.00,
                        confidence = ConfidenceLevel.MEDIUM,
                        score = 72,
                        reasoning = listOf(
                            ScoreReasoning("Resistance Zone", 15, 20, "Strong resistance above spot", true),
                            ScoreReasoning("Premium Decay", 14, 20, "Time decay favors seller", true),
                            ScoreReasoning("Bearish Trend", 15, 20, "Unlikely to breach this strike", true),
                            ScoreReasoning("IV Premium", 14, 20, "Elevated IV offers good premium", true),
                            ScoreReasoning("Risk", 14, 20, "Defined risk with stop loss", true)
                        ),
                        riskReward = 1.3,
                        maxProfit = 2000.00,
                        maxLoss = 1500.00
                    ),
                    AITradeSuggestion(
                        id = "4",
                        symbol = symbol,
                        strikePrice = atmStrike - 200,
                        optionType = OptionType.PUT,
                        expiry = "20-Feb-2025",
                        action = TradeDirection.BUY,
                        entryPrice = 58.00,
                        targetPrice = 120.00,
                        stopLoss = 35.00,
                        confidence = ConfidenceLevel.MEDIUM,
                        score = 65,
                        reasoning = listOf(
                            ScoreReasoning("Extended Move", 13, 20, "Targets deeper correction", true),
                            ScoreReasoning("Time Value", 14, 20, "Extra week for move to play out", true),
                            ScoreReasoning("Cost Efficient", 12, 20, "Lower premium, higher leverage", true),
                            ScoreReasoning("Volatility", 13, 20, "IV expansion possible on selloff", true),
                            ScoreReasoning("Risk", 13, 20, "Limited loss with high reward potential", true)
                        ),
                        riskReward = 2.7,
                        maxProfit = 1550.00,
                        maxLoss = 575.00
                    )
                ),
                insights = listOf(
                    MarketInsight(
                        title = "Heavy Put Buying Detected",
                        description = "Significant put buying in ${atmStrike.toLong()}-${(atmStrike - 100).toLong()} PE strikes indicates bearish positioning by institutions.",
                        sentiment = MarketSentiment.BEARISH,
                        importance = ConfidenceLevel.HIGH
                    ),
                    MarketInsight(
                        title = "Call Writers Active",
                        description = "Heavy call writing observed above ${atmStrike.toLong()} suggests strong resistance zone.",
                        sentiment = MarketSentiment.BEARISH,
                        importance = ConfidenceLevel.MEDIUM
                    ),
                    MarketInsight(
                        title = "Low PCR Warning",
                        description = "PCR at 0.75 indicates excessive bullishness - contrarian bearish signal.",
                        sentiment = MarketSentiment.BEARISH,
                        importance = ConfidenceLevel.MEDIUM
                    ),
                    MarketInsight(
                        title = "Max Pain Analysis",
                        description = "Spot at ${spotPrice.toLong()} is above max pain ${maxPain.toLong()}, expect pullback towards max pain.",
                        sentiment = MarketSentiment.BEARISH,
                        importance = ConfidenceLevel.MEDIUM
                    )
                ),
                pcr = 0.75,
                ivPercentile = 42.0,
                maxPain = maxPain
            )
        )
    }

    override suspend fun analyzeOption(
        symbol: String,
        strikePrice: Double,
        optionType: OptionType,
        expiry: String,
        spotPrice: Double
    ): Flow<Resource<OptionScore>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                // TODO: Implement API call for option analysis
                // For now, return a mock score
                val mockScore = OptionScore(
                    totalScore = 75,
                    maxScore = 100,
                    reasoning = listOf(
                        ScoreReasoning("IV Percentile", 15, 20, "IV is in favorable range", true),
                        ScoreReasoning("OI Analysis", 18, 20, "Strong OI build-up", true),
                        ScoreReasoning("PCR", 12, 20, "PCR indicates neutral sentiment", true),
                        ScoreReasoning("Greeks", 15, 20, "Favorable delta and theta", true),
                        ScoreReasoning("Technical", 15, 20, "Price near support", true)
                    ),
                    confidence = ConfidenceLevel.HIGH
                )
                Resource.Success(mockScore)
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    override suspend fun getOIAnalysis(
        symbol: String,
        expiry: String?
    ): Flow<Resource<OIAnalysisResult>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                // TODO: Implement API call for OI analysis
                // For now, return mock data
                Resource.Success(
                    OIAnalysisResult(
                        symbol = symbol,
                        spotPrice = 22000.0,
                        atmStrike = 22000.0,
                        pcr = 1.2,
                        maxPain = 22000.0,
                        supportZones = emptyList(),
                        resistanceZones = emptyList(),
                        heatmapData = emptyList(),
                        smartMoneyActivities = emptyList(),
                        ivSurface = emptyList(),
                        interpretation = OIInterpretation(
                            title = "Neutral Market",
                            description = "Market shows balanced OI",
                            bias = MarketSentiment.NEUTRAL,
                            confidence = ConfidenceLevel.MEDIUM
                        )
                    )
                )
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    // ============================
    // Enhanced AI Analysis Methods
    // ============================

    override suspend fun getEnhancedAIInsights(
        symbol: String,
        expiry: String?,
        optionChain: OptionChain?
    ): Flow<Resource<EnhancedAIAnalysisResult>> = flow {
        emit(Resource.Loading())
        try {
            // First get base analysis
            var baseAnalysis: AIAnalysisResult? = null
            getAIInsights(symbol, expiry).collect { result ->
                if (result is Resource.Success) {
                    baseAnalysis = result.data
                }
            }

            if (baseAnalysis == null) {
                emit(Resource.Error("Failed to get base analysis"))
                return@flow
            }

            val result = withContext(Dispatchers.IO) {
                val base = baseAnalysis!!

                // Generate enhanced analysis using engines
                val greeksRecommendations = mutableListOf<GreeksRecommendation>()
                val strategyRecommendations = mutableListOf<StrategyRecommendation>()
                var ivSkewAnalysis: IVSkewAnalysis? = null
                var smartMoneySignals = listOf<SmartMoneySignal>()
                var heatmapData: OptionsHeatmapData? = null
                var marketRegime = MarketRegime.RANGE_BOUND

                // If option chain is provided, perform advanced analysis
                optionChain?.let { chain ->
                    // IV Skew Analysis
                    ivSkewAnalysis = advancedAnalysisEngine.analyzeIVSkew(chain)

                    // Smart Money Detection
                    smartMoneySignals = advancedAnalysisEngine.detectSmartMoney(chain, null)

                    // Heatmap Generation
                    heatmapData = advancedAnalysisEngine.generateHeatmap(chain)

                    // Strategy Suggestions
                    strategyRecommendations.addAll(
                        strategySuggestionEngine.suggestStrategies(
                            optionChain = chain,
                            marketSentiment = base.marketSentiment,
                            marketRegime = marketRegime,
                            ivPercentile = base.ivPercentile
                        )
                    )

                    // Determine market regime
                    marketRegime = advancedAnalysisEngine.determineMarketRegime(
                        spotPrice = base.spotPrice,
                        ivPercentile = base.ivPercentile,
                        pcr = base.pcr,
                        recentPriceChange = 0.0 // Would need historical data
                    )

                    // Greeks recommendations for ATM options
                    chain.rows.find { it.isATM }?.let { atmRow ->
                        atmRow.callData?.let { callData ->
                            val daysToExpiry = chain.rows.firstOrNull()?.callData?.let { 7 } ?: 7
                            greeksRecommendations.addAll(
                                advancedAnalysisEngine.analyzeGreeks(callData, base.spotPrice, daysToExpiry)
                            )
                        }
                        atmRow.putData?.let { putData ->
                            val daysToExpiry = 7
                            greeksRecommendations.addAll(
                                advancedAnalysisEngine.analyzeGreeks(putData, base.spotPrice, daysToExpiry)
                            )
                        }
                    }
                }

                Resource.Success(
                    EnhancedAIAnalysisResult(
                        baseAnalysis = base,
                        greeksRecommendations = greeksRecommendations,
                        strategyRecommendations = strategyRecommendations,
                        ivSkewAnalysis = ivSkewAnalysis,
                        smartMoneySignals = smartMoneySignals,
                        heatmapData = heatmapData,
                        marketRegime = marketRegime
                    )
                )
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    override suspend fun getStrategyRecommendations(
        symbol: String,
        expiry: String,
        optionChain: OptionChain,
        marketSentiment: MarketSentiment,
        ivPercentile: Double
    ): Flow<Resource<List<StrategyRecommendation>>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                val marketRegime = advancedAnalysisEngine.determineMarketRegime(
                    spotPrice = optionChain.spotPrice,
                    ivPercentile = ivPercentile,
                    pcr = 1.0, // Default, would need real PCR
                    recentPriceChange = 0.0
                )

                val strategies = strategySuggestionEngine.suggestStrategies(
                    optionChain = optionChain,
                    marketSentiment = marketSentiment,
                    marketRegime = marketRegime,
                    ivPercentile = ivPercentile
                )

                Resource.Success(strategies)
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    override suspend fun getGreeksRecommendations(
        optionData: OptionData,
        spotPrice: Double,
        daysToExpiry: Int
    ): Flow<Resource<List<GreeksRecommendation>>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                val recommendations = advancedAnalysisEngine.analyzeGreeks(
                    optionData = optionData,
                    spotPrice = spotPrice,
                    daysToExpiry = daysToExpiry
                )
                Resource.Success(recommendations)
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    override suspend fun getSmartMoneySignals(
        optionChain: OptionChain,
        previousChain: OptionChain?
    ): Flow<Resource<List<SmartMoneySignal>>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                val signals = advancedAnalysisEngine.detectSmartMoney(
                    currentChain = optionChain,
                    previousChain = previousChain
                )
                Resource.Success(signals)
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    override suspend fun getIVSkewAnalysis(
        optionChain: OptionChain
    ): Flow<Resource<IVSkewAnalysis>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                val skewAnalysis = advancedAnalysisEngine.analyzeIVSkew(optionChain)
                Resource.Success(skewAnalysis)
            }
            emit(result)
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }

    /**
     * Get AI insights using LOCAL analysis on the option chain data.
     * This ensures consistent results with iOS which also does local analysis.
     */
    override suspend fun getLocalAIInsights(
        symbol: String,
        optionChain: OptionChain,
        pcr: Double,
        maxPainStrike: Double,
        indiaVix: Double?,
        intradayChangePct: Double?
    ): Flow<Resource<AIAnalysisResult>> = flow {
        emit(Resource.Loading())
        try {
            val result = withContext(Dispatchers.IO) {
                Log.d(TAG, "Running LOCAL AI analysis for $symbol with ${optionChain.rows.size} strikes, intradayChange=$intradayChangePct")

                // Use local scoring engine to analyze the option chain
                val localResult = optionScoringEngine.analyzeOptionChain(
                    optionChain = optionChain,
                    pcr = pcr,
                    maxPainStrike = maxPainStrike,
                    indiaVix = indiaVix,
                    intradayChangePct = intradayChangePct
                )

                Log.d(TAG, "Local analysis complete: ${localResult.callSuggestions.size} calls, ${localResult.putSuggestions.size} puts")

                // Convert local result to AIAnalysisResult format
                val allSuggestions = localResult.callSuggestions + localResult.putSuggestions

                // Determine market sentiment from bias
                val marketSentiment = when (localResult.marketBias) {
                    MarketBias.STRONG_BULLISH,
                    MarketBias.BULLISH -> MarketSentiment.BULLISH
                    MarketBias.STRONG_BEARISH,
                    MarketBias.BEARISH -> MarketSentiment.BEARISH
                    else -> MarketSentiment.NEUTRAL
                }

                // Calculate IV percentile estimate from ATM IV
                val atmRow = optionChain.rows.minByOrNull { kotlin.math.abs(it.strikePrice - optionChain.atmStrike) }
                val atmIV = atmRow?.callData?.impliedVolatility
                    ?: atmRow?.putData?.impliedVolatility
                    ?: 15.0
                val ivPercentile = when {
                    atmIV < 12 -> 20.0
                    atmIV < 15 -> 35.0
                    atmIV < 20 -> 50.0
                    atmIV < 25 -> 65.0
                    atmIV < 30 -> 80.0
                    else -> 90.0
                }

                Resource.Success(
                    AIAnalysisResult(
                        symbol = symbol,
                        spotPrice = optionChain.spotPrice,
                        marketSentiment = marketSentiment,
                        suggestions = allSuggestions.map { suggestion ->
                            // Update symbol in suggestions
                            suggestion.copy(symbol = symbol)
                        },
                        insights = localResult.marketInsights,
                        pcr = pcr,
                        ivPercentile = ivPercentile,
                        maxPain = maxPainStrike
                    )
                )
            }
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "Local analysis failed: ${e.message}", e)
            emit(Resource.Error(e.message ?: "Unknown error occurred"))
        }
    }
}
