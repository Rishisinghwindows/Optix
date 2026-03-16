package com.optix.app.presentation.screens.charts

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.domain.model.ChartTimeframe
import com.optix.app.domain.model.TradingIndex
import com.optix.app.presentation.theme.*
import kotlin.math.abs

/**
 * Charts Screen - Professional Index Price Charts
 * Features:
 * - Tab layout for different chart types
 * - Index selection (NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY)
 * - Price display with change percentage
 * - Multiple timeframes
 * - Technical indicators overlay
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    onBack: () -> Unit = {},
    viewModel: ChartsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showIndexPicker by remember { mutableStateOf(false) }
    var showMAOptions by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header with Index Selector
        ChartsHeader(
            selectedIndex = state.selectedIndex,
            spotPrice = state.spotPrice,
            priceChange = state.priceChange,
            priceChangePercent = state.priceChangePercent,
            onIndexClick = { showIndexPicker = true },
            onBack = onBack
        )

        // Timeframe Pills
        TimeframePills(
            selectedTimeframe = state.selectedTimeframe,
            onTimeframeSelected = { viewModel.selectTimeframe(it) }
        )

        // Chart Controls
        ChartControls(
            showVolume = state.showVolume,
            showMA = state.showMA,
            maDisplayType = state.selectedMAType,
            onToggleVolume = { viewModel.toggleVolume() },
            onToggleMA = { viewModel.toggleMA() },
            onMATypeClick = { showMAOptions = true }
        )

        // Chart Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            when {
                state.isLoading -> {
                    ChartLoadingState()
                }
                state.error != null -> {
                    ChartErrorState(
                        message = state.error!!,
                        onRetry = { viewModel.refresh() }
                    )
                }
                state.chartData != null -> {
                    IndexChartView(
                        chartData = state.chartData!!,
                        technicalIndicators = state.technicalIndicators,
                        showVolume = state.showVolume,
                        showMA = state.showMA,
                        maDisplayType = state.selectedMAType,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Market Stats Footer
        MarketStatsFooter(
            selectedIndex = state.selectedIndex,
            chartData = state.chartData,
            technicalIndicators = state.technicalIndicators
        )
    }

    // Index Picker Bottom Sheet
    if (showIndexPicker) {
        ModalBottomSheet(
            onDismissRequest = { showIndexPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            IndexPickerContent(
                selectedIndex = state.selectedIndex,
                onIndexSelected = {
                    viewModel.selectIndex(it)
                    showIndexPicker = false
                }
            )
        }
    }

    // MA Options Bottom Sheet
    if (showMAOptions) {
        ModalBottomSheet(
            onDismissRequest = { showMAOptions = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            MAOptionsContent(
                selectedType = state.selectedMAType,
                onTypeSelected = {
                    viewModel.setMADisplayType(it)
                    showMAOptions = false
                }
            )
        }
    }
}

@Composable
private fun ChartsHeader(
    selectedIndex: TradingIndex,
    spotPrice: Double,
    priceChange: Double,
    priceChangePercent: Double,
    onIndexClick: () -> Unit,
    onBack: () -> Unit
) {
    val isPositive = priceChange >= 0
    val changeColor = if (isPositive) Profit else Loss

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Back button + Index selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Index Selector
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onIndexClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ShowChart,
                        contentDescription = null,
                        tint = PrimaryGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedIndex.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Index",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Live indicator
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingDot()
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryGreen
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Price Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = String.format("%.2f", spotPrice),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = (-1).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Change badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = changeColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPositive) "+" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = changeColor
                            )
                            Text(
                                text = String.format("%.2f", priceChange),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = changeColor
                            )
                            Text(
                                text = " (${String.format("%.2f", abs(priceChangePercent))}%)",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = changeColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                contentDescription = null,
                                tint = changeColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .background(
                color = PrimaryGreen.copy(alpha = alpha),
                shape = CircleShape
            )
    )
}

@Composable
private fun TimeframePills(
    selectedTimeframe: ChartTimeframe,
    onTimeframeSelected: (ChartTimeframe) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ChartTimeframe.entries) { timeframe ->
            val isSelected = timeframe == selectedTimeframe

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onTimeframeSelected(timeframe) },
                color = if (isSelected) PrimaryGreen else MaterialTheme.colorScheme.surface,
                border = if (isSelected) null else BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Text(
                    text = timeframe.shortName,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ChartControls(
    showVolume: Boolean,
    showMA: Boolean,
    maDisplayType: MADisplayType,
    onToggleVolume: () -> Unit,
    onToggleMA: () -> Unit,
    onMATypeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Volume toggle
        ChartControlChip(
            label = "Volume",
            isActive = showVolume,
            onClick = onToggleVolume,
            icon = Icons.Outlined.BarChart
        )

        // MA toggle with type selector
        ChartControlChip(
            label = if (showMA) maDisplayType.displayName else "MA Off",
            isActive = showMA,
            onClick = onToggleMA,
            onLongClick = onMATypeClick,
            icon = Icons.Outlined.Timeline
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChartControlChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isActive) AccentBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isActive) BorderStroke(1.dp, AccentBlue.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChartLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = PrimaryGreen,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading chart data...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChartErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Loss,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun MarketStatsFooter(
    selectedIndex: TradingIndex,
    chartData: com.optix.app.domain.model.ChartData?,
    technicalIndicators: com.optix.app.domain.model.TechnicalIndicators
) {
    if (chartData == null || chartData.isEmpty) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatItem(
                label = "High",
                value = String.format("%.2f", chartData.highestPrice),
                color = Profit
            )
            StatItem(
                label = "Low",
                value = String.format("%.2f", chartData.lowestPrice),
                color = Loss
            )
            technicalIndicators.vwap?.let { vwap ->
                if (vwap > 0) {
                    StatItem(
                        label = "VWAP",
                        value = String.format("%.2f", vwap),
                        color = AccentPurple
                    )
                }
            }
            StatItem(
                label = "Lot",
                value = selectedIndex.lotSize.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            color = color
        )
    }
}

@Composable
private fun IndexPickerContent(
    selectedIndex: TradingIndex,
    onIndexSelected: (TradingIndex) -> Unit
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            text = "Select Index",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Main indices
        val mainIndices = listOf(
            TradingIndex.NIFTY50,
            TradingIndex.BANKNIFTY,
            TradingIndex.FINNIFTY,
            TradingIndex.MIDCPNIFTY
        )

        mainIndices.forEach { index ->
            IndexPickerItem(
                index = index,
                isSelected = index == selectedIndex,
                onClick = { onIndexSelected(index) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "BSE Indices",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        // BSE indices
        val bseIndices = listOf(TradingIndex.SENSEX, TradingIndex.BANKEX)
        bseIndices.forEach { index ->
            IndexPickerItem(
                index = index,
                isSelected = index == selectedIndex,
                onClick = { onIndexSelected(index) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun IndexPickerItem(
    index: TradingIndex,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (isSelected) PrimaryGreen.copy(alpha = 0.1f) else Color.Transparent,
        border = if (isSelected) BorderStroke(2.dp, PrimaryGreen) else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = index.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) PrimaryGreen else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Lot Size: ${index.lotSize}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun MAOptionsContent(
    selectedType: MADisplayType,
    onTypeSelected: (MADisplayType) -> Unit
) {
    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            text = "Moving Average Display",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose which moving averages to display on the chart",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        MADisplayType.entries.forEach { type ->
            val isSelected = type == selectedType

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTypeSelected(type) },
                color = if (isSelected) AccentBlue.copy(alpha = 0.1f) else Color.Transparent,
                border = if (isSelected) BorderStroke(2.dp, AccentBlue) else BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = type.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AccentBlue else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = getMADescription(type),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun getMADescription(type: MADisplayType): String {
    return when (type) {
        MADisplayType.SMA_20_50 -> "Simple Moving Average (20 & 50 period)"
        MADisplayType.EMA_9_21 -> "Exponential Moving Average (9 & 21 period)"
        MADisplayType.ALL -> "Show all available moving averages"
        MADisplayType.NONE -> "Hide all moving averages"
    }
}
