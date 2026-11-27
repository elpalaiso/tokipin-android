package com.tokipin.app

import com.tokipin.app.data.EntryKind
import com.tokipin.app.data.Journal
import com.tokipin.app.data.SampleGenerator
import com.tokipin.app.ui.BrowseCue
import com.tokipin.app.ui.SampleTour
import com.tokipin.app.ui.TourSheet
import com.tokipin.app.rules.Level
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class SampleTourRulesTest {
    @Test fun quietLeapYearDateUsesFourLocalizedStarterFragmentsOnlyInTheDraft() {
        val words = Json.decodeFromString<Map<String, String>>(File("src/main/res/raw-de/sample_words.json").readText())
        val end = LocalDate.of(2024, 2, 29)
        val quiet = (0L..9L).map(end::minusDays).first { !SampleGenerator.hasSample(it.toEpochDay()) }
        val journal = SampleGenerator.journal(quiet, words)
        assertTrue(journal.itemsOn(quiet).isEmpty())
        val starter = SampleGenerator.itemsOn(quiet, words, starter = true)
        assertEquals(listOf(EntryKind.WORD, EntryKind.EMOJI, EntryKind.PLACE, EntryKind.TIME), starter.map { it.kind })
        assertTrue(starter.first().value in words.values)
        assertTrue(starter[2].value in words.values)
        assertTrue(journal.itemsOn(quiet).isEmpty())
        assertTrue(journal.dates.size > 850)
    }

    @Test fun incompleteImeCompositionOffersInputHelpAndKeepsAValidEmojiReadyForSave() {
        val tour = SampleTour(Journal(), emptyList(), emptyMap())
        val d = com.tokipin.app.data.Draft("2026-11-27", kind = EntryKind.EMOJI, raw = "ㅎ")
        tour.chooseTool()
        tour.composing = true
        assertEquals(R.string.editor_emoji_prompt, tour.editorCue(d))
        assertEquals(R.string.editor_emoji_prompt, tour.editorCue(d.copy(raw = "✨ㅎ")))
        tour.composing = false
        assertEquals(R.string.editor_emoji_prompt, tour.editorCue(d))
        assertEquals(R.string.onboarding_save_guide, tour.editorCue(d.copy(raw = "✨")))
    }

    @Test fun freeExplorationAndDuplicateGesturesNeverCompleteTheGuideTwice() {
        val tour = SampleTour(Journal(), emptyList(), emptyMap())
        repeat(2) { tour.advance(BrowseCue.DaySwipe, BrowseCue.DayZoom) }
        assertEquals(BrowseCue.DayZoom, tour.cue)
        tour.enteredLevel(Level.YEAR, Level.MONTH)
        tour.enteredLevel(Level.MONTH, Level.DAILY)
        assertNull(tour.sheet) // Changing the view from its menu does not finish the tutorial.
        tour.invite()
        assertEquals(TourSheet.Invitation, tour.sheet)
        tour.explore()
        tour.enteredLevel(Level.YEAR, Level.MONTH)
        tour.enteredLevel(Level.MONTH, Level.DAILY)
        assertNull(tour.sheet)
        assertFalse(tour.guided)
    }
}
