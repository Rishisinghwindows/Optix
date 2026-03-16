package com.optix.app.presentation.screens.trade

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.R
import com.optix.app.core.util.Resource
import com.optix.app.domain.model.PaperPosition
import com.optix.app.domain.model.TradingIndex
import com.optix.app.presentation.theme.*
import java.text.NumberFormat
import java.util.*

@Composable
fun PaperTradingScreen(
    viewModel: PaperTradingViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        if (!state.isLoggedIn) {
            // Login Prompt
            LoginPromptView(onLoginClick = onNavigateToLogin)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Enhanced Header with Portfolio Summary
                PortfolioSummaryHeader(
                    portfolioValue = state.portfolioValue,
                    totalPnL = state.totalPnL,
                    totalPnLPercent = state.totalPnLPercent,
                    availableMargin = state.availableMargin,
                    openPositionsCount = state.openPositionsCount,
                    winRate = state.winRate
                )

                // Custom Tab Bar with Icons
                PaperTradingTabBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                // Content
                when (selectedTab) {
                    0 -> PositionsTab(
                        positionsState = state.positionsState,
                        onClosePosition = { viewModel.closePosition(it) },
                        onRefresh = { viewModel.refresh() }
                    )
                    1 -> HistoryTab(
                        historyState = state.historyState,
                        onRefresh = { viewModel.refresh() }
                    )
                    2 -> PerformanceTab(
                        totalTrades = state.totalTrades,
                        winningTrades = state.winningTrades,
                        losingTrades = state.losingTrades,
                        winRate = state.winRate,
                        avgProfit = state.avgProfit,
                        avgLoss = state.avgLoss,
                        totalProfit = state.totalProfit,
                        totalLoss = state.totalLoss,
                        profitFactor = state.profitFactor,
                        maxDrawdown = state.maxDrawdown
                    )
                }
            }
        }
    }
}

// iOS-matching gradient colors
private val LoginGradientStart = Color(0xFFF59E0B)
private val LoginGradientEnd = Color(0xFFD97706)
private val LoginButtonGradient = Brush.horizontalGradient(listOf(LoginGradientStart, LoginGradientEnd))

@Composable
fun LoginPromptView(onLoginClick: () -> Unit) {
    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "login")

    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Animated icon with glow effect (100dp main, 140dp with glow)
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow
            Canvas(
                modifier = Modifier
                    .size(140.dp)
                    .scale(iconScale)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AccentOrange.copy(alpha = glowAlpha),
                            AccentOrange.copy(alpha = glowAlpha * 0.5f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension / 2
                )
            }

            // Decorative ring (120dp)
            Canvas(modifier = Modifier.size(120.dp)) {
                drawCircle(
                    color = AccentOrange.copy(alpha = 0.2f),
                    radius = size.minDimension / 2 - 2.dp.toPx(),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8f, 12f),
                            0f
                        )
                    )
                )
            }

            // Main icon circle (100dp)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(AccentOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .scale(iconScale),
                    tint = AccentOrange
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title (24sp bold)
        Text(
            text = "Paper Trading",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Description (15sp, secondary color)
        Text(
            text = "Practice trading strategies risk-free\nwith virtual money",
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Features Card (14dp radius)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FeatureRow(
                    icon = Icons.Default.ShowChart,
                    text = "Practice trading with ₹10L virtual money",
                    iconColor = PrimaryGreen
                )
                FeatureRow(
                    icon = Icons.Default.ListAlt,
                    text = "Track your positions and history",
                    iconColor = AccentBlue
                )
                FeatureRow(
                    icon = Icons.Default.Analytics,
                    text = "Analyze your trading performance",
                    iconColor = AccentPurple
                )
                FeatureRow(
                    icon = Icons.Default.School,
                    text = "Learn without risking real money",
                    iconColor = AccentOrange
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Login Button (56dp height, 16dp radius, gradient)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onLoginClick),
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LoginButtonGradient),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White
                    )
                    Text(
                        text = "Login to Continue",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secondary note
        Text(
            text = "Login required for paper trading features",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun FeatureRow(
    icon: ImageVector,
    text: String,
    iconColor: Color = AccentOrange
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon with background circle (32dp)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = iconColor
            )
        }
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PortfolioSummaryHeader(
    portfolioValue: Double,
    totalPnL: Double,
    totalPnLPercent: Double,
    availableMargin: Double,
    openPositionsCount: Int,
    winRate: Double
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    val isProfit = totalPnL >= 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Portfolio Value Label
        Text(
            text = stringResource(R.string.paper_trading_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Portfolio Value (Large)
        Text(
            text = formatIndianCurrency(portfolioValue),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        // P&L Badge
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isProfit) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isProfit) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isProfit) SuccessGreen else ErrorRed
                )
                Text(
                    text = "${if (isProfit) "+" else ""}${formatIndianCurrency(totalPnL)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isProfit) SuccessGreen else ErrorRed
                )
                Text(
                    text = "(${if (isProfit) "+" else ""}%.2f%%)".format(totalPnLPercent),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isProfit) SuccessGreen else ErrorRed
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickStatItem(
                label = "Available",
                value = formatShortCurrency(availableMargin),
                color = PrimaryDark
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            QuickStatItem(
                label = "Positions",
                value = openPositionsCount.toString(),
                color = AccentOrange
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            QuickStatItem(
                label = "Win Rate",
                value = "%.1f%%".format(winRate),
                color = if (winRate >= 50) SuccessGreen else ErrorRed
            )
        }
    }
}

@Composable
fun QuickStatItem(
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
fun PaperTradingTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        Triple(stringResource(R.string.positions), Icons.Outlined.ListAlt, Icons.Filled.ListAlt),
        Triple(stringResource(R.string.history), Icons.Outlined.History, Icons.Filled.History),
        Triple(stringResource(R.string.performance), Icons.Outlined.Analytics, Icons.Filled.Analytics)
    )

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, (title, outlinedIcon, filledIcon) ->
                val isSelected = selectedTab == index

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTabSelected(index) },
                    color = if (isSelected) AccentOrange.copy(alpha = 0.15f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (isSelected) filledIcon else outlinedIcon,
                            contentDescription = title,
                            modifier = Modifier.size(20.dp),
                            tint = if (isSelected) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PositionsTab(
    positionsState: Resource<List<PaperPosition>>,
    onClosePosition: (String) -> Unit,
    onRefresh: () -> Unit
) {
    when (positionsState) {
        is Resource.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryDark)
            }
        }
        is Resource.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = positionsState.message ?: "Error loading positions",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark)
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        is Resource.Success -> {
            val positions = positionsState.data ?: emptyList()
            if (positions.isEmpty()) {
                EmptyPositionsView()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Positions Summary
                    item {
                        PositionsSummaryCard(positions)
                    }

                    items(positions) { position ->
                        EnhancedPositionCard(
                            position = position,
                            onClose = { onClosePosition(position.id) }
                        )
                    }

                    // Bottom spacing
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PositionsSummaryCard(positions: List<PaperPosition>) {
    val totalUnrealizedPnL = positions.sumOf { position ->
        val lotSize = TradingIndex.fromSymbol(position.symbol)?.lotSize ?: 75
        (position.currentPrice - position.entryPrice) * position.quantity * lotSize *
                if (position.isBuy) 1 else -1
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Unrealized P&L",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${if (totalUnrealizedPnL >= 0) "+" else ""}${formatIndianCurrency(totalUnrealizedPnL)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (totalUnrealizedPnL >= 0) SuccessGreen else ErrorRed
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Open Positions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${positions.size}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun EmptyPositionsView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Icon with background
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(AccentOrange.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ListAlt,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = AccentOrange
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.no_positions),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Start trading from Option Chain",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InstructionRow(number = 1, text = "Go to Option Chain tab")
                    InstructionRow(number = 2, text = "Long press on any option")
                    InstructionRow(number = 3, text = "Select \"Paper Buy\" or \"Paper Sell\"")
                }
            }
        }
    }
}

@Composable
fun InstructionRow(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(AccentOrange),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EnhancedPositionCard(
    position: PaperPosition,
    onClose: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val lotSize = TradingIndex.fromSymbol(position.symbol)?.lotSize ?: 75
    val pnl = (position.currentPrice - position.entryPrice) * position.quantity * lotSize *
            if (position.isBuy) 1 else -1
    val pnlPercent = if (position.entryPrice > 0) {
        (pnl / (position.entryPrice * position.quantity * lotSize)) * 100
    } else 0.0
    val isProfit = pnl >= 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isProfit) SuccessGreen.copy(alpha = 0.2f) else ErrorRed.copy(alpha = 0.2f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: Index Badge & Direction Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Index Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = PrimaryDark
                        )
                        Text(
                            text = position.symbol,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Direction Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (position.isBuy) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (position.isBuy) "BUY" else "SELL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (position.isBuy) SuccessGreen else ErrorRed,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    // Quantity Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${position.quantity}L",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Middle Row: Strike & LTP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "${position.strikePrice.toLong()} ${position.optionType}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Entry: ₹${position.entryPrice}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${position.currentPrice}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isProfit) SuccessGreen else ErrorRed
                    )
                    Text(
                        text = "LTP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Row: P&L & Expiry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // P&L
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isProfit) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isProfit) SuccessGreen else ErrorRed
                    )
                    Text(
                        text = "${if (isProfit) "+" else ""}${formatIndianCurrency(pnl)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isProfit) SuccessGreen else ErrorRed
                    )
                    Text(
                        text = "(${if (isProfit) "+" else ""}%.2f%%)".format(pnlPercent),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isProfit) SuccessGreen else ErrorRed
                    )
                }

                // Days to Expiry
                val daysToExpiry = calculateDaysToExpiry(position.expiry)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (daysToExpiry <= 2) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${daysToExpiry}D",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (daysToExpiry <= 2) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded Details
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Details Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val lotSize = TradingIndex.fromSymbol(position.symbol)?.lotSize ?: 75
                        DetailItem(label = "Total Qty", value = "${position.quantity * lotSize}")
                        DetailItem(label = "Investment", value = formatShortCurrency(position.entryPrice * position.quantity * lotSize))
                        DetailItem(label = "Current", value = formatShortCurrency(position.currentPrice * position.quantity * lotSize))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Square Off Button
                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.close_position),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun HistoryTab(
    historyState: Resource<List<PaperPosition>>,
    onRefresh: () -> Unit
) {
    when (historyState) {
        is Resource.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryDark)
            }
        }
        is Resource.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = historyState.message ?: "Error",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRefresh) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
        is Resource.Success -> {
            val history = historyState.data ?: emptyList()
            if (history.isEmpty()) {
                EmptyHistoryView()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(history) { trade ->
                        EnhancedHistoryCard(trade = trade)
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(PrimaryDark.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = PrimaryDark
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No Trade History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your closed trades will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EnhancedHistoryCard(trade: PaperPosition) {
    val lotSize = TradingIndex.fromSymbol(trade.symbol)?.lotSize ?: 75
    val pnl = ((trade.exitPrice ?: trade.currentPrice) - trade.entryPrice) * trade.quantity * lotSize *
            if (trade.isBuy) 1 else -1
    val isProfit = pnl >= 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Profit/Loss indicator
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isProfit) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isProfit) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = if (isProfit) SuccessGreen else ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "${trade.symbol} ${trade.strikePrice.toLong()} ${trade.optionType}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${trade.quantity} qty",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (trade.isBuy) "BUY" else "SELL",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (trade.isBuy) SuccessGreen else ErrorRed
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isProfit) "+" else ""}${formatIndianCurrency(pnl)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isProfit) SuccessGreen else ErrorRed
                )
                Text(
                    text = "₹${trade.entryPrice} → ₹${trade.exitPrice ?: trade.currentPrice}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PerformanceTab(
    totalTrades: Int,
    winningTrades: Int,
    losingTrades: Int,
    winRate: Double,
    avgProfit: Double,
    avgLoss: Double,
    totalProfit: Double = 0.0,
    totalLoss: Double = 0.0,
    profitFactor: Double = 0.0,
    maxDrawdown: Double = 0.0
) {
    if (totalTrades == 0) {
        EmptyPerformanceView()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Win Rate Card with Progress Bar
            item {
                WinRateCard(
                    winRate = winRate,
                    winningTrades = winningTrades,
                    losingTrades = losingTrades,
                    totalTrades = totalTrades,
                    avgProfit = avgProfit,
                    avgLoss = avgLoss
                )
            }

            // Performance Metrics Grid
            item {
                Text(
                    text = "Performance Metrics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Total Trades",
                        value = totalTrades.toString(),
                        icon = Icons.Default.SwapVert,
                        color = PrimaryDark,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Profit Factor",
                        value = if (profitFactor.isInfinite()) "∞" else "%.2f".format(profitFactor),
                        icon = Icons.Default.TrendingUp,
                        color = if (profitFactor >= 1) SuccessGreen else ErrorRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Total Profit",
                        value = formatShortCurrency(totalProfit),
                        icon = Icons.Default.ArrowUpward,
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Total Loss",
                        value = formatShortCurrency(totalLoss),
                        icon = Icons.Default.ArrowDownward,
                        color = ErrorRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "Avg Profit",
                        value = formatShortCurrency(avgProfit),
                        icon = Icons.Default.Add,
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricCard(
                        title = "Avg Loss",
                        value = formatShortCurrency(avgLoss),
                        icon = Icons.Default.Remove,
                        color = ErrorRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (maxDrawdown > 0) {
                item {
                    MetricCard(
                        title = "Max Drawdown",
                        value = formatShortCurrency(maxDrawdown),
                        icon = Icons.Default.TrendingDown,
                        color = ErrorRed,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun WinRateCard(
    winRate: Double,
    winningTrades: Int,
    losingTrades: Int,
    totalTrades: Int,
    avgProfit: Double,
    avgLoss: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Win Rate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "%.1f%%".format(winRate),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (winRate >= 50) SuccessGreen else ErrorRed
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Win/Loss Progress Bar
            val winRatio = if (totalTrades > 0) winningTrades.toFloat() / totalTrades else 0f
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                if (winRatio > 0) {
                    Box(
                        modifier = Modifier
                            .weight(winRatio.coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(SuccessGreen)
                    )
                }
                if (winRatio < 1) {
                    Box(
                        modifier = Modifier
                            .weight((1 - winRatio).coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(ErrorRed)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Text(
                            text = "$winningTrades Wins",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Avg: ${formatShortCurrency(avgProfit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$losingTrades Losses",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ErrorRed)
                        )
                    }
                    Text(
                        text = "Avg: ${formatShortCurrency(avgLoss)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyPerformanceView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(PrimaryDark.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = PrimaryDark
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No Performance Data",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Complete some trades to see your performance analytics",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// Helper functions
private fun formatIndianCurrency(value: Double): String {
    val absValue = kotlin.math.abs(value)
    val formatted = when {
        absValue >= 10000000 -> "%.2fCr".format(absValue / 10000000)
        absValue >= 100000 -> "%.2fL".format(absValue / 100000)
        absValue >= 1000 -> "%.2fK".format(absValue / 1000)
        else -> "%.2f".format(absValue)
    }
    return if (value < 0) "-₹$formatted" else "₹$formatted"
}

private fun formatShortCurrency(value: Double): String {
    val absValue = kotlin.math.abs(value)
    val formatted = when {
        absValue >= 10000000 -> "%.1fCr".format(absValue / 10000000)
        absValue >= 100000 -> "%.1fL".format(absValue / 100000)
        absValue >= 1000 -> "%.0fK".format(absValue / 1000)
        else -> "%.0f".format(absValue)
    }
    return if (value < 0) "-₹$formatted" else "₹$formatted"
}

private fun calculateDaysToExpiry(expiry: String): Int {
    return try {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH)
        val expiryDate = java.time.LocalDate.parse(expiry, formatter)
        val today = java.time.LocalDate.now()
        java.time.temporal.ChronoUnit.DAYS.between(today, expiryDate).toInt().coerceAtLeast(0)
    } catch (e: Exception) {
        0
    }
}
