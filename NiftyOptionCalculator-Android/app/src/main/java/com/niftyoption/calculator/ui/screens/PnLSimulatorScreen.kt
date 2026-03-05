package com.niftyoption.calculator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niftyoption.calculator.data.models.LegSimResult
import com.niftyoption.calculator.data.models.OptionType
import com.niftyoption.calculator.data.models.PayoffPoint
import com.niftyoption.calculator.data.models.SimulationInput
import com.niftyoption.calculator.ui.theme.AppColors
import com.niftyoption.calculator.ui.viewmodels.PnLSimulatorViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PnLSimulatorScreen(
    viewModel: PnLSimulatorViewModel,
    onBack: () -> Unit
) {
    val legs by viewModel.legs.collectAsState()
    val spotChangePercent by viewModel.spotChangePercent.collectAsState()
    val daysElapsed by viewModel.daysElapsed.collectAsState()
    val ivChangePercent by viewModel.ivChangePercent.collectAsState()
    val result by viewModel.result.collectAsState()
    val maxDTE by viewModel.maxDTE.collectAsState()

    var showAddLegDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("P&L Simulator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = AppColors.AccentBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.SurfaceDark
                )
            )
        },
        containerColor = AppColors.BackgroundDark
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Add Leg section
            item {
                if (showAddLegDialog) {
                    AddLegCard(
                        onAdd = { leg ->
                            viewModel.addLeg(leg)
                            showAddLegDialog = false
                        },
                        onCancel = { showAddLegDialog = false }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Legs (${legs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.addDefaultLeg() },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = AppColors.AccentBlue
                                )
                            ) {
                                Text("Quick Add")
                            }
                            Button(
                                onClick = { showAddLegDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.AccentBlue
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Leg")
                            }
                        }
                    }
                }
            }

            // Leg cards
            itemsIndexed(legs) { index, leg ->
                LegCard(
                    index = index,
                    leg = leg,
                    onRemove = { viewModel.removeLeg(index) }
                )
            }

            // Only show sliders and results if there are legs
            if (legs.isNotEmpty()) {
                // Sliders section
                item {
                    SlidersSection(
                        spotChangePercent = spotChangePercent,
                        daysElapsed = daysElapsed,
                        ivChangePercent = ivChangePercent,
                        maxDTE = maxDTE,
                        onSpotChange = { viewModel.updateSpotChange(it) },
                        onDaysChange = { viewModel.updateDaysElapsed(it) },
                        onIVChange = { viewModel.updateIVChange(it) }
                    )
                }

                // Total P&L card
                result?.let { simResult ->
                    item {
                        TotalPnLCard(
                            totalPnl = simResult.totalPnl,
                            totalPnlPercent = simResult.totalPnlPercent
                        )
                    }

                    // Payoff chart
                    if (simResult.payoffPoints.isNotEmpty()) {
                        item {
                            PayoffChart(
                                points = simResult.payoffPoints
                            )
                        }
                    }

                    // Per-leg breakdown
                    item {
                        Text(
                            text = "Leg Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            color = AppColors.TextPrimary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    itemsIndexed(simResult.legResults) { index, legResult ->
                        LegResultCard(index = index, legResult = legResult)
                    }
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Add a leg to start simulating.\nTap 'Quick Add' for a default NIFTY ATM Call.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun AddLegCard(
    onAdd: (SimulationInput) -> Unit,
    onCancel: () -> Unit
) {
    var spotPrice by remember { mutableStateOf("25000") }
    var strikePrice by remember { mutableStateOf("25000") }
    var optionType by remember { mutableStateOf(OptionType.CALL) }
    var entryPremium by remember { mutableStateOf("200") }
    var iv by remember { mutableStateOf("15") }
    var daysToExpiry by remember { mutableStateOf("7") }
    var lotSize by remember { mutableStateOf("75") }
    var quantity by remember { mutableStateOf("1") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Add New Leg",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary
            )

            // Option Type selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.CardDark)
            ) {
                OptionType.entries.forEach { type ->
                    val isSelected = type == optionType
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                when {
                                    isSelected && type == OptionType.CALL -> AppColors.AccentGreen
                                    isSelected && type == OptionType.PUT -> AppColors.AccentRed
                                    else -> Color.Transparent
                                }
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type.displayName.uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else AppColors.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SimTextField(
                    label = "Spot Price",
                    value = spotPrice,
                    onValueChange = { spotPrice = it },
                    modifier = Modifier.weight(1f)
                )
                SimTextField(
                    label = "Strike Price",
                    value = strikePrice,
                    onValueChange = { strikePrice = it },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SimTextField(
                    label = "Entry Premium",
                    value = entryPremium,
                    onValueChange = { entryPremium = it },
                    modifier = Modifier.weight(1f)
                )
                SimTextField(
                    label = "IV (%)",
                    value = iv,
                    onValueChange = { iv = it },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SimTextField(
                    label = "Days to Expiry",
                    value = daysToExpiry,
                    onValueChange = { daysToExpiry = it },
                    modifier = Modifier.weight(1f)
                )
                SimTextField(
                    label = "Lot Size",
                    value = lotSize,
                    onValueChange = { lotSize = it },
                    modifier = Modifier.weight(1f)
                )
            }

            SimTextField(
                label = "Quantity (lots)",
                value = quantity,
                onValueChange = { quantity = it },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextSecondary
                    )
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val leg = SimulationInput(
                            spotPrice = spotPrice.toDoubleOrNull() ?: 25000.0,
                            strikePrice = strikePrice.toDoubleOrNull() ?: 25000.0,
                            optionType = optionType,
                            entryPremium = entryPremium.toDoubleOrNull() ?: 200.0,
                            iv = (iv.toDoubleOrNull() ?: 15.0) / 100.0,
                            daysToExpiry = daysToExpiry.toIntOrNull() ?: 7,
                            lotSize = lotSize.toIntOrNull() ?: 75,
                            quantity = quantity.toIntOrNull() ?: 1
                        )
                        onAdd(leg)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.AccentBlue
                    )
                ) {
                    Text("Add Leg")
                }
            }
        }
    }
}

@Composable
private fun SimTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = AppColors.TextMuted, style = MaterialTheme.typography.labelSmall) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.AccentBlue,
            unfocusedBorderColor = AppColors.CardDark,
            focusedTextColor = AppColors.TextPrimary,
            unfocusedTextColor = AppColors.TextPrimary,
            cursorColor = AppColors.AccentBlue
        ),
        singleLine = true
    )
}

@Composable
private fun LegCard(
    index: Int,
    leg: SimulationInput,
    onRemove: () -> Unit
) {
    val typeColor = if (leg.optionType == OptionType.CALL) AppColors.AccentGreen else AppColors.AccentRed
    val typeName = if (leg.optionType == OptionType.CALL) "CE" else "PE"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "L${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.AccentBlue
                )
                Text(
                    text = "%.0f".format(leg.strikePrice),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.TextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(typeColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = typeName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                Text(
                    text = "@ %.2f".format(leg.entryPremium),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.TextSecondary
                )
                Text(
                    text = "${leg.quantity}x${leg.lotSize}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextMuted
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = AppColors.AccentRed
                )
            }
        }
    }
}

@Composable
private fun SlidersSection(
    spotChangePercent: Float,
    daysElapsed: Int,
    ivChangePercent: Float,
    maxDTE: Int,
    onSpotChange: (Float) -> Unit,
    onDaysChange: (Int) -> Unit,
    onIVChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Scenario Sliders",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary
            )

            // Spot Change slider
            SliderWithLabel(
                label = "Spot Change",
                value = spotChangePercent,
                valueRange = -10f..10f,
                displayValue = "%+.1f%%".format(spotChangePercent),
                accentColor = if (spotChangePercent >= 0) AppColors.AccentGreen else AppColors.AccentRed,
                onValueChange = onSpotChange
            )

            // Days Elapsed slider
            SliderWithLabel(
                label = "Days Elapsed",
                value = daysElapsed.toFloat(),
                valueRange = 0f..maxDTE.toFloat(),
                displayValue = "$daysElapsed days",
                accentColor = AppColors.AccentBlue,
                onValueChange = { onDaysChange(it.toInt()) }
            )

            // IV Change slider
            SliderWithLabel(
                label = "IV Change",
                value = ivChangePercent,
                valueRange = -30f..30f,
                displayValue = "%+.0f%%".format(ivChangePercent),
                accentColor = Color(0xFF9C27B0),
                onValueChange = onIVChange
            )
        }
    }
}

@Composable
private fun SliderWithLabel(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextMuted
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = accentColor
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = AppColors.CardDark
            )
        )
    }
}

@Composable
private fun TotalPnLCard(
    totalPnl: Double,
    totalPnlPercent: Double
) {
    val pnlColor = if (totalPnl >= 0) AppColors.AccentGreen else AppColors.AccentRed
    val bgColor = pnlColor.copy(alpha = 0.1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total P&L",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "%+,.0f".format(totalPnl),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = pnlColor
            )
            Text(
                text = "(%+.1f%%)".format(totalPnlPercent),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                color = pnlColor
            )
        }
    }
}

@Composable
private fun PayoffChart(
    points: List<PayoffPoint>
) {
    val greenColor = AppColors.AccentGreen
    val redColor = AppColors.AccentRed
    val mutedColor = AppColors.TextMuted

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Payoff Chart",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (points.isEmpty()) {
                Text(
                    text = "No data to display",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextMuted
                )
            } else {
                val minPnl = points.minOf { it.pnl }
                val maxPnl = points.maxOf { it.pnl }
                val minSpot = points.minOf { it.spotPrice }
                val maxSpot = points.maxOf { it.spotPrice }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.CardDark)
                        .padding(8.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val paddingLeft = 50f
                    val paddingBottom = 20f
                    val chartWidth = width - paddingLeft
                    val chartHeight = height - paddingBottom

                    val pnlRange = if (maxPnl - minPnl == 0.0) 1.0 else maxPnl - minPnl
                    val spotRange = if (maxSpot - minSpot == 0.0) 1.0 else maxSpot - minSpot

                    // Zero line
                    if (minPnl < 0 && maxPnl > 0) {
                        val zeroY = chartHeight - ((0 - minPnl) / pnlRange * chartHeight).toFloat()
                        drawLine(
                            color = Color.White.copy(alpha = 0.2f),
                            start = Offset(paddingLeft, zeroY),
                            end = Offset(width, zeroY),
                            strokeWidth = 1f
                        )
                    }

                    // Draw the path
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = paddingLeft + ((point.spotPrice - minSpot) / spotRange * chartWidth).toFloat()
                        val y = chartHeight - ((point.pnl - minPnl) / pnlRange * chartHeight).toFloat()

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = if (points.last().pnl >= 0) greenColor else redColor,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                    )

                    // Y-axis labels
                    val labelPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(128, 255, 255, 255)
                        textSize = 20f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }

                    drawContext.canvas.nativeCanvas.apply {
                        drawText(
                            "%+.0f".format(maxPnl),
                            paddingLeft - 4f,
                            16f,
                            labelPaint
                        )
                        drawText(
                            "%+.0f".format(minPnl),
                            paddingLeft - 4f,
                            chartHeight,
                            labelPaint
                        )
                    }
                }

                // Spot price range label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "%.0f".format(minSpot),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextMuted
                    )
                    Text(
                        text = "Spot Price",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextMuted
                    )
                    Text(
                        text = "%.0f".format(maxSpot),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun LegResultCard(
    index: Int,
    legResult: LegSimResult
) {
    val pnlColor = if (legResult.pnl >= 0) AppColors.AccentGreen else AppColors.AccentRed
    val typeColor = if (legResult.input.optionType == OptionType.CALL) AppColors.AccentGreen else AppColors.AccentRed
    val typeName = if (legResult.input.optionType == OptionType.CALL) "CE" else "PE"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Leg ${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.AccentBlue
                    )
                    Text(
                        text = "%.0f".format(legResult.input.strikePrice),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AppColors.TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeColor)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "%+,.0f".format(legResult.pnl),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = pnlColor
                    )
                    Text(
                        text = "(%+.1f%%)".format(legResult.pnlPercent),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = pnlColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Price change
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Entry: %.2f".format(legResult.input.entryPremium),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.TextSecondary
                )
                Text(
                    text = "New: %.2f".format(legResult.newPrice),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = pnlColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Greeks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SimGreekCell(label = "Delta", value = "%.4f".format(legResult.newDelta), color = AppColors.AccentGreen)
                SimGreekCell(label = "Gamma", value = "%.6f".format(legResult.newGamma), color = AppColors.AccentBlue)
                SimGreekCell(label = "Theta", value = "%.2f".format(legResult.newTheta), color = AppColors.AccentRed)
                SimGreekCell(label = "Vega", value = "%.2f".format(legResult.newVega), color = Color(0xFF9C27B0))
            }
        }
    }
}

@Composable
private fun SimGreekCell(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextMuted
        )
    }
}
