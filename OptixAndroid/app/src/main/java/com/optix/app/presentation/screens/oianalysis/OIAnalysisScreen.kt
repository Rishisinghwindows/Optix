package com.optix.app.presentation.screens.oianalysis

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.presentation.theme.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OIAnalysisScreen(
    viewModel: OIAnalysisViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Heatmap", "Smart Money", "Zones")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("OI Analysis", fontWeight = FontWeight.Bold)
                        Text(
                            text = state.selectedIndex.symbol,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = PrimaryGreen
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryGreen)
                }
            } else {
                when (selectedTab) {
                    0 -> OverviewTab(state)
                    1 -> HeatmapTab(state)
                    2 -> SmartMoneyTab(state)
                    3 -> ZonesTab(state)
                }
            }
        }
    }
}

@Composable
fun OverviewTab(state: OIAnalysisState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { KeyMetricsCard(state) }
        item {
            TopOICard(
                title = "Top Call OI",
                strikes = state.topCallOIStrikes,
                color = CallColor,
                icon = Icons.Default.TrendingUp
            )
        }
        item {
            TopOICard(
                title = "Top Put OI",
                strikes = state.topPutOIStrikes,
                color = PutColor,
                icon = Icons.Default.TrendingDown
            )
        }
    }
}

@Composable
fun KeyMetricsCard(state: OIAnalysisState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Key Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OIMetricItem("Spot", String.format("%.0f", state.spotPrice), MaterialTheme.colorScheme.onSurface)
                OIMetricItem("PCR", String.format("%.2f", state.pcr), if (state.pcr > 1) PrimaryGreen else ErrorRed)
                OIMetricItem("Max Pain", String.format("%.0f", state.maxPain), WarningOrange)
                OIMetricItem("ATM", String.format("%.0f", state.atmStrike), AccentBlue)
            }
            Spacer(modifier = Modifier.height(16.dp))
            PCRGauge(pcr = state.pcr)
        }
    }
}

@Composable
fun OIMetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun PCRGauge(pcr: Double) {
    val normalizedPCR = ((pcr.coerceIn(0.5, 1.5) - 0.5) / 1.0).toFloat()
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bearish", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
            Text("Neutral", style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
            Text("Bullish", style = MaterialTheme.typography.labelSmall, color = PrimaryGreen)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { normalizedPCR },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = when { pcr > 1.2 -> PrimaryGreen; pcr < 0.8 -> ErrorRed; else -> WarningOrange },
            trackColor = BorderDark
        )
    }
}

@Composable
fun TopOICard(title: String, strikes: List<Pair<Double, Long>>, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (strikes.isEmpty()) {
                Text("No data available", color = TextSecondaryDark)
            } else {
                val maxOI = (strikes.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
                strikes.forEach { (strike, oi) ->
                    OIBarRow(strike, oi, maxOI, color)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun OIBarRow(strike: Double, oi: Long, maxOI: Long, color: Color) {
    val ratio = (oi.toFloat() / maxOI).coerceIn(0f, 1f)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(String.format("%.0f", strike), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.width(80.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(formatOI(oi), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
    }
}

@Composable
fun HeatmapTab(state: OIAnalysisState) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("OI Heatmap", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
                Text("Call OI", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Strike", modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Put OI", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        val strikeInterval = if (state.heatmapData.size >= 2) {
            state.heatmapData.zipWithNext { a, b -> abs(b.strikePrice - a.strikePrice) }
                .filter { it > 0 }
                .minOrNull() ?: 50.0
        } else 50.0

        items(state.heatmapData) { cell ->
            HeatmapRow(cell, state.spotPrice, strikeInterval)
        }
    }
}

@Composable
fun HeatmapRow(cell: HeatmapCell, spotPrice: Double, strikeInterval: Double = 50.0) {
    val isATM = abs(cell.strikePrice - spotPrice) < strikeInterval / 2
    val maxOI = maxOf(cell.callOI, cell.putOI, 1L)
    val callIntensity = (cell.callOI.toFloat() / maxOI).coerceIn(0.2f, 1f)
    val putIntensity = (cell.putOI.toFloat() / maxOI).coerceIn(0.2f, 1f)

    Row(
        modifier = Modifier.fillMaxWidth().background(if (isATM) PrimaryGreen.copy(alpha = 0.1f) else Color.Transparent).padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).height(32.dp).background(CallColor.copy(alpha = callIntensity * 0.6f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Text(formatOI(cell.callOI), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        Text(String.format("%.0f", cell.strikePrice), modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center, fontWeight = if (isATM) FontWeight.Bold else FontWeight.Normal)
        Box(modifier = Modifier.weight(1f).height(32.dp).background(PutColor.copy(alpha = putIntensity * 0.6f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
            Text(formatOI(cell.putOI), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun SmartMoneyTab(state: OIAnalysisState) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Smart Money Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Detected patterns based on OI and price changes", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
        }
        if (state.smartMoneyActivities.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No significant activity detected", color = TextSecondaryDark)
                }
            }
        } else {
            items(state.smartMoneyActivities) { activity -> SmartMoneyCard(activity) }
        }
    }
}

@Composable
fun SmartMoneyCard(activity: SmartMoneyActivity) {
    val (color, icon) = when (activity.activityType) {
        ActivityType.HEAVY_CALL_WRITING -> ErrorRed to Icons.Default.Edit
        ActivityType.HEAVY_PUT_WRITING -> PrimaryGreen to Icons.Default.Edit
        ActivityType.CALL_UNWINDING -> WarningOrange to Icons.Default.TrendingDown
        ActivityType.PUT_UNWINDING -> WarningOrange to Icons.Default.TrendingUp
        ActivityType.FRESH_LONG_BUILD -> PrimaryGreen to Icons.Default.TrendingUp
        ActivityType.SHORT_COVERING -> AccentBlue to Icons.Default.SwapVert
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.1f), border = BorderStroke(1.dp, color.copy(alpha = 0.3f))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.2f)) {
                Icon(icon, null, tint = color, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(activity.activityType.name.replace("_", " "), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
                    Text(String.format("%.0f", activity.strikePrice), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(activity.description, style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
            }
        }
    }
}

@Composable
fun ZonesTab(state: OIAnalysisState) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Support & Resistance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item { ZoneSection("Resistance Zones", state.resistanceZones, ErrorRed, Icons.Default.ArrowUpward) }
        item {
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = PrimaryGreen.copy(alpha = 0.1f), border = BorderStroke(2.dp, PrimaryGreen)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.RadioButtonChecked, null, tint = PrimaryGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Current Spot: ${String.format("%.0f", state.spotPrice)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                }
            }
        }
        item { ZoneSection("Support Zones", state.supportZones, PrimaryGreen, Icons.Default.ArrowDownward) }
    }
}

@Composable
fun ZoneSection(title: String, zones: List<OIZone>, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, BorderDark)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (zones.isEmpty()) {
                Text("No strong zones detected", color = TextSecondaryDark)
            } else {
                zones.forEach { zone ->
                    ZoneRow(zone, color)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ZoneRow(zone: OIZone, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(4.dp).height(40.dp).background(color.copy(alpha = zone.strength), RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(String.format("%.0f", zone.strikePrice), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            val strengthText = if (zone.strength > 0.7) "Strong" else "Moderate"
            val zoneText = zone.zoneType.name.replace("_", " ")
            Text("$strengthText $zoneText", style = MaterialTheme.typography.labelSmall, color = TextSecondaryDark)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Call: ${formatOI(zone.callOI)}", style = MaterialTheme.typography.labelSmall, color = CallColor)
            Text("Put: ${formatOI(zone.putOI)}", style = MaterialTheme.typography.labelSmall, color = PutColor)
        }
    }
}

private fun formatOI(oi: Long): String = when {
    oi >= 10_000_000 -> String.format("%.1fCr", oi / 10_000_000.0)
    oi >= 100_000 -> String.format("%.1fL", oi / 100_000.0)
    oi >= 1_000 -> String.format("%.1fK", oi / 1_000.0)
    else -> oi.toString()
}
