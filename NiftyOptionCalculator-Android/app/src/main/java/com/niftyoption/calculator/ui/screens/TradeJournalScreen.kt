package com.niftyoption.calculator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.niftyoption.calculator.data.models.JournalEntry
import com.niftyoption.calculator.data.models.JournalStats
import com.niftyoption.calculator.data.models.MarketCondition
import com.niftyoption.calculator.data.models.TradeOutcome
import com.niftyoption.calculator.data.models.TradeMood
import com.niftyoption.calculator.ui.theme.AppColors
import com.niftyoption.calculator.ui.viewmodels.JournalFormState
import com.niftyoption.calculator.ui.viewmodels.JournalTab
import com.niftyoption.calculator.ui.viewmodels.TradeJournalViewModel
import kotlinx.coroutines.launch

// MARK: - Main Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeJournalScreen(
    viewModel: TradeJournalViewModel,
    onBack: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val showForm by viewModel.showForm.collectAsState()
    val formState by viewModel.formState.collectAsState()
    val filterOutcome by viewModel.filterOutcome.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trade Journal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (activeTab == JournalTab.ENTRIES) viewModel.loadEntries()
                        else viewModel.loadStats()
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.SurfaceDark
                )
            )
        },
        floatingActionButton = {
            if (activeTab == JournalTab.ENTRIES) {
                FloatingActionButton(
                    onClick = { viewModel.showAddForm() },
                    containerColor = AppColors.AccentBlue,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Entry")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = AppColors.BackgroundDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            JournalTabRow(
                activeTab = activeTab,
                onTabSelected = { viewModel.setActiveTab(it) }
            )

            // Loading indicator
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.AccentBlue)
                }
            }

            // Content
            when (activeTab) {
                JournalTab.ENTRIES -> {
                    EntriesContent(
                        entries = entries,
                        filterOutcome = filterOutcome,
                        onFilterChanged = { viewModel.setFilterOutcome(it) },
                        onEditEntry = { viewModel.showEditForm(it) },
                        onDeleteEntry = { showDeleteDialog = it.id }
                    )
                }
                JournalTab.STATS -> {
                    StatsContent(stats = stats)
                }
            }
        }
    }

    // Bottom Sheet Form
    if (showForm) {
        JournalFormBottomSheet(
            formState = formState,
            onFormUpdate = { viewModel.updateFormState(it) },
            onSubmit = {
                if (formState.isEditing) viewModel.updateEntry()
                else viewModel.createEntry()
            },
            onDismiss = { viewModel.dismissForm() }
        )
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { entryId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Entry", color = AppColors.TextPrimary) },
            text = { Text("Are you sure you want to delete this journal entry?", color = AppColors.TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEntry(entryId)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete", color = AppColors.AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.CardDark
        )
    }
}

// MARK: - Tab Row

@Composable
private fun JournalTabRow(
    activeTab: JournalTab,
    onTabSelected: (JournalTab) -> Unit
) {
    TabRow(
        selectedTabIndex = activeTab.ordinal,
        containerColor = AppColors.SurfaceDark,
        contentColor = AppColors.TextPrimary,
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab.ordinal]),
                color = AppColors.AccentBlue
            )
        }
    ) {
        JournalTab.entries.forEach { tab ->
            Tab(
                selected = activeTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.title,
                        color = if (activeTab == tab) AppColors.AccentBlue else AppColors.TextSecondary
                    )
                }
            )
        }
    }
}

// MARK: - Entries Content

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntriesContent(
    entries: List<JournalEntry>,
    filterOutcome: String?,
    onFilterChanged: (String?) -> Unit,
    onEditEntry: (JournalEntry) -> Unit,
    onDeleteEntry: (JournalEntry) -> Unit
) {
    Column {
        // Outcome Filter Chips
        FlowRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterOutcome == null,
                onClick = { onFilterChanged(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.AccentBlue.copy(alpha = 0.2f),
                    selectedLabelColor = AppColors.AccentBlue,
                    containerColor = AppColors.CardDark,
                    labelColor = AppColors.TextSecondary
                )
            )
            TradeOutcome.entries.forEach { outcome ->
                FilterChip(
                    selected = filterOutcome == outcome.apiValue,
                    onClick = {
                        onFilterChanged(if (filterOutcome == outcome.apiValue) null else outcome.apiValue)
                    },
                    label = { Text(outcome.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (outcome) {
                            TradeOutcome.PROFIT -> AppColors.AccentGreen.copy(alpha = 0.2f)
                            TradeOutcome.LOSS -> AppColors.AccentRed.copy(alpha = 0.2f)
                            TradeOutcome.BREAKEVEN -> AppColors.AccentBlue.copy(alpha = 0.2f)
                        },
                        selectedLabelColor = when (outcome) {
                            TradeOutcome.PROFIT -> AppColors.AccentGreen
                            TradeOutcome.LOSS -> AppColors.AccentRed
                            TradeOutcome.BREAKEVEN -> AppColors.AccentBlue
                        },
                        containerColor = AppColors.CardDark,
                        labelColor = AppColors.TextSecondary
                    )
                )
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No journal entries yet",
                        color = AppColors.TextMuted,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap + to add your first trade",
                        color = AppColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    JournalEntryCard(
                        entry = entry,
                        onEdit = { onEditEntry(entry) },
                        onDelete = { onDeleteEntry(entry) }
                    )
                }
            }
        }
    }
}

// MARK: - Journal Entry Card

@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val pnlColor = when {
        (entry.realized_pnl ?: 0.0) > 0 -> AppColors.AccentGreen
        (entry.realized_pnl ?: 0.0) < 0 -> AppColors.AccentRed
        else -> AppColors.TextSecondary
    }

    val typeColor = if (entry.option_type == "CE") AppColors.AccentGreen else AppColors.AccentRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row: Symbol + Strike + Type | P&L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.symbol,
                        color = AppColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${entry.strike_price.toInt()} ${entry.option_type}",
                        color = typeColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    entry.realized_pnl?.let { pnl ->
                        Text(
                            text = "${if (pnl >= 0) "+" else ""}${String.format("%.2f", pnl)}",
                            color = pnlColor,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    entry.realized_pnl_percent?.let { pct ->
                        Text(
                            text = "${if (pct >= 0) "+" else ""}${String.format("%.1f", pct)}%",
                            color = pnlColor,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Direction + Prices Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.direction.uppercase(),
                    color = if (entry.direction == "buy") AppColors.AccentGreen else AppColors.AccentRed,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Entry: ${String.format("%.2f", entry.entry_price)}",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                entry.exit_price?.let {
                    Text(
                        text = "Exit: ${String.format("%.2f", it)}",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "Qty: ${entry.quantity}x${entry.lot_size}",
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mood + Market Condition + Tags Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mood emoji
                entry.mood?.let { moodStr ->
                    val tradeMood = TradeMood.entries.find { it.apiValue == moodStr }
                    tradeMood?.let {
                        Text(
                            text = it.emoji,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Market condition chip
                entry.market_condition?.let { condStr ->
                    val condition = MarketCondition.entries.find { it.apiValue == condStr }
                    condition?.let {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AppColors.AccentBlue.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = it.displayName,
                                color = AppColors.AccentBlue,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                // Tags
                entry.tags?.let { tagsStr ->
                    tagsStr.split(",").take(3).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AppColors.SurfaceDark)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tag.trim(),
                                color = AppColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // Title/Notes
            entry.title?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = it,
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            entry.notes?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date + Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.entry_date,
                    color = AppColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )

                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit",
                            tint = AppColors.AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint = AppColors.AccentRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Stats Content

@Composable
private fun StatsContent(stats: JournalStats?) {
    if (stats == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No stats available",
                color = AppColors.TextMuted,
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Overview Stats Grid
        item {
            Text(
                text = "Overview",
                color = AppColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            StatsGrid(stats)
        }

        // Win Rate Card
        item {
            Spacer(modifier = Modifier.height(8.dp))
            WinRateCard(stats)
        }

        // P&L Summary
        item {
            Spacer(modifier = Modifier.height(8.dp))
            PnLSummaryCard(stats)
        }

        // Additional Info
        item {
            Spacer(modifier = Modifier.height(8.dp))
            AdditionalStatsCard(stats)
        }
    }
}

@Composable
private fun StatsGrid(stats: JournalStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                label = "Total Trades",
                value = stats.total_trades.toString(),
                color = AppColors.AccentBlue,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Winning",
                value = stats.winning_trades.toString(),
                color = AppColors.AccentGreen,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                label = "Losing",
                value = stats.losing_trades.toString(),
                color = AppColors.AccentRed,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Breakeven",
                value = stats.breakeven_trades.toString(),
                color = AppColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = AppColors.CardDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = color,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = AppColors.TextMuted,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun WinRateCard(stats: JournalStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Win Rate",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            val winRate = stats.win_rate
            val winRateColor = when {
                winRate >= 60 -> AppColors.AccentGreen
                winRate >= 40 -> Color(0xFFFFA726) // Orange
                else -> AppColors.AccentRed
            }

            Text(
                text = "${String.format("%.1f", winRate)}%",
                color = winRateColor,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Simple progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppColors.SurfaceDark)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (winRate / 100f).toFloat().coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(winRateColor)
                )
            }
        }
    }
}

@Composable
private fun PnLSummaryCard(stats: JournalStats) {
    val totalPnlColor = when {
        stats.total_pnl > 0 -> AppColors.AccentGreen
        stats.total_pnl < 0 -> AppColors.AccentRed
        else -> AppColors.TextSecondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "P&L Summary",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total P&L", color = AppColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "${if (stats.total_pnl >= 0) "+" else ""}${String.format("%.2f", stats.total_pnl)}",
                        color = totalPnlColor,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Avg P&L", color = AppColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                    val avgColor = if (stats.average_pnl >= 0) AppColors.AccentGreen else AppColors.AccentRed
                    Text(
                        text = "${if (stats.average_pnl >= 0) "+" else ""}${String.format("%.2f", stats.average_pnl)}",
                        color = avgColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                stats.best_trade?.let {
                    Column {
                        Text("Best Trade", color = AppColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = "+${String.format("%.2f", it)}",
                            color = AppColors.AccentGreen,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                stats.worst_trade?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Worst Trade", color = AppColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                        Text(
                            text = String.format("%.2f", it),
                            color = AppColors.AccentRed,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdditionalStatsCard(stats: JournalStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Additional Info",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(12.dp))

            stats.average_holding_days?.let {
                InfoRow(label = "Avg Holding Days", value = String.format("%.1f", it))
            }
            stats.most_traded_symbol?.let {
                InfoRow(label = "Most Traded", value = it)
            }
            if (stats.most_used_tags.isNotEmpty()) {
                InfoRow(label = "Top Tags", value = stats.most_used_tags.take(3).joinToString(", "))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = AppColors.TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

// MARK: - Journal Form Bottom Sheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun JournalFormBottomSheet(
    formState: JournalFormState,
    onFormUpdate: ((JournalFormState) -> JournalFormState) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.SurfaceDark,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (formState.isEditing) "Edit Entry" else "New Entry",
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = AppColors.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Symbol + Strike Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormTextField(
                    value = formState.symbol,
                    onValueChange = { v -> onFormUpdate { it.copy(symbol = v) } },
                    label = "Symbol",
                    modifier = Modifier.weight(1f)
                )
                FormTextField(
                    value = formState.strikePrice,
                    onValueChange = { v -> onFormUpdate { it.copy(strikePrice = v) } },
                    label = "Strike Price",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option Type (CE/PE)
            Text("Option Type", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("CE", "PE").forEach { type ->
                    FilterChip(
                        selected = formState.optionType == type,
                        onClick = { onFormUpdate { it.copy(optionType = type) } },
                        label = { Text(type) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (type == "CE") AppColors.AccentGreen.copy(alpha = 0.2f)
                            else AppColors.AccentRed.copy(alpha = 0.2f),
                            selectedLabelColor = if (type == "CE") AppColors.AccentGreen else AppColors.AccentRed,
                            containerColor = AppColors.CardDark,
                            labelColor = AppColors.TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Direction (Buy/Sell)
            Text("Direction", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("buy", "sell").forEach { dir ->
                    FilterChip(
                        selected = formState.direction == dir,
                        onClick = { onFormUpdate { it.copy(direction = dir) } },
                        label = { Text(dir.uppercase()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (dir == "buy") AppColors.AccentGreen.copy(alpha = 0.2f)
                            else AppColors.AccentRed.copy(alpha = 0.2f),
                            selectedLabelColor = if (dir == "buy") AppColors.AccentGreen else AppColors.AccentRed,
                            containerColor = AppColors.CardDark,
                            labelColor = AppColors.TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Entry Price + Exit Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormTextField(
                    value = formState.entryPrice,
                    onValueChange = { v -> onFormUpdate { it.copy(entryPrice = v) } },
                    label = "Entry Price",
                    modifier = Modifier.weight(1f)
                )
                FormTextField(
                    value = formState.exitPrice,
                    onValueChange = { v -> onFormUpdate { it.copy(exitPrice = v) } },
                    label = "Exit Price",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quantity + Lot Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormTextField(
                    value = formState.quantity,
                    onValueChange = { v -> onFormUpdate { it.copy(quantity = v) } },
                    label = "Quantity (lots)",
                    modifier = Modifier.weight(1f)
                )
                FormTextField(
                    value = formState.lotSize,
                    onValueChange = { v -> onFormUpdate { it.copy(lotSize = v) } },
                    label = "Lot Size",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Entry Date + Exit Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormTextField(
                    value = formState.entryDate,
                    onValueChange = { v -> onFormUpdate { it.copy(entryDate = v) } },
                    label = "Entry Date",
                    placeholder = "YYYY-MM-DD",
                    modifier = Modifier.weight(1f)
                )
                FormTextField(
                    value = formState.exitDate,
                    onValueChange = { v -> onFormUpdate { it.copy(exitDate = v) } },
                    label = "Exit Date",
                    placeholder = "YYYY-MM-DD",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expiry Date
            FormTextField(
                value = formState.expiryDate,
                onValueChange = { v -> onFormUpdate { it.copy(expiryDate = v) } },
                label = "Expiry Date",
                placeholder = "YYYY-MM-DD",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Mood
            Text("Mood", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TradeMood.entries.forEach { mood ->
                    FilterChip(
                        selected = formState.mood == mood,
                        onClick = {
                            onFormUpdate {
                                it.copy(mood = if (it.mood == mood) null else mood)
                            }
                        },
                        label = { Text("${mood.emoji} ${mood.displayName}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.AccentBlue.copy(alpha = 0.2f),
                            selectedLabelColor = AppColors.AccentBlue,
                            containerColor = AppColors.CardDark,
                            labelColor = AppColors.TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Market Condition
            Text("Market Condition", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MarketCondition.entries.forEach { condition ->
                    FilterChip(
                        selected = formState.marketCondition == condition,
                        onClick = {
                            onFormUpdate {
                                it.copy(marketCondition = if (it.marketCondition == condition) null else condition)
                            }
                        },
                        label = { Text(condition.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AppColors.AccentBlue.copy(alpha = 0.2f),
                            selectedLabelColor = AppColors.AccentBlue,
                            containerColor = AppColors.CardDark,
                            labelColor = AppColors.TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Outcome
            Text("Outcome", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TradeOutcome.entries.forEach { outcome ->
                    FilterChip(
                        selected = formState.outcome == outcome,
                        onClick = {
                            onFormUpdate {
                                it.copy(outcome = if (it.outcome == outcome) null else outcome)
                            }
                        },
                        label = { Text(outcome.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (outcome) {
                                TradeOutcome.PROFIT -> AppColors.AccentGreen.copy(alpha = 0.2f)
                                TradeOutcome.LOSS -> AppColors.AccentRed.copy(alpha = 0.2f)
                                TradeOutcome.BREAKEVEN -> AppColors.AccentBlue.copy(alpha = 0.2f)
                            },
                            selectedLabelColor = when (outcome) {
                                TradeOutcome.PROFIT -> AppColors.AccentGreen
                                TradeOutcome.LOSS -> AppColors.AccentRed
                                TradeOutcome.BREAKEVEN -> AppColors.AccentBlue
                            },
                            containerColor = AppColors.CardDark,
                            labelColor = AppColors.TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            FormTextField(
                value = formState.title,
                onValueChange = { v -> onFormUpdate { it.copy(title = v) } },
                label = "Title (optional)",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tags
            FormTextField(
                value = formState.tags,
                onValueChange = { v -> onFormUpdate { it.copy(tags = v) } },
                label = "Tags (comma separated)",
                placeholder = "scalp, expiry_day",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Notes
            FormTextField(
                value = formState.notes,
                onValueChange = { v -> onFormUpdate { it.copy(notes = v) } },
                label = "Notes (optional)",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 3
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Submit Button
            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.AccentBlue
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (formState.isEditing) "Update Entry" else "Add Entry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// MARK: - Form Text Field

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = AppColors.TextMuted) } },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.AccentBlue,
            unfocusedBorderColor = AppColors.TextMuted.copy(alpha = 0.3f),
            focusedLabelColor = AppColors.AccentBlue,
            unfocusedLabelColor = AppColors.TextMuted,
            cursorColor = AppColors.AccentBlue,
            focusedTextColor = AppColors.TextPrimary,
            unfocusedTextColor = AppColors.TextPrimary
        )
    )
}
