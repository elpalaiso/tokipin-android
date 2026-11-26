package com.tokipin.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged

/**
 * Two-finger pinch that changes the view level once per gesture (Figma 04 · 네 단계로 확대하고 축소하기).
 * Single-finger drags are left alone so lists keep scrolling; a pinch that ends near 1× does nothing.
 */
fun Modifier.pinchLevels(onZoomIn: () -> Unit, onZoomOut: () -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        var pinching = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                pinching = true
                zoom *= event.calculateZoom()
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (pinching) {
            when {
                zoom > 1.25f -> onZoomIn()
                zoom < 0.8f -> onZoomOut()
            }
        }
    }
}
