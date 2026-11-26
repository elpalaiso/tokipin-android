package com.tokipin.app.rules

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Tokipin native UX v0.1 (shared with iOS: tokipin/native-spec/v0.1). Every tuning value and every
 * product rule for gestures, haptics, calendar, time and emoji lives here, free of Android APIs, so
 * the shared JSON cases can test it directly. Platform code (pointer input, IME, time picker,
 * vibration) only converts its events into these calls.
 */
object UxSpec {
    const val VERSION = "0.2"

    // Swipe (W = usable width of the gesture area, in dp)
    const val AXIS_LOCK_RATIO = 1.3f
    const val DISTANCE_COMMIT_W = 0.22f
    const val FLICK_MIN_DISTANCE_W = 0.06f
    const val FLICK_MIN_SPEED_W_PER_S = 0.9f
    const val DAY_STEP = 1
    const val WEEK_STEP = 7

    // Pinch
    const val ZOOM_IN_AT_OR_ABOVE = 1.25f
    const val ZOOM_OUT_AT_OR_BELOW = 0.80f

    // Settling (ms); start values for device tuning
    const val COMMIT_MS = 200
    const val CANCEL_MS = 150
    const val REDUCED_MOTION_FADE_MS = 90

    // Emoji input on Android: focus the person's own keyboard; no app palette. No IME API can
    // guarantee opening another vendor's emoji panel, so a hint is shown when needed.
    const val EMOJI_REQUESTS_SYSTEM_KEYBOARD = true
    const val EMOJI_GUARANTEES_PANEL_SWITCH = false
    const val EMOJI_APP_PALETTE = false
    const val EMOJI_ACCEPTS_PASTE = true

    /** Floating-point slack so a value exactly on a threshold (0.22 × 390 = 85.8) counts. */
    internal const val EPS = 1e-3f
}

enum class Level(val specName: String) {
    YEAR("year"), MONTH("month"), RECORDS("recordedDays"), DAILY("day");

    companion object {
        fun fromSpec(name: String) = entries.first { it.specName == name }
    }
}

enum class Axis { HORIZONTAL, VERTICAL }

object SwipeRules {
    /** Direction lock once the platform touch slop is exceeded: horizontal needs |dx| ≥ 1.3 |dy|. */
    fun axis(dx: Float, dy: Float): Axis =
        if (abs(dx) + UxSpec.EPS >= UxSpec.AXIS_LOCK_RATIO * abs(dy)) Axis.HORIZONTAL else Axis.VERTICAL

    /**
     * Release decision for a horizontally locked drag. Returns +1 (forward: finger moved left, next
     * day/week), -1 (back) or 0 (settle back). [dx] and [vx] are in dp and dp/s, [width] in dp.
     */
    fun commit(width: Float, dx: Float, vx: Float): Int {
        val dir = if (dx < 0) 1 else -1
        val distance = abs(dx)
        if (distance + UxSpec.EPS >= UxSpec.DISTANCE_COMMIT_W * width) return dir
        val sameDirection = (dx < 0 && vx < 0) || (dx > 0 && vx > 0)
        val fastEnough = abs(vx) + UxSpec.EPS >= UxSpec.FLICK_MIN_SPEED_W_PER_S * width
        val farEnough = distance + UxSpec.EPS >= UxSpec.FLICK_MIN_DISTANCE_W * width
        return if (sameDirection && fastEnough && farEnough) dir else 0
    }

    /** The whole gesture, as described by the shared cases. Returns the change in days. */
    fun decide(
        width: Float, dx: Float, dy: Float, vx: Float, stepDays: Int,
        systemEdgeBack: Boolean = false, osCancel: Boolean = false, slopExceeded: Boolean = true,
    ): Int {
        if (systemEdgeBack || osCancel || !slopExceeded) return 0
        if (axis(dx, dy) != Axis.HORIZONTAL) return 0
        return commit(width, dx, vx) * stepDays
    }
}

object PinchRules {
    /** One level per gesture, decided from the last valid two-finger scale; cancels change nothing. */
    fun decide(level: Level, lastScale: Float, osCancel: Boolean = false, abnormalEnd: Boolean = false): Level {
        if (osCancel || abnormalEnd) return level
        val step = when {
            lastScale + UxSpec.EPS / 10 >= UxSpec.ZOOM_IN_AT_OR_ABOVE -> 1
            lastScale - UxSpec.EPS / 10 <= UxSpec.ZOOM_OUT_AT_OR_BELOW -> -1
            else -> 0
        }
        return Level.entries.getOrNull(level.ordinal + step) ?: level
    }
}

/** The date or month under the first valid two-finger midpoint stays the pinch target. */
class PinchAnchor {
    var target: Any? = null
        private set

    fun hit(candidate: Any?) {
        if (target == null) target = candidate
    }
}

enum class NavEvent(val specName: String, val commits: Boolean) {
    TAP_DATE("tapDate", true),
    SELECTED_DATE_CHANGED("selectedDateChanged", true),
    LEVEL_CHANGED("levelChanged", true),
    DAY_SWIPE_COMMIT("daySwipeCommit", true),
    WEEK_SWIPE_COMMIT("weekSwipeCommit", true),
    LEVEL_COMMIT("levelCommit", true),
    DRAG_PROGRESS("dragProgress", false),
    PINCH_PROGRESS("pinchProgress", false),
    SCROLL("scroll", false),
    SCROLL_CROSSES_MONTH_OR_YEAR("scrollCrossesMonthOrYear", false),
    CANCEL("cancel", false),
    LEVEL_LIMIT("levelLimit", false),
    RESELECT("reselect", false),
    TODAY("today", false),
    RECORDED_MONTH_COMMIT("recordedDaysMonthCommit", true),
    RECORDED_MONTH_PREVIEW("recordedDaysMonthPreview", false),
    RECORDED_RAIL_COAST("recordedDaysRailCoast", false);

    companion object {
        fun fromSpec(name: String) = entries.first { it.specName == name }
    }
}

object HapticRules {
    /** One light selection tick per committed action, however many state changes it causes. */
    fun ticks(events: Collection<NavEvent>, enabled: Boolean): Int =
        if (enabled && events.any { it.commits }) 1 else 0
}

object CalendarRules {
    const val MAX_FRAGMENTS = 3

    /** Column (0-based, from the locale's first weekday) of the 1st; the month number sits above it. */
    fun firstDayColumn(month: YearMonth, firstDayOfWeek: DayOfWeek): Int =
        (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7

    /** The first three fragments in entered order, plus "+N" for the rest. */
    fun <T> preview(items: List<T>): Pair<List<T>, Int> =
        items.take(MAX_FRAGMENTS) to (items.size - MAX_FRAGMENTS).coerceAtLeast(0)
}

object TimeRules {
    private val canonical = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    fun isCanonical(value: String) = canonical.matches(value)

    fun canonical(hour: Int, minute: Int) = "%02d:%02d".format(Locale.ROOT, hour, minute)

    fun parse(value: String): LocalTime? = if (isCanonical(value)) LocalTime.parse(value) else null

    /**
     * Display a stored "HH:mm" with the device's 12/24-hour preference. [pattern12] / [pattern24] are
     * the platform's best patterns for the locale (Android: DateFormat.getBestDateTimePattern).
     */
    fun display(
        value: String, is24Hour: Boolean, locale: Locale,
        pattern12: String = "a h:mm", pattern24: String = "HH:mm",
    ): String {
        val time = parse(value) ?: return value
        return DateTimeFormatter.ofPattern(if (is24Hour) pattern24 else pattern12, locale).format(time)
    }
}

object EmojiRules {
    private const val ZWJ = 0x200D
    private const val VS15 = 0xFE0E
    private const val VS16 = 0xFE0F
    private const val KEYCAP = 0x20E3

    private fun isRegional(cp: Int) = cp in 0x1F1E6..0x1F1FF
    private fun isKeycapBase(cp: Int) = cp in '0'.code..'9'.code || cp == '#'.code || cp == '*'.code
    private fun isTag(cp: Int) = cp in 0xE0020..0xE007F

    /**
     * Valid emoji sequences in [text], in order (UTS #51: flags, keycaps, presentation sequences,
     * modifiers, tag sequences and ZWJ sequences). Text-style symbols such as a bare © or ⌘, digits,
     * letters and stray joiners or selectors are dropped.
     */
    fun sequences(text: String): List<String> {
        val cps = text.codePoints().toArray()
        val out = mutableListOf<String>()
        var i = 0
        fun take(from: Int, to: Int) { out += String(cps, from, to - from) }
        while (i < cps.size) {
            val cp = cps[i]
            when {
                isRegional(cp) -> {
                    if (i + 1 < cps.size && isRegional(cps[i + 1])) { take(i, i + 2); i += 2 } else i++
                }
                isKeycapBase(cp) -> {
                    when {
                        i + 2 < cps.size && cps[i + 1] == VS16 && cps[i + 2] == KEYCAP -> { take(i, i + 3); i += 3 }
                        i + 1 < cps.size && cps[i + 1] == KEYCAP -> { take(i, i + 2); i += 2 }
                        else -> i++
                    }
                }
                EmojiData.isExtendedPictographic(cp) -> {
                    val start = i
                    val (end, presented) = element(cps, i)
                    if (!presented) { i = end; continue }
                    var j = end
                    while (j + 1 < cps.size && cps[j] == ZWJ && EmojiData.isExtendedPictographic(cps[j + 1])) {
                        j = element(cps, j + 1).first
                    }
                    take(start, j)
                    i = j
                }
                EmojiData.isEmojiPresentation(cp) -> { take(i, i + 1); i++ } // e.g. a lone skin tone
                else -> i++
            }
        }
        return out
    }

    /** One pictograph with its selector, skin tone and tags; returns (end index, shown as emoji). */
    private fun element(cps: IntArray, start: Int): Pair<Int, Boolean> {
        var i = start + 1
        var presented = EmojiData.isEmojiPresentation(cps[start])
        if (i < cps.size && cps[i] == VS16) { presented = true; i++ }
        else if (i < cps.size && cps[i] == VS15) { presented = false; i++ }
        if (i < cps.size && EmojiData.isEmojiModifier(cps[i])) { presented = true; i++ }
        while (i < cps.size && isTag(cps[i])) i++
        return i to presented
    }

    fun filter(text: String): String = sequences(text).joinToString("")

    fun isAllEmoji(text: String): Boolean = text.isNotEmpty() && filter(text) == text

    data class Edit(val text: String, val selectionStart: Int, val selectionEnd: Int)

    /** While an IME composition is open (Hangul, kana…) nothing is rewritten; it is checked once committed. */
    fun fieldChange(before: Edit, after: Edit, composing: Boolean): Edit = if (composing) after else applyEdit(before, after)

    /**
     * Applies one committed (non-composing) change to the emoji field. Valid emoji stay in order;
     * a change that adds only invalid text keeps the previous text and selection untouched.
     */
    fun applyEdit(before: Edit, after: Edit): Edit {
        if (filter(after.text) == after.text) return after
        // What the change inserted: the part of `after` outside the prefix/suffix it shares with `before`.
        var prefix = 0
        val max = minOf(before.text.length, after.text.length)
        while (prefix < max && before.text[prefix] == after.text[prefix]) prefix++
        var suffix = 0
        while (suffix < max - prefix &&
            before.text[before.text.length - 1 - suffix] == after.text[after.text.length - 1 - suffix]) suffix++
        val inserted = after.text.substring(prefix, after.text.length - suffix)
        if (inserted.isNotEmpty() && filter(inserted).isEmpty()) return before
        val filtered = filter(after.text)
        val cursor = filter(after.text.substring(0, after.selectionEnd.coerceIn(0, after.text.length))).length
        return Edit(filtered, cursor, cursor)
    }
}

/**
 * 기록한 날 v0.2 · one month per page. A pure model of the committed month, the preview month while
 * the body or the rail is moving, and the selected day, so the shared cases can drive it directly
 * and the screen has one place where a month is committed (and the one haptic tick is asked for).
 */
class RecordedMonths(committed: YearMonth, selectedDay: java.time.LocalDate) {
    var committed: YearMonth = committed
        private set
    /** The month the body or rail is heading to while moving; null when at rest. */
    var preview: YearMonth? = null
        private set
    var selectedDay: java.time.LocalDate = selectedDay
        private set

    /** Header year and the TalkBack "selected" month follow the committed month only. */
    val headerYear: Int get() = committed.year
    val centered: YearMonth get() = committed

    /** Entering the view (pinch, menu, back) or reloading: no navigation event of its own. */
    fun reset(month: YearMonth, selected: java.time.LocalDate) {
        committed = month
        selectedDay = selected
        preview = null
    }

    /** Body drag: the neighbour month is a preview; header and committed month stay. */
    fun dragging(fraction: Float): List<NavEvent> {
        preview = if (fraction == 0f) null else committed.plusMonths(if (fraction < 0) 1 else -1)
        return listOf(NavEvent.DRAG_PROGRESS, NavEvent.RECORDED_MONTH_PREVIEW)
    }

    /** Body release, judged with the same swipe rules as the day view (one month at most). */
    fun bodyRelease(
        width: Float, dx: Float, dy: Float, vx: Float,
        systemEdgeBack: Boolean = false, osCancel: Boolean = false, slopExceeded: Boolean = true,
    ): List<NavEvent> {
        preview = null
        val step = SwipeRules.decide(width, dx, dy, vx, 1, systemEdgeBack, osCancel, slopExceeded)
        return if (step == 0) listOf(NavEvent.CANCEL) else commit(committed.plusMonths(step.toLong()))
    }

    /** Rail coasting: the month nearest the centre is only a preview. */
    fun railCoast(nearest: YearMonth): List<NavEvent> {
        preview = nearest
        return listOf(NavEvent.RECORDED_RAIL_COAST)
    }

    /** Rail at rest [slots] month-widths away from the committed month: snap to the nearest month. */
    fun railSettle(slots: Float): List<NavEvent> {
        preview = null
        return commit(committed.plusMonths(kotlin.math.round(slots).toLong()))
    }

    fun tap(month: YearMonth): List<NavEvent> { preview = null; return commit(month) }

    fun cancel(): List<NavEvent> { preview = null; return listOf(NavEvent.CANCEL) }

    /** Today: the month of today, today selected, silent even though the month changes. */
    fun today(today: java.time.LocalDate): List<NavEvent> {
        preview = null
        committed = YearMonth.from(today)
        selectedDay = today
        return listOf(NavEvent.TODAY)
    }

    private fun commit(month: YearMonth): List<NavEvent> {
        if (month == committed) return listOf(NavEvent.RESELECT)
        committed = month
        return listOf(NavEvent.RECORDED_MONTH_COMMIT)
    }

    companion object {
        /** Months shown either side of the centre for a rail [widthDp] wide at [fontScale]. */
        fun sideSlots(widthDp: Float, fontScale: Float): Int {
            val slot = 64f * fontScale.coerceAtLeast(1f)
            return (((widthDp / slot) - 1) / 2).toInt().coerceIn(1, 5)
        }

        fun window(center: YearMonth, side: Int): List<YearMonth> = (-side..side).map { center.plusMonths(it.toLong()) }

        /** The supported range; the rail runs across years and stops at the ends without wrapping. */
        val FIRST: YearMonth = YearMonth.of(1970, 1)
        val LAST: YearMonth = YearMonth.of(2099, 12)
        fun index(month: YearMonth): Int = ((month.year - FIRST.year) * 12 + month.monthValue - 1)
        fun at(index: Int): YearMonth = FIRST.plusMonths(index.toLong())
        val COUNT: Int = index(LAST) + 1
    }
}
