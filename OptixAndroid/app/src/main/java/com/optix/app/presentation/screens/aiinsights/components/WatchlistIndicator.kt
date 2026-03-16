package com.optix.app.presentation.screens.aiinsights.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Star-style watchlist indicator with animation
 */
@Composable
fun WatchlistIndicator(
    isInWatchlist: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    activeColor: Color = Color(0xFFF59E0B), // Amber
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
) {
    val scale by animateFloatAsState(
        targetValue = if (isInWatchlist) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "watchlist_scale"
    )

    val color by animateColorAsState(
        targetValue = if (isInWatchlist) activeColor else inactiveColor,
        label = "watchlist_color"
    )

    Icon(
        imageVector = if (isInWatchlist) Icons.Default.Star else Icons.Default.StarBorder,
        contentDescription = if (isInWatchlist) "Remove from watchlist" else "Add to watchlist",
        tint = color,
        modifier = modifier
            .size(size)
            .scale(scale)
            .clickable { onClick() }
    )
}

/**
 * Bookmark-style watchlist indicator
 */
@Composable
fun BookmarkIndicator(
    isInWatchlist: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    activeColor: Color = Color(0xFF3B82F6), // Blue
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
) {
    val scale by animateFloatAsState(
        targetValue = if (isInWatchlist) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bookmark_scale"
    )

    val color by animateColorAsState(
        targetValue = if (isInWatchlist) activeColor else inactiveColor,
        label = "bookmark_color"
    )

    Icon(
        imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
        contentDescription = if (isInWatchlist) "Remove from watchlist" else "Add to watchlist",
        tint = color,
        modifier = modifier
            .size(size)
            .scale(scale)
            .clickable { onClick() }
    )
}

/**
 * Compact watchlist dot indicator (no click)
 */
@Composable
fun WatchlistDot(
    isInWatchlist: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    color: Color = Color(0xFF3B82F6)
) {
    if (isInWatchlist) {
        androidx.compose.foundation.Canvas(modifier = modifier.size(size)) {
            drawCircle(color = color)
        }
    }
}
