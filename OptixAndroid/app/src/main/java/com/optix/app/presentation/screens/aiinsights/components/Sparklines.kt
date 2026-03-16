package com.optix.app.presentation.screens.aiinsights.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mini sparkline chart for OI trend
 */
@Composable
fun OITrendSparkline(
    dataPoints: List<Long>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    showLastValue: Boolean = true
) {
    if (dataPoints.isEmpty()) return

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(dataPoints) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(800))
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .width(60.dp)
                .height(24.dp)
        ) {
            if (dataPoints.size < 2) return@Canvas

            val minVal = dataPoints.minOrNull()?.toFloat() ?: 0f
            val maxVal = dataPoints.maxOrNull()?.toFloat() ?: 1f
            val range = (maxVal - minVal).coerceAtLeast(1f)

            val path = Path()
            val pointsToShow = (dataPoints.size * animatedProgress.value).toInt().coerceAtLeast(2)

            dataPoints.take(pointsToShow).forEachIndexed { index, value ->
                val x = (index.toFloat() / (dataPoints.size - 1)) * size.width
                val y = size.height - ((value - minVal) / range) * size.height

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw last point
            if (pointsToShow == dataPoints.size) {
                val lastX = size.width
                val lastY = size.height - ((dataPoints.last() - minVal) / range) * size.height
                drawCircle(
                    color = lineColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
            }
        }

        if (showLastValue) {
            Spacer(modifier = Modifier.width(4.dp))
            val lastValue = dataPoints.lastOrNull() ?: 0L
            val previousValue = dataPoints.getOrNull(dataPoints.size - 2) ?: lastValue
            val change = lastValue - previousValue
            val changeColor = when {
                change > 0 -> Color(0xFF22C55E)
                change < 0 -> Color(0xFFEF4444)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Text(
                text = formatOI(lastValue),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                ),
                color = changeColor
            )
        }
    }
}

/**
 * Mini sparkline chart for IV trend
 */
@Composable
fun IVTrendSparkline(
    dataPoints: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF8B5CF6), // Purple for IV
    showLastValue: Boolean = true
) {
    if (dataPoints.isEmpty()) return

    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(dataPoints) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(800))
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .width(60.dp)
                .height(24.dp)
        ) {
            if (dataPoints.size < 2) return@Canvas

            val minVal = dataPoints.minOrNull()?.toFloat() ?: 0f
            val maxVal = dataPoints.maxOrNull()?.toFloat() ?: 1f
            val range = (maxVal - minVal).coerceAtLeast(0.01f)

            val path = Path()
            val pointsToShow = (dataPoints.size * animatedProgress.value).toInt().coerceAtLeast(2)

            dataPoints.take(pointsToShow).forEachIndexed { index, value ->
                val x = (index.toFloat() / (dataPoints.size - 1)) * size.width
                val y = size.height - ((value.toFloat() - minVal) / range) * size.height

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw last point
            if (pointsToShow == dataPoints.size) {
                val lastX = size.width
                val lastY = size.height - ((dataPoints.last().toFloat() - minVal) / range) * size.height
                drawCircle(
                    color = lineColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
            }
        }

        if (showLastValue) {
            Spacer(modifier = Modifier.width(4.dp))
            val lastValue = dataPoints.lastOrNull() ?: 0.0
            val previousValue = dataPoints.getOrNull(dataPoints.size - 2) ?: lastValue
            val change = lastValue - previousValue
            val changeColor = when {
                change > 1 -> Color(0xFFEF4444) // IV up is usually bearish
                change < -1 -> Color(0xFF22C55E) // IV down is usually bullish
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Text(
                text = "${String.format("%.1f", lastValue)}%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                ),
                color = changeColor
            )
        }
    }
}

/**
 * Price trend sparkline
 */
@Composable
fun PriceTrendSparkline(
    dataPoints: List<Double>,
    modifier: Modifier = Modifier,
    showChangePercent: Boolean = true
) {
    if (dataPoints.isEmpty()) return

    val firstValue = dataPoints.first()
    val lastValue = dataPoints.last()
    val changePercent = if (firstValue > 0) ((lastValue - firstValue) / firstValue) * 100 else 0.0
    val lineColor = when {
        changePercent > 0.5 -> Color(0xFF22C55E)
        changePercent < -0.5 -> Color(0xFFEF4444)
        else -> Color(0xFF6B7280)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .width(50.dp)
                .height(20.dp)
        ) {
            if (dataPoints.size < 2) return@Canvas

            val minVal = dataPoints.minOrNull()?.toFloat() ?: 0f
            val maxVal = dataPoints.maxOrNull()?.toFloat() ?: 1f
            val range = (maxVal - minVal).coerceAtLeast(0.01f)

            val path = Path()
            dataPoints.forEachIndexed { index, value ->
                val x = (index.toFloat() / (dataPoints.size - 1)) * size.width
                val y = size.height - ((value.toFloat() - minVal) / range) * (size.height - 4.dp.toPx())

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        if (showChangePercent) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${if (changePercent >= 0) "+" else ""}${String.format("%.1f", changePercent)}%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                ),
                color = lineColor
            )
        }
    }
}

/**
 * Format OI value for display
 */
private fun formatOI(value: Long): String {
    return when {
        value >= 10_000_000 -> "${String.format("%.1f", value / 1_000_000.0)}Cr"
        value >= 100_000 -> "${String.format("%.1f", value / 100_000.0)}L"
        value >= 1000 -> "${String.format("%.1f", value / 1000.0)}K"
        else -> value.toString()
    }
}
