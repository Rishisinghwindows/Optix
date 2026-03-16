package com.optix.app.core.calculation

import com.optix.app.core.constants.AppConstants
import com.optix.app.domain.model.Greeks
import com.optix.app.domain.model.GreeksResult
import com.optix.app.domain.model.OptionType
import kotlin.math.*

/**
 * Black-Scholes option pricing engine
 */
object BlackScholesEngine {

    /**
     * Calculate just the option price
     */
    fun calculateOptionPrice(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double,
        volatility: Double,
        isCall: Boolean
    ): Double {
        val optionType = if (isCall) OptionType.CALL else OptionType.PUT
        return calculate(spotPrice, strikePrice, timeToExpiry, volatility, riskFreeRate, optionType).optionPrice
    }

    /**
     * Calculate Greeks and return GreeksResult
     */
    fun calculateGreeks(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double,
        volatility: Double,
        isCall: Boolean
    ): GreeksResult {
        val optionType = if (isCall) OptionType.CALL else OptionType.PUT
        return calculate(spotPrice, strikePrice, timeToExpiry, volatility, riskFreeRate, optionType)
    }

    /**
     * Calculate option price and Greeks
     *
     * @param spotPrice Current price of underlying
     * @param strikePrice Strike price of option
     * @param timeToExpiry Time to expiry in years
     * @param volatility Implied volatility (annualized, e.g., 0.20 for 20%)
     * @param riskFreeRate Risk-free interest rate (default 7% for India)
     * @param optionType Call or Put
     */
    fun calculate(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        volatility: Double,
        riskFreeRate: Double = AppConstants.DEFAULT_RISK_FREE_RATE,
        optionType: OptionType
    ): GreeksResult {
        // Handle edge cases
        if (timeToExpiry <= 0 || spotPrice <= 0 || strikePrice <= 0 || volatility <= 0) {
            return calculateIntrinsicValue(spotPrice, strikePrice, optionType)
        }

        val sqrtT = sqrt(timeToExpiry)
        val d1 = calculateD1(spotPrice, strikePrice, riskFreeRate, volatility, timeToExpiry)
        val d2 = d1 - volatility * sqrtT

        val nd1 = normalCDF(d1)
        val nd2 = normalCDF(d2)
        val nNegD1 = normalCDF(-d1)
        val nNegD2 = normalCDF(-d2)
        val npd1 = normalPDF(d1)

        val discountFactor = exp(-riskFreeRate * timeToExpiry)

        val optionPrice: Double
        val delta: Double
        val gamma: Double
        val theta: Double
        val vega: Double
        val rho: Double

        when (optionType) {
            OptionType.CALL -> {
                optionPrice = spotPrice * nd1 - strikePrice * discountFactor * nd2
                delta = nd1
                gamma = npd1 / (spotPrice * volatility * sqrtT)
                theta = calculateCallTheta(
                    spotPrice, strikePrice, riskFreeRate, volatility,
                    timeToExpiry, sqrtT, npd1, nd2, discountFactor
                )
                vega = spotPrice * npd1 * sqrtT / 100 // Per 1% change in IV
                rho = strikePrice * timeToExpiry * discountFactor * nd2 / 100
            }
            OptionType.PUT -> {
                optionPrice = strikePrice * discountFactor * nNegD2 - spotPrice * nNegD1
                delta = nd1 - 1
                gamma = npd1 / (spotPrice * volatility * sqrtT)
                theta = calculatePutTheta(
                    spotPrice, strikePrice, riskFreeRate, volatility,
                    timeToExpiry, sqrtT, npd1, nNegD2, discountFactor
                )
                vega = spotPrice * npd1 * sqrtT / 100
                rho = -strikePrice * timeToExpiry * discountFactor * nNegD2 / 100
            }
        }

        val intrinsicValue = maxOf(
            0.0,
            when (optionType) {
                OptionType.CALL -> spotPrice - strikePrice
                OptionType.PUT -> strikePrice - spotPrice
            }
        )

        return GreeksResult(
            optionPrice = maxOf(0.0, optionPrice),
            greeks = Greeks(
                delta = delta,
                gamma = gamma,
                theta = theta / 365, // Convert to daily theta
                vega = vega,
                rho = rho
            ),
            intrinsicValue = intrinsicValue,
            timeValue = maxOf(0.0, optionPrice - intrinsicValue)
        )
    }

    /**
     * Calculate implied volatility using Newton-Raphson method
     */
    fun calculateIV(
        optionPrice: Double,
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = AppConstants.DEFAULT_RISK_FREE_RATE,
        optionType: OptionType,
        maxIterations: Int = 100,
        tolerance: Double = 0.0001
    ): Double {
        if (optionPrice <= 0 || timeToExpiry <= 0) return 0.0

        var iv = 0.20 // Initial guess: 20%
        var iteration = 0

        while (iteration < maxIterations) {
            val result = calculate(spotPrice, strikePrice, timeToExpiry, iv, riskFreeRate, optionType)
            val priceDiff = result.optionPrice - optionPrice

            if (abs(priceDiff) < tolerance) {
                return iv
            }

            // Newton-Raphson step
            val vega = result.greeks.vega * 100 // Convert back from per 1%
            if (vega < 0.0001) break

            iv -= priceDiff / vega
            iv = maxOf(0.01, minOf(5.0, iv)) // Clamp between 1% and 500%

            iteration++
        }

        return iv
    }

    /**
     * Calculate time to expiry in years
     */
    fun calculateTimeToExpiry(
        expiryDate: java.time.LocalDate,
        currentTime: java.time.LocalDateTime = java.time.LocalDateTime.now()
    ): Double {
        val expiryDateTime = expiryDate.atTime(
            AppConstants.MARKET_CLOSE_HOUR,
            AppConstants.MARKET_CLOSE_MINUTE
        )

        val minutesRemaining = java.time.temporal.ChronoUnit.MINUTES.between(currentTime, expiryDateTime)
        if (minutesRemaining <= 0) return 0.0

        // Convert to trading years
        val tradingMinutesPerDay = (AppConstants.MARKET_CLOSE_HOUR - AppConstants.MARKET_OPEN_HOUR) * 60 +
                (AppConstants.MARKET_CLOSE_MINUTE - AppConstants.MARKET_OPEN_MINUTE)
        val tradingMinutesPerYear = tradingMinutesPerDay * AppConstants.TRADING_DAYS_PER_YEAR

        return minutesRemaining.toDouble() / tradingMinutesPerYear
    }

    // Private helper functions
    private fun calculateD1(
        s: Double, k: Double, r: Double, v: Double, t: Double
    ): Double {
        return (ln(s / k) + (r + v * v / 2) * t) / (v * sqrt(t))
    }

    private fun calculateCallTheta(
        s: Double, k: Double, r: Double, v: Double, t: Double,
        sqrtT: Double, npd1: Double, nd2: Double, discountFactor: Double
    ): Double {
        val term1 = -s * npd1 * v / (2 * sqrtT)
        val term2 = -r * k * discountFactor * nd2
        return term1 + term2
    }

    private fun calculatePutTheta(
        s: Double, k: Double, r: Double, v: Double, t: Double,
        sqrtT: Double, npd1: Double, nNegD2: Double, discountFactor: Double
    ): Double {
        val term1 = -s * npd1 * v / (2 * sqrtT)
        val term2 = r * k * discountFactor * nNegD2
        return term1 + term2
    }

    private fun calculateIntrinsicValue(
        spotPrice: Double,
        strikePrice: Double,
        optionType: OptionType
    ): GreeksResult {
        val intrinsic = maxOf(
            0.0,
            when (optionType) {
                OptionType.CALL -> spotPrice - strikePrice
                OptionType.PUT -> strikePrice - spotPrice
            }
        )
        return GreeksResult(
            optionPrice = intrinsic,
            greeks = Greeks(
                delta = if (intrinsic > 0) (if (optionType == OptionType.CALL) 1.0 else -1.0) else 0.0
            ),
            intrinsicValue = intrinsic,
            timeValue = 0.0
        )
    }

    /**
     * Standard normal cumulative distribution function
     */
    private fun normalCDF(x: Double): Double {
        val a1 = 0.254829592
        val a2 = -0.284496736
        val a3 = 1.421413741
        val a4 = -1.453152027
        val a5 = 1.061405429
        val p = 0.3275911

        val sign = if (x < 0) -1 else 1
        val absX = abs(x)

        val t = 1.0 / (1.0 + p * absX)
        val y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-absX * absX / 2)

        return (1.0 + sign * y) / 2.0
    }

    /**
     * Standard normal probability density function
     */
    private fun normalPDF(x: Double): Double {
        return exp(-x * x / 2) / sqrt(2 * PI)
    }
}
