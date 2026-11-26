package com.tokipin.app

import com.tokipin.app.rules.CalendarFolding
import com.tokipin.app.rules.CalendarRange
import com.tokipin.app.rules.MonthRow
import com.tokipin.app.rules.YearRow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth

/** Tokipin-Calendar-Range checks against the shared fixture, with today pinned to 2026-11-26. */
class CalendarRangeTest {
    private val today = LocalDate.of(2026, 11, 26)
    private val fixture = File("src/debug/assets/calendar-range-records.json")
    private val days: Map<LocalDate, List<String>> by lazy {
        Json.parseToJsonElement(fixture.readText()).jsonObject["days"]!!.jsonArray.associate { d ->
            LocalDate.parse(d.jsonObject["date"]!!.jsonPrimitive.content) to
                d.jsonObject["items"]!!.jsonArray.map { it.jsonObject["value"]!!.jsonPrimitive.content }
        }
    }
    private val range by lazy { CalendarRange.of(days.keys, today) }
    private val months by lazy { days.keys.map(YearMonth::from).toSet() }
    private fun ym(s: String) = YearMonth.parse(s)

    @Test fun fixtureIsTheSharedFile() {
        val sha = MessageDigest.getInstance("SHA-256").digest(fixture.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals("530bfab851281991149abe48d5f4b083d3608d3cd180e5ea53b3e79930b88744", sha)
        assertEquals(512, days.size)
        assertEquals(1632, days.values.sumOf { it.size })
    }

    @Test fun emptyInstallShowsTheLast12MonthsAndWritesUntilMonthEnd() {
        val empty = CalendarRange.of(emptyList(), today)
        assertEquals(ym("2025-12"), empty.first)
        assertEquals(ym("2026-11"), empty.last)
        assertTrue(empty.canWrite(LocalDate.of(2026, 11, 30)))
        assertFalse(empty.canWrite(LocalDate.of(2026, 12, 1)))
        val rows = CalendarFolding.months(empty, emptySet(), emptySet())
        assertEquals(12, rows.size)
        assertTrue(rows.all { it is MonthRow.Month })
    }

    @Test fun fixtureStartsAt201705WithTheTwoAnchorRecords() {
        assertEquals(ym("2017-05"), range.first)
        assertEquals(listOf("바닷가"), days[LocalDate.of(2017, 5, 14)])
        assertEquals(listOf("책방"), days[LocalDate.of(2023, 4, 8)])
        val gap = CalendarFolding.months(range, months, emptySet()).filterIsInstance<MonthRow.Gap>().first()
        assertEquals(ym("2017-06"), gap.from)
        assertEquals(ym("2023-03"), gap.to)
        assertEquals(70, gap.months)
    }

    @Test fun fiveEmptyMonthsStayOpenSixFoldAndCanBeOpened() {
        val rows = CalendarFolding.months(range, months, emptySet())
        val shown = rows.filterIsInstance<MonthRow.Month>().map { it.month }
        (2..6).forEach { assertTrue("2024.0$it shown", ym("2024-0$it") in shown) }
        val gap = rows.filterIsInstance<MonthRow.Gap>().first { it.from == ym("2024-08") }
        assertEquals(ym("2025-01"), gap.to)
        assertTrue(ym("2024-09") !in shown)
        val opened = CalendarFolding.months(range, months, setOf(ym("2024-08")))
        assertTrue(opened.any { it is MonthRow.Month && it.month == ym("2024-08") })
        assertTrue(opened.any { it is MonthRow.Month && it.month == ym("2025-01") })
        assertTrue(opened.any { it is MonthRow.Gap && it.from == ym("2024-08") && it.expanded })
    }

    @Test fun recentTwelveMonthsAreAlwaysWhole() {
        val shown = CalendarFolding.months(range, months, emptySet()).filterIsInstance<MonthRow.Month>().map { it.month }
        generateSequence(ym("2025-12")) { it.plusMonths(1) }.takeWhile { it <= ym("2026-11") }.forEach { assertTrue("$it", it in shown) }
        assertTrue("2026.10 is empty but shown", ym("2026-10") !in months && ym("2026-10") in shown)
        assertEquals(ym("2026-11"), shown.last())
    }

    @Test fun yearsFold2018To2022AndKeepTheRest() {
        val years = days.keys.map { it.year }.toSet()
        val rows = CalendarFolding.years(range, years, emptySet())
        assertEquals(YearRow.Gap(2018, 2022, false), rows.filterIsInstance<YearRow.Gap>().single())
        listOf(2017, 2023, 2024, 2025, 2026).forEach { y -> assertTrue("$y", rows.any { it is YearRow.Year && it.year == y }) }
        assertFalse("2026.12 is after the range", ym("2026-12") in range)
        assertFalse("2017.04 is before the range", ym("2017-04") in range)
    }

    @Test fun november2026RecordDays() {
        val nov = days.keys.filter { YearMonth.from(it) == ym("2026-11") }.map { it.dayOfMonth }.toSet()
        (23..26).forEach { assertTrue("$it", it in nov) }
        listOf(3, 9, 13, 16).forEach { assertFalse("$it", it in nov) }
    }

    @Test fun onlySavingAnEarlierDateExtendsTheRange() {
        val extended = CalendarRange.of(days.keys + LocalDate.of(2015, 9, 6), today)
        assertEquals(ym("2015-09"), extended.first)
        assertEquals(ym("2017-05"), CalendarRange.of(days.keys, today).first) // a draft or cancel changes nothing
    }
}
