package com.optix.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TradeRequest(
    val symbol: String,
    @SerialName("strike_price") val strikePrice: Double,
    @SerialName("option_type") val optionType: String,
    val expiry: String,
    val direction: String,
    val quantity: Int,
    val price: Double
)

@Serializable
data class ClosePositionRequest(
    @SerialName("exit_price") val exitPrice: Double
)

@Serializable
data class TradeResponse(
    val success: Boolean,
    val trade: TradeDto? = null,
    val message: String? = null
)

@Serializable
data class TradeDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val symbol: String,
    @SerialName("strike_price") val strikePrice: Double,
    @SerialName("option_type") val optionType: String,
    val expiry: String,
    val direction: String,
    val quantity: Int,
    @SerialName("entry_price") val entryPrice: Double,
    @SerialName("exit_price") val exitPrice: Double? = null,
    val status: String = "open",
    val pnl: Double = 0.0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("closed_at") val closedAt: String? = null
)

@Serializable
data class PositionDto(
    val id: String,
    val symbol: String,
    @SerialName("strike_price") val strikePrice: Double,
    @SerialName("option_type") val optionType: String,
    val expiry: String,
    val direction: String,
    val quantity: Int,
    @SerialName("avg_entry_price") val avgEntryPrice: Double,
    @SerialName("current_price") val currentPrice: Double = 0.0,
    @SerialName("unrealized_pnl") val unrealizedPnl: Double = 0.0,
    @SerialName("unrealized_pnl_percent") val unrealizedPnlPercent: Double = 0.0,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class PositionsResponse(
    val positions: List<PositionDto>,
    val total: Int,
    @SerialName("total_unrealized_pnl") val totalUnrealizedPnl: Double = 0.0
)

@Serializable
data class TradeHistoryResponse(
    val trades: List<TradeDto>,
    val total: Int
)

@Serializable
data class PerformanceResponse(
    @SerialName("total_trades") val totalTrades: Int,
    @SerialName("winning_trades") val winningTrades: Int,
    @SerialName("losing_trades") val losingTrades: Int,
    @SerialName("total_pnl") val totalPnl: Double,
    @SerialName("total_profit") val totalProfit: Double,
    @SerialName("total_loss") val totalLoss: Double,
    @SerialName("win_rate") val winRate: Double,
    @SerialName("avg_profit") val avgProfit: Double,
    @SerialName("avg_loss") val avgLoss: Double,
    @SerialName("profit_factor") val profitFactor: Double,
    @SerialName("sharpe_ratio") val sharpeRatio: Double = 0.0,
    @SerialName("max_drawdown") val maxDrawdown: Double = 0.0
)
