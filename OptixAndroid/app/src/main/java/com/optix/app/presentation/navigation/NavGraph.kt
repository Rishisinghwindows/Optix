package com.optix.app.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.optix.app.presentation.screens.ai.AIInsightsScreen
import com.optix.app.presentation.screens.alerts.AlertsScreen
import com.optix.app.presentation.screens.auth.LoginScreen
import com.optix.app.presentation.screens.calculator.CalculatorScreen
import com.optix.app.presentation.screens.chain.OptionChainScreen
import com.optix.app.presentation.screens.chat.ChatScreen
import com.optix.app.presentation.screens.education.EducationScreen
import com.optix.app.presentation.screens.ipo.IPOScreen
import com.optix.app.presentation.screens.oianalysis.OIAnalysisScreen
import com.optix.app.presentation.screens.onboarding.OnboardingScreen
import com.optix.app.presentation.screens.settings.SettingsScreen
import com.optix.app.presentation.screens.splash.SplashScreen
import com.optix.app.presentation.screens.strategy.StrategyBuilderScreen
import com.optix.app.presentation.screens.strategy.StrategyDetailScreen
import com.optix.app.presentation.screens.trade.PaperTradingScreen
import com.optix.app.domain.model.*
import com.optix.app.presentation.screens.charts.ChartsScreen
import com.optix.app.presentation.screens.journal.TradeJournalScreen
import com.optix.app.presentation.screens.notifications.NotificationsScreen
import com.optix.app.presentation.screens.screener.OptionScreenerScreen
import com.optix.app.presentation.screens.simulator.PnLSimulatorScreen
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    startDestination: String = Screen.Splash.route
) {
    // Shared state for passing complex objects between screens
    val selectedStrategy = remember { mutableStateOf<Strategy?>(null) }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) +
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300)) +
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300)) +
                    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) +
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300))
        }
    ) {
        // Splash
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToMain = {
                    navController.navigate(Screen.OptionChain.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onNavigateToMain = {
                    navController.navigate(Screen.OptionChain.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Login
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Main Tabs
        composable(Screen.OptionChain.route) {
            OptionChainScreen(
                onNavigateToCalculator = { spotPrice, strikePrice, ltp, isCall, daysToExpiry ->
                    navController.navigate(
                        Screen.Calculator.createRoute(spotPrice, strikePrice, ltp, isCall, daysToExpiry)
                    )
                },
                onNavigateToOIAnalysis = {
                    navController.navigate(Screen.OIAnalysis.route)
                },
                onNavigateToStrategy = {
                    navController.navigate(Screen.Strategy.route)
                },
                onNavigateToCharts = {
                    navController.navigate(Screen.Charts.route)
                },
                onNavigateToScreener = {
                    navController.navigate(Screen.OptionScreener.route)
                }
            )
        }

        composable(Screen.AIInsights.route) {
            AIInsightsScreen(
                onNavigateToChat = { query ->
                    // Navigate to chat with the pre-filled query
                    navController.navigate("${Screen.Chat.route}?query=${java.net.URLEncoder.encode(query, "UTF-8")}")
                }
            )
        }

        composable(Screen.PaperTrading.route) {
            PaperTradingScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route)
                }
            )
        }

        composable(Screen.Alerts.route) {
            AlertsScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToCalculator = {
                    navController.navigate(Screen.Calculator.simpleRoute)
                },
                onNavigateToOIAnalysis = {
                    navController.navigate(Screen.OIAnalysis.route)
                },
                onNavigateToChat = {
                    navController.navigate(Screen.Chat.route)
                },
                onNavigateToIPO = {
                    navController.navigate(Screen.IPO.route)
                },
                onNavigateToEducation = {
                    navController.navigate(Screen.Education.route)
                },
                onNavigateToCharts = {
                    navController.navigate(Screen.Charts.route)
                },
                onNavigateToSimulator = {
                    navController.navigate(Screen.PnLSimulator.route)
                },
                onNavigateToJournal = {
                    navController.navigate(Screen.TradeJournal.route)
                }
            )
        }

        // Secondary Screens
        composable(
            route = Screen.Calculator.route,
            arguments = listOf(
                navArgument("spotPrice") { type = NavType.StringType },
                navArgument("strikePrice") { type = NavType.StringType },
                navArgument("ltp") { type = NavType.StringType },
                navArgument("isCall") { type = NavType.BoolType },
                navArgument("daysToExpiry") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val spotPrice = backStackEntry.arguments?.getString("spotPrice")?.toDoubleOrNull() ?: 0.0
            val strikePrice = backStackEntry.arguments?.getString("strikePrice")?.toDoubleOrNull() ?: 0.0
            val ltp = backStackEntry.arguments?.getString("ltp")?.toDoubleOrNull() ?: 0.0
            val isCall = backStackEntry.arguments?.getBoolean("isCall") ?: true
            val daysToExpiry = backStackEntry.arguments?.getInt("daysToExpiry") ?: 7

            CalculatorScreen(
                onNavigateBack = { navController.popBackStack() },
                initialSpotPrice = spotPrice,
                initialStrikePrice = strikePrice,
                initialLTP = ltp,
                initialIsCall = isCall,
                initialDaysToExpiry = daysToExpiry
            )
        }

        // Simple Calculator route (from Settings, without parameters)
        composable(Screen.Calculator.simpleRoute) {
            CalculatorScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.OIAnalysis.route) {
            OIAnalysisScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Strategy.route) {
            StrategyBuilderScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { strategy ->
                    selectedStrategy.value = strategy
                    navController.navigate(Screen.StrategyDetail.route)
                }
            )
        }

        composable(Screen.StrategyDetail.route) {
            val strategy = selectedStrategy.value
            if (strategy != null) {
                StrategyDetailScreen(
                    strategy = strategy,
                    onBack = { navController.popBackStack() }
                )
            } else {
                // Fallback: navigate back if no strategy is available
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        composable(
            route = "${Screen.Chat.route}?query={query}",
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query")?.let {
                try { java.net.URLDecoder.decode(it, "UTF-8") } catch (e: Exception) { "" }
            } ?: ""
            ChatScreen(
                onBack = { navController.popBackStack() },
                initialQuery = query
            )
        }

        composable(Screen.IPO.route) {
            IPOScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Education.route) {
            EducationScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Charts.route) {
            ChartsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Notifications.route) {
            NotificationsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.OptionScreener.route) {
            OptionScreenerScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PnLSimulator.route) {
            PnLSimulatorScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TradeJournal.route) {
            TradeJournalScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route)
                }
            )
        }
    }
}
