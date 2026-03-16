package com.optix.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AIInsightsResponse(
    val symbol: String? = null,
    @SerialName("spot_price") val spotPrice: Double? = null,
    val sentiment: String? = null,
    val suggestions: List<AISuggestionDto>? = null,
    val insights: List<AIMarketInsightDto>? = null,
    val pcr: Double? = null,
    @SerialName("iv_percentile") val ivPercentile: Double? = null,
    @SerialName("max_pain") val maxPain: Double? = null,
    @SerialName("india_vix") val indiaVix: Double? = null,
    @SerialName("vix_status") val vixStatus: String? = null
)

@Serializable
data class AISuggestionDto(
    val id: String? = null,
    @SerialName("strike_price") val strikePrice: Double? = null,
    @SerialName("option_type") val optionType: String? = null,
    val expiry: String? = null,
    val action: String? = null,
    @SerialName("entry_price") val entryPrice: Double? = null,
    @SerialName("target_price") val targetPrice: Double? = null,
    @SerialName("stop_loss") val stopLoss: Double? = null,
    val confidence: String? = null,
    val score: Int? = null,
    val reasoning: List<AIReasoningDto>? = null,
    @SerialName("risk_reward") val riskReward: Double? = null,
    @SerialName("max_profit") val maxProfit: Double? = null,
    @SerialName("max_loss") val maxLoss: Double? = null
)

@Serializable
data class AIReasoningDto(
    val factor: String? = null,
    val score: Int? = null,
    @SerialName("max_score") val maxScore: Int? = null,
    val description: String? = null,
    @SerialName("is_positive") val isPositive: Boolean? = null
)

@Serializable
data class AIMarketInsightDto(
    val title: String? = null,
    val description: String? = null,
    val sentiment: String? = null,
    val importance: String? = null
)

// ============== Ask AI Trade Analysis (matches iOS/backend) ==============

@Serializable
data class AnalyzeTradeRequest(
    val option: OptionDataRequest,
    @SerialName("market_context") val marketContext: MarketContextRequest,
    val suggestion: SuggestionRequest,
    @SerialName("option_chain") val optionChain: List<OptionChainRowRequest>? = null
)

@Serializable
data class OptionDataRequest(
    @SerialName("strike_price") val strikePrice: Double,
    @SerialName("option_type") val optionType: String,
    val ltp: Double,
    val iv: Double = 0.0,
    val delta: Double? = null,
    val theta: Double? = null,
    @SerialName("open_interest") val openInterest: Int = 0,
    @SerialName("oi_change") val oiChange: Int = 0,
    val volume: Int = 0,
    @SerialName("days_to_expiry") val daysToExpiry: Int = 0
)

@Serializable
data class MarketContextRequest(
    @SerialName("spot_price") val spotPrice: Double,
    val pcr: Double = 1.0,
    @SerialName("max_pain") val maxPain: Double? = null,
    @SerialName("atm_strike") val atmStrike: Double? = null,
    @SerialName("index_name") val indexName: String = "NIFTY",
    val support: Double? = null,
    val resistance: Double? = null,
    @SerialName("india_vix") val indiaVix: Double? = null,
    @SerialName("total_call_oi") val totalCallOi: Long? = null,
    @SerialName("total_put_oi") val totalPutOi: Long? = null
)

@Serializable
data class SuggestionRequest(
    val entry: Double,
    val target: Double,
    @SerialName("stop_loss") val stopLoss: Double,
    @SerialName("risk_reward") val riskReward: String = "1:1",
    val score: Int = 50
)

@Serializable
data class OptionChainRowRequest(
    val strike: Double,
    @SerialName("call_oi") val callOi: Long = 0,
    @SerialName("call_oi_change") val callOiChange: Long = 0,
    @SerialName("call_ltp") val callLtp: Double = 0.0,
    @SerialName("call_iv") val callIv: Double = 0.0,
    @SerialName("call_volume") val callVolume: Long = 0,
    @SerialName("call_delta") val callDelta: Double? = null,
    @SerialName("call_theta") val callTheta: Double? = null,
    @SerialName("put_oi") val putOi: Long = 0,
    @SerialName("put_oi_change") val putOiChange: Long = 0,
    @SerialName("put_ltp") val putLtp: Double = 0.0,
    @SerialName("put_iv") val putIv: Double = 0.0,
    @SerialName("put_volume") val putVolume: Long = 0,
    @SerialName("put_delta") val putDelta: Double? = null,
    @SerialName("put_theta") val putTheta: Double? = null
)

@Serializable
data class AnalyzeTradeResponse(
    val verdict: String = "WAIT",
    @SerialName("win_probability") val winProbability: Int = 50,
    @SerialName("key_reason") val keyReason: String = "Analysis completed",
    @SerialName("risk_warning") val riskWarning: String = "Always use stop-loss",
    @SerialName("better_alternative") val betterAlternative: String? = null,
    @SerialName("support_level") val supportLevel: Int? = null,
    @SerialName("resistance_level") val resistanceLevel: Int? = null,
    @SerialName("ai_powered") val aiPowered: Boolean = false,
    val disclaimer: String = "AI analysis is for educational purposes only."
)

// Legacy DTOs (keep for compatibility)
@Serializable
data class AnalyzeOptionRequest(
    val symbol: String,
    @SerialName("strike_price") val strikePrice: Double,
    @SerialName("option_type") val optionType: String,
    val expiry: String,
    @SerialName("spot_price") val spotPrice: Double
)

@Serializable
data class AIAnalysisResponse(
    val analysis: AIAnalysisDto? = null
)

@Serializable
data class AIAnalysisDto(
    val score: Double? = null,
    val reasoning: List<AIReasoningDto>? = null,
    val recommendation: String? = null
)

// Chat DTOs
@Serializable
data class ChatRequest(
    val message: String,
    @SerialName("session_id") val sessionId: String? = null,
    val context: ChatContextDto? = null
)

@Serializable
data class ChatResponse(
    val reply: String,
    @SerialName("session_id") val sessionId: String
)

@Serializable
data class QuickChatRequest(
    val message: String,
    val context: ChatContextDto? = null
)

@Serializable
data class QuickChatResponse(
    val response: String? = null,
    val message: String? = null,
    val suggestions: List<String>? = null
) {
    // Backend returns both 'response' and 'message' - use whichever is available
    val text: String get() = response ?: message ?: ""
}

@Serializable
data class ChatContextDto(
    @SerialName("spot_price") val spotPrice: Double? = null,
    val pcr: Double? = null,
    @SerialName("selected_expiry") val selectedExpiry: String? = null,
    @SerialName("selected_strike") val selectedStrike: Double? = null,
    @SerialName("iv_percentile") val ivPercentile: Double? = null,
    @SerialName("market_trend") val marketTrend: String? = null,
    val symbol: String? = null
)

@Serializable
data class ChatSessionsResponse(
    val sessions: List<ChatSessionDto>
)

@Serializable
data class ChatSessionDto(
    val id: String,
    val title: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("message_count") val messageCount: Int? = null
)

// IPO DTOs
@Serializable
data class IPOListResponse(
    val ipos: List<IPODto>
)

@Serializable
data class IPODetailResponse(
    val ipo: IPODto
)

@Serializable
data class IPODto(
    val id: String,
    val name: String,
    @SerialName("company_name") val companyName: String,
    val symbol: String? = null,
    @SerialName("issue_size") val issueSize: Double? = null,
    @SerialName("issue_price_min") val issuePriceMin: Double? = null,
    @SerialName("issue_price_max") val issuePriceMax: Double? = null,
    @SerialName("lot_size") val lotSize: Int? = null,
    @SerialName("open_date") val openDate: String? = null,
    @SerialName("close_date") val closeDate: String? = null,
    @SerialName("listing_date") val listingDate: String? = null,
    val status: String? = null,
    val gmp: Double? = null,
    @SerialName("subscription_status") val subscriptionStatus: SubscriptionStatusDto? = null
)

@Serializable
data class SubscriptionStatusDto(
    val retail: Double? = null,
    val nii: Double? = null,
    val qib: Double? = null,
    val total: Double? = null
)
