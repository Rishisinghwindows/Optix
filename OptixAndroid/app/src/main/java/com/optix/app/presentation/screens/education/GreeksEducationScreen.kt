package com.optix.app.presentation.screens.education

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.domain.model.*
import com.optix.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GreeksEducationScreen(
    greek: GreekInfo,
    onBack: () -> Unit
) {
    val greekColor = Color(greek.colorHex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(greek.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card with Greek Symbol
            item {
                GreekHeaderCard(greek = greek, color = greekColor)
            }

            // What It Measures
            item {
                GreekDescriptionCard(
                    title = "What It Measures",
                    content = greek.fullDescription,
                    color = greekColor
                )
            }

            // Impact
            item {
                GreekImpactCard(
                    impact = greek.impact,
                    color = greekColor
                )
            }

            // Range
            item {
                GreekRangeCard(
                    range = greek.range,
                    color = greekColor
                )
            }

            // Example
            item {
                GreekExampleCard(
                    example = greek.example,
                    color = greekColor
                )
            }

            // Pro Tips
            item {
                GreekTipsCard(
                    tips = greek.tips,
                    color = greekColor
                )
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun GreekHeaderCard(greek: GreekInfo, color: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = color.copy(alpha = 0.3f),
                spotColor = color.copy(alpha = 0.3f)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Greek Symbol
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = color,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = greek.symbol,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = greek.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = greek.shortDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryDark
                )
            }
        }
    }
}

@Composable
private fun GreekDescriptionCard(title: String, content: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 24.sp,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun GreekImpactCard(impact: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Impact",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = impact,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 24.sp,
                    color = TextSecondaryDark
                )
            }
        }
    }
}

@Composable
private fun GreekRangeCard(range: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Range",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Text(
                    text = range,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun GreekExampleCard(example: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = WarningOrange.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = WarningOrange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Example",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WarningOrange
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WarningOrange.copy(alpha = 0.1f)
            ) {
                Text(
                    text = example,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    color = TextSecondaryDark
                )
            }
        }
    }
}

@Composable
private fun GreekTipsCard(tips: List<String>, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = AccentOrange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pro Tips",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            tips.forEach { tip ->
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "\u2022",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryDark
                    )
                }
            }
        }
    }
}

// Strategy Detail Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyDetailScreen(
    strategy: OptionStrategy,
    onBack: () -> Unit
) {
    val typeColor = when (strategy.type) {
        EducationStrategyType.BULLISH -> PrimaryGreen
        EducationStrategyType.BEARISH -> ErrorRed
        EducationStrategyType.NEUTRAL -> AccentBlue
        EducationStrategyType.VOLATILE -> WarningOrange
    }

    val riskColor = when (strategy.riskLevel) {
        EducationRiskLevel.LOW -> PrimaryGreen
        EducationRiskLevel.MEDIUM -> WarningOrange
        EducationRiskLevel.HIGH -> ErrorRed
        EducationRiskLevel.VERY_HIGH -> Color(0xFFFF0000)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strategy.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with tags
            item {
                StrategyHeaderSection(
                    strategy = strategy,
                    typeColor = typeColor,
                    riskColor = riskColor
                )
            }

            // Strategy Legs
            item {
                StrategyLegsCard(legs = strategy.legs)
            }

            // Risk/Reward
            item {
                RiskRewardCard(strategy = strategy)
            }

            // When To Use
            item {
                WhenToUseCard(
                    reasons = strategy.whenToUse,
                    color = AccentPurple
                )
            }

            // Pros & Cons
            item {
                ProsConsCard(pros = strategy.pros, cons = strategy.cons)
            }

            // Payoff Example
            if (strategy.example != null) {
                item {
                    PayoffExampleCard(example = strategy.example)
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun StrategyHeaderSection(
    strategy: OptionStrategy,
    typeColor: Color,
    riskColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Tags
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Type Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = typeColor
                ) {
                    Text(
                        text = strategy.type.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Outlook Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AccentPurple.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = strategy.outlook.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentPurple,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Risk Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = riskColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strategy.riskLevel.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = strategy.description,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 24.sp,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun StrategyLegsCard(legs: List<EducationStrategyLeg>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Strategy Legs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            legs.forEach { leg ->
                StrategyLegRow(leg = leg)
                if (leg != legs.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StrategyLegRow(leg: EducationStrategyLeg) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Action
            Text(
                text = leg.action,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (leg.action == "Buy") PrimaryGreen else ErrorRed,
                modifier = Modifier.width(40.dp)
            )
            // Quantity
            Text(
                text = "x${leg.quantity}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.width(12.dp))
            // Option Type
            Text(
                text = leg.optionType,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Strike
            Text(
                text = "@ ${leg.strike}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun RiskRewardCard(strategy: OptionStrategy) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Risk / Reward",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Max Profit
            RiskRewardRow(
                label = "Max Profit",
                value = strategy.maxProfit,
                icon = Icons.Default.TrendingUp,
                color = PrimaryGreen
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Max Loss
            RiskRewardRow(
                label = "Max Loss",
                value = strategy.maxLoss,
                icon = Icons.Default.TrendingDown,
                color = ErrorRed
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Breakeven
            RiskRewardRow(
                label = "Breakeven",
                value = strategy.breakeven,
                icon = Icons.Default.Balance,
                color = AccentBlue
            )
        }
    }
}

@Composable
private fun RiskRewardRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WhenToUseCard(reasons: List<String>, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "When To Use",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            reasons.forEach { reason ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = color
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryDark
                    )
                }
            }
        }
    }
}

@Composable
private fun ProsConsCard(pros: List<String>, cons: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pros
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = PrimaryGreen.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = PrimaryGreen
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pros",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGreen
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                pros.forEach { pro ->
                    Text(
                        text = "\u2022 $pro",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        // Cons
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = ErrorRed.copy(alpha = 0.1f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ThumbDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ErrorRed
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cons",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                cons.forEach { con ->
                    Text(
                        text = "\u2022 $con",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PayoffExampleCard(example: StrategyExample) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Payoff Example",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Setup Info
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.padding(10.dp)
                ) {
                    Text(
                        text = "Spot Price: Rs ${String.format("%,.0f", example.spotPrice)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                    Text(
                        text = "Net Cost: Rs ${String.format("%,.0f", kotlin.math.abs(example.netCost))} ${if (example.netCost < 0) "(Credit)" else "(Debit)"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (example.netCost < 0) PrimaryGreen else TextSecondaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scenarios
            example.scenarios.forEach { scenario ->
                ScenarioRow(scenario = scenario)
                if (scenario != example.scenarios.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ScenarioRow(scenario: ScenarioResult) {
    val isProfit = scenario.profit >= 0

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isProfit) PrimaryGreen.copy(alpha = 0.05f) else ErrorRed.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expiry Price
            Column {
                Text(
                    text = "Expiry At",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryDark
                )
                Text(
                    text = "Rs ${String.format("%,.0f", scenario.expiryPrice)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Description
            Text(
                text = scenario.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.weight(1f))

            // P&L
            Text(
                text = "${if (isProfit) "+" else ""}Rs ${String.format("%,.0f", scenario.profit)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isProfit) PrimaryGreen else ErrorRed
            )
        }
    }
}
