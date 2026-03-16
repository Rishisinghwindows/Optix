package com.optix.app.presentation.screens.calculator

import androidx.lifecycle.ViewModel
import com.optix.app.core.calculation.BlackScholesEngine
import com.optix.app.domain.model.GreeksResult
import com.optix.app.domain.model.OptionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

data class CalculatorState(
    val spotPrice: String = "25935",
    val strikePrice: String = "25800",
    val daysToExpiry: String = "7",
    val volatility: String = "15.0",
    val riskFreeRate: String = "6.5",
    val currentLTP: String = "",
    val isCall: Boolean = true,
    val targetPrice: String = "",
    val stopLossPrice: String = "",

    // UI State
    val showDisclaimer: Boolean = true,
    val showAdvancedAnalysis: Boolean = true,
    val isCalculating: Boolean = false,

    // Results
    val result: CalculationResult? = null,
    val impliedVolatility: Double? = null,
    val targetCalculation: TargetCalculation? = null
)

data class CalculationResult(
    val optionPrice: Double,
    val greeks: GreeksResult,
    val intrinsicValue: Double,
    val timeValue: Double,
    val moneyness: String // "ITM", "ATM", "OTM"
)

data class TargetCalculation(
    val currentOptionPrice: Double,
    val targetSpot: Double,
    val stopLossSpot: Double,
    val targetOptionPrice: Double,
    val stopLossOptionPrice: Double,
    val targetProfit: Double,
    val stopLossLoss: Double,
    val targetProfitPercent: Double,
    val stopLossLossPercent: Double,
    val riskRewardRatio: Double,
    val isGoodRiskReward: Boolean
) {
    val displayProfit: String
        get() = if (targetProfit >= 0) "+₹${String.format("%.2f", targetProfit)}" else "₹${String.format("%.2f", targetProfit)}"

    val displayLoss: String
        get() = "-₹${String.format("%.2f", abs(stopLossLoss))}"

    val displayRiskReward: String
        get() = "1:${String.format("%.1f", riskRewardRatio)}"
}

@HiltViewModel
class CalculatorViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(CalculatorState())
    val state: StateFlow<CalculatorState> = _state.asStateFlow()

    init {
        // Auto-calculate on init
        calculate()
    }

    fun setOptionType(isCall: Boolean) {
        _state.update { current ->
            // Swap target and stop-loss when switching option type
            val newTarget = if (isCall) {
                current.spotPrice.toDoubleOrNull()?.let { String.format("%.0f", it + 500) } ?: ""
            } else {
                current.spotPrice.toDoubleOrNull()?.let { String.format("%.0f", it - 500) } ?: ""
            }
            val newStopLoss = if (isCall) {
                current.spotPrice.toDoubleOrNull()?.let { String.format("%.0f", it - 300) } ?: ""
            } else {
                current.spotPrice.toDoubleOrNull()?.let { String.format("%.0f", it + 300) } ?: ""
            }
            current.copy(
                isCall = isCall,
                targetPrice = newTarget,
                stopLossPrice = newStopLoss
            )
        }
        calculate()
    }

    fun updateSpotPrice(value: String) {
        _state.update { it.copy(spotPrice = value) }
        calculateIfValid()
    }

    fun updateStrikePrice(value: String) {
        android.util.Log.d("Calculator", "updateStrikePrice: $value")
        _state.update { it.copy(strikePrice = value) }
        calculateIfValid()
    }

    fun updateDaysToExpiry(value: String) {
        _state.update { it.copy(daysToExpiry = value) }
        calculateIfValid()
    }

    fun updateVolatility(value: String) {
        _state.update { it.copy(volatility = value) }
        calculateIfValid()
    }

    fun updateRiskFreeRate(value: String) {
        _state.update { it.copy(riskFreeRate = value) }
        calculateIfValid()
    }

    fun updateCurrentLTP(value: String) {
        _state.update { it.copy(currentLTP = value) }
    }

    fun updateTargetPrice(value: String) {
        _state.update { it.copy(targetPrice = value) }
        calculateTargetStopLoss()
    }

    fun updateStopLossPrice(value: String) {
        _state.update { it.copy(stopLossPrice = value) }
        calculateTargetStopLoss()
    }

    fun setTargetPreset(offset: Int) {
        val spotPrice = _state.value.spotPrice.toDoubleOrNull() ?: return
        val newTarget = spotPrice + offset
        _state.update { it.copy(targetPrice = String.format("%.0f", newTarget)) }
        calculateTargetStopLoss()
    }

    fun setStopLossPreset(offset: Int) {
        val spotPrice = _state.value.spotPrice.toDoubleOrNull() ?: return
        val newStopLoss = spotPrice + offset
        _state.update { it.copy(stopLossPrice = String.format("%.0f", newStopLoss)) }
        calculateTargetStopLoss()
    }

    fun toggleAdvancedAnalysis() {
        _state.update { it.copy(showAdvancedAnalysis = !it.showAdvancedAnalysis) }
    }

    fun dismissDisclaimer() {
        _state.update { it.copy(showDisclaimer = false) }
    }

    fun reset() {
        _state.value = CalculatorState()
        calculate()
    }

    /**
     * Initialize calculator with values from option chain
     */
    fun initializeFromOptionChain(
        spotPrice: Double,
        strikePrice: Double,
        ltp: Double,
        isCall: Boolean,
        daysToExpiry: Int
    ) {
        android.util.Log.d("Calculator", "initializeFromOptionChain: spot=$spotPrice, strike=$strikePrice, ltp=$ltp, isCall=$isCall, days=$daysToExpiry")

        val defaultTarget = if (isCall) spotPrice + 500 else spotPrice - 500
        val defaultStopLoss = if (isCall) spotPrice - 300 else spotPrice + 300

        _state.update {
            it.copy(
                spotPrice = String.format("%.2f", spotPrice),
                strikePrice = String.format("%.0f", strikePrice),
                currentLTP = if (ltp > 0) String.format("%.2f", ltp) else "",
                isCall = isCall,
                daysToExpiry = daysToExpiry.toString(),
                targetPrice = String.format("%.0f", defaultTarget),
                stopLossPrice = String.format("%.0f", defaultStopLoss),
                showDisclaimer = false
            )
        }
        // Auto-calculate IV if LTP is provided
        if (ltp > 0) {
            calculateIV()
        } else {
            calculate()
        }
    }

    /**
     * Calculate IV from current LTP
     */
    fun calculateIV() {
        val currentState = _state.value
        val spotPrice = currentState.spotPrice.toDoubleOrNull() ?: run { calculate(); return }
        val strikePrice = currentState.strikePrice.toDoubleOrNull() ?: run { calculate(); return }
        val daysToExpiry = currentState.daysToExpiry.toDoubleOrNull() ?: run { calculate(); return }
        val marketPrice = currentState.currentLTP.toDoubleOrNull() ?: run { calculate(); return }
        val riskFreeRate = (currentState.riskFreeRate.toDoubleOrNull() ?: 6.5) / 100.0

        // Can't calculate IV for expired options - just calculate theoretical price
        if (daysToExpiry <= 0 || marketPrice <= 0) {
            calculate()
            return
        }

        val timeToExpiry = daysToExpiry / 365.0

        // Newton-Raphson method to find IV
        var iv = 0.20 // Initial guess 20%
        val tolerance = 0.0001
        val maxIterations = 100

        for (i in 0 until maxIterations) {
            val price = BlackScholesEngine.calculateOptionPrice(
                spotPrice = spotPrice,
                strikePrice = strikePrice,
                timeToExpiry = timeToExpiry,
                riskFreeRate = riskFreeRate,
                volatility = iv,
                isCall = currentState.isCall
            )

            val vega = BlackScholesEngine.calculateGreeks(
                spotPrice = spotPrice,
                strikePrice = strikePrice,
                timeToExpiry = timeToExpiry,
                riskFreeRate = riskFreeRate,
                volatility = iv,
                isCall = currentState.isCall
            ).vega

            if (abs(vega) < 0.0001) break

            val diff = marketPrice - price
            if (abs(diff) < tolerance) break

            iv += diff / (vega * 100)
            iv = iv.coerceIn(0.01, 5.0)
        }

        val impliedVol = iv * 100
        _state.update {
            it.copy(
                impliedVolatility = impliedVol,
                volatility = String.format("%.1f", impliedVol)
            )
        }
        calculate()
    }

    /**
     * Calculate option prices at target and stop-loss levels
     */
    private fun calculateTargetStopLoss() {
        val currentState = _state.value
        val targetSpot = currentState.targetPrice.toDoubleOrNull() ?: return
        val stopLossSpot = currentState.stopLossPrice.toDoubleOrNull() ?: return
        val strikePrice = currentState.strikePrice.toDoubleOrNull() ?: return
        val daysToExpiry = currentState.daysToExpiry.toDoubleOrNull() ?: return
        val volatility = (currentState.volatility.toDoubleOrNull() ?: return) / 100.0
        val riskFreeRate = (currentState.riskFreeRate.toDoubleOrNull() ?: 6.5) / 100.0

        val currentOptionPrice = currentState.result?.optionPrice ?: return

        val timeToExpiry = if (daysToExpiry > 0) daysToExpiry / 365.0 else 0.0001

        // Calculate option price at target
        val targetOptionPrice = BlackScholesEngine.calculateOptionPrice(
            spotPrice = targetSpot,
            strikePrice = strikePrice,
            timeToExpiry = timeToExpiry,
            riskFreeRate = riskFreeRate,
            volatility = volatility,
            isCall = currentState.isCall
        )

        // Calculate option price at stop-loss
        val stopLossOptionPrice = BlackScholesEngine.calculateOptionPrice(
            spotPrice = stopLossSpot,
            strikePrice = strikePrice,
            timeToExpiry = timeToExpiry,
            riskFreeRate = riskFreeRate,
            volatility = volatility,
            isCall = currentState.isCall
        )

        val targetProfit = targetOptionPrice - currentOptionPrice
        val stopLossLoss = currentOptionPrice - stopLossOptionPrice

        val targetProfitPercent = if (currentOptionPrice > 0) (targetProfit / currentOptionPrice) * 100 else 0.0
        val stopLossLossPercent = if (currentOptionPrice > 0) (stopLossLoss / currentOptionPrice) * 100 else 0.0

        val riskRewardRatio = if (stopLossLoss > 0) targetProfit / stopLossLoss else 0.0

        _state.update {
            it.copy(
                targetCalculation = TargetCalculation(
                    currentOptionPrice = currentOptionPrice,
                    targetSpot = targetSpot,
                    stopLossSpot = stopLossSpot,
                    targetOptionPrice = targetOptionPrice,
                    stopLossOptionPrice = stopLossOptionPrice,
                    targetProfit = targetProfit,
                    stopLossLoss = stopLossLoss,
                    targetProfitPercent = targetProfitPercent,
                    stopLossLossPercent = stopLossLossPercent,
                    riskRewardRatio = riskRewardRatio,
                    isGoodRiskReward = riskRewardRatio >= 1.5
                )
            )
        }
    }

    private fun calculateIfValid() {
        val currentState = _state.value
        if (currentState.spotPrice.toDoubleOrNull() != null &&
            currentState.strikePrice.toDoubleOrNull() != null &&
            currentState.daysToExpiry.toDoubleOrNull() != null &&
            currentState.volatility.toDoubleOrNull() != null
        ) {
            calculate()
        }
    }

    fun calculate() {
        val currentState = _state.value

        val spotPrice = currentState.spotPrice.toDoubleOrNull() ?: return
        val strikePrice = currentState.strikePrice.toDoubleOrNull() ?: return
        val daysToExpiry = currentState.daysToExpiry.toDoubleOrNull() ?: return
        val volatility = (currentState.volatility.toDoubleOrNull() ?: return) / 100.0
        val riskFreeRate = (currentState.riskFreeRate.toDoubleOrNull() ?: 6.5) / 100.0

        val timeToExpiry = if (daysToExpiry > 0) daysToExpiry / 365.0 else 0.0001

        android.util.Log.d("Calculator", "Calculating: spot=$spotPrice, strike=$strikePrice, days=$daysToExpiry, iv=$volatility, isCall=${currentState.isCall}")

        val optionPrice = BlackScholesEngine.calculateOptionPrice(
            spotPrice = spotPrice,
            strikePrice = strikePrice,
            timeToExpiry = timeToExpiry,
            riskFreeRate = riskFreeRate,
            volatility = volatility,
            isCall = currentState.isCall
        )

        android.util.Log.d("Calculator", "Result: optionPrice=$optionPrice")

        val greeks = BlackScholesEngine.calculateGreeks(
            spotPrice = spotPrice,
            strikePrice = strikePrice,
            timeToExpiry = timeToExpiry,
            riskFreeRate = riskFreeRate,
            volatility = volatility,
            isCall = currentState.isCall
        )

        // Calculate intrinsic and time value
        val intrinsicValue = if (currentState.isCall) {
            max(0.0, spotPrice - strikePrice)
        } else {
            max(0.0, strikePrice - spotPrice)
        }
        val timeValue = max(0.0, optionPrice - intrinsicValue)

        // Determine moneyness
        val moneyness = when {
            currentState.isCall -> when {
                spotPrice > strikePrice -> "ITM"
                spotPrice < strikePrice -> "OTM"
                else -> "ATM"
            }
            else -> when {
                spotPrice < strikePrice -> "ITM"
                spotPrice > strikePrice -> "OTM"
                else -> "ATM"
            }
        }

        _state.update {
            it.copy(
                result = CalculationResult(
                    optionPrice = optionPrice,
                    greeks = greeks,
                    intrinsicValue = intrinsicValue,
                    timeValue = timeValue,
                    moneyness = moneyness
                )
            )
        }

        // Recalculate target/stop-loss if set
        if (currentState.targetPrice.isNotEmpty() && currentState.stopLossPrice.isNotEmpty()) {
            calculateTargetStopLoss()
        }
    }
}
