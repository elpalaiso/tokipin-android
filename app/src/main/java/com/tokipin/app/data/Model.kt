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
    /**
     * Blank → "", invalid → null, otherwise the value to store. Times must be canonical "HH:mm";
     * the emoji field keeps only its valid emoji sequences (UX v0.1 · 이모지 입력 계약), so an old
     * mixed draft such as "비☕" is saved as "☕" when the user confirms it.
     */
    fun normalized(kind: EntryKind, raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty()) return ""
        return when (kind) {
            EntryKind.TIME -> if (com.tokipin.app.rules.TimeRules.isCanonical(value)) value else null
            EntryKind.EMOJI -> com.tokipin.app.rules.EmojiRules.filter(value).ifEmpty { null }
            else -> value
        }
    }
}

/** The outcome of confirming the pending value (Enter, +, switching tools, or ✓). */
sealed interface Commit {
    data class Ok(val draft: Draft) : Commit
    data class Invalid(val kind: EntryKind) : Commit
    data object EmptyEdit : Commit
}

object DraftRules {
    fun commit(d: Draft): Commit {
        val value = EntryRules.normalized(d.kind, d.raw) ?: return Commit.Invalid(d.kind)
        if (value.isEmpty()) return if (d.editId != null) Commit.EmptyEdit else Commit.Ok(d)
        val items = d.items.toMutableList()
        val index = items.indexOfFirst { it.id == d.editId }
        if (index >= 0) items[index] = items[index].copy(kind = d.kind, value = value)
        else items += Entry(kind = d.kind, value = value)
        return Commit.Ok(d.copy(items = items, raw = "", editId = null, changed = true))
    }

    /** Leaving a pending value or an edit without confirming it keeps the items as they were. */
    fun cancelPending(d: Draft): Draft = d.copy(raw = "", editId = null)
}
