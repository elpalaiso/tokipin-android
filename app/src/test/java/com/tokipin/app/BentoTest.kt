package com.tokipin.app

import com.tokipin.app.rules.Bento
import com.tokipin.app.rules.Bento.Side
import org.junit.Assert.assertEquals
import org.junit.Test

/** Figma 5-2183 (390 wide, 14 each side → 362): every tile and empty bento of November 2026. */
class BentoTest {
    private val w = 362f
    // Natural widths from the frame's content (13 + content + 18), in date order 26 → 1.
    private val days = listOf(
        "26" to 315f, "25" to 270f, "24" to 325f, "23" to 375f, "22" to 167f, "21" to 47f,
        "20" to 247f, "19" to 107f, "18" to 61f, "17" to 270f, "15" to 247f, "14" to 92f,
        "12" to 247f, "11" to 195f, "10" to 341f, "8" to 216f, "7" to 187f, "6" to 326f,
        "5" to 92f, "4" to 107f, "2" to 245f, "1" to 167f,
    )
    private val cards = days.map { (_, n) -> Bento.Card(n.coerceAtLeast(Bento.MIN_CARD).coerceAtMost(w), multiline = n > w) }

    private fun row(r: Bento.Row) = r.cards.map { days[it].first } to r.widths

    @Test fun reproducesTheNovemberFrame() {
        val rows = Bento.rows(cards, w)
        val expected = listOf(
            Triple(listOf("26"), listOf(315f), 39f to Side.RIGHT),
            Triple(listOf("25"), listOf(270f), 84f to Side.LEFT),
            Triple(listOf("24"), listOf(325f), 29f to Side.RIGHT),
            Triple(listOf("23"), listOf(362f), null),
            Triple(listOf("22", "21"), listOf(167f, 67f), 112f to Side.LEFT),
            Triple(listOf("20", "19"), listOf(247f, 107f), null),
            Triple(listOf("18", "17"), listOf(75.5f, 278.5f), null),
            Triple(listOf("15", "14"), listOf(254.5f, 99.5f), null),
            Triple(listOf("12"), listOf(247f), 107f to Side.RIGHT),
            Triple(listOf("11"), listOf(195f), 159f to Side.LEFT),
            Triple(listOf("10"), listOf(362f), null),
            Triple(listOf("8"), listOf(216f), 138f to Side.RIGHT),
            Triple(listOf("7"), listOf(187f), 167f to Side.LEFT),
            Triple(listOf("6"), listOf(326f), 28f to Side.RIGHT),
            Triple(listOf("5", "4"), listOf(92f, 107f), 147f to Side.LEFT),
            Triple(listOf("2"), listOf(245f), 109f to Side.RIGHT),
            Triple(listOf("1"), listOf(167f), 187f to Side.LEFT),
        )
        assertEquals(expected.size, rows.size)
        expected.zip(rows).forEach { (e, r) ->
            assertEquals(e.first, row(r).first)
            assertEquals(e.first.toString(), e.second, row(r).second)
            assertEquals(e.first.toString(), e.third?.first, r.bento)
            assertEquals(e.first.toString(), e.third?.second, r.bentoSide)
        }
    }

    @Test fun multilineCardsNeverPair() {
        val rows = Bento.rows(listOf(Bento.Card(200f, multiline = true), Bento.Card(80f)), 362f)
        assertEquals(2, rows.size)
        assertEquals(154f, rows[0].bento) // its wrapped width leaves room for a bento
    }
}
