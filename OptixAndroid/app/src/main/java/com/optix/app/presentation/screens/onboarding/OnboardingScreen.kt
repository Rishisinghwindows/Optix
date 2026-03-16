package com.optix.app.presentation.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.R
import com.optix.app.presentation.theme.*
import androidx.compose.ui.composed

// iOS-matching light background (default theme)
private val OnboardingBackgroundTop = BackgroundGradientLightTop
private val OnboardingBackgroundMiddle = BackgroundLight
private val OnboardingBackgroundBottom = BackgroundGradientLightBottom

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color,
    val backgroundIconColor: Color,
    val accentGradient: List<Color>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onNavigateToMain: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pages = listOf(
        OnboardingPage(
            title = stringResource(R.string.onboarding_title_1),
            description = stringResource(R.string.onboarding_desc_1),
            icon = Icons.Default.BarChart,
            iconColor = PrimaryGreen,
            backgroundIconColor = PrimaryGreen.copy(alpha = 0.12f),
            accentGradient = listOf(PrimaryGreen, AccentGreen)
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_title_2),
            description = stringResource(R.string.onboarding_desc_2),
            icon = Icons.Default.AutoAwesome,
            iconColor = AccentPurple,
            backgroundIconColor = AccentPurple.copy(alpha = 0.12f),
            accentGradient = listOf(AccentPurple, Color(0xFFFF375F))
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_title_3),
            description = stringResource(R.string.onboarding_desc_3),
            icon = Icons.Default.ShowChart,
            iconColor = AccentBlue,
            backgroundIconColor = AccentBlue.copy(alpha = 0.12f),
            accentGradient = listOf(AccentBlue, Color(0xFF60A5FA))
        ),
        OnboardingPage(
            title = stringResource(R.string.onboarding_title_4),
            description = stringResource(R.string.onboarding_desc_4),
            icon = Icons.Default.Notifications,
            iconColor = AccentOrange,
            backgroundIconColor = AccentOrange.copy(alpha = 0.12f),
            accentGradient = listOf(AccentOrange, Color(0xFFFFC36A))
        ),
        OnboardingPage(
            title = "Start Trading",
            description = "You're all set! Begin your trading journey with real-time data and intelligent insights.",
            icon = Icons.Default.RocketLaunch,
            iconColor = PrimaryGreen,
            backgroundIconColor = PrimaryGreen.copy(alpha = 0.12f),
            accentGradient = listOf(PrimaryGreen, AccentGreen)
        )
    )

    var currentPage by remember { mutableIntStateOf(0) }
    var direction by remember { mutableIntStateOf(1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        OnboardingBackgroundTop,
                        OnboardingBackgroundMiddle,
                        OnboardingBackgroundBottom
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar with Skip button (44dp touch target)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Progress indicator (iOS style segmented bar)
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(pages.size) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(
                                    if (index <= currentPage)
                                        PrimaryGreen
                                    else
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
                                )
                        )
                    }
                }

                // Skip button (right aligned)
                if (currentPage < pages.size - 1) {
                    TextButton(
                        onClick = {
                            viewModel.completeOnboarding()
                            onNavigateToMain()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.skip),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Pager (hero content area)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(currentPage) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount ->
                                dragOffset += dragAmount
                            },
                            onDragEnd = {
                                val threshold = 80f
                                if (dragOffset < -threshold && currentPage < pages.size - 1) {
                                    direction = 1
                                    currentPage += 1
                                } else if (dragOffset > threshold && currentPage > 0) {
                                    direction = -1
                                    currentPage -= 1
                                }
                                dragOffset = 0f
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        val isForward = direction >= 0
                        (slideInHorizontally(
                            initialOffsetX = { if (isForward) it else -it }
                        ) + fadeIn()).togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { if (isForward) -it else it }
                            ) + fadeOut()
                        )
                    },
                    label = "onboardingPageTransition"
                ) { pageIndex ->
                    OnboardingPageContent(
                        page = pages[pageIndex],
                        pageIndex = pageIndex,
                        isActive = pageIndex == currentPage
                    )
                }
            }

            // Bottom section with page dots and button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicator dots (8dp active, 6dp inactive)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { index ->
                        val isActive = currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (isActive) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive)
                                        PrimaryGreen
                                    else
                                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action button (56dp height, 16dp radius, gradient)
                val isLastPage = currentPage == pages.size - 1

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()

                Button(
                    onClick = {
                        if (isLastPage) {
                            viewModel.completeOnboarding()
                            onNavigateToMain()
                        } else {
                            direction = 1
                            currentPage += 1
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .scale(if (isPressed) 0.96f else 1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    interactionSource = interactionSource
                ) {
                    val gradient = pages[currentPage].accentGradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(gradient)
                            )
                            .shimmer(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Text(
                                text = if (isLastPage)
                                    stringResource(R.string.get_started)
                                else
                                    stringResource(R.string.next),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            if (!isLastPage) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Back button (only show if not first page)
                AnimatedVisibility(
                    visible = currentPage > 0,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    TextButton(
                        onClick = {
                            if (currentPage > 0) {
                                direction = -1
                                currentPage -= 1
                            }
                        },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Back",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingPageContent(page: OnboardingPage, pageIndex: Int, isActive: Boolean) {
    var showHero by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    val featureCount = if (pageIndex == 0 || pageIndex == 1) 3 else 0
    var featureAppeared by remember { mutableStateOf(List(featureCount) { false }) }

    val heroScale by animateFloatAsState(
        targetValue = if (showHero) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessLow
        ),
        label = "heroScale"
    )
    val heroAlpha by animateFloatAsState(
        targetValue = if (showHero) 1f else 0f,
        animationSpec = tween(300),
        label = "heroAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "page$pageIndex")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    LaunchedEffect(isActive) {
        if (isActive) {
            showHero = false
            showText = false
            featureAppeared = List(featureCount) { false }

            showHero = true
            kotlinx.coroutines.delay(150)
            showText = true
            for (i in 0 until featureCount) {
                kotlinx.coroutines.delay(100)
                featureAppeared = featureAppeared.mapIndexed { idx, v ->
                    if (idx == i) true else v
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Hero Icon with animated glow (140dp container)
        Box(
            modifier = Modifier
                .size(180.dp)
                .graphicsLayer {
                    alpha = heroAlpha
                    scaleX = heroScale
                    scaleY = heroScale
                },
            contentAlignment = Alignment.Center
        ) {
            // Outer glow ring (animated)
            Canvas(
                modifier = Modifier
                    .size(180.dp)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            page.iconColor.copy(alpha = glowAlpha * 0.5f),
                            page.iconColor.copy(alpha = glowAlpha * 0.2f),
                            Color.Transparent
                        )
                    ),
                    radius = size.minDimension / 2
                )
            }

            // Middle decorative ring (160dp)
            Canvas(
                modifier = Modifier.size(160.dp)
            ) {
                drawCircle(
                    color = page.iconColor.copy(alpha = 0.15f),
                    radius = size.minDimension / 2 - 2.dp.toPx(),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(8f, 12f),
                            0f
                        )
                    )
                )
            }

            // Main icon circle (140dp)
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(page.backgroundIconColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp),
                    tint = page.iconColor
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Title (28sp, bold, white)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    alpha = if (showText) 1f else 0f
                    translationY = if (showText) 0f else 15f
                }
        ) {
            Text(
                text = page.title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = page.description,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Feature highlights (only on certain pages)
        if (pageIndex == 0) {
            FeatureHighlights(
                features = listOf(
                    "Real-time NIFTY & BANKNIFTY data",
                    "Visual OI & Greeks display",
                    "Smart strike selection"
                ),
                color = page.iconColor,
                featureAppeared = featureAppeared
            )
        } else if (pageIndex == 1) {
            FeatureHighlights(
                features = listOf(
                    "AI-powered trade signals",
                    "Market sentiment analysis",
                    "Personalized recommendations"
                ),
                color = page.iconColor,
                featureAppeared = featureAppeared
            )
        }
    }
}

@Composable
private fun FeatureHighlights(
    features: List<String>,
    color: Color,
    featureAppeared: List<Boolean>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        features.forEachIndexed { index, feature ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
                ,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = if (featureAppeared.getOrNull(index) == true) 1f else 0f
                        translationX = if (featureAppeared.getOrNull(index) == true) 0f else -25f
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = color
                    )
                }
                Text(
                    text = feature,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun Modifier.shimmer(): Modifier = composed {
    var phase by remember { mutableStateOf(-1f) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val animatedPhase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerPhase"
    )
    phase = animatedPhase

    this.then(
        Modifier.drawWithContent {
            drawContent()
            val width = size.width
            val shimmerWidth = width * 0.5f
            val startX = phase * width
            val brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0f),
                    Color.White.copy(alpha = 0.2f),
                    Color.White.copy(alpha = 0f)
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + shimmerWidth, size.height)
            )
            drawRect(brush = brush, size = size)
        }
    )
}
