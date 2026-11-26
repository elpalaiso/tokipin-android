package com.tokipin.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
enum class EntryKind {
    @SerialName("word") WORD,
    @SerialName("emoji") EMOJI,
    @SerialName("place") PLACE,
    @SerialName("time") TIME,
}

/** One fragment of a day's record. Items keep the order they were entered in. */
@Serializable
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val kind: EntryKind,
    val value: String,
)

/** All saved records, keyed by ISO date (yyyy-MM-dd). A date with no items is never stored. */
data class Journal(
    val days: Map<String, List<Entry>> = emptyMap(),
    /** Per-day ISO-8601 instant of the last change (shared file format with iOS). */
    val updatedAt: Map<String, String> = emptyMap(),
) {
    fun itemsOn(date: LocalDate): List<Entry> = days[date.toString()].orEmpty()

    fun recordedDates(): List<LocalDate> = days.keys.map(LocalDate::parse).sortedDescending()

    fun withDay(date: LocalDate, items: List<Entry>, now: java.time.Instant = java.time.Instant.now()): Journal {
        val next = days.toMutableMap()
        val stamps = updatedAt.toMutableMap()
        if (items.isEmpty()) {
            next.remove(date.toString()); stamps.remove(date.toString())
        } else {
            next[date.toString()] = items; stamps[date.toString()] = now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        }
        return copy(days = next, updatedAt = stamps)
    }

    val isEmpty: Boolean get() = days.isEmpty()
}

/**
 * The editor's working copy. It is written to disk on every change so that leaving the editor,
 * or the process being killed, never loses typed input (Figma 04 · 저장 실패와 중단).
 */
@Serializable
data class Draft(
    val date: String,
    val items: List<Entry> = emptyList(),
    val kind: EntryKind = EntryKind.WORD,
    val raw: String = "",
    val editId: String? = null,
    val changed: Boolean = false,
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
    val hasContent: Boolean get() = changed || raw.isNotBlank()
}

object EntryRules {
    private val timePattern = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    /**
     * Mirrors the web demo's validation: blank → "", invalid time/emoji → null, otherwise the trimmed value.
     */
    fun normalized(kind: EntryKind, raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        return when (kind) {
            EntryKind.TIME -> if (timePattern.matches(value)) value else null
            EntryKind.EMOJI -> if (containsEmoji(value)) value else null
            else -> value
        }
    }

    /** Keeps only whole emoji (grapheme clusters with a pictograph, incl. ZWJ sequences, flags, skin tones). */
    fun emojiOnly(value: String): String {
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(value)
        val out = StringBuilder()
        var start = it.first()
        var end = it.next()
        while (end != java.text.BreakIterator.DONE) {
            val cluster = value.substring(start, end)
            if (containsEmoji(cluster)) out.append(cluster)
            start = end
            end = it.next()
        }
        return out.toString()
    }

    fun containsEmoji(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val cp = value.codePointAt(i)
            if (isPictographic(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    private fun isPictographic(cp: Int): Boolean =
        cp in 0x1F000..0x1FAFF || // emoticons, symbols & pictographs, transport, supplemental
            cp in 0x2600..0x27BF || // misc symbols, dingbats
            cp in 0x2300..0x23FF || // misc technical (⌚ ⏰)
            cp in 0x2B00..0x2BFF || // arrows & stars (⭐)
            cp in 0x1F1E6..0x1F1FF || // regional indicators
            cp == 0x20E3 || cp == 0x00A9 || cp == 0x00AE || cp == 0x3030 || cp == 0x303D ||
            Character.getType(cp) == Character.OTHER_SYMBOL.toInt() && cp > 0x2000
}
