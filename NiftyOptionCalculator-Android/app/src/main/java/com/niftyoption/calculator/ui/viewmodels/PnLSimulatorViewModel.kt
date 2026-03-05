package com.niftyoption.calculator.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.niftyoption.calculator.data.models.LegSimResult
import com.niftyoption.calculator.data.models.OptionType
import com.niftyoption.calculator.data.models.PayoffPoint
import com.niftyoption.calculator.data.models.SimulationInput
import com.niftyoption.calculator.data.models.SimulationResult
import com.niftyoption.calculator.domain.BlackScholesEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

class PnLSimulatorViewModel : ViewModel() {

    companion object {
        private const val RISK_FREE_RATE = 0.065
    }

    private val _legs = MutableStateFlow<List<SimulationInput>>(emptyList())
    val legs: StateFlow<List<SimulationInput>> = _legs.asStateFlow()

    private val _spotChangePercent = MutableStateFlow(0f)
    val spotChangePercent: StateFlow<Float> = _spotChangePercent.asStateFlow()

    private val _daysElapsed = MutableStateFlow(0)
    val daysElapsed: StateFlow<Int> = _daysElapsed.asStateFlow()

    private val _ivChangePercent = MutableStateFlow(0f)
    val ivChangePercent: StateFlow<Float> = _ivChangePercent.asStateFlow()

    private val _result = MutableStateFlow<SimulationResult?>(null)
    val result: StateFlow<SimulationResult?> = _result.asStateFlow()

    private val _maxDTE = MutableStateFlow(30)
    val maxDTE: StateFlow<Int> = _maxDTE.asStateFlow()

    fun addLeg(leg: SimulationInput) {
        _legs.value = _legs.value + leg
        updateMaxDTE()
        simulate()
    }

    fun removeLeg(index: Int) {
        val current = _legs.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _legs.value = current
            updateMaxDTE()
            simulate()
        }
    }

    fun addDefaultLeg() {
        val defaultLeg = SimulationInput(
            spotPrice = 25000.0,
            strikePrice = 25000.0,
            optionType = OptionType.CALL,
            entryPremium = 200.0,
            iv = 0.15,
            daysToExpiry = 7,
            lotSize = 75,
            quantity = 1
        )
        addLeg(defaultLeg)
    }

    fun updateSpotChange(value: Float) {
        _spotChangePercent.value = value.coerceIn(-10f, 10f)
        simulate()
    }

    fun updateDaysElapsed(value: Int) {
        _daysElapsed.value = value.coerceIn(0, _maxDTE.value)
        simulate()
    }

    fun updateIVChange(value: Float) {
        _ivChangePercent.value = value.coerceIn(-30f, 30f)
        simulate()
    }

    fun reset() {
        _spotChangePercent.value = 0f
        _daysElapsed.value = 0
        _ivChangePercent.value = 0f
        simulate()
    }

    private fun updateMaxDTE() {
        val maxDays = _legs.value.maxOfOrNull { it.daysToExpiry } ?: 30
        _maxDTE.value = max(maxDays, 1)
        if (_daysElapsed.value > _maxDTE.value) {
            _daysElapsed.value = _maxDTE.value
        }
    }

    fun simulate() {
        val currentLegs = _legs.value
        if (currentLegs.isEmpty()) {
            _result.value = null
            return
        }

        val spotChange = _spotChangePercent.value.toDouble()
        val daysElapsedVal = _daysElapsed.value
        val ivChange = _ivChangePercent.value.toDouble()

        val legResults = mutableListOf<LegSimResult>()
        var totalPnl = 0.0
        var totalInvestment = 0.0

        for (leg in currentLegs) {
            val newSpot = leg.spotPrice * (1 + spotChange / 100.0)
            val remainingDays = max(leg.daysToExpiry - daysElapsedVal, 0)
            val newT = remainingDays.toDouble() / 365.0
            val newIV = max(leg.iv * (1 + ivChange / 100.0), 0.001)

            val newPrice = if (newT <= 0) {
                // At expiry, option value is intrinsic only
                when (leg.optionType) {
                    OptionType.CALL -> max(newSpot - leg.strikePrice, 0.0)
                    OptionType.PUT -> max(leg.strikePrice - newSpot, 0.0)
                }
            } else {
                BlackScholesEngine.calculateOptionPrice(
                    spotPrice = newSpot,
                    strikePrice = leg.strikePrice,
                    timeToExpiry = newT,
                    riskFreeRate = RISK_FREE_RATE,
                    volatility = newIV,
                    optionType = leg.optionType
                )
            }

            val newGreeks = if (newT > 0 && newIV > 0) {
                BlackScholesEngine.calculateGreeks(
                    spotPrice = newSpot,
                    strikePrice = leg.strikePrice,
                    timeToExpiry = newT,
                    riskFreeRate = RISK_FREE_RATE,
                    volatility = newIV,
                    optionType = leg.optionType
                )
            } else null

            val pnlPerLot = (newPrice - leg.entryPremium) * leg.lotSize * leg.quantity
            val pnlPct = if (leg.entryPremium > 0) {
                ((newPrice - leg.entryPremium) / leg.entryPremium) * 100
            } else 0.0

            totalPnl += pnlPerLot
            totalInvestment += leg.entryPremium * leg.lotSize * leg.quantity

            legResults.add(
                LegSimResult(
                    input = leg,
                    newPrice = newPrice,
                    pnl = pnlPerLot,
                    pnlPercent = pnlPct,
                    newDelta = newGreeks?.delta ?: 0.0,
                    newGamma = newGreeks?.gamma ?: 0.0,
                    newTheta = newGreeks?.theta ?: 0.0,
                    newVega = newGreeks?.vega ?: 0.0
                )
            )
        }

        val totalPnlPercent = if (totalInvestment > 0) {
            (totalPnl / totalInvestment) * 100
        } else 0.0

        // Generate payoff points (41 points from -10% to +10%)
        val payoffPoints = generatePayoffPoints(currentLegs, daysElapsedVal, ivChange)

        _result.value = SimulationResult(
            totalPnl = totalPnl,
            totalPnlPercent = totalPnlPercent,
            legResults = legResults,
            payoffPoints = payoffPoints
        )
    }

    private fun generatePayoffPoints(
        legs: List<SimulationInput>,
        daysElapsedVal: Int,
        ivChange: Double
    ): List<PayoffPoint> {
        val points = mutableListOf<PayoffPoint>()
        val referenceSpot = legs.firstOrNull()?.spotPrice ?: return points

        for (i in 0..40) {
            val pctChange = -10.0 + (i * 0.5) // -10% to +10% in 0.5% steps
            val simSpot = referenceSpot * (1 + pctChange / 100.0)
            var totalPnl = 0.0

            for (leg in legs) {
                val newSpot = leg.spotPrice * (simSpot / referenceSpot)
                val remainingDays = max(leg.daysToExpiry - daysElapsedVal, 0)
                val newT = remainingDays.toDouble() / 365.0
                val newIV = max(leg.iv * (1 + ivChange / 100.0), 0.001)

                val newPrice = if (newT <= 0) {
                    when (leg.optionType) {
                        OptionType.CALL -> max(newSpot - leg.strikePrice, 0.0)
                        OptionType.PUT -> max(leg.strikePrice - newSpot, 0.0)
                    }
                } else {
                    BlackScholesEngine.calculateOptionPrice(
                        spotPrice = newSpot,
                        strikePrice = leg.strikePrice,
                        timeToExpiry = newT,
                        riskFreeRate = RISK_FREE_RATE,
                        volatility = newIV,
                        optionType = leg.optionType
                    )
                }

                totalPnl += (newPrice - leg.entryPremium) * leg.lotSize * leg.quantity
            }

            points.add(PayoffPoint(spotPrice = simSpot, pnl = totalPnl))
        }

        return points
    }
}
