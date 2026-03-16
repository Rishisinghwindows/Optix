package com.optix.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.optix.app.data.local.db.entity.SuggestionInteractionEntity
import com.optix.app.data.local.db.entity.TradingPatternCacheEntity
import com.optix.app.data.local.db.entity.WatchlistEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for watchlist operations
 */
@Dao
interface WatchlistDao {

    // ============================
    // Watchlist Operations
    // ============================

    @Query("SELECT * FROM watchlist WHERE isActive = 1 ORDER BY addedAt DESC")
    fun getAllActiveWatchlistItems(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE symbol = :symbol AND isActive = 1")
    fun getWatchlistItemsBySymbol(symbol: String): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist WHERE id = :id")
    suspend fun getWatchlistItemById(id: String): WatchlistEntity?

    @Query("""
        SELECT * FROM watchlist
        WHERE symbol = :symbol
        AND strikePrice = :strikePrice
        AND optionType = :optionType
        AND expiry = :expiry
    """)
    suspend fun findWatchlistItem(
        symbol: String,
        strikePrice: Double,
        optionType: String,
        expiry: String
    ): WatchlistEntity?

    @Query("SELECT DISTINCT symbol FROM watchlist WHERE isActive = 1")
    suspend fun getWatchlistSymbols(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlistItem(item: WatchlistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchlistItems(items: List<WatchlistEntity>)

    @Update
    suspend fun updateWatchlistItem(item: WatchlistEntity)

    @Delete
    suspend fun deleteWatchlistItem(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE id = :id")
    suspend fun deleteWatchlistItemById(id: String)

    @Query("UPDATE watchlist SET isActive = 0 WHERE id = :id")
    suspend fun deactivateWatchlistItem(id: String)

    @Query("""
        UPDATE watchlist
        SET lastViewedAt = :timestamp, viewCount = viewCount + 1
        WHERE id = :id
    """)
    suspend fun recordWatchlistView(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM watchlist WHERE isActive = 1")
    suspend fun getActiveWatchlistCount(): Int

    @Query("""
        SELECT * FROM watchlist
        WHERE isActive = 1
        ORDER BY viewCount DESC, lastViewedAt DESC
        LIMIT :limit
    """)
    suspend fun getMostViewedWatchlistItems(limit: Int): List<WatchlistEntity>

    // ============================
    // Trading Pattern Cache Operations
    // ============================

    @Query("SELECT * FROM trading_pattern_cache WHERE userId = :userId")
    suspend fun getTradingPatternCache(userId: String): TradingPatternCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTradingPatternCache(cache: TradingPatternCacheEntity)

    @Query("DELETE FROM trading_pattern_cache WHERE userId = :userId")
    suspend fun deleteTradingPatternCache(userId: String)

    @Query("""
        SELECT * FROM trading_pattern_cache
        WHERE userId = :userId
        AND lastUpdated > :minTimestamp
    """)
    suspend fun getValidTradingPatternCache(
        userId: String,
        minTimestamp: Long
    ): TradingPatternCacheEntity?

    // ============================
    // Suggestion Interaction Operations
    // ============================

    @Query("""
        SELECT * FROM suggestion_interaction
        WHERE userId = :userId
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentInteractions(userId: String, limit: Int): List<SuggestionInteractionEntity>

    @Query("""
        SELECT * FROM suggestion_interaction
        WHERE userId = :userId
        AND interactionType = :interactionType
        ORDER BY timestamp DESC
    """)
    fun getInteractionsByType(
        userId: String,
        interactionType: String
    ): Flow<List<SuggestionInteractionEntity>>

    @Query("""
        SELECT * FROM suggestion_interaction
        WHERE userId = :userId
        AND timestamp > :sinceTimestamp
    """)
    suspend fun getInteractionsSince(
        userId: String,
        sinceTimestamp: Long
    ): List<SuggestionInteractionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInteraction(interaction: SuggestionInteractionEntity)

    @Query("""
        SELECT COUNT(*) FROM suggestion_interaction
        WHERE userId = :userId
        AND interactionType = 'traded'
    """)
    suspend fun getTradedSuggestionCount(userId: String): Int

    @Query("""
        SELECT suggestionConfidence, COUNT(*) as count
        FROM suggestion_interaction
        WHERE userId = :userId
        AND interactionType = 'traded'
        GROUP BY suggestionConfidence
    """)
    suspend fun getTradedCountByConfidence(userId: String): List<ConfidenceCount>

    @Query("DELETE FROM suggestion_interaction WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldInteractions(beforeTimestamp: Long)
}

/**
 * Helper class for confidence count query
 */
data class ConfidenceCount(
    val suggestionConfidence: String,
    val count: Int
)
