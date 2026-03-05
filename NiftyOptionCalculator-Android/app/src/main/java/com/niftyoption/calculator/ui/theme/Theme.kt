package com.niftyoption.calculator.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// MARK: - Color Definitions

object AppColors {
    // Background colors
    val BackgroundDark = Color(0xFF121212)
    val SurfaceDark = Color(0xFF1E1E1E)
    val CardDark = Color(0xFF2A2A2A)

    // Accent colors
    val AccentGreen = Color(0xFF00E676)
    val AccentRed = Color(0xFFFF5252)
    val AccentBlue = Color(0xFF448AFF)

    // Text colors
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.7f)
    val TextMuted = Color.White.copy(alpha = 0.5f)

    // Call/Put colors
    val CallColor = AccentGreen
    val PutColor = AccentRed

    // ITM/OTM colors
    val ITMBackground = AccentGreen.copy(alpha = 0.1f)
    val OTMBackground = Color.Transparent

    // Light theme colors
    val BackgroundLight = Color(0xFFFAFAFA)
    val SurfaceLight = Color.White
    val CardLight = Color(0xFFF5F5F5)
    val TextPrimaryLight = Color(0xFF212121)
    val TextSecondaryLight = Color(0xFF757575)
}

// MARK: - Color Schemes

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.AccentBlue,
    onPrimary = Color.White,
    secondary = AppColors.AccentGreen,
    onSecondary = Color.Black,
    tertiary = AppColors.AccentRed,
    onTertiary = Color.White,
    background = AppColors.BackgroundDark,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.SurfaceDark,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.CardDark,
    onSurfaceVariant = AppColors.TextSecondary,
    error = AppColors.AccentRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AppColors.AccentBlue,
    onPrimary = Color.White,
    secondary = AppColors.AccentGreen,
    onSecondary = Color.Black,
    tertiary = AppColors.AccentRed,
    onTertiary = Color.White,
    background = AppColors.BackgroundLight,
    onBackground = AppColors.TextPrimaryLight,
    surface = AppColors.SurfaceLight,
    onSurface = AppColors.TextPrimaryLight,
    surfaceVariant = AppColors.CardLight,
    onSurfaceVariant = AppColors.TextSecondaryLight,
    error = AppColors.AccentRed,
    onError = Color.White
)

// MARK: - Theme Composable

@Composable
fun NiftyOptionCalculatorTheme(
    darkTheme: Boolean = true, // Default to dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// MARK: - Typography

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// MARK: - Monospace Typography for Numbers

val MonospaceStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal
)
