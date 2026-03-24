package com.optix.app.presentation.screens.notifications

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.data.local.MarketSignal
import com.optix.app.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.markAllAsRead() }) {
                        Icon(Icons.Default.DoneAll, "Mark all read",
                            tint = AccentBlue)
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.DeleteSweep, "Clear all",
                            tint = ErrorRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Stats bar
            StatsBar(state)

            // Filter chips
            FilterChips(state.selectedFilter) { viewModel.setFilter(it) }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // Content
            val filtered = viewModel.filteredSignals
            if (state.signals.isEmpty()) {
                EmptyState()
            } else if (filtered.isEmpty()) {
                FilteredEmptyState { viewModel.setFilter(SignalFilter.ALL) }
            } else {
                SignalList(
                    signals = filtered,
                    onTap = { viewModel.markAsRead(it.id) },
                    onDelete = { viewModel.deleteSignal(it.id) }
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all notifications?") },
            text = { Text("This will remove all ${state.signals.size} signals. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) {
                    Text("Clear All", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatsBar(state: NotificationsState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatPill("Total", "${state.signals.size}", AccentBlue)
        StatPill("New", "${state.unreadCount}", AccentOrange)
        StatPill("Bullish", "${state.bullishCount}", PrimaryGreen)
        StatPill("Bearish", "${state.bearishCount}", ErrorRed)
    }
}

@Composable
private fun StatPill(label: String, value: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun FilterChips(selected: SignalFilter, onSelect: (SignalFilter) -> Unit) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val filters = listOf(
            SignalFilter.ALL to "All",
            SignalFilter.UNREAD to "Unread",
            SignalFilter.BULLISH to "Bullish",
            SignalFilter.BEARISH to "Bearish",
            SignalFilter.VOLATILITY to "VIX / OI",
        )
        items(filters) { (filter, label) ->
            val isSelected = selected == filter
            val color = when (filter) {
                SignalFilter.ALL -> AccentBlue
                SignalFilter.UNREAD -> AccentOrange
                SignalFilter.BULLISH -> PrimaryGreen
                SignalFilter.BEARISH -> ErrorRed
                SignalFilter.VOLATILITY -> AccentPurple
            }
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color,
                    selectedLabelColor = Color.White,
                )
            )
        }
    }
}

@Composable
private fun SignalList(
    signals: List<MarketSignal>,
    onTap: (MarketSignal) -> Unit,
    onDelete: (MarketSignal) -> Unit,
) {
    var expandedId by remember { mutableStateOf<String?>(null) }

    // Group by time
    val grouped = groupSignalsByTime(signals)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        grouped.forEach { (groupLabel, groupSignals) ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(groupLabel.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text("${groupSignals.size}", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }

            item {
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                    )
                ) {
                    Column {
                        groupSignals.forEachIndexed { index, signal ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 62.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                                )
                            }
                            SignalCard(
                                signal = signal,
                                isExpanded = expandedId == signal.id,
                                onTap = {
                                    expandedId = if (expandedId == signal.id) null else signal.id
                                    if (!signal.isRead) onTap(signal)
                                },
                                onDelete = { onDelete(signal) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalCard(
    signal: MarketSignal,
    isExpanded: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit
) {
    val signalColor = when {
        signal.isBullish -> PrimaryGreen
        signal.isBearish -> ErrorRed
        signal.signalType == "pcr_shift" -> AccentBlue
        signal.signalType == "vix_change" -> AccentOrange
        signal.signalType == "oi_surge" -> AccentPurple
        signal.signalType == "max_pain_shift" -> AccentCyan
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }

    val signalIcon = when (signal.signalType) {
        "long_buildup" -> Icons.Default.TrendingUp
        "short_buildup" -> Icons.Default.TrendingDown
        "long_unwinding" -> Icons.Default.SouthEast
        "short_covering" -> Icons.Default.NorthEast
        "pcr_shift" -> Icons.Default.SwapHoriz
        "vix_change" -> Icons.Default.FlashOn
        "oi_surge" -> Icons.Default.LocalFireDepartment
        "max_pain_shift" -> Icons.Default.GpsFixed
        else -> Icons.Default.BarChart
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!signal.isRead) Modifier.background(signalColor.copy(alpha = 0.03f))
                else Modifier
            )
            .clickable(onClick = onTap)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon with unread dot
            Box {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(signalColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(signalIcon, null, tint = signalColor, modifier = Modifier.size(20.dp))
                }
                if (!signal.isRead) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(AccentOrange)
                            .align(Alignment.TopStart)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                // Badges row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sentiment badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = signalColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            signal.sentiment.uppercase(),
                            fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = signalColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Index badge
                    if (signal.index.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentBlue.copy(alpha = 0.1f)
                        ) {
                            Text(
                                shortIndex(signal.index).uppercase(),
                                fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = AccentBlue,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        timeAgo(signal.timestamp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    signal.title,
                    fontSize = 14.sp,
                    fontWeight = if (signal.isRead) FontWeight.Medium else FontWeight.Bold,
                    color = if (signal.isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    signal.body,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
        }

        // Expanded content
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .padding(start = 66.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                )

                // Timestamp
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text(fullTimestamp(signal.timestamp), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }

                // Signal type
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Label, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Text(signal.signalTypeLabel, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }

                // Delete button
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ErrorRed.copy(alpha = 0.1f),
                    modifier = Modifier.clickable(onClick = onDelete)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(14.dp),
                            tint = ErrorRed)
                        Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = ErrorRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.NotificationsOff, null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
            Text("No Market Signals", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "PCR shifts, OI buildups, VIX spikes\nwill appear here during market hours",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text("Signals checked every 60 seconds", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun FilteredEmptyState(onShowAll: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.FilterList, null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            Text("No matching signals", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Try a different filter", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            TextButton(onClick = onShowAll) {
                Text("Show All", color = AccentBlue)
            }
        }
    }
}

// Helper functions

private fun shortIndex(name: String): String = when (name) {
    "NIFTY 50" -> "Nifty"
    "NIFTY BANK" -> "BankNifty"
    else -> name
}

private fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun fullTimestamp(timestamp: Long): String =
    SimpleDateFormat("dd MMM yyyy, h:mm:ss a", Locale.getDefault()).format(Date(timestamp))

private fun groupSignalsByTime(signals: List<MarketSignal>): List<Pair<String, List<MarketSignal>>> {
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()
    val todayStart = calendar.apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 86400000L

    val grouped = signals.groupBy { signal ->
        val age = now - signal.timestamp
        val minutes = TimeUnit.MILLISECONDS.toMinutes(age)
        when {
            signal.timestamp >= todayStart -> when {
                minutes < 1 -> "Just Now"
                minutes < 5 -> "Last 5 Minutes"
                minutes < 15 -> "Last 15 Minutes"
                minutes < 60 -> "Last Hour"
                else -> "Earlier Today"
            }
            signal.timestamp >= yesterdayStart -> "Yesterday"
            else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(signal.timestamp))
        }
    }

    val order = listOf("Just Now", "Last 5 Minutes", "Last 15 Minutes", "Last Hour", "Earlier Today", "Yesterday")
    return grouped.entries.sortedWith(compareBy {
        val idx = order.indexOf(it.key)
        if (idx >= 0) idx else Int.MAX_VALUE
    }).map { it.key to it.value }
}
