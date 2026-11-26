package com.tokipin.app.rules

/**
 * 기록한 날 · bento rows (Figma 5-2183, 289-16838). Pure geometry in dp (pt on iOS), so both apps
 * and the tests place the same cards in the same rows:
 *
 * - A card's natural width is its widest content line plus padding (13 leading, 18 trailing), at
 *   least [MIN_CARD], at most the row width; content that does not fit wraps (a multi-line card).
 * - In date order, two neighbouring cards share a row only when both are single-line and both
 *   natural widths plus the gap fit. Never more than two.
 * - What is left of a row becomes an empty bento (decoration) when it is at least [MIN_BENTO]
 *   after the gap; otherwise the cards share it equally. Bentos alternate right, left, right…,
 *   counting only rows that actually have one.
 */
object Bento {
    const val GAP = 8f
    const val MIN_BENTO = 24f
    const val MIN_CARD = 67f
    const val PAD_LEADING = 13f
    const val PAD_TRAILING = 18f

    data class Card(val natural: Float, val multiline: Boolean = false)

    enum class Side { LEFT, RIGHT }

    /** One row: card indexes with their final widths, and an optional empty bento. */
    data class Row(val cards: List<Int>, val widths: List<Float>, val bento: Float?, val bentoSide: Side?) {
        /** Extra width each card got beyond its natural width (goes to its leading padding). */
        fun extra(index: Int, naturals: List<Card>): Float = widths[cards.indexOf(index)] - naturals[index].natural
    }

    /** Natural width of a card whose widest content line is [content] wide. */
    fun natural(content: Float, width: Float): Float =
        (PAD_LEADING + content + PAD_TRAILING).coerceAtLeast(MIN_CARD).coerceAtMost(width)

    fun rows(cards: List<Card>, width: Float): List<Row> {
        val rows = mutableListOf<Row>()
        var nextSide = Side.RIGHT
        var i = 0
        while (i < cards.size) {
            val a = cards[i]
            val b = cards.getOrNull(i + 1)
            val group = if (b != null && !a.multiline && !b.multiline && a.natural + GAP + b.natural <= width) listOf(i, i + 1) else listOf(i)
            val naturals = group.map { cards[it].natural.coerceAtMost(width) }
            val leftover = width - naturals.sum() - GAP * (group.size - 1)
            if (leftover - GAP >= MIN_BENTO) {
                rows += Row(group, naturals, leftover - GAP, nextSide)
                nextSide = if (nextSide == Side.RIGHT) Side.LEFT else Side.RIGHT
            } else {
                val share = leftover.coerceAtLeast(0f) / group.size
                rows += Row(group, naturals.map { it + share }, null, null)
            }
            i += group.size
        }
        return rows
    }
}
