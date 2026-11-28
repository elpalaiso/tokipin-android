package com.tokipin.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Whether the soft keyboard was down when this screen was last paused, kept for the changes that
 * still recreate the activity (language, font size; window changes keep it, UX v0.4 §5). Callers
 * read it once when the screen is built and then forget it.
 *
 * Read from the window's insets as the screen pauses: a language or font-size change always passes
 * through pause before the teardown that drops the keyboard. (Compose's `WindowInsets.ime` stayed 0
 * here in emulator runs, so the view's own insets are used.)
 */
@Composable
fun rememberKeyboardWasDown(): Boolean {
    val down = rememberSaveable { mutableStateOf(false) }
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                down.value = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) != true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return down.value
}
