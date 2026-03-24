package com.optix.app.presentation.screens.ai

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.domain.model.*

// Clean iOS-like Colors
private val AIGreen = Color(0xFF34C759)
private val AIRed = Color(0xFFFF3B30)
private val AIPurple = Color(0xFFAF52DE)
private val AIBlue = Color(0xFF007AFF)
private val AIOrange = Color(0xFFFF9500)
private val AIGray = Color(0xFF8E8E93)
private val LightGray = Color(0xFFF2F2F7)
private val CardWhite = Color(0xFFFFFFFF)
private val BackgroundGray = Color(0xFFF8F8FA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIInsightsScreen(
    viewModel: AIInsightsViewModel = hiltViewModel(),
    onNavigateToChat: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var showIndexPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGray)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            AIHeader(
                selectedIndex = state.selectedIndex.symbol,
                suggestionsCount = state.totalSuggestions,
                isAnalyzing = state.isAnalyzing,
                onRefresh = { viewModel.refresh() },
                onIndexTap = { showIndexPicker = true }
            )

            // Main Content
            when {
                state.isAnalyzing -> LoadingView()
                state.hasAnalysis -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        // Expiry Chips
                        if (state.availableExpiries.isNotEmpty()) {
                            item {
                                ExpiryChipsRow(
                                    expiries = state.availableExpiries,
                                    selectedExpiry = state.selectedExpiry,
                                    onSelect = { viewModel.selectExpiry(it) }
                                )
                            }
                        }

                        // Market Summary Card (like iOS)
                        item {
                            MarketSummaryCard(
                                marketBias = state.marketBias,
                                spotPrice = state.spotPrice,
                                pcr = state.pcr,
                                maxPain = state.maxPain,
                                suggestionsCount = state.totalSuggestions,
                                indiaVix = state.indiaVix
                            )
                        }

                        // AI Risk Sentinel
                        item {
                            RiskSentinelCard(
                                alerts = buildRiskAlerts(state)
                            )
                        }

                        // Support/Resistance Row
                        if (state.hasTechnicalAnalysis) {
                            item {
                                SupportResistanceRow(
                                    support = state.technicalAnalysis.supportLevels.firstOrNull() ?: 0.0,
                                    resistance = state.technicalAnalysis.resistanceLevels.take(2)
                                )
                            }
                        }

                        // Tab Selector
                        item {
                            TabSelector(
                                selectedTab = state.selectedTab,
                                callCount = state.allSuggestions.count { it.optionType == OptionType.CALL },
                                putCount = state.allSuggestions.count { it.optionType == OptionType.PUT },
                                onTabChange = { viewModel.selectTab(it) }
                            )
                        }

                        // Content based on tab
                        if (state.selectedTab == InsightTab.MARKET) {
                            if (state.marketInsights.isEmpty()) {
                                item { EmptyTabView("No market insights available") }
                            } else {
                                items(state.marketInsights) { insight ->
                                    InsightCard(insight = insight)
                                }
                            }
                        } else {
                            val suggestions = when (state.selectedTab) {
                                InsightTab.CALLS -> state.allSuggestions.filter { it.optionType == OptionType.CALL }
                                InsightTab.PUTS -> state.allSuggestions.filter { it.optionType == OptionType.PUT }
                                else -> state.allSuggestions
                            }

                            if (suggestions.isEmpty()) {
                                item { EmptyTabView("No ${state.selectedTab.displayName.lowercase()} suggestions") }
                            } else {
                                items(suggestions) { suggestion ->
                                    SuggestionCard(
                                        suggestion = suggestion,
                                        onClick = { viewModel.selectSuggestion(suggestion) }
                                    )
                                }
                            }
                        }

                        // Disclaimer
                        item {
                            DisclaimerBanner()
                        }
                    }
                }
                else -> EmptyStateView(onAnalyze = { viewModel.refresh() })
            }
        }

        // Note: FloatingChatButton is provided by MainActivity, no need for duplicate here

        // Bottom Sheets
        if (showIndexPicker) {
            ModalBottomSheet(
                onDismissRequest = { showIndexPicker = false },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                IndexPickerSheet(
                    selectedIndex = state.selectedIndex,
                    indices = state.availableIndices,
                    onSelect = {
                        viewModel.selectIndex(it)
                        showIndexPicker = false
                    }
                )
            }
        }

        if (state.showSuggestionDetail && state.selectedSuggestion != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissSuggestionDetail() },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                SuggestionDetailSheet(
                    suggestion = state.selectedSuggestion!!,
                    onDismiss = { viewModel.dismissSuggestionDetail() },
                    onAskAI = { suggestion ->
                        viewModel.dismissSuggestionDetail()
                        val optionType = if (suggestion.optionType == OptionType.CALL) "CE" else "PE"
                        val query = "Analyze ${suggestion.strikePrice.toInt()} $optionType"
                        onNavigateToChat(query)
                    }
                )
            }
        }
    }
}

// ====================== HEADER ======================

@Composable
private fun AIHeader(
    selectedIndex: String,
    suggestionsCount: Int,
    isAnalyzing: Boolean,
    onRefresh: () -> Unit,
    onIndexTap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.linearGradient(listOf(AIPurple, Color(0xFFFF2D55))),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        "AI Insights",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Row(
                        modifier = Modifier.clickable { onIndexTap() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = AIGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            selectedIndex,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AIGreen
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = AIGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("•", color = AIGray, fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        Text(
                            "Updated ${timeFormat.format(java.util.Date())}",
                            fontSize = 12.sp,
                            color = AIGray
                        )
                    }
                }
            }

            val rotation by animateFloatAsState(
                targetValue = if (isAnalyzing) 360f else 0f,
                animationSpec = if (isAnalyzing) infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ) else tween(0),
                label = "refresh"
            )

            Surface(
                onClick = onRefresh,
                shape = CircleShape,
                color = AIBlue.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = AIBlue,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotation)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFFE5E5EA))
    }
}

// ====================== EXPIRY CHIPS ======================

@Composable
private fun ExpiryChipsRow(
    expiries: List<ExpiryDate>,
    selectedExpiry: ExpiryDate?,
    onSelect: (ExpiryDate) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(expiries) { expiry ->
            val isSelected = expiry == selectedExpiry
            // iOS style: 0D is red, <=7D is orange, else gray/white
            val daysColor = when {
                expiry.daysToExpiry == 0 -> AIRed
                expiry.daysToExpiry <= 7 -> AIOrange
                else -> if (isSelected) Color.White else AIGray
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) {
                            Brush.linearGradient(listOf(Color(0xFFBF5AF2), Color(0xFFFF375F)))
                        } else {
                            Brush.linearGradient(listOf(CardWhite, CardWhite))
                        }
                    )
                    .border(
                        width = if (isSelected) 0.dp else 1.dp,
                        color = Color(0xFFE5E5EA),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelect(expiry) }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        expiry.shortDisplayString,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF3A3A3C)
                    )
                    Text(
                        "${expiry.daysToExpiry}D",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = daysColor
                    )
                }
            }
        }
    }
}

// ====================== MARKET SUMMARY CARD ======================

@Composable
private fun MarketSummaryCard(
    marketBias: MarketBias,
    spotPrice: Double,
    pcr: Double,
    maxPain: Double,
    suggestionsCount: Int,
    indiaVix: Double
) {
    val biasColor = when (marketBias) {
        MarketBias.STRONG_BULLISH, MarketBias.BULLISH -> AIGreen
        MarketBias.STRONG_BEARISH, MarketBias.BEARISH -> AIRed
        else -> AIOrange
    }

    val biasIcon = when (marketBias) {
        MarketBias.STRONG_BULLISH, MarketBias.BULLISH -> Icons.Default.TrendingUp
        MarketBias.STRONG_BEARISH, MarketBias.BEARISH -> Icons.Default.TrendingDown
        else -> Icons.Default.TrendingFlat
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = CardWhite,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row - iOS style: Market Bias on left, Suggestions count on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left: Market Bias
                Column {
                    Text(
                        "Market Bias",
                        fontSize = 12.sp,
                        color = AIGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            biasIcon,
                            contentDescription = null,
                            tint = biasColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            marketBias.displayName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = biasColor
                        )
                    }
                }

                // Right: Suggestions count (iOS style - just number + text)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$suggestionsCount",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Text(
                        "Suggestions",
                        fontSize = 12.sp,
                        color = AIGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Market Stats Row - iOS style with colored icons above each stat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MarketStatItemWithIcon(
                    label = "Spot",
                    value = String.format("%.2f", spotPrice),
                    iconColor = AIRed,
                    icon = Icons.Default.RadioButtonChecked
                )
                MarketStatItemWithIcon(
                    label = "PCR",
                    value = String.format("%.2f", pcr),
                    iconColor = AIBlue,
                    icon = Icons.Default.SwapVert
                )
                MarketStatItemWithIcon(
                    label = "Max Pain",
                    value = String.format("%.0f", maxPain),
                    iconColor = AIPurple,
                    icon = Icons.Default.Adjust
                )
            }
        }
    }
}

private data class RiskAlert(
    val title: String,
    val detail: String,
    val severity: ConfidenceLevel
)

private fun buildRiskAlerts(state: AIInsightsState): List<RiskAlert> {
    val alerts = mutableListOf<RiskAlert>()

    if (state.indiaVix > 25) {
        alerts.add(RiskAlert("Extreme VIX", "VIX ${String.format("%.1f", state.indiaVix)} — premiums very expensive", ConfidenceLevel.HIGH))
    } else if (state.indiaVix > 20) {
        alerts.add(RiskAlert("High VIX", "VIX ${String.format("%.1f", state.indiaVix)} — volatility elevated", ConfidenceLevel.MEDIUM))
    }

    if (state.ivPercentile > 70) {
        alerts.add(RiskAlert("High IV Percentile", "IV Percentile ${String.format("%.0f", state.ivPercentile)}% — premiums expensive", ConfidenceLevel.MEDIUM))
    }

    val termStructure = when {
        state.indiaVix > 0 && state.atmIV - state.indiaVix >= 3 -> "Inverted"
        state.indiaVix > 0 && state.atmIV - state.indiaVix <= -3 -> "Contango"
        else -> "Flat"
    }
    if (termStructure == "Inverted") {
        alerts.add(RiskAlert("Inverted Term Structure", "Front‑month IV higher — event premium risk", ConfidenceLevel.MEDIUM))
    }

    val intraday = state.intradayChangePct ?: 0.0
    if (kotlin.math.abs(intraday) >= 0.7) {
        alerts.add(RiskAlert("Large Intraday Move", "Move ${String.format("%.2f", intraday)}% — avoid tight stops", ConfidenceLevel.MEDIUM))
    }

    if (alerts.isEmpty()) {
        alerts.add(RiskAlert("Risk Normal", "No major risk flags detected", ConfidenceLevel.LOW))
    }

    return alerts.take(4)
}

@Composable
private fun RiskSentinelCard(
    alerts: List<RiskAlert>
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = CardWhite,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI Risk Sentinel", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Live",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color(0xFFFF8A00), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            alerts.forEach { alert ->
                val color = when (alert.severity) {
                    ConfidenceLevel.HIGH -> AIRed
                    ConfidenceLevel.MEDIUM -> Color(0xFFFF8A00)
                    ConfidenceLevel.LOW -> AIGreen
                }
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(color, shape = CircleShape)
                            .padding(top = 3.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(alert.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                        Text(alert.detail, fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketStatItemWithIcon(
    label: String,
    value: String,
    iconColor: Color,
    icon: ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.Black
        )
        Text(
            label,
            fontSize = 10.sp,
            color = AIGray
        )
    }
}

@Composable
private fun MarketStatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = 11.sp,
            color = AIGray,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

// ====================== SUPPORT/RESISTANCE ROW ======================

@Composable
private fun SupportResistanceRow(
    support: Double,
    resistance: List<Double>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = AIGreen.copy(alpha = 0.12f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Support",
                    fontSize = 11.sp,
                    color = AIGray
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    String.format("%.0f", support),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AIGreen
                )
            }
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = AIRed.copy(alpha = 0.12f)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Resistance",
                    fontSize = 11.sp,
                    color = AIGray
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    resistance.firstOrNull()?.let { String.format("%.0f", it) } ?: "-",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AIRed
                )
            }
        }
    }
}

// ====================== TAB SELECTOR ======================

@Composable
private fun TabSelector(
    selectedTab: InsightTab,
    callCount: Int,
    putCount: Int,
    onTabChange: (InsightTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = CardWhite,
        border = BorderStroke(1.dp, Color(0xFFE5E5EA))
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            InsightTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val tabColor = when (tab) {
                    InsightTab.MARKET -> AIPurple
                    InsightTab.CALLS -> AIGreen
                    InsightTab.PUTS -> AIRed
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) tabColor else Color.Transparent)
                        .clickable { onTabChange(tab) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab.displayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) Color.White else AIGray
                    )
                }
            }
        }
    }
}

// ====================== SUGGESTION CARD ======================

@Composable
private fun SuggestionCard(
    suggestion: AITradeSuggestion,
    onClick: () -> Unit
) {
    val optionColor = if (suggestion.optionType == OptionType.CALL) AIGreen else AIRed
    val isHighScore = suggestion.score >= 70

    // Action-based styling (matching iOS - STRONG_BUY/BUY get green, STRONG_SELL/SELL get red)
    val actionColor = when (suggestion.action) {
        TradeDirection.STRONG_BUY, TradeDirection.BUY -> AIGreen
        TradeDirection.STRONG_SELL, TradeDirection.SELL -> AIRed
        TradeDirection.HOLD -> Color(0xFF2C2C2E) // HOLD - dark gray like iOS
    }
    // Use displayText from enum to show "STRONG BUY" etc.
    val actionText = suggestion.action.displayText
    val topReason = suggestion.reasoning.firstOrNull()?.description ?: "Trade with caution"

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = CardWhite,
        border = if (isHighScore) BorderStroke(1.dp, actionColor.copy(alpha = 0.3f)) else null,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Strike + Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        String.format("%.0f", suggestion.strikePrice),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = optionColor
                    ) {
                        Text(
                            if (suggestion.optionType == OptionType.CALL) "CE" else "PE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // ML Signal Badge + Score
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Signal Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = actionColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                actionText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = actionColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${suggestion.score}%",
                                fontSize = 10.sp,
                                color = actionColor
                            )
                        }
                    }

                    // Score Circle
                    ScoreCircle(score = suggestion.score, size = 44.dp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Price Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PriceItem("Entry", suggestion.entryPrice, Color.Black)
                PriceItem("Target", suggestion.targetPrice, AIGreen)
                PriceItem("Stop Loss", suggestion.stopLoss, AIRed)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFE5E5EA), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // R:R
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = AIBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // iOS format: "R:R 1:1.5"
                    Text(
                        "R:R 1:${String.format("%.1f", suggestion.riskReward)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (suggestion.riskReward >= 2) AIGreen else AIGray
                    )
                }

                // Profit/Loss - iOS style with arrows
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.NorthEast,
                        contentDescription = null,
                        tint = AIGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        "+${String.format("%.0f", suggestion.potentialReturn)}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AIGreen
                    )
                    Text("  /  ", color = AIGray, fontSize = 12.sp)
                    Icon(
                        Icons.Default.SouthEast,
                        contentDescription = null,
                        tint = AIRed,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        "-${String.format("%.0f", suggestion.maxLoss)}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AIRed
                    )
                }

                // Timeframe - iOS shows "Intraday"
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF2F2F7)
                ) {
                    Text(
                        "Intraday",
                        fontSize = 11.sp,
                        color = AIGray,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Reasoning hint - iOS style with [HIGHER RISK] label
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = AIOrange,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    topReason,
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = AIGray,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Risk Warning removed - consolidated into reasoning hint above
            val showRiskWarning = false

            if (showRiskWarning) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = AIRed.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = AIRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Options trading involves risk. Trade with caution.",
                            fontSize = 11.sp,
                            color = AIRed.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceItem(label: String, price: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = 10.sp,
            color = AIGray
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "₹${String.format("%.2f", price)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

@Composable
private fun ScoreCircle(score: Int, size: Dp) {
    val scoreColor = when {
        score >= 70 -> AIGreen
        score >= 50 -> AIOrange
        else -> AIRed
    }

    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "score"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Background
            drawArc(
                color = scoreColor.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx())
            )
            // Progress
            drawArc(
                color = scoreColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Text(
            score.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = scoreColor
        )
    }
}

// ====================== INSIGHT CARD ======================

@Composable
private fun InsightCard(insight: MarketInsight) {
    val sentimentColor = when (insight.sentiment) {
        MarketSentiment.BULLISH -> AIGreen
        MarketSentiment.BEARISH -> AIRed
        else -> AIOrange
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = CardWhite,
        border = BorderStroke(1.dp, sentimentColor.copy(alpha = 0.2f)),
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(sentimentColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (insight.sentiment) {
                        MarketSentiment.BULLISH -> Icons.Default.TrendingUp
                        MarketSentiment.BEARISH -> Icons.Default.TrendingDown
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = sentimentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    insight.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    insight.description,
                    fontSize = 14.sp,
                    color = AIGray,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// ====================== OTHER VIEWS ======================

@Composable
private fun LoadingView() {
    val rotation by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .rotate(rotation)
                    .border(
                        4.dp,
                        Brush.sweepGradient(listOf(AIPurple, Color(0xFFFF2D55), AIPurple)),
                        CircleShape
                    )
            )
            Icon(
                Icons.Default.Psychology,
                contentDescription = null,
                tint = AIPurple,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Analyzing Options",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Scanning OI, IV and Greeks...",
            fontSize = 15.sp,
            color = AIGray
        )
    }
}

@Composable
private fun EmptyStateView(onAnalyze: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Insights,
            contentDescription = null,
            tint = AIGray,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "No Analysis Yet",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Tap analyze to get AI-powered\ntrade suggestions",
            fontSize = 15.sp,
            color = AIGray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAnalyze,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier.background(
                Brush.linearGradient(listOf(AIPurple, Color(0xFFFF2D55))),
                RoundedCornerShape(14.dp)
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Analyze Now", fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun EmptyTabView(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = AIGray.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            message,
            fontSize = 15.sp,
            color = AIGray
        )
    }
}

@Composable
private fun DisclaimerBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = AIOrange.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = AIOrange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "SEBI Disclaimer",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AIOrange
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Investment in securities market is subject to market risks. AI-generated suggestions are for informational and educational purposes only and do not constitute investment advice. Past performance does not guarantee future results. Consult a SEBI-registered investment advisor before making any trading decisions.",
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ====================== BOTTOM SHEETS ======================

@Composable
private fun IndexPickerSheet(
    selectedIndex: TradingIndex,
    indices: List<TradingIndex>,
    onSelect: (TradingIndex) -> Unit
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            "Select Index",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(20.dp))

        indices.forEach { index ->
            val indexColor = when (index) {
                TradingIndex.NIFTY50 -> AIBlue
                TradingIndex.BANKNIFTY -> AIGreen
                TradingIndex.FINNIFTY -> AIOrange
                TradingIndex.MIDCPNIFTY -> AIPurple
                else -> AIGray
            }
            val isSelected = index == selectedIndex

            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) indexColor.copy(alpha = 0.1f) else CardWhite,
                border = if (isSelected) BorderStroke(2.dp, indexColor.copy(alpha = 0.5f)) else BorderStroke(1.dp, Color(0xFFE5E5EA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(indexColor.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ShowChart,
                                contentDescription = null,
                                tint = indexColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                index.displayName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                            Text(
                                "Lot: ${index.lotSize}",
                                fontSize = 13.sp,
                                color = AIGray
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = indexColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SuggestionDetailSheet(
    suggestion: AITradeSuggestion,
    onDismiss: () -> Unit,
    onAskAI: (AITradeSuggestion) -> Unit
) {
    val optionColor = if (suggestion.optionType == OptionType.CALL) AIGreen else AIRed
    val winProb = ((suggestion.score * 0.8) + 10).toInt().coerceIn(50, 95)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Trade Details",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AIGray)
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = optionColor.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                String.format("%.0f", suggestion.strikePrice),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(shape = RoundedCornerShape(8.dp), color = optionColor) {
                                Text(
                                    if (suggestion.optionType == OptionType.CALL) "CE" else "PE",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(suggestion.expiry, fontSize = 15.sp, color = AIGray)
                    }

                    ScoreCircle(score = suggestion.score, size = 72.dp)
                }
            }
        }

        item {
            Button(
                onClick = { onAskAI(suggestion) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.linearGradient(listOf(AIPurple, Color(0xFFFF2D55))),
                        RoundedCornerShape(14.dp)
                    ),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ask AI About This Trade", fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardWhite,
                border = BorderStroke(1.dp, AIBlue.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("AI Analysis", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Win Probability", fontSize = 13.sp, color = AIGray)
                            Text("$winProb%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AIGreen)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Confidence", fontSize = 13.sp, color = AIGray)
                            Text(
                                if (suggestion.score >= 75) "High" else "Medium",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (suggestion.score >= 75) AIGreen else AIOrange
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardWhite,
                shadowElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Price Targets", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DetailPriceItem("Entry", suggestion.entryPrice, Color.Black)
                        DetailPriceItem("Target", suggestion.targetPrice, AIGreen)
                        DetailPriceItem("Stop Loss", suggestion.stopLoss, AIRed)
                    }
                }
            }
        }

        if (suggestion.reasoning.isNotEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = CardWhite,
                    shadowElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text("Score Breakdown", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(14.dp))
                        suggestion.reasoning.forEach { reason ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(reason.factor, fontSize = 15.sp, color = Color.DarkGray)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${reason.score}/${reason.maxScore}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (reason.isPositive) AIGreen else AIRed
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        if (reason.isPositive) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (reason.isPositive) AIGreen else AIRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(40.dp)) }
    }
}

@Composable
private fun DetailPriceItem(label: String, value: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp, color = AIGray)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "₹${String.format("%.2f", value)}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

private val ExpiryDate.displayString: String
    get() = displayDate.take(11)

// iOS style short format: "17 Feb" instead of "17-Feb-2026"
private val ExpiryDate.shortDisplayString: String
    get() {
        // Parse from displayDate (format: "17-Feb-2026" or similar)
        val parts = displayDate.split("-")
        return if (parts.size >= 2) {
            "${parts[0]} ${parts[1]}"  // "17 Feb"
        } else {
            displayDate.take(6)
        }
    }
