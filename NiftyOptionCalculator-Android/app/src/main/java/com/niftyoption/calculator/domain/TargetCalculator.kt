package com.niftyoption.calculator.domain

import com.niftyoption.calculator.data.models.GreeksResult
import com.niftyoption.calculator.data.models.OptionData
import com.niftyoption.calculator.data.models.OptionType
import com.niftyoption.calculator.data.models.TargetCalculation
import kotlin.math.abs
import kotlin.math.max

/**
 * Target and Stop-Loss Calculator
 * Calculates expected option prices at different underlying levels
 */
object TargetCalculator {

    /**
     * Calculate target and stop-loss option prices
     */
    fun calculateTargetSL(
        option: OptionData,
        targetSpot: Double,
        stopLossSpot: Double,
        daysToTarget: Double = 0.0
    ): TargetCalculation {
        val currentSpot = option.underlyingValue
        val strikePrice = option.strikePrice
        val iv = option.impliedVolatility
        val timeToExpiry = option.timeToExpiryYears

        // Adjust time to expiry for theta calculation
        val adjustedTimeToExpiry = max(timeToExpiry - (daysToTarget / 365.0), 0.001)

        // Calculate current Greeks
        val currentGreeks = BlackScholesEngine.calculateGreeks(
            spotPrice = currentSpot,
            strikePrice = strikePrice,
            timeToExpiry = timeToExpiry,
            volatility = iv,
            optionType = option.optionType
        )

        // Calculate option price at target spot
        val targetOptionPrice = BlackScholesEngine.calculateOptionPrice(
            spotPrice = targetSpot,
            strikePrice = strikePrice,
            timeToExpiry = adjustedTimeToExpiry,
            volatility = iv,
            optionType = option.optionType
        )

        // Calculate option price at stop-loss spot
        val stopLossOptionPrice = BlackScholesEngine.calculateOptionPrice(
            spotPrice = stopLossSpot,
            strikePrice = strikePrice,
            timeToExpiry = adjustedTimeToExpiry,
            volatility = iv,
            optionType = option.optionType
        )

        return TargetCalculation(
            optionType = option.optionType,
            strikePrice = strikePrice,
            currentSpot = currentSpot,
            currentOptionPrice = option.lastTradedPrice,
            targetSpot = targetSpot,
            stopLossSpot = stopLossSpot,
            targetOptionPrice = targetOptionPrice,
            stopLossOptionPrice = stopLossOptionPrice,
            greeks = currentGreeks,
            impliedVolatility = iv,
            daysToExpiry = option.daysToExpiry
        )
    }

    /**
     * Calculate option price at a specific spot level
     */
    fun calculateOptionPrice(
        spotPrice: Double,
        strikePrice: Double,
        optionType: OptionType,
        timeToExpiry: Double,
        volatility: Double
    ): Double {
        return BlackScholesEngine.calculateOptionPrice(
            spotPrice = spotPrice,
            strikePrice = strikePrice,
            timeToExpiry = timeToExpiry,
            volatility = volatility,
            optionType = optionType
        )
    }

    /**
     * Calculate Greeks for any option
     */
    fun calculateGreeks(
        spotPrice: Double,
        strikePrice: Double,
        optionType: OptionType,
        timeToExpiry: Double,
        volatility: Double
    ): GreeksResult {
        return BlackScholesEngine.calculateGreeks(
            spotPrice = spotPrice,
            strikePrice = strikePrice,
            timeToExpiry = timeToExpiry,
            volatility = volatility,
            optionType = optionType
        )
    }

    /**
     * Quick estimate of option price change using Delta
     */
    fun quickEstimate(
        currentOptionPrice: Double,
        delta: Double,
        underlyingMove: Double
    ): Double {
        return currentOptionPrice + (delta * underlyingMove)
    }

    /**
     * More accurate estimate using Delta and Gamma
     */
    fun accurateEstimate(
        currentOptionPrice: Double,
        delta: Double,
        gamma: Double,
        theta: Double,
        underlyingMove: Double,
        daysElapsed: Double = 0.0
    ): Double {
        val deltaEffect = delta * underlyingMove
        val gammaEffect = 0.5 * gamma * underlyingMove * underlyingMove
        val thetaEffect = theta * daysElapsed

        return max(currentOptionPrice + deltaEffect + gammaEffect + thetaEffect, 0.0)
    }

    /**
     * Find the spot price at which option reaches target price using binary search
     */
    fun findSpotForTargetPrice(
        targetOptionPrice: Double,
        strikePrice: Double,
        optionType: OptionType,
        timeToExpiry: Double,
        volatility: Double,
        currentSpot: Double
    ): Double? {
        val isCall = optionType == OptionType.CALL

        val minSpot = max(strikePrice * 0.7, currentSpot * 0.9)
        val maxSpot = minOf(strikePrice * 1.3, currentSpot * 1.1)

        var low = if (isCall) currentSpot else minSpot
        var high = if (isCall) maxSpot else currentSpot

        repeat(50) {
            val mid = (low + high) / 2
            val price = calculateOptionPrice(mid, strikePrice, optionType, timeToExpiry, volatility)

            if (abs(price - targetOptionPrice) < 0.1) {
                return mid
            }

            if (isCall) {
                if (price > targetOptionPrice) high = mid else low = mid
            } else {
                if (price > targetOptionPrice) low = mid else high = mid
            }
        }

        return null
    }

    /**
     * Calculate P&L for a position at different spot levels
     */
    fun calculatePositionPnL(
        quantity: Int,
        entryPrice: Double,
        option: OptionData,
        spotLevels: List<Double>,
        lotSize: Int = 25
    ): List<Pair<Double, Double>> {
        val totalQty = quantity * lotSize

        return spotLevels.map { spot ->
            val optionPrice = calculateOptionPrice(
                spotPrice = spot,
                strikePrice = option.strikePrice,
                optionType = option.optionType,
                timeToExpiry = option.timeToExpiryYears,
                volatility = option.impliedVolatility
            )
            val pnl = (optionPrice - entryPrice) * totalQty
            spot to pnl
        }
    }

    /**
     * Suggest target and stop-loss based on risk-reward preferences
     */
    fun suggestTargetSL(
        option: OptionData,
        riskRewardRatio: Double = 2.0
    ): Pair<Double, Double>? {
        val currentSpot = option.underlyingValue
        val greeks = BlackScholesEngine.calculateGreeks(
            spotPrice = currentSpot,
            strikePrice = option.strikePrice,
            timeToExpiry = option.timeToExpiryYears,
            volatility = option.impliedVolatility,
            optionType = option.optionType
        )
        val delta = abs(greeks.delta)

        if (delta <= 0) return null

        val baseMove = option.lastTradedPrice / (delta * riskRewardRatio)
        val isCall = option.optionType == OptionType.CALL

        val targetSpot: Double
        val stopLossSpot: Double

        if (isCall) {
            targetSpot = currentSpot + (baseMove * riskRewardRatio)
            stopLossSpot = currentSpot - baseMove
        } else {
            targetSpot = currentSpot - (baseMove * riskRewardRatio)
            stopLossSpot = currentSpot + baseMove
        }

        return targetSpot to stopLossSpot
    }

    /**
     * Calculate option prices at different IV levels
     */
    fun calculateVolatilityImpact(
        option: OptionData,
        ivChanges: List<Double> = listOf(-0.05, -0.02, 0.0, 0.02, 0.05)
    ): List<Pair<Double, Double>> {
        val baseIV = option.impliedVolatility

        return ivChanges.map { change ->
            val newIV = max(baseIV + change, 0.01)
            val price = calculateOptionPrice(
                spotPrice = option.underlyingValue,
                strikePrice = option.strikePrice,
                optionType = option.optionType,
                timeToExpiry = option.timeToExpiryYears,
                volatility = newIV
            )
            newIV to price
        }
    }
}
