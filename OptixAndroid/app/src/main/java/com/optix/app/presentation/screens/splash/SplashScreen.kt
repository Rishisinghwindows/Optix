package com.optix.app.presentation.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.presentation.theme.PrimaryGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

// iOS-matching colors
private val SplashBackgroundTop = Color(0xFF0A0B0F)
private val SplashBackgroundMiddle = Color(0xFF0F1117)
private val SplashBackgroundBottom = Color(0xFF0A0B0F)
private val RadialGlowColor = Color(0xFF00C805)

@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToMain: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val shouldShowOnboarding by viewModel.shouldShowOnboarding.collectAsState()
    val isReady by viewModel.isReady.collectAsState()

    // Animation states
    val logoScale = remember { Animatable(0.3f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    // Rotation animations for rings
    val infiniteTransition = rememberInfiniteTransition(label = "rings")

    val outerRingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRing"
    )

    val innerRingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRing"
    )

    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )

    LaunchedEffect(Unit) {
        // Logo animation
        logoScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = 200f
            )
        )
    }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600)
        )
        delay(200)
        textAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500)
        )

        // Wait for minimum animation duration
        delay(2000)

        // Wait for DataStore read to complete before navigating
        viewModel.isReady.filter { it }.first()

        // Navigate based on onboarding status (now guaranteed to be up-to-date)
        if (viewModel.shouldShowOnboarding.value) {
            onNavigateToOnboarding()
        } else {
            onNavigateToMain()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SplashBackgroundTop,
                        SplashBackgroundMiddle,
                        SplashBackgroundBottom
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Radial glow effect behind logo
        Canvas(
            modifier = Modifier
                .size(300.dp)
                .alpha(0.15f * logoAlpha.value)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RadialGlowColor.copy(alpha = 0.4f),
                        RadialGlowColor.copy(alpha = 0.1f),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension / 2
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(logoScale.value)
                .alpha(logoAlpha.value)
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Animated Logo with rotating rings
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer rotating ring (160dp)
                Canvas(
                    modifier = Modifier
                        .size(160.dp)
                        .rotate(outerRingRotation)
                        .scale(ringScale)
                ) {
                    drawCircle(
                        color = PrimaryGreen.copy(alpha = 0.3f),
                        radius = size.minDimension / 2 - 3.dp.toPx(),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                // Middle dashed rotating ring (140dp)
                Canvas(
                    modifier = Modifier
                        .size(140.dp)
                        .rotate(-outerRingRotation * 0.7f)
                ) {
                    drawCircle(
                        color = PrimaryGreen.copy(alpha = 0.5f),
                        radius = size.minDimension / 2 - 2.dp.toPx(),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(10f, 15f),
                                0f
                            )
                        )
                    )
                }

                // Inner rotating ring (120dp)
                Canvas(
                    modifier = Modifier
                        .size(120.dp)
                        .rotate(innerRingRotation)
                ) {
                    drawCircle(
                        color = PrimaryGreen.copy(alpha = 0.4f),
                        radius = size.minDimension / 2 - 2.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Main logo circle (100dp)
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    PrimaryGreen,
                                    PrimaryGreen.copy(alpha = 0.8f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App Name - iOS style (44sp, black weight)
            Text(
                text = "OPTIX",
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 4.sp,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline - iOS style (12sp, bold, letter-spacing 5)
            Text(
                text = "SMART OPTIONS TRADING",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryGreen,
                letterSpacing = 5.sp,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle - iOS style (14sp, medium)
            Text(
                text = "Powered by AI",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Loading Dots
            LoadingDotsView(
                modifier = Modifier
                    .padding(bottom = 48.dp)
                    .alpha(textAlpha.value)
            )
        }
    }
}

@Composable
private fun LoadingDotsView(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val delay = index * 200
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )

            val scale by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dotScale$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(PrimaryGreen.copy(alpha = alpha))
            )
        }
    }
}
