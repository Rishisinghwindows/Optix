package com.optix.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============== Request DTOs ==============

@Serializable
data class JournalCreateRequest(
    val symbol: String,
    @SerialName("strike_price") val strikePrice: Double,
    @SerialName("option_type") val optionType: String,
    val direction: String,
    @SerialName("entry_price") val entryPrice: Double,
    @SerialName("exit_price") val exitPrice: Double? = null,
    val quantity: Int,
    @SerialName("lot_size") val lotSize: Int,
    @SerialName("entry_date") val entryDate: String,
    @SerialName("exit_date") val exitDate: String? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
    val title: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    @SerialName("market_condition") val marketCondition: String? = null,
    val mood: String? = null,
    val outcome: String? = null,
    @SerialName("paper_position_id") val paperPositionId: String? = null
)

@Serializable
data class JournalUpdateRequest(
    @SerialName("exit_price") val exitPrice: Double? = null,
    @SerialName("exit_date") val exitDate: String? = null,
    val title: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    @SerialName("market_condition") val marketCondition: String? = null,
    val mood: String? = null,
    val outcome: String? = null
)

// ============== Response DTOs ==============

@Serializable
data class JournalEntryDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val symbol: String,
    @SerialName("strike_price") val strikePrice: Double,
    @SerialName("option_type") val optionType: String,
    val direction: String,
    @SerialName("entry_price") val entryPrice: Double,
    @SerialName("exit_price") val exitPrice: Double? = null,
    val quantity: Int,
    @SerialName("lot_size") val lotSize: Int,
    @SerialName("entry_date") val entryDate: String,
    @SerialName("exit_date") val exitDate: String? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("realized_pnl") val realizedPnl: Double? = null,
    @SerialName("realized_pnl_percent") val realizedPnlPercent: Double? = null,
    val title: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    @SerialName("market_condition") val marketCondition: String? = null,
    val mood: String? = null,
    val outcome: String? = null,
    @SerialName("paper_position_id") val paperPositionId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class JournalListResponse(
    val entries: List<JournalEntryDto>,
    val total: Int
)

@Serializable
data class JournalStatsDto(
    @SerialName("total_trades") val totalTrades: Int,
    @SerialName("winning_trades") val winningTrades: Int,
    @SerialName("losing_trades") val losingTrades: Int,
    @SerialName("breakeven_trades") val breakevenTrades: Int,
    @SerialName("win_rate") val winRate: Double,
    @SerialName("total_pnl") val totalPnl: Double,
    @SerialName("average_pnl") val averagePnl: Double,
    @SerialName("best_trade") val bestTrade: Double? = null,
    @SerialName("worst_trade") val worstTrade: Double? = null,
    @SerialName("average_holding_days") val averageHoldingDays: Double? = null,
    @SerialName("most_traded_symbol") val mostTradedSymbol: String? = null,
    @SerialName("most_used_tags") val mostUsedTags: List<String> = emptyList(),
    @SerialName("trades_by_mood") val tradesByMood: Map<String, Int> = emptyMap(),
    @SerialName("trades_by_condition") val tradesByCondition: Map<String, Int> = emptyMap()
)

@Serializable
data class JournalDeleteResponse(
    val message: String,
    val id: String
)
