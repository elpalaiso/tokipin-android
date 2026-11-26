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
fun search(journal: Journal, query: String, kindNames: Map<EntryKind, String>): List<SearchHit> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val kinds = kindNames.filterValues { it.lowercase() == q }.keys
    return journal.recordedDates().flatMap { date ->
        val dateText = searchableDate(date)
        val dateMatches = dateText.contains(q) || date.toString().contains(q)
        journal.itemsOn(date).filter { entry ->
            dateMatches || entry.kind in kinds || entry.value.lowercase().contains(q)
        }.map { SearchHit(date, it) }
    }
}

@Composable
fun SearchScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val kindNames = remember { EntryKind.entries.associateWith { context.getString(it.label()) } }
    val hits = remember(vm.searchQuery, vm.journal) { search(vm.journal, vm.searchQuery, kindNames) }
    BackHandler { vm.closeSearch() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    TkBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
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
                        value = vm.searchQuery,
                        onValueChange = vm::updateSearch,
                        singleLine = true,
                        textStyle = TkType.body,
                        cursorBrush = SolidColor(Tk.Primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.weight(1f).focusRequester(focus).semantics { contentDescription = hint },
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
            if (vm.searchQuery.isNotBlank() && hits.isEmpty()) {
                Text(stringResource(R.string.search_none), style = TkType.body.copy(color = Tk.Secondary), modifier = Modifier.padding(24.dp))
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
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
