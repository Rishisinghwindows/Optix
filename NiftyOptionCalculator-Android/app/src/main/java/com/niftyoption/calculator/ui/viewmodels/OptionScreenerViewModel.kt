package com.niftyoption.calculator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.niftyoption.calculator.data.models.MoneynessFilter
import com.niftyoption.calculator.data.models.OptionChainRow
import com.niftyoption.calculator.data.models.OptionData
import com.niftyoption.calculator.data.models.ScreenerFilter
import com.niftyoption.calculator.data.models.ScreenerOptionType
import com.niftyoption.calculator.data.models.ScreenerResult
import com.niftyoption.calculator.data.models.ScreenerSortBy
import com.niftyoption.calculator.domain.BlackScholesEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OptionScreenerViewModel : ViewModel() {
    private val _filter = MutableStateFlow(ScreenerFilter())
    val filter: StateFlow<ScreenerFilter> = _filter.asStateFlow()

    private val _results = MutableStateFlow<List<ScreenerResult>>(emptyList())
    val results: StateFlow<List<ScreenerResult>> = _results.asStateFlow()

    private val _resultCount = MutableStateFlow(0)
    val resultCount: StateFlow<Int> = _resultCount.asStateFlow()

    private var optionChain: List<OptionChainRow> = emptyList()
    private var spotPrice: Double = 0.0

    fun setOptionChainData(chain: List<OptionChainRow>, spot: Double) {
        optionChain = chain
        spotPrice = spot
        applyFilters()
    }

    fun updateFilter(newFilter: ScreenerFilter) {
        _filter.value = newFilter
        applyFilters()
    }

    fun resetFilters() {
        _filter.value = ScreenerFilter()
        applyFilters()
    }

    private fun applyFilters() {
        val filter = _filter.value
        val results = mutableListOf<ScreenerResult>()

        for (row in optionChain) {
            // Process call option
            if (filter.optionType != ScreenerOptionType.PUT) {
                row.callOption?.let { option ->
                    if (passesFilters(option, filter)) {
                        results.add(toScreenerResult(option))
                    }
                }
            }
            // Process put option
            if (filter.optionType != ScreenerOptionType.CALL) {
                row.putOption?.let { option ->
                    if (passesFilters(option, filter)) {
                        results.add(toScreenerResult(option))
                    }
                }
            }
        }

        // Sort
        val sorted = when (filter.sortBy) {
            ScreenerSortBy.DELTA -> results.sortedBy { kotlin.math.abs(it.delta) }
            ScreenerSortBy.IV -> results.sortedBy { it.iv }
            ScreenerSortBy.OI -> results.sortedBy { it.oi.toLong() }
            ScreenerSortBy.VOLUME -> results.sortedBy { it.volume.toLong() }
            ScreenerSortBy.PREMIUM -> results.sortedBy { it.ltp }
        }

        _results.value = if (filter.sortAscending) sorted else sorted.reversed()
        _resultCount.value = _results.value.size
    }

    private fun passesFilters(option: OptionData, filter: ScreenerFilter): Boolean {
        // Moneyness filter
        if (filter.moneyness != MoneynessFilter.ALL) {
            val moneyness = option.moneyness
            if (filter.moneyness.displayName != moneyness) return false
        }

        // Delta filter (use absolute value)
        val T = option.timeToExpiryYears
        val absDelta = if (T > 0 && option.impliedVolatility > 0) {
            val greeks = BlackScholesEngine.calculateGreeks(
                option.underlyingValue, option.strikePrice, T, 0.065, option.impliedVolatility, option.optionType
            )
            kotlin.math.abs(greeks.delta)
        } else {
            0.0
        }
        if (absDelta < filter.deltaMin || absDelta > filter.deltaMax) return false

        // IV filter (compare in percentage)
        val ivPct = option.impliedVolatility * 100
        if (ivPct < filter.ivMin || ivPct > filter.ivMax) return false

        // OI filter
        if (option.openInterest < filter.oiMin) return false

        // Volume filter
        if (option.volume < filter.volumeMin) return false

        // Premium filter
        if (option.lastTradedPrice < filter.premiumMin || option.lastTradedPrice > filter.premiumMax) return false

        return true
    }

    private fun toScreenerResult(option: OptionData): ScreenerResult {
        val T = option.timeToExpiryYears
        val greeks = if (T > 0 && option.impliedVolatility > 0) {
            BlackScholesEngine.calculateGreeks(
                option.underlyingValue, option.strikePrice, T, 0.065, option.impliedVolatility, option.optionType
            )
        } else null

        return ScreenerResult(
            strikePrice = option.strikePrice,
            optionType = option.optionType,
            ltp = option.lastTradedPrice,
            iv = option.impliedVolatility,
            delta = greeks?.delta ?: 0.0,
            gamma = greeks?.gamma ?: 0.0,
            theta = greeks?.theta ?: 0.0,
            vega = greeks?.vega ?: 0.0,
            oi = option.openInterest,
            oiChange = option.changeInOI,
            volume = option.volume,
            underlyingValue = option.underlyingValue
        )
    }
}
