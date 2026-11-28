package com.tokipin.app.rules

import java.time.LocalDate
import java.time.YearMonth

/**
 * UX v0.4 wide-screen and folding-screen rules, ported from the shared reference
 * (../tokipin/native-spec/v0.4/adaptive-layout.cjs) and checked against the same JSON cases.
 * Lengths are dp of the app window (never reduced by the keyboard).
 */
object Adaptive {
    const val TWO_PANE_MIN_WIDTH = 600f
    const val TWO_PANE_MIN_HEIGHT = 480f
    const val REGION_MIN = 300f
    const val PANE_MAX_WIDTH = 640f
    const val YEAR_FOUR_COLUMNS_FROM = 560f

    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float) {
        val centerX get() = x + width / 2
        val right get() = x + width
        val bottom get() = y + height
    }

    enum class FoldOrientation { VERTICAL, HORIZONTAL }

    /** A fold in window coordinates: x of its left/right edges (vertical) or y of its top/bottom (horizontal). */
    data class Fold(val orientation: FoldOrientation, val start: Float, val end: Float, val separating: Boolean)

    sealed interface Layout {
        /** One column, centred in the window or in the larger side of a separating fold. */
        data class Single(val content: Rect) : Layout
        /** Calendar on the left, day on the right, both against the split. */
        data class SideBySide(val calendar: Rect, val day: Rect) : Layout
        /** Day above a separating horizontal fold, calendar below it. */
        data class Stacked(val day: Rect, val calendar: Rect) : Layout
    }

    private enum class Align { START, CENTER, END }

    private fun fit(region: Rect, align: Align): Rect {
        val width = minOf(PANE_MAX_WIDTH, region.width)
        val free = region.width - width
        val x = region.x + when (align) { Align.START -> 0f; Align.END -> free; Align.CENTER -> free / 2 }
        return Rect(x, region.y, width, region.height)
    }

    fun layout(width: Float, height: Float, fold: Fold? = null, journalEmpty: Boolean = false, sampleTour: Boolean = false): Layout {
        val twoPanes = !journalEmpty && !sampleTour
        fun single(region: Rect) = Layout.Single(fit(region, Align.CENTER))
        if (fold != null && fold.separating) {
            if (fold.orientation == FoldOrientation.VERTICAL) {
                val left = Rect(0f, 0f, fold.start, height)
                val right = Rect(fold.end, 0f, width - fold.end, height)
                if (twoPanes && left.width >= REGION_MIN && right.width >= REGION_MIN) {
                    return Layout.SideBySide(calendar = fit(left, Align.END), day = fit(right, Align.START))
                }
                return single(if (right.width > left.width) right else left)
            }
            val top = Rect(0f, 0f, width, fold.start)
            val bottom = Rect(0f, fold.end, width, height - fold.end)
            if (twoPanes && top.height >= REGION_MIN && bottom.height >= REGION_MIN) {
                return Layout.Stacked(day = fit(top, Align.CENTER), calendar = fit(bottom, Align.CENTER))
            }
            return single(if (bottom.height > top.height) bottom else top)
        }
        if (twoPanes && width >= TWO_PANE_MIN_WIDTH && height >= TWO_PANE_MIN_HEIGHT) {
            val half = width / 2
            return Layout.SideBySide(
                calendar = fit(Rect(0f, 0f, half, height), Align.END),
                day = fit(Rect(half, 0f, half, height), Align.START),
            )
        }
        return single(Rect(0f, 0f, width, height))
    }

    /** The level the calendar pane shows: the level itself, or the one a day was opened from. */
    fun calendarLevel(level: Level, dayReturnLevel: Level): Level = if (level == Level.DAILY) dayReturnLevel else level

    /** What a single column shows. */
    sealed interface SingleScreen {
        data class Editor(val date: LocalDate) : SingleScreen
        data object Search : SingleScreen
        data class Day(val date: LocalDate, val returnToSearchResults: Boolean) : SingleScreen
        data class Calendar(val level: Level) : SingleScreen
    }

    /** What the two panes show. [calendar] is null when the calendar pane shows the search list. */
    data class PaneScreens(val calendar: Level?, val calendarInert: Boolean, val dayIsEditor: Boolean, val dayDate: LocalDate)

    fun singleScreen(level: Level, selectedDay: LocalDate, searching: Boolean, searchHighlight: Boolean, editorDay: LocalDate?): SingleScreen = when {
        editorDay != null -> SingleScreen.Editor(editorDay)
        searching -> SingleScreen.Search
        level == Level.DAILY -> SingleScreen.Day(selectedDay, searchHighlight)
        else -> SingleScreen.Calendar(level)
    }

    fun paneScreens(level: Level, dayReturnLevel: Level, selectedDay: LocalDate, searching: Boolean, searchHighlight: Boolean, editorDay: LocalDate?) =
        PaneScreens(
            calendar = if (searching || searchHighlight) null else calendarLevel(level, dayReturnLevel),
            calendarInert = editorDay != null,
            dayIsEditor = editorDay != null,
            dayDate = editorDay ?: selectedDay,
        )

    data class PaneState(val level: Level, val dayReturnLevel: Level, val selectedDay: LocalDate, val recordedMonth: YearMonth? = null)

    sealed interface PaneAction {
        data class TapDate(val date: LocalDate) : PaneAction
        data class WeekRailTap(val date: LocalDate) : PaneAction
        data class DaySwipe(val days: Long) : PaneAction
        data class ViewMenu(val level: Level) : PaneAction
        data class TapMonth(val selectedDayAfter: LocalDate) : PaneAction
        data class RecordedMonthCommit(val month: YearMonth) : PaneAction
        data class Today(val today: LocalDate) : PaneAction
        data class Pinch(val inCalendarPane: Boolean, val scaleAtRelease: Float, val anchorDay: LocalDate?) : PaneAction
    }

    data class PaneResult(val state: PaneState, val calendarLevel: Level, val appTicks: Int)

    /**
     * One action in the two-pane layouts. `level == DAILY` only records that the day pane was used
     * last. One tick when the selected date, the calendar pane's level or the committed 기록한 날
     * month really changes; 오늘 is silent.
     */
    fun navigate(state: PaneState, action: PaneAction, hapticsEnabled: Boolean = true): PaneResult {
        var level = state.level
        var dayReturnLevel = state.dayReturnLevel
        var selectedDay = state.selectedDay
        var recordedMonth = state.recordedMonth
        val beforeCalendar = calendarLevel(level, dayReturnLevel)
        var silent = false
        fun useDayPane() { if (level != Level.DAILY) { dayReturnLevel = level; level = Level.DAILY } }
        when (action) {
            is PaneAction.TapDate -> { selectedDay = action.date; useDayPane() }
            is PaneAction.WeekRailTap -> { selectedDay = action.date; useDayPane() }
            is PaneAction.DaySwipe -> { selectedDay = selectedDay.plusDays(action.days); useDayPane() }
            is PaneAction.ViewMenu -> level = action.level
            is PaneAction.TapMonth -> { selectedDay = action.selectedDayAfter; level = Level.MONTH }
            is PaneAction.RecordedMonthCommit -> { recordedMonth = action.month; level = Level.RECORDS }
            is PaneAction.Today -> { selectedDay = action.today; silent = true }
            is PaneAction.Pinch -> if (action.inCalendarPane) {
                val step = PinchRules.decide(beforeCalendar, action.scaleAtRelease)
                if (step != beforeCalendar) {
                    action.anchorDay?.let { selectedDay = it }
                    if (step == Level.DAILY) useDayPane() else level = step
                }
            }
        }
        val after = PaneState(level, dayReturnLevel, selectedDay, recordedMonth)
        val shown = calendarLevel(level, dayReturnLevel)
        val changed = selectedDay != state.selectedDay || shown != beforeCalendar || recordedMonth != state.recordedMonth
        return PaneResult(after, shown, if (hapticsEnabled && changed && !silent) 1 else 0)
    }

    data class CalendarChrome(val back: Level?, val title: String?, val titleCenterX: Float?, val viewMenu: List<Level>)
    data class DayChrome(val title: String, val titleCenterX: Float, val more: Boolean, val editsExisting: Boolean, val opens: LocalDate, val keyboard: Boolean)

    /** The calendar pane's menu: no 일간 entry, other levels as in one column. */
    val CALENDAR_MENU = listOf(Level.RECORDS, Level.MONTH, Level.YEAR)

    fun calendarChrome(pane: Rect, level: Level, dayReturnLevel: Level, visibleMonth: YearMonth, recordedMonth: YearMonth): CalendarChrome {
        val shown = calendarLevel(level, dayReturnLevel)
        val year = when (shown) { Level.MONTH -> visibleMonth.year.toString(); Level.RECORDS -> recordedMonth.year.toString(); else -> null }
        return CalendarChrome(
            back = if (shown == Level.YEAR) null else Level.YEAR,
            title = year,
            titleCenterX = year?.let { pane.centerX },
            viewMenu = CALENDAR_MENU,
        )
    }

    fun dayChrome(pane: Rect, selectedDay: LocalDate, dayHasRecords: Boolean) = DayChrome(
        title = "%04d.%02d".format(selectedDay.year, selectedDay.monthValue),
        titleCenterX = pane.centerX,
        more = dayHasRecords,
        editsExisting = dayHasRecords,
        opens = selectedDay,
        keyboard = !dayHasRecords,
    )

    fun yearColumns(paneWidth: Float): Int = if (paneWidth >= YEAR_FOUR_COLUMNS_FROM) 4 else 3
}
