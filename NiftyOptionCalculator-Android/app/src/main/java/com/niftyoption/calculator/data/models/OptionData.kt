package com.niftyoption.calculator.data.models

import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

// MARK: - Option Type Enum

enum class OptionType(val code: String, val displayName: String) {
    CALL("CE", "Call"),
    PUT("PE", "Put");

    companion object {
        fun fromCode(code: String): OptionType = when (code) {
            "CE" -> CALL
            "PE" -> PUT
            else -> CALL
        }
    }
}

// MARK: - Option Data Model

data class OptionData(
    val id: String = UUID.randomUUID().toString(),
    val strikePrice: Double,
    val optionType: OptionType,
    val expiryDate: Date,
    val lastTradedPrice: Double,
    val openInterest: Int = 0,
    val changeInOI: Int = 0,
    val impliedVolatility: Double,
    val bidPrice: Double = 0.0,
    val askPrice: Double = 0.0,
    val bidQty: Int = 0,
    val askQty: Int = 0,
    val volume: Int = 0,
    val underlyingValue: Double
) {
    val isITM: Boolean
        get() = when (optionType) {
            OptionType.CALL -> underlyingValue > strikePrice
            OptionType.PUT -> underlyingValue < strikePrice
        }

    val isATM: Boolean
        get() = abs(underlyingValue - strikePrice) <= 50

    val moneyness: String
        get() = when {
            isATM -> "ATM"
            isITM -> "ITM"
            else -> "OTM"
        }

    val daysToExpiry: Int
        get() {
            val diff = expiryDate.time - Date().time
            return max((diff / (24 * 60 * 60 * 1000)).toInt(), 0)
        }

    val timeToExpiryYears: Double
        get() = daysToExpiry.toDouble() / 365.0

    val displayStrike: String
        get() = "%.0f".format(strikePrice)

    val displayLTP: String
        get() = "%.2f".format(lastTradedPrice)

    val displayIV: String
        get() = "%.2f%%".format(impliedVolatility * 100)
}

// MARK: - Option Chain Row

data class OptionChainRow(
    val id: String = UUID.randomUUID().toString(),
    val strikePrice: Double,
    val callOption: OptionData?,
    val putOption: OptionData?
) {
    val displayStrike: String
        get() = "%.0f".format(strikePrice)
}

// MARK: - Greeks Result

data class GreeksResult(
    val delta: Double,
    val gamma: Double,
    val theta: Double,  // Per day
    val vega: Double,   // Per 1% IV change
    val rho: Double     // Per 1% rate change
) {
    val displayDelta: String get() = "%.4f".format(delta)
    val displayGamma: String get() = "%.6f".format(gamma)
    val displayTheta: String get() = "%.2f".format(theta)
    val displayVega: String get() = "%.2f".format(vega)
    val displayRho: String get() = "%.2f".format(rho)

    val deltaInterpretation: String
        get() {
            val absDelta = abs(delta)
            return when {
                absDelta > 0.7 -> "Deep ITM - moves almost 1:1 with underlying"
                absDelta in 0.45..0.55 -> "ATM - 50% chance of expiring ITM"
                absDelta < 0.3 -> "Deep OTM - low probability of profit"
                else -> "Moderate sensitivity to underlying movement"
            }
        }

    val thetaInterpretation: String
        get() = when {
            theta < -5 -> "High time decay - losing value rapidly"
            theta < -2 -> "Moderate time decay"
            else -> "Low time decay"
        }

    val vegaInterpretation: String
        get() = when {
            vega > 10 -> "High volatility sensitivity"
            vega > 5 -> "Moderate volatility sensitivity"
            else -> "Low volatility sensitivity"
        }

    fun expectedPriceChange(underlyingMove: Double): Double {
        val firstOrder = delta * underlyingMove
        val secondOrder = 0.5 * gamma * underlyingMove * underlyingMove
        return firstOrder + secondOrder
    }

    fun expectedPrice(currentPrice: Double, underlyingMove: Double, daysElapsed: Double = 0.0): Double {
        val priceChange = expectedPriceChange(underlyingMove)
        val timeDecay = theta * daysElapsed
        return max(currentPrice + priceChange + timeDecay, 0.0)
    }
}

// MARK: - Target Calculation Result

data class TargetCalculation(
    val id: String = UUID.randomUUID().toString(),
    val optionType: OptionType,
    val strikePrice: Double,
    val currentSpot: Double,
    val currentOptionPrice: Double,
    val targetSpot: Double,
    val stopLossSpot: Double,
    val targetOptionPrice: Double,
    val stopLossOptionPrice: Double,
    val greeks: GreeksResult,
    val impliedVolatility: Double,
    val daysToExpiry: Int
) {
    val targetProfit: Double get() = targetOptionPrice - currentOptionPrice
    val stopLossLoss: Double get() = currentOptionPrice - stopLossOptionPrice
    val riskRewardRatio: Double get() = if (stopLossLoss > 0) targetProfit / stopLossLoss else 0.0

    val displayTargetPrice: String get() = "%.2f".format(targetOptionPrice)
    val displayStopLossPrice: String get() = "%.2f".format(stopLossOptionPrice)
    val displayProfit: String get() = "%+.2f".format(targetProfit)
    val displayLoss: String get() = "-%.2f".format(stopLossLoss)
    val displayRiskReward: String get() = "1:%.2f".format(riskRewardRatio)

    val profitPercentage: Double
        get() = if (currentOptionPrice > 0) (targetProfit / currentOptionPrice) * 100 else 0.0

    val lossPercentage: Double
        get() = if (currentOptionPrice > 0) (stopLossLoss / currentOptionPrice) * 100 else 0.0

    val displayProfitPercentage: String get() = "+%.1f%%".format(profitPercentage)
    val displayLossPercentage: String get() = "-%.1f%%".format(lossPercentage)
    val isGoodRiskReward: Boolean get() = riskRewardRatio >= 1.5
}

// MARK: - NSE API Response Models

data class NSEOptionChainResponse(
    val records: Records
) {
    data class Records(
        val expiryDates: List<String>,
        val data: List<DataItem>,
        val underlyingValue: Double,
        val strikePrices: List<Double>,
        val timestamp: String
    )

    data class DataItem(
        val strikePrice: Double,
        val expiryDate: String,
        @SerializedName("CE") val ce: OptionDetail?,
        @SerializedName("PE") val pe: OptionDetail?
    )

    data class OptionDetail(
        val strikePrice: Double,
        val expiryDate: String,
        val underlying: String,
        val identifier: String,
        val openInterest: Int,
        val changeinOpenInterest: Int,
        val pchangeinOpenInterest: Double,
        val totalTradedVolume: Int,
        val impliedVolatility: Double,
        val lastPrice: Double,
        val change: Double,
        val pChange: Double,
        val totalBuyQuantity: Int,
        val totalSellQuantity: Int,
        val bidQty: Int,
        val bidprice: Double,
        val askQty: Int,
        val askPrice: Double,
        val underlyingValue: Double
    )
}

// MARK: - Expiry Date Helper

data class ExpiryDate(
    val id: String,
    val date: Date,
    val displayString: String
) {
    companion object {
        fun fromString(dateString: String): ExpiryDate {
            val formatter = SimpleDateFormat("dd-MMM-yyyy", Locale("en", "IN"))
            val date = try {
                formatter.parse(dateString) ?: Date()
            } catch (e: Exception) {
                Date()
            }

            val displayFormatter = SimpleDateFormat("dd MMM", Locale.getDefault())
            val displayString = displayFormatter.format(date)

            return ExpiryDate(id = dateString, date = date, displayString = displayString)
        }
    }
}

// MARK: - UI State

sealed class LoadingState {
    data object Idle : LoadingState()
    data object Loading : LoadingState()
    data object Loaded : LoadingState()
    data class Error(val message: String) : LoadingState()
}
