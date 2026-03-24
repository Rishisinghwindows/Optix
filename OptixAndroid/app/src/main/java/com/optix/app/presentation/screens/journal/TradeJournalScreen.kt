package com.optix.app.presentation.screens.journal

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.os.Bundle
import com.optix.app.core.util.AnalyticsHelper
import com.optix.app.domain.model.*
import com.optix.app.presentation.theme.*
import kotlin.math.abs
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradeJournalScreen(
    viewModel: TradeJournalViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit = {}
) {
    LaunchedEffect(Unit) { AnalyticsHelper.logScreenView("trade_journal") }

    val state by viewModel.state.collectAsState()

    // Log rich trade journal data when entries load
    LaunchedEffect(state.isLoggedIn, state.totalEntries) {
        AnalyticsHelper.logEvent("trade_journal_view", Bundle().apply {
            putBoolean("is_logged_in", state.isLoggedIn)
            putInt("total_entries", state.totalEntries)
            putInt("displayed_entries", state.entries.size)
            putString("active_tab", state.activeTab.name)
            state.filterOutcome?.let { putString("filter_outcome", it.name) }
            putBoolean("has_stats", state.stats != null)
        })
    }

    // Error dialog
    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("Error") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("OK")
                }
            }
        )
    }

    // Delete confirmation dialog
    state.entryToDelete?.let {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteConfirmation() },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete this journal entry? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteEntry() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Loss)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Entry form bottom sheet
    if (state.showForm) {
        JournalEntryFormSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { viewModel.dismissForm() }
        )
    }

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
            JournalLoginPrompt(onLoginClick = onNavigateToLogin)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                JournalTopBar(onAddClick = { viewModel.startNewEntry() })

                // Tab bar
                JournalTabBar(
                    activeTab = state.activeTab,
                    onTabSelected = { viewModel.setActiveTab(it) }
                )

                // Content
                when (state.activeTab) {
                    JournalTab.ENTRIES -> EntriesContent(state = state, viewModel = viewModel)
                    JournalTab.STATS -> StatsContent(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

// ============== Top Bar ==============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalTopBar(onAddClick: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "Trade Journal",
                fontWeight = FontWeight.Bold
            )
        },
        actions = {
            IconButton(onClick = onAddClick) {
                Icon(
                    Icons.Filled.AddCircle,
                    contentDescription = "Add Entry",
                    tint = AccentBlue
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        )
    )
}

// ============== Tab Bar ==============

@Composable
private fun JournalTabBar(activeTab: JournalTab, onTabSelected: (JournalTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp)
    ) {
        JournalTab.entries.forEach { tab ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tab.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (activeTab == tab) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(2.dp)
                        .background(
                            if (activeTab == tab) AccentBlue else Color.Transparent,
                            RoundedCornerShape(1.dp)
                        )
                )
            }
        }
    }
}

// ============== Login Prompt ==============

@Composable
private fun JournalLoginPrompt(onLoginClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = AccentBlue
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Trade Journal",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Login to record and review your trades. Track your performance, emotions, and improve your strategy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onLoginClick,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Text("Login to Continue", fontWeight = FontWeight.Bold)
        }
    }
}

// ============== Entries Content ==============

@Composable
private fun EntriesContent(state: TradeJournalState, viewModel: TradeJournalViewModel) {
    if (state.isLoading && state.entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = AccentBlue)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (state.entries.isEmpty()) {
        EmptyEntriesView(onAddClick = { viewModel.startNewEntry() })
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter bar
            item {
                FilterBar(state = state, viewModel = viewModel)
            }

            // Entries
            items(state.entries, key = { it.id }) { entry ->
                JournalEntryCard(
                    entry = entry,
                    viewModel = viewModel,
                    onEdit = { viewModel.startEditEntry(entry) },
                    onDelete = { viewModel.requestDeleteEntry(entry) }
                )
            }
        }
    }
}

// ============== Filter Bar ==============

@Composable
private fun FilterBar(state: TradeJournalState, viewModel: TradeJournalViewModel) {
    val hasActiveFilter = state.filterOutcome != null || state.filterSymbol.isNotEmpty() || state.filterTag != null

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Outcome filter
        item {
            var expanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = state.filterOutcome != null,
                    onClick = { expanded = true },
                    label = { Text(state.filterOutcome?.displayName ?: "Outcome", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                        selectedLabelColor = AccentBlue
                    )
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("All") },
                        onClick = { viewModel.setFilterOutcome(null); expanded = false }
                    )
                    TradeOutcome.entries.forEach { outcome ->
                        DropdownMenuItem(
                            text = { Text(outcome.displayName) },
                            onClick = { viewModel.setFilterOutcome(outcome); expanded = false }
                        )
                    }
                }
            }
        }

        // Symbol filter
        item {
            var expanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = state.filterSymbol.isNotEmpty(),
                    onClick = { expanded = true },
                    label = { Text(state.filterSymbol.ifEmpty { "Symbol" }, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                        selectedLabelColor = AccentBlue
                    )
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("All") },
                        onClick = { viewModel.setFilterSymbol(""); expanded = false }
                    )
                    TradeJournalViewModel.AVAILABLE_SYMBOLS.forEach { symbol ->
                        DropdownMenuItem(
                            text = { Text(symbol) },
                            onClick = { viewModel.setFilterSymbol(symbol); expanded = false }
                        )
                    }
                }
            }
        }

        // Tag filter
        item {
            var expanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = state.filterTag != null,
                    onClick = { expanded = true },
                    label = { Text(state.filterTag?.displayName ?: "Tag", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue.copy(alpha = 0.2f),
                        selectedLabelColor = AccentBlue
                    )
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("All") },
                        onClick = { viewModel.setFilterTag(null); expanded = false }
                    )
                    JournalTag.entries.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text(tag.displayName) },
                            onClick = { viewModel.setFilterTag(tag); expanded = false }
                        )
                    }
                }
            }
        }

        // Clear button
        if (hasActiveFilter) {
            item {
                IconButton(onClick = { viewModel.clearFilters() }) {
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = "Clear filters",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============== Empty State ==============

@Composable
private fun EmptyEntriesView(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.NoteAdd,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No Journal Entries",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Start recording your trades to track your progress and identify patterns.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onAddClick,
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Add First Entry", fontWeight = FontWeight.Bold)
        }
    }
}

// ============== Journal Entry Card ==============

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    viewModel: TradeJournalViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val pnl = viewModel.computePnl(entry)
    val pnlColor = viewModel.pnlColor(entry)
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onEdit() },
                onLongClick = { showMenu = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: Symbol + Strike + P&L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.symbol,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${String.format("%.0f", entry.strikePrice)} ${entry.optionType}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        entry.direction.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (entry.direction == "buy") Profit else Loss
                    )
                }

                // P&L or Open badge
                if (pnl != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${if (pnl >= 0) "+" else "-"}\u20B9${String.format("%.0f", abs(pnl))}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = pnlColor
                        )
                        entry.realizedPnlPercent?.let { pct ->
                            Text(
                                text = String.format("%+.1f%%", pct),
                                fontSize = 12.sp,
                                color = pnlColor
                            )
                        }
                    }
                } else {
                    Text(
                        "Open",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentOrange,
                        modifier = Modifier
                            .background(
                                AccentOrange.copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Price info row
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PriceLabel("Entry:", "\u20B9${String.format("%.2f", entry.entryPrice)}")
                entry.exitPrice?.let { ep ->
                    PriceLabel("Exit:", "\u20B9${String.format("%.2f", ep)}")
                }
                PriceLabel("Qty:", "${entry.quantity}x${entry.lotSize}")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tags, mood, date row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tags
                entry.tags?.takeIf { it.isNotEmpty() }?.split(",")?.forEach { tag ->
                    Text(
                        text = tag.trim().replace("_", " "),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentBlue,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .background(
                                AccentBlue.copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Mood emoji
                entry.mood?.let { moodStr ->
                    TradeMood.fromApiValue(moodStr)?.let { mood ->
                        Text(mood.emoji, fontSize = 12.sp, modifier = Modifier.padding(end = 4.dp))
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Date
                Text(
                    entry.entryDate.take(10),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Title
            entry.title?.takeIf { it.isNotEmpty() }?.let { title ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    title,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Notes preview
            entry.notes?.takeIf { it.isNotEmpty() }?.let { notes ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    notes,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Context menu
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = { showMenu = false; onEdit() },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = Loss) },
                onClick = { showMenu = false; onDelete() },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Loss) }
            )
        }
    }
}

@Composable
private fun PriceLabel(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.width(3.dp))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============== Stats Content ==============

@Composable
private fun StatsContent(state: TradeJournalState, viewModel: TradeJournalViewModel) {
    if (state.isLoading && state.stats == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentBlue)
        }
    } else if (state.stats != null) {
        val stats = state.stats
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overview card
            item { StatsOverviewCard(stats) }

            // P&L card
            item { PnlSummaryCard(stats) }

            // Win rate card
            item { WinRateCard(stats) }

            // Most traded
            stats.mostTradedSymbol?.let { symbol ->
                item { InfoCard("Most Traded", symbol, Icons.Filled.BarChart) }
            }

            // Average holding
            stats.averageHoldingDays?.let { days ->
                item { InfoCard("Avg Holding Period", String.format("%.1f days", days), Icons.Filled.Schedule) }
            }

            // Mood distribution
            if (stats.tradesByMood.isNotEmpty()) {
                item { MoodDistributionCard(stats) }
            }

            // Market condition distribution
            if (stats.tradesByCondition.isNotEmpty()) {
                item { ConditionDistributionCard(stats) }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.PieChart,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("No stats available", fontWeight = FontWeight.Bold)
            Text(
                "Add some journal entries to see your statistics.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StatsOverviewCard(stats: JournalStats) {
    StatsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Overview", fontWeight = FontWeight.Bold)
            Text("${stats.totalTrades} trades", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Wins", "${stats.winningTrades}", Profit)
            StatItem("Losses", "${stats.losingTrades}", Loss)
            StatItem("Breakeven", "${stats.breakevenTrades}", MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PnlSummaryCard(stats: JournalStats) {
    StatsCard {
        Text("P&L Summary", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total P&L", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatIndianCurrency(stats.totalPnl),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (stats.totalPnl >= 0) Profit else Loss
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Average P&L", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatIndianCurrency(stats.averagePnl),
                    fontWeight = FontWeight.Bold,
                    color = if (stats.averagePnl >= 0) Profit else Loss
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            stats.bestTrade?.let { best ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Best Trade", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatIndianCurrency(best), fontWeight = FontWeight.Bold, color = Profit)
                }
            }
            stats.worstTrade?.let { worst ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Worst Trade", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatIndianCurrency(worst), fontWeight = FontWeight.Bold, color = Loss)
                }
            }
        }
    }
}

@Composable
private fun WinRateCard(stats: JournalStats) {
    StatsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Win Rate", fontWeight = FontWeight.Bold)
            Text(
                String.format("%.1f%%", stats.winRate),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (stats.winRate >= 50) Profit else Loss
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Win rate progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Loss.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (min(stats.winRate, 100.0) / 100.0).toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Profit)
            )
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MoodDistributionCard(stats: JournalStats) {
    StatsCard {
        Text("Trades by Mood", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        stats.tradesByMood
            .toList()
            .sortedByDescending { it.second }
            .forEach { (mood, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        TradeMood.fromApiValue(mood)?.let { m ->
                            Text(m.emoji, modifier = Modifier.padding(end = 6.dp))
                        }
                        Text(
                            mood.replaceFirstChar { it.uppercase() },
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        "$count",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
    }
}

@Composable
private fun ConditionDistributionCard(stats: JournalStats) {
    StatsCard {
        Text("Trades by Market Condition", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        stats.tradesByCondition
            .toList()
            .sortedByDescending { it.second }
            .forEach { (condition, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        condition.replace("_", " ").replaceFirstChar { it.uppercase() },
                        fontSize = 14.sp
                    )
                    Text(
                        "$count",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

// ============== Entry Form Bottom Sheet ==============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalEntryFormSheet(
    state: TradeJournalState,
    viewModel: TradeJournalViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = state.editingEntry != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                if (isEditing) "Edit Entry" else "New Journal Entry",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Symbol picker
            Text("Symbol", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TradeJournalViewModel.AVAILABLE_SYMBOLS) { symbol ->
                    FilterChip(
                        selected = state.formSymbol == symbol,
                        onClick = { viewModel.updateFormSymbol(symbol) },
                        label = { Text(symbol, fontSize = 12.sp) },
                        enabled = !isEditing
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Strike price + Option type
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.formStrikePrice,
                    onValueChange = { viewModel.updateFormStrikePrice(it) },
                    label = { Text("Strike Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = !isEditing,
                    singleLine = true
                )
                Column {
                    Text("Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("CE", "PE").forEach { type ->
                            FilterChip(
                                selected = state.formOptionType == type,
                                onClick = { viewModel.updateFormOptionType(type) },
                                label = { Text(type) },
                                enabled = !isEditing,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (type == "CE") Profit.copy(alpha = 0.2f) else Loss.copy(alpha = 0.2f),
                                    selectedLabelColor = if (type == "CE") Profit else Loss
                                )
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Direction
            Text("Direction", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("buy" to "Buy", "sell" to "Sell").forEach { (value, label) ->
                    FilterChip(
                        selected = state.formDirection == value,
                        onClick = { viewModel.updateFormDirection(value) },
                        label = { Text(label) },
                        enabled = !isEditing,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (value == "buy") Profit.copy(alpha = 0.2f) else Loss.copy(alpha = 0.2f),
                            selectedLabelColor = if (value == "buy") Profit else Loss
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Entry / Exit price
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.formEntryPrice,
                    onValueChange = { viewModel.updateFormEntryPrice(it) },
                    label = { Text("Entry Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    enabled = !isEditing,
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.formExitPrice,
                    onValueChange = { viewModel.updateFormExitPrice(it) },
                    label = { Text("Exit Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Quantity + Lot size
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.formQuantity,
                    onValueChange = { viewModel.updateFormQuantity(it) },
                    label = { Text("Lots") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = !isEditing,
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.formLotSize,
                    onValueChange = { viewModel.updateFormLotSize(it) },
                    label = { Text("Lot Size") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = !isEditing,
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Title
            OutlinedTextField(
                value = state.formTitle,
                onValueChange = { viewModel.updateFormTitle(it) },
                label = { Text("Title (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Notes
            OutlinedTextField(
                value = state.formNotes,
                onValueChange = { viewModel.updateFormNotes(it) },
                label = { Text("Notes (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                maxLines = 4
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Tags
            Text("Tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(JournalTag.entries.toList()) { tag ->
                    FilterChip(
                        selected = state.formSelectedTags.contains(tag),
                        onClick = { viewModel.toggleFormTag(tag) },
                        label = { Text(tag.displayName, fontSize = 12.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Mood
            Text("Mood", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(TradeMood.entries.toList()) { mood ->
                    FilterChip(
                        selected = state.formMood == mood,
                        onClick = {
                            viewModel.updateFormMood(if (state.formMood == mood) null else mood)
                        },
                        label = { Text("${mood.emoji} ${mood.displayName}", fontSize = 12.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Market Condition
            Text("Market Condition", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(MarketCondition.entries.toList()) { condition ->
                    FilterChip(
                        selected = state.formMarketCondition == condition,
                        onClick = {
                            viewModel.updateFormMarketCondition(
                                if (state.formMarketCondition == condition) null else condition
                            )
                        },
                        label = { Text(condition.displayName, fontSize = 12.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Outcome
            Text("Outcome", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TradeOutcome.entries.forEach { outcome ->
                    FilterChip(
                        selected = state.formOutcome == outcome,
                        onClick = {
                            viewModel.updateFormOutcome(
                                if (state.formOutcome == outcome) null else outcome
                            )
                        },
                        label = { Text(outcome.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (outcome) {
                                TradeOutcome.PROFIT -> Profit.copy(alpha = 0.2f)
                                TradeOutcome.LOSS -> Loss.copy(alpha = 0.2f)
                                TradeOutcome.BREAKEVEN -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            },
                            selectedLabelColor = when (outcome) {
                                TradeOutcome.PROFIT -> Profit
                                TradeOutcome.LOSS -> Loss
                                TradeOutcome.BREAKEVEN -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Save button
            Button(
                onClick = { viewModel.saveEntry() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (isEditing) "Update Entry" else "Save Entry",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
