package com.optix.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.R
import com.optix.app.core.util.AnalyticsHelper

// iOS-like Colors
private val AccentBlue = Color(0xFF007AFF)
private val AccentGreen = Color(0xFF34C759)
private val AccentOrange = Color(0xFFFF9500)
private val AccentPurple = Color(0xFFAF52DE)
private val AccentCyan = Color(0xFF32ADE6)
// Note: TextMuted, CardBackground, BackgroundColor, and BorderColor replaced by
// MaterialTheme.colorScheme equivalents throughout.

// Gradients
private val PurpleGradient = Brush.linearGradient(listOf(AccentPurple, Color(0xFF8B5CF6)))
private val BlueGradient = Brush.linearGradient(listOf(AccentBlue, Color(0xFF0A84FF)))
private val GreenGradient = Brush.linearGradient(listOf(AccentGreen, Color(0xFF30D158)))
private val OrangeGradient = Brush.linearGradient(listOf(AccentOrange, Color(0xFFFF9F0A)))

// Trading indices
private data class TradingIndexOption(
    val id: String,
    val displayName: String,
    val icon: ImageVector,
    val color: Color
)

private val tradingIndices = listOf(
    TradingIndexOption("NIFTY", "NIFTY 50", Icons.Default.ShowChart, AccentBlue),
    TradingIndexOption("BANKNIFTY", "Bank NIFTY", Icons.Default.AccountBalance, AccentGreen),
    TradingIndexOption("FINNIFTY", "FIN NIFTY", Icons.Default.TrendingUp, AccentOrange),
    TradingIndexOption("MIDCPNIFTY", "MIDCAP NIFTY", Icons.Default.BarChart, AccentPurple)
)

// Languages
private data class LanguageOption(
    val id: String,
    val displayName: String,
    val englishName: String
)

private val languages = listOf(
    LanguageOption("en", "English", "English"),
    LanguageOption("hi", "हिंदी", "Hindi"),
    LanguageOption("kn", "ಕನ್ನಡ", "Kannada"),
    LanguageOption("ta", "தமிழ்", "Tamil"),
    LanguageOption("te", "తెలుగు", "Telugu"),
    LanguageOption("bn", "বাংলা", "Bengali"),
    LanguageOption("ml", "മലയാളം", "Malayalam"),
    LanguageOption("pa", "ਪੰਜਾਬੀ", "Punjabi"),
    LanguageOption("or", "ଓଡ଼ିଆ", "Odia")
)

// Appearance modes
private data class AppearanceMode(
    val id: String,
    val displayName: String,
    val icon: ImageVector
)

private val appearanceModes = listOf(
    AppearanceMode("system", "System", Icons.Default.SettingsBrightness),
    AppearanceMode("light", "Light", Icons.Default.LightMode),
    AppearanceMode("dark", "Dark", Icons.Default.DarkMode)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToCalculator: () -> Unit,
    onNavigateToOIAnalysis: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToIPO: () -> Unit = {},
    onNavigateToEducation: () -> Unit = {},
    onNavigateToCharts: () -> Unit = {},
    onNavigateToSimulator: () -> Unit = {},
    onNavigateToJournal: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    // Track screen view once on composition
    LaunchedEffect(Unit) { AnalyticsHelper.logScreenView("settings") }

    val state by viewModel.state.collectAsState()

    // Settings values from ViewModel (persisted via DataStore)
    val spotPriceInterval = state.spotPriceInterval
    val optionChainInterval = state.optionChainInterval
    val defaultIndex = state.defaultIndex
    val hapticEnabled = state.hapticEnabled

    // Derive selected language ID from ViewModel's stored language name
    val selectedLanguage = remember(state.currentLanguage) {
        languages.find { it.displayName == state.currentLanguage }?.id ?: "en"
    }

    // Sync appearance state with ViewModel
    val selectedAppearance = remember(state.currentTheme) {
        when (state.currentTheme) {
            "Light" -> "light"
            "Dark" -> "dark"
            else -> "system"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 0.dp)
    ) {
        // Header
        item {
            SettingsHeader()
        }

        // Data Refresh Section
        item { SectionHeader("DATA REFRESH") }
        item {
            DataRefreshSection(
                spotPriceInterval = spotPriceInterval,
                optionChainInterval = optionChainInterval,
                onSpotPriceChange = { viewModel.setSpotPriceInterval(it) },
                onOptionChainChange = { viewModel.setOptionChainInterval(it) }
            )
        }

        // Default Index Section
        item { SectionHeader("DEFAULT INDEX") }
        item {
            DefaultIndexSection(
                selectedIndex = defaultIndex,
                onIndexSelect = { viewModel.setDefaultIndex(it) }
            )
        }

        // Preferences Section
        item { SectionHeader("PREFERENCES") }
        item {
            PreferencesSection(
                hapticEnabled = hapticEnabled,
                onHapticChange = { viewModel.setHapticEnabled(it) }
            )
        }

        // Education Section
        item { SectionHeader("LEARN") }
        item {
            EducationSection(
                onEducationClick = onNavigateToEducation,
                onQuizClick = { /* Quiz */ }
            )
        }

        // Tools Section
        item { SectionHeader("TOOLS & FEATURES") }
        item {
            ToolsSection(
                onCalculatorClick = onNavigateToCalculator,
                onOIAnalysisClick = onNavigateToOIAnalysis,
                onChatClick = onNavigateToChat,
                onIPOClick = onNavigateToIPO,
                onChartsClick = onNavigateToCharts,
                onSimulatorClick = onNavigateToSimulator,
                onJournalClick = onNavigateToJournal
            )
        }

        // Language Section
        item { SectionHeader("LANGUAGE") }
        item {
            LanguageSection(
                selectedLanguage = selectedLanguage,
                onLanguageSelect = {
                        viewModel.setLanguage(languages.find { l -> l.id == it }?.displayName ?: "English")
                }
            )
        }

        // Appearance Section
        item { SectionHeader("APPEARANCE") }
        item {
            AppearanceSection(
                selectedAppearance = selectedAppearance,
                onAppearanceSelect = { modeId ->
                    val themeName = appearanceModes.find { it.id == modeId }?.displayName ?: "System"
                    viewModel.setTheme(themeName)
                }
            )
        }

        // About Section
        item { SectionHeader("ABOUT") }
        item {
            AboutSection()
        }

        // Disclaimer Section
        item { SectionHeader("DISCLAIMER") }
        item {
            DisclaimerSection()
        }

        // Bottom padding
        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

@Composable
private fun SettingsHeader() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        color = Color.Transparent
    ) {
        Text(
            text = "Settings",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        thickness = 0.5.dp
    )
}

// ====================== DATA REFRESH SECTION ======================

@Composable
private fun DataRefreshSection(
    spotPriceInterval: Float,
    optionChainInterval: Float,
    onSpotPriceChange: (Float) -> Unit,
    onOptionChainChange: (Float) -> Unit
) {
    SettingsCard {
        // Spot Price Interval
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Spot Price Interval",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${spotPriceInterval.toInt()}s",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AccentBlue
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = spotPriceInterval,
                onValueChange = onSpotPriceChange,
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue
                )
            )
        }

        SettingsDivider()

        // Option Chain Interval
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Option Chain Refresh",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${optionChainInterval.toInt()}s",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = AccentBlue
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = optionChainInterval,
                onValueChange = onOptionChainChange,
                valueRange = 3f..30f,
                steps = 26,
                colors = SliderDefaults.colors(
                    thumbColor = AccentBlue,
                    activeTrackColor = AccentBlue
                )
            )
        }
    }
}

// ====================== DEFAULT INDEX SECTION ======================

@Composable
private fun DefaultIndexSection(
    selectedIndex: String,
    onIndexSelect: (String) -> Unit
) {
    SettingsCard {
        tradingIndices.forEachIndexed { index, tradingIndex ->
            if (index > 0) SettingsDivider()

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onIndexSelect(tradingIndex.id) },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = tradingIndex.icon,
                        contentDescription = null,
                        tint = tradingIndex.color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        tradingIndex.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedIndex == tradingIndex.id) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = tradingIndex.color,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ====================== PREFERENCES SECTION ======================

@Composable
private fun PreferencesSection(
    hapticEnabled: Boolean,
    onHapticChange: (Boolean) -> Unit
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Vibration,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Haptic Feedback",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = hapticEnabled,
                onCheckedChange = onHapticChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentGreen
                )
            )
        }
    }
}

// ====================== EDUCATION SECTION ======================

@Composable
private fun EducationSection(
    onEducationClick: () -> Unit,
    onQuizClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Learn Options
        FeatureCard(
            title = "Learn Options",
            subtitle = "Master options trading basics",
            icon = Icons.Default.MenuBook,
            gradient = PurpleGradient,
            onClick = onEducationClick
        )

        // Quiz
        FeatureCard(
            title = "Test Your Knowledge",
            subtitle = "Take quizzes to test your skills",
            icon = Icons.Default.Quiz,
            gradient = OrangeGradient,
            onClick = onQuizClick
        )
    }
}

// ====================== TOOLS SECTION ======================

private val CyanGradient = Brush.linearGradient(listOf(AccentCyan, Color(0xFF5AC8FA)))

@Composable
private fun ToolsSection(
    onCalculatorClick: () -> Unit,
    onOIAnalysisClick: () -> Unit,
    onChatClick: () -> Unit,
    onIPOClick: () -> Unit,
    onChartsClick: () -> Unit,
    onSimulatorClick: () -> Unit = {},
    onJournalClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FeatureCard(
            title = "Index Charts",
            subtitle = "Professional candlestick charts",
            icon = Icons.Default.ShowChart,
            gradient = CyanGradient,
            onClick = onChartsClick
        )

        FeatureCard(
            title = "Option Calculator",
            subtitle = "Black-Scholes pricing model",
            icon = Icons.Default.Calculate,
            gradient = BlueGradient,
            onClick = onCalculatorClick
        )

        FeatureCard(
            title = "P&L Simulator",
            subtitle = "Simulate option payoff scenarios",
            icon = Icons.Default.TrendingUp,
            gradient = OrangeGradient,
            onClick = onSimulatorClick
        )

        FeatureCard(
            title = "OI Analysis",
            subtitle = "Open Interest heatmap & zones",
            icon = Icons.Default.Analytics,
            gradient = GreenGradient,
            onClick = onOIAnalysisClick
        )

        FeatureCard(
            title = "AI Chat",
            subtitle = "Ask Optixia about trading",
            icon = Icons.Default.AutoAwesome,
            gradient = PurpleGradient,
            onClick = onChatClick
        )

        FeatureCard(
            title = "Trade Journal",
            subtitle = "Record & review your trades",
            icon = Icons.Default.MenuBook,
            gradient = BlueGradient,
            onClick = onJournalClick
        )

        FeatureCard(
            title = "IPO Dashboard",
            subtitle = "Track upcoming & live IPOs",
            icon = Icons.Default.Rocket,
            gradient = OrangeGradient,
            onClick = onIPOClick
        )
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ====================== LANGUAGE SECTION ======================

@Composable
private fun LanguageSection(
    selectedLanguage: String,
    onLanguageSelect: (String) -> Unit
) {
    SettingsCard {
        languages.forEachIndexed { index, language ->
            if (index > 0) SettingsDivider()

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLanguageSelect(language.id) },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            language.displayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            language.englishName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectedLanguage == language.id) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ====================== APPEARANCE SECTION ======================

@Composable
private fun AppearanceSection(
    selectedAppearance: String,
    onAppearanceSelect: (String) -> Unit
) {
    SettingsCard {
        appearanceModes.forEachIndexed { index, mode ->
            if (index > 0) SettingsDivider()

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAppearanceSelect(mode.id) },
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = null,
                        tint = if (selectedAppearance == mode.id) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        mode.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedAppearance == mode.id) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ====================== ABOUT SECTION ======================

@Composable
private fun AboutSection() {
    SettingsCard {
        AboutRow("Version", "1.0.0")
        SettingsDivider()
        AboutRow("Build", "1")
        SettingsDivider()
        AboutRow("Developer", "Rishi")
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ====================== DISCLAIMER SECTION ======================

@Composable
private fun DisclaimerSection() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentOrange.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Important Notice",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            DisclaimerBullet("Options trading involves substantial risk of loss")
            DisclaimerBullet("Past performance does not guarantee future results")
            DisclaimerBullet("Only invest capital you can afford to lose")
            DisclaimerBullet("This app does not provide financial advice")
        }
    }
}

@Composable
private fun DisclaimerBullet(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(AccentOrange)
        )
        Text(
            text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}
