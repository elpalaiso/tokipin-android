package com.tokipin.app.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.tokipin.app.AppLocale
import com.tokipin.app.data.Entry
import com.tokipin.app.data.EntryKind
import com.tokipin.app.rules.TimeRules

/**
 * Shows stored "HH:mm" times with the device's 12/24-hour setting (UX v0.1 §4 · 시간): the editor,
 * day view, month cells, recorded-day cards, search and TalkBack all go through [format].
 * The app language decides the words (오전/오후); the device setting decides the hour cycle.
 */
class TimeDisplay(val is24Hour: Boolean, private val pattern12: String, private val pattern24: String) {
    fun format(hhmm: String): String =
        TimeRules.display(hhmm, is24Hour, AppLocale.format, pattern12, pattern24)

    fun text(entry: Entry): String = if (entry.kind == EntryKind.TIME) format(entry.value) else entry.value

    companion object {
        fun from(context: Context): TimeDisplay {
            val locale = AppLocale.format
            return TimeDisplay(
                is24Hour = DateFormat.is24HourFormat(context),
                pattern12 = DateFormat.getBestDateTimePattern(locale, "hm"),
                pattern24 = DateFormat.getBestDateTimePattern(locale, "Hm"),
            )
        }
    }
}

val LocalTimeDisplay = staticCompositionLocalOf { TimeDisplay(true, "a h:mm", "HH:mm") }

@Composable
fun Entry.shown(): String = LocalTimeDisplay.current.text(this)
