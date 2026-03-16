package com.optix.app.presentation.screens.strategy

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.core.calculation.StrategyCalculationEngine
import com.optix.app.domain.model.*
import com.optix.app.presentation.theme.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyDetailScreen(
    strategy: Strategy,
    onBack: () -> Unit
) {
    val payoffData = remember(strategy) {
        StrategyCalculationEngine.analyzeStrategy(strategy, 75) // Default lot size (NIFTY)
    }
    val marginRequired = remember(strategy) {
        StrategyCalculationEngine.estimateMargin(strategy, 75)
    }
    val pop = remember(payoffData) {
        StrategyCalculationEngine.calculatePOP(payoffData)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = strategy.type.displayName,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${strategy.symbol} - ${strategy.expiry}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Share */ }) {
                        Icon(Icons.Default.Share, "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Payoff Diagram
            PayoffDiagramCard(
                payoffData = payoffData,
                spotPrice = strategy.spotPrice
            )

            // Key Metrics
            KeyMetricsCard(
                payoffData = payoffData,
                marginRequired = marginRequired,
                pop = pop,
                strategy = strategy
            )

            // Breakeven Analysis
            BreakevenAnalysisCard(
                breakevens = payoffData.breakevens,
                spotPrice = strategy.spotPrice
            )

            // Greeks Analysis
            GreeksAnalysisCard(strategy = strategy)

            // Legs Breakdown
            LegsBreakdownCard(
                legs = strategy.legs,
                lotSize = 25
            )

            // Risk Profile
            RiskProfileCard(
                strategyType = strategy.type,
                payoffData = payoffData
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// MARK: - Payoff Diagram Card

@Composable
fun PayoffDiagramCard(
    payoffData: PayoffData,
    spotPrice: Double
) {
    var touchedPrice by remember { mutableStateOf<Double?>(null) }
    var touchedPayoff by remember { mutableStateOf<Double?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Payoff at Expiry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (touchedPrice != null && touchedPayoff != null) {
                    TouchedValueBadge(
                        price = touchedPrice!!,
                        payoff = touchedPayoff!!
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            DetailedPayoffChart(
                payoffData = payoffData,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LegendItem(PrimaryGreen, "Profit Zone")
                LegendItem(ErrorRed, "Loss Zone")
                LegendItem(WarningOrange, "Breakeven")
                LegendItem(AccentBlue, "Spot Price")
            }
        }
    }
}

@Composable
fun DetailedPayoffChart(
    payoffData: PayoffData,
    modifier: Modifier = Modifier
) {
    val points = payoffData.points
    if (points.isEmpty()) return

    val minX = points.minOf { it.spotPrice }
    val maxX = points.maxOf { it.spotPrice }
    val minY = points.minOf { it.payoff }
    val maxY = points.maxOf { it.payoff }
    val yRange = maxOf(abs(minY), abs(maxY)) * 1.2

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val xScale = width / (maxX - minX)
        val yScale = height / (yRange * 2)
        val centerY = height / 2

        // Grid lines
        val gridColor = Color.Gray.copy(alpha = 0.2f)
        val numHorizontalLines = 5
        for (i in 0..numHorizontalLines) {
            val y = height * i / numHorizontalLines
            drawLine(gridColor, Offset(0f, y), Offset(width, y), 1.dp.toPx())
        }

        // Zero line
        drawLine(
            Color.Gray.copy(alpha = 0.5f),
            Offset(0f, centerY),
            Offset(width, centerY),
            2.dp.toPx()
        )

        // Current spot line
        val spotX = ((payoffData.currentSpot - minX) * xScale).toFloat()
        drawLine(
            AccentBlue.copy(alpha = 0.7f),
            Offset(spotX, 0f),
            Offset(spotX, height),
            1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        )

        // Fill path
        val fillPath = Path()
        points.forEachIndexed { index, point ->
            val x = ((point.spotPrice - minX) * xScale).toFloat()
            val y = (centerY - point.payoff * yScale).toFloat().coerceIn(0f, height)

            if (index == 0) {
                fillPath.moveTo(x, centerY)
                fillPath.lineTo(x, y)
            } else {
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(((points.last().spotPrice - minX) * xScale).toFloat(), centerY)
        fillPath.close()

        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    PrimaryGreen.copy(alpha = 0.3f),
                    Color.Transparent,
                    ErrorRed.copy(alpha = 0.3f)
                ),
                startY = 0f,
                endY = height
            )
        )

        // Main payoff line
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = ((point.spotPrice - minX) * xScale).toFloat()
            val y = (centerY - point.payoff * yScale).toFloat().coerceIn(0f, height)

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path,
            color = PrimaryGreen,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Breakeven points
        payoffData.breakevens.forEach { be ->
            val beX = ((be - minX) * xScale).toFloat()
            drawCircle(
                color = WarningOrange,
                radius = 8.dp.toPx(),
                center = Offset(beX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = Offset(beX, centerY)
            )
        }
    }
}

// MARK: - Key Metrics Card

@Composable
fun KeyMetricsCard(
    payoffData: PayoffData,
    marginRequired: Double,
    pop: Double,
    strategy: Strategy
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Key Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailMetricCard(
                    title = "Max Profit",
                    value = if (payoffData.maxProfit >= 1_000_000) "Unlimited"
                    else formatCurrency(payoffData.maxProfit),
                    subtitle = if (payoffData.maxProfit < 1_000_000) "at expiry" else null,
                    color = PrimaryGreen,
                    icon = Icons.Filled.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                DetailMetricCard(
                    title = "Max Loss",
                    value = if (payoffData.maxLoss <= -1_000_000) "Unlimited"
                    else formatCurrency(abs(payoffData.maxLoss)),
                    subtitle = if (payoffData.maxLoss > -1_000_000) "at expiry" else null,
                    color = ErrorRed,
                    icon = Icons.Filled.TrendingDown,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailMetricCard(
                    title = "Risk/Reward",
                    value = if (payoffData.riskRewardRatio.isInfinite()) "N/A"
                    else String.format("1:%.2f", payoffData.riskRewardRatio),
                    subtitle = "ratio",
                    color = AccentBlue,
                    icon = Icons.Filled.Balance,
                    modifier = Modifier.weight(1f)
                )
                DetailMetricCard(
                    title = "P.O.P.",
                    value = "${(pop * 100).toInt()}%",
                    subtitle = "probability of profit",
                    color = AccentPurple,
                    icon = Icons.Filled.Percent,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val netPremium = strategy.netPremium * 75
                DetailMetricCard(
                    title = "Net Premium",
                    value = formatCurrency(abs(netPremium)),
                    subtitle = if (strategy.isDebit) "debit" else "credit",
                    color = if (strategy.isDebit) ErrorRed else PrimaryGreen,
                    icon = if (strategy.isDebit) Icons.Filled.RemoveCircle else Icons.Filled.AddCircle,
                    modifier = Modifier.weight(1f)
                )
                DetailMetricCard(
                    title = "Margin",
                    value = if (marginRequired > 0) formatCurrency(marginRequired) else "N/A",
                    subtitle = "required",
                    color = WarningOrange,
                    icon = Icons.Filled.AccountBalance,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun DetailMetricCard(
    title: String,
    value: String,
    subtitle: String?,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryDark
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryDark
                )
            }
        }
    }
}

// MARK: - Breakeven Analysis Card

@Composable
fun BreakevenAnalysisCard(
    breakevens: List<Double>,
    spotPrice: Double
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Breakeven Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (breakevens.isEmpty()) {
                Text(
                    text = "No breakeven points - strategy is always profitable or always at loss",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryDark
                )
            } else {
                breakevens.forEachIndexed { index, be ->
                    val distanceFromSpot = be - spotPrice
                    val percentDistance = (distanceFromSpot / spotPrice) * 100
                    val direction = if (distanceFromSpot > 0) "above" else "below"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(WarningOrange.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = WarningOrange
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = String.format("Rs%.0f", be),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format("%.1f%% %s spot", abs(percentDistance), direction),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondaryDark
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (distanceFromSpot > 0) PrimaryGreen.copy(alpha = 0.1f)
                            else ErrorRed.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = String.format("%+.0f pts", distanceFromSpot),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (distanceFromSpot > 0) PrimaryGreen else ErrorRed
                            )
                        }
                    }

                    if (index < breakevens.size - 1) {
                        HorizontalDivider(color = BorderDark, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

// MARK: - Greeks Analysis Card

@Composable
fun GreeksAnalysisCard(strategy: Strategy) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Greeks Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Greeks Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GreekDetailCard(
                    symbol = "Delta",
                    greekLetter = "Delta",
                    value = strategy.netDelta,
                    format = "%.2f",
                    description = "Price sensitivity",
                    interpretation = when {
                        strategy.netDelta > 10 -> "Bullish bias"
                        strategy.netDelta < -10 -> "Bearish bias"
                        else -> "Neutral"
                    },
                    modifier = Modifier.weight(1f)
                )
                GreekDetailCard(
                    symbol = "Gamma",
                    greekLetter = "Gamma",
                    value = strategy.netGamma,
                    format = "%.4f",
                    description = "Delta acceleration",
                    interpretation = if (strategy.netGamma > 0) "Increases with move" else "Decreases with move",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GreekDetailCard(
                    symbol = "Theta",
                    greekLetter = "Theta",
                    value = strategy.netTheta,
                    format = "%.2f",
                    description = "Daily time decay",
                    interpretation = if (strategy.netTheta > 0) "Time helps" else "Time hurts",
                    modifier = Modifier.weight(1f)
                )
                GreekDetailCard(
                    symbol = "Vega",
                    greekLetter = "Vega",
                    value = strategy.netVega,
                    format = "%.2f",
                    description = "IV sensitivity",
                    interpretation = if (strategy.netVega > 0) "Wants higher IV" else "Wants lower IV",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun GreekDetailCard(
    symbol: String,
    greekLetter: String,
    value: Double,
    format: String,
    description: String,
    interpretation: String,
    modifier: Modifier = Modifier
) {
    val valueColor = when {
        symbol == "Delta" && abs(value) > 10 -> if (value > 0) PrimaryGreen else ErrorRed
        symbol == "Theta" -> if (value > 0) PrimaryGreen else ErrorRed
        symbol == "Gamma" -> if (value > 0) PrimaryGreen else ErrorRed
        symbol == "Vega" -> AccentPurple
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = greekLetter,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = AccentBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = String.format(format, value),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = valueColor.copy(alpha = 0.1f)
            ) {
                Text(
                    text = interpretation,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = valueColor
                )
            }
        }
    }
}

// MARK: - Legs Breakdown Card

@Composable
fun LegsBreakdownCard(
    legs: List<StrategyLeg>,
    lotSize: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Legs Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            legs.forEachIndexed { index, leg ->
                LegBreakdownRow(leg = leg, lotSize = lotSize)
                if (index < legs.size - 1) {
                    HorizontalDivider(
                        color = BorderDark,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = BorderDark)
            Spacer(modifier = Modifier.height(12.dp))

            // Total Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Net Position",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                val totalPremium = legs.sumOf { leg ->
                    if (leg.isLong) -leg.premium * leg.quantity else leg.premium * leg.quantity
                } * lotSize
                val isDebit = totalPremium < 0
                Text(
                    text = "${if (isDebit) "Debit" else "Credit"}: ${formatCurrency(abs(totalPremium))}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDebit) ErrorRed else PrimaryGreen
                )
            }
        }
    }
}

@Composable
fun LegBreakdownRow(leg: StrategyLeg, lotSize: Int) {
    val accentColor = if (leg.isLong) PrimaryGreen else ErrorRed

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Position Badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = accentColor
        ) {
            Text(
                text = if (leg.isLong) "BUY" else "SELL",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Quantity
        Text(
            text = "${leg.quantity}x",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Strike and Type
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${leg.strikePrice.toLong()} ${if (leg.optionType == OptionType.CALL) "CE" else "PE"}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "@ Rs${String.format("%.2f", leg.premium)} per lot",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryDark
            )
        }

        // Total Premium
        val totalPremium = leg.premium * leg.quantity * lotSize
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatCurrency(totalPremium),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = if (leg.isLong) "paid" else "received",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryDark
            )
        }
    }
}

// MARK: - Risk Profile Card

@Composable
fun RiskProfileCard(
    strategyType: StrategyType,
    payoffData: PayoffData
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Risk Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Max Risk
                RiskProfileItem(
                    title = "Max Risk",
                    value = if (payoffData.isLimitedRisk) "Limited" else "Unlimited",
                    color = if (payoffData.isLimitedRisk) PrimaryGreen else ErrorRed,
                    modifier = Modifier.weight(1f)
                )
                // Max Reward
                RiskProfileItem(
                    title = "Max Reward",
                    value = if (payoffData.isLimitedProfit) "Limited" else "Unlimited",
                    color = if (!payoffData.isLimitedProfit) PrimaryGreen else WarningOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Outlook
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = strategyType.outlookColor.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = strategyType.icon,
                        contentDescription = null,
                        tint = strategyType.outlookColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Market Outlook",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryDark
                        )
                        Text(
                            text = strategyType.outlook,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = strategyType.outlookColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Strategy Description
            Text(
                text = strategyType.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
fun RiskProfileItem(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

// MARK: - Helper Functions

private fun formatCurrency(value: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }
    return formatter.format(value)
}
