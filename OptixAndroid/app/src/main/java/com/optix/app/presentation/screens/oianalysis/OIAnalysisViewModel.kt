package com.optix.app.presentation.screens.oianalysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.core.util.Resource
import com.optix.app.domain.model.OptionChain
import com.optix.app.domain.model.OptionChainRow
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

data class OIZone(
    val strikePrice: Double,
    val callOI: Long,
    val putOI: Long,
    val zoneType: ZoneType,
    val strength: Float // 0-1
)

enum class ZoneType {
    STRONG_SUPPORT,
    SUPPORT,
    NEUTRAL,
    RESISTANCE,
    STRONG_RESISTANCE
}

data class SmartMoneyActivity(
    val strikePrice: Double,
    val activityType: ActivityType,
    val change: Long,
    val description: String
)

enum class ActivityType {
    HEAVY_CALL_WRITING,
    HEAVY_PUT_WRITING,
    CALL_UNWINDING,
    PUT_UNWINDING,
    FRESH_LONG_BUILD,
    SHORT_COVERING
}

data class HeatmapCell(
    val strikePrice: Double,
    val callOI: Long,
    val putOI: Long,
    val callOIChange: Long,
    val putOIChange: Long,
    val callIV: Double,
    val putIV: Double,
    val intensity: Float // 0-1 for heatmap coloring
)

data class OIAnalysisState(
    val selectedIndex: TradingIndex = TradingIndex.NIFTY50,
    val spotPrice: Double = 0.0,
    val atmStrike: Double = 0.0,
    val pcr: Double = 0.0,
    val maxPain: Double = 0.0,
    val topCallOIStrikes: List<Pair<Double, Long>> = emptyList(),
    val topPutOIStrikes: List<Pair<Double, Long>> = emptyList(),
    val topCallOIChangeStrikes: List<Pair<Double, Long>> = emptyList(),
    val topPutOIChangeStrikes: List<Pair<Double, Long>> = emptyList(),
    val supportZones: List<OIZone> = emptyList(),
    val resistanceZones: List<OIZone> = emptyList(),
    val smartMoneyActivities: List<SmartMoneyActivity> = emptyList(),
    val heatmapData: List<HeatmapCell> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OIAnalysisViewModel @Inject constructor(
    private val repository: OptionChainRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OIAnalysisState())
    val state: StateFlow<OIAnalysisState> = _state.asStateFlow()

    private var currentExpiry: String? = null

    init {
        loadData()
    }

    fun refresh() {
        loadData()
    }

    fun setIndex(index: TradingIndex) {
        _state.update { it.copy(selectedIndex = index) }
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // First get expiries
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

            val expiry = currentExpiry ?: return@launch

            // Load option chain
            when (val result = repository.getOptionChain(_state.value.selectedIndex, expiry)) {
                is Resource.Success -> {
                    result.data?.let { chain ->
                        analyzeOI(chain)
                    }
                }
                is Resource.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                else -> {}
            }
        }
    }

    private fun analyzeOI(chain: OptionChain) {
        val rows = chain.rows
        val spot = chain.spotPrice
        val atm = chain.atmStrike

        // Calculate PCR
        val totalCallOI = rows.sumOf { it.callData?.openInterest ?: 0L }
        val totalPutOI = rows.sumOf { it.putData?.openInterest ?: 0L }
        val pcr = if (totalCallOI > 0) totalPutOI.toDouble() / totalCallOI else 0.0

        // Find top OI strikes
        val topCallOI = rows
            .filter { it.callData != null }
            .sortedByDescending { it.callData?.openInterest ?: 0L }
            .take(5)
            .map { it.strikePrice to (it.callData?.openInterest ?: 0L) }

        val topPutOI = rows
            .filter { it.putData != null }
            .sortedByDescending { it.putData?.openInterest ?: 0L }
            .take(5)
            .map { it.strikePrice to (it.putData?.openInterest ?: 0L) }

        // Find top OI change strikes
        val topCallOIChange = rows
            .filter { it.callData != null }
            .sortedByDescending { abs(it.callData?.oiChange ?: 0L) }
            .take(5)
            .map { it.strikePrice to (it.callData?.oiChange ?: 0L) }

        val topPutOIChange = rows
            .filter { it.putData != null }
            .sortedByDescending { abs(it.putData?.oiChange ?: 0L) }
            .take(5)
            .map { it.strikePrice to (it.putData?.oiChange ?: 0L) }

        // Calculate Max Pain
        val maxPain = calculateMaxPain(rows, spot)

        // Identify support/resistance zones from OI
        val supportZones = identifySupportZones(rows, spot)
        val resistanceZones = identifyResistanceZones(rows, spot)

        // Detect smart money activity
        val smartMoneyActivities = detectSmartMoneyActivity(rows)

        // Generate heatmap data
        val heatmapData = generateHeatmapData(rows)

        _state.update {
            it.copy(
                spotPrice = spot,
                atmStrike = atm,
                pcr = pcr,
                maxPain = maxPain,
                topCallOIStrikes = topCallOI,
                topPutOIStrikes = topPutOI,
                topCallOIChangeStrikes = topCallOIChange,
                topPutOIChangeStrikes = topPutOIChange,
                supportZones = supportZones,
                resistanceZones = resistanceZones,
                smartMoneyActivities = smartMoneyActivities,
                heatmapData = heatmapData,
                isLoading = false
            )
        }
    }

    private fun calculateMaxPain(rows: List<OptionChainRow>, spot: Double): Double {
        var minPain = Double.MAX_VALUE
        var maxPainStrike = spot

        for (row in rows) {
            val strike = row.strikePrice
            var totalPain = 0.0

            // Calculate pain for all call buyers
            rows.forEach { r ->
                val callOI = r.callData?.openInterest ?: 0L
                if (strike > r.strikePrice) {
                    totalPain += (strike - r.strikePrice) * callOI
                }
            }

            // Calculate pain for all put buyers
            rows.forEach { r ->
                val putOI = r.putData?.openInterest ?: 0L
                if (strike < r.strikePrice) {
                    totalPain += (r.strikePrice - strike) * putOI
                }
            }

            if (totalPain < minPain) {
                minPain = totalPain
                maxPainStrike = strike
            }
        }

        return maxPainStrike
    }

    private fun identifySupportZones(rows: List<OptionChainRow>, spot: Double): List<OIZone> {
        val maxPutOI = rows.maxOfOrNull { it.putData?.openInterest ?: 0L } ?: 1L

        return rows
            .filter { it.strikePrice < spot && (it.putData?.openInterest ?: 0L) > maxPutOI * 0.3 }
            .sortedByDescending { it.putData?.openInterest ?: 0L }
            .take(3)
            .map { row ->
                val strength = ((row.putData?.openInterest ?: 0L).toFloat() / maxPutOI).coerceIn(0f, 1f)
                OIZone(
                    strikePrice = row.strikePrice,
                    callOI = row.callData?.openInterest ?: 0L,
                    putOI = row.putData?.openInterest ?: 0L,
                    zoneType = if (strength > 0.7) ZoneType.STRONG_SUPPORT else ZoneType.SUPPORT,
                    strength = strength
                )
            }
    }

    private fun identifyResistanceZones(rows: List<OptionChainRow>, spot: Double): List<OIZone> {
        val maxCallOI = rows.maxOfOrNull { it.callData?.openInterest ?: 0L } ?: 1L

        return rows
            .filter { it.strikePrice > spot && (it.callData?.openInterest ?: 0L) > maxCallOI * 0.3 }
            .sortedByDescending { it.callData?.openInterest ?: 0L }
            .take(3)
            .map { row ->
                val strength = ((row.callData?.openInterest ?: 0L).toFloat() / maxCallOI).coerceIn(0f, 1f)
                OIZone(
                    strikePrice = row.strikePrice,
                    callOI = row.callData?.openInterest ?: 0L,
                    putOI = row.putData?.openInterest ?: 0L,
                    zoneType = if (strength > 0.7) ZoneType.STRONG_RESISTANCE else ZoneType.RESISTANCE,
                    strength = strength
                )
            }
    }

    private fun detectSmartMoneyActivity(rows: List<OptionChainRow>): List<SmartMoneyActivity> {
        val activities = mutableListOf<SmartMoneyActivity>()
        val avgOIChange = rows.mapNotNull { 
            val callChange = it.callData?.oiChange ?: 0L
            val putChange = it.putData?.oiChange ?: 0L
            abs(callChange) + abs(putChange)
        }.average()

        rows.forEach { row ->
            val callOIChange = row.callData?.oiChange ?: 0L
            val putOIChange = row.putData?.oiChange ?: 0L
            val callPriceChange = row.callData?.change ?: 0.0
            val putPriceChange = row.putData?.change ?: 0.0

            // Heavy Call Writing: OI increasing + Price decreasing
            if (callOIChange > avgOIChange * 2 && callPriceChange < 0) {
                activities.add(SmartMoneyActivity(
                    strikePrice = row.strikePrice,
                    activityType = ActivityType.HEAVY_CALL_WRITING,
                    change = callOIChange,
                    description = "Heavy call writing detected - bearish signal"
                ))
            }

            // Heavy Put Writing: OI increasing + Price decreasing
            if (putOIChange > avgOIChange * 2 && putPriceChange < 0) {
                activities.add(SmartMoneyActivity(
                    strikePrice = row.strikePrice,
                    activityType = ActivityType.HEAVY_PUT_WRITING,
                    change = putOIChange,
                    description = "Heavy put writing detected - bullish signal"
                ))
            }

            // Call Unwinding: OI decreasing + Price decreasing
            if (callOIChange < -avgOIChange && callPriceChange < 0) {
                activities.add(SmartMoneyActivity(
                    strikePrice = row.strikePrice,
                    activityType = ActivityType.CALL_UNWINDING,
                    change = callOIChange,
                    description = "Call unwinding - bulls exiting"
                ))
            }

            // Put Unwinding: OI decreasing + Price decreasing
            if (putOIChange < -avgOIChange && putPriceChange < 0) {
                activities.add(SmartMoneyActivity(
                    strikePrice = row.strikePrice,
                    activityType = ActivityType.PUT_UNWINDING,
                    change = putOIChange,
                    description = "Put unwinding - bears exiting"
                ))
            }

            // Fresh Long Build: OI increasing + Price increasing (Call)
            if (callOIChange > avgOIChange && callPriceChange > 0) {
                activities.add(SmartMoneyActivity(
                    strikePrice = row.strikePrice,
                    activityType = ActivityType.FRESH_LONG_BUILD,
                    change = callOIChange,
                    description = "Fresh long buildup in calls"
                ))
            }

            // Short Covering: OI decreasing + Price increasing
            if (callOIChange < -avgOIChange && callPriceChange > 0) {
                activities.add(SmartMoneyActivity(
                    strikePrice = row.strikePrice,
                    activityType = ActivityType.SHORT_COVERING,
                    change = callOIChange,
                    description = "Short covering detected"
                ))
            }
        }

        return activities.sortedByDescending { abs(it.change) }.take(10)
    }

    private fun generateHeatmapData(rows: List<OptionChainRow>): List<HeatmapCell> {
        val maxOI = rows.maxOfOrNull { 
            maxOf(it.callData?.openInterest ?: 0L, it.putData?.openInterest ?: 0L)
        } ?: 1L

        return rows.map { row ->
            val totalOI = (row.callData?.openInterest ?: 0L) + (row.putData?.openInterest ?: 0L)
            HeatmapCell(
                strikePrice = row.strikePrice,
                callOI = row.callData?.openInterest ?: 0L,
                putOI = row.putData?.openInterest ?: 0L,
                callOIChange = row.callData?.oiChange ?: 0L,
                putOIChange = row.putData?.oiChange ?: 0L,
                callIV = row.callData?.impliedVolatility ?: 0.0,
                putIV = row.putData?.impliedVolatility ?: 0.0,
                intensity = (totalOI.toFloat() / (maxOI * 2)).coerceIn(0f, 1f)
            )
        }
    }
}
