package com.optix.app.domain.model

/**
 * Paper trade record for creating new trades
 */
data class PaperTrade(
    val symbol: String,
    val strikePrice: Double,
    val optionType: String,
    val expiry: String,
    val quantity: Int,
    val price: Double,
    val isBuy: Boolean
)

/**
 * Open/Closed position
 */
data class PaperPosition(
    val id: String,
    val symbol: String,
    val strikePrice: Double,
    val optionType: String,
    val expiry: String,
    val quantity: Int,
    val entryPrice: Double,
    val currentPrice: Double,
    val exitPrice: Double? = null,
    val isBuy: Boolean,
    val openedAt: Long,
    val closedAt: Long? = null
) {
    val pnl: Double
        get() {
            val multiplier = if (isBuy) 1 else -1
            return ((exitPrice ?: currentPrice) - entryPrice) * quantity * multiplier
        }

    val pnlPercent: Double
        get() = if (entryPrice > 0) (pnl / (entryPrice * quantity)) * 100 else 0.0

    val isProfit: Boolean get() = pnl >= 0
}

/**
 * Performance metrics
 */
data class PerformanceMetrics(
    val totalTrades: Int = 0,
    val winningTrades: Int = 0,
    val losingTrades: Int = 0,
    val winRate: Double = 0.0,
    val totalProfit: Double = 0.0,
    val totalLoss: Double = 0.0,
    val netPnL: Double = 0.0,
    val averageProfit: Double = 0.0,
    val averageLoss: Double = 0.0,
    val maxProfit: Double = 0.0,
    val maxLoss: Double = 0.0,
    val todayPnL: Double = 0.0
)
