package com.optix.app.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.domain.model.PayoffData
import com.optix.app.domain.model.PayoffPoint
import com.optix.app.presentation.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Payoff Diagram View for option strategies
 * Shows profit/loss curve with interactive touch support
 */
@Composable
fun PayoffDiagramView(
    payoffData: PayoffData,
    spotPrice: Double,
    breakevens: List<Double>,
    touchedPrice: Double?,
    touchedPayoff: Double?,
    onTouchChanged: (price: Double?, payoff: Double?) -> Unit,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 220.dp
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Animation for chart appearance
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600),
        label = "chartAnimation"
    )

    // Colors
    val profitColor = Profit
    val lossColor = Loss
    val spotLineColor = AccentBlue
    val breakevenColor = AccentOrange
    val gridColor = TextMutedDark.copy(alpha = 0.2f)
    val zeroLineColor = TextMutedDark.copy(alpha = 0.5f)
    val textColor = TextPrimaryDark
    val secondaryTextColor = TextSecondaryDark
    val backgroundColor = CardDark
    val surfaceColor = SurfaceDark

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with title and touched value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Payoff at Expiry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )

                if (touchedPrice != null && touchedPayoff != null) {
                    TouchedValueBadge(
                        price = touchedPrice,
                        payoff = touchedPayoff
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            ) {
                PayoffChart(
                    payoffData = payoffData,
                    spotPrice = spotPrice,
                    breakevens = breakevens,
                    touchedPrice = touchedPrice,
                    touchedPayoff = touchedPayoff,
                    animationProgress = animationProgress,
                    textMeasurer = textMeasurer,
                    profitColor = profitColor,
                    lossColor = lossColor,
                    spotLineColor = spotLineColor,
                    breakevenColor = breakevenColor,
                    gridColor = gridColor,
                    zeroLineColor = zeroLineColor,
                    textColor = textColor,
                    secondaryTextColor = secondaryTextColor,
                    onTouchChanged = onTouchChanged,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            PayoffLegend(
                profitColor = profitColor,
                lossColor = lossColor,
                breakevenColor = breakevenColor,
                spotColor = spotLineColor
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun PayoffChart(
    payoffData: PayoffData,
    spotPrice: Double,
    breakevens: List<Double>,
    touchedPrice: Double?,
    touchedPayoff: Double?,
    animationProgress: Float,
    textMeasurer: TextMeasurer,
    profitColor: Color,
    lossColor: Color,
    spotLineColor: Color,
    breakevenColor: Color,
    gridColor: Color,
    zeroLineColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    onTouchChanged: (price: Double?, payoff: Double?) -> Unit,
    modifier: Modifier = Modifier
) {
    val points = payoffData.points
    if (points.isEmpty()) return

    // Calculate bounds
    val minPrice = points.minOf { it.spotPrice }
    val maxPrice = points.maxOf { it.spotPrice }
    val minPayoff = points.minOf { it.payoff }
    val maxPayoff = points.maxOf { it.payoff }

    // Add padding to payoff range
    val payoffPadding = (maxPayoff - minPayoff) * 0.1
    val adjustedMinPayoff = minPayoff - payoffPadding
    val adjustedMaxPayoff = maxPayoff + payoffPadding

    // Padding for axes
    val leftPadding = 60f
    val rightPadding = 20f
    val topPadding = 30f
    val bottomPadding = 40f

    var currentTouchX by remember { mutableStateOf<Float?>(null) }

    Canvas(
        modifier = modifier
            .pointerInput(payoffData) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentTouchX = offset.x
                        handleTouch(
                            x = offset.x,
                            points = points,
                            minPrice = minPrice,
                            maxPrice = maxPrice,
                            leftPadding = leftPadding,
                            rightPadding = rightPadding,
                            canvasWidth = size.width.toFloat(),
                            onTouchChanged = onTouchChanged
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentTouchX = change.position.x
                        handleTouch(
                            x = change.position.x,
                            points = points,
                            minPrice = minPrice,
                            maxPrice = maxPrice,
                            leftPadding = leftPadding,
                            rightPadding = rightPadding,
                            canvasWidth = size.width.toFloat(),
                            onTouchChanged = onTouchChanged
                        )
                    },
                    onDragEnd = {
                        currentTouchX = null
                        onTouchChanged(null, null)
                    },
                    onDragCancel = {
                        currentTouchX = null
                        onTouchChanged(null, null)
                    }
                )
            }
            .pointerInput(payoffData) {
                detectTapGestures(
                    onPress = { offset ->
                        currentTouchX = offset.x
                        handleTouch(
                            x = offset.x,
                            points = points,
                            minPrice = minPrice,
                            maxPrice = maxPrice,
                            leftPadding = leftPadding,
                            rightPadding = rightPadding,
                            canvasWidth = size.width.toFloat(),
                            onTouchChanged = onTouchChanged
                        )
                        tryAwaitRelease()
                        currentTouchX = null
                        onTouchChanged(null, null)
                    }
                )
            }
    ) {
        val chartWidth = size.width - leftPadding - rightPadding
        val chartHeight = size.height - topPadding - bottomPadding

        // Draw grid lines
        drawGridLines(
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            leftPadding = leftPadding,
            topPadding = topPadding,
            gridColor = gridColor
        )

        // Draw zero line
        val zeroY = mapPayoffToY(0.0, adjustedMinPayoff, adjustedMaxPayoff, chartHeight, topPadding)
        drawDashedLine(
            start = Offset(leftPadding, zeroY),
            end = Offset(leftPadding + chartWidth, zeroY),
            color = zeroLineColor,
            dashLength = 8f,
            gapLength = 4f
        )

        // Draw payoff areas (profit and loss)
        drawPayoffAreas(
            points = points,
            minPrice = minPrice,
            maxPrice = maxPrice,
            adjustedMinPayoff = adjustedMinPayoff,
            adjustedMaxPayoff = adjustedMaxPayoff,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            leftPadding = leftPadding,
            topPadding = topPadding,
            zeroY = zeroY,
            profitColor = profitColor,
            lossColor = lossColor,
            animationProgress = animationProgress
        )

        // Draw payoff line
        drawPayoffLine(
            points = points,
            minPrice = minPrice,
            maxPrice = maxPrice,
            adjustedMinPayoff = adjustedMinPayoff,
            adjustedMaxPayoff = adjustedMaxPayoff,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            leftPadding = leftPadding,
            topPadding = topPadding,
            profitColor = profitColor,
            lossColor = lossColor,
            animationProgress = animationProgress
        )

        // Draw spot price line
        val spotX = mapPriceToX(spotPrice, minPrice, maxPrice, chartWidth, leftPadding)
        drawDashedLine(
            start = Offset(spotX, topPadding),
            end = Offset(spotX, topPadding + chartHeight),
            color = spotLineColor.copy(alpha = 0.7f),
            dashLength = 6f,
            gapLength = 4f,
            strokeWidth = 2f
        )

        // Draw "SPOT" label
        val spotLabel = textMeasurer.measure(
            text = "SPOT",
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = spotLineColor
            )
        )
        drawText(
            textLayoutResult = spotLabel,
            topLeft = Offset(spotX - spotLabel.size.width / 2, topPadding - 20f)
        )

        // Draw breakeven points
        breakevens.forEach { be ->
            val beX = mapPriceToX(be, minPrice, maxPrice, chartWidth, leftPadding)
            drawCircle(
                color = breakevenColor,
                radius = 6f,
                center = Offset(beX, zeroY)
            )

            // Draw breakeven label
            val beLabel = textMeasurer.measure(
                text = formatPrice(be),
                style = TextStyle(
                    fontSize = 9.sp,
                    color = breakevenColor
                )
            )
            drawText(
                textLayoutResult = beLabel,
                topLeft = Offset(beX - beLabel.size.width / 2, zeroY + 10f)
            )
        }

        // Draw touch indicator
        if (touchedPrice != null && touchedPayoff != null) {
            val touchX = mapPriceToX(touchedPrice, minPrice, maxPrice, chartWidth, leftPadding)
            val touchY = mapPayoffToY(touchedPayoff, adjustedMinPayoff, adjustedMaxPayoff, chartHeight, topPadding)

            // Vertical line at touch point
            drawLine(
                color = textColor.copy(alpha = 0.3f),
                start = Offset(touchX, topPadding),
                end = Offset(touchX, topPadding + chartHeight),
                strokeWidth = 1f
            )

            // Circle at intersection
            drawCircle(
                color = textColor,
                radius = 8f,
                center = Offset(touchX, touchY)
            )
            drawCircle(
                color = if (touchedPayoff >= 0) profitColor else lossColor,
                radius = 5f,
                center = Offset(touchX, touchY)
            )
        }

        // Draw Y-axis labels
        drawYAxisLabels(
            textMeasurer = textMeasurer,
            adjustedMinPayoff = adjustedMinPayoff,
            adjustedMaxPayoff = adjustedMaxPayoff,
            chartHeight = chartHeight,
            topPadding = topPadding,
            textColor = secondaryTextColor
        )

        // Draw X-axis labels
        drawXAxisLabels(
            textMeasurer = textMeasurer,
            minPrice = minPrice,
            maxPrice = maxPrice,
            chartWidth = chartWidth,
            chartHeight = chartHeight,
            leftPadding = leftPadding,
            topPadding = topPadding,
            textColor = secondaryTextColor
        )
    }
}

private fun handleTouch(
    x: Float,
    points: List<PayoffPoint>,
    minPrice: Double,
    maxPrice: Double,
    leftPadding: Float,
    rightPadding: Float,
    canvasWidth: Float,
    onTouchChanged: (price: Double?, payoff: Double?) -> Unit
) {
    val chartWidth = canvasWidth - leftPadding - rightPadding
    val clampedX = x.coerceIn(leftPadding, leftPadding + chartWidth)
    val price = mapXToPrice(clampedX, minPrice, maxPrice, chartWidth, leftPadding)

    // Find closest point and interpolate
    val payoff = interpolatePayoff(price, points)
    onTouchChanged(price, payoff)
}

private fun interpolatePayoff(price: Double, points: List<PayoffPoint>): Double {
    val sortedPoints = points.sortedBy { it.spotPrice }

    val lowerPoint = sortedPoints.lastOrNull { it.spotPrice <= price }
    val upperPoint = sortedPoints.firstOrNull { it.spotPrice >= price }

    return when {
        lowerPoint == null -> upperPoint?.payoff ?: 0.0
        upperPoint == null -> lowerPoint.payoff
        lowerPoint.spotPrice == upperPoint.spotPrice -> lowerPoint.payoff
        else -> {
            val ratio = (price - lowerPoint.spotPrice) / (upperPoint.spotPrice - lowerPoint.spotPrice)
            lowerPoint.payoff + ratio * (upperPoint.payoff - lowerPoint.payoff)
        }
    }
}

private fun DrawScope.drawGridLines(
    chartWidth: Float,
    chartHeight: Float,
    leftPadding: Float,
    topPadding: Float,
    gridColor: Color
) {
    val horizontalLines = 5
    val verticalLines = 5

    // Horizontal grid lines
    for (i in 0..horizontalLines) {
        val y = topPadding + (chartHeight / horizontalLines) * i
        drawLine(
            color = gridColor,
            start = Offset(leftPadding, y),
            end = Offset(leftPadding + chartWidth, y),
            strokeWidth = 0.5f
        )
    }

    // Vertical grid lines
    for (i in 0..verticalLines) {
        val x = leftPadding + (chartWidth / verticalLines) * i
        drawLine(
            color = gridColor,
            start = Offset(x, topPadding),
            end = Offset(x, topPadding + chartHeight),
            strokeWidth = 0.5f
        )
    }
}

private fun DrawScope.drawDashedLine(
    start: Offset,
    end: Offset,
    color: Color,
    dashLength: Float = 10f,
    gapLength: Float = 5f,
    strokeWidth: Float = 1f
) {
    val path = Path().apply {
        moveTo(start.x, start.y)
        lineTo(end.x, end.y)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, gapLength), 0f)
        )
    )
}

private fun DrawScope.drawPayoffAreas(
    points: List<PayoffPoint>,
    minPrice: Double,
    maxPrice: Double,
    adjustedMinPayoff: Double,
    adjustedMaxPayoff: Double,
    chartWidth: Float,
    chartHeight: Float,
    leftPadding: Float,
    topPadding: Float,
    zeroY: Float,
    profitColor: Color,
    lossColor: Color,
    animationProgress: Float
) {
    if (points.size < 2) return

    // Profit area (above zero line)
    val profitPath = Path()
    val lossPath = Path()

    var startedProfit = false
    var startedLoss = false

    for (i in points.indices) {
        val point = points[i]
        val x = mapPriceToX(point.spotPrice, minPrice, maxPrice, chartWidth, leftPadding)
        val y = mapPayoffToY(point.payoff * animationProgress, adjustedMinPayoff, adjustedMaxPayoff, chartHeight, topPadding)

        if (point.payoff >= 0) {
            if (!startedProfit) {
                profitPath.moveTo(x, zeroY)
                startedProfit = true
            }
            profitPath.lineTo(x, y)
        }

        if (point.payoff <= 0) {
            if (!startedLoss) {
                lossPath.moveTo(x, zeroY)
                startedLoss = true
            }
            lossPath.lineTo(x, y)
        }
    }

    // Close profit path
    if (startedProfit) {
        val lastProfitPoint = points.lastOrNull { it.payoff >= 0 }
        lastProfitPoint?.let {
            val x = mapPriceToX(it.spotPrice, minPrice, maxPrice, chartWidth, leftPadding)
            profitPath.lineTo(x, zeroY)
            profitPath.close()
        }

        drawPath(
            path = profitPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    profitColor.copy(alpha = 0.3f),
                    profitColor.copy(alpha = 0.05f)
                ),
                startY = topPadding,
                endY = zeroY
            )
        )
    }

    // Close loss path
    if (startedLoss) {
        val lastLossPoint = points.lastOrNull { it.payoff <= 0 }
        lastLossPoint?.let {
            val x = mapPriceToX(it.spotPrice, minPrice, maxPrice, chartWidth, leftPadding)
            lossPath.lineTo(x, zeroY)
            lossPath.close()
        }

        drawPath(
            path = lossPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lossColor.copy(alpha = 0.05f),
                    lossColor.copy(alpha = 0.3f)
                ),
                startY = zeroY,
                endY = topPadding + chartHeight
            )
        )
    }
}

private fun DrawScope.drawPayoffLine(
    points: List<PayoffPoint>,
    minPrice: Double,
    maxPrice: Double,
    adjustedMinPayoff: Double,
    adjustedMaxPayoff: Double,
    chartWidth: Float,
    chartHeight: Float,
    leftPadding: Float,
    topPadding: Float,
    profitColor: Color,
    lossColor: Color,
    animationProgress: Float
) {
    if (points.size < 2) return

    // Draw segments with color based on payoff sign
    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]

        val x1 = mapPriceToX(p1.spotPrice, minPrice, maxPrice, chartWidth, leftPadding)
        val y1 = mapPayoffToY(p1.payoff * animationProgress, adjustedMinPayoff, adjustedMaxPayoff, chartHeight, topPadding)
        val x2 = mapPriceToX(p2.spotPrice, minPrice, maxPrice, chartWidth, leftPadding)
        val y2 = mapPayoffToY(p2.payoff * animationProgress, adjustedMinPayoff, adjustedMaxPayoff, chartHeight, topPadding)

        val color = when {
            p1.payoff >= 0 && p2.payoff >= 0 -> profitColor
            p1.payoff <= 0 && p2.payoff <= 0 -> lossColor
            else -> {
                // Segment crosses zero - blend colors
                if (p1.payoff > p2.payoff) lossColor else profitColor
            }
        }

        drawLine(
            color = color,
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawYAxisLabels(
    textMeasurer: TextMeasurer,
    adjustedMinPayoff: Double,
    adjustedMaxPayoff: Double,
    chartHeight: Float,
    topPadding: Float,
    textColor: Color
) {
    val labelCount = 5
    for (i in 0..labelCount) {
        val payoff = adjustedMinPayoff + (adjustedMaxPayoff - adjustedMinPayoff) * (1 - i.toDouble() / labelCount)
        val y = topPadding + (chartHeight / labelCount) * i

        val label = textMeasurer.measure(
            text = formatCurrency(payoff),
            style = TextStyle(
                fontSize = 10.sp,
                color = textColor
            )
        )

        drawText(
            textLayoutResult = label,
            topLeft = Offset(5f, y - label.size.height / 2)
        )
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawXAxisLabels(
    textMeasurer: TextMeasurer,
    minPrice: Double,
    maxPrice: Double,
    chartWidth: Float,
    chartHeight: Float,
    leftPadding: Float,
    topPadding: Float,
    textColor: Color
) {
    val labelCount = 5
    for (i in 0..labelCount) {
        val price = minPrice + (maxPrice - minPrice) * (i.toDouble() / labelCount)
        val x = leftPadding + (chartWidth / labelCount) * i

        val label = textMeasurer.measure(
            text = formatPrice(price),
            style = TextStyle(
                fontSize = 10.sp,
                color = textColor
            )
        )

        drawText(
            textLayoutResult = label,
            topLeft = Offset(x - label.size.width / 2, topPadding + chartHeight + 8f)
        )
    }
}

private fun mapPriceToX(
    price: Double,
    minPrice: Double,
    maxPrice: Double,
    chartWidth: Float,
    leftPadding: Float
): Float {
    if (maxPrice == minPrice) return leftPadding + chartWidth / 2f
    val ratio = (price - minPrice) / (maxPrice - minPrice)
    return leftPadding + (chartWidth * ratio).toFloat()
}

private fun mapXToPrice(
    x: Float,
    minPrice: Double,
    maxPrice: Double,
    chartWidth: Float,
    leftPadding: Float
): Double {
    if (chartWidth == 0f || maxPrice == minPrice) return (minPrice + maxPrice) / 2.0
    val ratio = (x - leftPadding) / chartWidth
    return minPrice + (maxPrice - minPrice) * ratio
}

private fun mapPayoffToY(
    payoff: Double,
    minPayoff: Double,
    maxPayoff: Double,
    chartHeight: Float,
    topPadding: Float
): Float {
    if (maxPayoff == minPayoff) return topPadding + chartHeight / 2f
    val ratio = (payoff - minPayoff) / (maxPayoff - minPayoff)
    return topPadding + chartHeight - (chartHeight * ratio).toFloat()
}

private fun formatCurrency(value: Double): String {
    return when {
        abs(value) >= 100000 -> String.format("%.0fL", value / 100000)
        abs(value) >= 1000 -> String.format("%.0fK", value / 1000)
        else -> String.format("%.0f", value)
    }
}

private fun formatPrice(value: Double): String {
    return String.format("%.0f", value)
}

/**
 * Badge showing touched price and payoff values
 */
@Composable
private fun TouchedValueBadge(
    price: Double,
    payoff: Double
) {
    val borderColor = if (payoff >= 0) Profit.copy(alpha = 0.5f) else Loss.copy(alpha = 0.5f)
    val payoffColor = if (payoff >= 0) Profit else Loss

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = String.format("%.0f", price),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = TextPrimaryDark
            )
            Text(
                text = formatPayoffDisplay(payoff),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = payoffColor
            )
        }
    }
}

private fun formatPayoffDisplay(value: Double): String {
    val prefix = if (value >= 0) "+" else ""
    return when {
        abs(value) >= 100000 -> "$prefix${String.format("%.1fL", value / 100000)}"
        abs(value) >= 1000 -> "$prefix${String.format("%.1fK", value / 1000)}"
        else -> "$prefix${String.format("%.0f", value)}"
    }
}

/**
 * Legend showing chart indicators
 */
@Composable
private fun PayoffLegend(
    profitColor: Color,
    lossColor: Color,
    breakevenColor: Color,
    spotColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LegendItem(color = profitColor, label = "Profit Zone")
        LegendItem(color = lossColor, label = "Loss Zone")
        LegendItem(color = breakevenColor, label = "Breakeven")
        LegendItem(color = spotColor, label = "Spot Price")
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryDark
        )
    }
}

/**
 * Summary card showing key payoff metrics
 */
@Composable
fun PayoffSummaryCard(
    payoffData: PayoffData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = SurfaceDark
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PayoffMetric(
                label = "Max Profit",
                value = formatCurrencyFull(payoffData.maxProfit),
                color = if (payoffData.maxProfit > 0) Profit else TextPrimaryDark
            )

            PayoffMetric(
                label = "Max Loss",
                value = formatCurrencyFull(payoffData.maxLoss),
                color = if (payoffData.maxLoss < 0) Loss else TextPrimaryDark
            )

            PayoffMetric(
                label = "Breakeven",
                value = if (payoffData.breakevens.isNotEmpty()) {
                    payoffData.breakevens.joinToString(", ") { formatPrice(it) }
                } else {
                    "N/A"
                },
                color = AccentOrange
            )

            PayoffMetric(
                label = "R:R Ratio",
                value = if (payoffData.riskRewardRatio.isFinite()) {
                    String.format("1:%.1f", payoffData.riskRewardRatio)
                } else {
                    "Unlimited"
                },
                color = AccentBlue
            )
        }
    }
}

@Composable
private fun PayoffMetric(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryDark
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

private fun formatCurrencyFull(value: Double): String {
    val prefix = if (value > 0) "+" else ""
    return when {
        abs(value) >= 100000 -> "$prefix${String.format("%.1fL", value / 100000)}"
        abs(value) >= 1000 -> "$prefix${String.format("%.1fK", value / 1000)}"
        else -> "$prefix${String.format("%.0f", value)}"
    }
}
