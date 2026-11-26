package com.tokipin.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import com.tokipin.app.rules.Level
import com.tokipin.app.rules.PinchAnchor
import com.tokipin.app.rules.PinchRules
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where each date (or month, in the year view) is on screen, so a pinch can zoom around the date
 * under the fingers (UX v0.1 §2 · 핀치 시작·추적). A midpoint in a gap picks the nearest visible
 * cell; on a tie the current selection wins.
 */
class PinchTargets {
    // Layout coordinates, not rectangles: cells only hand over a reference as they move (every
    // scroll frame, for every visible date), and positions are worked out only when a pinch asks.
    private val nodes = LinkedHashMap<Any, LayoutCoordinates>()

    fun put(key: Any, coordinates: LayoutCoordinates) { nodes[key] = coordinates }
    fun remove(key: Any, coordinates: LayoutCoordinates? = null) {
        if (coordinates == null || nodes[key] === coordinates) nodes.remove(key)
    }

    fun at(point: Offset, preferred: Any? = null): Any? {
        val bounds = LinkedHashMap<Any, Rect>()
        nodes.forEach { (key, c) -> if (c.isAttached) bounds[key] = c.boundsInRoot() }
        bounds.entries.firstOrNull { it.value.contains(point) }?.let { return it.key }
        val nearest = bounds.entries.minByOrNull { distanceTo(it.value, point) } ?: return null
        val best = distanceTo(nearest.value, point)
        val preferredRect = preferred?.let { bounds[it] }
        return if (preferredRect != null && distanceTo(preferredRect, point) <= best) preferred else nearest.key
    }

    private fun distanceTo(r: Rect, p: Offset): Float {
        val dx = maxOf(r.left - p.x, 0f, p.x - r.right)
        val dy = maxOf(r.top - p.y, 0f, p.y - r.bottom)
        return dx * dx + dy * dy
    }
}

val LocalPinchTargets = staticCompositionLocalOf { PinchTargets() }

/** Registers this element as the date (LocalDate) or month (YearMonth) a pinch over it refers to. */
fun Modifier.pinchTarget(key: Any): Modifier = this then PinchTargetElement(key)

private data class PinchTargetElement(val key: Any) : androidx.compose.ui.node.ModifierNodeElement<PinchTargetNode>() {
    override fun create() = PinchTargetNode(key)
    override fun update(node: PinchTargetNode) = node.update(key)
}

/** A Modifier.Node (no recomposition, no per-frame allocation) that keeps the registry current. */
private class PinchTargetNode(private var key: Any) : Modifier.Node(),
    androidx.compose.ui.node.GlobalPositionAwareModifierNode,
    androidx.compose.ui.node.CompositionLocalConsumerModifierNode {
    private var targets: PinchTargets? = null
    private var last: LayoutCoordinates? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val t = targets ?: currentValueOf(LocalPinchTargets).also { targets = it }
        last = coordinates
        t.put(key, coordinates)
    }

    fun update(next: Any) {
        if (next == key) return
        targets?.remove(key, last)
        key = next
        last?.let { targets?.put(key, it) }
    }

    override fun onDetach() {
        targets?.remove(key, last)
        targets = null
        last = null
    }
}

/** Callbacks for one pinch, in the coordinates of the pinched area. */
interface PinchListener {
    /** First valid two-finger midpoint: the target is fixed from here on. */
    fun onStart(origin: Offset)
    fun onScale(scale: Float)
    /** Released with two fingers lifting normally; [next] already applies the one-level rule. */
    fun onEnd(next: Level, target: Any?, origin: Offset)
    /** OS cancel, a third finger, or any abnormal end: nothing changes. */
    fun onCancel()
}

private fun PointerEvent.twoPointerSpan(): Pair<Offset, Float>? {
    val down = changes.filter { it.pressed }
    if (down.size != 2) return null
    val a = down[0].position
    val b = down[1].position
    return Offset((a.x + b.x) / 2, (a.y + b.y) / 2) to (a - b).getDistance()
}

/**
 * Two-finger pinch over the calendar content (UX v0.1 §2). The scale is relative to the first valid
 * two-finger spacing, the decision uses the last valid two-finger scale, and only a normal release
 * commits. Once a pinch starts every pointer change is consumed until all fingers lift, so the
 * remaining finger never becomes a date tap or a swipe. One-finger drags pass through untouched.
 */
fun Modifier.pinchLevels(
    level: () -> Level,
    targets: PinchTargets,
    preferred: () -> Any?,
    listener: PinchListener,
): Modifier = composed {
    val holder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    onGloballyPositioned { holder[0] = it }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var started = false
            var startSpan = 0f
            var lastScale = 1f
            var origin = Offset.Zero
            var abnormal = false
            val anchor = PinchAnchor()
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.count { it.pressed }
                    if (pressed > 2) abnormal = true
                    val span = event.twoPointerSpan()
                    if (!started && span != null && span.second > 0f) {
                        started = true
                        startSpan = span.second
                        origin = span.first
                        val root = holder[0]?.takeIf { it.isAttached }?.localToRoot(origin) ?: origin
                        anchor.hit(targets.at(root, preferred()))
                        listener.onStart(origin)
                    } else if (started && span != null && !abnormal) {
                        lastScale = span.second / startSpan
                        listener.onScale(lastScale)
                    }
                    if (started) event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            } catch (e: CancellationException) {
                if (started) listener.onCancel()
                throw e
            }
            if (!started) return@awaitEachGesture
            if (abnormal) { listener.onCancel(); return@awaitEachGesture }
            listener.onEnd(PinchRules.decide(level(), lastScale), anchor.target, origin)
        }
    }
}
