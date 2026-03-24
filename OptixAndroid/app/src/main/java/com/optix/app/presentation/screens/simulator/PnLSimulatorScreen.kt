package com.optix.app.presentation.screens.simulator

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.core.util.AnalyticsHelper
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.PayoffPoint
import com.optix.app.presentation.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PnLSimulatorScreen(
    onBack: () -> Unit,
    viewModel: PnLSimulatorViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { AnalyticsHelper.logScreenView("pnl_simulator") }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "P&L Simulator",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddLeg() },
                containerColor = AccentBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Add Leg")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
        ) {
            // Legs summary
            if (state.legs.isNotEmpty()) {
                item { LegsSummarySection(state.legs, onRemove = { viewModel.removeLeg(it) }) }
            } else {
                item { EmptyLegsCard(onAdd = { viewModel.showAddLeg() }) }
            }

            // Sliders
            if (state.legs.isNotEmpty()) {
                item {
                    SlidersCard(
                        spotChangePercent = state.spotChangePercent,
                        daysElapsed = state.daysElapsed,
                        ivChangePercent = state.ivChangePercent,
                        maxDTE = state.maxDTE,
                        onSpotChange = viewModel::setSpotChangePercent,
                        onDaysChange = viewModel::setDaysElapsed,
                        onIVChange = viewModel::setIVChangePercent
                    )
                }

                // Total P&L
                item {
                    TotalPnLCard(
                        totalPnl = state.totalPnl,
                        totalPnlPercent = state.totalPnlPercent
                    )
                }

                // Key metrics
                item {
                    KeyMetricsRow(
                        breakevens = state.breakevens,
                        maxProfit = state.maxProfit,
                        maxLoss = state.maxLoss
                    )
                }

                // Payoff chart
                if (state.payoffPoints.isNotEmpty()) {
                    item {
                        PayoffChartCard(points = state.payoffPoints)
                    }
                }

                // Per-leg breakdown
                if (state.legResults.isNotEmpty()) {
                    item {
                        Text(
                            "Per-Leg Breakdown",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    itemsIndexed(state.legResults) { _, result ->
                        LegResultCard(result)
                    }
                }

                // Reset button
                item {
                    OutlinedButton(
                        onClick = { viewModel.resetSliders() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AccentBlue
                        )
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Reset Sliders", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Add Leg Bottom Sheet
    if (state.showAddLegSheet) {
        AddLegBottomSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { viewModel.hideAddLeg() }
        )
    }
}

// ──────────────────────────────────────────────
// Empty state
// ──────────────────────────────────────────────

@Composable
private fun EmptyLegsCard(onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No option legs added",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Add call/put legs to simulate P&L across different scenarios",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Leg")
            }
        }
    }
}

// ──────────────────────────────────────────────
// Legs summary
// ──────────────────────────────────────────────

@Composable
private fun LegsSummarySection(
    legs: List<SimulationLeg>,
    onRemove: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Option Legs (${legs.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            legs.forEach { leg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Direction badge
                    val dirColor = if (leg.position == LegDirection.BUY) Profit else Loss
                    Text(
                        text = if (leg.position == LegDirection.BUY) "BUY" else "SELL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(dirColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.width(6.dp))

                    // Type badge
                    val typeColor = if (leg.optionType == OptionType.CALL) Profit else Loss
                    Text(
                        text = leg.optionType.code,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = String.format("%.0f", leg.strikePrice),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "@ ${String.format("%.2f", leg.entryPremium)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${leg.quantity}x${leg.lotSize}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.weight(1f))

                    IconButton(
                        onClick = { onRemove(leg.id) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Remove",
                            tint = Loss,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────
// Sliders card
// ──────────────────────────────────────────────

@Composable
private fun SlidersCard(
    spotChangePercent: Float,
    daysElapsed: Float,
    ivChangePercent: Float,
    maxDTE: Int,
    onSpotChange: (Float) -> Unit,
    onDaysChange: (Float) -> Unit,
    onIVChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SimulatorSlider(
                label = "Spot Change",
                value = spotChangePercent,
                range = -10f..10f,
                steps = 199,
                unit = "%",
                format = "%.1f",
                accentColor = AccentBlue,
                onValueChange = onSpotChange
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            SimulatorSlider(
                label = "Days Elapsed",
                value = daysElapsed,
                range = 0f..maxOf(maxDTE.toFloat(), 1f),
                steps = maxOf(maxDTE - 1, 0),
                unit = "d",
                format = "%.0f",
                accentColor = AccentOrange,
                onValueChange = onDaysChange
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            SimulatorSlider(
                label = "IV Change",
                value = ivChangePercent,
                range = -30f..30f,
                steps = 119,
                unit = "%",
                format = "%.1f",
                accentColor = AccentPurple,
                onValueChange = onIVChange
            )
        }
    }
}

@Composable
private fun SimulatorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    unit: String,
    format: String,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    val displayColor by animateColorAsState(
        when {
            value == 0f -> MaterialTheme.colorScheme.onSurface
            value > 0 -> Profit
            else -> Loss
        },
        label = "sliderColor"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${String.format(format, value)}$unit",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = displayColor
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.2f)
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${String.format(format, range.start)}$unit",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                "${String.format(format, range.endInclusive)}$unit",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ──────────────────────────────────────────────
// Total P&L card
// ──────────────────────────────────────────────

@Composable
private fun TotalPnLCard(totalPnl: Double, totalPnlPercent: Double) {
    val isProfit = totalPnl >= 0
    val mainColor = if (isProfit) Profit else Loss

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = mainColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Total P&L",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatCurrency(totalPnl),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = mainColor
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = String.format("%s%.1f%%", if (totalPnlPercent >= 0) "+" else "", totalPnlPercent),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = mainColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(mainColor.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

// ──────────────────────────────────────────────
// Key metrics row
// ──────────────────────────────────────────────

@Composable
private fun KeyMetricsRow(
    breakevens: List<Double>,
    maxProfit: Double,
    maxLoss: Double
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "Breakeven",
            value = if (breakevens.isEmpty()) "N/A"
            else breakevens.joinToString(", ") { String.format("%.0f", it) },
            color = AccentBlue
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "Max Profit",
            value = formatCurrencyCompact(maxProfit),
            color = Profit
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "Max Loss",
            value = formatCurrencyCompact(maxLoss),
            color = Loss
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// ──────────────────────────────────────────────
// Payoff chart (Canvas-based)
// ──────────────────────────────────────────────

@Composable
private fun PayoffChartCard(points: List<PayoffPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ShowChart,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Payoff Diagram",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(12.dp))

            PayoffCanvas(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            // X-axis labels
            if (points.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        String.format("%.0f", points.first().spotPrice),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        "Spot Price",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        String.format("%.0f", points.last().spotPrice),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PayoffCanvas(
    points: List<PayoffPoint>,
    modifier: Modifier = Modifier
) {
    val profitColor = Profit
    val lossColor = Loss
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas

        val minX = points.minOf { it.spotPrice }
        val maxX = points.maxOf { it.spotPrice }
        val minY = points.minOf { it.payoff }
        val maxY = points.maxOf { it.payoff }
        val rangeX = maxX - minX
        val rangeY = (maxY - minY).let { if (it == 0.0) 1.0 else it }

        val paddingH = 8.dp.toPx()
        val chartWidth = size.width - paddingH * 2
        val chartHeight = size.height

        fun mapX(v: Double) = paddingH + ((v - minX) / rangeX * chartWidth).toFloat()
        fun mapY(v: Double) = (chartHeight - ((v - minY) / rangeY * chartHeight)).toFloat()

        val zeroY = mapY(0.0)

        // Grid: zero line
        drawLine(
            color = gridColor,
            start = androidx.compose.ui.geometry.Offset(paddingH, zeroY),
            end = androidx.compose.ui.geometry.Offset(size.width - paddingH, zeroY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(8f, 4f)
            )
        )

        // Profit area fill
        val profitPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(mapX(points.first().spotPrice.toDouble()), zeroY)
            for (p in points) {
                val py = mapY(maxOf(p.payoff, 0.0))
                lineTo(mapX(p.spotPrice.toDouble()), py)
            }
            lineTo(mapX(points.last().spotPrice.toDouble()), zeroY)
            close()
        }
        drawPath(
            profitPath,
            brush = Brush.verticalGradient(
                colors = listOf(profitColor.copy(alpha = 0.25f), profitColor.copy(alpha = 0.03f)),
                startY = 0f,
                endY = zeroY
            )
        )

        // Loss area fill
        val lossPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(mapX(points.first().spotPrice.toDouble()), zeroY)
            for (p in points) {
                val py = mapY(minOf(p.payoff, 0.0))
                lineTo(mapX(p.spotPrice.toDouble()), py)
            }
            lineTo(mapX(points.last().spotPrice.toDouble()), zeroY)
            close()
        }
        drawPath(
            lossPath,
            brush = Brush.verticalGradient(
                colors = listOf(lossColor.copy(alpha = 0.03f), lossColor.copy(alpha = 0.25f)),
                startY = zeroY,
                endY = chartHeight
            )
        )

        // Main P&L line
        val linePath = androidx.compose.ui.graphics.Path().apply {
            val first = points.first()
            moveTo(mapX(first.spotPrice.toDouble()), mapY(first.payoff))
            for (i in 1 until points.size) {
                val p = points[i]
                lineTo(mapX(p.spotPrice.toDouble()), mapY(p.payoff))
            }
        }
        drawPath(
            linePath,
            color = AccentBlue,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
        )
    }
}

// ──────────────────────────────────────────────
// Per-leg result card
// ──────────────────────────────────────────────

@Composable
private fun LegResultCard(result: LegSimResult) {
    val isProfit = result.pnl >= 0
    val pnlColor = if (isProfit) Profit else Loss

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    String.format("%.0f", result.leg.strikePrice),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))

                val typeColor = if (result.leg.optionType == OptionType.CALL) Profit else Loss
                Text(
                    result.leg.optionType.code,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(typeColor)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(6.dp))

                val dirText = if (result.leg.position == LegDirection.BUY) "BUY" else "SELL"
                Text(
                    dirText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))

                Text(
                    "${result.leg.quantity}x${result.leg.lotSize}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        String.format("%s%.2f", if (result.pnl >= 0) "+" else "", result.pnl),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = pnlColor
                    )
                    Text(
                        String.format("%s%.1f%%", if (result.pnlPercent >= 0) "+" else "", result.pnlPercent),
                        fontSize = 11.sp,
                        color = pnlColor
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Price transition
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format("%.2f", result.leg.entryPremium),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    String.format("%.2f", result.newPrice),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = pnlColor
                )
            }

            Spacer(Modifier.height(8.dp))

            // Greeks row
            Row(modifier = Modifier.fillMaxWidth()) {
                GreekItem(Modifier.weight(1f), "Delta", String.format("%.3f", result.newDelta))
                GreekItem(Modifier.weight(1f), "Gamma", String.format("%.5f", result.newGamma))
                GreekItem(Modifier.weight(1f), "Theta", String.format("%.2f", result.newTheta))
                GreekItem(Modifier.weight(1f), "Vega", String.format("%.2f", result.newVega))
            }
        }
    }
}

@Composable
private fun GreekItem(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ──────────────────────────────────────────────
// Add Leg Bottom Sheet
// ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddLegBottomSheet(
    state: PnLSimulatorState,
    viewModel: PnLSimulatorViewModel,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Add Option Leg",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))

            // Option Type toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(
                    modifier = Modifier.weight(1f),
                    label = "CALL",
                    selected = state.formOptionType == OptionType.CALL,
                    selectedColor = Profit,
                    onClick = { viewModel.updateFormOptionType(OptionType.CALL) }
                )
                ToggleChip(
                    modifier = Modifier.weight(1f),
                    label = "PUT",
                    selected = state.formOptionType == OptionType.PUT,
                    selectedColor = Loss,
                    onClick = { viewModel.updateFormOptionType(OptionType.PUT) }
                )
            }
            Spacer(Modifier.height(12.dp))

            // Direction toggle
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(
                    modifier = Modifier.weight(1f),
                    label = "BUY",
                    selected = state.formDirection == LegDirection.BUY,
                    selectedColor = Profit,
                    onClick = { viewModel.updateFormDirection(LegDirection.BUY) }
                )
                ToggleChip(
                    modifier = Modifier.weight(1f),
                    label = "SELL",
                    selected = state.formDirection == LegDirection.SELL,
                    selectedColor = Loss,
                    onClick = { viewModel.updateFormDirection(LegDirection.SELL) }
                )
            }
            Spacer(Modifier.height(16.dp))

            // Numeric inputs (2 per row)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(Modifier.weight(1f), "Spot Price", state.formSpotPrice, viewModel::updateFormSpotPrice)
                FormField(Modifier.weight(1f), "Strike Price", state.formStrike, viewModel::updateFormStrike)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(Modifier.weight(1f), "Premium (LTP)", state.formPremium, viewModel::updateFormPremium)
                FormField(Modifier.weight(1f), "IV (%)", state.formIV, viewModel::updateFormIV)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(Modifier.weight(1f), "Days to Expiry", state.formDTE, viewModel::updateFormDTE)
                FormField(Modifier.weight(1f), "Lot Size", state.formLotSize, viewModel::updateFormLotSize)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(Modifier.weight(1f), "Quantity (lots)", state.formQuantity, viewModel::updateFormQuantity)
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { viewModel.confirmAddLeg() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                enabled = state.formStrike.toDoubleOrNull() != null && state.formPremium.toDoubleOrNull() != null
            ) {
                Text("Add Leg", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ToggleChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val bg = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = fg)
    }
}

@Composable
private fun FormField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(10.dp),
        textStyle = LocalTextStyle.current.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    )
}

// ──────────────────────────────────────────────
// Utility functions
// ──────────────────────────────────────────────

private fun formatCurrency(value: Double): String {
    val sign = if (value >= 0) "+" else ""
    val absValue = abs(value)
    return when {
        absValue >= 100_000 -> "${sign}${String.format("%.1f", value / 100_000)}L"
        absValue >= 1_000 -> "${sign}${String.format("%.1f", value / 1_000)}K"
        else -> "${sign}${String.format("%.2f", value)}"
    }
}

private fun formatCurrencyCompact(value: Double): String {
    val absValue = abs(value)
    return when {
        value == Double.NEGATIVE_INFINITY || value == Double.POSITIVE_INFINITY -> "Unlimited"
        absValue >= 100_000 -> String.format("%.1fL", value / 100_000)
        absValue >= 1_000 -> String.format("%.1fK", value / 1_000)
        else -> String.format("%.0f", value)
    }
}
