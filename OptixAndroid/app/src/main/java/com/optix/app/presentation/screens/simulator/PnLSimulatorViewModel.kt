package com.optix.app.presentation.screens.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.core.calculation.BlackScholesEngine
import com.optix.app.core.constants.AppConstants
import com.optix.app.domain.model.Greeks
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.PayoffPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ──────────────────────────────────────────────
// Data models
// ──────────────────────────────────────────────

data class SimulationLeg(
    val id: String = UUID.randomUUID().toString(),
    val spotPrice: Double,
    val strikePrice: Double,
    val optionType: OptionType,
    val position: LegDirection,
    val entryPremium: Double,
    val iv: Double,              // decimal e.g. 0.15 for 15%
    val daysToExpiry: Int,
    val lotSize: Int,
    val quantity: Int = 1
)

enum class LegDirection { BUY, SELL }

data class LegSimResult(
    val id: String = UUID.randomUUID().toString(),
    val leg: SimulationLeg,
    val newPrice: Double,
    val pnl: Double,
    val pnlPercent: Double,
    val newDelta: Double,
    val newGamma: Double,
    val newTheta: Double,
    val newVega: Double
)

// ──────────────────────────────────────────────
// UI State
// ──────────────────────────────────────────────

data class PnLSimulatorState(
    // Slider values
    val spotChangePercent: Float = 0f,       // -10..10
    val daysElapsed: Float = 0f,             // 0..maxDTE
    val ivChangePercent: Float = 0f,         // -30..30

    // Legs
    val legs: List<SimulationLeg> = emptyList(),

    // Results
    val totalPnl: Double = 0.0,
    val totalPnlPercent: Double = 0.0,
    val legResults: List<LegSimResult> = emptyList(),
    val payoffPoints: List<PayoffPoint> = emptyList(),
    val breakevens: List<Double> = emptyList(),
    val maxProfit: Double = 0.0,
    val maxLoss: Double = 0.0,

    // Add-leg form
    val showAddLegSheet: Boolean = false,
    val formStrike: String = "",
    val formPremium: String = "",
    val formIV: String = "15",
    val formDTE: String = "7",
    val formLotSize: String = "75",
    val formQuantity: String = "1",
    val formOptionType: OptionType = OptionType.CALL,
    val formDirection: LegDirection = LegDirection.BUY,
    val formSpotPrice: String = "22000"
) {
    val maxDTE: Int get() = legs.maxOfOrNull { it.daysToExpiry } ?: 30
}

// ──────────────────────────────────────────────
// ViewModel
// ──────────────────────────────────────────────

@OptIn(FlowPreview::class)
@HiltViewModel
class PnLSimulatorViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(PnLSimulatorState())
    val state: StateFlow<PnLSimulatorState> = _state.asStateFlow()

    init {
        // Auto-simulate whenever sliders change (debounced)
        viewModelScope.launch {
            snapshotFlow {
                Triple(
                    _state.value.spotChangePercent,
                    _state.value.daysElapsed,
                    _state.value.ivChangePercent
                )
            }
                .debounce(50)
                .collect { simulate() }
        }
    }

    // ── Slider updates ──

    fun setSpotChangePercent(value: Float) {
        _state.update { it.copy(spotChangePercent = value) }
    }

    fun setDaysElapsed(value: Float) {
        _state.update { it.copy(daysElapsed = value) }
    }

    fun setIVChangePercent(value: Float) {
        _state.update { it.copy(ivChangePercent = value) }
    }

    fun resetSliders() {
        _state.update { it.copy(spotChangePercent = 0f, daysElapsed = 0f, ivChangePercent = 0f) }
    }

    // ── Leg management ──

    fun showAddLeg() {
        _state.update { it.copy(showAddLegSheet = true) }
    }

    fun hideAddLeg() {
        _state.update { it.copy(showAddLegSheet = false) }
    }

    fun updateFormStrike(v: String) { _state.update { it.copy(formStrike = v) } }
    fun updateFormPremium(v: String) { _state.update { it.copy(formPremium = v) } }
    fun updateFormIV(v: String) { _state.update { it.copy(formIV = v) } }
    fun updateFormDTE(v: String) { _state.update { it.copy(formDTE = v) } }
    fun updateFormLotSize(v: String) { _state.update { it.copy(formLotSize = v) } }
    fun updateFormQuantity(v: String) { _state.update { it.copy(formQuantity = v) } }
    fun updateFormSpotPrice(v: String) { _state.update { it.copy(formSpotPrice = v) } }
    fun updateFormOptionType(t: OptionType) { _state.update { it.copy(formOptionType = t) } }
    fun updateFormDirection(d: LegDirection) { _state.update { it.copy(formDirection = d) } }

    fun confirmAddLeg() {
        val s = _state.value
        val strike = s.formStrike.toDoubleOrNull() ?: return
        val premium = s.formPremium.toDoubleOrNull() ?: return
        val iv = (s.formIV.toDoubleOrNull() ?: 15.0) / 100.0
        val dte = s.formDTE.toIntOrNull() ?: 7
        val lotSize = s.formLotSize.toIntOrNull() ?: 75
        val quantity = s.formQuantity.toIntOrNull() ?: 1
        val spot = s.formSpotPrice.toDoubleOrNull() ?: 22000.0

        val leg = SimulationLeg(
            spotPrice = spot,
            strikePrice = strike,
            optionType = s.formOptionType,
            position = s.formDirection,
            entryPremium = premium,
            iv = iv,
            daysToExpiry = dte,
            lotSize = lotSize,
            quantity = quantity
        )

        _state.update {
            it.copy(
                legs = it.legs + leg,
                showAddLegSheet = false,
                formStrike = "",
                formPremium = ""
            )
        }
        simulate()
    }

    fun removeLeg(legId: String) {
        _state.update { it.copy(legs = it.legs.filter { leg -> leg.id != legId }) }
        simulate()
    }

    // ── Quick presets ──

    fun setupSingleOption(
        spotPrice: Double,
        strikePrice: Double,
        optionType: OptionType,
        premium: Double,
        iv: Double,
        dte: Int,
        lotSize: Int,
        quantity: Int = 1
    ) {
        val leg = SimulationLeg(
            spotPrice = spotPrice,
            strikePrice = strikePrice,
            optionType = optionType,
            position = LegDirection.BUY,
            entryPremium = premium,
            iv = iv,
            daysToExpiry = dte,
            lotSize = lotSize,
            quantity = quantity
        )
        _state.update {
            it.copy(
                legs = listOf(leg),
                spotChangePercent = 0f,
                daysElapsed = 0f,
                ivChangePercent = 0f,
                formSpotPrice = spotPrice.toString()
            )
        }
        simulate()
    }

    // ── Core simulation ──

    private fun simulate() {
        val s = _state.value
        if (s.legs.isEmpty()) {
            _state.update {
                it.copy(
                    totalPnl = 0.0, totalPnlPercent = 0.0,
                    legResults = emptyList(), payoffPoints = emptyList(),
                    breakevens = emptyList(), maxProfit = 0.0, maxLoss = 0.0
                )
            }
            return
        }

        val results = mutableListOf<LegSimResult>()
        var totalPnlAccum = 0.0
        var totalInvestment = 0.0

        for (leg in s.legs) {
            val dirMultiplier = if (leg.position == LegDirection.BUY) 1.0 else -1.0
            val newSpot = leg.spotPrice * (1.0 + s.spotChangePercent / 100.0)
            val newIV = (leg.iv * (1.0 + s.ivChangePercent / 100.0)).coerceIn(0.01, 5.0)
            val remainingDays = (leg.daysToExpiry - s.daysElapsed.toInt()).coerceAtLeast(0)
            val T = remainingDays / 365.0

            val newPrice: Double
            val greeks: Greeks

            if (T > 0) {
                val isCall = leg.optionType == OptionType.CALL
                val result = BlackScholesEngine.calculateGreeks(
                    spotPrice = newSpot,
                    strikePrice = leg.strikePrice,
                    timeToExpiry = T,
                    riskFreeRate = AppConstants.DEFAULT_RISK_FREE_RATE,
                    volatility = newIV,
                    isCall = isCall
                )
                newPrice = result.optionPrice
                greeks = result.greeks
            } else {
                // Expired — intrinsic value only
                newPrice = when (leg.optionType) {
                    OptionType.CALL -> maxOf(0.0, newSpot - leg.strikePrice)
                    OptionType.PUT -> maxOf(0.0, leg.strikePrice - newSpot)
                }
                greeks = Greeks.EMPTY
            }

            val positionSize = leg.quantity.toDouble() * leg.lotSize.toDouble()
            val legPnl = (newPrice - leg.entryPremium) * positionSize * dirMultiplier
            val legPnlPct = if (leg.entryPremium > 0) {
                ((newPrice - leg.entryPremium) / leg.entryPremium) * 100.0 * dirMultiplier
            } else 0.0

            totalPnlAccum += legPnl
            totalInvestment += leg.entryPremium * positionSize

            results.add(
                LegSimResult(
                    leg = leg,
                    newPrice = newPrice,
                    pnl = legPnl,
                    pnlPercent = legPnlPct,
                    newDelta = greeks.delta * dirMultiplier,
                    newGamma = greeks.gamma,
                    newTheta = greeks.theta,
                    newVega = greeks.vega
                )
            )
        }

        val totalPnlPct = if (totalInvestment > 0) (totalPnlAccum / totalInvestment) * 100.0 else 0.0

        // Generate payoff chart + statistics
        val (points, breakevens, maxProfit, maxLoss) = generatePayoffChart(s)

        _state.update {
            it.copy(
                totalPnl = totalPnlAccum,
                totalPnlPercent = totalPnlPct,
                legResults = results,
                payoffPoints = points,
                breakevens = breakevens,
                maxProfit = maxProfit,
                maxLoss = maxLoss
            )
        }
    }

    private data class PayoffResult(
        val points: List<PayoffPoint>,
        val breakevens: List<Double>,
        val maxProfit: Double,
        val maxLoss: Double
    )

    private fun generatePayoffChart(s: PnLSimulatorState): PayoffResult {
        val firstLeg = s.legs.firstOrNull() ?: return PayoffResult(emptyList(), emptyList(), 0.0, 0.0)
        val baseSpot = firstLeg.spotPrice
        val rangeMin = baseSpot * 0.90
        val rangeMax = baseSpot * 1.10
        val step = (rangeMax - rangeMin) / 50.0

        val points = mutableListOf<PayoffPoint>()
        var maxProfit = Double.NEGATIVE_INFINITY
        var maxLoss = Double.POSITIVE_INFINITY

        for (i in 0..50) {
            val simSpot = rangeMin + step * i
            var totalPnlAtSpot = 0.0

            for (leg in s.legs) {
                val dirMultiplier = if (leg.position == LegDirection.BUY) 1.0 else -1.0
                val newIV = (leg.iv * (1.0 + s.ivChangePercent / 100.0)).coerceIn(0.01, 5.0)
                val remainingDays = (leg.daysToExpiry - s.daysElapsed.toInt()).coerceAtLeast(0)
                val T = remainingDays / 365.0

                val price: Double = if (T > 0) {
                    BlackScholesEngine.calculateOptionPrice(
                        spotPrice = simSpot,
                        strikePrice = leg.strikePrice,
                        timeToExpiry = T,
                        riskFreeRate = AppConstants.DEFAULT_RISK_FREE_RATE,
                        volatility = newIV,
                        isCall = leg.optionType == OptionType.CALL
                    )
                } else {
                    when (leg.optionType) {
                        OptionType.CALL -> maxOf(0.0, simSpot - leg.strikePrice)
                        OptionType.PUT -> maxOf(0.0, leg.strikePrice - simSpot)
                    }
                }

                val positionSize = leg.quantity.toDouble() * leg.lotSize.toDouble()
                totalPnlAtSpot += (price - leg.entryPremium) * positionSize * dirMultiplier
            }

            points.add(PayoffPoint(spotPrice = simSpot, payoff = totalPnlAtSpot))
            if (totalPnlAtSpot > maxProfit) maxProfit = totalPnlAtSpot
            if (totalPnlAtSpot < maxLoss) maxLoss = totalPnlAtSpot
        }

        // Find breakeven crossings (where payoff crosses zero)
        val breakevens = mutableListOf<Double>()
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            if ((p1.payoff <= 0 && p2.payoff >= 0) || (p1.payoff >= 0 && p2.payoff <= 0)) {
                // Linear interpolation
                val ratio = kotlin.math.abs(p1.payoff) / (kotlin.math.abs(p1.payoff) + kotlin.math.abs(p2.payoff))
                val be = p1.spotPrice + ratio * (p2.spotPrice - p1.spotPrice)
                breakevens.add(be)
            }
        }

        return PayoffResult(points, breakevens, maxProfit, maxLoss)
    }

    /** Helper for snapshotFlow of MutableStateFlow */
    private fun <T> snapshotFlow(block: () -> T): Flow<T> {
        return _state.map { block() }.distinctUntilChanged()
    }
}
