package com.tokipin.app.rules

import java.time.LocalDate
import java.time.YearMonth

/**
 * The device's local date, in one place so tests can pin it (the calendar-range checks use
 * 2026-11-26) and the app re-reads it when it comes back from the background.
 */
object AppClock {
    @Volatile var override: LocalDate? = null
    fun today(): LocalDate = override ?: LocalDate.now()
    fun thisMonth(): YearMonth = YearMonth.from(today())
}

/**
 * Calendar range (Tokipin-Calendar-Range): the current month is the last month, and the first
 * month is the earlier of 11 months ago and the oldest saved record's month. Only saved records
 * move the start; opening the date picker, a draft or a cancelled choice never do.
 */
data class CalendarRange(val first: YearMonth, val last: YearMonth) {
    operator fun contains(month: YearMonth) = month in first..last

    /** New records can be written up to the last day of the current month. */
    fun canWrite(date: LocalDate) = !YearMonth.from(date).isAfter(last)

    /** The most recent 12 months, always shown in full. */
    val protectedFrom: YearMonth get() = last.minusMonths(11)

    companion object {
        const val COLLAPSE_MONTHS_AT_LEAST = 6

        fun of(recorded: Collection<LocalDate>, today: LocalDate): CalendarRange {
            val last = YearMonth.from(today)
            val recent = last.minusMonths(11)
            val oldest = recorded.minOrNull()?.let { YearMonth.from(it) }
            return CalendarRange(if (oldest != null && oldest < recent) oldest else recent, last)
        }
    }
}

sealed interface MonthRow {
    data class Month(val month: YearMonth) : MonthRow
    /** Consecutive empty months shown as one row; [expanded] shows the months themselves. */
    data class Gap(val from: YearMonth, val to: YearMonth, val expanded: Boolean) : MonthRow {
        val months: Int get() = (to.year - from.year) * 12 + to.monthValue - from.monthValue + 1
    }
}

sealed interface YearRow {
    data class Year(val year: Int) : YearRow
    data class Gap(val from: Int, val to: Int, val expanded: Boolean) : YearRow
}

object CalendarFolding {
    /**
     * Month calendar rows from the first to the last month. Before the protected recent 12 months,
     * a run of 6 or more empty months becomes one [MonthRow.Gap] (only the part before the
     * protected range counts); shorter runs stay open. [expanded] holds the start months of gaps
     * the person opened; an expanded gap is followed by its months.
     */
    fun months(range: CalendarRange, recordedMonths: Set<YearMonth>, expanded: Set<YearMonth>): List<MonthRow> {
        val rows = mutableListOf<MonthRow>()
        var m = range.first
        while (m <= range.last) {
            if (m < range.protectedFrom && m !in recordedMonths) {
                var end = m
                while (end.plusMonths(1) < range.protectedFrom && end.plusMonths(1) !in recordedMonths) end = end.plusMonths(1)
                val gap = MonthRow.Gap(m, end, m in expanded)
                if (gap.months >= CalendarRange.COLLAPSE_MONTHS_AT_LEAST) {
                    rows += gap
                    if (gap.expanded) generateSequence(m) { it.plusMonths(1) }.takeWhile { it <= end }.forEach { rows += MonthRow.Month(it) }
                } else {
                    generateSequence(m) { it.plusMonths(1) }.takeWhile { it <= end }.forEach { rows += MonthRow.Month(it) }
                }
                m = end.plusMonths(1)
                continue
            }
            rows += MonthRow.Month(m)
            m = m.plusMonths(1)
        }
        return rows
    }

    /** The gap (by its start month) that hides [month], if any. */
    fun gapHolding(range: CalendarRange, recordedMonths: Set<YearMonth>, month: YearMonth): YearMonth? =
        months(range, recordedMonths, emptySet()).filterIsInstance<MonthRow.Gap>().firstOrNull { month in it.from..it.to }?.from

    /**
     * Year blocks from the first to the last year. Years overlapping the recent 12 months or with
     * any record stay open; whole past years with no record between them become one gap row.
     */
    fun years(range: CalendarRange, recordedYears: Set<Int>, expanded: Set<Int>): List<YearRow> {
        val rows = mutableListOf<YearRow>()
        var y = range.first.year
        val keep = { year: Int -> year in recordedYears || year >= range.protectedFrom.year || year == range.first.year }
        while (y <= range.last.year) {
            if (keep(y)) { rows += YearRow.Year(y); y++; continue }
            var end = y
            while (end + 1 <= range.last.year && !keep(end + 1)) end++
            val gap = YearRow.Gap(y, end, y in expanded)
            rows += gap
            if (gap.expanded) (y..end).forEach { rows += YearRow.Year(it) }
            y = end + 1
        }
        return rows
    }
}
