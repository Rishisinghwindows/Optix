package com.niftyoption.calculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.niftyoption.calculator.data.models.GreeksResult
import com.niftyoption.calculator.data.models.OptionType
import com.niftyoption.calculator.data.models.TargetCalculation
import com.niftyoption.calculator.ui.theme.AppColors
import com.niftyoption.calculator.ui.viewmodels.CalculatorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculator") },
                actions = {
                    TextButton(onClick = { viewModel.reset() }) {
                        Text("Reset", color = AppColors.AccentBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.SurfaceDark
                )
            )
        },
        containerColor = AppColors.BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Input Section
            InputSection(
                spotPrice = uiState.spotPrice,
                strikePrice = uiState.strikePrice,
                daysToExpiry = uiState.daysToExpiry,
                impliedVolatility = uiState.impliedVolatility,
                currentOptionPrice = uiState.currentOptionPrice,
                onSpotChange = { viewModel.updateSpotPrice(it) },
                onStrikeChange = { viewModel.updateStrikePrice(it) },
                onDaysChange = { viewModel.updateDaysToExpiry(it) },
                onIVChange = { viewModel.updateImpliedVolatility(it) },
                onCurrentPriceChange = { viewModel.updateCurrentOptionPrice(it) },
                onCalculateIV = { viewModel.calculateImpliedVolatility() }
            )

            // Option Type Selector
            OptionTypeSelector(
                selectedType = uiState.optionType,
                onTypeSelected = { viewModel.updateOptionType(it) }
            )

            // Calculate Button
            Button(
                onClick = { viewModel.calculateAll() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.AccentBlue
                ),
                enabled = uiState.isInputValid
            ) {
                Text("Calculate", fontWeight = FontWeight.SemiBold)
            }

            // Results Section
            uiState.calculatedPrice?.let { price ->
                ResultsSection(
                    price = price,
                    optionType = uiState.optionType,
                    strikePrice = uiState.strikePrice
                )
            }

            // Greeks Section
            uiState.greeks?.let { greeks ->
                GreeksDisplay(greeks = greeks)
            }

            // Target/SL Section
            TargetSLSection(
                targetSpot = uiState.targetSpot,
                stopLossSpot = uiState.stopLossSpot,
                onTargetChange = { viewModel.updateTargetSpot(it) },
                onStopLossChange = { viewModel.updateStopLossSpot(it) },
                onSwap = { viewModel.swapTargetSL() },
                calculation = uiState.targetCalculation
            )

            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
private fun InputSection(
    spotPrice: String,
    strikePrice: String,
    daysToExpiry: String,
    impliedVolatility: String,
    currentOptionPrice: String,
    onSpotChange: (String) -> Unit,
    onStrikeChange: (String) -> Unit,
    onDaysChange: (String) -> Unit,
    onIVChange: (String) -> Unit,
    onCurrentPriceChange: (String) -> Unit,
    onCalculateIV: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Option Parameters",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InputField(
                    label = "Spot Price",
                    value = spotPrice,
                    onValueChange = onSpotChange,
                    modifier = Modifier.weight(1f)
                )
                InputField(
                    label = "Strike Price",
                    value = strikePrice,
                    onValueChange = onStrikeChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InputField(
                    label = "Days to Expiry",
                    value = daysToExpiry,
                    onValueChange = onDaysChange,
                    modifier = Modifier.weight(1f)
                )
                InputField(
                    label = "IV (%)",
                    value = impliedVolatility,
                    onValueChange = onIVChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Current Option Price (optional)",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextMuted
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = currentOptionPrice,
                    onValueChange = onCurrentPriceChange,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.AccentBlue,
                        unfocusedBorderColor = AppColors.CardDark,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    ),
                    singleLine = true
                )
                TextButton(onClick = onCalculateIV) {
                    Text("Calc IV", color = AppColors.AccentBlue)
                }
            }
        }
    }
}

@Composable
private fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.AccentBlue,
                unfocusedBorderColor = AppColors.CardDark,
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary
            ),
            singleLine = true
        )
    }
}

@Composable
private fun OptionTypeSelector(
    selectedType: OptionType,
    onTypeSelected: (OptionType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceDark)
    ) {
        OptionType.entries.forEach { type ->
            val isSelected = type == selectedType
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
                    .clickable { onTypeSelected(type) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = type.displayName.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.Black else AppColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ResultsSection(
    price: Double,
    optionType: OptionType,
    strikePrice: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Theoretical Price",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${optionType.displayName} Option",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextMuted
                    )
                    Text(
                        text = "₹%.2f".format(price),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (optionType == OptionType.CALL) AppColors.AccentGreen else AppColors.AccentRed
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Strike",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextMuted
                    )
                    Text(
                        text = strikePrice,
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextPrimary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GreeksDisplay(greeks: GreeksResult) {
    var showDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceDark),
        onClick = { showDetails = !showDetails }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Option Greeks",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = if (showDetails) "▲" else "▼",
                    color = AppColors.TextMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GreekCard("Δ", "Delta", greeks.displayDelta, AppColors.AccentGreen)
                GreekCard("Γ", "Gamma", greeks.displayGamma, AppColors.AccentBlue)
                GreekCard("Θ", "Theta", greeks.displayTheta, AppColors.AccentRed)
                GreekCard("ν", "Vega", greeks.displayVega, Color(0xFF9C27B0))
            }

            if (showDetails) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = AppColors.TextMuted.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                InterpretationRow("Delta", greeks.deltaInterpretation)
                InterpretationRow("Theta", greeks.thetaInterpretation)
                InterpretationRow("Vega", greeks.vegaInterpretation)
            }
        }
    }
}

@Composable
private fun GreekCard(
    symbol: String,
    name: String,
    value: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextMuted
        )
    }
}

@Composable
private fun InterpretationRow(greek: String, interpretation: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = greek,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = AppColors.AccentBlue,
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = interpretation,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary
        )
    }
}

@Composable
private fun TargetSLSection(
    targetSpot: String,
    stopLossSpot: String,
    onTargetChange: (String) -> Unit,
    onStopLossChange: (String) -> Unit,
    onSwap: () -> Unit,
    calculation: TargetCalculation?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Target & Stop-Loss",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )
                IconButton(onClick = onSwap) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = "Swap",
                        tint = AppColors.AccentBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InputField(
                    label = "Target Spot",
                    value = targetSpot,
                    onValueChange = onTargetChange,
                    modifier = Modifier.weight(1f)
                )
                InputField(
                    label = "Stop-Loss Spot",
                    value = stopLossSpot,
                    onValueChange = onStopLossChange,
                    modifier = Modifier.weight(1f)
                )
            }

            calculation?.let { calc ->
                Spacer(modifier = Modifier.height(16.dp))

                // Target Result
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.AccentGreen.copy(alpha = 0.1f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "At Target (%.0f)".format(calc.targetSpot),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextMuted
                        )
                        Text(
                            text = "₹${calc.displayTargetPrice}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.AccentGreen
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Profit",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextMuted
                        )
                        Text(
                            text = calc.displayProfit,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.AccentGreen
                        )
                        Text(
                            text = calc.displayProfitPercentage,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.AccentGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Stop-Loss Result
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.AccentRed.copy(alpha = 0.1f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "At Stop-Loss (%.0f)".format(calc.stopLossSpot),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextMuted
                        )
                        Text(
                            text = "₹${calc.displayStopLossPrice}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.AccentRed
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Loss",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextMuted
                        )
                        Text(
                            text = calc.displayLoss,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.AccentRed
                        )
                        Text(
                            text = calc.displayLossPercentage,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.AccentRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Risk-Reward
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.CardDark)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Risk:Reward",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextMuted
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = calc.displayRiskReward,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (calc.isGoodRiskReward) AppColors.AccentGreen else AppColors.AccentRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (calc.isGoodRiskReward) "✓" else "⚠",
                            color = if (calc.isGoodRiskReward) AppColors.AccentGreen else Color.Yellow
                        )
                    }
                }
            }
        }
    }
}
