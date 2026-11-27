package com.tokipin.app

import com.tokipin.app.ui.SampleCalendar
import com.tokipin.app.ui.SamplePage
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SampleCalendarRulesTest {
    @Test fun leapMonthReservesTheLastDayAndStopsWhenAllPracticeDaysAreFull() {
        val sample = SampleCalendar(LocalDate.of(2024, 3, 31))
        sample.navigate(sample.adjacent(-1)!!)
        val february = YearMonth.of(2024, 2)
        assertEquals(february, sample.month)
        val saved = mutableSetOf<LocalDate>()
        sample.openDay(sample.featured(february))
        while (sample.canPractice()) {
            sample.startPractice()
            if (saved.isEmpty()) assertEquals(february.atEndOfMonth(), sample.practiceDay)
            assertTrue(saved.add(sample.practiceDay))
            sample.select("🌱")
            sample.savePractice()
            sample.openDay(sample.featured(february))
        }
        sample.startPractice()
        assertEquals(SamplePage.Day, sample.page)
        assertEquals(8, saved.size)
    }

    @Test fun highlightedDateAndReservedPracticeNeverCoincideAcrossTheYearBoundary() {
        val sample = SampleCalendar(LocalDate.of(2026, 1, 15))
        repeat(3) {
            val featured = sample.featured(sample.month)
            assertEquals(14, featured.dayOfMonth)
            sample.openDay(featured)
            sample.startPractice()
            assertEquals(15, sample.practiceDay.dayOfMonth)
            sample.back()
            sample.back()
            sample.adjacent(-1)?.let(sample::navigate)
        }
        assertNull(sample.adjacent(-1))
        assertEquals(YearMonth.of(2025, 11), sample.month)
    }
}
