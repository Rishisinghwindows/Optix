package com.niftyoption.calculator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.niftyoption.calculator.data.api.NSERepository
import com.niftyoption.calculator.data.models.ExpiryDate
import com.niftyoption.calculator.data.models.LoadingState
import com.niftyoption.calculator.data.models.OptionChainRow
import com.niftyoption.calculator.data.models.OptionData
import com.niftyoption.calculator.data.models.OptionType
import com.niftyoption.calculator.data.models.GreeksResult
import com.niftyoption.calculator.data.models.TargetCalculation
import com.niftyoption.calculator.domain.BlackScholesEngine
import com.niftyoption.calculator.domain.TargetCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import kotlin.math.abs

class OptionChainViewModel : ViewModel() {

    private val repository = NSERepository()

    private val _optionChain = MutableStateFlow<List<OptionChainRow>>(emptyList())
    val optionChain: StateFlow<List<OptionChainRow>> = _optionChain.asStateFlow()

    private val _expiryDates = MutableStateFlow<List<ExpiryDate>>(emptyList())
    val expiryDates: StateFlow<List<ExpiryDate>> = _expiryDates.asStateFlow()

    private val _selectedExpiry = MutableStateFlow<ExpiryDate?>(null)
    val selectedExpiry: StateFlow<ExpiryDate?> = _selectedExpiry.asStateFlow()

    private val _spotPrice = MutableStateFlow(0.0)
    val spotPrice: StateFlow<Double> = _spotPrice.asStateFlow()

    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()

    private val _selectedOption = MutableStateFlow<OptionData?>(null)
    val selectedOption: StateFlow<OptionData?> = _selectedOption.asStateFlow()

    init {
        loadMockData()
    }

    fun loadData() {
        viewModelScope.launch {
            _loadingState.value = LoadingState.Loading

            try {
                val dates = repository.fetchExpiryDates()
                _expiryDates.value = dates

                if (_selectedExpiry.value == null && dates.isNotEmpty()) {
                    _selectedExpiry.value = dates.first()
                }

                _spotPrice.value = repository.fetchUnderlyingValue()

                val expiryString = _selectedExpiry.value?.id
                _optionChain.value = repository.fetchOptionChainData(expiry = expiryString)

                _loadingState.value = LoadingState.Loaded
            } catch (e: Exception) {
                _loadingState.value = LoadingState.Error(e.message ?: "Unknown error")
                loadMockData()
            }
        }
    }

    fun refresh() {
        loadData()
    }

    private fun loadMockData() {
        _spotPrice.value = 25000.0
        _optionChain.value = repository.generateMockData(_spotPrice.value)

        val calendar = Calendar.getInstance()
        _expiryDates.value = (0 until 4).map { weekOffset ->
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, 7 * weekOffset)
            val formatter = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.getDefault())
            ExpiryDate.fromString(formatter.format(calendar.time))
        }
        _selectedExpiry.value = _expiryDates.value.firstOrNull()
        _loadingState.value = LoadingState.Loaded
    }

    fun selectExpiry(expiry: ExpiryDate) {
        _selectedExpiry.value = expiry
        loadData()
    }

    fun selectOption(option: OptionData) {
        _selectedOption.value = option
    }

    fun clearSelectedOption() {
        _selectedOption.value = null
    }

    fun getNearbyStrikes(chain: List<OptionChainRow>, spot: Double): List<OptionChainRow> {
        if (spot <= 0) return chain
        return chain.filter { abs(it.strikePrice - spot) <= 500 }
    }

    fun calculatePCR(chain: List<OptionChainRow>): Double {
        val totalCallOI = getTotalCallOI(chain)
        val totalPutOI = getTotalPutOI(chain)
        return if (totalCallOI > 0) totalPutOI.toDouble() / totalCallOI.toDouble() else 0.0
    }

    fun getTotalCallOI(chain: List<OptionChainRow>): Int {
        return chain.mapNotNull { it.callOption?.openInterest }.sum()
    }

    fun getTotalPutOI(chain: List<OptionChainRow>): Int {
        return chain.mapNotNull { it.putOption?.openInterest }.sum()
    }

    fun calculateMaxPain(chain: List<OptionChainRow>): Double? {
        if (chain.isEmpty()) return null

        var minPain = Double.MAX_VALUE
        var maxPainStrike = chain[chain.size / 2].strikePrice

        for (row in chain) {
            var totalPain = 0.0

            for (otherRow in chain) {
                otherRow.callOption?.let { call ->
                    if (row.strikePrice > otherRow.strikePrice) {
                        totalPain += call.openInterest * (row.strikePrice - otherRow.strikePrice)
                    }
                }
            }

            for (otherRow in chain) {
                otherRow.putOption?.let { put ->
                    if (row.strikePrice < otherRow.strikePrice) {
                        totalPain += put.openInterest * (otherRow.strikePrice - row.strikePrice)
                    }
                }
            }

            if (totalPain < minPain) {
                minPain = totalPain
                maxPainStrike = row.strikePrice
            }
        }

        return maxPainStrike
    }
}

// MARK: - Calculator ViewModel

data class CalculatorUiState(
    val spotPrice: String = "25000",
    val strikePrice: String = "25000",
    val optionType: OptionType = OptionType.CALL,
    val daysToExpiry: String = "7",
    val impliedVolatility: String = "15",
    val targetSpot: String = "25500",
    val stopLossSpot: String = "24500",
    val currentOptionPrice: String = "",
    val calculatedPrice: Double? = null,
    val greeks: GreeksResult? = null,
    val targetCalculation: TargetCalculation? = null
) {
    val spotPriceValue: Double get() = spotPrice.toDoubleOrNull() ?: 0.0
    val strikePriceValue: Double get() = strikePrice.toDoubleOrNull() ?: 0.0
    val daysToExpiryValue: Int get() = daysToExpiry.toIntOrNull() ?: 0
    val ivValue: Double get() = (impliedVolatility.toDoubleOrNull() ?: 0.0) / 100.0
    val targetSpotValue: Double get() = targetSpot.toDoubleOrNull() ?: 0.0
    val stopLossSpotValue: Double get() = stopLossSpot.toDoubleOrNull() ?: 0.0
    val currentOptionPriceValue: Double get() = currentOptionPrice.toDoubleOrNull() ?: 0.0

    val timeToExpiry: Double get() = BlackScholesEngine.daysToYears(daysToExpiryValue)

    val isInputValid: Boolean
        get() = spotPriceValue > 0 && strikePriceValue > 0 && daysToExpiryValue > 0 && ivValue > 0

    val isTargetInputValid: Boolean
        get() = isInputValid && targetSpotValue > 0 && stopLossSpotValue > 0
}

class CalculatorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    fun loadOption(option: OptionData) {
        _uiState.value = _uiState.value.copy(
            spotPrice = "%.0f".format(option.underlyingValue),
            strikePrice = "%.0f".format(option.strikePrice),
            optionType = option.optionType,
            daysToExpiry = option.daysToExpiry.toString(),
            impliedVolatility = "%.1f".format(option.impliedVolatility * 100),
            currentOptionPrice = "%.2f".format(option.lastTradedPrice),
            targetSpot = if (option.optionType == OptionType.CALL) {
                "%.0f".format(option.underlyingValue + 500)
            } else {
                "%.0f".format(option.underlyingValue - 500)
            },
            stopLossSpot = if (option.optionType == OptionType.CALL) {
                "%.0f".format(option.underlyingValue - 300)
            } else {
                "%.0f".format(option.underlyingValue + 300)
            }
        )
        calculateAll()
    }

    fun updateSpotPrice(value: String) {
        _uiState.value = _uiState.value.copy(spotPrice = value)
    }

    fun updateStrikePrice(value: String) {
        _uiState.value = _uiState.value.copy(strikePrice = value)
    }

    fun updateDaysToExpiry(value: String) {
        _uiState.value = _uiState.value.copy(daysToExpiry = value)
    }

    fun updateImpliedVolatility(value: String) {
        _uiState.value = _uiState.value.copy(impliedVolatility = value)
    }

    fun updateTargetSpot(value: String) {
        _uiState.value = _uiState.value.copy(targetSpot = value)
    }

    fun updateStopLossSpot(value: String) {
        _uiState.value = _uiState.value.copy(stopLossSpot = value)
    }

    fun updateCurrentOptionPrice(value: String) {
        _uiState.value = _uiState.value.copy(currentOptionPrice = value)
    }

    fun updateOptionType(type: OptionType) {
        _uiState.value = _uiState.value.copy(optionType = type)
        calculateAll()
    }

    fun calculateAll() {
        calculatePrice()
        calculateGreeks()
        calculateTargetSL()
    }

    private fun calculatePrice() {
        val state = _uiState.value
        if (!state.isInputValid) {
            _uiState.value = _uiState.value.copy(calculatedPrice = null)
            return
        }

        val price = TargetCalculator.calculateOptionPrice(
            spotPrice = state.spotPriceValue,
            strikePrice = state.strikePriceValue,
            optionType = state.optionType,
            timeToExpiry = state.timeToExpiry,
            volatility = state.ivValue
        )
        _uiState.value = _uiState.value.copy(calculatedPrice = price)
    }

    private fun calculateGreeks() {
        val state = _uiState.value
        if (!state.isInputValid) {
            _uiState.value = _uiState.value.copy(greeks = null)
            return
        }

        val greeks = TargetCalculator.calculateGreeks(
            spotPrice = state.spotPriceValue,
            strikePrice = state.strikePriceValue,
            optionType = state.optionType,
            timeToExpiry = state.timeToExpiry,
            volatility = state.ivValue
        )
        _uiState.value = _uiState.value.copy(greeks = greeks)
    }

    private fun calculateTargetSL() {
        val state = _uiState.value
        if (!state.isTargetInputValid) {
            _uiState.value = _uiState.value.copy(targetCalculation = null)
            return
        }

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, state.daysToExpiryValue)
        val expiryDate = calendar.time

        val option = OptionData(
            strikePrice = state.strikePriceValue,
            optionType = state.optionType,
            expiryDate = expiryDate,
            lastTradedPrice = if (state.currentOptionPriceValue > 0) state.currentOptionPriceValue else (state.calculatedPrice ?: 0.0),
            impliedVolatility = state.ivValue,
            underlyingValue = state.spotPriceValue
        )

        val calculation = TargetCalculator.calculateTargetSL(
            option = option,
            targetSpot = state.targetSpotValue,
            stopLossSpot = state.stopLossSpotValue
        )
        _uiState.value = _uiState.value.copy(targetCalculation = calculation)
    }

    fun swapTargetSL() {
        val state = _uiState.value
        _uiState.value = state.copy(
            targetSpot = state.stopLossSpot,
            stopLossSpot = state.targetSpot
        )
        calculateTargetSL()
    }

    fun calculateImpliedVolatility() {
        val state = _uiState.value
        if (state.spotPriceValue <= 0 || state.strikePriceValue <= 0 ||
            state.timeToExpiry <= 0 || state.currentOptionPriceValue <= 0) {
            return
        }

        val iv = BlackScholesEngine.calculateImpliedVolatility(
            optionPrice = state.currentOptionPriceValue,
            spotPrice = state.spotPriceValue,
            strikePrice = state.strikePriceValue,
            timeToExpiry = state.timeToExpiry,
            isCall = state.optionType == OptionType.CALL
        )

        iv?.let {
            _uiState.value = _uiState.value.copy(impliedVolatility = "%.1f".format(it * 100))
            calculateAll()
        }
    }

    fun reset() {
        _uiState.value = CalculatorUiState()
    }
}
