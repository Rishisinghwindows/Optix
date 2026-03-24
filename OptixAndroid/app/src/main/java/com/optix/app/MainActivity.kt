package com.optix.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.optix.app.data.local.datastore.ThemeManager
import com.optix.app.data.local.datastore.ThemeMode
import com.optix.app.presentation.components.FloatingChatButton
import com.optix.app.presentation.navigation.BottomNavBar
import com.optix.app.presentation.navigation.NavGraph
import com.optix.app.presentation.navigation.Screen
import com.optix.app.presentation.navigation.shouldShowBottomBar
import com.optix.app.presentation.theme.BackgroundGradientDarkBottom
import com.optix.app.presentation.theme.BackgroundGradientDarkTop
import com.optix.app.presentation.theme.BackgroundGradientLightBottom
import com.optix.app.presentation.theme.BackgroundGradientLightTop
import com.optix.app.presentation.theme.AccentOrange
import com.optix.app.presentation.theme.OptixTheme
import com.optix.app.presentation.theme.SurfaceDark
import com.optix.app.presentation.theme.SurfaceLight
import com.optix.app.data.local.MarketSignalStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeManager: ThemeManager

    @Inject
    lateinit var marketSignalStore: MarketSignalStore

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate()
        installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by themeManager.themeMode.collectAsState(initial = ThemeMode.LIGHT)

            OptixTheme(themeMode = themeMode) {
                val systemDark = isSystemInDarkTheme()
                val isDarkTheme = when (themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> systemDark
                }
                val backgroundBrush = if (isDarkTheme) {
                    Brush.verticalGradient(
                        colors = listOf(BackgroundGradientDarkTop, BackgroundGradientDarkBottom)
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(BackgroundGradientLightTop, BackgroundGradientLightBottom)
                    )
                }
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // Show FAB on main screens but not on chat screen
                val showFab = shouldShowBottomBar(currentRoute) &&
                    currentRoute != Screen.Chat.route

                // Check if current screen needs edge-to-edge (no top padding)
                val isEdgeToEdgeScreen = currentRoute?.startsWith(Screen.Chat.route) == true

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundBrush)
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent,
                        bottomBar = {
                            if (shouldShowBottomBar(currentRoute)) {
                                BottomNavBar(navController = navController)
                            }
                        }
                    ) { innerPadding ->
                        // For edge-to-edge screens, only apply bottom padding
                        val adjustedPadding = if (isEdgeToEdgeScreen) {
                            PaddingValues(bottom = innerPadding.calculateBottomPadding())
                        } else {
                            innerPadding
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(adjustedPadding)
                        ) {
                            NavGraph(
                                navController = navController,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Floating Chat Button overlay
                            AnimatedVisibility(
                                visible = showFab,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                FloatingChatButton(
                                    onClick = {
                                        navController.navigate(Screen.Chat.route)
                                    }
                                )
                            }

                            // Notification Bell Button (top-right)
                            AnimatedVisibility(
                                visible = shouldShowBottomBar(currentRoute),
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                val unreadCount = remember(currentRoute) {
                                    marketSignalStore.unreadCount()
                                }
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    Box(modifier = Modifier.padding(top = 8.dp, end = 16.dp)) {
                                        FloatingActionButton(
                                            onClick = {
                                                navController.navigate(Screen.Notifications.route)
                                            },
                                            modifier = Modifier.size(36.dp),
                                            shape = CircleShape,
                                            containerColor = if (isDarkTheme) SurfaceDark else SurfaceLight,
                                            elevation = FloatingActionButtonDefaults.elevation(4.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Notifications,
                                                contentDescription = "Notifications",
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        if (unreadCount > 0) {
                                            Badge(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = 4.dp, y = (-4).dp),
                                                containerColor = AccentOrange,
                                                contentColor = Color.White
                                            ) {
                                                Text(
                                                    if (unreadCount > 99) "99+" else "$unreadCount",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
