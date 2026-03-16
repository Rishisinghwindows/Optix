package com.optix.app.domain.model

/**
 * Option Greeks values
 */
data class Greeks(
    val delta: Double = 0.0,
    val gamma: Double = 0.0,
    val theta: Double = 0.0,
    val vega: Double = 0.0,
    val rho: Double = 0.0
) {
    companion object {
        val EMPTY = Greeks()
    }
}

/**
 * Complete Greeks calculation result with formatted display values
 */
data class GreeksResult(
    val optionPrice: Double,
    val greeks: Greeks,
    val intrinsicValue: Double = 0.0,
    val timeValue: Double = 0.0,
    val impliedVolatility: Double = 0.0
) {
    // Flat properties for direct access
    val delta: Double get() = greeks.delta
    val gamma: Double get() = greeks.gamma
    val theta: Double get() = greeks.theta
    val vega: Double get() = greeks.vega
    val rho: Double get() = greeks.rho

    val deltaDisplay: String get() = formatGreek(greeks.delta, 4)
    val gammaDisplay: String get() = formatGreek(greeks.gamma, 6)
    val thetaDisplay: String get() = formatGreek(greeks.theta, 4)
    val vegaDisplay: String get() = formatGreek(greeks.vega, 4)
    val rhoDisplay: String get() = formatGreek(greeks.rho, 4)
    val priceDisplay: String get() = "₹${String.format("%.2f", optionPrice)}"

    private fun formatGreek(value: Double, decimals: Int): String {
        return String.format("%.${decimals}f", value)
    }

    companion object {
        val EMPTY = GreeksResult(
            optionPrice = 0.0,
            greeks = Greeks.EMPTY
        )
    }
}
