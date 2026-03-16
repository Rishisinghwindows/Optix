package com.optix.app.presentation.theme

import androidx.compose.ui.graphics.Color

// Primary Brand Colors - Professional Green Theme (like iOS)
val PrimaryGreen = Color(0xFF00C805)        // Vibrant green for profits
val AccentGreen = Color(0xFF32D74B)         // Lighter green accent
val ProfitGreen = Color(0xFF00B386)         // Teal green for profit indicators

// Dark Theme Colors
val BackgroundDark = Color(0xFF0D0D0D)      // Almost black
val SurfaceDark = Color(0xFF1A1A1A)         // Elevated surface
val SurfaceElevatedDark = Color(0xFF242424) // More elevated
val CardDark = Color(0xFF1E1E1E)            // Card background
val BorderDark = Color(0xFF333333)          // Borders

// Light Theme Colors
val BackgroundLight = Color(0xFFF5F5F5)     // Light gray
val SurfaceLight = Color(0xFFFFFFFF)        // White
val SurfaceElevatedLight = Color(0xFFFAFAFA)
val CardLight = Color(0xFFFFFFFF)           // White card
val BorderLight = Color(0xFFE5E7EB)         // Light border
val BackgroundGradientLightTop = Color(0xFFF5F5F5)
val BackgroundGradientLightBottom = Color(0xFFEBEBEB)
val BackgroundGradientDarkTop = Color(0xFF0D0D0D)
val BackgroundGradientDarkBottom = Color(0xFF000000)

// Semantic Colors
val Profit = Color(0xFF00C805)              // Green
val Loss = Color(0xFFFF3B30)                // Red
val Warning = Color(0xFFFF9F0A)             // Orange
val Info = Color(0xFF007AFF)                // Blue

// Aliases
val SuccessGreen = Color(0xFF00C805)
val ErrorRed = Color(0xFFFF3B30)
val WarningOrange = Color(0xFFFF9F0A)
val AccentOrange = Color(0xFFFF9F0A)  // Orange accent color
val AccentBlue = Color(0xFF007AFF)
val AccentPurple = Color(0xFFBF5AF2)
val AccentCyan = Color(0xFF64D2FF)

// Text Colors - Dark Theme
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFFA0A0A0)
val TextMutedDark = Color(0xFF6E6E6E)

// Text Colors - Light Theme
val TextPrimaryLight = Color(0xFF1A1A1A)
val TextSecondaryLight = Color(0xFF6B7280)
val TextMutedLight = Color(0xFF9CA3AF)
val TabBarShadowLight = Color(0x14000000)
val TabBarShadowDark = Color(0x4D000000)

// Call/Put Colors
val CallColor = Color(0xFF00C805)           // Green for calls
val PutColor = Color(0xFFFF3B30)            // Red for puts

// ITM Background Colors (subtle tints)
val CallITMBackground = Color(0x1A00C805)   // Green with 10% alpha
val PutITMBackground = Color(0x1AFF3B30)    // Red with 10% alpha

// Status Colors
val StatusLive = Color(0xFF00C805)          // Live data indicator
val StatusDemo = Color(0xFFFF9F0A)          // Demo mode

// Confidence/Score Colors
val ScoreHigh = Color(0xFF00C805)           // 70+
val ScoreMedium = Color(0xFFFF9F0A)         // 50-70
val ScoreLow = Color(0xFFFF3B30)            // <50

// Chart Colors
val ChartLine1 = Color(0xFF00C805)
val ChartLine2 = Color(0xFF007AFF)
val ChartLine3 = Color(0xFFBF5AF2)
val ChartGradientTop = Color(0x4D00C805)    // 30% alpha
val ChartGradientBottom = Color(0x0000C805) // 0% alpha

// Legacy aliases for backward compatibility
val PrimaryDark = PrimaryGreen
val SecondaryDark = AccentBlue
val PrimaryLight = PrimaryGreen
val SecondaryLight = AccentBlue
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
val TextTertiaryDark = TextMutedDark
val TextTertiaryLight = TextMutedLight
val ConfidenceHigh = ScoreHigh
val ConfidenceMedium = ScoreMedium
val ConfidenceLow = ScoreLow
