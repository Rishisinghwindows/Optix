package com.optix.app.presentation.screens.aiinsights.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.domain.model.ConfidenceLevel

/**
 * Animated circular score indicator with fill animation and pulse effect for high scores.
 *
 * @param score Score value (0-100)
 * @param confidence Confidence level for color coding
 * @param modifier Modifier for the component
 * @param size Size of the circle
 * @param strokeWidth Width of the progress stroke
 * @param animationDuration Duration of fill animation in milliseconds
 * @param showPulseForHighScore Whether to show pulse animation for high scores (>=75)
 */
@Composable
fun AnimatedScoreCircle(
    score: Int,
    confidence: ConfidenceLevel,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 6.dp,
    animationDuration: Int = 1000,
    showPulseForHighScore: Boolean = true
) {
    val scoreColor = Color(confidence.color)
    val backgroundColor = scoreColor.copy(alpha = 0.2f)

    // Animate the progress
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(score) {
        animatedProgress.animateTo(
            targetValue = score / 100f,
            animationSpec = tween(
                durationMillis = animationDuration,
                easing = FastOutSlowInEasing
            )
        )
    }

    // Pulse animation for high scores
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val shouldPulse = showPulseForHighScore && score >= 75
    val currentScale = if (shouldPulse) pulseScale else 1f

    Box(
        modifier = modifier.size(size * currentScale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val sweepAngle = 360f * animatedProgress.value
            val stroke = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )

            // Background circle
            drawArc(
                color = backgroundColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )

            // Progress arc
            drawArc(
                color = scoreColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = stroke
            )
        }

        // Score text
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 3).sp
            ),
            color = scoreColor
        )
    }
}

/**
 * Compact version of AnimatedScoreCircle for list items
 */
@Composable
fun CompactScoreCircle(
    score: Int,
    confidence: ConfidenceLevel,
    modifier: Modifier = Modifier
) {
    AnimatedScoreCircle(
        score = score,
        confidence = confidence,
        modifier = modifier,
        size = 48.dp,
        strokeWidth = 4.dp,
        animationDuration = 600,
        showPulseForHighScore = false
    )
}

/**
 * Mini score indicator for inline use
 */
@Composable
fun MiniScoreIndicator(
    score: Int,
    confidence: ConfidenceLevel,
    modifier: Modifier = Modifier
) {
    val scoreColor = Color(confidence.color)

    Box(
        modifier = modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val sweepAngle = 360f * (score / 100f)
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)

            drawArc(
                color = scoreColor.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )

            drawArc(
                color = scoreColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = stroke
            )
        }

        Text(
            text = score.toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = scoreColor
        )
    }
}
