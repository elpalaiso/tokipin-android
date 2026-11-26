package com.tokipin.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/** "yyyy-MM-dd" without a formatter: the keys are always canonical, and this is ~10× faster. */
internal fun isoDate(key: String): LocalDate =
    if (key.length == 10 && key[4] == '-' && key[7] == '-') {
        LocalDate.of(key.substring(0, 4).toInt(), key.substring(5, 7).toInt(), key.substring(8, 10).toInt())
    } else LocalDate.parse(key)

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

    // Indexes over the saved dates. A Journal never changes once made (each edit makes a new one),
    // so they are built once, on first use; AppViewModel builds them off the main thread
    // ([warmed]) before a large journal reaches the screen. None of them is part of equals.

    /** Every saved date, newest first. */
    val dates: List<LocalDate> by lazy { days.keys.map(::isoDate).sortedDescending() }
    val dateSet: Set<LocalDate> by lazy { dates.toHashSet() }
    /** Saved dates of each month, newest first. */
    val byMonth: Map<YearMonth, List<LocalDate>> by lazy { dates.groupBy { YearMonth.of(it.year, it.monthValue) } }
    val months: Set<YearMonth> get() = byMonth.keys
    val years: Set<Int> by lazy { byMonth.keys.mapTo(HashSet()) { it.year } }

    fun recordedDates(): List<LocalDate> = dates
    fun datesIn(month: YearMonth): List<LocalDate> = byMonth[month].orEmpty()

    /** Builds every index now (call off the main thread); returns this journal. */
    fun warmed(): Journal = apply { years; dateSet }

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

    /**
     * Moves one fragment to [to] (indexes in the saved order). Any kinds can trade places; ids,
     * values and kinds stay as they are, and a reorder alone is a change worth saving.
     */
    fun move(d: Draft, from: Int, to: Int): Draft {
        if (from !in d.items.indices) return d
        val target = to.coerceIn(0, d.items.lastIndex)
        if (target == from) return d
        val items = d.items.toMutableList()
        val moved = items.removeAt(from)
        items.add(target, moved)
        return d.copy(items = items, changed = true)
    }

    /** Removes fragments, remembering where each one was so undo can put back only those. */
    fun remove(d: Draft, ids: Set<String>): Pair<Draft, List<IndexedValue<Entry>>> {
        val removed = d.items.withIndex().filter { it.value.id in ids }
        val clearEdit = d.editId in ids
        return d.copy(
            items = d.items.filterNot { it.id in ids }, changed = true,
            raw = if (clearEdit) "" else d.raw, editId = if (clearEdit) null else d.editId,
        ) to removed
    }

    /**
     * Undo of [remove]: the removed fragments go back at their old positions, and everything done
     * since (edits, reorders, new fragments) is kept as it is now.
     */
    fun restore(d: Draft, removed: List<IndexedValue<Entry>>): Draft {
        val items = d.items.toMutableList()
        removed.sortedBy { it.index }.forEach { (index, entry) ->
            if (items.none { it.id == entry.id }) items.add(index.coerceIn(0, items.size), entry)
        }
        return d.copy(items = items, changed = true)
    }
}
