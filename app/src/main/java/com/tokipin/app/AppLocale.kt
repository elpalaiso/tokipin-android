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

    /** The device's own locale (region, regional preferences), independent of the app language. */
    var device: Locale = Locale.getDefault()
        private set

    /**
     * First day of the week from the device region, including Android 14's regional preference
     * (UX v0.1 §4: 주 시작일은 기기 설정). ICU reads the `-u-fw-` locale extension.
     */
    val firstDayOfWeek: java.time.DayOfWeek
        get() {
            val icu = android.icu.util.Calendar.getInstance(android.icu.util.ULocale.forLocale(device)).firstDayOfWeek
            return java.time.DayOfWeek.SUNDAY.plus((icu - 1).toLong())
        }

    fun wrap(base: Context): Context {
        device = base.resources.configuration.locales[0]
        current = if (device.language in supported) device else Locale.forLanguageTag(FALLBACK)
        val config = Configuration(base.resources.configuration).apply { setLocale(current) }
        return base.createConfigurationContext(config)
    }
}
