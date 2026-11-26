package com.tokipin.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tokipin.app.R
import com.tokipin.app.data.Draft
import com.tokipin.app.data.Entry
import com.tokipin.app.data.EntryKind
import com.tokipin.app.data.EntryRules
import com.tokipin.app.data.Journal
import com.tokipin.app.data.JournalStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class Screen { Loading, LoadFailed, Editor, Browse, Search }

/** The four zoom levels of the browse screen, from widest to closest. */
enum class Level { YEAR, MONTH, RECORDS, DAILY }

enum class Policy { PRIVACY, TERMS }

data class Undo(val message: String, val action: () -> Unit)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = JournalStore(application)
    private val res = application.resources

    var screen by mutableStateOf(Screen.Loading); private set
    /** True only while a slow read is still running, so fast launches never flash a skeleton. */
    var showSkeleton by mutableStateOf(false); private set
    var journal by mutableStateOf(Journal()); private set
    var draft by mutableStateOf<Draft?>(null); private set

    // Browse
    var level by mutableStateOf(Level.RECORDS); private set
    var selectedDate by mutableStateOf(LocalDate.now()); private set
    /** Incremented to ask the current view to scroll back to [selectedDate] (e.g. the Today button). */
    var focusRequest by mutableStateOf(0); private set
    private var dailyReturnLevel = Level.RECORDS

    // Search
    var searchQuery by mutableStateOf(""); private set
    var openedFromSearch by mutableStateOf(false); private set
    var highlightId by mutableStateOf<String?>(null); private set

    // Editor feedback
    var editorMessage by mutableStateOf<String?>(null); private set
    var saveFailed by mutableStateOf(false); private set
    var selection by mutableStateOf<Set<String>?>(null); private set

    // Shared
    var undo by mutableStateOf<Undo?>(null); private set
    var settingsOpen by mutableStateOf(false); private set
    var policy by mutableStateOf<Policy?>(null); private set
    var hapticsEnabled by mutableStateOf(store.hapticsEnabled); private set
    var transientMessage by mutableStateOf<String?>(null)

    private var undoJob: Job? = null

    init { load() }

    fun load() {
        screen = Screen.Loading
        viewModelScope.launch {
            val skeleton = launch { delay(SKELETON_DELAY_MS); showSkeleton = true }
            val loaded = runCatching { store.loadJournal() }
            skeleton.cancel()
            showSkeleton = false
            loaded.onFailure { screen = Screen.LoadFailed; return@launch }
            journal = loaded.getOrThrow()
            draft = store.loadDraft()
            val firstLaunch = !store.hasLaunchedBefore
            store.hasLaunchedBefore = true
            selectedDate = journal.recordedDates().firstOrNull() ?: LocalDate.now()
            when {
                draft?.hasContent == true -> screen = Screen.Editor
                firstLaunch && journal.isEmpty -> {
                    draft = Draft(date = LocalDate.now().toString())
                    screen = Screen.Editor
                }
                else -> screen = Screen.Browse
            }
        }
    }

    // region Editor

    /** "+" on the home: resume the kept draft, otherwise start on the selected day (today by default). */
    fun openEditor(date: LocalDate = if (level == Level.DAILY) selectedDate else LocalDate.now()) {
        val kept = draft
        draft = if (kept != null && kept.hasContent) kept
        else Draft(date = date.toString(), items = journal.itemsOn(date))
        resetEditorFeedback()
        screen = Screen.Editor
        persistDraft()
    }

    /** Opens an existing day's item straight into edit mode (daily view → item menu → 수정). */
    fun editSavedItem(date: LocalDate, entry: Entry) {
        draft = Draft(
            date = date.toString(), items = journal.itemsOn(date),
            kind = entry.kind, raw = entry.value, editId = entry.id,
        )
        resetEditorFeedback()
        screen = Screen.Editor
        persistDraft()
    }

    private fun resetEditorFeedback() {
        editorMessage = null
        saveFailed = false
        selection = null
        clearUndo()
    }

    fun updateRaw(raw: String) {
        val d = draft ?: return
        draft = d.copy(raw = raw)
        editorMessage = null
        persistDraft()
    }

    /** Commits the typed value as one item. Returns false (and shows why) if it is invalid. */
    fun commitRaw(): Boolean {
        val d = draft ?: return false
        val value = EntryRules.normalized(d.kind, d.raw)
        if (value == null) {
            editorMessage = res.getString(
                if (d.kind == EntryKind.TIME) R.string.editor_invalid_time else R.string.editor_invalid_emoji,
            )
            return false
        }
        if (value.isEmpty()) {
            if (d.editId != null) {
                editorMessage = res.getString(R.string.editor_empty_edit)
                return false
            }
            return true
        }
        val items = d.items.toMutableList()
        val index = items.indexOfFirst { it.id == d.editId }
        if (index >= 0) items[index] = items[index].copy(kind = d.kind, value = value)
        else items += Entry(kind = d.kind, value = value)
        draft = d.copy(items = items, raw = "", editId = null, changed = true)
        editorMessage = null
        persistDraft()
        return true
    }

    /** Switching tools first commits a valid pending value, then opens the next kind. */
    fun setKind(kind: EntryKind): Boolean {
        val d = draft ?: return false
        if (kind == d.kind) return true
        if (!commitRaw()) return false
        draft = draft!!.copy(kind = kind)
        persistDraft()
        return true
    }

    fun addValue(kind: EntryKind, value: String) {
        val d = draft ?: return
        if (d.editId == null && !commitRaw()) return
        draft = draft!!.copy(kind = kind, raw = value)
        commitRaw()
    }

    fun startEditing(entry: Entry) {
        if (!commitRaw()) return
        draft = draft!!.copy(editId = entry.id, raw = entry.value, kind = entry.kind)
        persistDraft()
    }

    fun beginSelection() {
        if (!commitRaw()) return
        selection = emptySet()
    }

    fun toggleSelected(id: String) {
        val s = selection ?: return
        selection = if (id in s) s - id else s + id
    }

    fun endSelection() { selection = null }

    fun deleteDraftItems(ids: Set<String>) {
        val d = draft ?: return
        if (ids.isEmpty()) return
        val before = d.items
        val raw = if (d.editId in ids) "" else d.raw
        val editId = if (d.editId in ids) null else d.editId
        draft = d.copy(items = before.filterNot { it.id in ids }, changed = true, raw = raw, editId = editId)
        selection = null
        persistDraft()
        val message = if (ids.size == 1) res.getString(R.string.editor_item_deleted)
        else res.getString(R.string.editor_items_deleted, ids.size)
        offerUndo(message) {
            val current = draft ?: return@offerUndo
            // Restore in the original entry order, keeping anything typed since.
            val kept = current.items.associateBy { it.id }
            val restored = before.filter { it.id in ids || it.id in kept } +
                current.items.filter { c -> before.none { it.id == c.id } }
            draft = current.copy(items = restored)
            persistDraft()
        }
    }

    /** ✓: commits the pending value, saves the whole day, and returns to its card. */
    fun saveDraft() {
        if (!commitRaw()) return
        val d = draft ?: return
        if (!d.changed) {
            if (d.items.isEmpty()) { editorMessage = res.getString(R.string.editor_need_one); return }
            closeEditor(d.localDate, keepDraft = false)
            return
        }
        val next = journal.withDay(d.localDate, d.items)
        viewModelScope.launch {
            val result = runCatching { store.saveJournal(next) }
            if (result.isFailure) {
                saveFailed = true
                return@launch
            }
            journal = next
            closeEditor(d.localDate, keepDraft = false)
        }
    }

    /** Back keeps the draft; "+" on the home continues it. */
    fun leaveEditor() {
        val d = draft ?: return
        val pending = d.raw.isNotBlank() || d.changed
        closeEditor(d.localDate, keepDraft = pending)
    }

    private fun closeEditor(date: LocalDate, keepDraft: Boolean) {
        if (!keepDraft) draft = null
        persistDraft()
        saveFailed = false
        selection = null
        clearUndo()
        if (journal.itemsOn(date).isNotEmpty()) selectedDate = date
        screen = Screen.Browse
        focusRequest++
    }

    private fun persistDraft() {
        val d = draft
        viewModelScope.launch { store.saveDraft(d) }
    }

    // endregion

    // region Browse

    fun changeLevel(next: Level) {
        if (next == level) return
        if (next == Level.DAILY) dailyReturnLevel = level
        level = next
        openedFromSearch = false
        highlightId = null
        focusRequest++
    }

    fun zoomIn(target: Any? = null): Boolean = zoom(level.ordinal + 1, target)
    fun zoomOut(target: Any? = null): Boolean = zoom(level.ordinal - 1, target)

    /**
     * Pinch keeps the date between the fingers in view: the year view opens that month, the month
     * calendar and recorded days select that date, and daily ↔ recorded days keep the selected day.
     * Returns whether the level changed (for the single haptic tick).
     */
    private fun zoom(ordinal: Int, target: Any?): Boolean {
        val next = Level.entries.getOrNull(ordinal) ?: return false
        when (target) {
            is LocalDate -> selectedDate = target
            is YearMonth -> if (YearMonth.from(selectedDate) != target) {
                selectedDate = journal.recordedDates().firstOrNull { YearMonth.from(it) == target } ?: target.atDay(1)
            }
        }
        changeLevel(next)
        return true
    }

    /** The "< parent" button in the top bar. */
    fun goUp() {
        when {
            level == Level.DAILY && openedFromSearch -> reopenSearch()
            level == Level.DAILY -> changeLevel(dailyReturnLevel.takeIf { it != Level.DAILY } ?: Level.RECORDS)
            level == Level.RECORDS || level == Level.MONTH -> changeLevel(Level.YEAR)
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        highlightId = null
    }

    fun openDay(date: LocalDate) {
        selectedDate = date
        changeLevel(Level.DAILY)
    }

    fun openMonth(month: YearMonth) {
        selectedDate = if (selectedDate.year == month.year && selectedDate.month == month.month) selectedDate
        else journal.recordedDates().firstOrNull { YearMonth.from(it) == month } ?: month.atDay(1)
        changeLevel(Level.MONTH)
    }

    fun goToday() {
        selectedDate = LocalDate.now()
        highlightId = null
        focusRequest++
    }

    fun deleteSavedItem(date: LocalDate, entry: Entry) {
        val before = journal.itemsOn(date)
        persistJournal(journal.withDay(date, before.filterNot { it.id == entry.id })) {
            offerUndo(res.getString(R.string.editor_item_deleted)) {
                persistJournal(journal.withDay(date, before))
            }
        }
    }

    fun deleteDay(date: LocalDate) {
        val before = journal.itemsOn(date)
        if (before.isEmpty()) return
        persistJournal(journal.withDay(date, emptyList())) {
            offerUndo(res.getString(R.string.day_deleted, before.size)) {
                persistJournal(journal.withDay(date, before))
            }
        }
    }

    private fun persistJournal(next: Journal, then: () -> Unit = {}) {
        viewModelScope.launch {
            if (runCatching { store.saveJournal(next) }.isSuccess) {
                journal = next
                then()
            } else {
                transientMessage = res.getString(R.string.editor_save_failed).lineSequence().first()
            }
        }
    }

    // endregion

    // region Search

    fun openSearch() {
        searchQuery = ""
        screen = Screen.Search
    }

    private fun reopenSearch() {
        openedFromSearch = false
        highlightId = null
        screen = Screen.Search
    }

    fun updateSearch(query: String) { searchQuery = query }

    fun closeSearch() {
        searchQuery = ""
        screen = Screen.Browse
    }

    fun openSearchResult(date: LocalDate, entry: Entry) {
        selectedDate = date
        if (level != Level.DAILY) dailyReturnLevel = level
        level = Level.DAILY
        openedFromSearch = true
        highlightId = entry.id
        screen = Screen.Browse
        focusRequest++
    }

    // endregion

    // region Settings

    fun openSettings() { settingsOpen = true }
    fun closeSettings() { settingsOpen = false }
    fun openPolicy(p: Policy?) { policy = p }

    fun setHaptics(enabled: Boolean) {
        hapticsEnabled = enabled
        store.hapticsEnabled = enabled
    }

    fun export(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            val ok = runCatching {
                val out = resolver.openOutputStream(uri) ?: error("no stream")
                store.export(journal, out)
            }.isSuccess
            transientMessage = res.getString(if (ok) R.string.settings_export_done else R.string.settings_export_failed)
        }
    }

    // endregion

    private fun offerUndo(message: String, action: () -> Unit) {
        undoJob?.cancel()
        undo = Undo(message) {
            clearUndo()
            action()
        }
        undoJob = viewModelScope.launch {
            delay(UNDO_MS)
            undo = null
        }
    }

    fun clearUndo() {
        undoJob?.cancel()
        undo = null
    }

    companion object {
        private const val SKELETON_DELAY_MS = 350L
        // Figma 03 · 기록일 하루 전체: "삭제 후 8초간 실행 취소".
        private const val UNDO_MS = 8_000L
    }
}
