package com.optix.app.presentation.screens.screener

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.core.util.AnalyticsHelper
import com.optix.app.domain.model.OptionType
import com.optix.app.domain.model.TradingIndex
import com.optix.app.presentation.theme.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionScreenerScreen(
    viewModel: OptionScreenerViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    LaunchedEffect(Unit) { AnalyticsHelper.logScreenView("option_screener") }

    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Option Screener", fontWeight = FontWeight.Bold)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Index selector
            item {
                IndexSelector(
                    selectedIndex = state.selectedIndex,
                    onIndexSelected = { viewModel.setIndex(it) }
                )
            }

            // Filter card
            item {
                FilterCard(
                    filters = state.filters,
                    onFiltersChanged = { viewModel.updateFilters(it) },
                    onReset = { viewModel.resetFilters() }
                )
            }

            // Result count
            item {
                Text(
                    text = "${state.resultCount} results",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            // Loading
            if (state.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryGreen)
                    }
                }
            }

            // Error
            state.error?.let { error ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = ErrorRed.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = error,
                            color = ErrorRed,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Results
            if (!state.isLoading && state.error == null) {
                if (state.results.isEmpty() && state.resultCount == 0) {
                    item {
                        EmptyResultsView()
                    }
                } else {
                    items(state.results, key = { "${it.strikePrice}_${it.optionType.code}" }) { result ->
                        ScreenerResultCard(result = result)
                    }
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// -- Index Selector --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndexSelector(
    selectedIndex: TradingIndex,
    onIndexSelected: (TradingIndex) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val indices = listOf(TradingIndex.NIFTY50, TradingIndex.BANKNIFTY, TradingIndex.FINNIFTY)
        indices.forEachIndexed { index, tradingIndex ->
            SegmentedButton(
                selected = selectedIndex == tradingIndex,
                onClick = { onIndexSelected(tradingIndex) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = indices.size)
            ) {
                Text(tradingIndex.symbol, fontSize = 12.sp)
            }
        }
    }
}

// -- Filter Card --

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterCard(
    filters: ScreenerFilter,
    onFiltersChanged: (ScreenerFilter) -> Unit,
    onReset: () -> Unit
) {
    var localFilters by remember(filters) { mutableStateOf(filters) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Option Type
            FilterLabel("Option Type")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ScreenerOptionType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = localFilters.optionType == type,
                        onClick = {
                            localFilters = localFilters.copy(optionType = type)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ScreenerOptionType.entries.size
                        )
                    ) {
                        Text(type.label, fontSize = 12.sp)
                    }
                }
            }

            // Moneyness
            FilterLabel("Moneyness")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                MoneynessFilter.entries.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = localFilters.moneyness == m,
                        onClick = {
                            localFilters = localFilters.copy(moneyness = m)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = MoneynessFilter.entries.size
                        )
                    ) {
                        Text(m.label, fontSize = 11.sp)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Delta Range
            RangeFilterRow(
                label = "Delta Range",
                minValue = localFilters.deltaMin.toString(),
                maxValue = localFilters.deltaMax.toString(),
                onMinChanged = { localFilters = localFilters.copy(deltaMin = it.toDoubleOrNull() ?: 0.0) },
                onMaxChanged = { localFilters = localFilters.copy(deltaMax = it.toDoubleOrNull() ?: 1.0) }
            )

            // IV Range
            RangeFilterRow(
                label = "IV Range (%)",
                minValue = localFilters.ivMin.toString(),
                maxValue = localFilters.ivMax.toString(),
                onMinChanged = { localFilters = localFilters.copy(ivMin = it.toDoubleOrNull() ?: 0.0) },
                onMaxChanged = { localFilters = localFilters.copy(ivMax = it.toDoubleOrNull() ?: 200.0) }
            )

            // Premium Range
            RangeFilterRow(
                label = "Premium Range",
                minValue = localFilters.premiumMin.toString(),
                maxValue = localFilters.premiumMax.toString(),
                onMinChanged = { localFilters = localFilters.copy(premiumMin = it.toDoubleOrNull() ?: 0.0) },
                onMaxChanged = { localFilters = localFilters.copy(premiumMax = it.toDoubleOrNull() ?: 100000.0) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Min OI and Min Volume
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IntFilterField(
                    label = "Min OI",
                    value = localFilters.oiMin.toString(),
                    onValueChanged = { localFilters = localFilters.copy(oiMin = it.toLongOrNull() ?: 0) },
                    modifier = Modifier.weight(1f)
                )
                IntFilterField(
                    label = "Min Volume",
                    value = localFilters.volumeMin.toString(),
                    onValueChanged = { localFilters = localFilters.copy(volumeMin = it.toLongOrNull() ?: 0) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Sort By + Order
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    FilterLabel("Sort By")
                    var sortExpanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { sortExpanded = true }) {
                            Text(localFilters.sortBy.label)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false }
                        ) {
                            ScreenerSortBy.entries.forEach { sortOption ->
                                DropdownMenuItem(
                                    text = { Text(sortOption.label) },
                                    onClick = {
                                        localFilters = localFilters.copy(sortBy = sortOption)
                                        sortExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    FilterLabel("Order")
                    TextButton(
                        onClick = {
                            localFilters = localFilters.copy(sortAscending = !localFilters.sortAscending)
                        }
                    ) {
                        Icon(
                            imageVector = if (localFilters.sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (localFilters.sortAscending) "Asc" else "Desc")
                    }
                }
            }

            // Reset + Apply buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        localFilters = ScreenerFilter()
                        onReset()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ErrorRed
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Reset", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { onFiltersChanged(localFilters) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(AccentBlue, AccentPurple)
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Apply Filters",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RangeFilterRow(
    label: String,
    minValue: String,
    maxValue: String,
    onMinChanged: (String) -> Unit,
    onMaxChanged: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterLabel(label)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = minValue,
                onValueChange = onMinChanged,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            Text(
                "to",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = maxValue,
                onValueChange = onMaxChanged,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        }
    }
}

@Composable
private fun IntFilterField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChanged,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

// -- Empty Results --

@Composable
private fun EmptyResultsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = "No options match your filters",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Try adjusting your criteria",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

// -- Result Card --

@Composable
private fun ScreenerResultCard(result: ScreenerResult) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Strike + Type Badge + LTP
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format("%.0f", result.strikePrice),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val badgeColor = if (result.optionType == OptionType.CALL) CallColor else PutColor
                    Text(
                        text = result.optionType.code,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = String.format("%.2f", result.ltp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Primary metrics row
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem("IV", String.format("%.1f%%", result.iv * 100), Modifier.weight(1f))
                MetricItem("Delta", String.format("%.3f", result.delta), Modifier.weight(1f))
                MetricItem("OI", formatCompact(result.oi), Modifier.weight(1f))
                MetricItem("Vol", formatCompact(result.volume), Modifier.weight(1f))
            }

            // Secondary Greeks row
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricItem("Gamma", String.format("%.5f", result.gamma), Modifier.weight(1f))
                MetricItem("Theta", String.format("%.2f", result.theta), Modifier.weight(1f))
                MetricItem("Vega", String.format("%.2f", result.vega), Modifier.weight(1f))
                val oiChangeColor = if (result.oiChange >= 0) ProfitGreen else ErrorRed
                MetricItem(
                    label = "OI Chg",
                    value = formatCompact(result.oiChange),
                    modifier = Modifier.weight(1f),
                    valueColor = oiChangeColor
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatCompact(value: Long): String {
    val absValue = abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        absValue >= 10_000_000 -> "${sign}${String.format("%.1f", absValue / 1_000_000.0)}M"
        absValue >= 100_000 -> "${sign}${String.format("%.1f", absValue / 100_000.0)}L"
        absValue >= 1_000 -> "${sign}${String.format("%.1f", absValue / 1_000.0)}K"
        else -> "$sign$absValue"
    }
}
