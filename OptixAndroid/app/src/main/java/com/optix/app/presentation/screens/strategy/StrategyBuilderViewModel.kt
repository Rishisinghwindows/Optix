package com.optix.app.presentation.screens.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.optix.app.core.calculation.StrategyCalculationEngine
import com.optix.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class StrategyBuilderState(
    val selectedType: StrategyType = StrategyType.LONG_CALL,
    val selectedIndex: TradingIndex = TradingIndex.NIFTY50,
    val spotPrice: Double = 22000.0,
    val expiry: String = "",
    val legs: List<StrategyLeg> = emptyList(),
    val payoffData: PayoffData? = null,
    val marginRequired: Double = 0.0,
    val probabilityOfProfit: Double = 0.0,
    val isAnalyzing: Boolean = false,
    val touchedPrice: Double? = null,
    val touchedPayoff: Double? = null,
    val selectedLegIndex: Int? = null
)

@HiltViewModel
class StrategyBuilderViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(StrategyBuilderState())
    val state: StateFlow<StrategyBuilderState> = _state.asStateFlow()

    private val lotSize: Int get() = _state.value.selectedIndex.lotSize

    init {
        // Initialize with default strategy
        applyTemplate(StrategyType.LONG_CALL)
    }

    fun selectStrategyType(type: StrategyType) {
        _state.update { it.copy(selectedType = type) }
        applyTemplate(type)
    }

    fun setSpotPrice(price: Double) {
        _state.update { it.copy(spotPrice = price) }
        recalculate()
    }

    fun setExpiry(expiry: String) {
        _state.update { it.copy(expiry = expiry) }
    }

    fun setIndex(index: TradingIndex) {
        _state.update { it.copy(selectedIndex = index) }
        recalculate()
    }

    fun addLeg(leg: StrategyLeg) {
        _state.update { it.copy(legs = it.legs + leg) }
        recalculate()
    }

    fun removeLeg(legId: String) {
        _state.update { it.copy(legs = it.legs.filter { leg -> leg.id != legId }) }
        recalculate()
    }

    fun updateLeg(legId: String, updatedLeg: StrategyLeg) {
        _state.update { state ->
            state.copy(legs = state.legs.map { if (it.id == legId) updatedLeg else it })
        }
        recalculate()
    }

    fun updateLegStrike(legId: String, strike: Double) {
        _state.update { state ->
            state.copy(legs = state.legs.map {
                if (it.id == legId) it.copy(strikePrice = strike) else it
            })
        }
        recalculate()
    }

    fun updateLegPremium(legId: String, premium: Double) {
        _state.update { state ->
            state.copy(legs = state.legs.map {
                if (it.id == legId) it.copy(premium = premium) else it
            })
        }
        recalculate()
    }

    fun updateLegQuantity(legId: String, quantity: Int) {
        _state.update { state ->
            state.copy(legs = state.legs.map {
                if (it.id == legId) it.copy(quantity = quantity) else it
            })
        }
        recalculate()
    }

    fun toggleLegPosition(legId: String) {
        _state.update { state ->
            state.copy(legs = state.legs.map { leg ->
                if (leg.id == legId) {
                    leg.copy(position = if (leg.position == LegPosition.BUY) LegPosition.SELL else LegPosition.BUY)
                } else leg
            })
        }
        recalculate()
    }

    fun toggleLegOptionType(legId: String) {
        _state.update { state ->
            state.copy(legs = state.legs.map { leg ->
                if (leg.id == legId) {
                    leg.copy(optionType = if (leg.optionType == OptionType.CALL) OptionType.PUT else OptionType.CALL)
                } else leg
            })
        }
        recalculate()
    }

    fun clearLegs() {
        _state.update { it.copy(legs = emptyList(), payoffData = null) }
    }

    fun resetToTemplate() {
        applyTemplate(_state.value.selectedType)
    }

    fun handleChartTouch(price: Double) {
        val currentState = _state.value
        if (currentState.legs.isEmpty()) return

        val strategy = Strategy(
            id = UUID.randomUUID().toString(),
            type = currentState.selectedType,
            symbol = currentState.selectedIndex.symbol,
            expiry = currentState.expiry,
            legs = currentState.legs,
            spotPrice = currentState.spotPrice
        )

        val payoff = StrategyCalculationEngine.calculatePayoff(strategy, price)
        _state.update { it.copy(touchedPrice = price, touchedPayoff = payoff * lotSize) }
    }

    fun clearChartTouch() {
        _state.update { it.copy(touchedPrice = null, touchedPayoff = null) }
    }

    private fun applyTemplate(type: StrategyType) {
        val spot = _state.value.spotPrice
        val atmStrike = roundToStrike(spot)

        val legs = when (type) {
            StrategyType.LONG_CALL -> listOf(
                createLeg(atmStrike, OptionType.CALL, LegPosition.BUY, estimatePremium(atmStrike, OptionType.CALL))
            )
            StrategyType.SHORT_CALL -> listOf(
                createLeg(atmStrike, OptionType.CALL, LegPosition.SELL, estimatePremium(atmStrike, OptionType.CALL))
            )
            StrategyType.LONG_PUT -> listOf(
                createLeg(atmStrike, OptionType.PUT, LegPosition.BUY, estimatePremium(atmStrike, OptionType.PUT))
            )
            StrategyType.SHORT_PUT -> listOf(
                createLeg(atmStrike, OptionType.PUT, LegPosition.SELL, estimatePremium(atmStrike, OptionType.PUT))
            )
            StrategyType.BULL_CALL_SPREAD -> {
                val lowerStrike = atmStrike
                val upperStrike = atmStrike + 100
                listOf(
                    createLeg(lowerStrike, OptionType.CALL, LegPosition.BUY, estimatePremium(lowerStrike, OptionType.CALL)),
                    createLeg(upperStrike, OptionType.CALL, LegPosition.SELL, estimatePremium(upperStrike, OptionType.CALL))
                )
            }
            StrategyType.BEAR_PUT_SPREAD -> {
                val lowerStrike = atmStrike - 100
                val upperStrike = atmStrike
                listOf(
                    createLeg(upperStrike, OptionType.PUT, LegPosition.BUY, estimatePremium(upperStrike, OptionType.PUT)),
                    createLeg(lowerStrike, OptionType.PUT, LegPosition.SELL, estimatePremium(lowerStrike, OptionType.PUT))
                )
            }
            StrategyType.BEAR_CALL_SPREAD -> {
                val lowerStrike = atmStrike
                val upperStrike = atmStrike + 100
                listOf(
                    createLeg(lowerStrike, OptionType.CALL, LegPosition.SELL, estimatePremium(lowerStrike, OptionType.CALL)),
                    createLeg(upperStrike, OptionType.CALL, LegPosition.BUY, estimatePremium(upperStrike, OptionType.CALL))
                )
            }
            StrategyType.BULL_PUT_SPREAD -> {
                val lowerStrike = atmStrike - 100
                val upperStrike = atmStrike
                listOf(
                    createLeg(upperStrike, OptionType.PUT, LegPosition.SELL, estimatePremium(upperStrike, OptionType.PUT)),
                    createLeg(lowerStrike, OptionType.PUT, LegPosition.BUY, estimatePremium(lowerStrike, OptionType.PUT))
                )
            }
            StrategyType.LONG_STRADDLE -> listOf(
                createLeg(atmStrike, OptionType.CALL, LegPosition.BUY, estimatePremium(atmStrike, OptionType.CALL)),
                createLeg(atmStrike, OptionType.PUT, LegPosition.BUY, estimatePremium(atmStrike, OptionType.PUT))
            )
            StrategyType.SHORT_STRADDLE -> listOf(
                createLeg(atmStrike, OptionType.CALL, LegPosition.SELL, estimatePremium(atmStrike, OptionType.CALL)),
                createLeg(atmStrike, OptionType.PUT, LegPosition.SELL, estimatePremium(atmStrike, OptionType.PUT))
            )
            StrategyType.LONG_STRANGLE -> {
                val callStrike = atmStrike + 100
                val putStrike = atmStrike - 100
                listOf(
                    createLeg(callStrike, OptionType.CALL, LegPosition.BUY, estimatePremium(callStrike, OptionType.CALL)),
                    createLeg(putStrike, OptionType.PUT, LegPosition.BUY, estimatePremium(putStrike, OptionType.PUT))
                )
            }
            StrategyType.SHORT_STRANGLE -> {
                val callStrike = atmStrike + 100
                val putStrike = atmStrike - 100
                listOf(
                    createLeg(callStrike, OptionType.CALL, LegPosition.SELL, estimatePremium(callStrike, OptionType.CALL)),
                    createLeg(putStrike, OptionType.PUT, LegPosition.SELL, estimatePremium(putStrike, OptionType.PUT))
                )
            }
            StrategyType.IRON_CONDOR -> {
                val putBuy = atmStrike - 200
                val putSell = atmStrike - 100
                val callSell = atmStrike + 100
                val callBuy = atmStrike + 200
                listOf(
                    createLeg(putBuy, OptionType.PUT, LegPosition.BUY, estimatePremium(putBuy, OptionType.PUT)),
                    createLeg(putSell, OptionType.PUT, LegPosition.SELL, estimatePremium(putSell, OptionType.PUT)),
                    createLeg(callSell, OptionType.CALL, LegPosition.SELL, estimatePremium(callSell, OptionType.CALL)),
                    createLeg(callBuy, OptionType.CALL, LegPosition.BUY, estimatePremium(callBuy, OptionType.CALL))
                )
            }
            StrategyType.IRON_BUTTERFLY -> {
                val lowerPut = atmStrike - 100
                val upperCall = atmStrike + 100
                listOf(
                    createLeg(lowerPut, OptionType.PUT, LegPosition.BUY, estimatePremium(lowerPut, OptionType.PUT)),
                    createLeg(atmStrike, OptionType.PUT, LegPosition.SELL, estimatePremium(atmStrike, OptionType.PUT)),
                    createLeg(atmStrike, OptionType.CALL, LegPosition.SELL, estimatePremium(atmStrike, OptionType.CALL)),
                    createLeg(upperCall, OptionType.CALL, LegPosition.BUY, estimatePremium(upperCall, OptionType.CALL))
                )
            }
            StrategyType.LONG_BUTTERFLY -> {
                val lower = atmStrike - 100
                val middle = atmStrike
                val upper = atmStrike + 100
                listOf(
                    createLeg(lower, OptionType.CALL, LegPosition.BUY, estimatePremium(lower, OptionType.CALL)),
                    createLeg(middle, OptionType.CALL, LegPosition.SELL, estimatePremium(middle, OptionType.CALL), quantity = 2),
                    createLeg(upper, OptionType.CALL, LegPosition.BUY, estimatePremium(upper, OptionType.CALL))
                )
            }
            StrategyType.SHORT_BUTTERFLY -> {
                val lower = atmStrike - 100
                val middle = atmStrike
                val upper = atmStrike + 100
                listOf(
                    createLeg(lower, OptionType.CALL, LegPosition.SELL, estimatePremium(lower, OptionType.CALL)),
                    createLeg(middle, OptionType.CALL, LegPosition.BUY, estimatePremium(middle, OptionType.CALL), quantity = 2),
                    createLeg(upper, OptionType.CALL, LegPosition.SELL, estimatePremium(upper, OptionType.CALL))
                )
            }
            StrategyType.CUSTOM -> emptyList()
            // Strategies that need more complex setup
            StrategyType.COVERED_CALL,
            StrategyType.PROTECTIVE_PUT,
            StrategyType.JADE_LIZARD,
            StrategyType.CALENDAR_SPREAD,
            StrategyType.DIAGONAL_SPREAD,
            StrategyType.RATIO_SPREAD -> emptyList()
        }

        _state.update { it.copy(legs = legs) }
        recalculate()
    }

    private fun createLeg(
        strike: Double,
        optionType: OptionType,
        position: LegPosition,
        premium: Double,
        quantity: Int = 1
    ): StrategyLeg {
        val greeks = estimateGreeks(strike, optionType, position)
        return StrategyLeg(
            id = UUID.randomUUID().toString(),
            strikePrice = strike,
            optionType = optionType,
            position = position,
            quantity = quantity,
            premium = premium,
            greeks = greeks
        )
    }

    private fun estimatePremium(strike: Double, optionType: OptionType): Double {
        val spot = _state.value.spotPrice
        val diff = kotlin.math.abs(spot - strike)
        val atm = spot * 0.02 // ~2% ATM premium
        return when {
            diff < 50 -> atm
            diff < 100 -> atm * 0.7
            diff < 200 -> atm * 0.4
            else -> atm * 0.2
        }
    }

    private fun estimateGreeks(strike: Double, optionType: OptionType, position: LegPosition): Greeks {
        val spot = _state.value.spotPrice
        val moneyness = spot / strike

        // Simple approximation for demo purposes
        val baseDelta = when (optionType) {
            OptionType.CALL -> when {
                moneyness > 1.05 -> 0.8
                moneyness < 0.95 -> 0.2
                else -> 0.5
            }
            OptionType.PUT -> when {
                moneyness > 1.05 -> -0.2
                moneyness < 0.95 -> -0.8
                else -> -0.5
            }
        }

        val baseGamma = 0.001 * (1 - kotlin.math.abs(1 - moneyness) * 5).coerceIn(0.0, 1.0)
        val baseTheta = -spot * 0.0005 // Approximate daily theta
        val baseVega = spot * 0.001 * (1 - kotlin.math.abs(1 - moneyness) * 3).coerceIn(0.0, 1.0)

        return Greeks(
            delta = baseDelta,
            gamma = baseGamma,
            theta = baseTheta,
            vega = baseVega,
            rho = 0.0
        )
    }

    private fun roundToStrike(price: Double): Double {
        val interval = if (price > 40000) 100.0 else 50.0
        return kotlin.math.round(price / interval) * interval
    }

    private fun recalculate() {
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true) }

            val currentState = _state.value
            if (currentState.legs.isEmpty()) {
                _state.update { it.copy(payoffData = null, isAnalyzing = false) }
                return@launch
            }

            val strategy = Strategy(
                id = UUID.randomUUID().toString(),
                type = currentState.selectedType,
                symbol = currentState.selectedIndex.symbol,
                expiry = currentState.expiry,
                legs = currentState.legs,
                spotPrice = currentState.spotPrice
            )

            val payoffData = StrategyCalculationEngine.analyzeStrategy(strategy, lotSize)
            val margin = StrategyCalculationEngine.estimateMargin(strategy, lotSize)
            val pop = StrategyCalculationEngine.calculatePOP(payoffData)

            _state.update {
                it.copy(
                    payoffData = payoffData,
                    marginRequired = margin,
                    probabilityOfProfit = pop,
                    isAnalyzing = false
                )
            }
        }
    }
}
