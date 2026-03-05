package com.niftyoption.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.niftyoption.calculator.ui.screens.CalculatorScreen
import com.niftyoption.calculator.ui.screens.OptionChainScreen
import com.niftyoption.calculator.ui.screens.OptionScreenerScreen
import com.niftyoption.calculator.ui.screens.PnLSimulatorScreen
import com.niftyoption.calculator.ui.screens.TradeJournalScreen
import com.niftyoption.calculator.ui.theme.AppColors
import com.niftyoption.calculator.ui.theme.NiftyOptionCalculatorTheme
import com.niftyoption.calculator.ui.viewmodels.CalculatorViewModel
import com.niftyoption.calculator.ui.viewmodels.OptionChainViewModel
import com.niftyoption.calculator.ui.viewmodels.OptionScreenerViewModel
import com.niftyoption.calculator.ui.viewmodels.PnLSimulatorViewModel
import com.niftyoption.calculator.ui.viewmodels.TradeJournalViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NiftyOptionCalculatorTheme(darkTheme = true) {
                MainScreen()
            }
        }
    }
}

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object OptionChain : Screen(
        route = "option_chain",
        title = "Option Chain",
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Outlined.List
    )

    data object Calculator : Screen(
        route = "calculator",
        title = "Calculator",
        selectedIcon = Icons.Filled.Calculate,
        unselectedIcon = Icons.Outlined.Calculate
    )

    data object Screener : Screen(
        route = "screener",
        title = "Screener",
        selectedIcon = Icons.Filled.FilterList,
        unselectedIcon = Icons.Outlined.FilterList
    )

    data object Simulator : Screen(
        route = "simulator",
        title = "Simulator",
        selectedIcon = Icons.Filled.TrendingUp,
        unselectedIcon = Icons.Outlined.TrendingUp
    )

    data object Journal : Screen(
        route = "journal",
        title = "Journal",
        selectedIcon = Icons.Filled.EditNote,
        unselectedIcon = Icons.Outlined.EditNote
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val optionChainViewModel: OptionChainViewModel = viewModel()
    val calculatorViewModel: CalculatorViewModel = viewModel()
    val screenerViewModel: OptionScreenerViewModel = viewModel()
    val simulatorViewModel: PnLSimulatorViewModel = viewModel()
    val journalViewModel: TradeJournalViewModel = viewModel()

    var selectedScreen by remember { mutableStateOf<Screen>(Screen.OptionChain) }

    val screens = listOf(Screen.OptionChain, Screen.Calculator, Screen.Screener, Screen.Simulator, Screen.Journal)

    // Handle option selection from option chain
    val selectedOption by optionChainViewModel.selectedOption.collectAsState()
    LaunchedEffect(selectedOption) {
        selectedOption?.let { option ->
            calculatorViewModel.loadOption(option)
            selectedScreen = Screen.Calculator
            optionChainViewModel.clearSelectedOption()
        }
    }

    // Sync option chain data to screener
    val chain by optionChainViewModel.optionChain.collectAsState()
    val spot by optionChainViewModel.spotPrice.collectAsState()
    LaunchedEffect(chain, spot) {
        screenerViewModel.setOptionChainData(chain, spot)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = AppColors.SurfaceDark
            ) {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selectedScreen == screen) {
                                    screen.selectedIcon
                                } else {
                                    screen.unselectedIcon
                                },
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.AccentBlue,
                            selectedTextColor = AppColors.AccentBlue,
                            indicatorColor = AppColors.AccentBlue.copy(alpha = 0.2f),
                            unselectedIconColor = AppColors.TextSecondary,
                            unselectedTextColor = AppColors.TextSecondary
                        )
                    )
                }
            }
        },
        containerColor = AppColors.BackgroundDark
    ) { paddingValues ->
        when (selectedScreen) {
            Screen.OptionChain -> {
                OptionChainScreen(
                    viewModel = optionChainViewModel,
                    onOptionSelected = { option ->
                        optionChainViewModel.selectOption(option)
                    }
                )
            }
            Screen.Calculator -> {
                CalculatorScreen(
                    viewModel = calculatorViewModel
                )
            }
            Screen.Screener -> {
                OptionScreenerScreen(
                    viewModel = screenerViewModel,
                    onBack = { selectedScreen = Screen.OptionChain }
                )
            }
            Screen.Simulator -> {
                PnLSimulatorScreen(
                    viewModel = simulatorViewModel,
                    onBack = { selectedScreen = Screen.OptionChain }
                )
            }
            Screen.Journal -> {
                TradeJournalScreen(
                    viewModel = journalViewModel,
                    onBack = { selectedScreen = Screen.OptionChain }
                )
            }
        }
    }
}
