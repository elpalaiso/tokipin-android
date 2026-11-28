package com.tokipin.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tokipin.app.R
import com.tokipin.app.data.Entry
import com.tokipin.app.data.EntryKind
import com.tokipin.app.data.Journal
import java.time.LocalDate

data class SearchHit(val date: LocalDate, val entry: Entry)

/**
 * Words, emoji, places, times and dates are searchable (Figma 03 · 검색 대상). Typing a kind's name
 * ("장소", "Place") lists every item of that kind. Newest dates first, entered order within a day.
 */
fun search(
    journal: Journal,
    query: String,
    kindNames: Map<EntryKind, String>,
    locale: java.util.Locale = com.tokipin.app.AppLocale.format,
    shown: (Entry) -> String = { it.value },
): List<SearchHit> = SearchIndex(journal, locale, shown).find(query, kindNames)

/**
 * Searchable text of every saved day, prepared once per journal, language and time format: the
 * stored values, what the screen shows (a time as "6:45 PM"), and the date both as stored (ISO) and
 * as written in the app ("2026.11.25(Wed)"), so whatever is on screen can be typed back. Text is
 * folded with the app language's case rules (Turkish İ/ı kept apart) and one kind of space, then compared off the main thread.
 */
class SearchIndex(journal: Journal, private val locale: java.util.Locale, shown: (Entry) -> String) {
    private class Day(val date: LocalDate, val dateText: String, val entries: List<Entry>, val texts: List<String>)

    /**
     * Comparable form: the language's own lower case (Turkish İ→i, I→ı, so kir and kır stay
     * different), a stray combining dot dropped (İ lower-cased outside Turkish), and every kind of
     * space — the narrow no-break space formatters put in "6:45 PM", no-break spaces — made one
     * ordinary space, so a time typed on a keyboard finds the time shown on screen.
     */
    fun fold(text: String): String = text.lowercase(locale).replace("\u0307", "").replace(SPACES, " ")

    private companion object {
        val SPACES = Regex("[\\s\\u00A0\\u2007\\u202F\\u2000-\\u200A\\u3000]+")
    }

    private val days: List<Day> = journal.dates.map { date ->
        val entries = journal.itemsOn(date)
        Day(
            date,
            fold(searchableDate(date) + " " + date.fullLabel(locale) + " " + date.toString()),
            entries,
            entries.map { e -> fold(if (e.kind == EntryKind.TIME) e.value + " " + shown(e) else e.value) },
        )
    }

    fun find(query: String, kindNames: Map<EntryKind, String>): List<SearchHit> {
        val q = fold(query.trim())
        if (q.isEmpty()) return emptyList()
        val kinds = kindNames.filterValues { fold(it) == q }.keys
        val hits = ArrayList<SearchHit>()
        for (day in days) {
            val dateMatches = day.dateText.contains(q)
            day.entries.forEachIndexed { i, entry ->
                if (dateMatches || entry.kind in kinds || day.texts[i].contains(q)) hits += SearchHit(day.date, entry)
            }
        }
        return hits
    }
}

@Composable
fun SearchScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val kindNames = remember { EntryKind.entries.associateWith { context.getString(it.label()) } }
    // Results are worked out on a background thread; the field never waits for them. The index is
    // built once per journal, and an older query's result is dropped when a newer one arrives.
    val journal = vm.journal
    val query = vm.searchQuery
    // Rebuilt when the journal, the app language or the time format changes.
    val locale = com.tokipin.app.AppLocale.format
    val timeDisplay = LocalTimeDisplay.current
    var index by remember(journal, locale, timeDisplay) { mutableStateOf<SearchIndex?>(null) }
    LaunchedEffect(journal, locale, timeDisplay) { index = withContext(Dispatchers.Default) { SearchIndex(journal, locale, timeDisplay::text) } }
    var hits by remember { mutableStateOf(emptyList<SearchHit>()) }
    var hitsFor by remember { mutableStateOf("") }
    LaunchedEffect(index, query) {
        val idx = index ?: return@LaunchedEffect
        hits = withContext(Dispatchers.Default) { idx.find(query, kindNames) }
        hitsFor = query
    }
    BackHandler { vm.closeSearch() }
    // Recreated after a rotation or fold with the keyboard put away: leave it down (UX v0.4 §5).
    val keyboardPutAway = rememberKeyboardPutAway()
    LaunchedEffect(Unit) {
        if (!keyboardPutAway.value) runCatching { focus.requestFocus() }
        else {
            // The restored window may bring the field's focus (and so the keyboard) back on its own.
            androidx.compose.runtime.withFrameNanos { }
            keyboard?.hide()
        }
    }
    // Saveable so the cursor survives activity recreation, like the editor's field.
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(vm.searchQuery, TextRange(vm.searchQuery.length)))
    }
    if (field.text != vm.searchQuery && field.composition == null) field = TextFieldValue(vm.searchQuery, TextRange(vm.searchQuery.length))

    TkBackground {
        Column(Modifier.fillMaxSize().contentColumn().statusBarsPadding().imePadding()) {
            SampleControls(vm)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(R.drawable.ic_back, stringResource(R.string.cd_close_search), vm::closeSearch)
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier.weight(1f).height(48.dp).glass(RoundedCornerShape(24.dp)).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TkIcon(R.drawable.ic_search, size = 18.dp, tint = Tk.Secondary)
                    Spacer(Modifier.width(10.dp))
                    val hint = stringResource(R.string.search_hint)
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it; if (it.text != vm.searchQuery) vm.updateSearch(it.text) },
                        singleLine = true,
                        textStyle = TkType.body,
                        cursorBrush = SolidColor(Tk.Primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.weight(1f).focusRequester(focus).testTag("searchField").semantics { contentDescription = hint },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (vm.searchQuery.isEmpty()) Text(hint, style = TkType.body.copy(color = Tk.Muted))
                                inner()
                            }
                        },
                    )
                    if (vm.searchQuery.isNotEmpty()) {
                        val clear = stringResource(R.string.cd_clear_search)
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { vm.updateSearch("") }
                                .semantics { contentDescription = clear },
                            contentAlignment = Alignment.Center,
                        ) { TkIcon(R.drawable.ic_close, size = 16.dp, tint = Tk.Secondary) }
                    }
                }
            }
            if (vm.searchQuery.isNotBlank() && hitsFor == vm.searchQuery && hits.isEmpty()) {
                Text(stringResource(R.string.search_none), style = TkType.body.copy(color = Tk.Secondary), modifier = Modifier.padding(24.dp))
            }
            LazyColumn(modifier = Modifier.testTag("searchResults"), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                var lastDate: LocalDate? = null
                hits.forEach { hit ->
                    val header = hit.date != lastDate
                    lastDate = hit.date
                    item(key = hit.entry.id) {
                        Column {
                            if (header) Text(hit.date.fullLabel(), style = TkType.small, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    .clickable(role = Role.Button) { vm.openSearchResult(hit.date, hit.entry) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (hit.entry.kind != EntryKind.EMOJI) {
                                    TkIcon(hit.entry.kind.icon(), size = 16.dp, tint = Tk.Secondary)
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(hit.entry.shown(), style = TkType.body, modifier = Modifier.weight(1f))
                                TkIcon(R.drawable.ic_chevron_right, size = 16.dp, tint = Tk.Secondary)
                            }
                            HorizontalDivider(color = Tk.Divider)
                        }
                    }
                }
            }
        }
    }
}
