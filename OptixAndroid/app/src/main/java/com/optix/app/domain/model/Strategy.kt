package com.optix.app.domain.model

/**
 * Strategy type with predefined configurations
 */
enum class StrategyType(
    val displayName: String,
    val category: StrategyCategory,
    val description: String,
    val legCount: Int = 1
) {
    // Single Leg
    LONG_CALL("Long Call", StrategyCategory.SINGLE_LEG, "Buy a call option - bullish outlook", 1),
    SHORT_CALL("Short Call", StrategyCategory.SINGLE_LEG, "Sell a call option - bearish/neutral outlook", 1),
    LONG_PUT("Long Put", StrategyCategory.SINGLE_LEG, "Buy a put option - bearish outlook", 1),
    SHORT_PUT("Short Put", StrategyCategory.SINGLE_LEG, "Sell a put option - bullish/neutral outlook", 1),

    // Covered Strategies
    COVERED_CALL("Covered Call", StrategyCategory.COVERED, "Own stock + sell call - income generation", 2),
    PROTECTIVE_PUT("Protective Put", StrategyCategory.COVERED, "Own stock + buy put - downside protection", 2),

    // Vertical Spreads
    BULL_CALL_SPREAD("Bull Call Spread", StrategyCategory.VERTICAL_SPREAD, "Buy lower strike call, sell higher strike call", 2),
    BEAR_CALL_SPREAD("Bear Call Spread", StrategyCategory.VERTICAL_SPREAD, "Sell lower strike call, buy higher strike call", 2),
    BULL_PUT_SPREAD("Bull Put Spread", StrategyCategory.VERTICAL_SPREAD, "Sell higher strike put, buy lower strike put", 2),
    BEAR_PUT_SPREAD("Bear Put Spread", StrategyCategory.VERTICAL_SPREAD, "Buy higher strike put, sell lower strike put", 2),

    // Straddles & Strangles
    LONG_STRADDLE("Long Straddle", StrategyCategory.STRADDLE_STRANGLE, "Buy ATM call and put - expect high volatility", 2),
    SHORT_STRADDLE("Short Straddle", StrategyCategory.STRADDLE_STRANGLE, "Sell ATM call and put - expect low volatility", 2),
    LONG_STRANGLE("Long Strangle", StrategyCategory.STRADDLE_STRANGLE, "Buy OTM call and put - expect high volatility", 2),
    SHORT_STRANGLE("Short Strangle", StrategyCategory.STRADDLE_STRANGLE, "Sell OTM call and put - expect low volatility", 2),

    // Multi-Leg
    IRON_CONDOR("Iron Condor", StrategyCategory.MULTI_LEG, "Sell OTM call spread and put spread - range-bound", 4),
    IRON_BUTTERFLY("Iron Butterfly", StrategyCategory.MULTI_LEG, "Sell ATM straddle, buy OTM strangle - range-bound", 4),
    LONG_BUTTERFLY("Long Butterfly", StrategyCategory.MULTI_LEG, "Buy 1 lower, sell 2 middle, buy 1 higher call", 3),
    SHORT_BUTTERFLY("Short Butterfly", StrategyCategory.MULTI_LEG, "Sell 1 lower, buy 2 middle, sell 1 higher call", 3),
    JADE_LIZARD("Jade Lizard", StrategyCategory.MULTI_LEG, "Short put + short call spread - bullish with premium", 3),

    // Calendar & Diagonal
    CALENDAR_SPREAD("Calendar Spread", StrategyCategory.CALENDAR, "Same strike, different expiries - time decay play", 2),
    DIAGONAL_SPREAD("Diagonal Spread", StrategyCategory.CALENDAR, "Different strike and expiries - directional time play", 2),

    // Ratio Spreads
    RATIO_SPREAD("Ratio Spread", StrategyCategory.RATIO, "Unequal number of long/short options", 2),

    // Custom
    CUSTOM("Custom", StrategyCategory.CUSTOM, "Build your own strategy", 0)
}

/**
 * Strategy category for grouping
 */
enum class StrategyCategory(val displayName: String) {
    SINGLE_LEG("Single Leg"),
    COVERED("Covered Strategies"),
    VERTICAL_SPREAD("Vertical Spreads"),
    STRADDLE_STRANGLE("Straddles & Strangles"),
    MULTI_LEG("Multi-Leg"),
    CALENDAR("Calendar & Diagonal"),
    RATIO("Ratio Spreads"),
    CUSTOM("Custom")
}

/**
 * Position in a strategy leg
 */
enum class LegPosition {
    BUY, SELL
}

/**
 * Single leg in a strategy
 */
data class StrategyLeg(
    val id: String,
    val optionType: OptionType,
    val strikePrice: Double,
    val position: LegPosition,
    val quantity: Int = 1,
    val premium: Double = 0.0,
    val greeks: Greeks = Greeks.EMPTY
) {
    val isLong: Boolean get() = position == LegPosition.BUY
    val isShort: Boolean get() = position == LegPosition.SELL

    val netPremium: Double
        get() = if (isLong) -premium * quantity else premium * quantity

    val positionText: String
        get() = "${if (isLong) "Buy" else "Sell"} $quantity ${optionType.displayName} @ $strikePrice"
}

/**
 * Complete strategy with all legs
 */
data class Strategy(
    val id: String,
    val type: StrategyType,
    val symbol: String,
    val expiry: String,
    val legs: List<StrategyLeg>,
    val spotPrice: Double,
    val createdAt: Long = System.currentTimeMillis()
) {
    val netPremium: Double
        get() = legs.sumOf { it.netPremium }

    val isDebit: Boolean get() = netPremium < 0
    val isCredit: Boolean get() = netPremium > 0

    val netDelta: Double get() = legs.sumOf {
        val multiplier = if (it.isLong) 1.0 else -1.0
        it.greeks.delta * it.quantity * multiplier
    }

    val netGamma: Double get() = legs.sumOf {
        val multiplier = if (it.isLong) 1.0 else -1.0
        it.greeks.gamma * it.quantity * multiplier
    }

    val netTheta: Double get() = legs.sumOf {
        val multiplier = if (it.isLong) 1.0 else -1.0
        it.greeks.theta * it.quantity * multiplier
    }

    val netVega: Double get() = legs.sumOf {
        val multiplier = if (it.isLong) 1.0 else -1.0
        it.greeks.vega * it.quantity * multiplier
    }
}

/**
 * Payoff point for charting
 */
data class PayoffPoint(
    val spotPrice: Double,
    val payoff: Double
)

/**
 * Complete payoff analysis
 */
data class PayoffData(
    val points: List<PayoffPoint>,
    val maxProfit: Double,
    val maxLoss: Double,
    val breakevens: List<Double>,
    val currentSpot: Double,
    val riskRewardRatio: Double
) {
    val isLimitedRisk: Boolean get() = maxLoss > Double.NEGATIVE_INFINITY
    val isLimitedProfit: Boolean get() = maxProfit < Double.POSITIVE_INFINITY
}
