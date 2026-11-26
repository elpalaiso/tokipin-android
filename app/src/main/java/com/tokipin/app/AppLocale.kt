package com.tokipin.app

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * The 17 languages of the web (Figma 309-1097 · 언어와 검수), chosen from the person's language list
 * the way the web does: the first preferred language Tokipin supports wins, regional variants are
 * kept apart (es-ES / es-419, zh-Hans / zh-Hant-TW / zh-Hant-HK, pt → pt-BR, in → id), and a list
 * with no supported language gets English. On Android 13+ the per-app language from system settings
 * (res/xml/locales_config.xml) comes first in that list.
 *
 * Dates, weekdays and system-provided UI (date and time pickers) follow the chosen language; the
 * first day of the week and the 12/24-hour setting follow the device.
 */
object AppLocale {
    val supported = listOf(
        "ko", "en", "ja", "de", "fr", "id", "vi", "tr", "ru", "hi", "th",
        "pt-BR", "es-ES", "es-419", "zh-Hans", "zh-Hant-TW", "zh-Hant-HK",
    )
    const val FALLBACK = "en"

    var current: Locale = Locale.forLanguageTag(FALLBACK)
        private set

    /** The device's own locale (region, regional preferences), independent of the app language. */
    var device: Locale = Locale.getDefault()
        private set

    /**
     * Locale for dates, weekdays and times: the app language, with the device's region when the
     * device speaks that language too, so British English reads "Wed, 25 Nov 2026" and Mexican
     * Spanish uses Mexico's conventions, while the words stay in the app language.
     */
    val format: Locale get() = formatFor(current, device)

    fun formatFor(app: Locale, device: Locale): Locale {
        val sameScript = device.script.isEmpty() || app.script.isEmpty() || device.script == app.script
        if (device.language != app.language || !sameScript || device.country.isEmpty()) return app
        return Locale.Builder().setLocale(app).setRegion(device.country).build()
    }

    /** The supported tag for one preferred locale, or null (same rules as the web's matchLocale). */
    fun match(locale: Locale): String? {
        val parts = locale.toLanguageTag().lowercase().replace('_', '-').split('-')
        return when (val language = parts[0]) {
            "zh" -> when {
                "hans" in parts -> "zh-Hans"
                "hk" in parts || "mo" in parts -> "zh-Hant-HK"
                "hant" in parts || "tw" in parts -> "zh-Hant-TW"
                else -> "zh-Hans"
            }
            "es" -> if (parts.size == 1 || parts.drop(1).contains("es")) "es-ES" else "es-419"
            "pt" -> "pt-BR"
            "in" -> "id"
            else -> supported.firstOrNull { it == language }
        }
    }

    /** The app language for a preference list: first supported entry, otherwise English. */
    fun resolve(preferred: LocaleList): String =
        (0 until preferred.size()).firstNotNullOfOrNull { match(preferred[it]) } ?: FALLBACK

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
        val locales = base.resources.configuration.locales
        // The system locale, not the per-app language: the week start and region come from the device.
        device = Resources.getSystem().configuration.locales[0]
        current = Locale.forLanguageTag(resolve(locales))
        return localized(base)
    }

    /** [base] with the app language applied (used for the activity and for view-model messages). */
    fun localized(base: Context): Context {
        val config = Configuration(base.resources.configuration).apply { setLocale(current) }
        return base.createConfigurationContext(config)
    }

    private var cached: Pair<Locale, Resources>? = null

    /** Resources in the app language, for code outside an activity (messages from the view model). */
    fun resources(base: Context): Resources {
        cached?.let { (locale, res) -> if (locale == current) return res }
        return localized(base).resources.also { cached = current to it }
    }
}
