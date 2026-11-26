package com.tokipin.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokipin.app.R
import com.tokipin.app.data.Entry
import com.tokipin.app.data.EntryKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private fun firstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
private fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))
private fun weekdays(): List<DayOfWeek> = (0L until 7L).map { firstDayOfWeek().plus(it) }
private fun DayOfWeek.short(): String = getDisplayName(DateTextStyle.NARROW, Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(vm: AppViewModel) {
    val haptics = LocalHaptics.current
    var dayMenuFor by remember { mutableStateOf<LocalDate?>(null) }

    BackHandler(enabled = vm.level == Level.DAILY || (vm.level != Level.YEAR && !vm.journal.isEmpty)) {
        vm.goUp(); haptics.tick()
    }

    TkBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            BrowseTopBar(vm, onDayMenu = { dayMenuFor = vm.selectedDate })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pinchLevels(
                        onZoomIn = { if (vm.level != Level.DAILY) { vm.zoomIn(); haptics.tick() } },
                        onZoomOut = { if (vm.level != Level.YEAR) { vm.zoomOut(); haptics.tick() } },
                    ),
            ) {
                when {
                    vm.journal.isEmpty && (vm.level == Level.RECORDS || vm.level == Level.YEAR) -> EmptyHome(vm)
                    vm.level == Level.DAILY -> DailyView(vm)
                    vm.level == Level.RECORDS -> RecordsView(vm, onLongPress = { dayMenuFor = it })
                    vm.level == Level.MONTH -> MonthView(vm)
                    else -> YearView(vm)
                }
            }
        }
        BottomActions(vm)
    }

    dayMenuFor?.let { date ->
        val count = vm.journal.itemsOn(date).size
        if (count == 0) { dayMenuFor = null; return@let }
        ModalBottomSheet(
            onDismissRequest = { dayMenuFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Tk.Sheet,
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(date.fullLabel(), style = TkType.title, modifier = Modifier.semantics { heading() })
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(Tk.DangerSoft)
                        .clickable(role = Role.Button) { vm.deleteDay(date); dayMenuFor = null }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TkIcon(R.drawable.ic_trash, tint = Tk.DangerText, size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.delete_day, count), style = TkType.body.copy(color = Tk.DangerText))
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).glass(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) { dayMenuFor = null },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.cancel), style = TkType.body) }
            }
        }
    }
}

@Composable
private fun BrowseTopBar(vm: AppViewModel, onDayMenu: () -> Unit) {
    val haptics = LocalHaptics.current
    var menuOpen by remember { mutableStateOf(false) }
    val parent = when (vm.level) {
        Level.DAILY -> if (vm.openedFromSearch) null else vm.selectedDate.monthValue.toString()
        Level.RECORDS, Level.MONTH -> vm.selectedDate.year.toString()
        Level.YEAR -> null
    }
    Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (parent != null && !(vm.journal.isEmpty && vm.level != Level.DAILY && vm.level != Level.MONTH)) {
            val backLabel = stringResource(R.string.cd_back_to, parent)
            Row(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { vm.goUp(); haptics.tick() }
                    .semantics(mergeDescendants = true) { contentDescription = backLabel }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TkIcon(R.drawable.ic_back, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                Text(parent, style = TkType.title)
            }
        } else if (vm.journal.isEmpty) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TkIcon(R.drawable.ic_back, size = 18.dp, tint = Tk.Primary.copy(alpha = 0.5f))
                Spacer(Modifier.width(6.dp))
                Text(LocalDate.now().year.toString(), style = TkType.title)
            }
        }
        if (vm.openedFromSearch && vm.level == Level.DAILY) {
            GlassPill(stringResource(R.string.search_results), onClick = { vm.goUp() }, leading = R.drawable.ic_search)
        }
        Spacer(Modifier.weight(1f))
        if (vm.level == Level.DAILY && vm.journal.itemsOn(vm.selectedDate).isNotEmpty()) {
            RoundIconButton(R.drawable.ic_more, stringResource(R.string.cd_day_menu), onDayMenu, glass = false)
        }
        RoundIconButton(R.drawable.ic_search, stringResource(R.string.cd_search), vm::openSearch, glass = false)
        Box {
            RoundIconButton(R.drawable.ic_views, stringResource(R.string.cd_view_menu), { menuOpen = true }, glass = false)
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = Tk.Sheet,
                shape = RoundedCornerShape(18.dp),
            ) {
                listOf(
                    Triple(Level.DAILY, R.string.view_daily, R.drawable.ic_view_day),
                    Triple(Level.RECORDS, R.string.view_records, R.drawable.ic_view_bento),
                    Triple(Level.MONTH, R.string.view_month, R.drawable.ic_view_month),
                    Triple(Level.YEAR, R.string.view_year, R.drawable.ic_view_year),
                ).forEach { (lvl, label, icon) ->
                    val current = vm.level == lvl
                    DropdownMenuItem(
                        text = { Text(stringResource(label), style = TkType.body) },
                        leadingIcon = { TkIcon(icon, size = 20.dp) },
                        trailingIcon = if (current) ({ TkIcon(R.drawable.ic_check, size = 18.dp, tint = Tk.Secondary) }) else null,
                        modifier = Modifier.semantics { selected = current },
                        onClick = {
                            menuOpen = false
                            if (!current) { vm.changeLevel(lvl); haptics.tick() }
                        },
                    )
                }
                HorizontalDivider(color = Tk.Divider, modifier = Modifier.padding(horizontal = 12.dp))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings), style = TkType.body) },
                    leadingIcon = { TkIcon(R.drawable.ic_gear, size = 20.dp) },
                    onClick = { menuOpen = false; vm.openSettings() },
                )
            }
        }
    }
}

@Composable
private fun BoxScope.BottomActions(vm: AppViewModel) {
    Column(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        vm.undo?.let { UndoBar(it) }
        if (!vm.journal.isEmpty || vm.level == Level.DAILY || vm.level == Level.MONTH) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassPill(stringResource(R.string.today), onClick = vm::goToday)
                Spacer(Modifier.weight(1f))
                RoundIconButton(
                    R.drawable.ic_plus, stringResource(R.string.cd_new_record), { vm.openEditor() },
                    modifier = Modifier.size(56.dp), background = Tk.SheetRow.copy(alpha = 0.95f),
                )
            }
        }
    }
}

@Composable
private fun EmptyHome(vm: AppViewModel) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.home_empty), style = TkType.title)
            GlassPill(stringResource(R.string.home_first_record), onClick = { vm.openEditor(LocalDate.now()) }, leading = R.drawable.ic_plus)
        }
        // The rabbit peeks in from the corner (Figma 01 · 토끼는 모서리에서 빼꼼).
        Image(
            painterResource(R.drawable.ic_peek_rabbit),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xFFA9BC9E)),
            modifier = Modifier.align(Alignment.BottomEnd).size(200.dp).offset(x = 20.dp, y = 8.dp),
        )
    }
}

// region Daily

@Composable
private fun DailyView(vm: AppViewModel) {
    val haptics = LocalHaptics.current
    val date = vm.selectedDate
    val items = vm.journal.itemsOn(date)
    Column(Modifier.fillMaxSize()) {
        WeekStrip(date, onSelect = { if (it != date) { vm.selectDate(it); haptics.tick() } }, onShiftWeek = { weeks ->
            vm.selectDate(date.plusWeeks(weeks.toLong())); haptics.tick()
        })
        HorizontalDivider(color = Tk.Divider, modifier = Modifier.padding(horizontal = 20.dp))
        // Body: swipe left for the next day, right for the previous day.
        var dragTotal by remember { mutableStateOf(0f) }
        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(date) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onDragEnd = {
                            if (dragTotal < -120f) { vm.selectDate(date.plusDays(1)); haptics.tick() }
                            else if (dragTotal > 120f) { vm.selectDate(date.minusDays(1)); haptics.tick() }
                        },
                        onHorizontalDrag = { _, amount -> dragTotal += amount },
                    )
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (items.isEmpty()) {
                Text(stringResource(R.string.day_empty), style = TkType.body.copy(color = Tk.Secondary))
            }
            items.forEachIndexed { index, entry ->
                DailyItem(
                    entry = entry,
                    lead = index == 0,
                    highlighted = vm.highlightId == entry.id,
                    onEdit = { vm.editSavedItem(date, entry) },
                    onDelete = { vm.deleteSavedItem(date, entry) },
                )
            }
        }
    }
}

@Composable
private fun WeekStrip(date: LocalDate, onSelect: (LocalDate) -> Unit, onShiftWeek: (Int) -> Unit) {
    val start = date.weekStart()
    var drag by remember { mutableStateOf(0f) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pointerInput(date) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag < -80f) onShiftWeek(1) else if (drag > 80f) onShiftWeek(-1) },
                    onHorizontalDrag = { _, amount -> drag += amount },
                )
            },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        (0L until 7L).map { start.plusDays(it) }.forEach { day ->
            val active = day == date
            val label = day.fullLabel()
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Tab) { onSelect(day) }
                    .semantics(mergeDescendants = true) { contentDescription = label; selected = active }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(day.dayOfWeek.short(), style = TkType.small)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(if (active) Tk.Focus else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.dayOfMonth.toString(),
                        style = TkType.body.copy(color = if (active) Tk.OnFocus else Tk.Primary, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyItem(entry: Entry, lead: Boolean, highlighted: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val more = stringResource(R.string.cd_item_menu, entry.value)
    val shape = RoundedCornerShape(if (lead) 22.dp else 10.dp)
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (lead) 128.dp else 64.dp)
                .then(
                    when {
                        lead -> Modifier.clip(shape).background(Tk.Card).border(0.8.dp, Tk.CardBorder, shape)
                        highlighted -> Modifier.clip(shape).background(Tk.SheetRow.copy(alpha = 0.7f))
                        else -> Modifier
                    },
                )
                .then(if (highlighted) Modifier.border(1.dp, Tk.Focus.copy(alpha = 0.7f), shape) else Modifier)
                .padding(start = if (lead) 22.dp else 4.dp),
            verticalAlignment = if (lead) Alignment.Top else Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).padding(top = if (lead) 32.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!lead && (entry.kind == EntryKind.PLACE || entry.kind == EntryKind.TIME)) {
                    TkIcon(entry.kind.icon(), tint = Tk.Secondary, size = 16.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(entry.value, style = if (lead) TkType.word else TkType.title)
            }
            MoreButton(more, onClick = { menu = true }, modifier = Modifier.padding(top = if (lead) 4.dp else 0.dp))
        }
        if (!lead) HorizontalDivider(color = Tk.Divider, modifier = Modifier.align(Alignment.BottomCenter))
        Box(Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Tk.Sheet, shape = RoundedCornerShape(16.dp)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.item_edit), style = TkType.body) },
                    leadingIcon = { TkIcon(R.drawable.ic_pencil, size = 18.dp) },
                    onClick = { menu = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.item_delete), style = TkType.body) },
                    leadingIcon = { TkIcon(R.drawable.ic_trash, size = 18.dp) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

// endregion

// region Recorded days (bento)

private sealed interface RecordRow {
    data class Header(val month: YearMonth) : RecordRow
    data class Cards(val dates: List<LocalDate>) : RecordRow
}

/** Months newest first; inside a month the cards alternate one wide, then two side by side. */
private fun recordRows(dates: List<LocalDate>): List<RecordRow> {
    val rows = mutableListOf<RecordRow>()
    dates.groupBy { YearMonth.from(it) }.forEach { (month, days) ->
        rows += RecordRow.Header(month)
        var i = 0
        var wide = true
        while (i < days.size) {
            val take = if (wide) 1 else 2
            rows += RecordRow.Cards(days.subList(i, minOf(i + take, days.size)))
            i += take
            wide = !wide
        }
    }
    return rows
}

@Composable
private fun RecordsView(vm: AppViewModel, onLongPress: (LocalDate) -> Unit) {
    val haptics = LocalHaptics.current
    val rows = remember(vm.journal) { recordRows(vm.journal.recordedDates()) }
    val state = rememberLazyListState()
    LaunchedEffect(vm.focusRequest, rows.size) {
        val target = rows.indexOfFirst { it is RecordRow.Cards && vm.selectedDate in it.dates }
            .let { idx -> if (idx > 0 && rows[idx - 1] is RecordRow.Header) idx - 1 else idx }
        if (target >= 0) state.scrollToItem(target)
        else {
            val month = YearMonth.from(vm.selectedDate)
            val header = rows.indexOfFirst { it is RecordRow.Header && it.month <= month }
            if (header >= 0) state.scrollToItem(header)
        }
    }
    YearTracker(state, rows) { vm.selectDate(it) }
    LazyColumn(state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(rows, key = { _, row -> if (row is RecordRow.Header) "h${row.month}" else "c${(row as RecordRow.Cards).dates.first()}" }) { _, row ->
            when (row) {
                is RecordRow.Header -> Text(
                    row.month.label(), style = TkType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
                )
                is RecordRow.Cards -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.dates.forEach { date ->
                        DayCard(
                            date = date,
                            items = vm.journal.itemsOn(date),
                            wide = row.dates.size == 1,
                            selected = date == vm.selectedDate,
                            onClick = { vm.openDay(date); haptics.tick() },
                            onLongClick = { onLongPress(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Keeps the top-bar year in step with the month at the top of the list. */
@Composable
private fun YearTracker(state: LazyListState, rows: List<RecordRow>, onMonth: (LocalDate) -> Unit) {
    val first by remember(rows) { derivedStateOf { rows.getOrNull(state.firstVisibleItemIndex) } }
    LaunchedEffect(first) {
        val row = first
        if (state.isScrollInProgress && row is RecordRow.Cards) onMonth(row.dates.first())
    }
}

@Composable
private fun DayCard(
    date: LocalDate,
    items: List<Entry>,
    wide: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val title = items.firstOrNull()
    val rest = items.drop(1)
    val description = date.fullLabel() + ", " + items.joinToString(", ") { it.value }
    Column(
        modifier
            .heightIn(min = if (wide) 140.dp else 120.dp)
            .clip(shape)
            .background(Tk.Card)
            .border(if (selected) 1.dp else 0.8.dp, if (selected) Tk.Secondary.copy(alpha = 0.7f) else Tk.CardBorder, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(date.cardLabel(), style = TkType.small)
        Spacer(Modifier.height(14.dp))
        if (title != null) {
            Text(
                title.value, style = if (wide) TkType.title.copy(fontSize = 26.sp) else TkType.title,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (rest.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                rest.joinToString("  ·  ") { it.value }, style = TkType.label,
                maxLines = if (wide) 2 else 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// endregion

// region Month calendar

@Composable
private fun MonthView(vm: AppViewModel) {
    val haptics = LocalHaptics.current
    val today = LocalDate.now()
    val dates = vm.journal.recordedDates()
    val first = YearMonth.from(minOf(dates.lastOrNull() ?: today, today)).minusMonths(12)
    val last = YearMonth.from(maxOf(dates.firstOrNull() ?: today, today)).plusMonths(12)
    val months = remember(first, last) {
        generateSequence(first) { it.plusMonths(1) }.takeWhile { it <= last }.toList()
    }
    val state = rememberLazyListState()
    LaunchedEffect(vm.focusRequest) {
        val idx = months.indexOf(YearMonth.from(vm.selectedDate))
        if (idx >= 0) state.scrollToItem(idx)
    }
    val topMonth by remember { derivedStateOf { months.getOrNull(state.firstVisibleItemIndex) } }
    LaunchedEffect(topMonth) {
        val m = topMonth ?: return@LaunchedEffect
        if (state.isScrollInProgress && m.year != vm.selectedDate.year) vm.selectDate(m.atDay(1))
    }
    Column(Modifier.fillMaxSize()) {
        // Weekday header stays fixed while months scroll underneath (Figma 04 · 월간 해가 바뀌는 경계).
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            weekdays().forEach { Text(it.short(), style = TkType.small, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        LazyColumn(state = state, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 110.dp)) {
            itemsIndexed(months, key = { _, m -> m.toString() }) { _, month ->
                MonthBlock(month, vm, onOpenDay = { vm.openDay(it); haptics.tick() })
            }
        }
    }
}

@Composable
private fun MonthBlock(month: YearMonth, vm: AppViewModel, onOpenDay: (LocalDate) -> Unit) {
    val lead = (month.atDay(1).dayOfWeek.value - firstDayOfWeek().value + 7) % 7
    val cells = lead + month.lengthOfMonth()
    val weeks = (cells + 6) / 7
    val monthDescription = stringResource(R.string.cd_month, month.monthValue)
    Column(Modifier.padding(top = 18.dp)) {
        // The month number sits outside the frame, above the column of the 1st.
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            repeat(7) { col ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (col == lead) {
                        Text(
                            month.monthValue.toString(),
                            style = TkType.title.copy(fontSize = 20.sp),
                            modifier = Modifier.semantics { contentDescription = "${month.year} $monthDescription"; heading() },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(20.dp)
        Column(Modifier.clip(shape).background(Tk.Surface.copy(alpha = 0.55f)).border(0.8.dp, Tk.CardBorder, shape).padding(4.dp)) {
            repeat(weeks) { week ->
                if (week > 0) HorizontalDivider(color = Tk.Divider, modifier = Modifier.padding(horizontal = 6.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = 82.dp)) {
                    repeat(7) { col ->
                        val day = week * 7 + col - lead + 1
                        Box(Modifier.weight(1f).heightIn(min = 82.dp)) {
                            if (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                MonthCell(date, vm.journal.itemsOn(date), date == vm.selectedDate, date == LocalDate.now()) { onOpenDay(date) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCell(date: LocalDate, items: List<Entry>, selected: Boolean, today: Boolean, onClick: () -> Unit) {
    val label = date.fullLabel() + if (items.isEmpty()) "" else ", " + items.joinToString(", ") { it.value }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF4A5C6B) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label; this.selected = selected }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = TkType.label.copy(
                color = if (selected) Tk.Focus else Tk.Primary,
                fontWeight = if (items.isNotEmpty() || today) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
        items.take(3).forEach {
            Text(it.value, style = TkType.small.copy(fontSize = 10.sp, lineHeight = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (items.size > 3) Text(stringResource(R.string.more_count, items.size - 3), style = TkType.small.copy(fontSize = 10.sp))
    }
}

// endregion

// region Year

@Composable
private fun YearView(vm: AppViewModel) {
    val haptics = LocalHaptics.current
    val today = LocalDate.now()
    val dates = vm.journal.recordedDates()
    val firstYear = minOf(dates.lastOrNull()?.year ?: today.year, today.year) - 1
    val lastYear = maxOf(dates.firstOrNull()?.year ?: today.year, today.year) + 1
    val years = (firstYear..lastYear).toList()
    val recorded = remember(vm.journal) { dates.toSet() }
    val state = rememberLazyListState()
    LaunchedEffect(vm.focusRequest) {
        val idx = years.indexOf(vm.selectedDate.year)
        if (idx >= 0) state.scrollToItem(idx)
    }
    LazyColumn(state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp)) {
        itemsIndexed(years, key = { _, y -> y }) { _, year ->
            Column(Modifier.padding(top = 12.dp, bottom = 12.dp)) {
                Text(year.toString(), style = TkType.title.copy(fontSize = 30.sp), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).semantics { heading() })
                (1..12).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m ->
                            val month = YearMonth.of(year, m)
                            MiniMonth(
                                month, recorded,
                                selected = YearMonth.from(vm.selectedDate) == month,
                                modifier = Modifier.weight(1f),
                            ) { vm.openMonth(month); haptics.tick() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(month: YearMonth, recorded: Set<LocalDate>, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val lead = (month.atDay(1).dayOfWeek.value - firstDayOfWeek().value + 7) % 7
    val count = recorded.count { YearMonth.from(it) == month }
    val description = stringResource(R.string.cd_month, month.monthValue) + if (count > 0) ", $count" else ""
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF4A5C6B).copy(alpha = 0.8f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description; this.selected = selected }
            .padding(6.dp),
    ) {
        Text(month.monthValue.toString(), style = TkType.label.copy(color = Tk.Primary, fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(4.dp))
        val cells = lead + month.lengthOfMonth()
        repeat((cells + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val day = week * 7 + col - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1.1f), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val has = month.atDay(day) in recorded
                            Text(
                                day.toString(),
                                fontSize = 7.sp,
                                lineHeight = 8.sp,
                                color = if (has) Tk.Primary else Tk.Muted,
                                fontWeight = if (has) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

// endregion
