package com.optix.app.presentation.screens.aiinsights.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.StrategyRecommendation
import com.optix.app.domain.model.TradeDirection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Mini payoff chart preview for strategy recommendations
 */
@Composable
fun MiniPayoffChart(
    strategy: StrategyRecommendation,
    modifier: Modifier = Modifier,
    profitColor: Color = Color(0xFF22C55E),
    lossColor: Color = Color(0xFFEF4444),
    neutralColor: Color = Color(0xFF6B7280)
) {
    val payoffData = remember(strategy) {
        calculatePayoffPoints(strategy)
    }

    Canvas(
        modifier = modifier
            .width(120.dp)
            .height(60.dp)
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2

        if (payoffData.isEmpty()) return@Canvas

        val minPnL = payoffData.minOf { it.second }
        val maxPnL = payoffData.maxOf { it.second }
        val pnlRange = max(abs(minPnL), abs(maxPnL)).coerceAtLeast(1.0)

        // Draw zero line
        drawLine(
            color = neutralColor.copy(alpha = 0.3f),
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
        )

        // Draw payoff curve
        val path = Path()
        var isFirstPoint = true

        payoffData.forEachIndexed { index, (_, pnl) ->
            val x = (index.toFloat() / (payoffData.size - 1)) * width
            val normalizedPnL = (pnl / pnlRange).toFloat()
            val y = midY - (normalizedPnL * (height / 2 - 4.dp.toPx()))

            if (isFirstPoint) {
                path.moveTo(x, y)
                isFirstPoint = false
            } else {
                path.lineTo(x, y)
            }
        }

        // Draw the path
        drawPath(
            path = path,
            color = if (strategy.maxProfit > strategy.maxLoss) profitColor else lossColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw breakeven points
        strategy.breakevens.forEach { breakeven ->
            val priceRange = payoffData.last().first - payoffData.first().first
            if (priceRange > 0) {
                val normalizedBreakeven = ((breakeven - payoffData.first().first) / priceRange).toFloat()
                val beX = normalizedBreakeven * width
                if (beX in 0f..width) {
                    drawCircle(
                        color = neutralColor,
                        radius = 3.dp.toPx(),
                        center = Offset(beX, midY)
                    )
                }
            }
        }
    }
}

/**
 * Calculate payoff points for a strategy
 */
private fun calculatePayoffPoints(strategy: StrategyRecommendation): List<Pair<Double, Double>> {
    val points = mutableListOf<Pair<Double, Double>>()

    // Determine price range
    val strikes = strategy.legs.map { it.strikePrice }
    val minStrike = strikes.minOrNull() ?: return points
    val maxStrike = strikes.maxOrNull() ?: return points
    val range = (maxStrike - minStrike).coerceAtLeast(100.0)

    val startPrice = minStrike - range * 0.3
    val endPrice = maxStrike + range * 0.3
    val step = (endPrice - startPrice) / 50

    var price = startPrice
    while (price <= endPrice) {
        val pnl = calculatePnLAtPrice(strategy, price)
        points.add(price to pnl)
        price += step
    }

    return points
}

/**
 * Calculate P&L at a specific underlying price
 */
private fun calculatePnLAtPrice(strategy: StrategyRecommendation, underlyingPrice: Double): Double {
    var totalPnL = 0.0

    strategy.legs.forEach { leg ->
        val intrinsicValue = when (leg.optionType) {
            OptionType.CALL -> max(0.0, underlyingPrice - leg.strikePrice)
            OptionType.PUT -> max(0.0, leg.strikePrice - underlyingPrice)
        }

        val legPnL = when (leg.position) {
            com.optix.app.domain.model.LegPosition.BUY -> intrinsicValue - leg.premium
            com.optix.app.domain.model.LegPosition.SELL -> leg.premium - intrinsicValue
        }

        totalPnL += legPnL * leg.quantity
    }

    return totalPnL
}

/**
 * Mini payoff chart for single option trade
 */
@Composable
fun SingleOptionPayoffChart(
    strikePrice: Double,
    entryPrice: Double,
    targetPrice: Double,
    stopLoss: Double,
    optionType: OptionType,
    direction: TradeDirection,
    modifier: Modifier = Modifier,
    profitColor: Color = Color(0xFF22C55E),
    lossColor: Color = Color(0xFFEF4444)
) {
    Canvas(
        modifier = modifier
            .width(100.dp)
            .height(50.dp)
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2

        // Calculate price range
        val prices = listOf(strikePrice, strikePrice * 0.9, strikePrice * 1.1)
        val minPrice = prices.minOrNull() ?: strikePrice * 0.9
        val maxPrice = prices.maxOrNull() ?: strikePrice * 1.1
        val priceRange = maxPrice - minPrice

        // Draw payoff based on option type and direction
        val path = Path()
        val isBuy = direction == TradeDirection.BUY || direction == TradeDirection.STRONG_BUY
        val isSell = direction == TradeDirection.SELL || direction == TradeDirection.STRONG_SELL

        when {
            optionType == OptionType.CALL && isBuy -> {
                // Long call payoff
                val bePoint = strikePrice + entryPrice
                val beX = ((bePoint - minPrice) / priceRange * width).toFloat()
                val strikeX = ((strikePrice - minPrice) / priceRange * width).toFloat()

                path.moveTo(0f, midY + 15.dp.toPx())
                path.lineTo(strikeX, midY + 15.dp.toPx())
                path.lineTo(width, midY - 15.dp.toPx())
            }
            optionType == OptionType.PUT && isBuy -> {
                // Long put payoff
                val strikeX = ((strikePrice - minPrice) / priceRange * width).toFloat()

                path.moveTo(0f, midY - 15.dp.toPx())
                path.lineTo(strikeX, midY + 15.dp.toPx())
                path.lineTo(width, midY + 15.dp.toPx())
            }
            optionType == OptionType.CALL && isSell -> {
                // Short call payoff
                val strikeX = ((strikePrice - minPrice) / priceRange * width).toFloat()

                path.moveTo(0f, midY - 10.dp.toPx())
                path.lineTo(strikeX, midY - 10.dp.toPx())
                path.lineTo(width, midY + 20.dp.toPx())
            }
            optionType == OptionType.PUT && isSell -> {
                // Short put payoff
                val strikeX = ((strikePrice - minPrice) / priceRange * width).toFloat()

                path.moveTo(0f, midY + 20.dp.toPx())
                path.lineTo(strikeX, midY - 10.dp.toPx())
                path.lineTo(width, midY - 10.dp.toPx())
            }
        }

        // Draw zero line
        drawLine(
            color = Color.Gray.copy(alpha = 0.3f),
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = 0.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f))
        )

        // Draw payoff path
        val isLongPosition = isBuy
        drawPath(
            path = path,
            color = if (isLongPosition) profitColor else lossColor,
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
