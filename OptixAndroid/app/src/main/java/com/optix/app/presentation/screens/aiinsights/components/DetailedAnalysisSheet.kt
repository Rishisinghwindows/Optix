package com.optix.app.presentation.screens.aiinsights.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.domain.model.ConfidenceLevel
import com.optix.app.domain.model.PersonalizedAITradeSuggestion
import com.optix.app.domain.model.TradeDirection
import com.optix.app.presentation.screens.aiinsights.AIExplanation
import kotlinx.coroutines.launch

// Colors
private val GreenPrimary = Color(0xFF00C805)
private val GreenLight = Color(0xFFDCFCE7)
private val RedPrimary = Color(0xFFFF3B30)
private val RedLight = Color(0xFFFEE2E2)
private val AmberPrimary = Color(0xFFF59E0B)
private val AmberLight = Color(0xFFFEF3C7)
private val PurplePrimary = Color(0xFFBF5AF2)
private val PinkPrimary = Color(0xFFFF375F)

/**
 * Modal bottom sheet showing detailed analysis for a suggestion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailedAnalysisSheet(
    suggestion: PersonalizedAITradeSuggestion,
    isInWatchlist: Boolean,
    onDismiss: () -> Unit,
    onAddToWatchlist: () -> Unit,
    onCreateAlert: () -> Unit,
    onTrade: () -> Unit,
    onAskAI: () -> Unit = {},
    isAskingAI: Boolean = false,
    aiExplanation: AIExplanation? = null,
    askAIError: String? = null,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val base = suggestion.baseSuggestion

    // Determine sentiment based on option type and action
    // BUY/STRONG_BUY CALL = Bullish (Green), SELL/STRONG_SELL CALL = Bearish (Red)
    // BUY/STRONG_BUY PUT = Bearish (Red), SELL/STRONG_SELL PUT = Bullish (Green)
    val isCall = base.optionType == com.optix.app.domain.model.OptionType.CALL
    val isBuy = base.action == TradeDirection.BUY || base.action == TradeDirection.STRONG_BUY
    val isSell = base.action == TradeDirection.SELL || base.action == TradeDirection.STRONG_SELL
    val isHold = base.action == TradeDirection.HOLD
    val isBullishTrade = (isCall && isBuy) || (!isCall && isSell)
    val directionColor = if (isHold) AmberPrimary else if (isBullishTrade) GreenPrimary else RedPrimary

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedScoreCircle(
                        score = suggestion.totalScore,
                        confidence = base.confidence,
                        size = 64.dp
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isBuy)
                                    Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = directionColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = base.actionText,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = directionColor
                            )
                        }
                        Text(
                            text = "${base.symbol} ${String.format("%.0f", base.strikePrice)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Expiry: ${base.expiry}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Personalization Badge
            if (suggestion.personalizationScore > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                PersonalizationBadge(
                    boostScore = suggestion.personalizationScore,
                    matchesWatchlist = suggestion.matchesWatchlist,
                    matchesRiskProfile = suggestion.matchesRiskProfile
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // Price Levels Section
            Text(
                text = "Price Levels",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))

            PriceLevelsRow(
                entryPrice = base.entryPrice,
                targetPrice = base.targetPrice,
                stopLoss = base.stopLoss,
                riskReward = base.riskReward,
                potentialReturn = base.potentialReturn
            )

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // Analysis Breakdown Section
            Text(
                text = "Analysis Breakdown",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(12.dp))

            base.reasoning.forEach { reason ->
                AnalysisFactorRow(
                    factor = reason.factor,
                    score = reason.score,
                    maxScore = reason.maxScore,
                    description = reason.description,
                    isPositive = reason.isPositive
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Payoff Chart
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Payoff Preview",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            SingleOptionPayoffChart(
                strikePrice = base.strikePrice,
                entryPrice = base.entryPrice,
                targetPrice = base.targetPrice,
                stopLoss = base.stopLoss,
                optionType = base.optionType,
                direction = base.action,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )

            // Relevance Reasons
            if (suggestion.relevanceReasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Why This Trade",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))

                suggestion.relevanceReasons.forEach { reason ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "•", color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Hedge Recommendation
            suggestion.hedgeRecommendation?.let { hedge ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "💡", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Hedge Suggestion",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = hedge,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Position Size
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Recommended Position: ${suggestion.recommendedPositionSize} lot(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Ask AI About This Trade Section
            AskAISection(
                onAskAI = onAskAI,
                isAskingAI = isAskingAI,
                aiExplanation = aiExplanation,
                askAIError = askAIError
            )

            Spacer(modifier = Modifier.height(12.dp))

            // SEBI Disclaimer
            Text(
                text = "⚠️ Not investment advice. AI analysis is for educational purposes only. Investment in securities market is subject to market risks. Consult a SEBI-registered advisor.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onAddToWatchlist,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isInWatchlist) "Saved" else "Watchlist")
                }

                OutlinedButton(
                    onClick = onCreateAlert,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Alert")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onTrade,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = directionColor
                )
            ) {
                Text(
                    text = "Paper Trade",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PriceLevelsRow(
    entryPrice: Double,
    targetPrice: Double,
    stopLoss: Double,
    riskReward: Double,
    potentialReturn: Double,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        PriceLevelItem("Entry", "₹${String.format("%.2f", entryPrice)}")
        PriceLevelItem("Target", "₹${String.format("%.2f", targetPrice)}", Color(0xFF22C55E))
        PriceLevelItem("Stop Loss", "₹${String.format("%.2f", stopLoss)}", Color(0xFFEF4444))
        PriceLevelItem("R:R", String.format("%.2f", riskReward), MaterialTheme.colorScheme.primary)
        PriceLevelItem("Return", "${String.format("%.1f", potentialReturn)}%",
            if (potentialReturn > 0) Color(0xFF22C55E) else Color(0xFFEF4444))
    }
}

@Composable
private fun PriceLevelItem(
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}

@Composable
private fun AnalysisFactorRow(
    factor: String,
    score: Int,
    maxScore: Int,
    description: String,
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
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = "$score/$maxScore",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = barColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.2f),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Ask AI About This Trade Section
 */
@Composable
private fun AskAISection(
    onAskAI: () -> Unit,
    isAskingAI: Boolean,
    aiExplanation: AIExplanation?,
    askAIError: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Ask AI Button (like iOS)
        Button(
            onClick = onAskAI,
            enabled = !isAskingAI && aiExplanation == null,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(PurplePrimary, PinkPrimary)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isAskingAI) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Analyzing with AI...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                } else if (aiExplanation != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Analysis Complete",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ask AI About This Trade",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Error message
        askAIError?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = RedPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // AI Explanation Result
        AnimatedVisibility(
            visible = aiExplanation != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            aiExplanation?.let { explanation ->
                Spacer(modifier = Modifier.height(16.dp))
                AIExplanationCard(explanation = explanation)
            }
        }
    }
}

/**
 * AI Explanation Result Card (like iOS)
 */
@Composable
private fun AIExplanationCard(explanation: AIExplanation) {
    val verdictColor = when (explanation.verdict.uppercase()) {
        "YES" -> GreenPrimary
        "NO" -> RedPrimary
        else -> AmberPrimary
    }
    val verdictBgColor = when (explanation.verdict.uppercase()) {
        "YES" -> GreenLight
        "NO" -> RedLight
        else -> AmberLight
    }
    val verdictIcon = when (explanation.verdict.uppercase()) {
        "YES" -> Icons.Default.CheckCircle
        "NO" -> Icons.Default.Cancel
        else -> Icons.Default.Schedule
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Verdict Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Verdict Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = verdictBgColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = verdictIcon,
                            contentDescription = null,
                            tint = verdictColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = explanation.verdict.uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = verdictColor
                        )
                    }
                }

                // Win Probability
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Win Probability",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${explanation.winProbability}%",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (explanation.winProbability >= 60) GreenPrimary
                               else if (explanation.winProbability <= 40) RedPrimary
                               else AmberPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // Key Reason
            Row(verticalAlignment = Alignment.Top) {
                Text(text = "💡", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Key Insight",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = explanation.keyReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Risk Warning
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Risk Warning",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = explanation.riskWarning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Better Alternative (if present)
            explanation.betterAlternative?.let { alternative ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(text = "✨", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Consider",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = alternative,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // AI Powered Badge
            if (explanation.aiPowered) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = PurplePrimary.copy(alpha = 0.1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "✨", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Powered by Gemini AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = PurplePrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
