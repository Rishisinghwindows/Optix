package com.optix.app.presentation.screens.charts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.domain.model.Candlestick
import com.optix.app.domain.model.ChartData
import com.optix.app.domain.model.CrosshairData
import com.optix.app.domain.model.TechnicalIndicators
import com.optix.app.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Custom candlestick chart view with volume bars and moving averages
 * Built using Jetpack Compose Canvas for high performance
 */
@Composable
fun IndexChartView(
    chartData: ChartData,
    technicalIndicators: TechnicalIndicators,
    showVolume: Boolean = true,
    showMA: Boolean = true,
    maDisplayType: MADisplayType = MADisplayType.SMA_20_50,
    modifier: Modifier = Modifier
) {
    var crosshairData by remember { mutableStateOf<CrosshairData?>(null) }
    var showCrosshair by remember { mutableStateOf(false) }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (showCrosshair) 1f else 0f,
        animationSpec = tween(200),
        label = "crosshairAlpha"
    )

    Column(modifier = modifier) {
        // Crosshair info display
        CrosshairInfoDisplay(
            crosshairData = crosshairData,
            visible = showCrosshair && crosshairData != null
        )

        // Main chart area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CandlestickChart(
                chartData = chartData,
                technicalIndicators = technicalIndicators,
                showMA = showMA,
                maDisplayType = maDisplayType,
                crosshairAlpha = animatedAlpha,
                crosshairData = crosshairData,
                onCrosshairMove = { data ->
                    crosshairData = data
                    showCrosshair = true
                },
                onCrosshairEnd = {
                    showCrosshair = false
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Volume chart
        if (showVolume) {
            Spacer(modifier = Modifier.height(4.dp))
            VolumeChart(
                chartData = chartData,
                crosshairIndex = crosshairData?.index,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )
        }

        // Time axis
        TimeAxisLabels(
            chartData = chartData,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        )
    }
}

@Composable
private fun CrosshairInfoDisplay(
    crosshairData: CrosshairData?,
    visible: Boolean
) {
    if (!visible || crosshairData == null) {
        Spacer(modifier = Modifier.height(48.dp))
        return
    }

    val candle = crosshairData.candlestick
    val dateFormat = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = dateFormat.format(Date(candle.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CrosshairPriceItem("O", candle.open)
                CrosshairPriceItem("H", candle.high, highlight = true)
                CrosshairPriceItem("L", candle.low, highlight = true)
                CrosshairPriceItem("C", candle.close, isClose = true, isBullish = candle.isBullish)
                CrosshairVolumeItem(candle.volume)
            }
        }
    }
}

@Composable
private fun CrosshairPriceItem(
    label: String,
    value: Double,
    highlight: Boolean = false,
    isClose: Boolean = false,
    isBullish: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = String.format("%.2f", value),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isClose) FontWeight.Bold else FontWeight.Normal
            ),
            color = when {
                isClose -> if (isBullish) Profit else Loss
                highlight && label == "H" -> Profit
                highlight && label == "L" -> Loss
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun CrosshairVolumeItem(volume: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Vol",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatVolume(volume),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CandlestickChart(
    chartData: ChartData,
    technicalIndicators: TechnicalIndicators,
    showMA: Boolean,
    maDisplayType: MADisplayType,
    crosshairAlpha: Float,
    crosshairData: CrosshairData?,
    onCrosshairMove: (CrosshairData) -> Unit,
    onCrosshairEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val candlesticks = chartData.candlesticks
    if (candlesticks.isEmpty()) return

    val bullishColor = Profit
    val bearishColor = Loss
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    // MA colors
    val sma20Color = AccentBlue
    val sma50Color = AccentOrange
    val ema9Color = AccentPurple
    val ema21Color = AccentCyan

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Canvas(
        modifier = modifier
            .pointerInput(candlesticks) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val index = calculateCandleIndex(offset.x, candlesticks.size, size.width.toFloat())
                        if (index in candlesticks.indices) {
                            onCrosshairMove(
                                CrosshairData(
                                    candlestick = candlesticks[index],
                                    index = index,
                                    x = offset.x,
                                    y = offset.y
                                )
                            )
                        }
                    },
                    onDrag = { change, _ ->
                        val index = calculateCandleIndex(change.position.x, candlesticks.size, size.width.toFloat())
                        if (index in candlesticks.indices) {
                            onCrosshairMove(
                                CrosshairData(
                                    candlestick = candlesticks[index],
                                    index = index,
                                    x = change.position.x,
                                    y = change.position.y
                                )
                            )
                        }
                    },
                    onDragEnd = { onCrosshairEnd() },
                    onDragCancel = { onCrosshairEnd() }
                )
            }
            .pointerInput(candlesticks) {
                detectTapGestures(
                    onPress = { offset ->
                        val index = calculateCandleIndex(offset.x, candlesticks.size, size.width.toFloat())
                        if (index in candlesticks.indices) {
                            onCrosshairMove(
                                CrosshairData(
                                    candlestick = candlesticks[index],
                                    index = index,
                                    x = offset.x,
                                    y = offset.y
                                )
                            )
                        }
                        tryAwaitRelease()
                        onCrosshairEnd()
                    }
                )
            }
    ) {
        val chartWidth = size.width
        val chartHeight = size.height
        val priceAxisWidth = 60.dp.toPx()
        val chartAreaWidth = chartWidth - priceAxisWidth

        // Calculate price range with padding
        val highestPrice = candlesticks.maxOf { it.high }
        val lowestPrice = candlesticks.minOf { it.low }
        val priceRange = highestPrice - lowestPrice
        val pricePadding = priceRange * 0.1
        val adjustedHigh = highestPrice + pricePadding
        val adjustedLow = lowestPrice - pricePadding
        val adjustedRange = adjustedHigh - adjustedLow

        // Draw grid lines
        drawGridLines(
            chartAreaWidth = chartAreaWidth,
            chartHeight = chartHeight,
            gridColor = gridColor,
            adjustedHigh = adjustedHigh,
            adjustedRange = adjustedRange,
            textMeasurer = textMeasurer
        )

        // Draw price axis
        drawPriceAxis(
            chartAreaWidth = chartAreaWidth,
            priceAxisWidth = priceAxisWidth,
            chartHeight = chartHeight,
            adjustedHigh = adjustedHigh,
            adjustedRange = adjustedRange,
            textMeasurer = textMeasurer
        )

        // Calculate candle dimensions
        val candleCount = candlesticks.size
        val candleWidth = chartAreaWidth / candleCount
        val candleBodyWidth = candleWidth * 0.7f
        val wickWidth = 1.5.dp.toPx()

        // Draw moving averages if enabled
        if (showMA && maDisplayType != MADisplayType.NONE) {
            when (maDisplayType) {
                MADisplayType.SMA_20_50 -> {
                    drawMovingAverage(
                        values = technicalIndicators.sma20,
                        color = sma20Color,
                        candleWidth = candleWidth,
                        chartHeight = chartHeight,
                        adjustedHigh = adjustedHigh,
                        adjustedRange = adjustedRange
                    )
                    drawMovingAverage(
                        values = technicalIndicators.sma50,
                        color = sma50Color,
                        candleWidth = candleWidth,
                        chartHeight = chartHeight,
                        adjustedHigh = adjustedHigh,
                        adjustedRange = adjustedRange
                    )
                }
                MADisplayType.EMA_9_21 -> {
                    drawMovingAverage(
                        values = technicalIndicators.ema9,
                        color = ema9Color,
                        candleWidth = candleWidth,
                        chartHeight = chartHeight,
                        adjustedHigh = adjustedHigh,
                        adjustedRange = adjustedRange
                    )
                    drawMovingAverage(
                        values = technicalIndicators.ema21,
                        color = ema21Color,
                        candleWidth = candleWidth,
                        chartHeight = chartHeight,
                        adjustedHigh = adjustedHigh,
                        adjustedRange = adjustedRange
                    )
                }
                MADisplayType.ALL -> {
                    drawMovingAverage(technicalIndicators.sma20, sma20Color, candleWidth, chartHeight, adjustedHigh, adjustedRange)
                    drawMovingAverage(technicalIndicators.sma50, sma50Color, candleWidth, chartHeight, adjustedHigh, adjustedRange)
                    drawMovingAverage(technicalIndicators.ema9, ema9Color, candleWidth, chartHeight, adjustedHigh, adjustedRange)
                    drawMovingAverage(technicalIndicators.ema21, ema21Color, candleWidth, chartHeight, adjustedHigh, adjustedRange)
                }
                MADisplayType.NONE -> { /* No MAs */ }
            }
        }

        // Draw candlesticks
        candlesticks.forEachIndexed { index, candle ->
            val centerX = index * candleWidth + candleWidth / 2

            val highY = ((adjustedHigh - candle.high) / adjustedRange * chartHeight).toFloat()
            val lowY = ((adjustedHigh - candle.low) / adjustedRange * chartHeight).toFloat()
            val openY = ((adjustedHigh - candle.open) / adjustedRange * chartHeight).toFloat()
            val closeY = ((adjustedHigh - candle.close) / adjustedRange * chartHeight).toFloat()

            val color = if (candle.isBullish) bullishColor else bearishColor

            // Draw wick
            drawLine(
                color = color,
                start = Offset(centerX, highY),
                end = Offset(centerX, lowY),
                strokeWidth = wickWidth
            )

            // Draw body
            val bodyTop = minOf(openY, closeY)
            val bodyBottom = maxOf(openY, closeY)
            val bodyHeight = maxOf(bodyBottom - bodyTop, 1f)

            if (candle.isBullish) {
                // Hollow candle for bullish
                drawRect(
                    color = color,
                    topLeft = Offset(centerX - candleBodyWidth / 2, bodyTop),
                    size = Size(candleBodyWidth, bodyHeight),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            } else {
                // Filled candle for bearish
                drawRect(
                    color = color,
                    topLeft = Offset(centerX - candleBodyWidth / 2, bodyTop),
                    size = Size(candleBodyWidth, bodyHeight)
                )
            }
        }

        // Draw crosshair
        if (crosshairAlpha > 0 && crosshairData != null) {
            val crosshairX = crosshairData.index * candleWidth + candleWidth / 2
            val candle = crosshairData.candlestick
            val crosshairY = ((adjustedHigh - candle.close) / adjustedRange * chartHeight).toFloat()

            // Vertical line
            drawLine(
                color = crosshairColor.copy(alpha = crosshairAlpha * 0.6f),
                start = Offset(crosshairX, 0f),
                end = Offset(crosshairX, chartHeight),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            )

            // Horizontal line
            drawLine(
                color = crosshairColor.copy(alpha = crosshairAlpha * 0.6f),
                start = Offset(0f, crosshairY),
                end = Offset(chartAreaWidth, crosshairY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            )

            // Price label on crosshair
            drawCircle(
                color = if (candle.isBullish) bullishColor else bearishColor,
                radius = 4.dp.toPx(),
                center = Offset(crosshairX, crosshairY)
            )
        }
    }
}

private fun DrawScope.drawGridLines(
    chartAreaWidth: Float,
    chartHeight: Float,
    gridColor: Color,
    adjustedHigh: Double,
    adjustedRange: Double,
    textMeasurer: TextMeasurer
) {
    val gridLineCount = 5

    for (i in 0..gridLineCount) {
        val y = (chartHeight / gridLineCount) * i
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(chartAreaWidth, y),
            strokeWidth = 1f
        )
    }
}

private fun DrawScope.drawPriceAxis(
    chartAreaWidth: Float,
    priceAxisWidth: Float,
    chartHeight: Float,
    adjustedHigh: Double,
    adjustedRange: Double,
    textMeasurer: TextMeasurer
) {
    val gridLineCount = 5
    val textStyle = TextStyle(
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        color = Color.Gray
    )

    for (i in 0..gridLineCount) {
        val y = (chartHeight / gridLineCount) * i
        val price = adjustedHigh - (adjustedRange * i / gridLineCount)
        val priceText = String.format("%.0f", price)

        val textLayoutResult = textMeasurer.measure(priceText, textStyle)
        drawText(
            textLayoutResult = textLayoutResult,
            topLeft = Offset(
                chartAreaWidth + 8.dp.toPx(),
                y - textLayoutResult.size.height / 2
            )
        )
    }
}

private fun DrawScope.drawMovingAverage(
    values: List<Double>,
    color: Color,
    candleWidth: Float,
    chartHeight: Float,
    adjustedHigh: Double,
    adjustedRange: Double
) {
    if (values.isEmpty()) return

    val path = Path()
    var started = false

    values.forEachIndexed { index, value ->
        if (value > 0) {
            val x = index * candleWidth + candleWidth / 2
            val y = ((adjustedHigh - value) / adjustedRange * chartHeight).toFloat()

            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                path.lineTo(x, y)
            }
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

@Composable
private fun VolumeChart(
    chartData: ChartData,
    crosshairIndex: Int?,
    modifier: Modifier = Modifier
) {
    val candlesticks = chartData.candlesticks
    if (candlesticks.isEmpty()) return

    val maxVolume = chartData.maxVolume

    Canvas(modifier = modifier) {
        val chartWidth = size.width - 60.dp.toPx()
        val chartHeight = size.height
        val candleCount = candlesticks.size
        val barWidth = chartWidth / candleCount
        val barBodyWidth = barWidth * 0.7f

        candlesticks.forEachIndexed { index, candle ->
            val centerX = index * barWidth + barWidth / 2
            val barHeight = if (maxVolume > 0) {
                (candle.volume.toFloat() / maxVolume * chartHeight * 0.9f)
            } else 0f

            val color = if (candle.isBullish) Profit else Loss
            val alpha = if (crosshairIndex == index) 1f else 0.6f

            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(centerX - barBodyWidth / 2, chartHeight - barHeight),
                size = Size(barBodyWidth, barHeight)
            )
        }
    }
}

@Composable
private fun TimeAxisLabels(
    chartData: ChartData,
    modifier: Modifier = Modifier
) {
    val candlesticks = chartData.candlesticks
    if (candlesticks.isEmpty()) return

    val timeFormat = remember(chartData.timeframe) {
        when {
            chartData.timeframe.intervalMinutes < 60 -> SimpleDateFormat("HH:mm", Locale.getDefault())
            chartData.timeframe.intervalMinutes < 1440 -> SimpleDateFormat("HH:mm", Locale.getDefault())
            else -> SimpleDateFormat("dd MMM", Locale.getDefault())
        }
    }

    Row(
        modifier = modifier
            .padding(end = 60.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val labelCount = 5
        val step = candlesticks.size / labelCount

        for (i in 0 until labelCount) {
            val index = i * step
            if (index < candlesticks.size) {
                Text(
                    text = timeFormat.format(Date(candlesticks[index].timestamp)),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun calculateCandleIndex(x: Float, candleCount: Int, chartWidth: Float): Int {
    val chartAreaWidth = chartWidth - 60f * 2.625f // Approximate dp to px conversion
    val candleWidth = chartAreaWidth / candleCount
    return (x / candleWidth).toInt().coerceIn(0, candleCount - 1)
}

private fun formatVolume(volume: Long): String {
    return when {
        volume >= 10_000_000 -> String.format("%.1fCr", volume / 10_000_000.0)
        volume >= 100_000 -> String.format("%.1fL", volume / 100_000.0)
        volume >= 1000 -> String.format("%.1fK", volume / 1000.0)
        else -> volume.toString()
    }
}
