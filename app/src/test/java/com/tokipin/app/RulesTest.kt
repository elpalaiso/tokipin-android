package com.tokipin.app

import com.tokipin.app.data.Entry
import com.tokipin.app.data.EntryKind
import com.tokipin.app.data.EntryRules
import com.tokipin.app.data.Journal
import com.tokipin.app.data.JournalStore
import com.tokipin.app.ui.search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    @Test fun blankIsEmpty() = assertEquals("", EntryRules.normalized(EntryKind.WORD, "   "))

    @Test fun wordIsTrimmed() = assertEquals("귤", EntryRules.normalized(EntryKind.WORD, " 귤 "))

    @Test fun timeMustBeHourMinute() {
        assertEquals("07:30", EntryRules.normalized(EntryKind.TIME, "07:30"))
        assertNull(EntryRules.normalized(EntryKind.TIME, "24:00"))
        assertNull(EntryRules.normalized(EntryKind.TIME, "7:30"))
    }

    @Test fun emojiNeedsPictograph() {
        assertEquals("🌧️", EntryRules.normalized(EntryKind.EMOJI, "🌧️"))
        assertEquals("☕", EntryRules.normalized(EntryKind.EMOJI, "☕"))
        assertNull(EntryRules.normalized(EntryKind.EMOJI, "비"))
    }

    @Test fun emojiOnlyDropsEverythingElse() {
        assertEquals("🌧️☕", com.tokipin.app.rules.EmojiRules.filter("비🌧️ a1☕"))
        assertEquals("👩‍👩‍👧🇰🇷👍🏽", com.tokipin.app.rules.EmojiRules.filter("가👩‍👩‍👧 🇰🇷x👍🏽"))
        assertEquals("", com.tokipin.app.rules.EmojiRules.filter("산책"))
    }

    private val journal = Journal()
        .withDay(LocalDate.of(2026, 11, 25), listOf(Entry(kind = EntryKind.WORD, value = "살구잼"), Entry(kind = EntryKind.PLACE, value = "연남동")))
        .withDay(LocalDate.of(2026, 11, 18), listOf(Entry(kind = EntryKind.PLACE, value = "한강")))

    private val names = mapOf(EntryKind.WORD to "단어", EntryKind.EMOJI to "이모지", EntryKind.PLACE to "장소", EntryKind.TIME to "시간")

    @Test fun searchByKindNameListsPlacesNewestFirst() {
        val hits = search(journal, "장소", names)
        assertEquals(listOf("연남동", "한강"), hits.map { it.entry.value })
    }

    @Test fun searchByDate() {
        assertEquals(listOf("한강"), search(journal, "11.18", names).map { it.entry.value })
    }

    @Test fun exportKeepsOrderOldestDayFirst() {
        val text = JournalStore.exportJson(journal)
        assertTrue(text.indexOf("2026-11-18") < text.indexOf("2026-11-25"))
        assertTrue(text.indexOf("살구잼") < text.indexOf("연남동"))
        assertTrue(text.contains("\"version\": 1"))
        assertTrue(text.contains("\"kind\": \"place\""))
        assertTrue(text.contains("\"updatedAt\""))
    }

    private fun e(id: String, kind: EntryKind, v: String) = Entry(id = id, kind = kind, value = v)
    private val mixed = com.tokipin.app.data.Draft(
        date = "2026-10-01",
        items = listOf(e("a", EntryKind.WORD, "산책"), e("b", EntryKind.PLACE, "공원"), e("c", EntryKind.EMOJI, "🌿"), e("d", EntryKind.TIME, "16:20"), e("e", EntryKind.WORD, "산책")),
    )

    @Test fun moveKeepsIdsAcrossKindsAndCountsAsAChange() {
        val moved = com.tokipin.app.data.DraftRules.move(mixed, 0, 4)
        assertEquals(listOf("b", "c", "d", "e", "a"), moved.items.map { it.id })
        assertTrue(moved.changed)
        assertEquals(mixed, com.tokipin.app.data.DraftRules.move(mixed, 2, 2))
    }

    @Test fun undoRestoresOnlyTheDeletedFragmentAndKeepsLaterReorders() {
        val (removed, gone) = com.tokipin.app.data.DraftRules.remove(mixed, setOf("b"))
        val reordered = com.tokipin.app.data.DraftRules.move(removed, 3, 0) // e to the top
        val restored = com.tokipin.app.data.DraftRules.restore(reordered, gone)
        assertEquals(listOf("e", "b", "a", "c", "d"), restored.items.map { it.id })
    }

    @Test fun emptyDayIsRemoved() {
        assertTrue(journal.withDay(LocalDate.of(2026, 11, 18), emptyList()).itemsOn(LocalDate.of(2026, 11, 18)).isEmpty())
        assertEquals(1, journal.withDay(LocalDate.of(2026, 11, 18), emptyList()).days.size)
    }
}
