package com.tokipin.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app ships in Korean only for now, but every string lives in resources so more languages can
 * be added later: translate res/values/strings.xml into values-<lang>/, list the language here and
 * in res/xml/locales_config.xml. A device in an unsupported language still gets the Korean app,
 * including dates, weekdays and system-provided UI such as the time picker.
 */
object AppLocale {
    private val supported = listOf("ko")
    private const val FALLBACK = "ko"

    var current: Locale = Locale.forLanguageTag(FALLBACK)
        private set

    fun wrap(base: Context): Context {
        val device = base.resources.configuration.locales[0]
        current = if (device.language in supported) device else Locale.forLanguageTag(FALLBACK)
        val config = Configuration(base.resources.configuration).apply { setLocale(current) }
        return base.createConfigurationContext(config)
    }
}
