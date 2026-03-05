package com.niftyoption.calculator.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.niftyoption.calculator.data.models.MoneynessFilter
import com.niftyoption.calculator.data.models.OptionType
import com.niftyoption.calculator.data.models.ScreenerFilter
import com.niftyoption.calculator.data.models.ScreenerOptionType
import com.niftyoption.calculator.data.models.ScreenerResult
import com.niftyoption.calculator.data.models.ScreenerSortBy
import com.niftyoption.calculator.ui.theme.AppColors
import com.niftyoption.calculator.ui.viewmodels.OptionScreenerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionScreenerScreen(
    viewModel: OptionScreenerViewModel,
    onBack: () -> Unit
) {
    val filter by viewModel.filter.collectAsState()
    val results by viewModel.results.collectAsState()
    val resultCount by viewModel.resultCount.collectAsState()

    var filtersExpanded by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Option Screener") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetFilters() }) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter section
            item {
                FilterSection(
                    filter = filter,
                    expanded = filtersExpanded,
                    onToggleExpand = { filtersExpanded = !filtersExpanded },
                    onFilterChange = { viewModel.updateFilter(it) }
                )
            }

            // Result count
            item {
                Text(
                    text = "$resultCount results found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Results list
            if (results.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No options match the current filters.\nTry adjusting your criteria.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppColors.TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(results, key = { "${it.strikePrice}_${it.optionType}" }) { result ->
                    ScreenerResultCard(result = result)
                }
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FilterSection(
    filter: ScreenerFilter,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onFilterChange: (ScreenerFilter) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceDark),
        onClick = onToggleExpand
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filters",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = AppColors.TextMuted
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Option Type chips
                    Text(
                        text = "Option Type",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextMuted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScreenerOptionType.entries.forEach { type ->
                            FilterChip(
                                selected = filter.optionType == type,
                                onClick = { onFilterChange(filter.copy(optionType = type)) },
                                label = { Text(type.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (type) {
                                        ScreenerOptionType.CALL -> AppColors.AccentGreen
                                        ScreenerOptionType.PUT -> AppColors.AccentRed
                                        ScreenerOptionType.BOTH -> AppColors.AccentBlue
                                    },
                                    containerColor = AppColors.CardDark,
                                    labelColor = AppColors.TextPrimary,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }

                    // Moneyness chips
                    Text(
                        text = "Moneyness",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextMuted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MoneynessFilter.entries.forEach { moneyness ->
                            FilterChip(
                                selected = filter.moneyness == moneyness,
                                onClick = { onFilterChange(filter.copy(moneyness = moneyness)) },
                                label = { Text(moneyness.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.AccentBlue,
                                    containerColor = AppColors.CardDark,
                                    labelColor = AppColors.TextPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Delta range
                    Text(
                        text = "Delta Range",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScreenerTextField(
                            label = "Min",
                            value = if (filter.deltaMin == 0.0) "" else filter.deltaMin.toString(),
                            onValueChange = {
                                val v = it.toDoubleOrNull() ?: 0.0
                                onFilterChange(filter.copy(deltaMin = v))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ScreenerTextField(
                            label = "Max",
                            value = if (filter.deltaMax == 1.0) "" else filter.deltaMax.toString(),
                            onValueChange = {
                                val v = it.toDoubleOrNull() ?: 1.0
                                onFilterChange(filter.copy(deltaMax = v))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // IV range
                    Text(
                        text = "IV Range (%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScreenerTextField(
                            label = "Min",
                            value = if (filter.ivMin == 0.0) "" else filter.ivMin.toString(),
                            onValueChange = {
                                val v = it.toDoubleOrNull() ?: 0.0
                                onFilterChange(filter.copy(ivMin = v))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ScreenerTextField(
                            label = "Max",
                            value = if (filter.ivMax == 200.0) "" else filter.ivMax.toString(),
                            onValueChange = {
                                val v = it.toDoubleOrNull() ?: 200.0
                                onFilterChange(filter.copy(ivMax = v))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Min OI and Min Volume
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScreenerTextField(
                            label = "Min OI",
                            value = if (filter.oiMin == 0L) "" else filter.oiMin.toString(),
                            onValueChange = {
                                val v = it.toLongOrNull() ?: 0L
                                onFilterChange(filter.copy(oiMin = v))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ScreenerTextField(
                            label = "Min Volume",
                            value = if (filter.volumeMin == 0L) "" else filter.volumeMin.toString(),
                            onValueChange = {
                                val v = it.toLongOrNull() ?: 0L
                                onFilterChange(filter.copy(volumeMin = v))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Premium range
                    Text(
                        text = "Premium Range",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScreenerTextField(
                            label = "Min",
                            value = if (filter.premiumMin == 0.0) "" else filter.premiumMin.toString(),
                            onValueChange = {
                                val v = it.toDoubleOrNull() ?: 0.0
                                onFilterChange(filter.copy(premiumMin = v))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        ScreenerTextField(
                            label = "Max",
                            value = if (filter.premiumMax == 99999.0) "" else filter.premiumMax.toString(),
                            onValueChange = {
                                val v = it.toDoubleOrNull() ?: 99999.0
                                onFilterChange(filter.copy(premiumMax = v))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Sort by
                    Text(
                        text = "Sort By",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextMuted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScreenerSortBy.entries.forEach { sort ->
                            FilterChip(
                                selected = filter.sortBy == sort,
                                onClick = { onFilterChange(filter.copy(sortBy = sort)) },
                                label = { Text(sort.displayName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.AccentBlue,
                                    containerColor = AppColors.CardDark,
                                    labelColor = AppColors.TextPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    // Sort direction
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Direction:",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppColors.TextMuted
                        )
                        FilterChip(
                            selected = !filter.sortAscending,
                            onClick = { onFilterChange(filter.copy(sortAscending = false)) },
                            label = { Text("Desc") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.AccentBlue,
                                containerColor = AppColors.CardDark,
                                labelColor = AppColors.TextPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = filter.sortAscending,
                            onClick = { onFilterChange(filter.copy(sortAscending = true)) },
                            label = { Text("Asc") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppColors.AccentBlue,
                                containerColor = AppColors.CardDark,
                                labelColor = AppColors.TextPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenerTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = AppColors.TextMuted) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.AccentBlue,
            unfocusedBorderColor = AppColors.CardDark,
            focusedTextColor = AppColors.TextPrimary,
            unfocusedTextColor = AppColors.TextPrimary,
            cursorColor = AppColors.AccentBlue
        ),
        singleLine = true
    )
}

@Composable
private fun ScreenerResultCard(result: ScreenerResult) {
    val typeColor = if (result.optionType == OptionType.CALL) AppColors.AccentGreen else AppColors.AccentRed
    val typeName = if (result.optionType == OptionType.CALL) "CE" else "PE"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Strike + Type badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.displayStrike,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AppColors.TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(typeColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
                Text(
                    text = result.displayLTP,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AppColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Data grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScreenerDataCell(label = "IV", value = result.displayIV, color = Color(0xFF9C27B0))
                ScreenerDataCell(label = "Delta", value = result.displayDelta, color = AppColors.AccentGreen)
                ScreenerDataCell(label = "OI", value = result.displayOI, color = AppColors.AccentBlue)
                ScreenerDataCell(label = "Vol", value = result.displayVolume, color = AppColors.TextSecondary)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Second row: Gamma, Theta, Vega
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ScreenerDataCell(
                    label = "Gamma",
                    value = "%.6f".format(result.gamma),
                    color = AppColors.AccentBlue
                )
                ScreenerDataCell(
                    label = "Theta",
                    value = "%.2f".format(result.theta),
                    color = AppColors.AccentRed
                )
                ScreenerDataCell(
                    label = "Vega",
                    value = "%.2f".format(result.vega),
                    color = Color(0xFF9C27B0)
                )
                ScreenerDataCell(
                    label = "OI Chg",
                    value = when {
                        result.oiChange >= 1000 -> "%+.1fK".format(result.oiChange / 1000.0)
                        else -> "%+d".format(result.oiChange)
                    },
                    color = if (result.oiChange >= 0) AppColors.AccentGreen else AppColors.AccentRed
                )
            }
        }
    }
}

@Composable
private fun ScreenerDataCell(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextMuted,
            textAlign = TextAlign.Center
        )
    }
}
