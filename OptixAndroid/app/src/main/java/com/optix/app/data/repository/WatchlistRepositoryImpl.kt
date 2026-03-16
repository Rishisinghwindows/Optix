package com.optix.app.data.repository

import com.optix.app.core.util.Resource
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.WatchlistItem
import com.optix.app.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of WatchlistRepository using in-memory storage.
 *
 * Note: For production, this should be backed by Room database using WatchlistDao.
 * This implementation provides a working solution until Room is fully set up.
 */
@Singleton
class WatchlistRepositoryImpl @Inject constructor() : WatchlistRepository {

    // In-memory storage (replace with Room DAO in production)
    private val _watchlistItems = MutableStateFlow<List<WatchlistItem>>(emptyList())

    override fun getWatchlistItems(): Flow<List<WatchlistItem>> {
        return _watchlistItems.asStateFlow()
    }

    override fun getWatchlistItemsBySymbol(symbol: String): Flow<List<WatchlistItem>> {
        return _watchlistItems.map { items ->
            items.filter { it.symbol.equals(symbol, ignoreCase = true) }
        }
    }

    override suspend fun getWatchlistItem(id: String): Resource<WatchlistItem> {
        val item = _watchlistItems.value.find { it.id == id }
        return if (item != null) {
            Resource.Success(item)
        } else {
            Resource.Error("Watchlist item not found")
        }
    }

    override suspend fun isInWatchlist(
        symbol: String,
        strikePrice: Double?,
        optionType: String?,
        expiry: String?
    ): Boolean {
        return _watchlistItems.value.any { item ->
            item.symbol.equals(symbol, ignoreCase = true) &&
                    (strikePrice == null || item.strikePrice == strikePrice) &&
                    (optionType == null || item.optionType?.code?.equals(optionType, ignoreCase = true) == true ||
                            item.optionType?.displayName?.equals(optionType, ignoreCase = true) == true) &&
                    (expiry == null || item.expiry == expiry)
        }
    }

    override suspend fun addToWatchlist(item: WatchlistItem): Resource<WatchlistItem> {
        return try {
            // Check if already exists
            val exists = isInWatchlist(
                symbol = item.symbol,
                strikePrice = item.strikePrice,
                optionType = item.optionType?.code,
                expiry = item.expiry
            )

            if (exists) {
                return Resource.Error("Item already in watchlist")
            }

            // Generate ID if not provided
            val itemWithId = if (item.id.isEmpty()) {
                item.copy(id = UUID.randomUUID().toString())
            } else {
                item
            }

            _watchlistItems.value = _watchlistItems.value + itemWithId
            Resource.Success(itemWithId)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to add to watchlist")
        }
    }

    override suspend fun removeFromWatchlist(id: String): Resource<Boolean> {
        return try {
            val initialSize = _watchlistItems.value.size
            _watchlistItems.value = _watchlistItems.value.filter { it.id != id }
            val removed = _watchlistItems.value.size < initialSize
            if (removed) {
                Resource.Success(true)
            } else {
                Resource.Error("Item not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to remove from watchlist")
        }
    }

    override suspend fun updateWatchlistItem(item: WatchlistItem): Resource<WatchlistItem> {
        return try {
            val index = _watchlistItems.value.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                val updatedList = _watchlistItems.value.toMutableList()
                updatedList[index] = item
                _watchlistItems.value = updatedList
                Resource.Success(item)
            } else {
                Resource.Error("Item not found")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update watchlist item")
        }
    }

    override suspend fun getWatchlistSymbols(): List<String> {
        return _watchlistItems.value
            .map { it.symbol }
            .distinct()
    }

    override suspend fun getWatchlistCount(): Int {
        return _watchlistItems.value.size
    }

    override suspend fun recordView(id: String) {
        val index = _watchlistItems.value.indexOfFirst { it.id == id }
        if (index >= 0) {
            // In production, update view count and timestamp via DAO
            // For now, this is a no-op in memory implementation
        }
    }
}
