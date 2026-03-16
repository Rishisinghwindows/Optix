package com.optix.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for watchlist items
 */
@Entity(
    tableName = "watchlist",
    indices = [
        Index(value = ["symbol"]),
        Index(value = ["symbol", "strikePrice", "optionType", "expiry"], unique = true)
    ]
)
data class WatchlistEntity(
    @PrimaryKey
    val id: String,

    val symbol: String,

    val strikePrice: Double? = null,

    val optionType: String? = null, // "CALL" or "PUT"

    val expiry: String? = null,

    val addedAt: Long = System.currentTimeMillis(),

    val notes: String? = null,

    val alertPriceTarget: Double? = null,

    val isActive: Boolean = true,

    val lastViewedAt: Long? = null,

    val viewCount: Int = 0
)

/**
 * Room entity for trading history analysis cache
 */
@Entity(
    tableName = "trading_pattern_cache",
    indices = [Index(value = ["userId"])]
)
data class TradingPatternCacheEntity(
    @PrimaryKey
    val userId: String,

    val preferredOptionType: String?, // "CALL" or "PUT"

    val preferredDirection: String?, // "BUY" or "SELL"

    val averageHoldingPeriodHours: Double,

    val profitableHours: String, // Comma-separated hours

    val preferredStrikeDistance: Int,

    val winRateHigh: Double,

    val winRateMedium: Double,

    val winRateLow: Double,

    val totalTrades: Int,

    val winningTrades: Int,

    val averageProfitPercent: Double,

    val averageLossPercent: Double,

    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Room entity for AI suggestion interaction history
 */
@Entity(
    tableName = "suggestion_interaction",
    indices = [
        Index(value = ["suggestionId"]),
        Index(value = ["userId"]),
        Index(value = ["timestamp"])
    ]
)
data class SuggestionInteractionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val userId: String,

    val suggestionId: String,

    val interactionType: String, // "viewed", "dismissed", "added_to_watchlist", "traded"

    val suggestionSymbol: String,

    val suggestionStrike: Double,

    val suggestionType: String, // "CALL" or "PUT"

    val suggestionScore: Int,

    val suggestionConfidence: String, // "HIGH", "MEDIUM", "LOW"

    val timestamp: Long = System.currentTimeMillis()
)
