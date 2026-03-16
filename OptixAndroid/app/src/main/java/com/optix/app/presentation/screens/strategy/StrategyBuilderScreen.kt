package com.optix.app.presentation.screens.strategy

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.domain.model.*
import com.optix.app.presentation.theme.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyBuilderScreen(
    viewModel: StrategyBuilderViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToDetail: (Strategy) -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var showStrategyPicker by remember { mutableStateOf(false) }
    var showAddLegDialog by remember { mutableStateOf(false) }
    var showStrikePicker by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Strategy Builder",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                actions = {
                    // Menu with options
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Reset to Template") },
                            onClick = {
                                viewModel.resetToTemplate()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear All Legs") },
                            onClick = {
                                viewModel.clearLegs()
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Share Strategy") },
                            onClick = {
                                // TODO: Implement share
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Share, null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Legs Builder Card
            item {
                LegsBuilderCard(
                    state = state,
                    onSelectStrategy = { showStrategyPicker = true },
                    onAddLeg = { showAddLegDialog = true },
                    onRemoveLeg = { viewModel.removeLeg(it) },
                    onUpdateStrike = { id, strike ->
                        viewModel.updateLegStrike(id, strike)
                    },
                    onUpdatePremium = { id, premium ->
                        viewModel.updateLegPremium(id, premium)
                    },
                    onUpdateQuantity = { id, qty ->
                        viewModel.updateLegQuantity(id, qty)
                    },
                    onTogglePosition = { viewModel.toggleLegPosition(it) },
                    onToggleOptionType = { viewModel.toggleLegOptionType(it) },
                    onShowStrikePicker = { showStrikePicker = it },
                    onResetTemplate = { viewModel.resetToTemplate() }
                )
            }

            // Payoff Chart
            item {
                PayoffChartCard(
                    payoffData = state.payoffData,
                    spotPrice = state.spotPrice,
                    isLoading = state.isAnalyzing,
                    touchedPrice = state.touchedPrice,
                    touchedPayoff = state.touchedPayoff,
                    onTouch = { price -> viewModel.handleChartTouch(price) },
                    onTouchEnd = { viewModel.clearChartTouch() }
                )
            }

            // Analysis Summary
            item {
                state.payoffData?.let { data ->
                    AnalysisSummaryCard(
                        payoffData = data,
                        strategy = Strategy(
                            id = "",
                            type = state.selectedType,
                            symbol = state.selectedIndex.symbol,
                            expiry = state.expiry,
                            legs = state.legs,
                            spotPrice = state.spotPrice
                        ),
                        marginRequired = state.marginRequired,
                        pop = state.probabilityOfProfit,
                        lotSize = state.selectedIndex.lotSize,
                        onViewDetails = {
                            val strategy = Strategy(
                                id = "",
                                type = state.selectedType,
                                symbol = state.selectedIndex.symbol,
                                expiry = state.expiry,
                                legs = state.legs,
                                spotPrice = state.spotPrice
                            )
                            onNavigateToDetail(strategy)
                        }
                    )
                }
            }

            // Spacer for bottom padding
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Strategy Picker Bottom Sheet
    if (showStrategyPicker) {
        StrategyPickerSheet(
            selectedType = state.selectedType,
            onSelectType = {
                viewModel.selectStrategyType(it)
                showStrategyPicker = false
            },
            onDismiss = { showStrategyPicker = false }
        )
    }

    // Add Leg Dialog
    if (showAddLegDialog) {
        AddLegDialog(
            spotPrice = state.spotPrice,
            onAddLeg = {
                viewModel.addLeg(it)
                showAddLegDialog = false
            },
            onDismiss = { showAddLegDialog = false }
        )
    }

    // Strike Picker Sheet
    showStrikePicker?.let { legId ->
        StrikePickerSheet(
            currentStrike = state.legs.find { it.id == legId }?.strikePrice ?: state.spotPrice,
            spotPrice = state.spotPrice,
            onSelectStrike = { strike ->
                viewModel.updateLegStrike(legId, strike)
                showStrikePicker = null
            },
            onDismiss = { showStrikePicker = null }
        )
    }
}

// MARK: - Legs Builder Card

@Composable
fun LegsBuilderCard(
    state: StrategyBuilderState,
    onSelectStrategy: () -> Unit,
    onAddLeg: () -> Unit,
    onRemoveLeg: (String) -> Unit,
    onUpdateStrike: (String, Double) -> Unit,
    onUpdatePremium: (String, Double) -> Unit,
    onUpdateQuantity: (String, Int) -> Unit,
    onTogglePosition: (String) -> Unit,
    onToggleOptionType: (String) -> Unit,
    onShowStrikePicker: (String) -> Unit,
    onResetTemplate: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with strategy type selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Strategy Type Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onSelectStrategy),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = state.selectedType.icon,
                            contentDescription = null,
                            tint = state.selectedType.outlookColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = state.selectedType.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = state.selectedType.outlook,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Reset Button
                    FilledTonalIconButton(
                        onClick = onResetTemplate,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Add Leg Button
                    FilledIconButton(
                        onClick = onAddLeg,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = AccentBlue
                        )
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Leg",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Strategy Description
            Text(
                text = state.selectedType.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Legs List
            if (state.legs.isEmpty()) {
                EmptyLegsView(onAddLeg = onAddLeg)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.legs.forEach { leg ->
                        LegRowCard(
                            leg = leg,
                            onRemove = { onRemoveLeg(leg.id) },
                            onUpdateStrike = { onUpdateStrike(leg.id, it) },
                            onUpdatePremium = { onUpdatePremium(leg.id, it) },
                            onUpdateQuantity = { onUpdateQuantity(leg.id, it) },
                            onTogglePosition = { onTogglePosition(leg.id) },
                            onToggleOptionType = { onToggleOptionType(leg.id) },
                            onShowStrikePicker = { onShowStrikePicker(leg.id) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BorderDark)
                Spacer(modifier = Modifier.height(12.dp))

                // Net Premium Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.legs.joinToString(" + ") { leg ->
                            "${if (leg.isLong) "B" else "S"}${leg.quantity} ${leg.strikePrice.toLong()} ${if (leg.optionType == OptionType.CALL) "CE" else "PE"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    val netPremium = state.legs.sumOf { leg ->
                        if (leg.isLong) -leg.premium * leg.quantity else leg.premium * leg.quantity
                    } * state.selectedIndex.lotSize
                    val isDebit = netPremium < 0

                    Text(
                        text = "${if (isDebit) "Debit: " else "Credit: "}${formatCurrency(abs(netPremium))}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDebit) ErrorRed else PrimaryGreen
                    )
                }
            }
        }
    }
}

// MARK: - Leg Row Card

@Composable
fun LegRowCard(
    leg: StrategyLeg,
    onRemove: () -> Unit,
    onUpdateStrike: (Double) -> Unit,
    onUpdatePremium: (Double) -> Unit,
    onUpdateQuantity: (Int) -> Unit,
    onTogglePosition: () -> Unit,
    onToggleOptionType: () -> Unit,
    onShowStrikePicker: () -> Unit
) {
    val accentColor = if (leg.isLong) PrimaryGreen else ErrorRed
    val bgColor = accentColor.copy(alpha = 0.08f)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Position, Quantity, Strike, Option Type, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Position Toggle (BUY/SELL)
                PositionToggle(
                    isLong = leg.isLong,
                    onToggle = onTogglePosition
                )

                // Quantity Control
                QuantityControl(
                    quantity = leg.quantity,
                    onQuantityChange = onUpdateQuantity
                )

                // Strike Picker
                StrikeButton(
                    strike = leg.strikePrice,
                    onClick = onShowStrikePicker
                )

                // Option Type Toggle (CE/PE)
                OptionTypeToggle(
                    isCall = leg.optionType == OptionType.CALL,
                    onToggle = onToggleOptionType
                )

                Spacer(modifier = Modifier.weight(1f))

                // Delete Button
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Row: Premium and Greeks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Premium Input
                PremiumInput(
                    premium = leg.premium,
                    onPremiumChange = onUpdatePremium
                )

                // Greeks Display
                GreeksDisplay(greeks = leg.greeks)
            }
        }
    }
}

// MARK: - Position Toggle

@Composable
fun PositionToggle(
    isLong: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
        color = CardDark
    ) {
        Row {
            listOf(true to "BUY", false to "SELL").forEach { (isLongOption, label) ->
                val selected = isLong == isLongOption
                val color = if (isLongOption) PrimaryGreen else ErrorRed

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggle),
                    color = if (selected) color else Color.Transparent
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else TextSecondaryDark
                    )
                }
            }
        }
    }
}

// MARK: - Quantity Control

@Composable
fun QuantityControl(
    quantity: Int,
    onQuantityChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { if (quantity > 1) onQuantityChange(quantity - 1) },
            color = CardDark
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Decrease",
                    modifier = Modifier.size(14.dp),
                    tint = TextSecondaryDark
                )
            }
        }

        Text(
            text = quantity.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 24.dp),
            textAlign = TextAlign.Center
        )

        Surface(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { onQuantityChange(quantity + 1) },
            color = CardDark
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Increase",
                    modifier = Modifier.size(14.dp),
                    tint = TextSecondaryDark
                )
            }
        }
    }
}

// MARK: - Strike Button

@Composable
fun StrikeButton(
    strike: Double,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = CardDark
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strike.toLong().toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Default.UnfoldMore,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = TextSecondaryDark
            )
        }
    }
}

// MARK: - Option Type Toggle

@Composable
fun OptionTypeToggle(
    isCall: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
        color = CardDark
    ) {
        Row {
            listOf(true to "CE", false to "PE").forEach { (isCallOption, label) ->
                val selected = isCall == isCallOption
                val color = if (isCallOption) CallColor else PutColor

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggle),
                    color = if (selected) color else Color.Transparent
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else if (isCallOption) CallColor else PutColor
                    )
                }
            }
        }
    }
}

// MARK: - Premium Input

@Composable
fun PremiumInput(
    premium: Double,
    onPremiumChange: (Double) -> Unit
) {
    var textValue by remember(premium) { mutableStateOf(String.format("%.2f", premium)) }

    Column {
        Text(
            text = "Premium",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryDark
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Rs",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.width(4.dp))
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    it.toDoubleOrNull()?.let(onPremiumChange)
                },
                modifier = Modifier.width(70.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderDark.copy(alpha = 0.5f)
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// MARK: - Greeks Display

@Composable
fun GreeksDisplay(greeks: Greeks) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GreekItem("Delta", greeks.delta, "%.2f")
        GreekItem("Gamma", greeks.gamma, "%.4f")
        GreekItem("Theta", greeks.theta, "%.2f")
        GreekItem("V", greeks.vega, "%.2f")
    }
}

@Composable
fun GreekItem(label: String, value: Double, format: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryDark,
            fontSize = 10.sp
        )
        Text(
            text = String.format(format, value),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryDark
        )
    }
}

// MARK: - Empty Legs View

@Composable
fun EmptyLegsView(onAddLeg: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.Layers,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = TextSecondaryDark
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No legs added",
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondaryDark
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Add legs manually or select a strategy template",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryDark,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        FilledTonalButton(
            onClick = onAddLeg,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = AccentBlue.copy(alpha = 0.1f)
            )
        ) {
            Icon(Icons.Default.Add, null, tint = AccentBlue)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add First Leg", color = AccentBlue)
        }
    }
}

// MARK: - Payoff Chart Card

@Composable
fun PayoffChartCard(
    payoffData: PayoffData?,
    spotPrice: Double,
    isLoading: Boolean,
    touchedPrice: Double?,
    touchedPayoff: Double?,
    onTouch: (Double) -> Unit,
    onTouchEnd: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryGreen)
            }
        } else if (payoffData == null || payoffData.points.isEmpty()) {
            PayoffPlaceholder()
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
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

                    // Touched value badge
                    if (touchedPrice != null && touchedPayoff != null) {
                        TouchedValueBadge(
                            price = touchedPrice,
                            payoff = touchedPayoff
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Chart
                PayoffChart(
                    payoffData = payoffData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Legend
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
}

@Composable
fun PayoffPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.ShowChart,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Add legs to see payoff diagram",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
fun TouchedValueBadge(price: Double, payoff: Double) {
    val isProfit = payoff >= 0
    val color = if (isProfit) PrimaryGreen else ErrorRed

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = SurfaceElevatedDark,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = String.format("%.0f", price),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatPayoff(payoff),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryDark
        )
    }
}

@Composable
fun PayoffChart(
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
        if (maxX == minX || yRange == 0.0) return@Canvas
        val xScale = width / (maxX - minX)
        val yScale = height / (yRange * 2)
        val centerY = height / 2

        // Draw zero line
        drawLine(
            Color.Gray.copy(alpha = 0.5f),
            Offset(0f, centerY),
            Offset(width, centerY),
            2.dp.toPx()
        )

        // Draw current spot line
        val spotX = ((payoffData.currentSpot - minX) * xScale).toFloat()
        drawLine(
            AccentBlue.copy(alpha = 0.7f),
            Offset(spotX, 0f),
            Offset(spotX, height),
            1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
        )

        // Draw fill path
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

        // Gradient fill
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

        // Draw main payoff line
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

        // Draw breakeven points
        payoffData.breakevens.forEach { be ->
            val beX = ((be - minX) * xScale).toFloat()
            drawCircle(
                color = WarningOrange,
                radius = 6.dp.toPx(),
                center = Offset(beX, centerY)
            )
        }
    }
}

// MARK: - Analysis Summary Card

@Composable
fun AnalysisSummaryCard(
    payoffData: PayoffData,
    strategy: Strategy,
    marginRequired: Double,
    pop: Double,
    lotSize: Int = 1,
    onViewDetails: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, BorderDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Strategy Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onViewDetails) {
                    Text("Details", color = AccentBlue)
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Metrics Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Max Profit",
                    value = if (payoffData.maxProfit >= 1_000_000) "Unlimited"
                    else formatCurrency(payoffData.maxProfit),
                    color = PrimaryGreen,
                    icon = Icons.Filled.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Max Loss",
                    value = if (payoffData.maxLoss <= -1_000_000) "Unlimited"
                    else formatCurrency(abs(payoffData.maxLoss)),
                    color = ErrorRed,
                    icon = Icons.Filled.TrendingDown,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Metrics Row 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Risk/Reward",
                    value = if (payoffData.riskRewardRatio.isInfinite()) "N/A"
                    else String.format("1:%.2f", payoffData.riskRewardRatio),
                    color = AccentBlue,
                    icon = Icons.Filled.Balance,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "P.O.P.",
                    value = "${(pop * 100).toInt()}%",
                    color = AccentPurple,
                    icon = Icons.Filled.Percent,
                    modifier = Modifier.weight(1f)
                )
            }

            // Breakevens
            if (payoffData.breakevens.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Breakeven Points",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryDark
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    payoffData.breakevens.forEach { be ->
                        BreakevenBadge(value = be)
                    }
                }
            }

            // Net Premium and Margin
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Net Premium",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark
                    )
                    val netPremium = strategy.netPremium * lotSize
                    Text(
                        text = "${if (strategy.isDebit) "Debit: " else "Credit: "}${formatCurrency(kotlin.math.abs(netPremium))}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (strategy.isDebit) ErrorRed else PrimaryGreen
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Margin Required",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark
                    )
                    Text(
                        text = if (marginRequired > 0) formatCurrency(marginRequired) else "N/A",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Net Greeks
            if (strategy.legs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BorderDark)
                Spacer(modifier = Modifier.height(12.dp))
                NetGreeksSummary(strategy = strategy)
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryDark
                )
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

@Composable
fun BreakevenBadge(value: Double) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = WarningOrange.copy(alpha = 0.15f)
    ) {
        Text(
            text = String.format("%.0f", value),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = WarningOrange
        )
    }
}

@Composable
fun NetGreeksSummary(strategy: Strategy) {
    Column {
        Text(
            text = "Net Greeks",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = TextSecondaryDark
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GreekCell("Delta", strategy.netDelta, strategy.netDelta > 0.1)
            GreekCell("Gamma", strategy.netGamma, decimals = 4)
            GreekCell("Theta", strategy.netTheta, strategy.netTheta > 0)
            GreekCell("Vega", strategy.netVega)
        }
    }
}

@Composable
fun GreekCell(
    label: String,
    value: Double,
    isPositive: Boolean = false,
    decimals: Int = 2
) {
    val color = when {
        label == "Delta" && abs(value) > 0.1 -> if (value > 0) PrimaryGreen else ErrorRed
        label == "Theta" -> if (value > 0) PrimaryGreen else ErrorRed
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryDark
        )
        Text(
            text = String.format("%.${decimals}f", value),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

// MARK: - Strategy Picker Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyPickerSheet(
    selectedType: StrategyType,
    onSelectType: (StrategyType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "Select Strategy",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            StrategyCategory.entries.filter { it != StrategyCategory.CUSTOM }.forEach { category ->
                // Category Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Strategy Cards Grid
                val strategies = StrategyType.entries.filter { it.category == category }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height((((strategies.size + 1) / 2) * 140).dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false
                ) {
                    items(strategies) { type ->
                        StrategyCard(
                            type = type,
                            isSelected = type == selectedType,
                            onSelect = { onSelectType(type) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun StrategyCard(
    type: StrategyType,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect),
        color = if (isSelected) AccentBlue.copy(alpha = 0.1f) else CardDark,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) AccentBlue else BorderDark.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = type.icon,
                    contentDescription = null,
                    tint = type.outlookColor,
                    modifier = Modifier.size(24.dp)
                )
                // Risk indicator
                RiskIndicator(isLimited = type.isLimitedRisk)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Name
            Text(
                text = type.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Description
            Text(
                text = type.description,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.weight(1f))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${type.legCount} leg${if (type.legCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryDark
                )
                Text(
                    text = type.outlook,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = type.outlookColor
                )
            }
        }
    }
}

@Composable
fun RiskIndicator(isLimited: Boolean) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (isLimited) PrimaryGreen else ErrorRed)
    )
}

// MARK: - Add Leg Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLegDialog(
    spotPrice: Double,
    onAddLeg: (StrategyLeg) -> Unit,
    onDismiss: () -> Unit
) {
    var strike by remember { mutableStateOf(spotPrice.toLong().toString()) }
    var premium by remember { mutableStateOf("100") }
    var quantity by remember { mutableStateOf("1") }
    var optionType by remember { mutableStateOf(OptionType.CALL) }
    var position by remember { mutableStateOf(LegPosition.BUY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Leg", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Option Type Selection
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = optionType == OptionType.CALL,
                        onClick = { optionType = OptionType.CALL },
                        label = { Text("Call (CE)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CallColor.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = optionType == OptionType.PUT,
                        onClick = { optionType = OptionType.PUT },
                        label = { Text("Put (PE)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PutColor.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Position Selection
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = position == LegPosition.BUY,
                        onClick = { position = LegPosition.BUY },
                        label = { Text("Buy (Long)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryGreen.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = position == LegPosition.SELL,
                        onClick = { position = LegPosition.SELL },
                        label = { Text("Sell (Short)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ErrorRed.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Strike Price
                OutlinedTextField(
                    value = strike,
                    onValueChange = { strike = it },
                    label = { Text("Strike Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("Rs") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Premium
                OutlinedTextField(
                    value = premium,
                    onValueChange = { premium = it },
                    label = { Text("Premium") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("Rs") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quantity
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity (Lots)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val leg = StrategyLeg(
                        id = java.util.UUID.randomUUID().toString(),
                        strikePrice = strike.toDoubleOrNull() ?: spotPrice,
                        optionType = optionType,
                        position = position,
                        quantity = quantity.toIntOrNull() ?: 1,
                        premium = premium.toDoubleOrNull() ?: 0.0,
                        greeks = Greeks.EMPTY
                    )
                    onAddLeg(leg)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Text("Add Leg")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// MARK: - Strike Picker Sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrikePickerSheet(
    currentStrike: Double,
    spotPrice: Double,
    onSelectStrike: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val strikes = remember(spotPrice) {
        val interval = if (spotPrice > 40000) 100.0 else 50.0
        val baseStrike = (spotPrice / interval).toLong() * interval.toLong()
        (-20..20).map { (baseStrike + it * interval.toLong()).toDouble() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Select Strike Price",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(strikes) { strike ->
                    val isSelected = strike == currentStrike
                    val isATM = abs(strike - spotPrice) < 25

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectStrike(strike) },
                        color = when {
                            isSelected -> AccentBlue.copy(alpha = 0.15f)
                            isATM -> PrimaryGreen.copy(alpha = 0.1f)
                            else -> Color.Transparent
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = strike.toLong().toString(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected || isATM) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isATM) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = PrimaryGreen.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "ATM",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PrimaryGreen
                                        )
                                    }
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = AccentBlue
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("Done")
            }

            Spacer(modifier = Modifier.height(32.dp))
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

private fun formatPayoff(value: Double): String {
    val prefix = if (value >= 0) "+" else ""
    return when {
        abs(value) >= 100000 -> prefix + String.format("Rs%.1fL", value / 100000)
        abs(value) >= 1000 -> prefix + String.format("Rs%.1fK", value / 1000)
        else -> prefix + String.format("Rs%.0f", value)
    }
}

// MARK: - Extension Properties

val StrategyType.icon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (this) {
        StrategyType.LONG_CALL -> Icons.Default.TrendingUp
        StrategyType.SHORT_CALL -> Icons.Default.TrendingDown
        StrategyType.LONG_PUT -> Icons.Default.TrendingDown
        StrategyType.SHORT_PUT -> Icons.Default.TrendingUp
        StrategyType.BULL_CALL_SPREAD, StrategyType.BULL_PUT_SPREAD -> Icons.Default.ShowChart
        StrategyType.BEAR_CALL_SPREAD, StrategyType.BEAR_PUT_SPREAD -> Icons.Default.ShowChart
        StrategyType.LONG_STRADDLE, StrategyType.SHORT_STRADDLE -> Icons.Default.SwapHoriz
        StrategyType.LONG_STRANGLE, StrategyType.SHORT_STRANGLE -> Icons.Default.SwapHoriz
        StrategyType.IRON_CONDOR -> Icons.Default.Layers
        StrategyType.IRON_BUTTERFLY -> Icons.Default.Layers
        StrategyType.LONG_BUTTERFLY, StrategyType.SHORT_BUTTERFLY -> Icons.Default.Layers
        else -> Icons.Default.Layers
    }

val StrategyType.outlook: String
    get() = when (this) {
        StrategyType.LONG_CALL, StrategyType.BULL_CALL_SPREAD, StrategyType.BULL_PUT_SPREAD -> "Bullish"
        StrategyType.LONG_PUT, StrategyType.BEAR_CALL_SPREAD, StrategyType.BEAR_PUT_SPREAD -> "Bearish"
        StrategyType.SHORT_CALL -> "Bearish/Neutral"
        StrategyType.SHORT_PUT -> "Bullish/Neutral"
        StrategyType.LONG_STRADDLE, StrategyType.LONG_STRANGLE -> "Volatility"
        StrategyType.SHORT_STRADDLE, StrategyType.SHORT_STRANGLE -> "Neutral"
        StrategyType.IRON_CONDOR, StrategyType.IRON_BUTTERFLY -> "Neutral"
        StrategyType.LONG_BUTTERFLY, StrategyType.SHORT_BUTTERFLY -> "Neutral"
        else -> "Directional"
    }

val StrategyType.outlookColor: Color
    get() = when (outlook) {
        "Bullish", "Bullish/Neutral" -> PrimaryGreen
        "Bearish", "Bearish/Neutral" -> ErrorRed
        "Neutral" -> AccentBlue
        "Volatility" -> AccentPurple
        else -> WarningOrange
    }

val StrategyType.isLimitedRisk: Boolean
    get() = when (this) {
        StrategyType.LONG_CALL, StrategyType.LONG_PUT,
        StrategyType.BULL_CALL_SPREAD, StrategyType.BEAR_CALL_SPREAD,
        StrategyType.BULL_PUT_SPREAD, StrategyType.BEAR_PUT_SPREAD,
        StrategyType.LONG_STRADDLE, StrategyType.LONG_STRANGLE,
        StrategyType.IRON_CONDOR, StrategyType.IRON_BUTTERFLY,
        StrategyType.LONG_BUTTERFLY, StrategyType.SHORT_BUTTERFLY -> true
        else -> false
    }

val StrategyCategory.icon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (this) {
        StrategyCategory.SINGLE_LEG -> Icons.Default.LooksOne
        StrategyCategory.COVERED -> Icons.Default.Shield
        StrategyCategory.VERTICAL_SPREAD -> Icons.Default.SwapVert
        StrategyCategory.STRADDLE_STRANGLE -> Icons.Default.SwapHoriz
        StrategyCategory.MULTI_LEG -> Icons.Default.Layers
        StrategyCategory.CALENDAR -> Icons.Default.CalendarMonth
        StrategyCategory.RATIO -> Icons.Default.StackedBarChart
        StrategyCategory.CUSTOM -> Icons.Default.Settings
    }
