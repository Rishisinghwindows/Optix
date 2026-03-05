package com.niftyoption.calculator.domain

import com.niftyoption.calculator.data.models.GreeksResult
import com.niftyoption.calculator.data.models.OptionType
import java.util.Date
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Black-Scholes Option Pricing Engine
 * Calculates option prices and Greeks for European-style options
 */
object BlackScholesEngine {

    // Default risk-free rate for India (RBI repo rate approximation)
    const val DEFAULT_RISK_FREE_RATE = 0.07

    // MARK: - Core Calculations

    /**
     * Calculate d1 component of Black-Scholes formula
     */
    private fun calculateD1(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double,
        volatility: Double
    ): Double {
        if (timeToExpiry <= 0 || volatility <= 0 || strikePrice <= 0 || spotPrice <= 0) {
            return 0.0
        }

        val sqrtT = sqrt(timeToExpiry)
        val numerator = ln(spotPrice / strikePrice) + (riskFreeRate + (volatility * volatility) / 2) * timeToExpiry
        val denominator = volatility * sqrtT

        return numerator / denominator
    }

    /**
     * Calculate d2 component of Black-Scholes formula
     * d2 = d1 - σ√T
     */
    private fun calculateD2(d1: Double, volatility: Double, timeToExpiry: Double): Double {
        return d1 - volatility * sqrt(timeToExpiry)
    }

    /**
     * Standard normal cumulative distribution function (CDF)
     * Uses approximation for N(x)
     */
    private fun normalCDF(x: Double): Double {
        return 0.5 * erfc(-x / sqrt(2.0))
    }

    /**
     * Complementary error function approximation
     */
    private fun erfc(x: Double): Double {
        val t = 1.0 / (1.0 + 0.5 * abs(x))
        val tau = t * exp(
            -x * x - 1.26551223 +
                    t * (1.00002368 +
                    t * (0.37409196 +
                    t * (0.09678418 +
                    t * (-0.18628806 +
                    t * (0.27886807 +
                    t * (-1.13520398 +
                    t * (1.48851587 +
                    t * (-0.82215223 +
                    t * 0.17087277))))))))
        )
        return if (x >= 0) tau else 2.0 - tau
    }

    /**
     * Standard normal probability density function (PDF)
     * N'(x) = (1/√2π) * e^(-x²/2)
     */
    private fun normalPDF(x: Double): Double {
        return exp(-0.5 * x * x) / sqrt(2 * Math.PI)
    }

    // MARK: - Option Pricing

    /**
     * Calculate Call option price using Black-Scholes formula
     * Call = S·N(d1) - K·e^(-rT)·N(d2)
     */
    fun calculateCallPrice(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = DEFAULT_RISK_FREE_RATE,
        volatility: Double
    ): Double {
        if (timeToExpiry <= 0) {
            // At expiry, call value is max(S - K, 0)
            return max(spotPrice - strikePrice, 0.0)
        }

        val d1 = calculateD1(spotPrice, strikePrice, timeToExpiry, riskFreeRate, volatility)
        val d2 = calculateD2(d1, volatility, timeToExpiry)

        val discountFactor = exp(-riskFreeRate * timeToExpiry)
        val callPrice = spotPrice * normalCDF(d1) - strikePrice * discountFactor * normalCDF(d2)

        return max(callPrice, 0.0)
    }

    /**
     * Calculate Put option price using Black-Scholes formula
     * Put = K·e^(-rT)·N(-d2) - S·N(-d1)
     */
    fun calculatePutPrice(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = DEFAULT_RISK_FREE_RATE,
        volatility: Double
    ): Double {
        if (timeToExpiry <= 0) {
            // At expiry, put value is max(K - S, 0)
            return max(strikePrice - spotPrice, 0.0)
        }

        val d1 = calculateD1(spotPrice, strikePrice, timeToExpiry, riskFreeRate, volatility)
        val d2 = calculateD2(d1, volatility, timeToExpiry)

        val discountFactor = exp(-riskFreeRate * timeToExpiry)
        val putPrice = strikePrice * discountFactor * normalCDF(-d2) - spotPrice * normalCDF(-d1)

        return max(putPrice, 0.0)
    }

    /**
     * Calculate option price based on type
     */
    fun calculateOptionPrice(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = DEFAULT_RISK_FREE_RATE,
        volatility: Double,
        optionType: OptionType
    ): Double {
        return when (optionType) {
            OptionType.CALL -> calculateCallPrice(spotPrice, strikePrice, timeToExpiry, riskFreeRate, volatility)
            OptionType.PUT -> calculatePutPrice(spotPrice, strikePrice, timeToExpiry, riskFreeRate, volatility)
        }
    }

    // MARK: - Greeks Calculations

    /**
     * Calculate all Greeks for a Call option
     */
    fun calculateCallGreeks(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = DEFAULT_RISK_FREE_RATE,
        volatility: Double
    ): GreeksResult {
        if (timeToExpiry <= 0 || volatility <= 0) {
            return GreeksResult(
                delta = if (spotPrice > strikePrice) 1.0 else 0.0,
                gamma = 0.0,
                theta = 0.0,
                vega = 0.0,
                rho = 0.0
            )
        }

        val d1 = calculateD1(spotPrice, strikePrice, timeToExpiry, riskFreeRate, volatility)
        val d2 = calculateD2(d1, volatility, timeToExpiry)
        val sqrtT = sqrt(timeToExpiry)
        val discountFactor = exp(-riskFreeRate * timeToExpiry)

        // Delta: N(d1)
        val delta = normalCDF(d1)

        // Gamma: N'(d1) / (S·σ·√T)
        val gamma = normalPDF(d1) / (spotPrice * volatility * sqrtT)

        // Theta: -[S·N'(d1)·σ/(2√T)] - r·K·e^(-rT)·N(d2)
        // Expressed per day (divide by 365)
        val thetaAnnual = -(spotPrice * normalPDF(d1) * volatility / (2 * sqrtT)) -
                riskFreeRate * strikePrice * discountFactor * normalCDF(d2)
        val theta = thetaAnnual / 365.0

        // Vega: S·√T·N'(d1) / 100 (per 1% change in volatility)
        val vega = spotPrice * sqrtT * normalPDF(d1) / 100.0

        // Rho: K·T·e^(-rT)·N(d2) / 100 (per 1% change in rate)
        val rho = strikePrice * timeToExpiry * discountFactor * normalCDF(d2) / 100.0

        return GreeksResult(delta = delta, gamma = gamma, theta = theta, vega = vega, rho = rho)
    }

    /**
     * Calculate all Greeks for a Put option
     */
    fun calculatePutGreeks(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = DEFAULT_RISK_FREE_RATE,
        volatility: Double
    ): GreeksResult {
        if (timeToExpiry <= 0 || volatility <= 0) {
            return GreeksResult(
                delta = if (spotPrice < strikePrice) -1.0 else 0.0,
                gamma = 0.0,
                theta = 0.0,
                vega = 0.0,
                rho = 0.0
            )
        }

        val d1 = calculateD1(spotPrice, strikePrice, timeToExpiry, riskFreeRate, volatility)
        val d2 = calculateD2(d1, volatility, timeToExpiry)
        val sqrtT = sqrt(timeToExpiry)
        val discountFactor = exp(-riskFreeRate * timeToExpiry)

        // Delta: N(d1) - 1
        val delta = normalCDF(d1) - 1

        // Gamma: Same as call - N'(d1) / (S·σ·√T)
        val gamma = normalPDF(d1) / (spotPrice * volatility * sqrtT)

        // Theta: -[S·N'(d1)·σ/(2√T)] + r·K·e^(-rT)·N(-d2)
        val thetaAnnual = -(spotPrice * normalPDF(d1) * volatility / (2 * sqrtT)) +
                riskFreeRate * strikePrice * discountFactor * normalCDF(-d2)
        val theta = thetaAnnual / 365.0

        // Vega: Same as call - S·√T·N'(d1) / 100
        val vega = spotPrice * sqrtT * normalPDF(d1) / 100.0

        // Rho: -K·T·e^(-rT)·N(-d2) / 100
        val rho = -strikePrice * timeToExpiry * discountFactor * normalCDF(-d2) / 100.0

        return GreeksResult(delta = delta, gamma = gamma, theta = theta, vega = vega, rho = rho)
    }

    /**
     * Calculate Greeks based on option type
     */
    fun calculateGreeks(
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = DEFAULT_RISK_FREE_RATE,
        volatility: Double,
        optionType: OptionType
    ): GreeksResult {
        return when (optionType) {
            OptionType.CALL -> calculateCallGreeks(spotPrice, strikePrice, timeToExpiry, riskFreeRate, volatility)
            OptionType.PUT -> calculatePutGreeks(spotPrice, strikePrice, timeToExpiry, riskFreeRate, volatility)
        }
    }

    // MARK: - Implied Volatility

    /**
     * Calculate implied volatility using Newton-Raphson method
     */
    fun calculateImpliedVolatility(
        optionPrice: Double,
        spotPrice: Double,
        strikePrice: Double,
        timeToExpiry: Double,
        riskFreeRate: Double = DEFAULT_RISK_FREE_RATE,
        isCall: Boolean,
        maxIterations: Int = 100,
        tolerance: Double = 0.0001
    ): Double? {
        if (optionPrice <= 0 || timeToExpiry <= 0) return null

        // Initial guess using Brenner-Subrahmanyam approximation
        var sigma = sqrt(2 * Math.PI / timeToExpiry) * optionPrice / spotPrice
        sigma = max(0.01, min(sigma, 5.0))

        for (i in 0 until maxIterations) {
            val price = if (isCall) {
                calculateCallPrice(spotPrice, strikePrice, timeToExpiry, riskFreeRate, sigma)
            } else {
                calculatePutPrice(spotPrice, strikePrice, timeToExpiry, riskFreeRate, sigma)
            }

            val diff = price - optionPrice

            if (abs(diff) < tolerance) {
                return sigma
            }

            // Calculate vega for Newton-Raphson
            val d1 = calculateD1(spotPrice, strikePrice, timeToExpiry, riskFreeRate, sigma)
            val vega = spotPrice * sqrt(timeToExpiry) * normalPDF(d1)

            sigma = if (vega < 0.0001) {
                if (diff > 0) sigma * 0.9 else sigma * 1.1
            } else {
                sigma - diff / vega
            }

            sigma = max(0.01, min(sigma, 5.0))
        }

        return sigma
    }

    // MARK: - Utility Methods

    /**
     * Convert days to years for time to expiry calculation
     */
    fun daysToYears(days: Int): Double {
        return days.toDouble() / 365.0
    }

    /**
     * Calculate time to expiry from expiry date
     */
    fun timeToExpiry(expiryDate: Date, currentDate: Date = Date()): Double {
        val diffInMillis = expiryDate.time - currentDate.time
        val days = diffInMillis / (24 * 60 * 60 * 1000).toDouble()
        return max(days / 365.0, 0.0)
    }
}
