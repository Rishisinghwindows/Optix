package com.optix.app.presentation.screens.notifications

import androidx.lifecycle.ViewModel
import com.optix.app.data.local.MarketSignal
import com.optix.app.data.local.MarketSignalStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class SignalFilter {
    ALL, UNREAD, BULLISH, BEARISH, VOLATILITY
}

data class NotificationsState(
    val signals: List<MarketSignal> = emptyList(),
    val selectedFilter: SignalFilter = SignalFilter.ALL,
    val unreadCount: Int = 0,
    val bullishCount: Int = 0,
    val bearishCount: Int = 0,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val signalStore: MarketSignalStore
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val signals = signalStore.loadSignals()
        _state.value = _state.value.copy(
            signals = signals,
            unreadCount = signals.count { !it.isRead },
            bullishCount = signals.count { it.isBullish },
            bearishCount = signals.count { it.isBearish },
        )
    }

    fun setFilter(filter: SignalFilter) {
        _state.value = _state.value.copy(selectedFilter = filter)
    }

    fun markAsRead(signalId: String) {
        signalStore.markAsRead(signalId)
        refresh()
    }

    fun markAllAsRead() {
        signalStore.markAllAsRead()
        refresh()
    }

    fun deleteSignal(signalId: String) {
        signalStore.deleteSignal(signalId)
        refresh()
    }

    fun clearAll() {
        signalStore.clearAll()
        refresh()
    }

    val filteredSignals: List<MarketSignal>
        get() {
            val signals = _state.value.signals
            return when (_state.value.selectedFilter) {
                SignalFilter.ALL -> signals
                SignalFilter.UNREAD -> signals.filter { !it.isRead }
                SignalFilter.BULLISH -> signals.filter { it.isBullish }
                SignalFilter.BEARISH -> signals.filter { it.isBearish }
                SignalFilter.VOLATILITY -> signals.filter { it.isVolatility }
            }
        }
}
