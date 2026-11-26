package com.tokipin.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.tokipin.app.rules.Axis
import com.tokipin.app.rules.SwipeRules
import com.tokipin.app.rules.UxSpec
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Horizontal paging for the day body (±1 day) and the week rail (±1 week), UX v0.1 §2:
 * the current and neighbouring pages follow the finger 1:1 once the drag passes the platform touch
 * slop and locks horizontally (|dx| ≥ 1.3 |dy|); vertical drags are left to the scroll inside.
 * Release commits on 0.22 W, or a 0.06 W flick at 0.9 W/s in the same direction (W in dp), and
 * settles from where the finger left the page; anything else settles back. A touch that starts in
 * the system back-gesture edge is never taken.
 *
 * [onCommit] receives +1 for forward (finger moved left: next day/week) or -1.
 */
@Composable
fun <T> SwipePager(
    page: T,
    neighbor: (T, Int) -> T,
    onCommit: (Int) -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val edges = WindowInsets.systemGestures
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }

    BoxWithConstraints(modifier.clipToBounds().onGloballyPositioned { holder[0] = it }) {
        val widthPx = constraints.maxWidth.toFloat()
        val widthDp = widthPx / density.density

        Box(
            Modifier.fillMaxWidth().pointerInput(page, widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val window = holder[0]?.takeIf { it.isAttached }?.localToWindow(down.position) ?: down.position
                    val rootWidth = holder[0]?.let { c ->
                        var r: LayoutCoordinates = c
                        while (r.parentLayoutCoordinates != null) r = r.parentLayoutCoordinates!!
                        r.size.width.toFloat()
                    } ?: widthPx
                    val leftEdge = edges.getLeft(density, direction).toFloat()
                    val rightEdge = edges.getRight(density, direction).toFloat()
                    if (window.x < leftEdge || window.x > rootWidth - rightEdge) return@awaitEachGesture

                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var total = Offset.Zero
                    var axis: Axis? = null
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } > 1) break // a pinch, not a swipe
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) { released = true; break }
                            total += change.positionChange()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (axis == null && total.getDistance() > viewConfiguration.touchSlop) {
                                axis = SwipeRules.axis(total.x, total.y)
                            }
                            if (axis == Axis.VERTICAL) return@awaitEachGesture
                            if (axis == Axis.HORIZONTAL) {
                                change.consume()
                                scope.launch { offset.snapTo(total.x) }
                            }
                        }
                    } catch (e: CancellationException) {
                        scope.launch { offset.animateTo(0f, tween(UxSpec.CANCEL_MS)) }
                        throw e
                    }
                    if (axis != Axis.HORIZONTAL) return@awaitEachGesture
                    val dir = if (released) {
                        SwipeRules.commit(widthDp, total.x / density.density, tracker.calculateVelocity().x / density.density)
                    } else 0
                    scope.launch {
                        if (dir != 0) {
                            if (!reduceMotion) offset.animateTo(-dir * widthPx, tween(UxSpec.COMMIT_MS))
                            onCommit(dir)
                            offset.snapTo(0f)
                        } else {
                            offset.animateTo(0f, tween(if (reduceMotion) 0 else UxSpec.CANCEL_MS))
                        }
                    }
                }
            },
        ) {
            for (d in -1..1) {
                val p = neighbor(page, d)
                key(p) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { translationX = offset.value + d * widthPx }
                            .then(if (d != 0) Modifier.clearAndSetSemantics { } else Modifier),
                    ) { content(p) }
                }
            }
        }
    }
}
