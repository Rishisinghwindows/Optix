package com.optix.app.domain.model

/**
 * Option type - Call or Put
 */
enum class OptionType(val code: String, val displayName: String) {
    CALL("CE", "Call"),
    PUT("PE", "Put")
}

/**
 * Complete option contract data
 */
data class OptionData(
    val strikePrice: Double,
    val optionType: OptionType,
    val expiry: String,
    val lastPrice: Double,
    val change: Double = 0.0,
    val changePercent: Double = 0.0,
    val openInterest: Long = 0,
    val oiChange: Long = 0,
    val oiChangePercent: Double = 0.0,
    val volume: Long = 0,
    val impliedVolatility: Double = 0.0,
    val bidPrice: Double = 0.0,
    val bidQty: Long = 0,
    val askPrice: Double = 0.0,
    val askQty: Long = 0,
    val delta: Double = 0.0,
    val gamma: Double = 0.0,
    val theta: Double = 0.0,
    val vega: Double = 0.0,
    val instrumentKey: String = "",
    val underlyingSpot: Double = 0.0
) {
    val isITM: Boolean
        get() = when (optionType) {
            OptionType.CALL -> underlyingSpot > strikePrice
            OptionType.PUT -> underlyingSpot < strikePrice
        }

    val isATM: Boolean
        get() = kotlin.math.abs(underlyingSpot - strikePrice) < (strikePrice * 0.005)

    val isOTM: Boolean
        get() = !isITM && !isATM

    val moneyness: String
        get() = when {
            isATM -> "ATM"
            isITM -> "ITM"
            else -> "OTM"
        }
}

/**
 * Single row in option chain (one strike with both Call and Put)
 */
data class OptionChainRow(
    val strikePrice: Double,
    val callData: OptionData?,
    val putData: OptionData?,
    val isATM: Boolean = false
)

/**
 * Complete option chain data
 */
data class OptionChain(
    val index: TradingIndex,
    val spotPrice: Double,
    val expiry: String,
    val rows: List<OptionChainRow>,
    val atmStrike: Double,
    val priceChange: Double? = null,
    val priceChangePercent: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val fullChainCallOI: Long? = null,
    val fullChainPutOI: Long? = null
)

/**
 * Expiry date with formatting
 */
data class ExpiryDate(
    val value: String,
    val displayDate: String,
    val daysToExpiry: Int,
    val isWeekly: Boolean = false,
    val isMonthly: Boolean = false
) {
    // Computed properties for backwards compatibility
    val displayString: String get() = displayDate
    val shortString: String get() = displayDate
}
