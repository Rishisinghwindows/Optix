package com.optix.app.presentation.screens.trade

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.core.util.Resource
import com.optix.app.domain.model.AuthState
import com.optix.app.domain.model.PaperPosition
import com.optix.app.domain.model.TradingIndex
import com.optix.app.domain.repository.AuthRepository
import com.optix.app.domain.repository.PaperTradingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaperTradingState(
    // Authentication
    val isLoggedIn: Boolean = false,

    // Portfolio Overview
    val portfolioValue: Double = 1000000.0, // 10 Lakh starting balance
    val totalPnL: Double = 0.0,
    val totalPnLPercent: Double = 0.0,
    val todayPnL: Double = 0.0,
    val availableMargin: Double = 1000000.0,
    val openPositionsCount: Int = 0,

    // Positions & History
    val positionsState: Resource<List<PaperPosition>> = Resource.Loading(),
    val historyState: Resource<List<PaperPosition>> = Resource.Loading(),

    // Performance Metrics
    val winRate: Double = 0.0,
    val totalTrades: Int = 0,
    val winningTrades: Int = 0,
    val losingTrades: Int = 0,
    val avgProfit: Double = 0.0,
    val avgLoss: Double = 0.0,
    val totalProfit: Double = 0.0,
    val totalLoss: Double = 0.0,
    val profitFactor: Double = 0.0,
    val maxDrawdown: Double = 0.0
)

@HiltViewModel
class PaperTradingViewModel @Inject constructor(
    private val repository: PaperTradingRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        private const val STARTING_BALANCE = 1000000.0 // 10 Lakh
    }

    private val _state = MutableStateFlow(PaperTradingState())
    val state: StateFlow<PaperTradingState> = _state.asStateFlow()

    init {
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                val isLoggedIn = authState is AuthState.Authenticated
                _state.update { it.copy(isLoggedIn = isLoggedIn) }

                if (isLoggedIn) {
                    loadData()
                } else {
                    // Reset to initial state when logged out
                    _state.update {
                        PaperTradingState(isLoggedIn = false)
                    }
                }
            }
        }
    }

    fun refresh() {
        if (_state.value.isLoggedIn) {
            loadData()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    positionsState = Resource.Loading(),
                    historyState = Resource.Loading()
                )
            }

            // Load positions
            launch {
                when (val result = repository.getOpenPositions()) {
                    is Resource.Success -> {
                        val positions = result.data ?: emptyList()
                        val totalUnrealizedPnL = positions.sumOf { pos ->
                            val lotSize = TradingIndex.fromSymbol(pos.symbol)?.lotSize ?: 75
                            (pos.currentPrice - pos.entryPrice) * pos.quantity * lotSize *
                                    if (pos.isBuy) 1 else -1
                        }

                        // Calculate used margin (simplified: entry value of all positions)
                        val usedMargin = positions.sumOf { pos ->
                            val lotSize = TradingIndex.fromSymbol(pos.symbol)?.lotSize ?: 75
                            pos.entryPrice * pos.quantity * lotSize
                        }

                        val portfolioValue = STARTING_BALANCE + totalUnrealizedPnL
                        val totalPnLPercent = if (STARTING_BALANCE > 0) {
                            (totalUnrealizedPnL / STARTING_BALANCE) * 100
                        } else 0.0

                        _state.update {
                            it.copy(
                                positionsState = Resource.Success(positions),
                                totalPnL = totalUnrealizedPnL,
                                totalPnLPercent = totalPnLPercent,
                                portfolioValue = portfolioValue,
                                availableMargin = STARTING_BALANCE - usedMargin,
                                openPositionsCount = positions.size
                            )
                        }
                    }
                    is Resource.Error -> {
                        _state.update {
                            it.copy(positionsState = Resource.Error(result.message ?: "Unknown error"))
                        }
                    }
                    is Resource.Loading -> {}
                }
            }

            // Load history
            launch {
                when (val result = repository.getClosedPositions()) {
                    is Resource.Success -> {
                        val history = result.data ?: emptyList()
                        calculatePerformanceMetrics(history)
                        _state.update { it.copy(historyState = Resource.Success(history)) }
                    }
                    is Resource.Error -> {
                        _state.update {
                            it.copy(historyState = Resource.Error(result.message ?: "Unknown error"))
                        }
                    }
                    is Resource.Loading -> {}
                }
            }

            // Load performance metrics from API
            launch {
                when (val result = repository.getPerformanceMetrics()) {
                    is Resource.Success -> {
                        result.data?.let { metrics ->
                            _state.update {
                                it.copy(
                                    winRate = metrics.winRate,
                                    totalTrades = metrics.totalTrades,
                                    winningTrades = metrics.winningTrades,
                                    losingTrades = metrics.losingTrades,
                                    avgProfit = metrics.averageProfit,
                                    avgLoss = metrics.averageLoss,
                                    todayPnL = metrics.todayPnL,
                                    totalProfit = metrics.totalProfit,
                                    totalLoss = metrics.totalLoss,
                                    profitFactor = if (metrics.totalLoss > 0)
                                        metrics.totalProfit / metrics.totalLoss else 0.0,
                                    maxDrawdown = metrics.maxLoss
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun calculatePerformanceMetrics(history: List<PaperPosition>) {
        if (history.isEmpty()) return

        val profits = history.map { trade ->
            val exitPrice = trade.exitPrice ?: trade.currentPrice
            val lotSize = TradingIndex.fromSymbol(trade.symbol)?.lotSize ?: 75
            (exitPrice - trade.entryPrice) * trade.quantity * lotSize * if (trade.isBuy) 1 else -1
        }

        val winning = profits.count { it > 0 }
        val losing = profits.count { it < 0 }
        val winRate = if (profits.isNotEmpty()) (winning.toDouble() / profits.size) * 100 else 0.0
        val avgProfit = profits.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val avgLoss = profits.filter { it < 0 }.takeIf { it.isNotEmpty() }?.average()?.let { -it } ?: 0.0
        val totalProfit = profits.filter { it > 0 }.sum()
        val totalLoss = profits.filter { it < 0 }.sum().let { -it }
        val profitFactor = if (totalLoss > 0) totalProfit / totalLoss else 0.0

        _state.update {
            it.copy(
                totalTrades = history.size,
                winningTrades = winning,
                losingTrades = losing,
                winRate = winRate,
                avgProfit = avgProfit,
                avgLoss = avgLoss,
                totalProfit = totalProfit,
                totalLoss = totalLoss,
                profitFactor = profitFactor
            )
        }
    }

    fun closePosition(positionId: String) {
        viewModelScope.launch {
            try {
                when (val result = repository.closePosition(positionId)) {
                    is Resource.Success -> loadData()
                    is Resource.Error -> {
                        _state.update {
                            it.copy(positionsState = Resource.Error(result.message ?: "Failed to close position"))
                        }
                    }
                    is Resource.Loading -> { /* waiting */ }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(positionsState = Resource.Error(e.message ?: "Failed to close position"))
                }
            }
        }
    }
}
