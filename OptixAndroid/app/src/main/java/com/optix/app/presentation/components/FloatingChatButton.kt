package com.optix.app.presentation.components

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val GradientStart = Color(0xFF6366F1)
private val GradientEnd = Color(0xFF8B5CF6)
private val ChatGradient = Brush.horizontalGradient(listOf(GradientStart, GradientEnd))

private const val PREFS_NAME = "chat_button_prefs"
private const val KEY_OFFSET_X = "offset_x"
private const val KEY_OFFSET_Y = "offset_y"

/**
 * A draggable floating action button for opening the AI chat.
 * Position persists across app sessions using SharedPreferences.
 * Snaps to edge when released.
 */
@Composable
fun BoxScope.FloatingChatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val buttonSizePx = with(density) { 56.dp.toPx() }
    val paddingPx = with(density) { 16.dp.toPx() }

    // Load saved position
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val defaultX = screenWidthPx - buttonSizePx - paddingPx
    val defaultY = screenHeightPx * 0.7f

    var offsetX by remember { mutableFloatStateOf(prefs.getFloat(KEY_OFFSET_X, defaultX)) }
    var offsetY by remember { mutableFloatStateOf(prefs.getFloat(KEY_OFFSET_Y, defaultY)) }

    var isDragging by remember { mutableStateOf(false) }
    var justDragged by remember { mutableStateOf(false) }

    // Animated scale for press feedback
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.1f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scale"
    )

    // Snap to nearest edge when not dragging
    val targetX by animateFloatAsState(
        targetValue = if (!isDragging) {
            // Snap to nearest horizontal edge
            if (offsetX < screenWidthPx / 2) {
                paddingPx
            } else {
                screenWidthPx - buttonSizePx - paddingPx
            }
        } else {
            offsetX
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "targetX"
    )

    // Save snapped position when drag ends (not raw drag position)
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            val snappedX = if (offsetX < screenWidthPx / 2) {
                paddingPx
            } else {
                screenWidthPx - buttonSizePx - paddingPx
            }
            prefs.edit()
                .putFloat(KEY_OFFSET_X, snappedX)
                .putFloat(KEY_OFFSET_Y, offsetY)
                .apply()
        }
    }

    // Reset justDragged after a brief delay to allow click again
    LaunchedEffect(justDragged) {
        if (justDragged) {
            delay(200)
            justDragged = false
        }
    }

    Box(
        modifier = modifier
            .offset { IntOffset(targetX.roundToInt(), offsetY.roundToInt()) }
            .scale(scale)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        justDragged = true
                    },
                    onDragCancel = {
                        isDragging = false
                        justDragged = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Update position during drag
                        offsetX = (offsetX + dragAmount.x).coerceIn(
                            paddingPx,
                            screenWidthPx - buttonSizePx - paddingPx
                        )
                        offsetY = (offsetY + dragAmount.y).coerceIn(
                            paddingPx,
                            screenHeightPx - buttonSizePx - paddingPx * 4 // Extra padding for nav bar
                        )
                    }
                )
            }
    ) {
        FloatingActionButton(
            onClick = { if (!justDragged) onClick() },
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = GradientStart.copy(alpha = 0.3f),
                    spotColor = GradientStart.copy(alpha = 0.3f)
                ),
            shape = CircleShape,
            containerColor = Color.Transparent,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            )
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ChatGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Open AI Chat",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Stateless version without position persistence
 */
@Composable
fun FloatingChatButtonSimple(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = GradientStart.copy(alpha = 0.3f),
                spotColor = GradientStart.copy(alpha = 0.3f)
            ),
        shape = CircleShape,
        containerColor = Color.Transparent,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ChatGradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Open AI Chat",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
