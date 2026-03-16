package com.optix.app.presentation.screens.calculator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.domain.model.GreeksResult

// Colors matching iOS design
private val GreenPrimary = Color(0xFF22C55E)
private val GreenLight = Color(0xFFDCFCE7)
private val RedPrimary = Color(0xFFEF4444)
private val RedLight = Color(0xFFFEE2E2)
private val AmberWarning = Color(0xFFFEF3C7)
private val AmberText = Color(0xFFD97706)
private val PurpleBadge = Color(0xFF8B5CF6)
private val PurpleLight = Color(0xFFF3E8FF)
private val BluePrimary = Color(0xFF3B82F6)
private val BlueLight = Color(0xFFDBEAFE)
private val OrangePrimary = Color(0xFFF97316)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    onNavigateBack: () -> Unit,
    initialSpotPrice: Double = 0.0,
    initialStrikePrice: Double = 0.0,
    initialLTP: Double = 0.0,
    initialIsCall: Boolean = true,
    initialDaysToExpiry: Int = 7,
    viewModel: CalculatorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Initialize with passed values
    LaunchedEffect(initialSpotPrice, initialStrikePrice) {
        if (initialSpotPrice > 0 && initialStrikePrice > 0) {
            viewModel.initializeFromOptionChain(
                spotPrice = initialSpotPrice,
                strikePrice = initialStrikePrice,
                ltp = initialLTP,
                isCall = initialIsCall,
                daysToExpiry = initialDaysToExpiry
            )
        }
    }

    Scaffold(
        topBar = {
            CalculatorTopBar(
                onNavigateBack = onNavigateBack,
                onReset = { viewModel.reset() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Disclaimer Banner
            AnimatedVisibility(
                visible = state.showDisclaimer,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                DisclaimerBanner(
                    onDismiss = { viewModel.dismissDisclaimer() }
                )
            }

            // Call/Put Toggle
            CallPutToggle(
                isCall = state.isCall,
                onCallSelected = { viewModel.setOptionType(true) },
                onPutSelected = { viewModel.setOptionType(false) }
            )

            // Enhanced Results Section (if calculated)
            state.result?.let { result ->
                EnhancedResultsSection(
                    result = result,
                    strikePrice = state.strikePrice,
                    daysToExpiry = state.daysToExpiry,
                    volatility = state.volatility,
                    isCall = state.isCall
                )
            }

            // Price Calculator Section with Target & Stop-Loss
            PriceCalculatorSection(
                state = state,
                onTargetPriceChange = { viewModel.updateTargetPrice(it) },
                onStopLossPriceChange = { viewModel.updateStopLossPrice(it) },
                onTargetPreset = { viewModel.setTargetPreset(it) },
                onStopLossPreset = { viewModel.setStopLossPreset(it) }
            )

            // Advanced Analysis Section (Collapsible Greeks)
            state.result?.let { result ->
                AdvancedAnalysisSection(
                    greeks = result.greeks,
                    showAdvanced = state.showAdvancedAnalysis,
                    onToggle = { viewModel.toggleAdvancedAnalysis() },
                    daysToExpiry = state.daysToExpiry.toIntOrNull() ?: 7
                )
            }

            // Parameters Section (moved to bottom)
            ParametersSection(
                state = state,
                onSpotPriceChange = { viewModel.updateSpotPrice(it) },
                onStrikePriceChange = { viewModel.updateStrikePrice(it) },
                onDaysToExpiryChange = { viewModel.updateDaysToExpiry(it) },
                onVolatilityChange = { viewModel.updateVolatility(it) },
                onCurrentLTPChange = { viewModel.updateCurrentLTP(it) },
                onCalculateIV = { viewModel.calculateIV() }
            )

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorTopBar(
    onNavigateBack: () -> Unit,
    onReset: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            Surface(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = onNavigateBack
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = "f(x)",
                    color = BluePrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Option Calculator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Black-Scholes Pricing Model",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = BlueLight,
                onClick = onReset
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = BluePrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun DisclaimerBanner(
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AmberWarning
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AmberText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Theoretical prices. Actual market prices may differ due to IV changes, liquidity, and market gaps. Not investment advice.",
                style = MaterialTheme.typography.bodySmall,
                color = AmberText,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                tint = AmberText,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onDismiss() }
            )
        }
    }
}

@Composable
private fun CallPutToggle(
    isCall: Boolean,
    onCallSelected: () -> Unit,
    onPutSelected: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // CALL Button
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = if (isCall) GreenPrimary else Color.Transparent,
                onClick = onCallSelected
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = if (isCall) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "CALL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCall) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // PUT Button
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = if (!isCall) RedPrimary else Color.Transparent,
                onClick = onPutSelected
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = if (!isCall) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "PUT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (!isCall) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ParametersSection(
    state: CalculatorState,
    onSpotPriceChange: (String) -> Unit,
    onStrikePriceChange: (String) -> Unit,
    onDaysToExpiryChange: (String) -> Unit,
    onVolatilityChange: (String) -> Unit,
    onCurrentLTPChange: (String) -> Unit,
    onCalculateIV: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚙️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Parameters",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                // BS Model Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PurpleLight
                ) {
                    Text(
                        text = "BS Model",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PurpleBadge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Spot Price and Strike Price Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ParameterTextField(
                    label = "Spot Price",
                    value = state.spotPrice,
                    onValueChange = onSpotPriceChange,
                    leadingIcon = "₹",
                    modifier = Modifier.weight(1f)
                )
                ParameterTextField(
                    label = "Strike Price",
                    value = state.strikePrice,
                    onValueChange = onStrikePriceChange,
                    leadingIcon = "◎",
                    modifier = Modifier.weight(1f)
                )
            }

            // Days to Expiry and IV Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ParameterTextField(
                    label = "Days to Expiry",
                    value = state.daysToExpiry,
                    onValueChange = onDaysToExpiryChange,
                    leadingIcon = "📅",
                    modifier = Modifier.weight(1f)
                )
                ParameterTextField(
                    label = "IV (%)",
                    value = state.volatility,
                    onValueChange = onVolatilityChange,
                    leadingIcon = "📊",
                    modifier = Modifier.weight(1f)
                )
            }

            // Current LTP and Calculate IV Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                ParameterTextField(
                    label = "Current LTP (optional)",
                    value = state.currentLTP,
                    onValueChange = onCurrentLTPChange,
                    leadingIcon = "🏷️",
                    modifier = Modifier.weight(1f)
                )
                // Calculate IV Button
                Surface(
                    modifier = Modifier
                        .height(56.dp)
                        .width(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = PurpleLight,
                    onClick = onCalculateIV
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "f(x)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PurpleBadge
                        )
                        Text(
                            text = "IV",
                            style = MaterialTheme.typography.labelSmall,
                            color = PurpleBadge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParameterTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    leadingIcon: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Text(text = leadingIcon, fontSize = 16.sp)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BluePrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

@Composable
private fun EnhancedResultsSection(
    result: CalculationResult,
    strikePrice: String,
    daysToExpiry: String,
    volatility: String,
    isCall: Boolean
) {
    val priceColor = if (isCall) GreenPrimary else RedPrimary
    val moneynessColor = when (result.moneyness) {
        "ITM" -> GreenPrimary
        "OTM" -> RedPrimary
        else -> BluePrimary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main Price Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Theoretical Price",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Moneyness Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = moneynessColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = result.moneyness,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = moneynessColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "₹",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = priceColor.copy(alpha = 0.7f)
                        )
                        Text(
                            text = String.format("%.2f", result.optionPrice),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = priceColor
                        )
                    }
                }

                // Option Type Badge
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = priceColor
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isCall) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isCall) "CALL" else "PUT",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Strike: $strikePrice",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Value Breakdown
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ValueBreakdownItem(
                        label = "Intrinsic",
                        value = "₹${String.format("%.2f", result.intrinsicValue)}",
                        color = GreenPrimary
                    )
                    VerticalDivider()
                    ValueBreakdownItem(
                        label = "Time Value",
                        value = "₹${String.format("%.2f", result.timeValue)}",
                        color = OrangePrimary
                    )
                    VerticalDivider()
                    ValueBreakdownItem(
                        label = "Days",
                        value = daysToExpiry,
                        color = BluePrimary
                    )
                    VerticalDivider()
                    ValueBreakdownItem(
                        label = "IV",
                        value = "$volatility%",
                        color = PurpleBadge
                    )
                }
            }

            // Note
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ℹ️",
                    fontSize = 9.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Theoretical price based on Black-Scholes model. Actual prices may vary.",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ValueBreakdownItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
    )
}

@Composable
private fun PriceCalculatorSection(
    state: CalculatorState,
    onTargetPriceChange: (String) -> Unit,
    onStopLossPriceChange: (String) -> Unit,
    onTargetPreset: (Int) -> Unit,
    onStopLossPreset: (Int) -> Unit
) {
    val isCall = state.isCall
    val targetPresets = if (isCall) listOf(100, 200, 300, 500) else listOf(-100, -200, -300, -500)
    val stopLossPresets = if (isCall) listOf(-100, -150, -200, -300) else listOf(100, 150, 200, 300)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🎯", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Price Calculator",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GreenLight
                ) {
                    Text(
                        text = "Simple",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = GreenPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                // TODO: Replace with selected index name when CalculatorState includes TradingIndex
                text = "Enter where you think the index will go, and we'll calculate your option price",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Target Input
            TargetStopLossInput(
                title = if (isCall) "If index goes to" else "If index falls to",
                subtitle = "Your target",
                value = state.targetPrice,
                onValueChange = onTargetPriceChange,
                color = GreenPrimary,
                isUp = isCall,
                presets = targetPresets,
                onPresetClick = onTargetPreset
            )

            // Stop-Loss Input
            TargetStopLossInput(
                title = if (isCall) "If index falls to" else "If index goes to",
                subtitle = "Your stop-loss",
                value = state.stopLossPrice,
                onValueChange = onStopLossPriceChange,
                color = RedPrimary,
                isUp = !isCall,
                presets = stopLossPresets,
                onPresetClick = onStopLossPreset
            )

            // Results
            state.targetCalculation?.let { calc ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "YOUR OPTION WILL BE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                // Target Result
                PriceResultCard(
                    scenario = "At Target (${calc.targetSpot.toInt()})",
                    icon = "🏁",
                    optionPrice = calc.targetOptionPrice,
                    priceChange = calc.targetProfit,
                    percentChange = calc.targetProfitPercent,
                    color = GreenPrimary
                )

                // Stop-Loss Result
                PriceResultCard(
                    scenario = "At Stop-Loss (${calc.stopLossSpot.toInt()})",
                    icon = "⚠️",
                    optionPrice = calc.stopLossOptionPrice,
                    priceChange = -calc.stopLossLoss,
                    percentChange = -calc.stopLossLossPercent,
                    color = RedPrimary
                )

                // Risk/Reward Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Max Profit
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = GreenLight
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Max Profit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = calc.displayProfit,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = GreenPrimary
                            )
                        }
                    }

                    // Max Loss
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = RedLight
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Max Loss",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = calc.displayLoss,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RedPrimary
                            )
                        }
                    }

                    // Risk:Reward
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = BlueLight
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Risk:Reward",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = calc.displayRiskReward,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (calc.isGoodRiskReward) GreenPrimary else OrangePrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (calc.isGoodRiskReward) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                                    contentDescription = null,
                                    tint = if (calc.isGoodRiskReward) GreenPrimary else OrangePrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetStopLossInput(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    color: Color,
    isUp: Boolean,
    presets: List<Int>,
    onPresetClick: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Surface(
                    shape = CircleShape,
                    color = color.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isUp) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Labels
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Input
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.width(100.dp),
                    singleLine = true,
                    leadingIcon = {
                        Text(
                            text = "₹",
                            style = MaterialTheme.typography.bodyMedium,
                            color = color.copy(alpha = 0.7f)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = color,
                        unfocusedBorderColor = color.copy(alpha = 0.3f),
                        focusedContainerColor = color.copy(alpha = 0.1f),
                        unfocusedContainerColor = color.copy(alpha = 0.1f)
                    )
                )
            }
        }

        // Preset Buttons
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            presets.forEach { preset ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = color.copy(alpha = 0.12f),
                    onClick = { onPresetClick(preset) },
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = color.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(6.dp)
                    )
                ) {
                    Text(
                        text = if (preset >= 0) "+$preset" else "$preset",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceResultCard(
    scenario: String,
    icon: String,
    optionPrice: Double,
    priceChange: Double,
    percentChange: Double,
    color: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = icon, fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Scenario Label
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scenario,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Option premium",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Price and Change
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${String.format("%.2f", optionPrice)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (priceChange >= 0) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = color.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${if (priceChange >= 0) "+" else ""}${String.format("%.0f", priceChange)} (${String.format("%.0f", percentChange)}%)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = color.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedAnalysisSection(
    greeks: GreeksResult,
    showAdvanced: Boolean,
    onToggle: () -> Unit,
    daysToExpiry: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            // Header (Always visible)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📊", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Advanced Analysis",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Greeks & Risk",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PurpleLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showAdvanced) "Hide" else "Show",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = PurpleBadge
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = PurpleBadge,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Collapsible Content
            AnimatedVisibility(visible = showAdvanced) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Greeks Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        GreekItem("Delta", String.format("%.4f", greeks.delta), BluePrimary)
                        GreekItem("Gamma", String.format("%.6f", greeks.gamma), PurpleBadge)
                        GreekItem("Theta", String.format("%.4f", greeks.theta), OrangePrimary)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        GreekItem("Vega", String.format("%.4f", greeks.vega), GreenPrimary)
                        GreekItem("Rho", String.format("%.4f", greeks.rho), RedPrimary)
                        GreekItem("IV", String.format("%.2f%%", greeks.impliedVolatility * 100), PurpleBadge)
                    }

                    // Risk Assessment
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Risk Assessment",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Theta Risk
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Theta Risk (Time Decay)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val thetaRisk = when {
                                    daysToExpiry <= 3 -> "High"
                                    daysToExpiry <= 7 -> "Medium"
                                    else -> "Low"
                                }
                                val thetaColor = when (thetaRisk) {
                                    "High" -> RedPrimary
                                    "Medium" -> OrangePrimary
                                    else -> GreenPrimary
                                }
                                Text(
                                    text = thetaRisk,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = thetaColor
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Vega Risk
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Vega Risk (IV Sensitivity)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val vegaRisk = when {
                                    greeks.vega > 10 -> "High"
                                    greeks.vega > 5 -> "Medium"
                                    else -> "Low"
                                }
                                val vegaColor = when (vegaRisk) {
                                    "High" -> RedPrimary
                                    "Medium" -> OrangePrimary
                                    else -> GreenPrimary
                                }
                                Text(
                                    text = vegaRisk,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = vegaColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GreekItem(
    name: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
