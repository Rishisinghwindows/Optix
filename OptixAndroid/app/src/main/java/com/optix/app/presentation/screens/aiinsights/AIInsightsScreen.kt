package com.optix.app.presentation.screens.aiinsights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.domain.model.AITradeSuggestion
import com.optix.app.domain.model.ConfidenceLevel
import com.optix.app.domain.model.MarketSentiment
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.PersonalizedAITradeSuggestion
import com.optix.app.domain.model.TradeDirection
import com.optix.app.presentation.screens.aiinsights.components.DetailedAnalysisSheet
import com.optix.app.presentation.screens.aiinsights.components.SuggestionListLoadingSkeleton
import kotlinx.coroutines.flow.collectLatest

// Colors matching iOS
private val GreenPrimary = Color(0xFF00C805)  // iOS green
private val GreenLight = Color(0xFFDCFCE7)
private val RedPrimary = Color(0xFFFF3B30)    // iOS red
private val RedLight = Color(0xFFFEE2E2)
private val AmberPrimary = Color(0xFFF59E0B)
private val AmberLight = Color(0xFFFEF3C7)
private val BluePrimary = Color(0xFF007AFF)   // iOS blue
private val PurplePrimary = Color(0xFFBF5AF2) // iOS purple gradient start
private val PinkPrimary = Color(0xFFFF375F)   // iOS purple gradient end

/**
 * Main AI Insights Screen - iOS Style
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIInsightsScreen(
    onNavigateToTrade: (String) -> Unit = {},
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: AIInsightsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AIInsightsEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is AIInsightsEvent.NavigateToTrade -> {
                    onNavigateToTrade(event.suggestion.id)
                }
                is AIInsightsEvent.NavigateToChat -> {
                    onNavigateToChat(event.context)
                }
                is AIInsightsEvent.NavigateToSettings -> {
                    onNavigateToSettings()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AIInsightsTopBar(
                selectedSymbol = uiState.selectedSymbol,
                suggestionCount = uiState.personalizedSuggestions.size,
                onRefresh = { viewModel.refresh() },
                onSymbolClick = { /* TODO: show symbol picker */ },
                isRefreshing = uiState.isRefreshing
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.error != null -> {
                    ErrorContent(
                        error = uiState.error!!,
                        onRetry = { viewModel.loadInsights() }
                    )
                }
                else -> {
                    AIInsightsContent(
                        uiState = uiState,
                        filteredSuggestions = viewModel.getFilteredSuggestions(),
                        onOptionTypeFilterSelect = { viewModel.setOptionTypeFilter(it) },
                        onSuggestionClick = { viewModel.selectSuggestion(it) }
                    )
                }
            }
        }

        // Detail sheet
        if (uiState.showDetailSheet && uiState.selectedSuggestion != null) {
            DetailedAnalysisSheet(
                suggestion = uiState.selectedSuggestion!!,
                isInWatchlist = viewModel.isInWatchlist(uiState.selectedSuggestion!!.baseSuggestion),
                onDismiss = {
                    viewModel.clearAIExplanation()
                    viewModel.closeDetailSheet()
                },
                onAddToWatchlist = {
                    viewModel.addToWatchlist(uiState.selectedSuggestion!!.baseSuggestion)
                },
                onCreateAlert = { /* TODO */ },
                onTrade = {
                    viewModel.openTrade(uiState.selectedSuggestion!!.baseSuggestion)
                },
                onAskAI = {
                    viewModel.askAIAboutTrade(uiState.selectedSuggestion!!)
                },
                isAskingAI = uiState.isAskingAI,
                aiExplanation = uiState.aiExplanation,
                askAIError = uiState.askAIError
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIInsightsTopBar(
    selectedSymbol: String,
    suggestionCount: Int,
    onRefresh: () -> Unit,
    onSymbolClick: () -> Unit,
    isRefreshing: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Brain icon with gradient background
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(PurplePrimary, PinkPrimary)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🧠",
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "AI Insights",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )

                    // Symbol selector row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onSymbolClick() }
                    ) {
                        Text(
                            text = "📈",
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = selectedSymbol,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$suggestionCount suggestions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Refresh button
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = BluePrimary.copy(alpha = 0.15f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = BluePrimary
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SuggestionListLoadingSkeleton(count = 4)
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Failed to load insights",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun AIInsightsContent(
    uiState: AIInsightsUiState,
    filteredSuggestions: List<PersonalizedAITradeSuggestion>,
    onOptionTypeFilterSelect: (String?) -> Unit,
    onSuggestionClick: (PersonalizedAITradeSuggestion) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Market Sentiment Pill
        uiState.analysisResult?.let { result ->
            item {
                MarketSentimentPill(sentiment = result.marketSentiment)
            }

            // Market Metrics Card
            item {
                MarketMetricsCard(
                    pcr = result.pcr,
                    ivPercentile = result.ivPercentile,
                    maxPain = result.maxPain,
                    spotPrice = result.spotPrice,
                    indiaVix = result.indiaVix
                )
            }
        }

        // Option Type Filter Chips
        item {
            OptionTypeFilterChips(
                selectedFilter = selectedFilter,
                onFilterSelect = { filter ->
                    selectedFilter = filter
                    onOptionTypeFilterSelect(filter)
                }
            )
        }

        // Suggestions List
        val filtered = if (selectedFilter == null) {
            filteredSuggestions
        } else {
            filteredSuggestions.filter { suggestion ->
                when (selectedFilter) {
                    "Calls" -> suggestion.baseSuggestion.optionType == OptionType.CALL
                    "Puts" -> suggestion.baseSuggestion.optionType == OptionType.PUT
                    else -> true
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No suggestions available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(
                items = filtered,
                key = { it.baseSuggestion.id }
            ) { suggestion ->
                IOSStyleSuggestionCard(
                    suggestion = suggestion,
                    onClick = { onSuggestionClick(suggestion) }
                )
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun MarketSentimentPill(
    sentiment: MarketSentiment
) {
    val backgroundColor = when (sentiment) {
        MarketSentiment.BULLISH -> GreenLight
        MarketSentiment.BEARISH -> RedLight
        MarketSentiment.NEUTRAL -> Color(0xFFF3F4F6)
    }
    val textColor = when (sentiment) {
        MarketSentiment.BULLISH -> GreenPrimary
        MarketSentiment.BEARISH -> RedPrimary
        MarketSentiment.NEUTRAL -> Color(0xFF6B7280)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sentiment.emoji,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Market Sentiment: ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = sentiment.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = textColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (sentiment == MarketSentiment.BULLISH) "📈" else if (sentiment == MarketSentiment.BEARISH) "📉" else "➡️",
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun MarketMetricsCard(
    pcr: Double,
    ivPercentile: Double,
    maxPain: Double,
    spotPrice: Double,
    indiaVix: Double? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricItem(
                label = "PCR",
                value = String.format("%.2f", pcr),
                valueColor = if (pcr > 1.0) GreenPrimary else if (pcr < 0.8) RedPrimary else AmberPrimary
            )
            MetricDivider()
            MetricItem(
                label = "IV Percentile",
                value = "${ivPercentile.toInt()}%",
                valueColor = if (ivPercentile < 30) GreenPrimary else if (ivPercentile > 70) RedPrimary else AmberPrimary
            )
            MetricDivider()
            MetricItem(
                label = "Max Pain",
                value = String.format("%.0f", maxPain),
                valueColor = BluePrimary
            )
            MetricDivider()
            MetricItem(
                label = "Spot",
                value = String.format("%.0f", spotPrice),
                valueColor = MaterialTheme.colorScheme.onSurface
            )
            if (indiaVix != null && indiaVix > 0) {
                MetricDivider()
                MetricItem(
                    label = "India VIX",
                    value = String.format("%.1f", indiaVix),
                    valueColor = if (indiaVix > 20) RedPrimary else if (indiaVix > 15) AmberPrimary else GreenPrimary
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun OptionTypeFilterChips(
    selectedFilter: String?,
    onFilterSelect: (String?) -> Unit
) {
    val filters = listOf("All Suggestions", "Calls", "Puts")

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { filter ->
            val isSelected = (filter == "All Suggestions" && selectedFilter == null) ||
                    filter == selectedFilter

            FilterChip(
                selected = isSelected,
                onClick = {
                    onFilterSelect(if (filter == "All Suggestions") null else filter)
                },
                label = {
                    Text(
                        text = filter,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GreenLight,
                    selectedLabelColor = GreenPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = if (isSelected) GreenPrimary else MaterialTheme.colorScheme.outline,
                    enabled = true,
                    selected = isSelected
                )
            )
        }
    }
}

@Composable
private fun IOSStyleSuggestionCard(
    suggestion: PersonalizedAITradeSuggestion,
    onClick: () -> Unit
) {
    val base = suggestion.baseSuggestion
    val isBuy = base.action == TradeDirection.BUY || base.action == TradeDirection.STRONG_BUY
    val isSell = base.action == TradeDirection.SELL || base.action == TradeDirection.STRONG_SELL
    val isHold = base.action == TradeDirection.HOLD
    val isCall = base.optionType == OptionType.CALL

    // Use the action's display text (e.g., "STRONG BUY", "BUY", etc.)
    val displayAction = base.action.displayText

    // Determine sentiment based on option type and action
    // BUY/STRONG_BUY CALL = Bullish (Green), SELL/STRONG_SELL CALL = Bearish (Red)
    // BUY/STRONG_BUY PUT = Bearish (Red), SELL/STRONG_SELL PUT = Bullish (Green)
    val isBullishTrade = (isCall && isBuy) || (!isCall && isSell)

    // For HOLD, use amber color; otherwise use sentiment-based color
    val directionColor = if (isHold) AmberPrimary
                         else if (isBullishTrade) GreenPrimary else RedPrimary
    val directionBgColor = if (isHold) AmberLight
                           else if (isBullishTrade) GreenLight else RedLight

    // Card border color based on option type
    val cardBorderColor = if (isCall) GreenPrimary.copy(alpha = 0.3f) else RedPrimary.copy(alpha = 0.3f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (base.score >= 70) androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Row: Action Badge, Option Type, Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // BUY/SELL/HOLD Badge - color based on confidence and trade sentiment
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = directionBgColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isHold) "⏸️" else if (isBullishTrade) "📈" else "📉",
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = displayAction,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = directionColor
                            )
                            Text(
                                text = " ${base.score}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Normal
                                ),
                                color = directionColor.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // CE/PE Badge - CE is green, PE is red
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCall) GreenPrimary else RedPrimary
                    ) {
                        Text(
                            text = if (isCall) "CE" else "PE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Confidence Badge
                ConfidenceBadge(confidence = base.confidence)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Symbol and Strike Price Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = base.symbol,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹${String.format("%,.0f", base.strikePrice)}",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Score Circle - use option type color
                ScoreCircle(
                    score = base.score,
                    circleColor = if (isCall) GreenPrimary else AmberPrimary // Orange for puts to differentiate
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Entry, Target, Stop Loss Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PriceItem(
                    label = "Entry",
                    value = "₹${String.format("%.2f", base.entryPrice)}",
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                PriceItem(
                    label = "Target",
                    value = "₹${String.format("%.2f", base.targetPrice)}",
                    valueColor = GreenPrimary
                )
                PriceItem(
                    label = "Stop Loss",
                    value = "₹${String.format("%.2f", base.stopLoss)}",
                    valueColor = RedPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Analysis Factors Section
            Text(
                text = "Analysis Factors",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Show first 3 reasoning factors with progress bars
            base.reasoning.take(3).forEach { reason ->
                AnalysisFactorRow(
                    factor = reason.factor,
                    score = reason.score,
                    maxScore = reason.maxScore,
                    isPositive = reason.isPositive
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Row: R:R and Expiry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "R:R 1:${String.format("%.1f", base.riskReward)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = base.expiry,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: ConfidenceLevel) {
    val (backgroundColor, textColor, text) = when (confidence) {
        ConfidenceLevel.HIGH -> Triple(GreenLight, GreenPrimary, "HIGH")
        ConfidenceLevel.MEDIUM -> Triple(AmberLight, AmberPrimary, "MEDIUM")
        ConfidenceLevel.LOW -> Triple(RedLight, RedPrimary, "LOW")
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ScoreCircle(
    score: Int,
    circleColor: Color = GreenPrimary
) {
    // Animated progress for the score circle
    val animatedProgress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 800),
        label = "scoreProgress"
    )

    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background circle
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawCircle(
                color = circleColor.copy(alpha = 0.2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )
        }

        // Animated progress arc
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawArc(
                color = circleColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }

        // Score text
        Text(
            text = score.toString(),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = circleColor
        )
    }
}

@Composable
private fun PriceItem(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(
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
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
    }
}

@Composable
private fun AnalysisFactorRow(
    factor: String,
    score: Int,
    maxScore: Int,
    isPositive: Boolean
) {
    val progress = score.toFloat() / maxScore.toFloat()
    val progressColor = when {
        progress >= 0.8f -> GreenPrimary
        progress >= 0.6f -> AmberPrimary
        else -> RedPrimary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isPositive) GreenPrimary else RedPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = factor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(progressColor)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$score/$maxScore",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = progressColor
        )
    }
}
