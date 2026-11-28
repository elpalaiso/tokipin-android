package com.tokipin.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tokipin.app.R
import com.tokipin.app.UsageSharing
import com.tokipin.app.data.Draft
import com.tokipin.app.data.Entry
import com.tokipin.app.data.EntryKind
import com.tokipin.app.data.Commit
import com.tokipin.app.data.DraftRules
import com.tokipin.app.rules.Level
import com.tokipin.app.data.Journal
import com.tokipin.app.data.JournalStore
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

enum class Screen { Loading, LoadFailed, Sample, Editor, Browse, Search }


enum class Policy { PRIVACY, TERMS }

data class Undo(val message: String, val action: () -> Unit)

class AppViewModel private constructor(
    application: Application,
    private val usage: UsageSharing?,
    private val store: JournalStore?,
    internal val tour: SampleTour?,
) : AndroidViewModel(application) {
    internal constructor(application: Application, usage: UsageSharing) : this(application, usage, JournalStore(application), null)
    constructor(application: Application) : this(application, UsageSharing(application))
    val usageAvailable get() = usage?.available == true
    private val shouldOfferUsage get() = usageAvailable && usage?.hasChoice != true
    var usagePromptOpen by mutableStateOf(false); private set
    var usageEnabled by mutableStateOf(usage?.enabled == true); private set
    private var savingDraft = false

    fun usagePromptPresented() { usage?.markPresented() }

    fun chooseUsageSharing(value: Boolean) {
        usage?.setEnabled(value)
        usageEnabled = usage?.enabled == true
        usagePromptOpen = false
    }
    // Messages use the app language (AppLocale), not the device language the Application starts with.
    private val res get() = com.tokipin.app.AppLocale.resources(getApplication())

    var screen by mutableStateOf(Screen.Loading); private set
    /** True only while a slow read is still running, so fast launches never flash a skeleton. */
    var showSkeleton by mutableStateOf(false); private set
    var journal by mutableStateOf(Journal()); private set
    var draft by mutableStateOf<Draft?>(null); private set

    internal var sample by mutableStateOf<AppViewModel?>(null); private set

    internal val replayingOnboarding get() = sample != null && screen != Screen.Sample
    private var replayJob: Job? = null

    // Browse
    var level by mutableStateOf(Level.RECORDS); private set
    var selectedDate by mutableStateOf(com.tokipin.app.rules.AppClock.today()); private set
    /** Incremented to ask the current view to scroll back to [selectedDate] (e.g. the Today button). */
    var focusRequest by mutableStateOf(0); private set
    private var dailyReturnLevel by mutableStateOf(Level.RECORDS)

    /**
     * UX v0.4: set by the window host while the window shows the calendar and day panes. The state
     * is the one the single column uses; here `level == DAILY` only means the day pane was used
     * last, and the calendar pane shows [calendarLevel].
     */
    private val twoPaneState = mutableStateOf(false)
    var twoPane: Boolean
        get() = twoPaneState.value
        internal set(value) {
            if (value == twoPaneState.value) return
            twoPaneState.value = value
            // The views are composed again in their new place: put each list back where the person
            // left it (§5 "각 보기의 스크롤 위치"), not on the selected date.
            entry = ListEntry.Restore
        }

    /** The level the calendar pane shows: [level], or the level a day was opened from. */
    val calendarLevel: Level get() = com.tokipin.app.rules.Adaptive.calendarLevel(level, dailyReturnLevel)

    /** Tests (PaneNavigationReplayTest): put the navigation state where a shared case starts. */
    internal fun setNavigationStateForTest(level: Level, dayReturnLevel: Level, selected: LocalDate, recordedMonth: YearMonth?) {
        this.level = level
        dailyReturnLevel = dayReturnLevel
        selectedDate = selected
        visibleMonth = YearMonth.from(selected)
        recordedMonth?.let { this.recordedMonth = it; recordedPreview = null }
    }

    /** Two panes: the day pane was used, so folding the device lands on the day. Never ticks. */
    private fun useDayPane() {
        if (level != Level.DAILY) { dailyReturnLevel = level; level = Level.DAILY }
    }

    /**
     * Two panes: after the day pane changed the date, the calendar pane brings it into view (no
     * tick). 기록한 날 moves only to a month it lists.
     */
    private fun followInCalendar() {
        val month = YearMonth.from(selectedDate)
        if (calendarLevel == Level.RECORDS) {
            if (month != recordedMonth) {
                // Another month only when it is listed; that page then shows the date's card.
                if (month in visibleRecordMonths) {
                    recordedMonth = month
                    recordedPreview = null
                    entry = ListEntry.Anchor(null)
                    focusRequest++
                }
                return
            }
            // Same month, no card for the date: the reading position stays.
            if (journal.itemsOn(selectedDate).isEmpty()) return
        }
        followRequest++
    }

    /** Incremented to ask the calendar pane to bring [selectedDate] into view only if it is hidden. */
    var followRequest by mutableStateOf(0); private set

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
    var hapticsEnabled by mutableStateOf(store?.hapticsEnabled ?: true); private set
    var transientMessage by mutableStateOf<String?>(null)

    private var undoJob: Job? = null



    /** Set by MainActivity before the first load finishes (debug and benchmark builds only). */
    internal var seedRequest: String? = null

    fun load() {
        val store = store ?: return
        screen = Screen.Loading
        viewModelScope.launch {
            val skeleton = launch { delay(SKELETON_DELAY_MS); showSkeleton = true }
            val loaded = runCatching { store.loadJournal() }
            skeleton.cancel()
            showSkeleton = false
            loaded.onFailure { screen = Screen.LoadFailed; return@launch }
            journal = loaded.getOrThrow()
            // Debug/benchmark builds: a launch with `tokipin.seed` starts from that sample journal.
            seedRequest?.let { name ->
                seedRequest = null
                if (com.tokipin.app.data.SampleData.AVAILABLE) {
                    val seeded = withContext(Dispatchers.Default) {
                        (if (name == "large") com.tokipin.app.data.SampleData.large() else com.tokipin.app.data.SampleData.journal()).warmed()
                    }
                    store.saveJournal(seeded)
                    withContext(Dispatchers.IO) { store.writeDrafts(com.tokipin.app.data.DraftsFile()) }
                    store.hasLaunchedBefore = true
                    journal = seeded
                }
            }
            val drafts = store.loadDrafts()
            keptDrafts = drafts.drafts.filter { it.hasContent }.associateBy { it.date }
            draft = drafts.active?.let { keptDrafts[it] }
            val firstLaunch = !store.hasLaunchedBefore
            if (firstLaunch && journal.isEmpty && drafts.drafts.isEmpty() && usage?.hasChoice != true) {
                sample = newSampleSession()
                screen = Screen.Sample
                return@launch
            }
            store.hasLaunchedBefore = true
            usagePromptOpen = shouldOfferUsage
            selectedDate = journal.recordedDates().firstOrNull() ?: com.tokipin.app.rules.AppClock.today()
            recordedMonth = listedMonth(YearMonth.from(selectedDate))
            when {
                draft?.hasContent == true -> screen = Screen.Editor
                firstLaunch && journal.isEmpty && keptDrafts.isEmpty() -> {
                    draft = Draft(date = com.tokipin.app.rules.AppClock.today().toString())
                    screen = Screen.Editor
                }
                else -> screen = Screen.Browse
            }
        }
    }

    private suspend fun newSampleSession(): AppViewModel {
        val newTour = withContext(Dispatchers.Default) { SampleTour.create(today, res) }
        return AppViewModel(getApplication(), null, null, newTour).also { it.hapticsEnabled = hapticsEnabled }
    }

    internal fun replayOnboarding() {
        if (!com.tokipin.app.data.SampleData.AVAILABLE || !settingsOpen || tour != null ||
            sample != null || replayJob?.isActive == true) return
        replayJob = viewModelScope.launch { sample = newSampleSession() }
    }

    internal fun closeDeveloperReplay() {
        if (!replayingOnboarding) return
        if (screen == Screen.Browse) entry = ListEntry.Restore
        disposeSample()
    }

    private fun disposeSample() {
        sample?.viewModelScope?.cancel()
        sample = null
    }

    internal fun startPersonalJournal() {
        if (replayingOnboarding) {
            if (usageAvailable) sample?.tour?.previewUsage() else closeDeveloperReplay()
            return
        }
        if (screen != Screen.Sample) return
        store?.hasLaunchedBefore = true
        disposeSample()
        usagePromptOpen = shouldOfferUsage
        draft = Draft(date = com.tokipin.app.rules.AppClock.today().toString())
        screen = Screen.Editor
    }

    override fun onCleared() {
        sample?.viewModelScope?.cancel()
        super.onCleared()
    }

    // region Editor

    /** Unsaved drafts by date ("yyyy-MM-dd"); each day keeps its own. */
    private var keptDrafts: Map<String, Draft> = emptyMap()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val draftWriter = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(1)

    /** Whether the editor should focus its field and open the keyboard when it appears. */
    var editorAutoFocus by mutableStateOf(true); private set

    /**
     * Opens the editor for [date]: that day's kept draft if there is one, otherwise its saved
     * fragments in their order (or an empty draft). "+" on an empty day focuses the word field;
     * the pencil on a filled day ([focus] = false) opens with the keyboard closed.
     */
    /** In two panes "+" and the pencil sit in the day pane and open the date it shows (v0.4 §3). */
    fun openEditor(date: LocalDate = if (level == Level.DAILY || twoPane) selectedDate else com.tokipin.app.rules.AppClock.today(), focus: Boolean = true) {
        draft = keptDrafts[date.toString()]?.takeIf { it.hasContent }
            ?: Draft(date = date.toString(), items = journal.itemsOn(date))
        editorAutoFocus = focus
        tour?.resetEditor()
        resetEditorFeedback()
        screen = Screen.Editor
        persistDraft()
    }

    /** Opens an existing day's item straight into edit mode. */
    fun editSavedItem(date: LocalDate, entry: Entry) {
        draft = Draft(
            date = date.toString(), items = journal.itemsOn(date),
            kind = entry.kind, raw = entry.value, editId = entry.id,
        )
        editorAutoFocus = true
        resetEditorFeedback()
        screen = Screen.Editor
        persistDraft()
    }

    /**
     * The editor's date picker confirmed [target] (the draft only; ✓ still saves):
     * - a draft of new fragments moves to [target], after that day's saved fragments (or its kept
     *   draft), without duplicating anything if done again;
     * - while editing a saved day, that day's changes stay as its own draft and [target]'s editor
     *   opens. Saved records and other days' drafts are never moved or overwritten.
     * The calendar range is untouched until something is saved.
     */
    fun changeDraftDate(target: LocalDate) {
        if (draft?.editId != null && !commitRaw()) return // finish an edit in progress where it belongs
        val d = draft ?: return
        if (target.toString() == d.date || !range.canWrite(target)) return
        val saved = journal.itemsOn(d.localDate)
        val savedIds = saved.map { it.id }.toSet()
        val base = keptDrafts[target.toString()]?.takeIf { it.hasContent }
            ?: Draft(date = target.toString(), items = journal.itemsOn(target))
        // New fragments (and the value being typed) go to the target day, after what it has.
        val carried = d.items.filter { it.id !in savedIds && base.items.none { b -> b.id == it.id } }
        draft = base.copy(
            items = base.items + carried,
            raw = if (d.editId == null) d.raw else "", kind = d.kind, editId = null,
            changed = base.changed || carried.isNotEmpty(),
        )
        // What was done to the old day's saved fragments stays there as that day's draft.
        val left = d.copy(items = d.items.filter { it.id in savedIds }, raw = "", editId = null)
        keptDrafts = keptDrafts - target.toString() - d.date +
            (if (left.items != saved) mapOf(d.date to left.copy(changed = true)) else emptyMap())
        editorMessage = null
        selection = null
        clearUndo()
        persistDraft()
    }

    /** Long-press drag or TalkBack "위로/아래로 이동": move one fragment within the draft. */
    fun moveDraftItem(id: String, to: Int) {
        val d = draft ?: return
        val from = d.items.indexOfFirst { it.id == id }
        val next = DraftRules.move(d, from, to)
        if (next != d) { draft = next; persistDraft() }
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
        return when (val result = DraftRules.commit(d)) {
            is Commit.Invalid -> {
                editorMessage = res.getString(
                    if (result.kind == EntryKind.TIME) R.string.editor_invalid_time else R.string.editor_invalid_emoji,
                )
                false
            }
            Commit.EmptyEdit -> { editorMessage = res.getString(R.string.editor_empty_edit); false }
            is Commit.Ok -> {
                if (result.draft != d) { draft = result.draft; tour?.committed(); persistDraft() }
                editorMessage = null
                true
            }
        }
    }

    /** Closes a pending value or edit without changing any item (time picker cancel). */
    fun cancelPending() {
        val d = draft ?: return
        draft = DraftRules.cancelPending(d)
        editorMessage = null
        persistDraft()
    }

    /** Switching tools first commits a valid pending value, then opens the next kind. */
    fun setKind(kind: EntryKind): Boolean {
        val d = draft ?: return false
        if (kind == d.kind) { tour?.chooseTool(); return true }
        if (!commitRaw()) return false
        draft = draft!!.copy(kind = kind)
        tour?.chooseTool()
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
        val (next, removed) = DraftRules.remove(d, ids)
        draft = next
        selection = null
        persistDraft()
        val message = if (ids.size == 1) res.getString(R.string.editor_item_deleted)
        else res.getQuantityString(R.plurals.editor_items_deleted, ids.size, ids.size)
        offerUndo(message) {
            // Only the deleted fragments come back; later edits and reorders stay.
            val current = draft ?: return@offerUndo
            draft = DraftRules.restore(current, removed)
            persistDraft()
        }
    }

    /** ✓: commits the pending value, saves the whole day, and returns to its card. */
    fun saveDraft() {
        if (savingDraft || !commitRaw()) return
        val d = draft ?: return
        if (!d.changed && (tour == null || d.items == journal.itemsOn(d.localDate))) {
            if (d.items.isEmpty()) { editorMessage = res.getString(R.string.editor_need_one); return }
            closeEditor(d.localDate, keepDraft = false, saved = true)
            return
        }
        val before = journal.itemsOn(d.localDate)
        // Removing entries alone is a deletion. Undoing edits back to their original values is a no-op.
        val retained = d.items.mapTo(HashSet()) { it.id }
        val countSave = usageEnabled && d.items.isNotEmpty() && before.filter { it.id in retained } != d.items
        val next = journal.withDay(d.localDate, d.items)
        savingDraft = true
        viewModelScope.launch {
            val result = runCatching { store?.saveJournal(next) }
            savingDraft = false
            if (result.isFailure) {
                saveFailed = true
                return@launch
            }
            journal = next
            if (countSave) usage?.recordSaved()
            closeEditor(d.localDate, keepDraft = false, saved = true)
        }
    }

    /** Back keeps the draft; "+" on the home continues it. */
    fun leaveEditor() {
        val d = draft ?: return
        val pending = d.raw.isNotBlank() || d.changed
        closeEditor(d.localDate, keepDraft = pending)
    }

    /**
     * [saved]: ✓ succeeded, so this day's draft is gone and the day view of the same date opens
     * (empty again if its last fragment was removed). Otherwise (back) the draft is kept for that
     * date only if it has unsaved changes, and the previous view stays.
     */
    private fun closeEditor(date: LocalDate, keepDraft: Boolean, saved: Boolean = false) {
        val d = draft
        keptDrafts = if (keepDraft && d != null) keptDrafts + (d.date to d) else keptDrafts - date.toString()
        draft = null
        persistDraft()
        saveFailed = false
        selection = null
        clearUndo()
        if (twoPane) {
            // Two panes (§3, 7efb087): the calendar pane keeps its level, committed month and scroll.
            // Saving uses the day pane (level DAILY, the calendar level kept as the return level); it
            // moves only by following a changed date, or when its month left the list (reconcile).
            val before = selectedDate
            if (saved || journal.itemsOn(date).isNotEmpty() || level == Level.DAILY) selectedDate = date
            screen = Screen.Browse
            if (saved) useDayPane()
            if (selectedDate != before) followInCalendar()
            reconcileRecorded()
            if (tour != null && saved) tour.didSave(journal.itemsOn(date).isNotEmpty())
            return
        }
        if (saved || journal.itemsOn(date).isNotEmpty() || level == Level.DAILY) selectedDate = date
        recordedMonth = listedMonth(YearMonth.from(selectedDate))
        screen = Screen.Browse
        if (saved && level != Level.DAILY) {
            dailyReturnLevel = level
            level = Level.DAILY
            entry = ListEntry.Anchor(null)
        }
        if (tour != null && saved) {
            tour.didSave(journal.itemsOn(date).isNotEmpty())
        } else if (tour != null && tour.guided) {
            tour.replayBrowse(level)
        }
        focusRequest++
    }

    private fun persistDraft() {
        val store = store ?: return
        val current = draft
        val all = (keptDrafts + (current?.let { mapOf(it.date to it) } ?: emptyMap())).values.filter { it.hasContent || it == current }
        val file = com.tokipin.app.data.DraftsFile(active = current?.date, drafts = all.toList())
        // One writer, in order, so a later state is never overwritten by an earlier one.
        viewModelScope.launch(draftWriter) { store.writeDrafts(file) }
    }

    // endregion

    // region Browse

    /**
     * How a list view should position itself when it appears: [Restore] puts it back where the
     * person left it (back from a day), [Anchor] brings the selected date to [Anchor.y] px from the
     * top (pinch: the date stays where the fingers were; null = default spot).
     */
    sealed interface ListEntry {
        data object Restore : ListEntry
        data class Anchor(val y: Float?) : ListEntry
    }

    data class ScrollPos(val index: Int, val offset: Int)

    /** Selected day, visible month/year and per-level scroll position are kept apart (UX v0.1 §2). */
    var visibleMonth by mutableStateOf(com.tokipin.app.rules.AppClock.thisMonth()); private set
    var entry by mutableStateOf<ListEntry>(ListEntry.Anchor(null)); private set
    private val scrollMemory = mutableMapOf<Level, ScrollPos>()

    fun rememberScroll(level: Level, pos: ScrollPos) { scrollMemory[level] = pos }
    fun savedScroll(level: Level): ScrollPos? = scrollMemory[level]

    // region Calendar range (Tokipin-Calendar-Range)

    /** The device's local date; re-read when the app returns to the foreground. */
    var today by mutableStateOf(com.tokipin.app.rules.AppClock.today()); private set
    fun refreshToday() {
        today = com.tokipin.app.rules.AppClock.today()
        reconcileRecorded()
    }

    /** From the earlier of 11 months ago and the oldest saved record, to the current month. */
    val range: com.tokipin.app.rules.CalendarRange
        get() = memo("range") { com.tokipin.app.rules.CalendarRange.of(journal.dates.lastOrNull()?.let(::listOf).orEmpty(), today) }

    val recordedMonths: Set<YearMonth> get() = journal.months

    /**
     * Values derived from (journal, today), kept until either changes: the screens read them on
     * every recomposition, and with ten years of records recomputing them each time shows as jank.
     */
    private val memos = HashMap<String, Any>()
    private var memoJournal: Journal? = null
    private var memoToday: LocalDate? = null
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> memo(name: String, compute: () -> T): T {
        // Same journal object (by identity: comparing two journals' contents is itself O(n)) and same day.
        val j = journal
        val t = today
        if (memoJournal !== j || memoToday != t) { memos.clear(); memoJournal = j; memoToday = t }
        return memos.getOrPut(name, compute) as T
    }

    var expandedMonthGaps by mutableStateOf<Set<YearMonth>>(emptySet()); private set
    var expandedYearGaps by mutableStateOf<Set<Int>>(emptySet()); private set

    fun toggleMonthGap(from: YearMonth) {
        expandedMonthGaps = if (from in expandedMonthGaps) expandedMonthGaps - from else expandedMonthGaps + from
    }

    fun toggleYearGap(from: Int) {
        expandedYearGaps = if (from in expandedYearGaps) expandedYearGaps - from else expandedYearGaps + from
    }

    /** Entering a month that sits in a folded gap (from the year view or a pinch) opens that gap. */
    private fun revealMonth(month: YearMonth) {
        com.tokipin.app.rules.CalendarFolding.gapHolding(range, recordedMonths, month)?.let { expandedMonthGaps = expandedMonthGaps + it }
    }

    // endregion

    // region 기록한 날 (v0.3): one page per month of the visible-month list, separate from the selected day.

    private val months = com.tokipin.app.rules.RecordedMonths(com.tokipin.app.rules.AppClock.thisMonth(), com.tokipin.app.rules.AppClock.today())

    /** Months with a saved fragment up to this month, plus this month; drafts never change it. */
    val visibleRecordMonths: List<YearMonth>
        get() = memo("visibleMonths") { com.tokipin.app.rules.RecordedMonths.visibleMonthsOf(journal.months, today) }

    var recordedMonth by mutableStateOf(com.tokipin.app.rules.AppClock.thisMonth()); private set

    /** The committed month if it is listed, otherwise the nearest listed month (ties: more recent). */
    private fun listedMonth(requested: YearMonth): YearMonth =
        com.tokipin.app.rules.RecordedMonths.nearest(visibleRecordMonths, requested)

    /**
     * After a delete, restore, import, month boundary or return to the app: keep the committed
     * month while it is listed; when it is gone, the nearest one. Silent (no haptic), and the
     * selected day stays.
     */
    private fun reconcileRecorded() {
        months.reset(recordedMonth, selectedDate, visibleRecordMonths)
        months.refresh(visibleRecordMonths)
        if (months.committed != recordedMonth) {
            recordedMonth = months.committed
            recordedPreview = null
            // Two panes: the calendar pane may show 기록한 날 while the day pane was used last.
            if ((if (twoPane) calendarLevel else level) == Level.RECORDS) { entry = ListEntry.Anchor(null); focusRequest++ }
        }
    }
    var recordedPreview by mutableStateOf<YearMonth?>(null); private set

    /** Where the person was reading when they opened a day from a card (month, top card, offset). */
    data class RecordedRestore(val month: YearMonth, val topCard: LocalDate?, val offset: Int)
    var recordedRestore: RecordedRestore? = null
        private set

    /** A 기록한 날 page leaving the screen (a layout change) keeps its reading position too. */
    fun rememberRecordedReading(month: YearMonth, topCard: LocalDate?, offset: Int) {
        // Only when the page goes because the layout changed (기록한 날 still shown); opening a day
        // in one column has already kept its own position (openDayFromRecords).
        val shown = if (twoPane) calendarLevel else level
        if (month == recordedMonth && shown == Level.RECORDS) recordedRestore = RecordedRestore(month, topCard, offset)
    }

    /** Runs one recorded-days action and returns its events for the single haptic decision. */
    fun recorded(action: com.tokipin.app.rules.RecordedMonths.() -> List<com.tokipin.app.rules.NavEvent>): List<com.tokipin.app.rules.NavEvent> {
        months.reset(recordedMonth, selectedDate, visibleRecordMonths)
        val events = months.action()
        if (recordedMonth != months.committed) tour?.advance(BrowseCue.RecordsSwipe, BrowseCue.RecordsZoom)
        recordedMonth = months.committed
        recordedPreview = months.preview
        if (months.selectedDay != selectedDate) selectedDate = months.selectedDay
        // Two panes: committing a month in the calendar pane means the calendar pane was used last
        // (paneNavigation.recordedMonthCommit). 오늘 and silent recalculations leave level alone.
        if (twoPane && com.tokipin.app.rules.NavEvent.RECORDED_MONTH_COMMIT in events && level != Level.RECORDS) level = Level.RECORDS
        if (com.tokipin.app.rules.NavEvent.RECORDED_MONTH_COMMIT in events || com.tokipin.app.rules.NavEvent.TODAY in events) {
            visibleMonth = recordedMonth
            entry = ListEntry.Anchor(null)
            focusRequest++
        }
        return events
    }

    /** Opening a day from a recorded-days card remembers the reading position first. */
    fun openDayFromRecords(date: LocalDate, topCard: LocalDate?, offset: Int): Boolean {
        recordedRestore = RecordedRestore(recordedMonth, topCard, offset)
        return openDay(date)
    }

    // endregion

    /** Scrolling only changes what is visible; the selected day stays (12월 → 1월 included). */
    fun scrolledTo(month: YearMonth) { visibleMonth = month }

    /** Returns whether the level changed, i.e. whether this is a committed navigation (one tick). */
    fun changeLevel(next: Level, entryMode: ListEntry = ListEntry.Anchor(null)): Boolean {
        if (next == level) return false
        // Two panes: the calendar pane already shows the day's return level; choosing it only
        // records that the calendar pane was used last (no tick, the list stays where it is).
        if (twoPane && next != Level.DAILY && next == calendarLevel) { level = next; return false }
        if (next == Level.DAILY) dailyReturnLevel = level
        if (next == Level.MONTH) revealMonth(YearMonth.from(selectedDate))
        if (next == Level.RECORDS) {
            val restore = recordedRestore
            // Not listed (an empty past month, or a month that has since lost its last record):
            // the nearest listed month, ties to the more recent one. The selected day stays.
            recordedMonth = listedMonth(if (entryMode is ListEntry.Restore && restore != null) restore.month else YearMonth.from(selectedDate))
            recordedPreview = null
        }
        val previous = level
        level = next
        tour?.enteredLevel(previous, next)
        entry = entryMode
        visibleMonth = YearMonth.from(selectedDate)
        openedFromSearch = false
        highlightId = null
        focusRequest++
        return true
    }

    /**
     * Pinch: one level per gesture around the date or month first under the fingers. [anchorY] is
     * where that date was on screen, so the next view can keep it there.
     */
    fun pinchTo(next: Level, target: Any?, anchorY: Float?): Boolean {
        if (twoPane) {
            // Only the calendar pane pinches, starting from the level it shows. Spreading into a day
            // opens that date in the day pane and leaves the calendar pane as it is.
            if (next == calendarLevel) return false
            val before = selectedDate
            when (target) {
                is LocalDate -> selectedDate = target
                is YearMonth -> if (YearMonth.from(selectedDate) != target) selectedDate = journal.datesIn(target).firstOrNull() ?: target.atDay(1)
            }
            if (next == Level.DAILY) { useDayPane(); return selectedDate != before }
            return changeLevel(next, ListEntry.Anchor(anchorY)) || selectedDate != before
        }
        if (next == level) return false
        when (target) {
            is LocalDate -> selectedDate = target
            is YearMonth -> if (YearMonth.from(selectedDate) != target) {
                selectedDate = journal.datesIn(target).firstOrNull() ?: target.atDay(1)
            }
        }
        return changeLevel(next, ListEntry.Anchor(anchorY))
    }

    /** Where "<" leads (TalkBack names it); null when there is no back button (year view, search day). */
    val upTarget: Level?
        get() = if (twoPane) Level.YEAR.takeIf { calendarLevel != Level.YEAR } else when (level) {
            Level.DAILY -> if (openedFromSearch) null else dailyReturnLevel.takeIf { it != Level.DAILY } ?: Level.RECORDS
            Level.RECORDS, Level.MONTH -> Level.YEAR
            Level.YEAR -> null
        }

    /** The "< parent" button and system back. Closing a day returns to the view and spot it was opened from. */
    fun goUp(): Boolean = when {
        // Two panes: the search list closes first; otherwise up from the level the calendar pane
        // shows, never "closing" the day pane.
        twoPane && openedFromSearch -> { closeSearch(); false }
        twoPane -> calendarLevel != Level.YEAR && changeLevel(Level.YEAR)
        level == Level.DAILY && openedFromSearch -> { reopenSearch(); false }
        level == Level.DAILY -> changeLevel(dailyReturnLevel.takeIf { it != Level.DAILY } ?: Level.RECORDS, ListEntry.Restore)
        level == Level.RECORDS || level == Level.MONTH -> changeLevel(Level.YEAR)
        else -> false
    }

    /** Returns whether the selected date actually changed (re-selecting is silent). */
    fun selectDate(date: LocalDate): Boolean {
        highlightId = null
        if (date == selectedDate) return false
        if (level == Level.DAILY) tour?.advance(BrowseCue.DaySwipe, BrowseCue.DayZoom)
        selectedDate = date
        return true
    }

    /**
     * A date chosen in the day view (week rail, day or week swipe). In two panes the calendar pane
     * follows silently.
     */
    fun selectDay(date: LocalDate): Boolean {
        val changed = selectDate(date)
        if (twoPane) {
            useDayPane()
            if (changed) followInCalendar()
        }
        return changed
    }

    fun openDay(date: LocalDate): Boolean {
        if (twoPane) {
            // The calendar pane keeps its level and scroll; the date opens in the day pane.
            val changed = selectDate(date)
            useDayPane()
            return changed
        }
        val pickedLastDay = level == Level.MONTH && tour?.guided == true && tour.cue == BrowseCue.PickDay
        val changed = selectDate(date)
        val opened = changeLevel(Level.DAILY)
        if (pickedLastDay && opened) tour?.invite()
        return opened || changed
    }

    fun openMonth(month: YearMonth): Boolean {
        selectedDate = if (YearMonth.from(selectedDate) == month) selectedDate
        else journal.datesIn(month).firstOrNull() ?: month.atDay(1)
        return changeLevel(Level.MONTH)
    }

    /** Today keeps the current view and brings today back on screen, even if it is already selected. Silent. */
    fun goToday() {
        // Two panes: both panes show today; the calendar pane brings it into view. Silent.
        if (twoPane && calendarLevel == Level.RECORDS && level == Level.DAILY) {
            selectedDate = com.tokipin.app.rules.AppClock.today()
            highlightId = null
            followInCalendar()
            return
        }
        if (level == Level.RECORDS) { recorded { today(com.tokipin.app.rules.AppClock.today()) }; return }
        selectedDate = com.tokipin.app.rules.AppClock.today()
        visibleMonth = com.tokipin.app.rules.AppClock.thisMonth()
        highlightId = null
        entry = ListEntry.Anchor(null)
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
            offerUndo(res.getQuantityString(R.plurals.day_deleted, before.size, before.size)) {
                persistJournal(journal.withDay(date, before))
            }
        }
    }

    private fun persistJournal(next: Journal, then: () -> Unit = {}) {
        viewModelScope.launch {
            if (runCatching { store?.saveJournal(next) }.isSuccess) {
                journal = next
                reconcileRecorded()
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
        if (twoPane) {
            // The calendar pane goes back to its view and shows the selected date (the result's
            // date when one was opened).
            openedFromSearch = false
            highlightId = null
            visibleMonth = YearMonth.from(selectedDate)
            entry = ListEntry.Anchor(null)
            focusRequest++
        }
    }

    fun openSearchResult(date: LocalDate, entry: Entry) {
        selectedDate = date
        recordedMonth = listedMonth(YearMonth.from(date))
        if (level != Level.DAILY) dailyReturnLevel = level
        level = Level.DAILY
        tour?.replayBrowse(level)
        this.entry = ListEntry.Anchor(null)
        openedFromSearch = true
        highlightId = entry.id
        screen = Screen.Browse
        focusRequest++
    }

    // endregion

    // region Settings

    fun openSettings() { settingsOpen = true }
    fun closeSettings() {
        replayJob?.cancel()
        settingsOpen = false
    }
    fun openPolicy(p: Policy?) { policy = p }

    fun setHaptics(enabled: Boolean) {
        hapticsEnabled = enabled
        store?.hapticsEnabled = enabled
    }

    /** Debug builds (개발용 테스트 데이터): replace all records with the homepage's sample journal. */
    fun replaceWithSamples() = replaceAll(com.tokipin.app.data.SampleData.journal(), R.string.debug_samples_done)

    fun replaceWithCalendarRangeSamples() = replaceWithAsset("calendar-range-records.json", R.string.debug_range_done)

    /** Debug builds: the homepage generator over ten years (shared with iOS) for speed checks. */
    fun replaceWithLargeSamples() {
        if (!com.tokipin.app.data.SampleData.AVAILABLE) return
        viewModelScope.launch {
            val next = withContext(Dispatchers.Default) { com.tokipin.app.data.SampleData.large().warmed() }
            replaceAll(next, R.string.debug_large_done)
        }
    }

    private fun replaceWithAsset(name: String, done: Int) {
        val store = store ?: return
        if (!com.tokipin.app.data.SampleData.AVAILABLE) return
        viewModelScope.launch {
            val next = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().assets.open(name).bufferedReader().use {
                        store.decodeJournal(it.readText()).warmed()
                    }
                }
            } catch (e: java.io.IOException) {
                transientMessage = res.getString(R.string.debug_range_failed)
                return@launch
            } catch (e: kotlinx.serialization.SerializationException) {
                transientMessage = res.getString(R.string.debug_range_failed)
                return@launch
            }
            replaceAll(next, done)
        }
    }

    /** Debug builds: clear every record and the draft, to check the empty home. */
    fun clearAllRecords() = replaceAll(Journal(), R.string.debug_cleared)

    private fun replaceAll(next: Journal, message: Int) {
        persistJournal(next) {
            draft = null
            keptDrafts = emptyMap()
            persistDraft()
            clearUndo()
            transientMessage = res.getString(message)
            selectedDate = next.recordedDates().firstOrNull() ?: com.tokipin.app.rules.AppClock.today()
            visibleMonth = YearMonth.from(selectedDate)
            recordedMonth = listedMonth(visibleMonth)
            entry = ListEntry.Anchor(null)
            focusRequest++
        }
    }

    fun export(uri: Uri) {
        val store = store ?: return
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

    init {
        if (tour == null) {
            load()
        } else {
            journal = tour.seed
            draft = Draft(date = today.toString(), items = tour.starter)
            editorAutoFocus = false
            screen = Screen.Editor
        }
    }

    companion object {
        private const val SKELETON_DELAY_MS = 350L
        // Figma 03 · 기록일 하루 전체: "삭제 후 8초간 실행 취소".
        private const val UNDO_MS = 8_000L
    }
}
