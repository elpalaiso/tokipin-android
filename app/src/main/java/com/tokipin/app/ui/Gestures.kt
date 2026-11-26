package com.tokipin.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where each date (or month, in the year view) is on screen, so a pinch can zoom around the date
 * between the two fingers (Figma 04 · 핀치 중에는 날짜를 기준으로).
 */
class PinchTargets {
    private val bounds = mutableMapOf<Any, Rect>()

    fun put(key: Any, rect: Rect) { bounds[key] = rect }
    fun remove(key: Any) { bounds.remove(key) }

    fun at(point: Offset): Any? =
        bounds.entries.firstOrNull { it.value.contains(point) }?.key
            ?: bounds.entries.minByOrNull { (it.value.center - point).getDistanceSquared() }?.key
}

val LocalPinchTargets = staticCompositionLocalOf { PinchTargets() }

/** Registers this element as the date (LocalDate) or month (YearMonth) a pinch over it refers to. */
fun Modifier.pinchTarget(key: Any): Modifier = composed {
    val targets = LocalPinchTargets.current
    DisposableEffect(key) { onDispose { targets.remove(key) } }
    onGloballyPositioned { targets.put(key, it.boundsInRoot()) }
}

/**
 * Two-finger pinch that changes the view level once per gesture (Figma 04 · 네 단계로 확대하고 축소하기).
 * Single-finger drags pass through so lists keep scrolling. While pinching, [onProgress] reports the
 * live zoom so the view can follow the fingers; a pinch released near 1× changes nothing.
 * [onPinch] gets `true` for zoom in, and the centroid in root coordinates.
 */
fun Modifier.pinchLevels(
    onProgress: (Float) -> Unit,
    onPinch: (zoomIn: Boolean, centroidInRoot: Offset) -> Unit,
): Modifier = composed {
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    onGloballyPositioned { holder[0] = it }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var zoom = 1f
            var pinching = false
            var centroid = Offset.Unspecified
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    pinching = true
                    zoom *= event.calculateZoom()
                    val c = event.calculateCentroid(useCurrent = true)
                    if (c.isSpecified) centroid = c
                    onProgress(zoom)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
            onProgress(1f)
            if (pinching) {
                val root = holder[0]?.takeIf { it.isAttached && centroid.isSpecified }?.localToRoot(centroid) ?: centroid
                when {
                    zoom > ZOOM_IN -> onPinch(true, root)
                    zoom < ZOOM_OUT -> onPinch(false, root)
                }
            }
        }
    }
}

// GUESS: thresholds are not in Figma; tuned so a deliberate pinch switches and a nudge does not.
private const val ZOOM_IN = 1.25f
private const val ZOOM_OUT = 0.8f
