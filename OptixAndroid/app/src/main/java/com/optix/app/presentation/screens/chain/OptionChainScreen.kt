package com.optix.app.presentation.screens.chain

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.os.Bundle
import com.optix.app.R
import com.optix.app.core.util.AnalyticsHelper
import com.optix.app.core.util.Resource
import com.optix.app.domain.model.ExpiryDate
import com.optix.app.domain.model.OptionChain
import com.optix.app.domain.model.OptionChainRow
import com.optix.app.domain.model.TradingIndex
import com.optix.app.presentation.theme.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionChainScreen(
    onNavigateToCalculator: (spotPrice: Double, strikePrice: Double, ltp: Double, isCall: Boolean, daysToExpiry: Int) -> Unit = { _, _, _, _, _ -> },
    onNavigateToOIAnalysis: () -> Unit = {},
    onNavigateToStrategy: () -> Unit = {},
    onNavigateToCharts: () -> Unit = {},
    onNavigateToScreener: () -> Unit = {},
    viewModel: OptionChainViewModel = hiltViewModel()
) {
    // Track screen view once on composition
    LaunchedEffect(Unit) { AnalyticsHelper.logScreenView("option_chain") }

    val state by viewModel.state.collectAsState()

    // Log detailed chain context (index, expiry, spot, VIX) each time a new spot price loads.
    // Guards against firing on the initial zero/null state.
    LaunchedEffect(state.spotPrice) {
        val spot = state.spotPrice
        if (spot != null && spot > 0) {
            AnalyticsHelper.logEvent("option_chain_view", Bundle().apply {
                putString("index", state.selectedIndex.symbol)
                putString("expiry", state.selectedExpiry ?: "none")
                putDouble("spot_price", spot)
                putBoolean("is_live", state.isLiveConnected)
                putString("connection_status", state.connectionStatus)
                state.indiaVix?.let { putDouble("india_vix", it) }
            })
        }
    }
    var showIndexPicker by remember { mutableStateOf(false) }

    // Get days to expiry from selected expiry
    val daysToExpiry = state.availableExpiries
        .find { it.value == state.selectedExpiry }
        ?.daysToExpiry ?: 7

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // iOS-Style Header
            IOSStyleHeader(
                selectedIndex = state.selectedIndex,
                spotPrice = state.spotPrice,
                priceChange = state.priceChange,
                priceChangePercent = state.priceChangePercent,
                isLiveConnected = state.isLiveConnected,
                connectionStatus = state.connectionStatus,
                onIndexClick = { showIndexPicker = true },
                onMenuClick = { /* TODO: Show option chain settings menu */ },
                onRefresh = { viewModel.refresh() },
                onChartClick = onNavigateToCharts
            )

            // Quick Stats Bar
            state.optionChainState.let { chainState ->
                if (chainState is Resource.Success && chainState.data != null) {
                    QuickStatsBar(chain = chainState.data, indiaVix = state.indiaVix)
                }
            }

            // Expiry Pills Row with All Strikes
            if (state.availableExpiries.isNotEmpty()) {
                ExpiryPillsWithFilter(
                    expiries = state.availableExpiries,
                    selectedExpiry = state.selectedExpiry,
                    totalStrikes = (state.optionChainState as? Resource.Success)?.data?.rows?.size ?: 0,
                    onExpirySelected = { viewModel.selectExpiry(it) }
                )
            }

            // Option Chain Content
            when (val chainState = state.optionChainState) {
                is Resource.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = PrimaryGreen,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading option chain...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is Resource.Error -> {
                    ErrorContent(
                        message = chainState.message,
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.weight(1f)
                    )
                }
                is Resource.Success -> {
                    chainState.data?.let { chain ->
                        IOSStyleOptionChainTable(
                            chain = chain,
                            onOptionClick = { strikePrice, ltp, isCall ->
                                onNavigateToCalculator(
                                    chain.spotPrice,
                                    strikePrice,
                                    ltp,
                                    isCall,
                                    daysToExpiry
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Floating Action Buttons (iOS Style)
        if (state.optionChainState is Resource.Success) {
            FloatingActionButtons(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                onNavigateToOIAnalysis = onNavigateToOIAnalysis,
                onNavigateToStrategyBuilder = onNavigateToStrategy,
                onNavigateToScreener = onNavigateToScreener
            )
        }
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
}

@Composable
fun IOSStyleHeader(
    selectedIndex: TradingIndex,
    spotPrice: Double?,
    priceChange: Double?,
    priceChangePercent: Double?,
    isLiveConnected: Boolean = false,
    connectionStatus: String = "Offline",
    onIndexClick: () -> Unit,
    onMenuClick: () -> Unit,
    onRefresh: () -> Unit,
    onChartClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Color based on connection state
    val statusColor = if (isLiveConnected) PrimaryGreen else WarningOrange

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Row: Index selector + LIVE badge + Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Index Selector with chart icon
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onIndexClick)
                    .padding(4.dp),
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
                    text = "${selectedIndex.symbol} Option Chain",
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

            // LIVE Badge + Menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // LIVE Badge (iOS style) - Shows actual connection status
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = statusColor.copy(alpha = if (isLiveConnected) pulseAlpha else 0.5f),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = connectionStatus,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isLiveConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Menu button
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Large Price Display (Centered like iOS)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = spotPrice?.let { formatLargePrice(it) } ?: "--",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 42.sp,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Change Badge + Today label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                priceChange?.let { change ->
                    val isPositive = change >= 0
                    val color = if (isPositive) PrimaryGreen else ErrorRed

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = color.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isPositive) "↑" else "↓",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${formatLargePrice(abs(change))} (${String.format("%.2f", abs(priceChangePercent ?: 0.0))}%)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = color
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mini Sparkline Chart - Clickable to open full charts
            MiniSparklineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onChartClick)
            )

            // Hint text to open charts
            Text(
                text = "Tap chart for detailed view",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun MiniSparklineChart(modifier: Modifier = Modifier) {
    val chartColor = PrimaryGreen

    // Sample data points for sparkline (simulated intraday movement)
    val dataPoints = remember {
        listOf(0.3f, 0.35f, 0.32f, 0.38f, 0.42f, 0.40f, 0.45f, 0.48f, 0.52f, 0.50f, 0.55f, 0.58f, 0.62f)
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val stepX = width / (dataPoints.size - 1)

        val minY = dataPoints.minOrNull() ?: 0f
        val maxY = dataPoints.maxOrNull() ?: 1f
        val rangeY = maxY - minY

        val path = Path()
        val fillPath = Path()

        dataPoints.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - ((value - minY) / rangeY * height * 0.8f) - height * 0.1f

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(width, height)
        fillPath.close()

        // Draw gradient fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    chartColor.copy(alpha = 0.3f),
                    chartColor.copy(alpha = 0.0f)
                )
            )
        )

        // Draw line
        drawPath(
            path = path,
            color = chartColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Data class holding calculated stats from option chain
 */
data class QuickStats(
    val pcr: Double,
    val atmIV: Double,
    val maxPain: Double,
    val support: Double,
    val resistance: Double
)

/**
 * Calculate quick stats from option chain data
 */
@Composable
fun rememberQuickStats(chain: OptionChain): QuickStats {
    return remember(chain) {
        // Calculate PCR (Put-Call Ratio) = Total Put OI / Total Call OI
        // Use full-chain totals from backend if available (accurate PCR across ALL strikes)
        val totalCallOI = chain.fullChainCallOI
            ?: chain.rows.sumOf { it.callData?.openInterest ?: 0L }
        val totalPutOI = chain.fullChainPutOI
            ?: chain.rows.sumOf { it.putData?.openInterest ?: 0L }
        val pcr = if (totalCallOI > 0) totalPutOI.toDouble() / totalCallOI else 1.0

        // Calculate ATM IV - get IV from ATM strike (average of call and put IV)
        val atmRow = chain.rows.minByOrNull { abs(it.strikePrice - chain.atmStrike) }
        val atmCallIV = atmRow?.callData?.impliedVolatility ?: 0.0
        val atmPutIV = atmRow?.putData?.impliedVolatility ?: 0.0
        val atmIV = when {
            atmCallIV > 0 && atmPutIV > 0 -> (atmCallIV + atmPutIV) / 2
            atmCallIV > 0 -> atmCallIV
            atmPutIV > 0 -> atmPutIV
            else -> 0.0
        }

        // Calculate Max Pain - strike where option writers lose minimum money
        // At each strike, calculate total money lost if price settles there
        val maxPain = calculateMaxPain(chain)

        // Calculate Support - highest Put OI strike below spot price
        val support = chain.rows
            .filter { it.strikePrice < chain.spotPrice && (it.putData?.openInterest ?: 0) > 0 }
            .maxByOrNull { it.putData?.openInterest ?: 0 }
            ?.strikePrice ?: chain.atmStrike

        // Calculate Resistance - highest Call OI strike above spot price
        val resistance = chain.rows
            .filter { it.strikePrice > chain.spotPrice && (it.callData?.openInterest ?: 0) > 0 }
            .maxByOrNull { it.callData?.openInterest ?: 0 }
            ?.strikePrice ?: (chain.atmStrike + 100)

        QuickStats(
            pcr = pcr,
            atmIV = atmIV,
            maxPain = maxPain,
            support = support,
            resistance = resistance
        )
    }
}

/**
 * Calculate Max Pain - strike where total option buyer loss is maximum
 * (which means option writers' profit is maximum)
 */
private fun calculateMaxPain(chain: OptionChain): Double {
    if (chain.rows.isEmpty()) return chain.atmStrike

    var maxPainStrike = chain.atmStrike
    var minWriterLoss = Double.MAX_VALUE

    for (row in chain.rows) {
        val settlePrice = row.strikePrice
        var totalLoss = 0.0

        // Calculate Call writers' loss at this settle price
        for (r in chain.rows) {
            val callOI = r.callData?.openInterest ?: 0
            if (callOI > 0) {
                // Call is ITM if settle > strike
                val intrinsicValue = maxOf(0.0, settlePrice - r.strikePrice)
                totalLoss += intrinsicValue * callOI
            }
        }

        // Calculate Put writers' loss at this settle price
        for (r in chain.rows) {
            val putOI = r.putData?.openInterest ?: 0
            if (putOI > 0) {
                // Put is ITM if settle < strike
                val intrinsicValue = maxOf(0.0, r.strikePrice - settlePrice)
                totalLoss += intrinsicValue * putOI
            }
        }

        if (totalLoss < minWriterLoss) {
            minWriterLoss = totalLoss
            maxPainStrike = settlePrice
        }
    }

    return maxPainStrike
}

@Composable
fun QuickStatsBar(
    chain: OptionChain,
    indiaVix: Double? = null
) {
    val stats = rememberQuickStats(chain)

    // Determine PCR color based on sentiment
    val pcrColor = when {
        stats.pcr > 1.2 -> PrimaryGreen  // Bullish (more puts = contrarian bullish)
        stats.pcr < 0.8 -> ErrorRed      // Bearish
        else -> WarningOrange            // Neutral
    }

    // ATM IV color based on value
    val ivColor = when {
        stats.atmIV > 30 -> ErrorRed      // High volatility
        stats.atmIV > 20 -> WarningOrange // Moderate
        else -> PrimaryGreen              // Low
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            StatChip(
                label = "PCR",
                value = String.format("%.2f", stats.pcr),
                color = pcrColor
            )
        }
        item {
            StatChip(
                label = "Max Pain",
                value = formatStrike(stats.maxPain),
                color = WarningOrange
            )
        }
        item {
            StatChip(
                label = "ATM IV",
                value = if (stats.atmIV > 0) String.format("%.1f%%", stats.atmIV) else "--",
                color = ivColor
            )
        }
        if (indiaVix != null && indiaVix > 0) {
            item {
                StatChip(
                    label = "India VIX",
                    value = String.format("%.1f", indiaVix),
                    color = when {
                        indiaVix > 20 -> ErrorRed
                        indiaVix > 15 -> WarningOrange
                        else -> PrimaryGreen
                    }
                )
            }
        }
        item {
            StatChip(
                label = "Support",
                value = formatStrike(stats.support),
                color = PrimaryGreen
            )
        }
        item {
            StatChip(
                label = "Resistance",
                value = formatStrike(stats.resistance),
                color = ErrorRed
            )
        }
    }
}

@Composable
fun StatChip(
    label: String,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
        }
    }
}

@Composable
fun ExpiryPillsWithFilter(
    expiries: List<ExpiryDate>,
    selectedExpiry: String?,
    totalStrikes: Int,
    onExpirySelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Expiry Pills (take most of the space)
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(expiries.take(4)) { expiry ->
                val isSelected = expiry.value == selectedExpiry
                val daysText = "${expiry.daysToExpiry}D"
                val daysColor = when {
                    expiry.daysToExpiry <= 2 -> ErrorRed
                    expiry.daysToExpiry <= 7 -> WarningOrange
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onExpirySelected(expiry.value) },
                    color = if (isSelected) PrimaryGreen else MaterialTheme.colorScheme.surface,
                    border = if (isSelected) null else BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = formatExpiryShort(expiry.displayDate),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = daysText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White.copy(alpha = 0.8f) else daysColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // All Strikes Button
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { /* TODO: Toggle between near-ATM and all strikes view */ },
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "All Strikes $totalStrikes",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryGreen
                )
            }
        }
    }
}

@Composable
fun IOSStyleOptionChainTable(
    chain: OptionChain,
    onOptionClick: (strikePrice: Double, ltp: Double, isCall: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxCallOI = chain.rows.mapNotNull { it.callData?.openInterest }.maxOrNull() ?: 1L
    val maxPutOI = chain.rows.mapNotNull { it.putData?.openInterest }.maxOrNull() ?: 1L

    // Find ATM index for auto-scroll
    val atmIndex = chain.rows.indexOfFirst { abs(it.strikePrice - chain.atmStrike) < 1 }
    val listState = rememberLazyListState()

    // Auto-scroll to ATM strike on first load
    LaunchedEffect(chain.atmStrike) {
        if (atmIndex >= 0) {
            // Scroll to ATM with some offset to center it
            val scrollToIndex = maxOf(0, atmIndex - 3)
            listState.animateScrollToItem(scrollToIndex)
        }
    }

    Column(modifier = modifier) {
        // Table Header (iOS style)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OI",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CallColor,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "LTP",
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "STRIKE",
                    modifier = Modifier.weight(1.3f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "LTP",
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "OI",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PutColor,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Table Body with padding for FABs
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            itemsIndexed(chain.rows) { index, row ->
                val isAtm = chain.atmStrike.let { abs(row.strikePrice - it) < 1 }
                val isCallITM = row.strikePrice < chain.spotPrice
                val isPutITM = row.strikePrice > chain.spotPrice

                IOSStyleOptionChainRow(
                    row = row,
                    isAtm = isAtm,
                    isCallITM = isCallITM,
                    isPutITM = isPutITM,
                    maxCallOI = maxCallOI,
                    maxPutOI = maxPutOI,
                    onCallClick = {
                        row.callData?.let { call ->
                            onOptionClick(row.strikePrice, call.lastPrice, true)
                        }
                    },
                    onPutClick = {
                        row.putData?.let { put ->
                            onOptionClick(row.strikePrice, put.lastPrice, false)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun IOSStyleOptionChainRow(
    row: OptionChainRow,
    isAtm: Boolean,
    isCallITM: Boolean,
    isPutITM: Boolean,
    maxCallOI: Long,
    maxPutOI: Long,
    onCallClick: () -> Unit = {},
    onPutClick: () -> Unit = {}
) {
    val callOIPercent = row.callData?.openInterest?.let { it.toFloat() / maxCallOI } ?: 0f
    val putOIPercent = row.putData?.openInterest?.let { it.toFloat() / maxPutOI } ?: 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Call Side (Clickable)
        Row(
            modifier = Modifier
                .weight(2.2f)
                .fillMaxHeight()
                .background(if (isCallITM) CallITMBackground else Color.Transparent)
                .clickable(enabled = row.callData != null) { onCallClick() }
                .padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Call OI with bar
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = row.callData?.openInterest?.let { formatOI(it) } ?: "-",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // OI Bar
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(CallColor.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(callOIPercent)
                                .align(Alignment.BottomCenter)
                                .background(CallColor)
                        )
                    }
                }
            }

            // Call LTP
            Text(
                text = row.callData?.lastPrice?.let { formatLTP(it) } ?: "-",
                modifier = Modifier.weight(1.2f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            )
        }

        // Strike Price (Center) - iOS style with ATM highlight
        Box(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
                .background(
                    if (isAtm) PrimaryGreen.copy(alpha = 0.15f)
                    else Color.Transparent
                )
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatStrike(row.strikePrice),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isAtm) PrimaryGreen else MaterialTheme.colorScheme.onSurface
                )
                if (isAtm) {
                    Text(
                        text = "ATM",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Put Side (Clickable)
        Row(
            modifier = Modifier
                .weight(2.2f)
                .fillMaxHeight()
                .background(if (isPutITM) PutITMBackground else Color.Transparent)
                .clickable(enabled = row.putData != null) { onPutClick() }
                .padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Put LTP
            Text(
                text = row.putData?.lastPrice?.let { formatLTP(it) } ?: "-",
                modifier = Modifier.weight(1.2f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start
            )

            // Put OI with bar
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // OI Bar
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(PutColor.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(putOIPercent)
                                .align(Alignment.BottomCenter)
                                .background(PutColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = row.putData?.openInterest?.let { formatOI(it) } ?: "-",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
        thickness = 0.5.dp
    )
}

@Composable
fun FloatingActionButtons(
    modifier: Modifier = Modifier,
    onNavigateToOIAnalysis: () -> Unit = {},
    onNavigateToStrategyBuilder: () -> Unit = {},
    onNavigateToScreener: () -> Unit = {}
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Screener FAB (iOS-style orange gradient pill)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(AccentOrange, ErrorRed)
                    )
                )
                .clickable { onNavigateToScreener() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Screener",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // OI Analysis Button
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onNavigateToOIAnalysis() },
                color = Color(0xFF2D2D2D),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OI Analysis",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            // Build Strategy Button (Gradient)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF8B5CF6),
                                Color(0xFF06B6D4)
                            )
                        )
                    )
                    .clickable { onNavigateToStrategyBuilder() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Build Strategy",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorContent(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message ?: "Something went wrong",
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
fun IndexPickerContent(
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

        TradingIndex.entries.forEach { index ->
            val isSelected = index == selectedIndex

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onIndexSelected(index) },
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

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// Utility functions
private fun formatLargePrice(value: Double): String {
    return String.format("%.2f", value)
}

private fun formatStrike(value: Double): String {
    return String.format("%.0f", value)
}

private fun formatLTP(value: Double): String {
    return String.format("%.2f", value)
}

private fun formatOI(value: Long): String {
    return when {
        value >= 10_000_000 -> String.format("%.0fCr", value / 10_000_000.0)
        value >= 100_000 -> String.format("%.0fL", value / 100_000.0)
        value >= 1000 -> String.format("%.0fK", value / 1000.0)
        else -> value.toString()
    }
}

private fun formatExpiryShort(date: String): String {
    // Convert "10-Feb-2026" to "10 Feb"
    return try {
        val parts = date.split("-")
        "${parts[0]} ${parts[1]}"
    } catch (e: Exception) {
        date
    }
}
