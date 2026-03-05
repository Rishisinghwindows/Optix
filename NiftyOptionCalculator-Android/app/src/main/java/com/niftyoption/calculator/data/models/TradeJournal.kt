package com.niftyoption.calculator.data.models

data class JournalEntry(
    val id: String = "",
    val user_id: String = "",
    val symbol: String,
    val strike_price: Double,
    val option_type: String,  // CE or PE
    val direction: String,    // buy or sell
    val entry_price: Double,
    val exit_price: Double? = null,
    val quantity: Int,
    val lot_size: Int,
    val entry_date: String,
    val exit_date: String? = null,
    val expiry_date: String? = null,
    val realized_pnl: Double? = null,
    val realized_pnl_percent: Double? = null,
    val title: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    val market_condition: String? = null,
    val mood: String? = null,
    val outcome: String? = null,
    val paper_position_id: String? = null,
    val created_at: String = "",
    val updated_at: String = ""
)

data class JournalListResponse(
    val entries: List<JournalEntry>,
    val total: Int
)

data class JournalStats(
    val total_trades: Int,
    val winning_trades: Int,
    val losing_trades: Int,
    val breakeven_trades: Int,
    val win_rate: Double,
    val total_pnl: Double,
    val average_pnl: Double,
    val best_trade: Double? = null,
    val worst_trade: Double? = null,
    val average_holding_days: Double? = null,
    val most_traded_symbol: String? = null,
    val most_used_tags: List<String> = emptyList(),
    val trades_by_mood: Map<String, Int> = emptyMap(),
    val trades_by_condition: Map<String, Int> = emptyMap()
)

data class JournalCreateRequest(
    val symbol: String,
    val strike_price: Double,
    val option_type: String,
    val direction: String,
    val entry_price: Double,
    val exit_price: Double? = null,
    val quantity: Int,
    val lot_size: Int,
    val entry_date: String,
    val exit_date: String? = null,
    val expiry_date: String? = null,
    val title: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    val market_condition: String? = null,
    val mood: String? = null,
    val outcome: String? = null
)

data class JournalUpdateRequest(
    val exit_price: Double? = null,
    val exit_date: String? = null,
    val title: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    val market_condition: String? = null,
    val mood: String? = null,
    val outcome: String? = null
)

enum class JournalTag(val displayName: String) {
    SCALP("Scalp"),
    SWING("Swing"),
    HEDGING("Hedging"),
    SPREAD("Spread"),
    DIRECTIONAL("Directional"),
    EXPIRY_DAY("Expiry Day");

    val apiValue: String get() = name.lowercase()
}

enum class TradeMood(val displayName: String, val emoji: String) {
    CONFIDENT("Confident", "\uD83D\uDCAA"),
    NERVOUS("Nervous", "\uD83D\uDE30"),
    NEUTRAL("Neutral", "\uD83D\uDE10"),
    GREEDY("Greedy", "\uD83E\uDD11"),
    FEARFUL("Fearful", "\uD83D\uDE28"),
    DISCIPLINED("Disciplined", "\uD83E\uDDD8");

    val apiValue: String get() = name.lowercase()
}

enum class MarketCondition(val displayName: String) {
    TRENDING_UP("Trending Up"),
    TRENDING_DOWN("Trending Down"),
    RANGE_BOUND("Range Bound"),
    VOLATILE("Volatile"),
    LOW_VOL("Low Vol");

    val apiValue: String get() = name.lowercase()
}

enum class TradeOutcome(val displayName: String) {
    PROFIT("Profit"),
    LOSS("Loss"),
    BREAKEVEN("Breakeven");

    val apiValue: String get() = name.lowercase()
}
