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
    const val VERSION = "0.3"

    // Swipe (W = usable width of the gesture area, in dp)
    const val AXIS_LOCK_RATIO = 1.3f
    const val DISTANCE_COMMIT_W = 0.22f
    const val FLICK_MIN_DISTANCE_W = 0.06f
    const val FLICK_MIN_SPEED_W_PER_S = 0.9f
    const val DAY_STEP = 1
    const val WEEK_STEP = 7
    /** v0.3 swipe.stepsInVisibleMonthSlots.recordedDaysBody: one slot of the visible-month list. */
    const val RECORDED_SLOT_STEP = 1
    /** The small year above a rail month is part of that slot's TalkBack node, not its own. */
    const val RAIL_HINT_MERGED_INTO_SLOT = true

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
    /** Editor: a fragment picked up by a long press (one tick; none while passing places). */
    FRAGMENT_PICK_UP("fragmentPickUp", true),
    RECORDED_MONTH_PREVIEW("recordedDaysMonthPreview", false),
    RECORDED_RAIL_COAST("recordedDaysRailCoast", false),
    /** v0.3: the visible-month list was recalculated (data, month boundary); silent. */
    RECORDED_DATA_RECONCILIATION("recordedDaysDataReconciliation", false),
    RECORDED_HINT_CHANGE("recordedDaysHintChange", false);

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
 * 기록한 날 v0.3 · one page per month in the visible-month list: the months (up to the current
 * month) that hold at least one saved fragment, plus the current month. A pure model of the
 * committed month, the preview month while the body or the rail is moving, and the selected day,
 * so the shared cases can drive it directly and the screen has one place where a month is
 * committed (and the one haptic tick is asked for). Neighbours are list slots, not calendar months.
 */
class RecordedMonths(
    committed: YearMonth,
    selectedDay: java.time.LocalDate,
    months: List<YearMonth> = listOf(committed),
) {
    /** The visible-month list, sorted and unique; never empty. */
    var months: List<YearMonth> = months.ifEmpty { listOf(committed) }
        private set
    var committed: YearMonth = nearest(this.months, committed)
        private set
    /** The month the body or rail is heading to while moving; null when at rest. */
    var preview: YearMonth? = null
        private set
    var selectedDay: java.time.LocalDate = selectedDay
        private set

    /** Header year and the TalkBack "selected" month follow the committed month only. */
    val headerYear: Int get() = committed.year
    val centered: YearMonth get() = committed
    val index: Int get() = months.indexOf(committed)

    /** The month [dir] slots away in the list, or null past either end. */
    fun neighbor(dir: Int): YearMonth? = months.getOrNull(index + dir)

    /** Entering the view (pinch, menu, back) or reloading: no navigation event of its own. */
    fun reset(month: YearMonth, selected: java.time.LocalDate, list: List<YearMonth> = months) {
        months = list.ifEmpty { listOf(month) }
        committed = nearest(months, month)
        selectedDay = selected
        preview = null
    }

    /**
     * Data or the month boundary changed: keep the committed month if it is still listed, else the
     * nearest listed month (ties go to the more recent one). Silent; the selected day stays.
     */
    fun refresh(list: List<YearMonth>): List<NavEvent> {
        months = list.ifEmpty { listOf(committed) }
        committed = nearest(months, committed)
        preview = null
        return listOf(NavEvent.RECORDED_DATA_RECONCILIATION)
    }

    /** Entering with a month that is not listed (e.g. pinch from an empty past month). Silent. */
    fun resolve(requested: YearMonth): List<NavEvent> {
        committed = nearest(months, requested)
        preview = null
        return listOf(NavEvent.RECORDED_DATA_RECONCILIATION)
    }

    /** Body drag: the neighbour slot is a preview; header and committed month stay. */
    fun dragging(fraction: Float): List<NavEvent> {
        preview = if (fraction == 0f) null else neighbor(if (fraction < 0) 1 else -1)
        return listOf(NavEvent.DRAG_PROGRESS, NavEvent.RECORDED_MONTH_PREVIEW)
    }

    /** Body release, judged with the same swipe rules as the day view (one slot at most). */
    fun bodyRelease(
        width: Float, dx: Float, dy: Float, vx: Float,
        systemEdgeBack: Boolean = false, osCancel: Boolean = false, slopExceeded: Boolean = true,
    ): List<NavEvent> {
        preview = null
        val step = SwipeRules.decide(width, dx, dy, vx, UxSpec.RECORDED_SLOT_STEP, systemEdgeBack, osCancel, slopExceeded)
        return step(step)
    }

    /** One body step of [dir] slots (the pager's commit); at either end nothing moves. */
    fun step(dir: Int): List<NavEvent> {
        preview = null
        if (dir == 0) return listOf(NavEvent.CANCEL)
        val next = neighbor(dir) ?: return listOf(NavEvent.LEVEL_LIMIT)
        return commit(next)
    }

    /** Rail coasting: the month nearest the centre is only a preview. */
    fun railCoast(nearest: YearMonth): List<NavEvent> {
        preview = nearest
        return listOf(NavEvent.RECORDED_RAIL_COAST)
    }

    /** Rail at rest [slots] slot-widths away from the committed month: snap to the nearest slot. */
    fun railSettle(slots: Float): List<NavEvent> {
        preview = null
        val i = (index + kotlin.math.round(slots).toInt()).coerceIn(0, months.lastIndex)
        return commit(months[i])
    }

    /** Tapping a listed month; empty padding slots have no month and cannot be tapped. */
    fun tap(month: YearMonth): List<NavEvent> {
        preview = null
        if (month !in months) return listOf(NavEvent.CANCEL)
        return commit(month)
    }

    fun cancel(): List<NavEvent> { preview = null; return listOf(NavEvent.CANCEL) }

    /** Today: the month of today (always listed), today selected, silent even though the month changes. */
    fun today(today: java.time.LocalDate): List<NavEvent> {
        preview = null
        val month = YearMonth.from(today)
        if (month !in months) months = (months + month).distinct().sorted()
        committed = month
        selectedDay = today
        return listOf(NavEvent.TODAY)
    }

    /** The rail's slots around the committed month; null is an empty padding slot past an end. */
    fun window(side: Int): List<YearMonth?> = (-side..side).map { months.getOrNull(index + it) }

    /** Small year hints over [window]: see [Companion.hints]. */
    fun hints(side: Int): List<String?> = hints(window(side), committed)

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

        /**
         * Months with a saved fragment, up to the device's current month, plus the current month;
         * sorted, each once. Draft-only, empty and future months are not listed.
         */
        fun visibleMonths(recordDates: Collection<java.time.LocalDate>, today: java.time.LocalDate): List<YearMonth> {
            val current = YearMonth.from(today)
            return (recordDates.map(YearMonth::from).filter { it <= current } + current).distinct().sorted()
        }

        /** [requested] if listed; otherwise the listed month fewest calendar months away, ties to the more recent. */
        fun nearest(months: List<YearMonth>, requested: YearMonth): YearMonth {
            if (months.isEmpty() || requested in months) return requested
            fun distance(m: YearMonth) = kotlin.math.abs((m.year - requested.year) * 12L + m.monthValue - requested.monthValue)
            return months.minWith(compareBy<YearMonth> { distance(it) }.thenByDescending { it })
        }

        /**
         * The rail's hints over the whole list, keyed to the committed month: per other year, the
         * slot of that year nearest the committed slot. Inside the visible window this is exactly
         * [hints]; slots scrolled into view by hand keep the same answer until a month settles.
         */
        fun listHints(months: List<YearMonth>, committed: YearMonth): List<String?> {
            val center = months.indexOf(committed).coerceAtLeast(0)
            val chosen = months.withIndex()
                .filter { (_, m) -> m.year != committed.year }
                .groupBy { (_, m) -> m.year }
                .mapValues { (_, slots) -> slots.minBy { kotlin.math.abs(it.index - center) }.index }
                .entries.associate { (year, i) -> i to year.toString() }
            return months.indices.map { chosen[it] }
        }

        /**
         * For each year in [window] other than the committed month's, its slot nearest the centre
         * carries that year as a small hint above the month number; every other slot has none.
         */
        fun hints(window: List<YearMonth?>, committed: YearMonth): List<String?> {
            val center = window.size / 2
            val chosen = window.withIndex()
                .filter { (_, m) -> m != null && m.year != committed.year }
                .groupBy { (_, m) -> m!!.year }
                .mapValues { (_, slots) -> slots.minBy { kotlin.math.abs(it.index - center) }.index }
            return window.indices.map { i -> chosen.entries.firstOrNull { it.value == i }?.key?.toString() }
        }
    }
}

/**
 * v0.3 date headers: what the centred title of each view says. Numbers only, like the design
 * (TalkBack gets a localized sentence from the screen). Calendar years, never week-based years.
 */
object DateHeaders {
    /** Day view: the selected date's year and month, "2017.05". */
    fun day(selected: java.time.LocalDate): String = "%04d.%02d".format(java.util.Locale.ROOT, selected.year, selected.monthValue)

    /** 기록한 날: the committed month's year only; the rail shows the month. */
    fun recordedDays(committed: YearMonth): String = committed.year.toString()

    /** Month calendar: the year of the month in view. */
    fun month(visible: YearMonth): String = visible.year.toString()

    /** Year calendar: no global title; each 12-month block carries its year at its leading edge. */
    @Suppress("UNUSED_PARAMETER")
    fun year(block: Int): String? = null

    /** Editor: the draft's whole date, followed by the ⌄ of the date picker on screen. */
    fun editor(draft: java.time.LocalDate): String =
        "%04d.%02d.%02d".format(java.util.Locale.ROOT, draft.year, draft.monthValue, draft.dayOfMonth)

    /** The title sits on the screen's centre line, whatever the buttons on either side take. */
    @Suppress("UNUSED_PARAMETER")
    fun titleCenterX(width: Float, leadingActions: Float, trailingActions: Float): Float = width / 2
}
