package com.optix.app.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation destinations
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null,
    val selectedIcon: ImageVector? = null
) {
    // Splash & Onboarding
    data object Splash : Screen("splash", "Splash")
    data object Onboarding : Screen("onboarding", "Onboarding")

    // Auth
    data object Login : Screen("login", "Login")

    // Main Tabs
    data object OptionChain : Screen(
        "option_chain",
        "Chain",
        Icons.Outlined.BarChart,
        Icons.Filled.BarChart
    )

    data object AIInsights : Screen(
        "ai_insights",
        "AI",
        Icons.Outlined.Psychology,
        Icons.Filled.Psychology
    )

    data object PaperTrading : Screen(
        "paper_trading",
        "Trade",
        Icons.Outlined.CurrencyRupee,
        Icons.Filled.CurrencyRupee
    )

    data object Alerts : Screen(
        "alerts",
        "IPO",
        Icons.Outlined.ShowChart,
        Icons.Filled.ShowChart
    )

    data object Settings : Screen(
        "settings",
        "Settings",
        Icons.Outlined.Settings,
        Icons.Filled.Settings
    )

    // Secondary Screens
    data object Calculator : Screen("calculator/{spotPrice}/{strikePrice}/{ltp}/{isCall}/{daysToExpiry}", "Calculator") {
        const val simpleRoute = "calculator_simple"

        fun createRoute(
            spotPrice: Double,
            strikePrice: Double,
            ltp: Double,
            isCall: Boolean,
            daysToExpiry: Int
        ): String = "calculator/$spotPrice/$strikePrice/$ltp/$isCall/$daysToExpiry"
    }
    data object Strategy : Screen("strategy", "Strategy")
    data object StrategyDetail : Screen("strategy_detail", "Strategy Detail")
    data object OIAnalysis : Screen("oi_analysis", "OI Analysis")
    data object IPO : Screen("ipo", "IPO")
    data object Education : Screen("education", "Learn")
    data object Chat : Screen("chat", "Chat")
    data object Charts : Screen("charts", "Charts")

    companion object {
        val bottomNavItems = listOf(
            OptionChain,
            AIInsights,
            PaperTrading,
            Alerts,
            Settings
        )
    }
}
