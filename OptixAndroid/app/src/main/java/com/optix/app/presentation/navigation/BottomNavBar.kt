package com.optix.app.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.optix.app.presentation.theme.AccentBlue
import com.optix.app.presentation.theme.AccentOrange
import com.optix.app.presentation.theme.AccentPurple
import com.optix.app.presentation.theme.PrimaryGreen
import com.optix.app.presentation.theme.TabBarShadowDark
import com.optix.app.presentation.theme.TabBarShadowLight

@Composable
fun BottomNavBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isDark = isSystemInDarkTheme()
    val shadowColor = if (isDark) TabBarShadowDark else TabBarShadowLight
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .shadow(
                elevation = 20.dp,
                shape = shape,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Screen.bottomNavItems.forEach { screen ->
                val isSelected = currentRoute == screen.route
                val accentColor = when (screen) {
                    Screen.AIInsights -> AccentPurple
                    Screen.PaperTrading -> AccentOrange
                    Screen.Alerts -> AccentBlue
                    Screen.Settings -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> PrimaryGreen
                }

                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    label = "tab_icon_color"
                )
                val capsulePadding by animateDpAsState(
                    targetValue = if (isSelected) 4.dp else 10.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "capsule_padding"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Transparent)
                        .padding(vertical = 2.dp)
                        .clickable {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(horizontal = capsulePadding, vertical = 4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(accentColor.copy(alpha = 0.18f))
                                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(50))
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) {
                                screen.selectedIcon ?: screen.icon ?: Icons.Default.Home
                            } else {
                                screen.icon ?: Icons.Default.Home
                            },
                            contentDescription = screen.title,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = screen.title,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = iconColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Check if current route is a main tab route
 */
fun shouldShowBottomBar(currentRoute: String?): Boolean {
    return currentRoute in Screen.bottomNavItems.map { it.route }
}
