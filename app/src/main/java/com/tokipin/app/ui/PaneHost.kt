package com.tokipin.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.tokipin.app.rules.Adaptive
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * UX v0.4 window host. Reads the app window's size (never reduced by the keyboard) and a separating
 * fold, decides one column or two panes with [Adaptive.layout], and draws the same state either way:
 * one column is the v0.3 screen; two panes put the calendar (or the search list) and the day (or
 * the editor) side by side or, across a horizontal fold, the day above the calendar.
 */
@Composable
fun PaneHost(vm: AppViewModel, single: @Composable () -> Unit, pane: @Composable (PaneRole) -> Unit) {
    val fold = rememberFold()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowWidth = maxWidth.value
        val windowHeight = maxHeight.value
        val layout = Adaptive.layout(maxWidth.value, maxHeight.value, fold, journalEmpty = vm.journal.isEmpty, sampleTour = vm.tour != null)
        val panes = layout !is Adaptive.Layout.Single &&
            (vm.screen == Screen.Browse || vm.screen == Screen.Editor || vm.screen == Screen.Search)
        SideEffect { if (vm.twoPane != panes) vm.twoPane = panes }
        if (!panes) {
            // One column; beside a separating fold it stays in the larger side.
            val region = (layout as? Adaptive.Layout.Single)?.let { regionOf(it.content, fold, maxWidth.value, maxHeight.value) }
            if (region == null || (region.width == maxWidth.value && region.height == maxHeight.value)) single()
            else TkBackground { Box(Modifier.place(region).ownInsets(region, windowWidth, windowHeight)) { single() } }
            return@BoxWithConstraints
        }
        val (calendar, day) = when (layout) {
            is Adaptive.Layout.SideBySide -> layout.calendar to layout.day
            is Adaptive.Layout.Stacked -> layout.calendar to layout.day
            is Adaptive.Layout.Single -> error("unreachable")
        }
        val stacked = layout is Adaptive.Layout.Stacked
        TkBackground {
            // Reading order follows the screen: calendar then day side by side; day then calendar stacked.
            val calendarPane = @Composable {
                Box(
                    Modifier.place(calendar).testTag("calendarPane")
                        // Read one pane through before the next (TalkBack follows the screen order).
                        .semantics { isTraversalGroup = true }
                        // The lower pane starts at its header: no status bar room.
                        .ownInsets(calendar, windowWidth, windowHeight),
                ) {
                    val editing = vm.screen == Screen.Editor
                    Box(if (editing) Modifier.fillMaxSize().clearAndSetSemantics { } else Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalPaneRole provides PaneRole.CALENDAR) { pane(PaneRole.CALENDAR) }
                    }
                    // While editing, the calendar is dimmed and out of reach of touch and assistive tech.
                    if (editing) {
                        Box(
                            Modifier.fillMaxSize().background(Color(0x99151A17)).testTag("calendarScrim")
                                .clearAndSetSemantics { }
                                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
                        )
                    }
                }
            }
            val dayPane = @Composable {
                Box(
                    Modifier.place(day).testTag("dayPane")
                        .semantics { isTraversalGroup = true }
                        .ownInsets(day, windowWidth, windowHeight),
                ) {
                    CompositionLocalProvider(LocalPaneRole provides PaneRole.DAY) { pane(PaneRole.DAY) }
                }
            }
            if (stacked) { dayPane(); calendarPane() } else { calendarPane(); dayPane() }
            // One 1dp line between the panes, in the divider colour.
            val divider = if (stacked) Adaptive.Rect(day.x, (day.bottom + calendar.y) / 2, day.width, 1f)
            else Adaptive.Rect((calendar.right + day.x) / 2, 0f, 1f, windowHeight)
            Box(Modifier.place(divider).background(Tk.Divider).clearAndSetSemantics { })
        }
    }
}

private fun Modifier.place(r: Adaptive.Rect): Modifier = offset(r.x.dp, r.y.dp).size(r.width.dp, r.height.dp)

/**
 * Window insets belong to a pane only where they overlap it: the stretch between the pane and each
 * window edge is consumed. A pane above a fold takes neither the navigation bar nor the keyboard
 * (which covers the pane below; only an overflow above the fold reaches it), and a pane below the
 * fold starts at its header with no status bar room.
 */
private fun Modifier.ownInsets(r: Adaptive.Rect, windowWidth: Float, windowHeight: Float): Modifier =
    consumeWindowInsets(androidx.compose.foundation.layout.PaddingValues(
        start = r.x.dp, top = r.y.dp,
        end = (windowWidth - r.right).coerceAtLeast(0f).dp, bottom = (windowHeight - r.bottom).coerceAtLeast(0f).dp,
    ))

/** The side of a separating fold a single column sits in (the whole window without one). */
private fun regionOf(content: Adaptive.Rect, fold: Adaptive.Fold?, width: Float, height: Float): Adaptive.Rect {
    if (fold == null || !fold.separating) return Adaptive.Rect(0f, 0f, width, height)
    return if (fold.orientation == Adaptive.FoldOrientation.VERTICAL) {
        if (content.x >= fold.end) Adaptive.Rect(fold.end, 0f, width - fold.end, height) else Adaptive.Rect(0f, 0f, fold.start, height)
    } else {
        if (content.y >= fold.end) Adaptive.Rect(0f, fold.end, width, height - fold.end) else Adaptive.Rect(0f, 0f, width, fold.start)
    }
}

/** The window's separating fold in dp window coordinates, or null (flat, no hinge, other windows). */
@Composable
private fun rememberFold(): Adaptive.Fold? {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
    } ?: return null
    val density = LocalDensity.current
    val flow = remember(activity) {
        WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        }
    }
    val feature by flow.collectAsState(initial = null)
    val f = feature ?: return null
    // Bounds are in window pixels.
    val b = f.bounds
    with(density) {
        return if (f.orientation == FoldingFeature.Orientation.VERTICAL) {
            Adaptive.Fold(Adaptive.FoldOrientation.VERTICAL, b.left.toDp().value, b.right.toDp().value, f.isSeparating)
        } else {
            Adaptive.Fold(Adaptive.FoldOrientation.HORIZONTAL, b.top.toDp().value, b.bottom.toDp().value, f.isSeparating)
        }
    }
}

/**
 * A text field that moves between one column and a pane (the same composition, see MainActivity)
 * can lose focus as the window changes: the move re-places its node, and a fold also hands the
 * window to the other display first. If the field lost focus just before the layout or window
 * changed, take it back once it sits in its new place, so the cursor and keyboard stay with the
 * person's typing (UX v0.4 §5).
 */
@Composable
fun Modifier.keepFocusAcrossLayouts(focus: androidx.compose.ui.focus.FocusRequester): Modifier {
    val role = LocalPaneRole.current
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val window = config.screenWidthDp to config.screenHeightDp
    val focused = remember { androidx.compose.runtime.mutableStateOf(false) }
    // [0] when focus was last lost, [1] when the layout or window last changed.
    val times = remember { longArrayOf(0L, 0L) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun now() = android.os.SystemClock.uptimeMillis()
    fun restore() = scope.launch {
        androidx.compose.runtime.withFrameNanos { }
        if (!focused.value) runCatching { focus.requestFocus() }
    }
    androidx.compose.runtime.LaunchedEffect(role, window) {
        times[1] = now()
        // Focus that went just before the change (the window moving to the other display).
        if (now() - times[0] < FOCUS_RESTORE_WINDOW_MS) restore()
    }
    return this.then(Modifier.onFocusChanged {
        if (focused.value && !it.isFocused) {
            times[0] = now()
            // Focus that goes just after the change (the node being re-placed).
            if (now() - times[1] < FOCUS_RESTORE_WINDOW_MS) restore()
        }
        focused.value = it.isFocused
    })
}

/** How close a lost focus must be to a layout or window change to count as caused by it. */
private const val FOCUS_RESTORE_WINDOW_MS = 1_000L
