package com.optix.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PickOutcome(val displayName: String) {
    ACTIVE("Active"),
    WIN("Win"),
    LOSS("Loss"),
    EXPIRED("Expired")
}

@Serializable
data class TrackedPick(
    val id: String,
    val indexName: String,
    val strikePrice: Double,
    val optionType: String,  // "CE" or "PE"
    val entryPrice: Double,
    val targetPrice: Double,
    val stopLossPrice: Double,
    val score: Int,
    val tier: String,  // "topPick" or "worthWatching"
    val createdAt: Long = System.currentTimeMillis(),
    val expiryDate: String = "",
    var outcome: PickOutcome = PickOutcome.ACTIVE,
    var exitPrice: Double? = null,
    var returnPct: Double? = null,
    var highWaterMark: Double? = null,
    var resolvedAt: Long? = null
) {
    companion object {
        fun compositeKey(indexName: String, strike: Double, optionType: String, expiry: String): String {
            return "${indexName}_${strike.toInt()}_${optionType}_${expiry}"
        }

        fun from(suggestion: AITradeSuggestion, indexName: String): TrackedPick {
            val key = compositeKey(indexName, suggestion.strikePrice, suggestion.optionType.code, suggestion.expiry)
            return TrackedPick(
                id = key,
                indexName = indexName,
                strikePrice = suggestion.strikePrice,
                optionType = suggestion.optionType.code,
                entryPrice = suggestion.entryPrice,
                targetPrice = suggestion.targetPrice,
                stopLossPrice = suggestion.stopLoss,
                score = suggestion.score,
                tier = if (suggestion.score >= 62) "topPick" else "worthWatching",
                expiryDate = suggestion.expiry,
                highWaterMark = suggestion.entryPrice
            )
        }
    }
}

data class ScorecardStats(
    val totalPicks: Int = 0,
    val activePicks: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val expired: Int = 0,
    val winRate: Double = 0.0,
    val avgReturn: Double = 0.0,
    val topPickWinRate: Double = 0.0,
    val recentPicks: List<TrackedPick> = emptyList()
)
