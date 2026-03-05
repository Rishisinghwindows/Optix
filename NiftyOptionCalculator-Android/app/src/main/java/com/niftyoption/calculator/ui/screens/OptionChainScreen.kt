package com.niftyoption.calculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.niftyoption.calculator.data.models.ExpiryDate
import com.niftyoption.calculator.data.models.LoadingState
import com.niftyoption.calculator.data.models.OptionChainRow
import com.niftyoption.calculator.data.models.OptionData
import com.niftyoption.calculator.ui.theme.AppColors
import com.niftyoption.calculator.ui.viewmodels.OptionChainViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionChainScreen(
    viewModel: OptionChainViewModel,
    onOptionSelected: (OptionData) -> Unit
) {
    val optionChain by viewModel.optionChain.collectAsState()
    val expiryDates by viewModel.expiryDates.collectAsState()
    val selectedExpiry by viewModel.selectedExpiry.collectAsState()
    val spotPrice by viewModel.spotPrice.collectAsState()
    val loadingState by viewModel.loadingState.collectAsState()
    var showNearbyOnly by remember { mutableStateOf(true) }

    val displayedChain = if (showNearbyOnly) {
        viewModel.getNearbyStrikes(optionChain, spotPrice)
    } else {
        optionChain
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NIFTY Options") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
        ) {
            // Header Section
            HeaderSection(
                spotPrice = spotPrice,
                putCallRatio = viewModel.calculatePCR(optionChain),
                totalCallOI = viewModel.getTotalCallOI(optionChain),
                totalPutOI = viewModel.getTotalPutOI(optionChain),
                maxPainStrike = viewModel.calculateMaxPain(optionChain)
            )

            // Expiry Selector
            ExpirySelector(
                expiryDates = expiryDates,
                selectedExpiry = selectedExpiry,
                onExpirySelected = { viewModel.selectExpiry(it) },
                showNearbyOnly = showNearbyOnly,
                onNearbyToggle = { showNearbyOnly = it }
            )

            // Column Headers
            ColumnHeaders()

            // Loading indicator
            when (loadingState) {
                is LoadingState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppColors.AccentBlue)
                    }
                }
                is LoadingState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (loadingState as LoadingState.Error).message,
                            color = AppColors.AccentRed
                        )
                    }
                }
                else -> {
                    // Option Chain List
                    OptionChainList(
                        optionChain = displayedChain,
                        spotPrice = spotPrice,
                        onCallClick = { onOptionSelected(it) },
                        onPutClick = { onOptionSelected(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    spotPrice: Double,
    putCallRatio: Double,
    totalCallOI: Int,
    totalPutOI: Int,
    maxPainStrike: Double?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.SurfaceDark)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "NIFTY SPOT",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextMuted
                )
                Text(
                    text = "%.0f".format(spotPrice),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "PCR",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextMuted
                )
                Text(
                    text = "%.2f".format(putCallRatio),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (putCallRatio > 1) AppColors.AccentGreen else AppColors.AccentRed
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.AccentGreen)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Call OI: ${formatOI(totalCallOI)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.AccentRed)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Put OI: ${formatOI(totalPutOI)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary
                )
            }

            maxPainStrike?.let {
                Text(
                    text = "Max Pain: %.0f".format(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.AccentBlue
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpirySelector(
    expiryDates: List<ExpiryDate>,
    selectedExpiry: ExpiryDate?,
    onExpirySelected: (ExpiryDate) -> Unit,
    showNearbyOnly: Boolean,
    onNearbyToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        expiryDates.forEach { expiry ->
            FilterChip(
                selected = selectedExpiry?.id == expiry.id,
                onClick = { onExpirySelected(expiry) },
                label = { Text(expiry.displayString) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.AccentBlue,
                    containerColor = AppColors.CardDark
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        FilterChip(
            selected = showNearbyOnly,
            onClick = { onNearbyToggle(!showNearbyOnly) },
            label = { Text("Nearby") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AppColors.AccentBlue,
                containerColor = AppColors.CardDark
            )
        )
    }
}

@Composable
private fun ColumnHeaders() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.SurfaceDark)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        // Call side headers
        Row(modifier = Modifier.weight(1f)) {
            Text(
                text = "OI",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.AccentGreen,
                textAlign = TextAlign.Center
            )
            Text(
                text = "LTP",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.AccentGreen,
                textAlign = TextAlign.Center
            )
            Text(
                text = "IV",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.AccentGreen,
                textAlign = TextAlign.Center
            )
        }

        // Strike
        Text(
            text = "STRIKE",
            modifier = Modifier.width(70.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = AppColors.AccentBlue,
            textAlign = TextAlign.Center
        )

        // Put side headers
        Row(modifier = Modifier.weight(1f)) {
            Text(
                text = "IV",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.AccentRed,
                textAlign = TextAlign.Center
            )
            Text(
                text = "LTP",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.AccentRed,
                textAlign = TextAlign.Center
            )
            Text(
                text = "OI",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AppColors.AccentRed,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OptionChainList(
    optionChain: List<OptionChainRow>,
    spotPrice: Double,
    onCallClick: (OptionData) -> Unit,
    onPutClick: (OptionData) -> Unit
) {
    val listState = rememberLazyListState()

    // Scroll to ATM strike on first load
    LaunchedEffect(optionChain, spotPrice) {
        if (optionChain.isNotEmpty() && spotPrice > 0) {
            val atmIndex = optionChain.indexOfFirst { abs(it.strikePrice - spotPrice) <= 25 }
            if (atmIndex >= 0) {
                listState.animateScrollToItem(atmIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(optionChain, key = { it.id }) { row ->
            OptionRow(
                row = row,
                spotPrice = spotPrice,
                onCallClick = { row.callOption?.let(onCallClick) },
                onPutClick = { row.putOption?.let(onPutClick) }
            )
        }
    }
}

@Composable
private fun OptionRow(
    row: OptionChainRow,
    spotPrice: Double,
    onCallClick: () -> Unit,
    onPutClick: () -> Unit
) {
    val isATM = abs(row.strikePrice - spotPrice) <= 25
    val callIsITM = spotPrice > row.strikePrice
    val putIsITM = spotPrice < row.strikePrice

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.SurfaceDark)
    ) {
        // Call side
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onCallClick)
                .background(if (callIsITM) AppColors.AccentGreen.copy(alpha = 0.1f) else Color.Transparent)
                .padding(vertical = 10.dp)
        ) {
            Text(
                text = formatOI(row.callOption?.openInterest),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = row.callOption?.displayLTP ?: "-",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = formatIV(row.callOption?.impliedVolatility),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center
            )
        }

        // Strike price
        Box(
            modifier = Modifier
                .width(70.dp)
                .background(if (isATM) AppColors.AccentBlue.copy(alpha = 0.2f) else Color.Transparent)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = row.displayStrike,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isATM) AppColors.AccentBlue else AppColors.TextPrimary
            )
        }

        // Put side
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPutClick)
                .background(if (putIsITM) AppColors.AccentRed.copy(alpha = 0.1f) else Color.Transparent)
                .padding(vertical = 10.dp)
        ) {
            Text(
                text = formatIV(row.putOption?.impliedVolatility),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = row.putOption?.displayLTP ?: "-",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = formatOI(row.putOption?.openInterest),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatOI(oi: Int?): String {
    if (oi == null) return "-"
    return when {
        oi >= 100_000 -> "%.0fL".format(oi / 100_000.0)
        oi >= 1_000 -> "%.0fK".format(oi / 1_000.0)
        else -> oi.toString()
    }
}

private fun formatIV(iv: Double?): String {
    if (iv == null) return "-"
    return "%.1f".format(iv * 100)
}
