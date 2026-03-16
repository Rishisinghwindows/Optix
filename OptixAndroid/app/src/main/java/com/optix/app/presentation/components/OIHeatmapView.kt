package com.optix.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.core.calculation.OIAnalysisEngine
import com.optix.app.domain.model.*
import com.optix.app.presentation.theme.*
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * OI Heatmap data for a single strike
 */
data class OIHeatmapData(
    val strikePrice: Double,
    val callOI: Long,
    val putOI: Long,
    val callOIChange: Long,
    val putOIChange: Long,
    val callIntensity: Float,
    val putIntensity: Float,
    val zoneType: HeatmapZoneType,
    val isATM: Boolean = false
)

/**
 * OI Heatmap View - Grid-based heatmap visualization for Open Interest data
 *
 * Displays Call OI and Put OI with color-coded zones:
 * - Strong Support: Green
 * - Weak Support: Light Green
 * - Neutral: Gray
 * - Weak Resistance: Light Red
 * - Strong Resistance: Red
 * - Max Pain: Purple
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OIHeatmapView(
    heatmapData: List<OIHeatmapData>,
    spotPrice: Double,
    maxPain: Double,
    onStrikeSelect: (Double) -> Unit,
    modifier: Modifier = Modifier,
    strikeInterval: Double = 50.0
) {
    var showCallOI by remember { mutableStateOf(true) }
    var showPutOI by remember { mutableStateOf(true) }
    var selectedStrike by remember { mutableStateOf<Double?>(null) }
    var showDetailsSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Find ATM index for auto-scroll
    val atmThreshold = strikeInterval / 2
    val atmIndex = remember(heatmapData, spotPrice, atmThreshold) {
        heatmapData.indexOfFirst { abs(it.strikePrice - spotPrice) <= atmThreshold }
            .takeIf { it >= 0 } ?: 0
    }

    // Auto-scroll to ATM on first composition
    LaunchedEffect(atmIndex) {
        if (atmIndex > 0 && heatmapData.isNotEmpty()) {
            listState.animateScrollToItem(
                index = maxOf(0, atmIndex - 3),
                scrollOffset = 0
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OI Heatmap",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Toggle chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip(
                    label = "CE",
                    isOn = showCallOI,
                    color = Loss,
                    onToggle = { showCallOI = it }
                )
                ToggleChip(
                    label = "PE",
                    isOn = showPutOI,
                    color = Profit,
                    onToggle = { showPutOI = it }
                )
            }
        }

        // Legend
        HeatmapLegend(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Column Headers
        HeatmapHeader(
            showCallOI = showCallOI,
            showPutOI = showPutOI,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Heatmap Grid
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(
                items = heatmapData,
                key = { _, item -> item.strikePrice }
            ) { index, data ->
                HeatmapRow(
                    data = data,
                    spotPrice = spotPrice,
                    maxPain = maxPain,
                    showCallOI = showCallOI,
                    showPutOI = showPutOI,
                    isSelected = selectedStrike == data.strikePrice,
                    atmThreshold = atmThreshold,
                    onClick = {
                        selectedStrike = data.strikePrice
                        showDetailsSheet = true
                        onStrikeSelect(data.strikePrice)
                    }
                )

                if (index < heatmapData.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        thickness = 0.5.dp
                    )
                }
            }
        }

        // Jump to ATM button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(maxOf(0, atmIndex - 3))
                    }
                }
            ) {
                Text(
                    text = "Jump to ATM",
                    color = AccentBlue
                )
            }
        }
    }

    // Details Bottom Sheet
    if (showDetailsSheet && selectedStrike != null) {
        val selectedData = heatmapData.find { it.strikePrice == selectedStrike }
        if (selectedData != null) {
            ModalBottomSheet(
                onDismissRequest = { showDetailsSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                StrikeDetailsSheet(
                    data = selectedData,
                    spotPrice = spotPrice,
                    maxPain = maxPain,
                    modifier = Modifier.padding(16.dp),
                    atmThreshold = atmThreshold
                )
            }
        }
    }
}

@Composable
private fun HeatmapHeader(
    showCallOI: Boolean,
    showPutOI: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showCallOI) {
            Text(
                text = "Call OI",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Loss,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = "Strike",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AccentBlue,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(70.dp)
        )

        if (showPutOI) {
            Text(
                text = "Put OI",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Profit,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeatmapRow(
    data: OIHeatmapData,
    spotPrice: Double,
    maxPain: Double,
    showCallOI: Boolean,
    showPutOI: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    atmThreshold: Double = 25.0
) {
    val isATM = abs(data.strikePrice - spotPrice) <= atmThreshold
    val isMaxPain = abs(data.strikePrice - maxPain) < 1

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> AccentBlue.copy(alpha = 0.1f)
            isATM -> AccentGreen.copy(alpha = 0.1f)
            isMaxPain -> AccentPurple.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        animationSpec = tween(300),
        label = "rowBackground"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Call OI Cell
        if (showCallOI) {
            HeatmapCell(
                value = data.callOI,
                change = data.callOIChange,
                intensity = data.callIntensity,
                baseColor = Loss,
                modifier = Modifier.weight(1f)
            )
        }

        // Strike Price
        Column(
            modifier = Modifier
                .width(70.dp)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = String.format("%.0f", data.strikePrice),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isATM || isMaxPain) FontWeight.Black else FontWeight.Medium
                ),
                color = when {
                    isMaxPain -> AccentPurple
                    isATM -> AccentGreen
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center
            )

            if (isATM) {
                Text(
                    text = "ATM",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
            } else if (isMaxPain) {
                Text(
                    text = "MAX PAIN",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    fontWeight = FontWeight.Bold,
                    color = AccentPurple
                )
            }
        }

        // Put OI Cell
        if (showPutOI) {
            HeatmapCell(
                value = data.putOI,
                change = data.putOIChange,
                intensity = data.putIntensity,
                baseColor = Profit,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeatmapCell(
    value: Long,
    change: Long,
    intensity: Float,
    baseColor: Color,
    modifier: Modifier = Modifier
) {
    val backgroundColor = baseColor.copy(alpha = 0.1f + (intensity * 0.6f))
    val textColor = if (intensity > 0.7f) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .height(50.dp)
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // OI Value
            Text(
                text = OIAnalysisEngine.formatOI(value),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )

            // Change indicator
            if (change != 0L) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (change > 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = if (change > 0) Profit else Loss
                    )
                    Text(
                        text = OIAnalysisEngine.formatOI(abs(change)),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = if (change > 0) Profit else Loss,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    isOn: Boolean,
    color: Color,
    onToggle: (Boolean) -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isOn) color else color.copy(alpha = 0.2f),
        animationSpec = tween(200),
        label = "chipBackground"
    )

    val textColor by animateColorAsState(
        targetValue = if (isOn) Color.White else color,
        animationSpec = tween(200),
        label = "chipText"
    )

    Surface(
        modifier = Modifier
            .clickable { onToggle(!isOn) },
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun HeatmapLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Intensity gradient
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Low",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp, 12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AccentBlue.copy(alpha = 0.1f + (index * 0.2f)))
                    )
                }
            }

            Text(
                text = "High",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Change indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Profit
                )
                Text(
                    text = "Building",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Loss
                )
                Text(
                    text = "Unwinding",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun StrikeDetailsSheet(
    data: OIHeatmapData,
    spotPrice: Double,
    maxPain: Double,
    modifier: Modifier = Modifier,
    atmThreshold: Double = 25.0
) {
    val isATM = abs(data.strikePrice - spotPrice) <= atmThreshold
    val isMaxPain = abs(data.strikePrice - maxPain) < 1
    val distanceFromSpot = data.strikePrice - spotPrice
    val distancePercent = (distanceFromSpot / spotPrice) * 100

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Strike ${String.format("%.0f", data.strikePrice)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = if (distanceFromSpot >= 0) {
                        "${String.format("%.0f", distanceFromSpot)} points above spot (${String.format("%.2f", distancePercent)}%)"
                    } else {
                        "${String.format("%.0f", abs(distanceFromSpot))} points below spot (${String.format("%.2f", abs(distancePercent))}%)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Zone badge
            ZoneBadge(zoneType = data.zoneType, isATM = isATM, isMaxPain = isMaxPain)
        }

        HorizontalDivider()

        // OI Data Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Call OI Card
            OIDetailCard(
                title = "Call OI",
                value = data.callOI,
                change = data.callOIChange,
                intensity = data.callIntensity,
                color = Loss,
                modifier = Modifier.weight(1f)
            )

            // Put OI Card
            OIDetailCard(
                title = "Put OI",
                value = data.putOI,
                change = data.putOIChange,
                intensity = data.putIntensity,
                color = Profit,
                modifier = Modifier.weight(1f)
            )
        }

        // PCR at strike
        val pcrAtStrike = if (data.callOI > 0) data.putOI.toDouble() / data.callOI else 0.0
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PCR at Strike",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Text(
                    text = String.format("%.2f", pcrAtStrike),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        pcrAtStrike > 1.2 -> Profit
                        pcrAtStrike < 0.8 -> Loss
                        else -> AccentBlue
                    }
                )
            }
        }

        // Interpretation
        val interpretation = getStrikeInterpretation(data, spotPrice, maxPain)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Interpretation",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = interpretation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ZoneBadge(
    zoneType: HeatmapZoneType,
    isATM: Boolean,
    isMaxPain: Boolean
) {
    val (text, color) = when {
        isATM -> "ATM" to AccentGreen
        isMaxPain -> "Max Pain" to AccentPurple
        else -> when (zoneType) {
            HeatmapZoneType.STRONG_SUPPORT -> "Strong Support" to Color(0xFF22C55E)
            HeatmapZoneType.WEAK_SUPPORT -> "Weak Support" to Color(0xFF86EFAC)
            HeatmapZoneType.NEUTRAL -> "Neutral" to Color(0xFF6B7280)
            HeatmapZoneType.WEAK_RESISTANCE -> "Weak Resistance" to Color(0xFFFCA5A5)
            HeatmapZoneType.STRONG_RESISTANCE -> "Strong Resistance" to Color(0xFFEF4444)
            HeatmapZoneType.MAX_PAIN -> "Max Pain" to Color(0xFF8B5CF6)
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun OIDetailCard(
    title: String,
    value: Long,
    change: Long,
    intensity: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )

            Text(
                text = OIAnalysisEngine.formatOI(value),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (change >= 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (change >= 0) Profit else Loss
                )
                Text(
                    text = "${if (change >= 0) "+" else ""}${OIAnalysisEngine.formatOI(change)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (change >= 0) Profit else Loss
                )
            }

            // Intensity bar
            LinearProgressIndicator(
                progress = { intensity },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.2f)
            )

            Text(
                text = "${(intensity * 100).toInt()}% of max",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

private fun getStrikeInterpretation(
    data: OIHeatmapData,
    spotPrice: Double,
    maxPain: Double
): String {
    val isAboveSpot = data.strikePrice > spotPrice
    val pcrAtStrike = if (data.callOI > 0) data.putOI.toDouble() / data.callOI else 0.0

    val interpretations = mutableListOf<String>()

    // Zone interpretation
    when (data.zoneType) {
        HeatmapZoneType.STRONG_SUPPORT -> {
            interpretations.add("Heavy put OI indicates strong buying interest at this level. Expect price to bounce from here.")
        }
        HeatmapZoneType.WEAK_SUPPORT -> {
            interpretations.add("Moderate put OI suggests some support. May hold initially but could break on strong selling.")
        }
        HeatmapZoneType.STRONG_RESISTANCE -> {
            interpretations.add("Heavy call OI indicates strong selling pressure. Price likely to face rejection here.")
        }
        HeatmapZoneType.WEAK_RESISTANCE -> {
            interpretations.add("Moderate call OI suggests some resistance. Could break on strong momentum.")
        }
        HeatmapZoneType.MAX_PAIN -> {
            interpretations.add("Max pain level - price tends to gravitate here near expiry. Option writers have maximum profit at this strike.")
        }
        HeatmapZoneType.NEUTRAL -> {
            interpretations.add("No significant OI concentration. Price may pass through without much friction.")
        }
    }

    // OI change interpretation
    when {
        data.callOIChange > 0 && data.putOIChange > 0 -> {
            interpretations.add("Both call and put writing building - expect rangebound action near this strike.")
        }
        data.callOIChange > 0 && data.putOIChange <= 0 -> {
            interpretations.add("Fresh call writing with put unwinding - bearish sentiment around this level.")
        }
        data.callOIChange <= 0 && data.putOIChange > 0 -> {
            interpretations.add("Fresh put writing with call unwinding - bullish sentiment around this level.")
        }
        data.callOIChange < 0 && data.putOIChange < 0 -> {
            interpretations.add("Both call and put unwinding - expect trending move away from this level.")
        }
    }

    // PCR interpretation
    when {
        pcrAtStrike > 1.5 -> {
            interpretations.add("Very high PCR (${String.format("%.2f", pcrAtStrike)}) - strong put writing suggests bullish outlook.")
        }
        pcrAtStrike < 0.5 -> {
            interpretations.add("Very low PCR (${String.format("%.2f", pcrAtStrike)}) - heavy call writing suggests bearish outlook.")
        }
    }

    return interpretations.joinToString("\n\n")
}

/**
 * Utility function to convert OIHeatmapCell list to OIHeatmapData list
 */
fun List<OIHeatmapCell>.toHeatmapData(
    spotPrice: Double,
    maxPain: Double,
    strikeInterval: Double = 50.0
): List<OIHeatmapData> {
    val maxCallOI = (this.maxOfOrNull { it.callOI } ?: 1L).coerceAtLeast(1L)
    val maxPutOI = (this.maxOfOrNull { it.putOI } ?: 1L).coerceAtLeast(1L)

    return this.map { cell ->
        val callIntensity = cell.callOI.toFloat() / maxCallOI
        val putIntensity = cell.putOI.toFloat() / maxPutOI

        val zoneType = when {
            abs(cell.strikePrice - maxPain) < 1 -> HeatmapZoneType.MAX_PAIN
            cell.strikePrice < spotPrice && putIntensity >= 0.85f -> HeatmapZoneType.STRONG_SUPPORT
            cell.strikePrice < spotPrice && putIntensity >= 0.6f -> HeatmapZoneType.WEAK_SUPPORT
            cell.strikePrice > spotPrice && callIntensity >= 0.85f -> HeatmapZoneType.STRONG_RESISTANCE
            cell.strikePrice > spotPrice && callIntensity >= 0.6f -> HeatmapZoneType.WEAK_RESISTANCE
            else -> HeatmapZoneType.NEUTRAL
        }

        OIHeatmapData(
            strikePrice = cell.strikePrice,
            callOI = cell.callOI,
            putOI = cell.putOI,
            callOIChange = cell.callOIChange,
            putOIChange = cell.putOIChange,
            callIntensity = callIntensity,
            putIntensity = putIntensity,
            zoneType = zoneType,
            isATM = abs(cell.strikePrice - spotPrice) <= strikeInterval / 2
        )
    }
}

/**
 * Utility function to convert HeatmapCell list to OIHeatmapData list
 */
fun List<HeatmapCell>.toOIHeatmapData(
    spotPrice: Double,
    maxPain: Double,
    strikeInterval: Double = 50.0
): List<OIHeatmapData> {
    val maxCallOI = (this.maxOfOrNull { it.callOI } ?: 1L).coerceAtLeast(1L)
    val maxPutOI = (this.maxOfOrNull { it.putOI } ?: 1L).coerceAtLeast(1L)

    return this.map { cell ->
        val callIntensity = cell.callOI.toFloat() / maxCallOI
        val putIntensity = cell.putOI.toFloat() / maxPutOI

        OIHeatmapData(
            strikePrice = cell.strikePrice,
            callOI = cell.callOI,
            putOI = cell.putOI,
            callOIChange = cell.callOIChange,
            putOIChange = cell.putOIChange,
            callIntensity = callIntensity,
            putIntensity = putIntensity,
            zoneType = cell.zoneType,
            isATM = abs(cell.strikePrice - spotPrice) <= strikeInterval / 2
        )
    }
}
