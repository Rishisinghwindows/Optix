package com.optix.app.presentation.screens.aiinsights.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Badge showing personalization boost for a suggestion
 */
@Composable
fun PersonalizationBadge(
    boostScore: Int,
    matchesWatchlist: Boolean,
    matchesRiskProfile: Boolean,
    modifier: Modifier = Modifier
) {
    if (boostScore <= 0) return

    val badgeColor = when {
        boostScore >= 15 -> Color(0xFF8B5CF6) // Purple for high personalization
        boostScore >= 10 -> Color(0xFF3B82F6) // Blue for medium
        else -> Color(0xFF6B7280) // Gray for low
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = badgeColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Personalized",
                tint = badgeColor,
                modifier = Modifier.size(14.dp)
            )

            Text(
                text = "+$boostScore",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = badgeColor
            )

            if (matchesWatchlist) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "In Watchlist",
                    tint = badgeColor,
                    modifier = Modifier.size(12.dp)
                )
            }

            if (matchesRiskProfile) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Matches Profile",
                    tint = badgeColor,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/**
 * Compact personalization indicator
 */
@Composable
fun PersonalizationIndicator(
    boostScore: Int,
    modifier: Modifier = Modifier
) {
    if (boostScore <= 0) return

    val scale by animateFloatAsState(
        targetValue = if (boostScore >= 10) 1.1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    val color by animateColorAsState(
        targetValue = when {
            boostScore >= 15 -> Color(0xFF8B5CF6)
            boostScore >= 10 -> Color(0xFF3B82F6)
            else -> Color(0xFF6B7280)
        },
        label = "color"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .background(color.copy(alpha = 0.15f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.3f), CircleShape)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$boostScore",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            ),
            color = color
        )
    }
}

/**
 * Row of relevance reason chips
 */
@Composable
fun RelevanceReasons(
    reasons: List<String>,
    modifier: Modifier = Modifier,
    maxReasons: Int = 3
) {
    if (reasons.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reasons.take(maxReasons).forEach { reason ->
            ReasonChip(reason = reason)
        }

        if (reasons.size > maxReasons) {
            Text(
                text = "+${reasons.size - maxReasons}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun ReasonChip(
    reason: String,
    modifier: Modifier = Modifier
) {
    val (icon, color) = getReasonIconAndColor(reason)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )

            Text(
                text = reason,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = color,
                maxLines = 1
            )
        }
    }
}

private fun getReasonIconAndColor(reason: String): Pair<ImageVector, Color> {
    return when {
        reason.contains("watchlist", ignoreCase = true) ->
            Icons.Default.Bookmark to Color(0xFF3B82F6)
        reason.contains("profile", ignoreCase = true) ->
            Icons.Default.Person to Color(0xFF8B5CF6)
        reason.contains("profitable", ignoreCase = true) ->
            Icons.Default.TrendingUp to Color(0xFF22C55E)
        reason.contains("preferred", ignoreCase = true) ->
            Icons.Default.Star to Color(0xFFF59E0B)
        else ->
            Icons.Default.Star to Color(0xFF6B7280)
    }
}

/**
 * Summary card showing personalization status
 */
@Composable
fun PersonalizationSummaryCard(
    personalizedCount: Int,
    watchlistMatches: Int,
    profileMatches: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryItem(
                icon = Icons.Default.Star,
                count = personalizedCount,
                label = "Personalized"
            )

            SummaryItem(
                icon = Icons.Default.Bookmark,
                count = watchlistMatches,
                label = "Watchlist"
            )

            SummaryItem(
                icon = Icons.Default.Person,
                count = profileMatches,
                label = "Profile"
            )
        }
    }
}

@Composable
private fun SummaryItem(
    icon: ImageVector,
    count: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
