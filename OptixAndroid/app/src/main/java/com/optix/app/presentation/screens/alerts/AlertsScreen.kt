package com.optix.app.presentation.screens.alerts

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.R
import com.optix.app.core.util.Resource
import com.optix.app.domain.model.Alert
import com.optix.app.domain.model.AlertType
import com.optix.app.presentation.theme.*

// ==================== Theme Colors (iOS matching) ====================
private val GradientStart = Color(0xFF667EEA)
private val GradientEnd = Color(0xFF764BA2)

// ==================== IPO Data Classes ====================

enum class IPOStatus(val displayName: String, val color: Color, val icon: ImageVector) {
    OPEN("Open", PrimaryGreen, Icons.Default.PlayArrow),
    UPCOMING("Upcoming", AccentBlue, Icons.Default.Schedule),
    CLOSED("Closed", WarningOrange, Icons.Default.Lock),
    LISTED("Listed", AccentPurple, Icons.Default.CheckCircle),
    ALLOTMENT("Allotment", AccentCyan, Icons.Default.HowToVote)
}

enum class IPOType(val displayName: String, val color: Color) {
    MAINBOARD("Mainboard", AccentBlue),
    SME("SME", AccentPurple)
}

enum class IPOVerdict(val displayName: String, val color: Color, val icon: ImageVector) {
    SUBSCRIBE("Subscribe", PrimaryGreen, Icons.Default.ThumbUp),
    AVOID("Avoid", ErrorRed, Icons.Default.ThumbDown),
    NEUTRAL("Neutral", WarningOrange, Icons.Default.Remove)
}

data class GMPData(
    val gmpValue: Double?,
    val estimatedListingPrice: Double?,
    val listingGainPct: Double?
) {
    val isPositive: Boolean get() = (gmpValue ?: 0.0) >= 0
}

data class IPO(
    val id: String = java.util.UUID.randomUUID().toString(),
    val companyName: String,
    val slug: String = companyName.lowercase().replace(" ", "-"),
    val type: IPOType,
    val status: IPOStatus,
    val priceBandLow: Double?,
    val priceBandHigh: Double?,
    val lotSize: Int,
    val issueSizeCr: String,
    val minInvestment: Double?,
    val exchange: String,
    val openDate: String,
    val closeDate: String,
    val listingDate: String?,
    val gmp: GMPData?,
    val aiVerdict: IPOVerdict? = null,
    val aiAnalysis: String? = null,
    val keyPositives: List<String> = emptyList(),
    val keyRisks: List<String> = emptyList(),
    val sector: String,
    val description: String
) {
    val priceBandDisplay: String
        get() = when {
            priceBandLow != null && priceBandHigh != null && priceBandLow != priceBandHigh ->
                "₹${priceBandLow.toInt()}-${priceBandHigh.toInt()}"
            priceBandHigh != null -> "₹${priceBandHigh.toInt()}"
            priceBandLow != null -> "₹${priceBandLow.toInt()}"
            else -> "TBA"
        }

    val minInvestmentDisplay: String
        get() = minInvestment?.let { "₹${String.format("%,.0f", it)}" } ?: "TBA"

    val dateRangeDisplay: String
        get() = "$openDate - $closeDate"
}

data class IPOState(
    val ipos: List<IPO> = emptyList(),
    val selectedStatus: IPOStatus? = IPOStatus.OPEN,
    val selectedType: IPOType? = null,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val selectedIPO: IPO? = null,
    val showDetailSheet: Boolean = false,
    val analyzingIPO: String? = null
)

// ==================== Main Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    viewModel: AlertsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(1) }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = PrimaryGreen
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_alert), tint = Color.White)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header - iOS: 24pt Bold title, 44x44 refresh button, 12pt corner radius
            IPOHeader(
                selectedTab = selectedTab,
                onRefresh = { if (selectedTab == 1) viewModel.refreshIPOs() else viewModel.refresh() },
                isRefreshing = state.ipoState.isLoading
            )

            // Tab Bar
            IPOTabBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                activeAlertsCount = state.activeCount,
                openIPOCount = state.ipoState.ipos.count { it.status == IPOStatus.OPEN }
            )

            when (selectedTab) {
                0 -> AlertsTabContent(
                    state = state,
                    onRefresh = { viewModel.refresh() },
                    onToggleAlert = { viewModel.toggleAlert(it) },
                    onDeleteAlert = { viewModel.deleteAlert(it) }
                )
                1 -> IPOTabContent(
                    ipoState = state.ipoState,
                    onRefresh = { viewModel.refreshIPOs() },
                    onStatusFilterChange = { viewModel.setIPOFilter(it) },
                    onTypeFilterChange = { viewModel.setIPOTypeFilter(it) },
                    onSearchChange = { viewModel.setIPOSearch(it) },
                    onIPOClick = { viewModel.selectIPO(it) },
                    onAnalyzeIPO = { viewModel.analyzeIPO(it) }
                )
            }
        }
    }

    // IPO Detail Sheet
    if (state.ipoState.showDetailSheet && state.ipoState.selectedIPO != null) {
        IPODetailSheet(
            ipo = state.ipoState.selectedIPO!!,
            onDismiss = { viewModel.dismissIPODetail() },
            onAnalyze = { viewModel.analyzeIPO(it) },
            isAnalyzing = state.ipoState.analyzingIPO == state.ipoState.selectedIPO?.id
        )
    }

    if (showCreateDialog) {
        CreateAlertDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { alert ->
                viewModel.createAlert(alert)
                showCreateDialog = false
            }
        )
    }
}

// ==================== Header (iOS: 24pt Bold, 44x44 refresh, 12pt radius) ====================

@Composable
fun IPOHeader(
    selectedTab: Int,
    onRefresh: () -> Unit,
    isRefreshing: Boolean = false
) {
    val rotation by rememberInfiniteTransition(label = "refresh").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = if (selectedTab == 1) "IPO Dashboard" else "Price Alerts",
                fontSize = 24.sp, // iOS: 24pt
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (selectedTab == 1) "Track upcoming & live IPOs" else "Manage your alerts",
                fontSize = 12.sp, // iOS: 12pt
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp) // iOS: 44x44pt
                .clip(RoundedCornerShape(12.dp)) // iOS: 12pt radius
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onRefresh() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                modifier = Modifier
                    .size(16.dp) // iOS: 16pt
                    .then(if (isRefreshing) Modifier.rotate(rotation) else Modifier),
                tint = PrimaryGreen // iOS: accentGreen
            )
        }
    }
}

@Composable
fun IPOTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    activeAlertsCount: Int,
    openIPOCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabButton(
            title = "IPO",
            icon = if (selectedTab == 1) Icons.Filled.Business else Icons.Outlined.Business,
            isSelected = selectedTab == 1,
            badge = if (openIPOCount > 0) openIPOCount.toString() else null,
            badgeColor = PrimaryGreen,
            onClick = { onTabSelected(1) },
            modifier = Modifier.weight(1f)
        )
        TabButton(
            title = "Alerts",
            icon = if (selectedTab == 0) Icons.Filled.Notifications else Icons.Outlined.Notifications,
            isSelected = selectedTab == 0,
            badge = if (activeAlertsCount > 0) activeAlertsCount.toString() else null,
            badgeColor = AccentOrange,
            onClick = { onTabSelected(0) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TabButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    badge: String?,
    badgeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (isSelected) PrimaryGreen.copy(alpha = 0.15f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, modifier = Modifier.size(20.dp), tint = if (isSelected) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = if (isSelected) PrimaryGreen else MaterialTheme.colorScheme.onSurfaceVariant)
            badge?.let {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = badgeColor) {
                    Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
}

// ==================== IPO Tab Content ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IPOTabContent(
    ipoState: IPOState,
    onRefresh: () -> Unit,
    onStatusFilterChange: (IPOStatus?) -> Unit,
    onTypeFilterChange: (IPOType?) -> Unit,
    onSearchChange: (String) -> Unit,
    onIPOClick: (IPO) -> Unit,
    onAnalyzeIPO: (IPO) -> Unit
) {
    val statusScroll = rememberScrollState()
    val typeScroll = rememberScrollState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            // Search Bar - iOS: 12pt corner radius, 12pt inner H padding, 10pt inner V padding
            OutlinedTextField(
                value = ipoState.searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by company name...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (ipoState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp), // iOS: 12pt
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }

        item {
            // Status Filter Chips - horizontal scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(statusScroll)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IPOFilterChip(
                    title = "All",
                    count = ipoState.ipos.size,
                    isSelected = ipoState.selectedStatus == null,
                    color = PrimaryGreen,
                    onClick = { onStatusFilterChange(null) }
                )
                IPOStatus.entries.forEach { status ->
                    IPOFilterChip(
                        title = status.displayName,
                        count = ipoState.ipos.count { it.status == status },
                        isSelected = ipoState.selectedStatus == status,
                        color = status.color,
                        onClick = { onStatusFilterChange(status) }
                    )
                }
            }
        }

        item {
            // Type Filter - horizontal scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(typeScroll)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IPOTypeFilterButton("All Types", ipoState.selectedType == null, PrimaryGreen) { onTypeFilterChange(null) }
                IPOType.entries.forEach { type ->
                    IPOTypeFilterButton(type.displayName, ipoState.selectedType == type, type.color) { onTypeFilterChange(type) }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        if (ipoState.isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PrimaryGreen, modifier = Modifier.scale(1.2f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading IPOs...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            val filteredIPOs = ipoState.ipos.filter { ipo ->
                (ipoState.selectedStatus == null || ipo.status == ipoState.selectedStatus) &&
                        (ipoState.selectedType == null || ipo.type == ipoState.selectedType) &&
                        (ipoState.searchQuery.isEmpty() || ipo.companyName.contains(ipoState.searchQuery, ignoreCase = true))
            }

            if (filteredIPOs.isEmpty()) {
                item { IPOEmptyState(onRefresh) }
            } else {
                items(filteredIPOs, key = { it.id }) { ipo ->
                    IPOCard(ipo, { onIPOClick(ipo) }, { onAnalyzeIPO(ipo) }, ipoState.analyzingIPO == ipo.id)
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

// ==================== Filter Chip (iOS: 13pt text, 11pt badge, 14/10 padding, 20pt radius) ====================

@Composable
fun IPOFilterChip(
    title: String,
    count: Int,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp)) // iOS: 20pt pill
            .clickable { onClick() }
            .then(
                if (!isSelected) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                else Modifier
            ),
        color = if (isSelected) color else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), // iOS: 14pt H, 10pt V
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 13.sp, // iOS: 13pt
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp), // iOS: 8pt badge radius
                    color = if (isSelected) Color.White.copy(alpha = 0.3f) else color.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 11.sp, // iOS: 11pt
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else color,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp) // iOS: 6pt H, 2pt V
                    )
                }
            }
        }
    }
}

// ==================== Type Filter Button (iOS: 12pt text, 12/8 padding, 8pt radius) ====================

@Composable
fun IPOTypeFilterButton(
    title: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp)) // iOS: 8pt
            .clickable { onClick() }
            .then(
                if (!isSelected) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                else Modifier
            ),
        color = if (isSelected) color else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp, // iOS: 12pt
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp) // iOS: 12pt H, 8pt V
        )
    }
}

// ==================== IPO Card (iOS: 16pt padding, 16pt radius, 1pt border) ====================

@Composable
fun IPOCard(
    ipo: IPO,
    onClick: () -> Unit,
    onAnalyze: () -> Unit,
    isAnalyzing: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)) // iOS: 16pt
            .clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { // iOS: 16pt padding
            // Header: Company Name + GMP
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ipo.companyName,
                        fontSize = 16.sp, // iOS: 16pt
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Type Badge - iOS: 10pt text, 8/4 padding, 6pt radius
                        Surface(shape = RoundedCornerShape(6.dp), color = ipo.type.color.copy(alpha = 0.15f)) {
                            Text(ipo.type.displayName, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = ipo.type.color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        // Status Badge - iOS: 10pt text, 8/4 padding, 6pt radius, solid color
                        Surface(shape = RoundedCornerShape(6.dp), color = ipo.status.color) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(ipo.status.icon, null, modifier = Modifier.size(10.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(ipo.status.displayName, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }

                // GMP Display - iOS: 10pt label, 16pt value, 11pt secondary
                ipo.gmp?.let { gmpData ->
                    Column(horizontalAlignment = Alignment.End) {
                        val gmpColor = if (gmpData.isPositive) PrimaryGreen else ErrorRed
                        Text("GMP", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        gmpData.gmpValue?.let {
                            Text("${if (it >= 0) "+" else ""}₹${it.toInt()}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = gmpColor)
                        }
                        gmpData.listingGainPct?.let {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("${if (it >= 0) "+" else ""}${String.format("%.1f", it)}%", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = gmpColor)
                        }
                    }
                }
            }

            // Divider
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))

            // Details Grid - iOS: 16pt spacing, 10pt title, 13pt value
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IPODetailItem("Price Band", ipo.priceBandDisplay)
                IPODetailItem("Lot Size", "${ipo.lotSize} shares")
                IPODetailItem("Min Invest", ipo.minInvestmentDisplay)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Date & Exchange Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(ipo.dateRangeDisplay, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Exchange Badge - iOS: 11pt, 8/4 padding, 4pt radius
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(ipo.exchange, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI Verdict or Analyze Button
            if (ipo.aiVerdict != null) {
                VerdictBadge(ipo.aiVerdict)
            } else {
                AnimatedAnalyzeButton(onAnalyze, isAnalyzing)
            }
        }
    }
}

// ==================== IPO Detail Item (iOS: 10pt title, 13pt value) ====================

@Composable
fun IPODetailItem(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ==================== Verdict Badge (iOS: 12pt icon/text, 12/6 padding, 8pt radius) ====================

@Composable
fun VerdictBadge(verdict: IPOVerdict) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp), // iOS: 8pt
        color = verdict.color
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), // iOS: 12pt H, 6pt V
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(verdict.icon, null, modifier = Modifier.size(12.dp), tint = Color.White)
            Spacer(modifier = Modifier.width(6.dp))
            Text("AI: ${verdict.displayName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ==================== Animated Analyze Button (iOS: 40pt height, 10pt radius, gradient #667EEA to #764BA2) ====================

@Composable
fun AnimatedAnalyzeButton(onClick: () -> Unit, isLoading: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "analyze")

    // Shimmer effect - iOS: 2s linear
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "shimmer"
    )

    // Sparkle rotation - iOS: 2s ease-in-out, 15 degrees
    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = -15f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sparkleRotation"
    )

    // Sparkle scale - iOS: 1s ease-in-out, up to 1.15
    val sparkleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sparkleScale"
    )

    // Pulse glow - iOS: 1.5s ease-in-out
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp) // iOS: 40pt
            .scale(if (!isLoading) pulseScale else 1f)
            .clip(RoundedCornerShape(10.dp)) // iOS: 10pt
            .background(Brush.horizontalGradient(listOf(GradientStart, GradientEnd)))
            .clickable(enabled = !isLoading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp).scale(0.8f), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyzing...", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            } else {
                Icon(
                    Icons.Default.AutoAwesome, null,
                    modifier = Modifier.size(14.dp).rotate(sparkleRotation).scale(sparkleScale),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get AI Analysis", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ==================== IPO Detail Sheet (iOS: 20pt spacing, 30pt bottom padding) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IPODetailSheet(ipo: IPO, onDismiss: () -> Unit, onAnalyze: (IPO) -> Unit, isAnalyzing: Boolean) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp) // iOS: 20pt
        ) {
            // Header - iOS: 22pt Bold
            item {
                Column {
                    Text(ipo.companyName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(6.dp), color = ipo.type.color.copy(alpha = 0.15f)) {
                            Text(ipo.type.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ipo.type.color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = ipo.status.color) {
                            Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(ipo.status.icon, null, modifier = Modifier.size(12.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(ipo.status.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // GMP Card
            ipo.gmp?.let { item { GMPCard(it, ipo.priceBandHigh) } }

            // Details Card
            item { IPODetailsCard(ipo) }

            // AI Analysis
            item {
                if (ipo.aiVerdict != null) AIAnalysisCard(ipo)
                else AnimatedAnalyzeButton({ onAnalyze(ipo) }, isAnalyzing)
            }

            // Sector & Description
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(ipo.sector, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentPurple)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(ipo.description, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(30.dp)) } // iOS: 30pt bottom padding
        }
    }
}

// ==================== GMP Card (iOS: 16pt padding, 16pt radius, 2pt border, 24pt values) ====================

@Composable
fun GMPCard(gmpData: GMPData, priceBandHigh: Double?) {
    val borderColor = if (gmpData.isPositive) PrimaryGreen else ErrorRed

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)), // iOS: 2pt border, 0.3 opacity
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Grey Market Premium", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                // GMP Value
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GMP", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        gmpData.gmpValue?.let { "${if (it >= 0) "+" else ""}₹${it.toInt()}" } ?: "-",
                        fontSize = 24.sp, // iOS: 24pt
                        fontWeight = FontWeight.Bold,
                        color = borderColor
                    )
                }

                // Divider - iOS: 1pt width, 40pt height
                Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))

                // Expected Listing
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Est. Listing", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    val estPrice = gmpData.estimatedListingPrice ?: (priceBandHigh?.plus(gmpData.gmpValue ?: 0.0))
                    Text(estPrice?.let { "₹${it.toInt()}" } ?: "-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = borderColor)
                }

                Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))

                // Listing Gain
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Listing Gain", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        gmpData.listingGainPct?.let { "${if (it >= 0) "+" else ""}${String.format("%.1f", it)}%" } ?: "-",
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = borderColor
                    )
                }
            }
        }
    }
}

// ==================== IPO Details Card (iOS: 16pt padding, 16pt radius, 14pt title, 13pt values) ====================

@Composable
fun IPODetailsCard(ipo: IPO) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("IPO Details", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            DetailRow("Price Band", ipo.priceBandDisplay)
            DetailRow("Lot Size", "${ipo.lotSize} shares")
            DetailRow("Min Investment", ipo.minInvestmentDisplay)
            DetailRow("Issue Size", ipo.issueSizeCr)
            DetailRow("Open Date", ipo.openDate)
            DetailRow("Close Date", ipo.closeDate)
            ipo.listingDate?.let { DetailRow("Listing Date", it) }
            DetailRow("Exchange", ipo.exchange)
        }
    }
}

// ==================== Detail Row (iOS: 13pt text, 12pt spacing) ====================

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ==================== AI Analysis Card (iOS: 16pt padding, 14pt titles, 13pt text) ====================

@Composable
fun AIAnalysisCard(ipo: IPO) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp), tint = AccentBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Analysis", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ipo.aiVerdict?.let { verdict ->
                    Surface(shape = RoundedCornerShape(8.dp), color = verdict.color) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(verdict.icon, null, modifier = Modifier.size(12.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(verdict.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Summary
            ipo.aiAnalysis?.let {
                Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Key Positives - iOS: 12pt title, 13pt text, 6pt bullet
            if (ipo.keyPositives.isNotEmpty()) {
                Text("Key Positives", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryGreen)
                Spacer(modifier = Modifier.height(8.dp))
                ipo.keyPositives.forEach { positive ->
                    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(PrimaryGreen))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(positive, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Key Risks
            if (ipo.keyRisks.isNotEmpty()) {
                Text("Key Risks", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ErrorRed)
                Spacer(modifier = Modifier.height(8.dp))
                ipo.keyRisks.forEach { risk ->
                    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(ErrorRed))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(risk, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Disclaimer - iOS: 10pt, 8pt top padding
            Spacer(modifier = Modifier.height(8.dp))
            Text("Disclaimer: AI analysis is for informational purposes only.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}

// ==================== Empty State (iOS: 48pt icon, 16pt title, 14pt message, 60pt V padding) ====================

@Composable
fun IPOEmptyState(onRefresh: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Default.Search, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            Text("No IPOs Found", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Try changing your filters", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRefresh,
                shape = RoundedCornerShape(12.dp), // iOS: 12pt
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp) // iOS: 24/12
            ) {
                Text("Refresh", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ==================== Alerts Tab Content ====================

@Composable
fun AlertsTabContent(state: AlertsState, onRefresh: () -> Unit, onToggleAlert: (String) -> Unit, onDeleteAlert: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusBadge(stringResource(R.string.active), state.activeCount, SuccessGreen)
            StatusBadge(stringResource(R.string.triggered), state.triggeredCount, WarningOrange)
        }

        when (val alertsState = state.alertsState) {
            is Resource.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PrimaryGreen) }
            is Resource.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(alertsState.message ?: stringResource(R.string.error), color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)) { Text(stringResource(R.string.retry)) }
                }
            }
            is Resource.Success -> {
                val alerts = alertsState.data ?: emptyList()
                if (alerts.isEmpty()) EmptyAlertsView()
                else LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(alerts) { alert -> AlertCard(alert, { onToggleAlert(alert.id) }, { onDeleteAlert(alert.id) }) }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(label: String, count: Int, color: Color) {
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.15f)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$count", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, color = color)
        }
    }
}

@Composable
fun EmptyAlertsView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.NotificationsNone, null, modifier = Modifier.size(48.dp), tint = AccentOrange)
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.no_alerts), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.no_alerts_desc), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun AlertCard(alert: Alert, onToggle: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AlertTypeIcon(alert.alertType)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(alert.displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("${alert.symbol} ${alert.condition.displayName} ₹${alert.targetValue}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                alert.note?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = alert.isActive, onCheckedChange = { onToggle() }, colors = SwitchDefaults.colors(checkedTrackColor = SuccessGreen))
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = ErrorRed.copy(alpha = 0.7f)) }
            }
        }
    }
}

@Composable
fun AlertTypeIcon(type: AlertType) {
    val icon = when (type) {
        AlertType.SPOT_PRICE -> Icons.Default.ShowChart
        AlertType.OPTION_PREMIUM -> Icons.Default.TrendingUp
        AlertType.PCR -> Icons.Default.Analytics
        AlertType.OI_CHANGE -> Icons.Default.BarChart
        AlertType.IV -> Icons.Default.Speed
    }
    Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(PrimaryGreen.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = PrimaryGreen)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAlertDialog(onDismiss: () -> Unit, onCreate: (Alert) -> Unit) {
    var alertName by remember { mutableStateOf("") }
    var symbol by remember { mutableStateOf("NIFTY 50") }
    var targetValue by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AlertType.SPOT_PRICE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_alert), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(alertName, { alertName = it }, label = { Text(stringResource(R.string.alert_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(symbol, { symbol = it }, label = { Text("Symbol") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(targetValue, { targetValue = it }, label = { Text(stringResource(R.string.target_value)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = targetValue.toDoubleOrNull() ?: return@Button
                    onCreate(Alert(id = "", userId = "", alertType = selectedType, symbol = symbol, condition = com.optix.app.domain.model.AlertCondition.ABOVE, targetValue = target, name = alertName.ifEmpty { null }, isActive = true))
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) { Text(stringResource(R.string.create_alert)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
