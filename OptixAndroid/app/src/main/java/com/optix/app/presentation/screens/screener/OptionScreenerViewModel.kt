package com.optix.app.presentation.screens.screener

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.core.util.Resource
import com.optix.app.domain.model.OptionChain
import com.optix.app.domain.model.OptionChainRow
import com.optix.app.domain.model.OptionData
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.TradingIndex
import com.optix.app.domain.repository.OptionChainRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

// -- Supporting types --

enum class ScreenerOptionType(val label: String) {
    BOTH("Both"),
    CE("CE"),
    PE("PE")
}

enum class MoneynessFilter(val label: String) {
    ALL("All"),
    ATM("ATM"),
    ITM("ITM"),
    OTM("OTM")
}

enum class ScreenerSortBy(val label: String) {
    OI("OI"),
    VOLUME("Volume"),
    IV("IV"),
    DELTA("Delta"),
    PREMIUM("Premium")
}

data class ScreenerFilter(
    val optionType: ScreenerOptionType = ScreenerOptionType.BOTH,
    val moneyness: MoneynessFilter = MoneynessFilter.ALL,
    val deltaMin: Double = 0.0,
    val deltaMax: Double = 1.0,
    val ivMin: Double = 0.0,
    val ivMax: Double = 200.0,
    val oiMin: Long = 0,
    val volumeMin: Long = 0,
    val premiumMin: Double = 0.0,
    val premiumMax: Double = 100000.0,
    val sortBy: ScreenerSortBy = ScreenerSortBy.OI,
    val sortAscending: Boolean = false
)

data class ScreenerResult(
    val strikePrice: Double,
    val optionType: OptionType,
    val ltp: Double,
    val iv: Double,
    val delta: Double,
    val gamma: Double,
    val theta: Double,
    val vega: Double,
    val oi: Long,
    val oiChange: Long,
    val volume: Long
)

data class OptionScreenerState(
    val selectedIndex: TradingIndex = TradingIndex.NIFTY50,
    val filters: ScreenerFilter = ScreenerFilter(),
    val results: List<ScreenerResult> = emptyList(),
    val resultCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val spotPrice: Double = 0.0
)

@HiltViewModel
class OptionScreenerViewModel @Inject constructor(
    private val repository: OptionChainRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OptionScreenerState())
    val state: StateFlow<OptionScreenerState> = _state.asStateFlow()

    private var chainRows: List<OptionChainRow> = emptyList()
    private var spotPrice: Double = 0.0
    private var currentExpiry: String? = null

    init {
        loadData()
    }

    fun setIndex(index: TradingIndex) {
        _state.update { it.copy(selectedIndex = index) }
        loadData()
    }

    fun updateFilters(filters: ScreenerFilter) {
        _state.update { it.copy(filters = filters) }
        applyFilters()
    }

    fun resetFilters() {
        _state.update { it.copy(filters = ScreenerFilter()) }
        applyFilters()
    }

    fun refresh() {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            when (val expiryResult = repository.getExpiries(_state.value.selectedIndex)) {
                is Resource.Success -> {
                    currentExpiry = expiryResult.data?.firstOrNull()?.value
                }
                is Resource.Error -> {
                    _state.update { it.copy(isLoading = false, error = expiryResult.message) }
                    return@launch
                }
                else -> {}
            }

            val expiry = currentExpiry ?: run {
                _state.update { it.copy(isLoading = false, error = "No expiry available") }
                return@launch
            }

            when (val result = repository.getOptionChain(_state.value.selectedIndex, expiry)) {
                is Resource.Success -> {
                    result.data?.let { chain ->
                        chainRows = chain.rows
                        spotPrice = chain.spotPrice
                        _state.update { it.copy(spotPrice = chain.spotPrice) }
                        applyFilters()
                    }
                }
                is Resource.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                else -> {}
            }
        }
    }

    private fun applyFilters() {
        val filters = _state.value.filters
        val filtered = mutableListOf<ScreenerResult>()

        for (row in chainRows) {
            if (filters.optionType == ScreenerOptionType.BOTH || filters.optionType == ScreenerOptionType.CE) {
                row.callData?.let { call ->
                    evaluateOption(call, filters)?.let { filtered.add(it) }
                }
            }
            if (filters.optionType == ScreenerOptionType.BOTH || filters.optionType == ScreenerOptionType.PE) {
                row.putData?.let { put ->
                    evaluateOption(put, filters)?.let { filtered.add(it) }
                }
            }
        }

        val sorted = filtered.sortedWith(
            compareBy<ScreenerResult> { result ->
                val value = when (filters.sortBy) {
                    ScreenerSortBy.OI -> result.oi.toDouble()
                    ScreenerSortBy.VOLUME -> result.volume.toDouble()
                    ScreenerSortBy.IV -> result.iv
                    ScreenerSortBy.DELTA -> abs(result.delta)
                    ScreenerSortBy.PREMIUM -> result.ltp
                }
                if (filters.sortAscending) value else -value
            }
        )

        _state.update {
            it.copy(
                results = sorted,
                resultCount = sorted.size,
                isLoading = false
            )
        }
    }

    private fun evaluateOption(option: OptionData, filters: ScreenerFilter): ScreenerResult? {
        // Moneyness filter
        when (filters.moneyness) {
            MoneynessFilter.ALL -> { /* pass */ }
            MoneynessFilter.ATM -> if (!option.isATM) return null
            MoneynessFilter.ITM -> if (!option.isITM || option.isATM) return null
            MoneynessFilter.OTM -> if (option.isITM || option.isATM) return null
        }

        val iv = if (option.impliedVolatility > 0) option.impliedVolatility else 0.15
        val absDelta = abs(option.delta)

        // Delta filter
        if (absDelta < filters.deltaMin || absDelta > filters.deltaMax) return null

        // IV filter (compare as percentage)
        val ivPercent = iv * 100.0
        if (ivPercent < filters.ivMin || ivPercent > filters.ivMax) return null

        // OI filter
        if (option.openInterest < filters.oiMin) return null

        // Volume filter
        if (option.volume < filters.volumeMin) return null

        // Premium filter
        if (option.lastPrice < filters.premiumMin || option.lastPrice > filters.premiumMax) return null

        return ScreenerResult(
            strikePrice = option.strikePrice,
            optionType = option.optionType,
            ltp = option.lastPrice,
            iv = iv,
            delta = option.delta,
            gamma = option.gamma,
            theta = option.theta,
            vega = option.vega,
            oi = option.openInterest,
            oiChange = option.oiChange,
            volume = option.volume
        )
    }
}
