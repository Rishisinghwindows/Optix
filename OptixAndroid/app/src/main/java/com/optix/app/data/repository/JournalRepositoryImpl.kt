package com.optix.app.data.repository

import com.optix.app.core.util.Resource
import com.optix.app.data.remote.api.OptixApiService
import com.optix.app.data.remote.dto.JournalCreateRequest
import com.optix.app.data.remote.dto.JournalEntryDto
import com.optix.app.data.remote.dto.JournalUpdateRequest
import com.optix.app.domain.model.JournalEntry
import com.optix.app.domain.model.JournalStats
import com.optix.app.domain.repository.JournalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepositoryImpl @Inject constructor(
    private val api: OptixApiService
) : JournalRepository {

    override suspend fun getEntries(
        outcome: String?,
        symbol: String?,
        tag: String?,
        mood: String?,
        limit: Int,
        offset: Int
    ): Resource<Pair<List<JournalEntry>, Int>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getJournalEntries(
                outcome = outcome,
                symbol = symbol,
                tag = tag,
                mood = mood,
                limit = limit,
                offset = offset
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val entries = body.entries.map { it.toDomain() }
                Resource.Success(Pair(entries, body.total))
            } else {
                Resource.Error("Failed to load journal entries: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error loading journal entries")
        }
    }

    override suspend fun getStats(): Resource<JournalStats> = withContext(Dispatchers.IO) {
        try {
            val response = api.getJournalStats()
            if (response.isSuccessful && response.body() != null) {
                val dto = response.body()!!
                Resource.Success(
                    JournalStats(
                        totalTrades = dto.totalTrades,
                        winningTrades = dto.winningTrades,
                        losingTrades = dto.losingTrades,
                        breakevenTrades = dto.breakevenTrades,
                        winRate = dto.winRate,
                        totalPnl = dto.totalPnl,
                        averagePnl = dto.averagePnl,
                        bestTrade = dto.bestTrade,
                        worstTrade = dto.worstTrade,
                        averageHoldingDays = dto.averageHoldingDays,
                        mostTradedSymbol = dto.mostTradedSymbol,
                        mostUsedTags = dto.mostUsedTags,
                        tradesByMood = dto.tradesByMood,
                        tradesByCondition = dto.tradesByCondition
                    )
                )
            } else {
                Resource.Error("Failed to load journal stats: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error loading journal stats")
        }
    }

    override suspend fun createEntry(
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
    ): Resource<JournalEntry> = withContext(Dispatchers.IO) {
        try {
            val request = JournalCreateRequest(
                symbol = symbol,
                strikePrice = strikePrice,
                optionType = optionType,
                direction = direction,
                entryPrice = entryPrice,
                exitPrice = exitPrice,
                quantity = quantity,
                lotSize = lotSize,
                entryDate = entryDate,
                exitDate = exitDate,
                expiryDate = expiryDate,
                title = title,
                notes = notes,
                tags = tags,
                marketCondition = marketCondition,
                mood = mood,
                outcome = outcome,
                paperPositionId = paperPositionId
            )
            val response = api.createJournalEntry(request)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!.toDomain())
            } else {
                Resource.Error("Failed to create journal entry: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error creating journal entry")
        }
    }

    override suspend fun updateEntry(
        id: String,
        exitPrice: Double?,
        exitDate: String?,
        title: String?,
        notes: String?,
        tags: String?,
        marketCondition: String?,
        mood: String?,
        outcome: String?
    ): Resource<JournalEntry> = withContext(Dispatchers.IO) {
        try {
            val request = JournalUpdateRequest(
                exitPrice = exitPrice,
                exitDate = exitDate,
                title = title,
                notes = notes,
                tags = tags,
                marketCondition = marketCondition,
                mood = mood,
                outcome = outcome
            )
            val response = api.updateJournalEntry(id, request)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!.toDomain())
            } else {
                Resource.Error("Failed to update journal entry: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error updating journal entry")
        }
    }

    override suspend fun deleteEntry(id: String): Resource<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteJournalEntry(id)
            if (response.isSuccessful) {
                Resource.Success(true)
            } else {
                Resource.Error("Failed to delete journal entry: ${response.message()}")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Unknown error deleting journal entry")
        }
    }

    private fun JournalEntryDto.toDomain(): JournalEntry {
        return JournalEntry(
            id = id,
            userId = userId,
            symbol = symbol,
            strikePrice = strikePrice,
            optionType = optionType,
            direction = direction,
            entryPrice = entryPrice,
            exitPrice = exitPrice,
            quantity = quantity,
            lotSize = lotSize,
            entryDate = entryDate,
            exitDate = exitDate,
            expiryDate = expiryDate,
            realizedPnl = realizedPnl,
            realizedPnlPercent = realizedPnlPercent,
            title = title,
            notes = notes,
            tags = tags,
            marketCondition = marketCondition,
            mood = mood,
            outcome = outcome,
            paperPositionId = paperPositionId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
