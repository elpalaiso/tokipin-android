package com.tokipin.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Whether the soft keyboard was put away on this screen, kept across activity recreation
 * (rotation, fold/unfold, resize; UX v0.4 §5), so a recreated screen brings the keyboard back only
 * if it was up.
 *
 * Read from the window's own insets (the activity's adjustResize window reports them whether or not
 * Compose consumes them), sampled while the screen is resumed: the teardown for a rotation or fold
 * also drops the keyboard and must not count. A new window can briefly report the old window's
 * keyboard, so only a keyboard this screen has seen come up can be put away.
 */
@Composable
fun rememberKeyboardPutAway(): MutableState<Boolean> {
    val putAway = rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(view, lifecycle) {
        var shown = false
        fun sample() {
            val visible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (visible) shown = true
            if (shown && putAway.value == visible) putAway.value = !visible
        }
        // One last look as the screen pauses, before any teardown hides the keyboard.
        val onPause = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) sample() }
        lifecycle.addObserver(onPause)
        try {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    sample()
                    delay(KEYBOARD_SAMPLE_MS)
                }
            }
        } finally {
            lifecycle.removeObserver(onPause)
        }
    }
    return putAway
}

private const val KEYBOARD_SAMPLE_MS = 200L
