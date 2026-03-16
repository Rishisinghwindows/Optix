package com.optix.app.presentation.screens.aiinsights.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.optix.app.domain.model.NetGreeks
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Radar/Spider chart for visualizing Greeks
 */
@Composable
fun GreeksRadarChart(
    delta: Double,
    gamma: Double,
    theta: Double,
    vega: Double,
    modifier: Modifier = Modifier,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
    strokeColor: Color = MaterialTheme.colorScheme.primary,
    gridColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
    labelColor: Color = MaterialTheme.colorScheme.onSurface
) {
    // Normalize values to 0-1 range
    val normalizedData = remember(delta, gamma, theta, vega) {
        listOf(
            "Delta" to normalizeGreek(delta, 1.0),
            "Gamma" to normalizeGreek(gamma, 0.1),
            "Theta" to normalizeGreek(theta, 10.0),
            "Vega" to normalizeGreek(vega, 50.0)
        )
    }

    Canvas(modifier = modifier.size(150.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = minOf(size.width, size.height) / 2 - 20.dp.toPx()
        val labelPadding = 15.dp.toPx()

        val numAxes = normalizedData.size
        val angleStep = (2 * PI / numAxes).toFloat()

        // Draw grid circles
        listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { level ->
            val radius = maxRadius * level
            drawCircle(
                color = gridColor,
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Draw axes
        normalizedData.forEachIndexed { index, _ ->
            val angle = (index * angleStep) - (PI / 2).toFloat()
            val endX = center.x + maxRadius * cos(angle)
            val endY = center.y + maxRadius * sin(angle)

            drawLine(
                color = gridColor,
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Draw data polygon
        val dataPath = Path()
        normalizedData.forEachIndexed { index, (_, value) ->
            val angle = (index * angleStep) - (PI / 2).toFloat()
            val radius = maxRadius * value.coerceIn(0f, 1f)
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle)

            if (index == 0) {
                dataPath.moveTo(x, y)
            } else {
                dataPath.lineTo(x, y)
            }
        }
        dataPath.close()

        // Fill polygon
        drawPath(
            path = dataPath,
            color = fillColor,
            style = Fill
        )

        // Stroke polygon
        drawPath(
            path = dataPath,
            color = strokeColor,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw data points
        normalizedData.forEachIndexed { index, (_, value) ->
            val angle = (index * angleStep) - (PI / 2).toFloat()
            val radius = maxRadius * value.coerceIn(0f, 1f)
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle)

            drawCircle(
                color = strokeColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }

        // Draw labels
        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.hashCode()
            textSize = 10.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }

        normalizedData.forEachIndexed { index, (label, _) ->
            val angle = (index * angleStep) - (PI / 2).toFloat()
            val labelRadius = maxRadius + labelPadding
            val x = center.x + labelRadius * cos(angle)
            val y = center.y + labelRadius * sin(angle)

            drawContext.canvas.nativeCanvas.drawText(
                label,
                x,
                y + 4.dp.toPx(),
                labelPaint
            )
        }
    }
}

/**
 * Normalize a Greek value to 0-1 range
 */
private fun normalizeGreek(value: Double, maxExpected: Double): Float {
    return (abs(value) / maxExpected).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Radar chart specifically for NetGreeks
 */
@Composable
fun NetGreeksRadarChart(
    netGreeks: NetGreeks,
    modifier: Modifier = Modifier,
    fillColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
    strokeColor: Color = MaterialTheme.colorScheme.primary
) {
    GreeksRadarChart(
        delta = netGreeks.delta,
        gamma = netGreeks.gamma,
        theta = netGreeks.theta,
        vega = netGreeks.vega,
        modifier = modifier,
        fillColor = fillColor,
        strokeColor = strokeColor
    )
}

/**
 * Compact Greeks display row
 */
@Composable
fun GreeksRow(
    delta: Double,
    gamma: Double,
    theta: Double,
    vega: Double,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.size(width = 200.dp, height = 30.dp)
    ) {
        val barHeight = 8.dp.toPx()
        val barSpacing = size.width / 4
        val maxBarWidth = barSpacing - 10.dp.toPx()

        val greeks = listOf(
            Triple("Δ", delta, 1.0),
            Triple("Γ", gamma, 0.1),
            Triple("Θ", theta, 10.0),
            Triple("ν", vega, 50.0)
        )

        greeks.forEachIndexed { index, (symbol, value, max) ->
            val x = index * barSpacing + 5.dp.toPx()
            val normalized = (abs(value) / max).coerceIn(0.0, 1.0)
            val barWidth = (maxBarWidth * normalized).toFloat()

            val color = when {
                value > 0 -> Color(0xFF22C55E)
                value < 0 -> Color(0xFFEF4444)
                else -> Color(0xFF6B7280)
            }

            // Background bar
            drawRoundRect(
                color = color.copy(alpha = 0.2f),
                topLeft = Offset(x, (size.height - barHeight) / 2),
                size = androidx.compose.ui.geometry.Size(maxBarWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )

            // Value bar
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (size.height - barHeight) / 2),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
        }
    }
}
