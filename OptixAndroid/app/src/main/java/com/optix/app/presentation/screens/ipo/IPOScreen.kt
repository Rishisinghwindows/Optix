package com.optix.app.presentation.screens.ipo

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.presentation.theme.*

// AI Gradient colors
private val AIPurple = Color(0xFF8B5CF6)
private val AIPink = Color(0xFF06B6D4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IPOScreen(
    viewModel: IPOViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = IPO, 1 = Alerts
    val statusScroll = rememberScrollState()
    val typeScroll = rememberScrollState()

    // Count IPOs by status
    val openCount = state.ipos.count { it.status == IPOStatus.OPEN }
    val upcomingCount = state.ipos.count { it.status == IPOStatus.UPCOMING }
    val closedCount = state.ipos.count { it.status == IPOStatus.CLOSED }
    val listedCount = state.ipos.count { it.status == IPOStatus.LISTED }
    val allCount = state.ipos.size

    // Calculate average GMP for open IPOs
    val avgGmp = state.ipos
        .filter { it.status == IPOStatus.OPEN && it.gmp != null }
        .mapNotNull { it.gmp }
        .takeIf { it.isNotEmpty() }
        ?.average()?.toInt() ?: 0

    // Calculate total subscription value
    val totalSubscription = state.ipos
        .filter { it.status == IPOStatus.OPEN }
        .mapNotNull { it.subscription }
        .takeIf { it.isNotEmpty() }
        ?.average() ?: 0.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            // Hero Header with gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF059669),
                                Color(0xFF10B981),
                                Color(0xFF34D399).copy(alpha = 0.8f)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 0.dp, bottom = 12.dp)
                        .offset(y = (-8).dp)
                ) {
                    // Top Row: Title + Refresh
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "IPO Dashboard",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Track upcoming & live IPOs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        // Refresh Button
                        Surface(
                            onClick = { viewModel.refresh() },
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stats Cards Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Open IPOs Card
                        IPOStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.PlayArrow,
                            value = "$openCount",
                            label = "Open",
                            iconColor = Color(0xFF22C55E)
                        )

                        // Upcoming Card
                        IPOStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Schedule,
                            value = "$upcomingCount",
                            label = "Upcoming",
                            iconColor = Color(0xFF3B82F6)
                        )

                        // Avg GMP Card
                        IPOStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.TrendingUp,
                            value = if (avgGmp > 0) "+₹$avgGmp" else "--",
                            label = "Avg GMP",
                            iconColor = if (avgGmp > 0) Color(0xFF22C55E) else Color(0xFF9CA3AF)
                        )
                    }
                }
            }
        }

        item {
            // Tab Buttons (IPO / Alerts)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // IPO Tab
                Surface(
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedTab == 0) PrimaryGreen else Color(0xFFF5F5F5)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = if (selectedTab == 0) Color.White else Color(0xFF666666),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "IPO",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedTab == 0) Color.White else Color(0xFF666666)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedTab == 0) Color.White.copy(alpha = 0.25f) else PrimaryGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$openCount",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) Color.White else PrimaryGreen
                            )
                        }
                    }
                }

                // Alerts Tab
                Surface(
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedTab == 1) PrimaryGreen else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = if (selectedTab == 1) Color.White else Color(0xFF666666),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Alerts",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedTab == 1) Color.White else Color(0xFF666666)
                        )
                    }
                }
            }
        }

        item {
            // Search Bar (kept on top)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFE5E5E5))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = Color.Black,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(PrimaryGreen),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search by company name...",
                                    color = Color(0xFF999999),
                                    fontSize = 15.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }

        item {
            // Status Filter Chips with counts (scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(statusScroll)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IPOFilterChip(
                    label = "All",
                    count = allCount,
                    isSelected = state.selectedFilter == null,
                    onClick = { viewModel.setFilter(null) }
                )
                IPOFilterChip(
                    label = "Open",
                    count = openCount,
                    isSelected = state.selectedFilter == IPOStatus.OPEN,
                    onClick = { viewModel.setFilter(IPOStatus.OPEN) },
                    selectedColor = PrimaryGreen
                )
                IPOFilterChip(
                    label = "Upcoming",
                    count = upcomingCount,
                    isSelected = state.selectedFilter == IPOStatus.UPCOMING,
                    onClick = { viewModel.setFilter(IPOStatus.UPCOMING) },
                    selectedColor = AccentBlue
                )
                IPOFilterChip(
                    label = "Closed",
                    count = closedCount,
                    isSelected = state.selectedFilter == IPOStatus.CLOSED,
                    onClick = { viewModel.setFilter(IPOStatus.CLOSED) },
                    selectedColor = WarningOrange
                )
                IPOFilterChip(
                    label = "Listed",
                    count = listedCount,
                    isSelected = state.selectedFilter == IPOStatus.LISTED,
                    onClick = { viewModel.setFilter(IPOStatus.LISTED) },
                    selectedColor = AccentPurple
                )
            }
        }

        item {
            // Type Filter Chips (scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(typeScroll)
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IPOTypeChip(
                    label = "All Types",
                    isSelected = state.selectedType == null,
                    onClick = { viewModel.setTypeFilter(null) }
                )
                IPOTypeChip(
                    label = "Mainboard",
                    isSelected = state.selectedType == IPOType.MAINBOARD,
                    onClick = { viewModel.setTypeFilter(IPOType.MAINBOARD) }
                )
                IPOTypeChip(
                    label = "SME",
                    isSelected = state.selectedType == IPOType.SME,
                    onClick = { viewModel.setTypeFilter(IPOType.SME) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(2.dp)) }

        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryGreen, strokeWidth = 3.dp)
                }
            }
        } else {
            val filteredIPOs = viewModel.getFilteredIPOs().filter {
                searchQuery.isEmpty() || it.companyName.contains(searchQuery, ignoreCase = true)
            }

            if (filteredIPOs.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color(0xFFCCCCCC)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No IPOs found",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF666666)
                        )
                        Text(
                            "Try adjusting your filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF999999)
                        )
                    }
                }
            } else {
                items(filteredIPOs) { ipo ->
                    Box(modifier = Modifier.padding(vertical = 6.dp)) {
                        IPOCardNew(ipo = ipo)
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
fun IPOFilterChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = PrimaryGreen
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) selectedColor else Color.White,
        border = if (!isSelected) BorderStroke(1.dp, Color(0xFFE5E5E5)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color.Black
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) Color.White.copy(alpha = 0.25f) else selectedColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "$count",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else selectedColor
                )
            }
        }
    }
}

@Composable
fun IPOTypeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) PrimaryGreen else Color.White,
        border = if (!isSelected) BorderStroke(1.dp, Color(0xFFE5E5E5)) else null
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) Color.White else Color.Black
        )
    }
}

@Composable
fun IPOCardNew(ipo: IPO) {
    val statusColor = when (ipo.status) {
        IPOStatus.OPEN -> PrimaryGreen
        IPOStatus.UPCOMING -> AccentBlue
        IPOStatus.CLOSED -> WarningOrange
        IPOStatus.LISTED -> AccentPurple
    }

    val gmpColor = if ((ipo.gmp ?: 0) >= 0) PrimaryGreen else ErrorRed
    val gmpPercent = ipo.gmp?.let {
        val minPrice = ipo.priceRange.split("-").firstOrNull()?.trim()?.toDoubleOrNull() ?: 100.0
        if (minPrice > 0) (it / minPrice * 100) else 0.0
    } ?: 0.0

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, Color(0xFFE5E5EA))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Company Name and Tags
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ipo.companyName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Type Badge (Mainboard/SME)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PrimaryGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = ipo.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryGreen
                            )
                        }

                        // Status Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusColor
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (ipo.status == IPOStatus.OPEN) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                                Text(
                                    text = ipo.status.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // GMP Section
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "GMP",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF999999)
                    )
                    ipo.gmp?.let { gmp ->
                        Text(
                            text = "${if (gmp >= 0) "+" else ""}₹$gmp",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = gmpColor
                        )
                        Text(
                            text = "${if (gmpPercent >= 0) "+" else ""}${String.format("%.1f", gmpPercent)}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = gmpColor
                        )
                    } ?: Text(
                        text = "--",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF999999)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Price Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IPOInfoItem(
                    label = "Price Band",
                    value = "₹${ipo.priceRange}"
                )
                IPOInfoItem(
                    label = "Lot Size",
                    value = "${ipo.lotSize} shares"
                )
                IPOInfoItem(
                    label = "Min Invest",
                    value = "₹${String.format("%,d", (ipo.lotSize * (ipo.priceRange.split("-").lastOrNull()?.trim()?.toDoubleOrNull() ?: 0.0)).toInt())}"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date Row
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${ipo.openDate} - ${ipo.closeDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Exchange Badge
                Text(
                    text = "NSE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF999999)
                )
            }

            // Subscription info (if available)
            ipo.subscription?.let { sub ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = AccentBlue.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Subscription",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF666666)
                        )
                        Text(
                            text = "${String.format("%.2f", sub)}x",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = AccentBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Get AI Analysis Button
            Surface(
                onClick = { /* Navigate to AI analysis */ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(AIPurple, AIPink)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Get AI Analysis",
                            style = MaterialTheme.typography.labelLarge,
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
fun IPOInfoItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF999999)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
    }
}

// Keep existing data classes for compatibility
@Composable
fun IPOCard(ipo: IPO) {
    IPOCardNew(ipo = ipo)
}

@Composable
fun InfoColumn(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF999999))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun DateColumn(label: String, date: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF999999))
        Text(date, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun IPOStatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    iconColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = Color.White,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
