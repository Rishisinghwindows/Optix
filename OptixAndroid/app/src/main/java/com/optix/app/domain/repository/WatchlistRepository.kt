package com.optix.app.domain.repository

import com.optix.app.core.util.Resource
import com.optix.app.domain.model.WatchlistItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for watchlist operations
 */
interface WatchlistRepository {

    /**
     * Get all active watchlist items as a Flow
     */
    fun getWatchlistItems(): Flow<List<WatchlistItem>>

    /**
     * Get watchlist items for a specific symbol
     */
    fun getWatchlistItemsBySymbol(symbol: String): Flow<List<WatchlistItem>>

    /**
     * Get a single watchlist item by ID
     */
    suspend fun getWatchlistItem(id: String): Resource<WatchlistItem>

    /**
     * Check if an item is in the watchlist
     */
    suspend fun isInWatchlist(
        symbol: String,
        strikePrice: Double? = null,
        optionType: String? = null,
        expiry: String? = null
    ): Boolean

    /**
     * Add item to watchlist
     */
    suspend fun addToWatchlist(item: WatchlistItem): Resource<WatchlistItem>

    /**
     * Remove item from watchlist
     */
    suspend fun removeFromWatchlist(id: String): Resource<Boolean>

    /**
     * Update watchlist item
     */
    suspend fun updateWatchlistItem(item: WatchlistItem): Resource<WatchlistItem>

    /**
     * Get all unique symbols in watchlist
     */
    suspend fun getWatchlistSymbols(): List<String>

    /**
     * Get watchlist count
     */
    suspend fun getWatchlistCount(): Int

    /**
     * Record a view for analytics
     */
    suspend fun recordView(id: String)
}
