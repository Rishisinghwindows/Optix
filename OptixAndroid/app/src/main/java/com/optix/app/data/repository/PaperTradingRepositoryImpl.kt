package com.optix.app.data.repository

import com.optix.app.core.util.Resource
import com.optix.app.data.remote.api.OptixApiService
import com.optix.app.data.remote.dto.ClosePositionRequest
import com.optix.app.data.remote.dto.TradeRequest
import com.optix.app.domain.model.PaperPosition
import com.optix.app.domain.model.PaperTrade
import com.optix.app.domain.model.PerformanceMetrics
import com.optix.app.domain.repository.PaperTradingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaperTradingRepositoryImpl @Inject constructor(
    private val api: OptixApiService
) : PaperTradingRepository {

    override suspend fun getOpenPositions(): Resource<List<PaperPosition>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getPositions()
                if (response.isSuccessful && response.body() != null) {
                    val positions = response.body()!!.positions.map { dto ->
                        PaperPosition(
                            id = dto.id,
                            symbol = dto.symbol,
                            strikePrice = dto.strikePrice,
                            optionType = dto.optionType,
                            expiry = dto.expiry,
                            quantity = dto.quantity,
                            entryPrice = dto.avgEntryPrice,
                            currentPrice = dto.currentPrice,
                            isBuy = dto.direction.lowercase() == "buy",
                            openedAt = parseTimestamp(dto.createdAt)
                        )
                    }
                    Resource.Success(positions)
                } else {
                    Resource.Error("Failed to fetch positions")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override suspend fun getClosedPositions(): Resource<List<PaperPosition>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getTradeHistory()
                if (response.isSuccessful && response.body() != null) {
                    val positions = response.body()!!.trades
                        .filter { it.status == "closed" }
                        .map { dto ->
                            PaperPosition(
                                id = dto.id,
                                symbol = dto.symbol,
                                strikePrice = dto.strikePrice,
                                optionType = dto.optionType,
                                expiry = dto.expiry,
                                quantity = dto.quantity,
                                entryPrice = dto.entryPrice,
                                currentPrice = dto.exitPrice ?: dto.entryPrice,
                                exitPrice = dto.exitPrice,
                                isBuy = dto.direction.lowercase() == "buy",
                                openedAt = parseTimestamp(dto.createdAt),
                                closedAt = dto.closedAt?.let { parseTimestamp(it) }
                            )
                        }
                    Resource.Success(positions)
                } else {
                    Resource.Error("Failed to fetch history")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override suspend fun openPosition(trade: PaperTrade): Resource<PaperPosition> =
        withContext(Dispatchers.IO) {
            try {
                val request = TradeRequest(
                    symbol = trade.symbol,
                    strikePrice = trade.strikePrice,
                    optionType = trade.optionType,
                    expiry = trade.expiry,
                    direction = if (trade.isBuy) "buy" else "sell",
                    quantity = trade.quantity,
                    price = trade.price
                )
                val response = api.executeTrade(request)
                if (response.isSuccessful && response.body() != null) {
                    val dto = response.body()!!.trade
                    if (dto != null) {
                        Resource.Success(
                            PaperPosition(
                                id = dto.id,
                                symbol = dto.symbol,
                                strikePrice = dto.strikePrice,
                                optionType = dto.optionType,
                                expiry = dto.expiry,
                                quantity = dto.quantity,
                                entryPrice = dto.entryPrice,
                                currentPrice = dto.entryPrice,
                                isBuy = dto.direction.lowercase() == "buy",
                                openedAt = parseTimestamp(dto.createdAt)
                            )
                        )
                    } else {
                        Resource.Error("Trade response was empty")
                    }
                } else {
                    Resource.Error("Failed to open position")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override suspend fun closePosition(positionId: String): Resource<PaperPosition> =
        withContext(Dispatchers.IO) {
            try {
                // Get current price first (in real app, you'd fetch from market data)
                val closeRequest = ClosePositionRequest(exitPrice = 0.0) // API should calculate
                val response = api.closePosition(positionId, closeRequest)
                if (response.isSuccessful && response.body() != null) {
                    val dto = response.body()!!.trade
                    if (dto != null) {
                        Resource.Success(
                            PaperPosition(
                                id = dto.id,
                                symbol = dto.symbol,
                                strikePrice = dto.strikePrice,
                                optionType = dto.optionType,
                                expiry = dto.expiry,
                                quantity = dto.quantity,
                                entryPrice = dto.entryPrice,
                                currentPrice = dto.exitPrice ?: dto.entryPrice,
                                exitPrice = dto.exitPrice,
                                isBuy = dto.direction.lowercase() == "buy",
                                openedAt = parseTimestamp(dto.createdAt),
                                closedAt = dto.closedAt?.let { parseTimestamp(it) }
                            )
                        )
                    } else {
                        Resource.Error("Close response was empty")
                    }
                } else {
                    Resource.Error("Failed to close position")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    override suspend fun getPerformanceMetrics(): Resource<PerformanceMetrics> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getPerformance()
                if (response.isSuccessful && response.body() != null) {
                    val dto = response.body()!!
                    Resource.Success(
                        PerformanceMetrics(
                            totalTrades = dto.totalTrades,
                            winningTrades = dto.winningTrades,
                            losingTrades = dto.losingTrades,
                            winRate = dto.winRate,
                            totalProfit = dto.totalProfit,
                            totalLoss = dto.totalLoss,
                            netPnL = dto.totalPnl,
                            averageProfit = dto.avgProfit,
                            averageLoss = dto.avgLoss,
                            maxProfit = dto.totalProfit,  // API doesn't have this
                            maxLoss = dto.totalLoss,       // API doesn't have this
                            todayPnL = 0.0                 // API doesn't have this
                        )
                    )
                } else {
                    Resource.Error("Failed to fetch metrics")
                }
            } catch (e: Exception) {
                Resource.Error(e.message ?: "Unknown error occurred")
            }
        }

    private fun parseTimestamp(dateString: String): Long {
        return try {
            Instant.parse(dateString).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
