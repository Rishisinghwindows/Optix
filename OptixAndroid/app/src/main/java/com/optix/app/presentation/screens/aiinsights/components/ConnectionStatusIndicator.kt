package com.optix.app.presentation.screens.aiinsights.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.data.remote.websocket.WebSocketConnectionState

/**
 * Indicator showing WebSocket connection status
 */
@Composable
fun ConnectionStatusIndicator(
    connectionState: WebSocketConnectionState,
    modifier: Modifier = Modifier
) {
    val (color, text) = getStatusColorAndText(connectionState)
    val isAnimated = connectionState is WebSocketConnectionState.Connected ||
            connectionState is WebSocketConnectionState.Connecting ||
            connectionState is WebSocketConnectionState.Reconnecting

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(300),
        label = "status_color"
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = animatedColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Pulsing dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(if (isAnimated) pulseScale else 1f)
                    .background(animatedColor, CircleShape)
            )

            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = animatedColor
            )
        }
    }
}

/**
 * Compact dot-only indicator
 */
@Composable
fun CompactConnectionDot(
    connectionState: WebSocketConnectionState,
    modifier: Modifier = Modifier
) {
    val (color, _) = getStatusColorAndText(connectionState)
    val isAnimated = connectionState is WebSocketConnectionState.Connected

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .scale(if (isAnimated) pulseScale else 1f)
            .background(color, CircleShape)
    )
}

/**
 * Full-width status bar for header
 */
@Composable
fun ConnectionStatusBar(
    connectionState: WebSocketConnectionState,
    modifier: Modifier = Modifier
) {
    val (color, text) = getStatusColorAndText(connectionState)
    val showBar = connectionState !is WebSocketConnectionState.Connected

    if (showBar) {
        Surface(
            modifier = modifier,
            color = color.copy(alpha = 0.15f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )

                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = color,
                    modifier = Modifier.padding(start = 8.dp)
                )

                if (connectionState is WebSocketConnectionState.Reconnecting) {
                    Text(
                        text = " (${connectionState.attempt}/${connectionState.maxAttempts})",
                        style = MaterialTheme.typography.bodySmall,
                        color = color.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

private fun getStatusColorAndText(state: WebSocketConnectionState): Pair<Color, String> {
    return when (state) {
        is WebSocketConnectionState.Idle -> Color(0xFF6B7280) to "Offline"
        is WebSocketConnectionState.Connecting -> Color(0xFFF59E0B) to "Connecting..."
        is WebSocketConnectionState.Connected -> Color(0xFF22C55E) to "Live"
        is WebSocketConnectionState.Disconnected -> Color(0xFF6B7280) to "Disconnected"
        is WebSocketConnectionState.Reconnecting -> Color(0xFFF59E0B) to "Reconnecting..."
        is WebSocketConnectionState.Error -> Color(0xFFEF4444) to "Error"
    }
}
