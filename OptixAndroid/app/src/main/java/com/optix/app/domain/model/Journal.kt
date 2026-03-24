package com.optix.app.domain.model

/**
 * Domain model for a trade journal entry
 */
data class JournalEntry(
    val id: String,
    val userId: String,
    val symbol: String,
    val strikePrice: Double,
    val optionType: String,
    val direction: String,
    val entryPrice: Double,
    val exitPrice: Double? = null,
    val quantity: Int,
    val lotSize: Int,
    val entryDate: String,
    val exitDate: String? = null,
    val expiryDate: String? = null,
    val realizedPnl: Double? = null,
    val realizedPnlPercent: Double? = null,
    val title: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    val marketCondition: String? = null,
    val mood: String? = null,
    val outcome: String? = null,
    val paperPositionId: String? = null,
    val createdAt: String = "",
    val updatedAt: String = ""
)

/**
 * Domain model for journal statistics
 */
data class JournalStats(
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val breakevenTrades: Int,
    val winRate: Double,
    val totalPnl: Double,
    val averagePnl: Double,
    val bestTrade: Double? = null,
    val worstTrade: Double? = null,
    val averageHoldingDays: Double? = null,
    val mostTradedSymbol: String? = null,
    val mostUsedTags: List<String> = emptyList(),
    val tradesByMood: Map<String, Int> = emptyMap(),
    val tradesByCondition: Map<String, Int> = emptyMap()
)

/**
 * Journal tag options
 */
enum class JournalTag(val displayName: String) {
    SCALP("Scalp"),
    SWING("Swing"),
    HEDGING("Hedging"),
    SPREAD("Spread"),
    DIRECTIONAL("Directional"),
    EXPIRY_DAY("Expiry Day");

    val apiValue: String get() = name.lowercase()

    companion object {
        fun fromApiValue(value: String): JournalTag? {
            return entries.find { it.apiValue == value.lowercase().trim() }
        }
    }
}

/**
 * Trade mood options
 */
enum class TradeMood(val displayName: String, val emoji: String) {
    CONFIDENT("Confident", "\uD83D\uDCAA"),
    NERVOUS("Nervous", "\uD83D\uDE30"),
    NEUTRAL("Neutral", "\uD83D\uDE10"),
    GREEDY("Greedy", "\uD83E\uDD11"),
    FEARFUL("Fearful", "\uD83D\uDE28"),
    DISCIPLINED("Disciplined", "\uD83E\uDDD8");

    val apiValue: String get() = name.lowercase()

    companion object {
        fun fromApiValue(value: String): TradeMood? {
            return entries.find { it.apiValue == value.lowercase().trim() }
        }
    }
}

/**
 * Market condition options
 */
enum class MarketCondition(val displayName: String) {
    TRENDING_UP("Trending Up"),
    TRENDING_DOWN("Trending Down"),
    RANGE_BOUND("Range Bound"),
    VOLATILE("Volatile"),
    LOW_VOL("Low Vol");

    val apiValue: String get() = name.lowercase()

    companion object {
        fun fromApiValue(value: String): MarketCondition? {
            return entries.find { it.apiValue == value.lowercase().trim() }
        }
    }
}

/**
 * Trade outcome
 */
enum class TradeOutcome(val displayName: String) {
    PROFIT("Profit"),
    LOSS("Loss"),
    BREAKEVEN("Breakeven");

    val apiValue: String get() = name.lowercase()

    companion object {
        fun fromApiValue(value: String): TradeOutcome? {
            return entries.find { it.apiValue == value.lowercase().trim() }
        }
    }
}
