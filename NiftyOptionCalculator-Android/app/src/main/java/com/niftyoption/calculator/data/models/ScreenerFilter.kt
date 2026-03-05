package com.niftyoption.calculator.data.models

data class ScreenerFilter(
    val optionType: ScreenerOptionType = ScreenerOptionType.BOTH,
    val moneyness: MoneynessFilter = MoneynessFilter.ALL,
    val deltaMin: Double = 0.0,
    val deltaMax: Double = 1.0,
    val ivMin: Double = 0.0,       // percentage (0-200)
    val ivMax: Double = 200.0,
    val oiMin: Long = 0,
    val volumeMin: Long = 0,
    val premiumMin: Double = 0.0,
    val premiumMax: Double = 99999.0,
    val sortBy: ScreenerSortBy = ScreenerSortBy.OI,
    val sortAscending: Boolean = false
)

enum class ScreenerOptionType(val displayName: String) {
    CALL("CE"), PUT("PE"), BOTH("Both")
}

enum class MoneynessFilter(val displayName: String) {
    ALL("All"), ATM("ATM"), ITM("ITM"), OTM("OTM")
}

enum class ScreenerSortBy(val displayName: String) {
    DELTA("Delta"), IV("IV"), OI("OI"), VOLUME("Volume"), PREMIUM("Premium")
}

data class ScreenerResult(
    val strikePrice: Double,
    val optionType: OptionType,
    val ltp: Double,
    val iv: Double,         // decimal (0.15 for 15%)
    val delta: Double,
    val gamma: Double,
    val theta: Double,
    val vega: Double,
    val oi: Int,
    val oiChange: Int,
    val volume: Int,
    val underlyingValue: Double
) {
    val displayStrike: String get() = "%.0f".format(strikePrice)
    val displayLTP: String get() = "%.2f".format(ltp)
    val displayIV: String get() = "%.1f%%".format(iv * 100)
    val displayDelta: String get() = "%.4f".format(delta)
    val displayOI: String get() = when {
        oi >= 100000 -> "%.1fL".format(oi / 100000.0)
        oi >= 1000 -> "%.1fK".format(oi / 1000.0)
        else -> oi.toString()
    }
    val displayVolume: String get() = when {
        volume >= 100000 -> "%.1fL".format(volume / 100000.0)
        volume >= 1000 -> "%.1fK".format(volume / 1000.0)
        else -> volume.toString()
    }
}
