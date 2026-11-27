package com.tokipin.app.ui

import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tokipin.app.R
import com.tokipin.app.data.Entry
import com.tokipin.app.data.EntryKind
import java.time.LocalDate
import java.time.YearMonth

internal enum class SamplePage { Month, Day, Practice }

/** A single in-memory demonstration. It has no reference to the personal journal, its storage, or analytics. */
internal class SampleCalendar(val today: LocalDate) {
    private val lastMonth = YearMonth.from(today)
    private val firstMonth = lastMonth.minusMonths(2)
    var month by mutableStateOf(lastMonth); private set
    var day by mutableStateOf(featured(month)); private set
    var practiceDay by mutableStateOf(today); private set
    var page by mutableStateOf(SamplePage.Month); private set
    var showGuide by mutableStateOf(true); private set
    var choice by mutableStateOf<String?>(null); private set
    var savedDay by mutableStateOf<LocalDate?>(null); private set
    var showReceipt by mutableStateOf(false); private set

    private val added = mutableStateMapOf<LocalDate, String>()
    private fun reserved(m: YearMonth) = m.atDay(minOf(today.dayOfMonth, m.lengthOfMonth()))
    fun featured(m: YearMonth) = m.atDay(if (reserved(m).dayOfMonth == 15) 14 else 15)

    private fun seed(date: LocalDate): Seed? {
        val m = YearMonth.from(date)
        if (date == reserved(m)) return null
        if (date == featured(m)) return pottery
        val index = filledDays.indexOf(date.dayOfMonth)
        return if (index < 0) null else seeds[index % seeds.size]
    }

    fun entries(date: LocalDate, resources: Resources): List<Entry> {
        val seed = seed(date)
        if (seed != null) return listOf(
            Entry("sample-word", EntryKind.WORD, resources.getString(seed.word)),
            Entry("sample-place", EntryKind.PLACE, resources.getString(seed.place)),
            Entry("sample-emoji", EntryKind.EMOJI, seed.emoji),
        )
        return added[date]?.let { listOf(Entry("sample-practice", EntryKind.EMOJI, it)) }.orEmpty()
    }

    private fun empty(date: LocalDate) = seed(date) == null && date !in added

    private fun availableDays(m: YearMonth) = (1..m.lengthOfMonth()).map(m::atDay).filter(::empty)

    fun adjacent(offset: Int): LocalDate? = when (page) {
        SamplePage.Month -> month.plusMonths(offset.toLong()).takeIf { it in firstMonth..lastMonth }?.atDay(1)
        SamplePage.Day -> day.plusDays(offset.toLong()).takeIf { YearMonth.from(it) in firstMonth..lastMonth }
        SamplePage.Practice -> availableDays(month).let { it.getOrNull(it.indexOf(practiceDay) + offset) }
    }

    fun navigate(target: LocalDate) {
        when (page) {
            SamplePage.Month -> { month = YearMonth.from(target); showReceipt = false }
            SamplePage.Day -> openDay(target)
            SamplePage.Practice -> { practiceDay = target; choice = null }
        }
    }

    fun openDay(date: LocalDate) {
        month = YearMonth.from(date)
        day = date
        page = SamplePage.Day
        showReceipt = false
    }

    fun canPractice() = availableDays(month).isNotEmpty()

    fun startPractice() {
        val target = day.takeIf(::empty) ?: reserved(month).takeIf(::empty) ?: availableDays(month).firstOrNull() ?: return
        practiceDay = target
        choice = null
        page = SamplePage.Practice
    }

    fun select(emoji: String) { choice = emoji }

    fun savePractice() {
        val emoji = choice ?: return
        if (page != SamplePage.Practice) return
        added[practiceDay] = emoji
        savedDay = practiceDay
        choice = null
        page = SamplePage.Month
        showGuide = false
        showReceipt = true
    }

    fun back() {
        page = if (page == SamplePage.Practice) SamplePage.Day else SamplePage.Month
        choice = null
    }

    fun dismissGuide() { showGuide = false }
    fun clearReceipt() { showReceipt = false }

    private data class Seed(val word: Int, val place: Int, val emoji: String)
    companion object {
        val choices = listOf("🌱", "☕", "🌧️", "✨")
        private val filledDays = listOf(1, 3, 5, 7, 9, 11, 13, 17, 19, 21, 23, 25, 27)
        private val pottery = Seed(R.string.sample_pottery, R.string.sample_studio, "🏺")
        private val seeds = listOf(
            Seed(R.string.sample_walk, R.string.sample_park, "🌿"),
            Seed(R.string.sample_coffee, R.string.sample_cafe, "☕"),
            Seed(R.string.sample_reading, R.string.sample_library, "📚"),
            Seed(R.string.sample_cycling, R.string.sample_trail, "🚲"),
            Seed(R.string.sample_cooking, R.string.sample_kitchen, "🍲"),
        )
    }
}
