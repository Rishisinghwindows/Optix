package com.optix.app.domain.repository

import com.optix.app.core.util.Resource
import com.optix.app.domain.model.JournalEntry
import com.optix.app.domain.model.JournalStats

/**
 * Repository interface for trade journal operations
 */
interface JournalRepository {

    /**
     * Get journal entries with optional filters
     */
    suspend fun getEntries(
        outcome: String? = null,
        symbol: String? = null,
        tag: String? = null,
        mood: String? = null,
        limit: Int = 100,
        offset: Int = 0
    ): Resource<Pair<List<JournalEntry>, Int>>

    /**
     * Get journal statistics
     */
    suspend fun getStats(): Resource<JournalStats>

    /**
     * Create a new journal entry
     */
    suspend fun createEntry(
        symbol: String,
        strikePrice: Double,
        optionType: String,
        direction: String,
        entryPrice: Double,
        exitPrice: Double?,
        quantity: Int,
        lotSize: Int,
        entryDate: String,
        exitDate: String?,
        expiryDate: String?,
        title: String?,
        notes: String?,
        tags: String?,
        marketCondition: String?,
        mood: String?,
        outcome: String?,
        paperPositionId: String?
    ): Resource<JournalEntry>

    /**
     * Update an existing journal entry
     */
    suspend fun updateEntry(
        id: String,
        exitPrice: Double?,
        exitDate: String?,
        title: String?,
        notes: String?,
        tags: String?,
        marketCondition: String?,
        mood: String?,
        outcome: String?
    ): Resource<JournalEntry>

    /**
     * Delete a journal entry
     */
    suspend fun deleteEntry(id: String): Resource<Boolean>
}
