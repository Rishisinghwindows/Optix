package com.optix.app.core.calculation

import com.optix.app.domain.model.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Payoff calculation engine for option strategies
 * Supports single leg and multi-leg strategies
 */
object PayoffCalculator {

    /**
     * Default lot size for NIFTY/BANKNIFTY options
     */
    private const val DEFAULT_LOT_SIZE = 75

    /**
     * Calculate payoff at a specific spot price for a single leg
     */
    fun calculateLegPayoff(
        leg: StrategyLeg,
        spotAtExpiry: Double,
        lotSize: Int = DEFAULT_LOT_SIZE
    ): Double {
        val intrinsicValue = when (leg.optionType) {
            OptionType.CALL -> max(0.0, spotAtExpiry - leg.strikePrice)
            OptionType.PUT -> max(0.0, leg.strikePrice - spotAtExpiry)
        }

        val multiplier = if (leg.position == LegPosition.BUY) 1.0 else -1.0
        val legPremium = leg.premium * leg.quantity * lotSize
        val legPayoff = intrinsicValue * leg.quantity * lotSize

        return (legPayoff * multiplier) - (legPremium * multiplier)
    }

    /**
     * Calculate total payoff at a specific spot price for a strategy
     */
    fun calculateStrategyPayoff(
        strategy: Strategy,
        spotAtExpiry: Double,
        lotSize: Int = DEFAULT_LOT_SIZE
    ): Double {
        return strategy.legs.sumOf { leg ->
            calculateLegPayoff(leg, spotAtExpiry, lotSize)
        }
    }

    /**
     * Calculate payoff for a single option position
     */
    fun calculateSingleOptionPayoff(
        optionType: OptionType,
        strikePrice: Double,
        premium: Double,
        isLong: Boolean,
        spotAtExpiry: Double,
        quantity: Int = 1,
        lotSize: Int = DEFAULT_LOT_SIZE
    ): Double {
        val intrinsicValue = when (optionType) {
            OptionType.CALL -> max(0.0, spotAtExpiry - strikePrice)
            OptionType.PUT -> max(0.0, strikePrice - spotAtExpiry)
        }

        val multiplier = if (isLong) 1.0 else -1.0
        val totalPremium = premium * quantity * lotSize
        val payoff = intrinsicValue * quantity * lotSize

        return (payoff * multiplier) - (totalPremium * multiplier)
    }

    /**
     * Generate complete payoff analysis for a strategy
     */
    fun analyzeStrategy(
        strategy: Strategy,
        lotSize: Int = DEFAULT_LOT_SIZE,
        priceRangePercent: Double = 0.15,
        pointCount: Int = 100
    ): PayoffData {
        val spotPrice = strategy.spotPrice
        val range = spotPrice * priceRangePercent
        val minSpot = spotPrice - range
        val maxSpot = spotPrice + range
        val step = (maxSpot - minSpot) / pointCount

        val points = mutableListOf<PayoffPoint>()
        var currentSpot = minSpot
        while (currentSpot <= maxSpot) {
            val payoff = calculateStrategyPayoff(strategy, currentSpot, lotSize)
            points.add(PayoffPoint(currentSpot, payoff))
            currentSpot += step
        }

        // Add spot price point if not already included
        if (points.none { abs(it.spotPrice - spotPrice) < step / 2 }) {
            val spotPayoff = calculateStrategyPayoff(strategy, spotPrice, lotSize)
            points.add(PayoffPoint(spotPrice, spotPayoff))
            points.sortBy { it.spotPrice }
        }

        val payoffs = points.map { it.payoff }
        val maxProfit = calculateMaxProfit(strategy, points, lotSize)
        val maxLoss = calculateMaxLoss(strategy, points, lotSize)
        val breakevens = findBreakevens(points)

        val riskRewardRatio = if (maxLoss < 0 && maxLoss > Double.NEGATIVE_INFINITY) {
            abs(maxProfit / maxLoss)
        } else {
            Double.POSITIVE_INFINITY
        }

        return PayoffData(
            points = points,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = breakevens,
            currentSpot = spotPrice,
            riskRewardRatio = riskRewardRatio
        )
    }

    /**
     * Generate payoff data for a single option
     */
    fun analyzeSingleOption(
        optionType: OptionType,
        strikePrice: Double,
        premium: Double,
        isLong: Boolean,
        spotPrice: Double,
        quantity: Int = 1,
        lotSize: Int = DEFAULT_LOT_SIZE,
        priceRangePercent: Double = 0.15,
        pointCount: Int = 100
    ): PayoffData {
        val range = spotPrice * priceRangePercent
        val minSpot = spotPrice - range
        val maxSpot = spotPrice + range
        val step = (maxSpot - minSpot) / pointCount

        val points = mutableListOf<PayoffPoint>()
        var currentSpot = minSpot
        while (currentSpot <= maxSpot) {
            val payoff = calculateSingleOptionPayoff(
                optionType, strikePrice, premium, isLong, currentSpot, quantity, lotSize
            )
            points.add(PayoffPoint(currentSpot, payoff))
            currentSpot += step
        }

        val payoffs = points.map { it.payoff }
        val maxProfit = calculateSingleOptionMaxProfit(optionType, isLong, payoffs)
        val maxLoss = calculateSingleOptionMaxLoss(optionType, isLong, premium, quantity, lotSize, payoffs)
        val breakevens = findBreakevens(points)

        val riskRewardRatio = if (maxLoss < 0 && maxLoss > Double.NEGATIVE_INFINITY) {
            abs(maxProfit / maxLoss)
        } else {
            Double.POSITIVE_INFINITY
        }

        return PayoffData(
            points = points,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = breakevens,
            currentSpot = spotPrice,
            riskRewardRatio = riskRewardRatio
        )
    }

    /**
     * Calculate breakeven point for a single option
     */
    fun calculateSingleOptionBreakeven(
        optionType: OptionType,
        strikePrice: Double,
        premium: Double,
        isLong: Boolean
    ): Double {
        return when {
            optionType == OptionType.CALL && isLong -> strikePrice + premium
            optionType == OptionType.CALL && !isLong -> strikePrice + premium
            optionType == OptionType.PUT && isLong -> strikePrice - premium
            optionType == OptionType.PUT && !isLong -> strikePrice - premium
            else -> strikePrice
        }
    }

    /**
     * Find all breakeven points from payoff curve
     */
    fun findBreakevens(points: List<PayoffPoint>): List<Double> {
        val breakevens = mutableListOf<Double>()

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            // Check if payoff crosses zero
            if ((p1.payoff <= 0 && p2.payoff >= 0) || (p1.payoff >= 0 && p2.payoff <= 0)) {
                // Linear interpolation to find exact breakeven
                if (p2.payoff != p1.payoff) {
                    val breakeven = p1.spotPrice - p1.payoff * (p2.spotPrice - p1.spotPrice) / (p2.payoff - p1.payoff)
                    breakevens.add(breakeven)
                }
            }
        }

        return breakevens.distinct().sorted()
    }

    /**
     * Calculate max profit considering strategy characteristics
     */
    private fun calculateMaxProfit(
        strategy: Strategy,
        points: List<PayoffPoint>,
        lotSize: Int
    ): Double {
        val observedMax = points.maxOfOrNull { it.payoff } ?: 0.0

        // Check if strategy has unlimited profit potential
        val hasUnlimitedUpside = strategy.legs.any { leg ->
            leg.position == LegPosition.BUY && leg.optionType == OptionType.CALL
        }
        val hasUnlimitedDownside = strategy.legs.any { leg ->
            leg.position == LegPosition.BUY && leg.optionType == OptionType.PUT
        }

        // For strategies with uncovered long options, profit could be unlimited
        // But within observed range, return the maximum
        return observedMax
    }

    /**
     * Calculate max loss considering strategy characteristics
     */
    private fun calculateMaxLoss(
        strategy: Strategy,
        points: List<PayoffPoint>,
        lotSize: Int
    ): Double {
        val observedMin = points.minOfOrNull { it.payoff } ?: 0.0

        // Check if strategy has naked short options (unlimited risk)
        val hasNakedShortCall = strategy.legs.any { leg ->
            leg.position == LegPosition.SELL && leg.optionType == OptionType.CALL &&
                    !hasMatchingLongCall(strategy, leg)
        }
        val hasNakedShortPut = strategy.legs.any { leg ->
            leg.position == LegPosition.SELL && leg.optionType == OptionType.PUT &&
                    !hasMatchingLongPut(strategy, leg)
        }

        // For naked short options, risk is theoretically unlimited
        // But within observed range, return the minimum
        return observedMin
    }

    /**
     * Check if a short call has a matching long call (for spreads)
     */
    private fun hasMatchingLongCall(strategy: Strategy, shortCall: StrategyLeg): Boolean {
        return strategy.legs.any { leg ->
            leg.position == LegPosition.BUY &&
                    leg.optionType == OptionType.CALL &&
                    leg.quantity >= shortCall.quantity
        }
    }

    /**
     * Check if a short put has a matching long put (for spreads)
     */
    private fun hasMatchingLongPut(strategy: Strategy, shortPut: StrategyLeg): Boolean {
        return strategy.legs.any { leg ->
            leg.position == LegPosition.BUY &&
                    leg.optionType == OptionType.PUT &&
                    leg.quantity >= shortPut.quantity
        }
    }

    /**
     * Calculate max profit for single option
     */
    private fun calculateSingleOptionMaxProfit(
        optionType: OptionType,
        isLong: Boolean,
        payoffs: List<Double>
    ): Double {
        return when {
            // Long call has unlimited profit potential
            isLong && optionType == OptionType.CALL -> payoffs.maxOrNull() ?: 0.0
            // Long put has limited profit (strike - premium)
            isLong && optionType == OptionType.PUT -> payoffs.maxOrNull() ?: 0.0
            // Short positions have limited profit (premium received)
            else -> payoffs.maxOrNull() ?: 0.0
        }
    }

    /**
     * Calculate max loss for single option
     */
    private fun calculateSingleOptionMaxLoss(
        optionType: OptionType,
        isLong: Boolean,
        premium: Double,
        quantity: Int,
        lotSize: Int,
        payoffs: List<Double>
    ): Double {
        return when {
            // Long options have limited loss (premium paid)
            isLong -> -(premium * quantity * lotSize)
            // Short call has unlimited loss potential
            !isLong && optionType == OptionType.CALL -> payoffs.minOrNull() ?: 0.0
            // Short put has limited loss potential (strike - premium)
            !isLong && optionType == OptionType.PUT -> payoffs.minOrNull() ?: 0.0
            else -> payoffs.minOrNull() ?: 0.0
        }
    }

    /**
     * Calculate probability of profit (simplified estimation)
     */
    fun calculateProbabilityOfProfit(payoffData: PayoffData): Double {
        if (payoffData.breakevens.isEmpty()) {
            // No breakeven - check if always profit or always loss
            return if (payoffData.maxLoss >= 0) 1.0 else 0.0
        }

        val points = payoffData.points
        var profitableCount = 0

        for (point in points) {
            if (point.payoff > 0) {
                profitableCount++
            }
        }

        return profitableCount.toDouble() / points.size
    }

    /**
     * Get payoff at specific price point
     */
    fun getPayoffAtPrice(payoffData: PayoffData, price: Double): Double {
        // Find the closest points and interpolate
        val sortedPoints = payoffData.points.sortedBy { it.spotPrice }

        // Exact match
        sortedPoints.find { abs(it.spotPrice - price) < 0.01 }?.let { return it.payoff }

        // Find surrounding points for interpolation
        val lowerPoint = sortedPoints.lastOrNull { it.spotPrice <= price }
        val upperPoint = sortedPoints.firstOrNull { it.spotPrice >= price }

        return when {
            lowerPoint == null -> upperPoint?.payoff ?: 0.0
            upperPoint == null -> lowerPoint.payoff
            lowerPoint == upperPoint -> lowerPoint.payoff
            else -> {
                // Linear interpolation
                val ratio = (price - lowerPoint.spotPrice) / (upperPoint.spotPrice - lowerPoint.spotPrice)
                lowerPoint.payoff + ratio * (upperPoint.payoff - lowerPoint.payoff)
            }
        }
    }

    /**
     * Calculate margin requirement estimate for a strategy
     */
    fun estimateMargin(
        strategy: Strategy,
        lotSize: Int = DEFAULT_LOT_SIZE
    ): Double {
        var margin = 0.0

        for (leg in strategy.legs) {
            if (leg.position == LegPosition.SELL) {
                // Short options require margin
                val spanMargin = strategy.spotPrice * 0.15 * leg.quantity * lotSize
                val premiumMargin = leg.premium * leg.quantity * lotSize
                margin += max(spanMargin, premiumMargin)
            }
        }

        return margin
    }

    /**
     * Get strategy risk profile summary
     */
    fun getRiskProfile(payoffData: PayoffData): StrategyRiskProfile {
        val isLimitedRisk = payoffData.maxLoss > Double.NEGATIVE_INFINITY
        val isLimitedProfit = payoffData.maxProfit < Double.POSITIVE_INFINITY

        val riskLevel = when {
            isLimitedRisk && payoffData.riskRewardRatio > 2.0 -> RiskLevel.LOW
            isLimitedRisk && payoffData.riskRewardRatio > 1.0 -> RiskLevel.MODERATE
            isLimitedRisk -> RiskLevel.HIGH
            else -> RiskLevel.VERY_HIGH
        }

        return StrategyRiskProfile(
            isLimitedRisk = isLimitedRisk,
            isLimitedProfit = isLimitedProfit,
            riskLevel = riskLevel,
            riskRewardRatio = payoffData.riskRewardRatio,
            breakevens = payoffData.breakevens
        )
    }
}

/**
 * Risk level enumeration
 */
enum class RiskLevel {
    LOW,
    MODERATE,
    HIGH,
    VERY_HIGH
}

/**
 * Strategy risk profile summary
 */
data class StrategyRiskProfile(
    val isLimitedRisk: Boolean,
    val isLimitedProfit: Boolean,
    val riskLevel: RiskLevel,
    val riskRewardRatio: Double,
    val breakevens: List<Double>
)
