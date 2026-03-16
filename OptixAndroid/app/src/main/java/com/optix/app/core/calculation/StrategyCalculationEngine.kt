package com.optix.app.core.calculation

import com.optix.app.domain.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Strategy payoff calculation engine
 */
object StrategyCalculationEngine {

    /**
     * Calculate payoff for a strategy at a given spot price
     */
    fun calculatePayoff(strategy: Strategy, spotAtExpiry: Double): Double {
        return strategy.legs.sumOf { leg ->
            val intrinsic = when (leg.optionType) {
                OptionType.CALL -> max(0.0, spotAtExpiry - leg.strikePrice)
                OptionType.PUT -> max(0.0, leg.strikePrice - spotAtExpiry)
            }
            val multiplier = if (leg.position == LegPosition.BUY) 1 else -1
            val lotSize = leg.greeks.let { 25 } // Default lot size
            (intrinsic * leg.quantity * multiplier) - (leg.premium * leg.quantity * multiplier)
        }
    }

    /**
     * Calculate full payoff analysis for a strategy
     */
    fun analyzeStrategy(strategy: Strategy, lotSize: Int = 25): PayoffData {
        val spot = strategy.spotPrice
        val range = spot * 0.15 // ±15% range
        val minSpot = spot - range
        val maxSpot = spot + range
        val step = range / 50

        val points = mutableListOf<PayoffPoint>()
        var spotPrice = minSpot
        while (spotPrice <= maxSpot) {
            val payoff = calculatePayoffAtExpiry(strategy, spotPrice, lotSize)
            points.add(PayoffPoint(spotPrice, payoff))
            spotPrice += step
        }

        val payoffs = points.map { it.payoff }
        val maxProfit = payoffs.maxOrNull() ?: 0.0
        val maxLoss = payoffs.minOrNull() ?: 0.0

        val breakevens = findBreakevens(points)
        
        val riskRewardRatio = if (maxLoss != 0.0 && maxLoss > Double.NEGATIVE_INFINITY) {
            abs(maxProfit / maxLoss)
        } else {
            Double.POSITIVE_INFINITY
        }

        return PayoffData(
            points = points,
            maxProfit = maxProfit,
            maxLoss = maxLoss,
            breakevens = breakevens,
            currentSpot = spot,
            riskRewardRatio = riskRewardRatio
        )
    }

    /**
     * Calculate payoff at expiry
     */
    private fun calculatePayoffAtExpiry(
        strategy: Strategy,
        spotAtExpiry: Double,
        lotSize: Int
    ): Double {
        return strategy.legs.sumOf { leg ->
            val intrinsic = when (leg.optionType) {
                OptionType.CALL -> max(0.0, spotAtExpiry - leg.strikePrice)
                OptionType.PUT -> max(0.0, leg.strikePrice - spotAtExpiry)
            }
            val multiplier = if (leg.position == LegPosition.BUY) 1.0 else -1.0
            val legPremium = leg.premium * leg.quantity * lotSize
            val legPayoff = intrinsic * leg.quantity * lotSize

            (legPayoff * multiplier) - (legPremium * multiplier)
        }
    }

    /**
     * Find breakeven points from payoff data
     */
    private fun findBreakevens(points: List<PayoffPoint>): List<Double> {
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
     * Calculate margin requirement estimate
     */
    fun estimateMargin(strategy: Strategy, lotSize: Int = 25): Double {
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
     * Calculate probability of profit (simplified)
     */
    fun calculatePOP(payoffData: PayoffData): Double {
        if (payoffData.breakevens.isEmpty()) {
            // No breakeven - check if always profit or always loss
            return if (payoffData.maxLoss >= 0) 1.0 else 0.0
        }

        // Simplified POP based on breakeven distance from current spot
        val spot = payoffData.currentSpot
        val range = spot * 0.15
        
        var profitableRange = 0.0
        val points = payoffData.points
        
        for (point in points) {
            if (point.payoff > 0) {
                profitableRange += 1.0
            }
        }
        
        return profitableRange / points.size
    }
}
