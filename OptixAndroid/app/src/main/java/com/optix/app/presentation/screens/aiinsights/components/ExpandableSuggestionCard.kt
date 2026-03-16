package com.optix.app.presentation.screens.aiinsights.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.domain.model.ConfidenceLevel
import com.optix.app.domain.model.PersonalizedAITradeSuggestion
import com.optix.app.domain.model.TradeDirection

/**
 * Expandable card showing AI trade suggestion with detailed analysis
 */
@Composable
fun ExpandableSuggestionCard(
    suggestion: PersonalizedAITradeSuggestion,
    modifier: Modifier = Modifier,
    onDetailClick: () -> Unit = {},
    onAddToWatchlist: () -> Unit = {},
    onCreateAlert: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "expand_arrow"
    )

    val base = suggestion.baseSuggestion
    val isBuy = base.action == TradeDirection.BUY || base.action == TradeDirection.STRONG_BUY
    val isHold = base.action == TradeDirection.HOLD
    val directionColor = when {
        isHold -> Color(0xFFF59E0B) // Amber
        isBuy -> Color(0xFF22C55E) // Green
        else -> Color(0xFFEF4444) // Red
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Score and Action
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedScoreCircle(
                        score = suggestion.totalScore,
                        confidence = base.confidence,
                        size = 52.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isBuy)
                                    Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = directionColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = base.actionText,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = directionColor
                            )
                        }

                        Text(
                            text = "${base.symbol} ${String.format("%.0f", base.strikePrice)} (${base.expiry})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Right side: Expand arrow
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Personalization badge
            if (suggestion.personalizationScore > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                PersonalizationBadge(
                    boostScore = suggestion.personalizationScore,
                    matchesWatchlist = suggestion.matchesWatchlist,
                    matchesRiskProfile = suggestion.matchesRiskProfile
                )
            }

            // Price Info Row
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceInfoItem(
                    label = "Entry",
                    value = "₹${String.format("%.2f", base.entryPrice)}",
                    color = MaterialTheme.colorScheme.onSurface
                )
                PriceInfoItem(
                    label = "Target",
                    value = "₹${String.format("%.2f", base.targetPrice)}",
                    color = Color(0xFF22C55E)
                )
                PriceInfoItem(
                    label = "Stop Loss",
                    value = "₹${String.format("%.2f", base.stopLoss)}",
                    color = Color(0xFFEF4444)
                )
                PriceInfoItem(
                    label = "R:R",
                    value = "${String.format("%.2f", base.riskReward)}",
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Expanded Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Reasoning Section
                    Text(
                        text = "Analysis Breakdown",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    base.reasoning.forEach { reason ->
                        ReasoningProgressBar(
                            factor = reason.factor,
                            score = reason.score,
                            maxScore = reason.maxScore,
                            isPositive = reason.isPositive
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Relevance Reasons
                    if (suggestion.relevanceReasons.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Why This Matters",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        RelevanceReasons(reasons = suggestion.relevanceReasons)
                    }

                    // Hedge Recommendation
                    suggestion.hedgeRecommendation?.let { hedge ->
                        Spacer(modifier = Modifier.height(12.dp))
                        HedgeRecommendationCard(recommendation = hedge)
                    }

                    // Position Size
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Recommended Position: ${suggestion.recommendedPositionSize} lot(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceInfoItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun ReasoningProgressBar(
    factor: String,
    score: Int,
    maxScore: Int,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val progress = if (maxScore > 0) score.toFloat() / maxScore else 0f
    val barColor = if (isPositive) Color(0xFF22C55E) else Color(0xFFEF4444)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = factor,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$score/$maxScore",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = barColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.2f),
        )
    }
}

@Composable
private fun HedgeRecommendationCard(
    recommendation: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "💡",
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = recommendation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
