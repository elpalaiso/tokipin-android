package com.tokipin.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tokipin.app.ui.AppViewModel
import com.tokipin.app.ui.BrowseScreen
import com.tokipin.app.ui.EditorScreen
import com.tokipin.app.ui.Haptics
import com.tokipin.app.ui.LoadFailedScreen
import com.tokipin.app.ui.LoadingSkeleton
import com.tokipin.app.ui.LocalHaptics
import com.tokipin.app.ui.PolicyScreen
import com.tokipin.app.ui.Screen
import com.tokipin.app.ui.SearchScreen
import com.tokipin.app.ui.SettingsSheet
import com.tokipin.app.ui.TkBackground
import com.tokipin.app.ui.TokipinTheme
import android.provider.Settings as SystemSettings

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the system splash only while a quick read is still running; a slow read shows the skeleton.
        splash.setKeepOnScreenCondition { vm.screen == Screen.Loading && !vm.showSkeleton }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val reduceMotion = runCatching {
            SystemSettings.Global.getFloat(contentResolver, SystemSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)

        setContent {
            TokipinTheme {
                val view = LocalView.current
                val haptics = remember(view) { Haptics(view) { vm.hapticsEnabled } }
                CompositionLocalProvider(LocalHaptics provides haptics) {
                    LaunchedEffect(vm.transientMessage) {
                        vm.transientMessage?.let {
                            Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                            vm.transientMessage = null
                        }
                    }
                    val policy = vm.policy
                    if (policy != null) {
                        PolicyScreen(policy) { vm.openPolicy(null) }
                        return@CompositionLocalProvider
                    }
                    // Editor ↔ home: a short fade (Figma 04 · 180–240ms 시작값), instant when motion is reduced.
                    val duration = if (reduceMotion) 0 else 200
                    AnimatedContent(
                        targetState = vm.screen,
                        transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                        label = "screen",
                    ) { screen ->
                        when (screen) {
                            Screen.Loading -> if (vm.showSkeleton) LoadingSkeleton() else TkBackground {}
                            Screen.LoadFailed -> LoadFailedScreen(onRetry = vm::load)
                            Screen.Editor -> EditorScreen(vm)
                            Screen.Browse -> BrowseScreen(vm)
                            Screen.Search -> SearchScreen(vm)
                        }
                    }
                    if (vm.settingsOpen) SettingsSheet(vm)
                }
            }
        }
    }
}
