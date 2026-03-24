package com.optix.app.presentation.screens.aiinsights.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.domain.model.PickOutcome
import com.optix.app.domain.model.ScorecardStats
import com.optix.app.domain.model.TrackedPick

private val Profit = Color(0xFF00C805)
private val Loss = Color(0xFFFF3B30)
private val AccentBlue = Color(0xFF007AFF)
private val AccentGray = Color(0xFF8E8E93)
private val CardBg = Color(0xFF1C1C1E)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF8E8E93)
private val TextMuted = Color(0xFF636366)

@Composable
fun AIScorecardCard(stats: ScorecardStats) {
    var showAllPicks by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.TrackChanges,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "AI Scorecard",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "${stats.totalPicks} picks tracked",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            if (stats.totalPicks == 0) {
                Text(
                    "Scorecard will populate as AI suggestions are generated.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatPill("Win Rate", String.format("%.0f%%", stats.winRate), if (stats.winRate >= 50) Profit else Loss)
                    StatPill("Avg Return", String.format("%+.1f%%", stats.avgReturn), if (stats.avgReturn >= 0) Profit else Loss)
                    StatPill("Top Pick WR", String.format("%.0f%%", stats.topPickWinRate), if (stats.topPickWinRate >= 50) Profit else Loss)
                }

                // Outcome Bar
                val resolved = stats.wins + stats.losses + stats.expired
                if (resolved > 0) {
                    OutcomeBar(stats.wins, stats.losses, stats.expired)
                }

                // Active count
                if (stats.activePicks > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentBlue)
                        )
                        Text(
                            "${stats.activePicks} active picks being tracked",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                // Recent Picks
                if (stats.recentPicks.isNotEmpty()) {
                    HorizontalDivider(color = TextMuted.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Picks", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (stats.recentPicks.size > 5) {
                            TextButton(onClick = { showAllPicks = !showAllPicks }) {
                                Text(
                                    if (showAllPicks) "Show Less" else "Show All",
                                    color = AccentBlue,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    val picksToShow = if (showAllPicks) stats.recentPicks else stats.recentPicks.take(5)
                    picksToShow.forEach { pick ->
                        RecentPickRow(pick)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun OutcomeBar(wins: Int, losses: Int, expired: Int) {
    val total = wins + losses + expired
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            if (wins > 0) {
                Box(
                    modifier = Modifier
                        .weight(wins.toFloat() / total)
                        .fillMaxHeight()
                        .background(Profit, RoundedCornerShape(3.dp))
                )
            }
            if (losses > 0) {
                Box(
                    modifier = Modifier
                        .weight(losses.toFloat() / total)
                        .fillMaxHeight()
                        .background(Loss, RoundedCornerShape(3.dp))
                )
            }
            if (expired > 0) {
                Box(
                    modifier = Modifier
                        .weight(expired.toFloat() / total)
                        .fillMaxHeight()
                        .background(AccentGray, RoundedCornerShape(3.dp))
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${wins}W", color = Profit, fontSize = 10.sp)
            Text("${losses}L", color = Loss, fontSize = 10.sp)
            Text("${expired}E", color = AccentGray, fontSize = 10.sp)
        }
    }
}

@Composable
private fun RecentPickRow(pick: TrackedPick) {
    val outcomeColor = when (pick.outcome) {
        PickOutcome.ACTIVE -> AccentBlue
        PickOutcome.WIN -> Profit
        PickOutcome.LOSS -> Loss
        PickOutcome.EXPIRED -> AccentGray
    }
    val outcomeIcon = when (pick.outcome) {
        PickOutcome.ACTIVE -> Icons.Default.Schedule
        PickOutcome.WIN -> Icons.Default.CheckCircle
        PickOutcome.LOSS -> Icons.Default.Cancel
        PickOutcome.EXPIRED -> Icons.Default.EventBusy
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(outcomeIcon, contentDescription = null, tint = outcomeColor, modifier = Modifier.size(14.dp))
            Text(
                "${pick.indexName} ${pick.strikePrice.toInt()} ${pick.optionType}",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pick.returnPct != null) {
                Text(
                    String.format("%+.1f%%", pick.returnPct),
                    color = if ((pick.returnPct ?: 0.0) >= 0) Profit else Loss,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text("Active", color = AccentBlue, fontSize = 11.sp)
            }
            Text("S:${pick.score}", color = TextMuted, fontSize = 10.sp)
        }
    }
}
