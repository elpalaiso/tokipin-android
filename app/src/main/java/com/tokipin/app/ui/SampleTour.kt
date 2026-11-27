package com.tokipin.app.ui

import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tokipin.app.R
import com.tokipin.app.data.Draft
import com.tokipin.app.data.Entry
import com.tokipin.app.data.EntryKind
import com.tokipin.app.data.Journal
import com.tokipin.app.data.SampleGenerator
import com.tokipin.app.rules.Level
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth

internal enum class BrowseCue(val message: Int) {
    DaySwipe(R.string.onboarding_day_swipe), DayZoom(R.string.onboarding_day_zoom),
    RecordsSwipe(R.string.onboarding_records_swipe), RecordsZoom(R.string.onboarding_records_zoom),
    MonthScroll(R.string.onboarding_month_scroll), MonthZoom(R.string.onboarding_month_zoom),
    YearOpen(R.string.onboarding_year_open), PickDay(R.string.onboarding_open_day),
}

internal enum class TourSheet { ExitOptions, Invitation, PreviewUsage }

/** Cues observe the native screen's committed actions. The guided fallback uses the same actions. */
internal class SampleTour(
    val seed: Journal,
    val starter: List<Entry>,
    private val words: Map<String, String>,
) {
    var guided by mutableStateOf(true); private set
    var enteredTool by mutableStateOf(false); private set
    var composing by mutableStateOf(false)
    var displayedTime: String? = null
    private var committedInput by mutableStateOf(false)
    fun chooseTool() { enteredTool = true; committedInput = false; composing = false }
    fun resetEditor() { enteredTool = false; committedInput = false; composing = false }
    fun committed() { committedInput = true }
    var cue by mutableStateOf(BrowseCue.DaySwipe); private set
    var sheet by mutableStateOf<TourSheet?>(null); private set
    var receipt by mutableIntStateOf(0); private set
    private var savedOnce = false
    var scrollTarget by mutableStateOf<YearMonth?>(null); private set

    fun editorCue(draft: Draft): Int = when {
        draft.kind == EntryKind.TIME && draft.raw.isNotBlank() -> R.string.onboarding_add_time
        !composing && (draft.raw.isNotBlank() || committedInput || (!enteredTool && draft.changed)) &&
            (draft.kind != EntryKind.EMOJI || draft.raw.isBlank() || com.tokipin.app.rules.EmojiRules.filter(draft.raw).isNotEmpty()) -> R.string.onboarding_save_guide
        composing && draft.kind == EntryKind.WORD -> R.string.onboarding_write_word
        draft.kind == EntryKind.WORD && !enteredTool -> R.string.onboarding_emoji_tool
        draft.kind == EntryKind.EMOJI -> R.string.editor_emoji_prompt
        draft.kind == EntryKind.PLACE -> R.string.onboarding_write_place
        draft.kind == EntryKind.TIME -> R.string.onboarding_add_time
        else -> R.string.onboarding_write_word
    }

    fun currentCue(vm: AppViewModel): Int =
        if (vm.screen == Screen.Editor) vm.draft?.let(::editorCue) ?: 0 else cue.message

    fun advance(expected: BrowseCue, next: BrowseCue) {
        if (guided && cue == expected && sheet == null) cue = next
    }

    fun enteredLevel(from: Level, to: Level) {
        scrollTarget = null
        if (!guided) return
        if (from == Level.YEAR && to == Level.MONTH) {
            cue = BrowseCue.PickDay
        } else replayBrowse(to)
    }

    fun replayBrowse(level: Level) {
        cue = when (level) {
            Level.DAILY -> BrowseCue.DaySwipe
            Level.RECORDS -> BrowseCue.RecordsSwipe
            Level.MONTH -> BrowseCue.MonthScroll
            Level.YEAR -> BrowseCue.YearOpen
        }
    }

    fun replay(vm: AppViewModel) {
        if (vm.screen == Screen.Search) vm.closeSearch()
        guided = true
        resetEditor()
        replayBrowse(vm.level)
    }

    fun didSave(nonempty: Boolean) {
        receipt = when {
            !nonempty -> R.string.onboarding_record_cleared
            !savedOnce -> R.string.onboarding_first_save
            else -> R.string.onboarding_record_saved
        }
        if (nonempty) savedOnce = true
        replayBrowse(Level.DAILY)
    }

    fun clearReceipt() { receipt = 0 }
    fun exitOptions() { sheet = TourSheet.ExitOptions }
    fun cancelOptions() { sheet = null }
    fun explore() { sheet = null; guided = false; scrollTarget = null }
    fun previewUsage() { sheet = TourSheet.PreviewUsage }
    fun invite() { sheet = TourSheet.Invitation; scrollTarget = null }

    fun scrolled() {
        advance(BrowseCue.MonthScroll, BrowseCue.MonthZoom)
        scrollTarget = null
    }

    fun next(vm: AppViewModel) {
        if (!guided || sheet != null) return
        if (vm.screen == Screen.Search) vm.closeSearch()
        if (vm.screen == Screen.Editor) {
            val draft = vm.draft ?: return
            when (editorCue(draft)) {
                R.string.onboarding_emoji_tool -> vm.setKind(EntryKind.EMOJI)
                R.string.onboarding_save_guide -> vm.saveDraft()
                R.string.onboarding_add_time -> {
                    if (draft.raw.isBlank()) vm.updateRaw(displayedTime ?: return)
                    vm.commitRaw()
                }
                else -> if (draft.raw.isBlank()) vm.updateRaw(when (draft.kind) {
                    EntryKind.EMOJI -> "🌱"
                    EntryKind.PLACE -> words.getValue("park")
                    else -> words.getValue("walk")
                })
            }
            return
        }
        when (cue) {
            BrowseCue.DaySwipe -> vm.selectDate(if (vm.selectedDate <= vm.range.first.atDay(1)) vm.selectedDate.plusDays(1) else vm.selectedDate.minusDays(1))
            BrowseCue.DayZoom -> vm.pinchTo(Level.RECORDS, vm.selectedDate, null)
            BrowseCue.RecordsSwipe -> if (vm.visibleRecordMonths.size > 1) {
                vm.recorded { step(if (vm.recordedMonth == vm.visibleRecordMonths.first()) 1 else -1) }
            } else advance(BrowseCue.RecordsSwipe, BrowseCue.RecordsZoom)
            BrowseCue.RecordsZoom -> vm.pinchTo(Level.MONTH, vm.recordedMonth, null)
            BrowseCue.MonthScroll -> {
                val month = vm.visibleMonth
                scrollTarget = if (month > vm.range.first) month.minusMonths(1) else month.plusMonths(1)
            }
            BrowseCue.MonthZoom -> vm.pinchTo(Level.YEAR, vm.visibleMonth, null)
            BrowseCue.YearOpen -> vm.openMonth(YearMonth.from(vm.selectedDate))
            BrowseCue.PickDay -> {
                val m = vm.visibleMonth
                vm.openDay(if (YearMonth.from(vm.selectedDate) == m) vm.selectedDate else vm.journal.datesIn(m).firstOrNull() ?: m.atDay(1))
            }
        }
    }

    companion object {
        fun create(today: LocalDate, resources: Resources): SampleTour {
            val words = resources.openRawResource(R.raw.sample_words).bufferedReader().use { Json.decodeFromString<Map<String, String>>(it.readText()) }
            val journal = SampleGenerator.journal(today, words).warmed()
            val starter = journal.itemsOn(today).ifEmpty { SampleGenerator.itemsOn(today, words, starter = true) }
            return SampleTour(journal, starter, words)
        }
    }
}
