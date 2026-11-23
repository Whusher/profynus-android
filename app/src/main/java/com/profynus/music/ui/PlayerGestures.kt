package com.profynus.music.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Swipe left for the next track, right for the previous one.
 *
 * The content follows the finger with a little resistance, fades slightly, and springs back on
 * release. A light haptic tick confirms the skip. Does nothing while [enabled] is false.
 */
@Composable
internal fun Modifier.swipeToSkip(
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 84.dp.toPx() }
    val currentPrevious by rememberUpdatedState(onPrevious)
    val currentNext by rememberUpdatedState(onNext)
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val shown by animateFloatAsState(
        targetValue = if (dragging) dragX else 0f,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "swipe offset",
    )

    return this
        .graphicsLayer {
            translationX = shown * 0.5f
            alpha = 1f - (abs(shown) / (threshold * 4f)).coerceAtMost(0.35f)
        }
        .pointerInput(enabled, threshold) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = {
                    dragX = 0f
                    dragging = true
                },
                onDragEnd = {
                    when {
                        dragX <= -threshold -> {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentNext()
                        }
                        dragX >= threshold -> {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentPrevious()
                        }
                    }
                    dragging = false
                },
                onDragCancel = { dragging = false },
                onHorizontalDrag = { change, delta ->
                    change.consume()
                    dragX += delta
                },
            )
        }
}