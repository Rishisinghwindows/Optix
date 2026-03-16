package com.optix.app.presentation.screens.aiinsights.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.optix.app.domain.model.ConfidenceLevel
import com.optix.app.domain.model.PersonalizedAITradeSuggestion
import com.optix.app.domain.model.TradeDirection
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Swipeable suggestion card with actions:
 * - Swipe right: Add to watchlist
 * - Swipe left: Create alert
 */
@Composable
fun SwipeableSuggestionCard(
    suggestion: PersonalizedAITradeSuggestion,
    modifier: Modifier = Modifier,
    isInWatchlist: Boolean = false,
    onAddToWatchlist: () -> Unit = {},
    onCreateAlert: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val density = LocalDensity.current
    val maxSwipeDistance = with(density) { 100.dp.toPx() }
    val swipeThreshold = maxSwipeDistance * 0.5f

    var offsetX by remember { mutableFloatStateOf(0f) }
    var isSwipingRight by remember { mutableStateOf(false) }
    var isSwipingLeft by remember { mutableStateOf(false) }

    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(durationMillis = 100),
        label = "swipe_offset"
    )

    // Calculate reveal progress (0 to 1)
    val revealProgress = (abs(animatedOffset) / maxSwipeDistance).coerceIn(0f, 1f)

    // Background colors
    val rightSwipeColor = Color(0xFF3B82F6) // Blue for watchlist
    val leftSwipeColor = Color(0xFFF59E0B) // Amber for alert

    val backgroundColor by animateColorAsState(
        targetValue = when {
            animatedOffset > 0 -> rightSwipeColor.copy(alpha = revealProgress * 0.3f)
            animatedOffset < 0 -> leftSwipeColor.copy(alpha = revealProgress * 0.3f)
            else -> Color.Transparent
        },
        label = "bg_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
    ) {
        // Background action indicators
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Add to Watchlist (revealed on right swipe)
            if (animatedOffset > 0) {
                SwipeActionIndicator(
                    icon = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    label = if (isInWatchlist) "In Watchlist" else "Watchlist",
                    color = rightSwipeColor,
                    alpha = revealProgress
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Right side - Create Alert (revealed on left swipe)
            if (animatedOffset < 0) {
                SwipeActionIndicator(
                    icon = Icons.Default.Notifications,
                    label = "Alert",
                    color = leftSwipeColor,
                    alpha = revealProgress
                )
            }
        }

        // Foreground card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .graphicsLayer {
                    // Subtle rotation during swipe
                    rotationZ = (animatedOffset / maxSwipeDistance) * 2f
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isSwipingRight = false
                            isSwipingLeft = false
                        },
                        onDragEnd = {
                            when {
                                offsetX > swipeThreshold -> {
                                    onAddToWatchlist()
                                }
                                offsetX < -swipeThreshold -> {
                                    onCreateAlert()
                                }
                            }
                            offsetX = 0f
                        },
                        onDragCancel = {
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount)
                                .coerceIn(-maxSwipeDistance, maxSwipeDistance)
                            isSwipingRight = offsetX > 0
                            isSwipingLeft = offsetX < 0
                        }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp + (revealProgress * 4).dp
            ),
            onClick = onClick
        ) {
            SwipeableCardContent(
                suggestion = suggestion,
                isInWatchlist = isInWatchlist
            )
        }
    }
}

@Composable
private fun SwipeActionIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SwipeableCardContent(
    suggestion: PersonalizedAITradeSuggestion,
    isInWatchlist: Boolean,
    modifier: Modifier = Modifier
) {
    val base = suggestion.baseSuggestion
    val isBuy = base.action == TradeDirection.BUY || base.action == TradeDirection.STRONG_BUY
    val isHold = base.action == TradeDirection.HOLD
    val directionColor = when {
        isHold -> Color(0xFFF59E0B) // Amber
        isBuy -> Color(0xFF22C55E) // Green
        else -> Color(0xFFEF4444) // Red
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Score and main info
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactScoreCircle(
                    score = suggestion.totalScore,
                    confidence = base.confidence
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isBuy)
                                Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = directionColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = base.actionText,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = directionColor
                        )
                    }
                    Text(
                        text = "${base.symbol} ${String.format("%.0f", base.strikePrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Right: Indicators
            Column(horizontalAlignment = Alignment.End) {
                if (isInWatchlist) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "In Watchlist",
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (suggestion.personalizationScore > 0) {
                    PersonalizationIndicator(boostScore = suggestion.personalizationScore)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Price row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            CompactPriceItem("Entry", "₹${String.format("%.1f", base.entryPrice)}")
            CompactPriceItem("Target", "₹${String.format("%.1f", base.targetPrice)}", Color(0xFF22C55E))
            CompactPriceItem("SL", "₹${String.format("%.1f", base.stopLoss)}", Color(0xFFEF4444))
            CompactPriceItem("R:R", "${String.format("%.1f", base.riskReward)}", MaterialTheme.colorScheme.primary)
        }

        // Swipe hint
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "← Alert | Watchlist →",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun CompactPriceItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = valueColor
        )
    }
}
