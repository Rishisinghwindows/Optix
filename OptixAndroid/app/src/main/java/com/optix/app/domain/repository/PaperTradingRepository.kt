package com.optix.app.domain.repository

import com.optix.app.core.util.Resource
import com.optix.app.domain.model.PaperPosition
import com.optix.app.domain.model.PaperTrade
import com.optix.app.domain.model.PerformanceMetrics

/**
 * Repository interface for paper trading
 */
interface PaperTradingRepository {
    /**
     * Get open positions
     */
    suspend fun getOpenPositions(): Resource<List<PaperPosition>>

    /**
     * Get closed positions (history)
     */
    suspend fun getClosedPositions(): Resource<List<PaperPosition>>

    /**
     * Open a new position
     */
    suspend fun openPosition(trade: PaperTrade): Resource<PaperPosition>

    /**
     * Close a position
     */
    suspend fun closePosition(positionId: String): Resource<PaperPosition>

    /**
     * Get performance metrics
     */
    suspend fun getPerformanceMetrics(): Resource<PerformanceMetrics>
}
