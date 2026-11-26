package com.tokipin.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.tappableElement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokipin.app.AppLocale
import com.tokipin.app.R
import com.tokipin.app.data.Entry
import com.tokipin.app.data.EntryKind
import com.tokipin.app.rules.CalendarRules
import com.tokipin.app.rules.Level
import com.tokipin.app.rules.NavEvent
import com.tokipin.app.rules.UxSpec
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.TemporalAdjusters

/** Week start follows the device region (UX v0.1 §4), weekday names the app language. */
private fun firstDayOfWeek(): DayOfWeek = AppLocale.firstDayOfWeek
private fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))
private fun weekdays(): List<DayOfWeek> = (0L until 7L).map { firstDayOfWeek().plus(it) }
private fun DayOfWeek.short(): String = getDisplayName(DateTextStyle.NARROW, AppLocale.current)

/** Month cells keep a 96dp minimum (same as iOS 96pt) and grow with larger text. */
private val MONTH_CELL_MIN = 96.dp
private val MONTH_TITLE = 38.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(vm: AppViewModel) {
    val haptics = LocalHaptics.current
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    var dayMenuFor by remember { mutableStateOf<LocalDate?>(null) }
    val pinchTargets = remember { PinchTargets() }
    // Live pinch: the content scales around the first finger midpoint; on release the next view
    // eases in from that same point (or settles back on cancel).
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    BackHandler(enabled = vm.level == Level.DAILY || (vm.level != Level.YEAR && !vm.journal.isEmpty)) {
        haptics.commitIf(vm.goUp(), NavEvent.LEVEL_COMMIT)
    }

    val listener = remember(vm) {
        object : PinchListener {
            override fun onStart(origin0: Offset) { origin = origin0 }
            override fun onScale(scale0: Float) {
                scope.launch { scale.snapTo(if (reduceMotion) 1f else scale0.coerceIn(0.5f, 2f)) }
            }
            override fun onCancel() {
                scope.launch { scale.animateTo(1f, tween(if (reduceMotion) 0 else UxSpec.CANCEL_MS)) }
            }
            override fun onEnd(next: Level, target: Any?, origin: Offset) {
                val zoomIn = next.ordinal > vm.level.ordinal
                val changed = vm.pinchTo(next, target, origin.y)
                if (!changed) { onCancel(); return }
                haptics.report(NavEvent.LEVEL_COMMIT)
                scope.launch {
                    if (reduceMotion) {
                        scale.snapTo(1f)
                        alpha.snapTo(0f)
                        alpha.animateTo(1f, tween(UxSpec.REDUCED_MOTION_FADE_MS))
                    } else {
                        // The new level starts slightly smaller (zoom in) or larger (zoom out) around the
                        // same point and settles to 1×, so the pinched date grows or shrinks into place.
                        scale.snapTo(if (zoomIn) 0.82f else 1.2f)
                        alpha.snapTo(0.4f)
                        launch { alpha.animateTo(1f, tween(UxSpec.COMMIT_MS)) }
                        scale.animateTo(1f, tween(UxSpec.COMMIT_MS))
                    }
                }
            }
        }
    }

    TkBackground {
        CompositionLocalProvider(LocalPinchTargets provides pinchTargets) {
            Column(Modifier.fillMaxSize().contentColumn().statusBarsPadding()) {
                BrowseTopBar(vm, onDayMenu = { dayMenuFor = vm.selectedDate })
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { boxSize = it }
                        .testTag("level_${vm.level.name}")
                        // Measure the pinch outside the scaling layer, so the live zoom never feeds back
                        // into the measured finger spread.
                        .pinchLevels(
                            level = { vm.level },
                            targets = pinchTargets,
                            preferred = { vm.selectedDate },
                            listener = listener,
                        )
                        .graphicsLayer {
                            scaleX = scale.value; scaleY = scale.value
                            this.alpha = alpha.value
                            transformOrigin = if (boxSize.width > 0) TransformOrigin(
                                (origin.x / boxSize.width).coerceIn(0f, 1f),
                                (origin.y / boxSize.height).coerceIn(0f, 1f),
                            ) else TransformOrigin.Center
                        },
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
        }
        if (vm.journal.isEmpty && (vm.level == Level.RECORDS || vm.level == Level.YEAR)) PeekRabbit()
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
        // The top bar names the year in view; scrolling never changes the selected day.
        // 기록한 날 shows the committed month's year (it changes only when a month settles);
        // the month calendar shows the year in view.
        Level.RECORDS -> vm.recordedMonth.year.toString()
        Level.MONTH -> vm.visibleMonth.year.toString()
        Level.YEAR -> null
    }
    Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (parent != null && !(vm.journal.isEmpty && vm.level != Level.DAILY && vm.level != Level.MONTH)) {
            val backLabel = stringResource(R.string.cd_back_to, parent)
            Row(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { haptics.commitIf(vm.goUp(), NavEvent.LEVEL_COMMIT) }
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
                Text(com.tokipin.app.rules.AppClock.today().year.toString(), style = TkType.title)
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
                        modifier = Modifier.semantics { selected = current }.testTag("menu_${lvl.name}"),
                        onClick = {
                            menuOpen = false
                            haptics.commitIf(vm.changeLevel(lvl), NavEvent.LEVEL_COMMIT)
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
    val haptics = LocalHaptics.current
    Column(
        Modifier.align(Alignment.BottomCenter).contentColumn().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        vm.undo?.let { UndoBar(it) }
        if (!vm.journal.isEmpty || vm.level == Level.DAILY || vm.level == Level.MONTH) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Today keeps the view and is silent (UX v0.1 §3).
                GlassPill(stringResource(R.string.today), onClick = { vm.goToday(); haptics.report(NavEvent.TODAY) })
                Spacer(Modifier.weight(1f))
                // Daily: "+" opens an empty day; a filled day shows one pencil that opens all of its
                // fragments with the keyboard closed. Other views keep "+".
                val filled = vm.level == Level.DAILY && vm.journal.itemsOn(vm.selectedDate).isNotEmpty()
                if (filled) {
                    RoundIconButton(
                        R.drawable.ic_pencil, stringResource(R.string.cd_edit_day), { vm.openEditor(vm.selectedDate, focus = false) },
                        modifier = Modifier.size(56.dp).testTag("editDay"), background = Tk.SheetRow.copy(alpha = 0.95f),
                    )
                } else {
                    RoundIconButton(
                        R.drawable.ic_plus, stringResource(R.string.cd_new_record), { vm.openEditor() },
                        modifier = Modifier.size(56.dp).testTag("addRecord"), background = Tk.SheetRow.copy(alpha = 0.95f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(vm: AppViewModel) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(stringResource(R.string.home_empty), style = TkType.title)
            GlassPill(stringResource(R.string.home_first_record), onClick = { vm.openEditor(com.tokipin.app.rules.AppClock.today()) }, leading = R.drawable.ic_plus)
        }
    }
}

/**
 * The rabbit peeks in from the screen's bottom-right corner (Figma 01 · 토끼는 모서리에서 빼꼼), on the
 * whole screen even when the content column is narrower (tablets). Same rule as iOS: 52% of the
 * screen width (170–300dp), shifted right by 6% of its size, its bottom on the screen edge. With
 * 3-button navigation it sits above the buttons so they never cover its eyes.
 */
@Composable
private fun BoxScope.PeekRabbit() {
    val density = LocalDensity.current
    val buttons = with(density) { WindowInsets.tappableElement.getBottom(density).toDp() }
    BoxWithConstraints(Modifier.align(Alignment.BottomEnd).fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        val side = (maxWidth * 0.52f).coerceIn(170.dp, 300.dp)
        Image(
            painterResource(R.drawable.ic_peek_rabbit),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xFFA9BC9E)),
            modifier = Modifier.padding(bottom = buttons).size(side).offset(x = side * 0.06f),
        )
    }
}

/** Places list item [index] so its top sits [y] px below the list top (pinch continuity). */
private suspend fun LazyListState.placeAt(index: Int, y: Float?, innerOffset: Float = 0f) {
    scrollToItem(index)
    if (y != null) scrollBy(-(y - innerOffset))
    else if (innerOffset > 0f) scrollBy(innerOffset - 24f)
}

/**
 * Remembers this list's position for its level and, on appearing, either restores it (back from a
 * day) or brings the selected date to the pinch point / default spot.
 */
@Composable
private fun TrackScroll(vm: AppViewModel, level: Level, state: LazyListState, anchor: suspend (Float?) -> Unit) {
    LaunchedEffect(vm.focusRequest) {
        val saved = vm.savedScroll(level)
        val entry = vm.entry
        if (entry is AppViewModel.ListEntry.Restore && saved != null) state.scrollToItem(saved.index, saved.offset)
        else anchor((entry as? AppViewModel.ListEntry.Anchor)?.y)
    }
    DisposableEffect(state) {
        onDispose {
            vm.rememberScroll(level, AppViewModel.ScrollPos(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset))
        }
    }
}

// region Daily

@Composable
private fun DailyView(vm: AppViewModel) {
    val haptics = LocalHaptics.current
    val reduceMotion = LocalReduceMotion.current
    val date = vm.selectedDate
    Column(Modifier.fillMaxSize()) {
        // Week rail: ±1 week, keeping the weekday (UX v0.1 §2).
        SwipePager(
            page = date.weekStart(),
            neighbor = { start, d -> start.plusWeeks(d.toLong()) },
            onCommit = { dir -> if (vm.selectDate(date.plusWeeks(dir.toLong()))) haptics.report(NavEvent.WEEK_SWIPE_COMMIT) },
            reduceMotion = reduceMotion,
            modifier = Modifier.fillMaxWidth().testTag("weekRail"),
        ) { start ->
            WeekRow(start, date) { day -> haptics.commitIf(vm.selectDate(day), NavEvent.TAP_DATE) }
        }
        HorizontalDivider(color = Tk.Divider, modifier = Modifier.padding(horizontal = 20.dp))
        // Day body: ±1 day; the neighbouring days sit beside it and follow the finger 1:1.
        SwipePager(
            page = date,
            neighbor = { d, step -> d.plusDays(step.toLong()) },
            onCommit = { dir -> if (vm.selectDate(date.plusDays(dir.toLong()))) haptics.report(NavEvent.DAY_SWIPE_COMMIT) },
            reduceMotion = reduceMotion,
            modifier = Modifier.fillMaxSize().testTag("dayBody"),
        ) { shown -> DayBody(vm, shown) }
    }
}

@Composable
private fun DayBody(vm: AppViewModel, shown: LocalDate) {
    val items = vm.journal.itemsOn(shown)
    if (items.isEmpty()) {
        // Empty day: the shared rabbit card, read once by TalkBack; "+" below opens this day.
        EmptyStateArea(stringResource(R.string.day_empty), tag = "dayEmpty_$shown")
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, entry ->
            DailyItem(entry = entry, lead = index == 0, highlighted = vm.highlightId == entry.id)
        }
    }
}

@Composable
private fun WeekRow(start: LocalDate, date: LocalDate, onSelect: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        (0L until 7L).map { start.plusDays(it) }.forEach { day ->
            val active = day == date
            val label = day.fullLabel()
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
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

/** Reading only: editing goes through the pencil at the bottom (no per-fragment ⋯ any more). */
@Composable
private fun DailyItem(entry: Entry, lead: Boolean, highlighted: Boolean) {
    val text = entry.shown()
    val shape = RoundedCornerShape(if (lead) 22.dp else 10.dp)
    Box(Modifier.testTag("dayItem_${entry.id}")) {
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
                .padding(start = if (lead) 22.dp else 4.dp, end = 16.dp),
            verticalAlignment = if (lead) Alignment.Top else Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).padding(top = if (lead) 32.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!lead && (entry.kind == EntryKind.PLACE || entry.kind == EntryKind.TIME)) {
                    TkIcon(entry.kind.icon(), tint = Tk.Secondary, size = 16.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(text, style = if (lead) TkType.word else TkType.title)
            }
        }
        if (!lead) HorizontalDivider(color = Tk.Divider, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// endregion

// region Recorded days (bento)

private sealed interface RecordRow {
    data class Header(val month: YearMonth) : RecordRow
    data class Cards(val dates: List<LocalDate>) : RecordRow
}

/** One month's cards, newest first, alternating one wide card and two side by side. */
private fun monthRows(dates: List<LocalDate>): List<RecordRow.Cards> {
    val rows = mutableListOf<RecordRow.Cards>()
    var i = 0
    var wide = true
    while (i < dates.size) {
        val take = if (wide) 1 else 2
        rows += RecordRow.Cards(dates.subList(i, minOf(i + take, dates.size)))
        i += take
        wide = !wide
    }
    return rows
}

/**
 * 기록한 날 v0.2: a fixed year header and month rail above one page per month. The body pages
 * ±1 month with the day view's swipe rules; the rail follows the body's drag progress, can be
 * tapped or flung across months and years, and snaps to the nearest month. Every month commit goes
 * through vm.recorded { … }, the single place that changes the month and decides the one tick.
 */
@Composable
private fun RecordsView(vm: AppViewModel, onLongPress: (LocalDate) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    val haptics = LocalHaptics.current
    var dragFraction by remember { mutableStateOf(0f) }
    val view = androidx.compose.ui.platform.LocalView.current
    val emptyText = stringResource(R.string.month_empty_cd, vm.recordedMonth.monthValue)
    LaunchedEffect(vm.recordedMonth) {
        // Only the settled month is announced; months passed while dragging or cancelled are not.
        val empty = vm.journal.recordedDates().none { YearMonth.from(it) == vm.recordedMonth }
        if (empty) view.announceForAccessibility(emptyText)
    }
    Column(Modifier.fillMaxSize()) {
        MonthRail(vm, dragFraction)
        SwipePager(
            page = vm.recordedMonth,
            neighbor = { m, d -> m.plusMonths(d.toLong()) },
            onCommit = { dir -> haptics.report(*vm.recorded { tap(committed.plusMonths(dir.toLong())) }.toTypedArray()) },
            reduceMotion = reduceMotion,
            onProgress = { dragFraction = it },
            canMove = { m, d -> m.plusMonths(d.toLong()) in vm.range },
            modifier = Modifier.fillMaxSize().testTag("recordsBody"),
        ) { month -> MonthPage(vm, month, onLongPress) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MonthRail(vm: AppViewModel, dragFraction: Float) {
    val haptics = LocalHaptics.current
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    // The rail runs over the calendar range only: from its first month to the current month.
    val range = vm.range
    val count = (range.last.year - range.first.year) * 12 + range.last.monthValue - range.first.monthValue + 1
    fun indexOf(m: YearMonth) = ((m.year - range.first.year) * 12 + m.monthValue - range.first.monthValue).coerceIn(0, count - 1)
    fun monthAt(i: Int): YearMonth = range.first.plusMonths(i.coerceIn(0, count - 1).toLong())
    BoxWithConstraints(Modifier.fillMaxWidth().height(52.dp).testTag("monthRail")) {
        // Neighbours on each side adapt to the width and the text size (2 on phones by default).
        val side = com.tokipin.app.rules.RecordedMonths.sideSlots(maxWidth.value, density.fontScale)
        val slot = maxWidth / (2 * side + 1)
        val state = rememberLazyListState(initialFirstVisibleItemIndex = indexOf(vm.recordedMonth))
        val snap = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(
            state, androidx.compose.foundation.gestures.snapping.SnapPosition.Center,
        )
        val dragged by state.interactionSource.collectIsDraggedAsState()
        var touched by remember { mutableStateOf(false) }
        LaunchedEffect(dragged) { if (dragged) touched = true }

        fun centeredIndex(): Int {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            return info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }?.index
                ?: state.firstVisibleItemIndex
        }

        // The person flung or dragged the rail: preview while coasting, commit once at rest.
        LaunchedEffect(state) {
            snapshotFlow { state.isScrollInProgress }.collect { moving ->
                if (!moving && touched) {
                    touched = false
                    val month = monthAt(centeredIndex())
                    haptics.report(*vm.recorded { tap(month) }.toTypedArray())
                }
            }
        }
        LaunchedEffect(state) {
            snapshotFlow { if (touched) centeredIndex() else -1 }.distinctUntilChanged().collect { i ->
                if (i >= 0) vm.recorded { railCoast(monthAt(i)) }
            }
        }
        // Follow the committed month and the body's drag progress (same fraction, same direction).
        LaunchedEffect(vm.recordedMonth, dragFraction) {
            if (touched) return@LaunchedEffect
            val target = indexOf(vm.recordedMonth)
            val slotPx = with(density) { slot.toPx() }
            if (dragFraction != 0f) {
                val pos = (target - dragFraction).coerceIn(0f, (count - 1).toFloat())
                val whole = kotlin.math.floor(pos).toInt()
                state.scrollToItem(whole, ((pos - whole) * slotPx).toInt())
            } else if (state.firstVisibleItemIndex != target || state.firstVisibleItemScrollOffset != 0) {
                if (reduceMotion || kotlin.math.abs(state.firstVisibleItemIndex - target) > side * 3) state.scrollToItem(target)
                else state.animateScrollToItem(target)
            }
        }

        LazyRow(
            state = state,
            flingBehavior = snap,
            contentPadding = PaddingValues(horizontal = slot * side),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(count) { i ->
                val month = monthAt(i)
                val current = month == vm.recordedMonth
                val name = stringResource(R.string.cd_year_month, month.year, month.monthValue)
                Box(
                    Modifier
                        .width(slot)
                        .fillMaxSize()
                        .testTag("rail_$month")
                        .clickable(role = Role.Tab) { haptics.report(*vm.recorded { tap(month) }.toTypedArray()) }
                        .semantics { contentDescription = name; selected = current },
                    contentAlignment = Alignment.Center,
                ) {
                    if (current) Box(Modifier.size(40.dp).clip(CircleShape).background(Tk.Focus.copy(alpha = 0.18f)))
                    Text(
                        month.monthValue.toString(),
                        style = TkType.title.copy(
                            fontSize = if (current) 20.sp else 16.sp,
                            color = if (current) Tk.Primary else Tk.Muted,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthPage(vm: AppViewModel, month: YearMonth, onLongPress: (LocalDate) -> Unit) {
    val haptics = LocalHaptics.current
    val dates = remember(vm.journal, month) { vm.journal.recordedDates().filter { YearMonth.from(it) == month } }
    val rows = remember(dates) { monthRows(dates) }
    val state = rememberLazyListState()
    LaunchedEffect(vm.focusRequest, month) {
        if (month != vm.recordedMonth) return@LaunchedEffect
        val restore = vm.recordedRestore
        val entry = vm.entry
        if (entry is AppViewModel.ListEntry.Restore && restore != null && restore.month == month) {
            // Back from a day: same top card and offset; if that card is gone, the nearest one.
            val idx = rows.indexOfFirst { restore.topCard in it.dates }.takeIf { it >= 0 }
                ?: rows.indexOfFirst { r -> r.dates.any { restore.topCard == null || it <= restore.topCard } }.coerceAtLeast(0)
            if (rows.isNotEmpty()) state.scrollToItem(idx + 1, if (restore.topCard in rows[idx].dates) restore.offset else 0) // +1: title
        } else {
            val idx = rows.indexOfFirst { vm.selectedDate in it.dates }
            if (idx > 0) state.placeAt(idx + 1, (entry as? AppViewModel.ListEntry.Anchor)?.y) else state.scrollToItem(0)
        }
    }
    if (rows.isEmpty()) {
        // Empty month: the same rabbit card inside this month's page, so it slides with the page
        // and comes back with it on cancel. No words on screen; TalkBack hears it once on settling.
        EmptyStateArea(stringResource(R.string.month_empty_cd, month.monthValue), tag = "monthEmpty")
        return
    }
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().testTag("monthPage_$month"),
    ) {
        item(key = "title") {
            Text(month.label(), style = TkType.body.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium), modifier = Modifier.padding(start = 4.dp).semantics { heading() })
        }
        itemsIndexed(rows, key = { _, row -> "c${row.dates.first()}" }) { _, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.dates.forEach { date ->
                    DayCard(
                        date = date,
                        items = vm.journal.itemsOn(date),
                        wide = row.dates.size == 1,
                        selected = date == vm.selectedDate,
                        onClick = {
                            val top = rows.getOrNull(state.firstVisibleItemIndex - 1)?.dates?.first()
                            val offset = if (top != null) state.firstVisibleItemScrollOffset else 0
                            haptics.commitIf(vm.openDayFromRecords(date, top, offset), NavEvent.TAP_DATE)
                        },
                        onLongClick = { onLongPress(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
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
    val time = LocalTimeDisplay.current
    val title = items.firstOrNull()
    val rest = items.drop(1)
    val description = date.fullLabel() + ", " + items.joinToString(", ") { time.text(it) }
    Column(
        modifier
            .pinchTarget(date)
            .testTag("card_$date")
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
                time.text(title), style = if (wide) TkType.title.copy(fontSize = 26.sp) else TkType.title,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (rest.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                rest.joinToString("  ·  ") { time.text(it) }, style = TkType.label,
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
    val density = LocalDensity.current
    val range = vm.range
    val recorded = vm.recordedMonths
    // From the first month of the range (it stops there) to the current month; long empty
    // stretches before the recent 12 months fold into one row.
    val rows = remember(range, recorded, vm.expandedMonthGaps) {
        com.tokipin.app.rules.CalendarFolding.months(range, recorded, vm.expandedMonthGaps)
    }
    val state = rememberLazyListState()
    TrackScroll(vm, Level.MONTH, state) { y ->
        val month = YearMonth.from(vm.selectedDate)
        val idx = rows.indexOfFirst { it is com.tokipin.app.rules.MonthRow.Month && it.month == month }.takeIf { it >= 0 } ?: return@TrackScroll
        // Keep the selected date's row where the fingers were.
        val row = (CalendarRules.firstDayColumn(month, firstDayOfWeek()) + vm.selectedDate.dayOfMonth - 1) / 7
        val inner = with(density) { (MONTH_TITLE + MONTH_CELL_MIN * row).toPx() }
        state.placeAt(idx, y, inner)
    }
    LaunchedEffect(state, rows) {
        snapshotFlow {
            when (val r = rows.getOrNull(state.firstVisibleItemIndex)) {
                is com.tokipin.app.rules.MonthRow.Month -> r.month
                is com.tokipin.app.rules.MonthRow.Gap -> r.from
                null -> null
            }
        }.distinctUntilChanged().collect { m -> if (m != null && state.isScrollInProgress) vm.scrolledTo(m) }
    }
    Column(Modifier.fillMaxSize()) {
        // Weekday header stays fixed while months scroll underneath (Figma 04 · 월간 해가 바뀌는 경계).
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            weekdays().forEach { Text(it.short(), style = TkType.small, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        LazyColumn(state = state, contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 110.dp)) {
            itemsIndexed(rows, key = { _, r ->
                when (r) {
                    is com.tokipin.app.rules.MonthRow.Month -> r.month.toString()
                    is com.tokipin.app.rules.MonthRow.Gap -> "gap_${r.from}"
                }
            }) { _, r ->
                when (r) {
                    is com.tokipin.app.rules.MonthRow.Month ->
                        MonthBlock(r.month, vm, onOpenDay = { haptics.commitIf(vm.openDay(it), NavEvent.TAP_DATE) })
                    is com.tokipin.app.rules.MonthRow.Gap -> GapRow(
                        tag = "gap_${r.from}",
                        label = "%04d.%02d – %04d.%02d".format(r.from.year, r.from.monthValue, r.to.year, r.to.monthValue),
                        description = stringResource(R.string.cd_gap_months, r.from.year, r.from.monthValue, r.to.year, r.to.monthValue, r.months),
                        expanded = r.expanded,
                        onToggle = { vm.toggleMonthGap(r.from) },
                    )
                }
            }
        }
    }
}

/**
 * One row standing in for a stretch of empty months or years. Tapping opens just that stretch in
 * place (and closes it again); TalkBack reads the period and whether it is folded or open.
 */
@Composable
private fun GapRow(tag: String, label: String, description: String, expanded: Boolean, onToggle: () -> Unit) {
    val state = stringResource(if (expanded) R.string.cd_expanded else R.string.cd_collapsed)
    val action = stringResource(if (expanded) R.string.cd_collapse else R.string.cd_expand)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .heightIn(min = 48.dp)
            .glass(RoundedCornerShape(16.dp))
            .background(Tk.Surface.copy(alpha = 0.45f))
            .clickable(onClickLabel = action, role = Role.Button, onClick = onToggle)
            .testTag(tag)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = state
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = TkType.body, modifier = Modifier.weight(1f))
        TkIcon(R.drawable.ic_chevron_right, size = 18.dp, tint = Tk.Secondary, modifier = Modifier.graphicsLayer { rotationZ = if (expanded) -90f else 90f })
    }
}

@Composable
private fun MonthBlock(month: YearMonth, vm: AppViewModel, onOpenDay: (LocalDate) -> Unit) {
    val lead = CalendarRules.firstDayColumn(month, firstDayOfWeek())
    val cells = lead + month.lengthOfMonth()
    val weeks = (cells + 6) / 7
    val monthDescription = stringResource(R.string.cd_month, month.monthValue)
    Column(Modifier.testTag("month_$month")) {
        // The month number sits outside the frame, above the column of the 1st.
        Row(Modifier.fillMaxWidth().height(MONTH_TITLE).padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
            repeat(7) { col ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (col == lead) {
                        Text(
                            month.monthValue.toString(),
                            style = TkType.title.copy(fontSize = 20.sp),
                            modifier = Modifier
                                .testTag("monthLabel_$month")
                                .semantics { contentDescription = "${month.year} $monthDescription"; heading() },
                        )
                    }
                }
            }
        }
        val shape = RoundedCornerShape(20.dp)
        Column(Modifier.padding(top = 6.dp).clip(shape).background(Tk.Surface.copy(alpha = 0.55f)).border(0.8.dp, Tk.CardBorder, shape).padding(4.dp)) {
            repeat(weeks) { week ->
                if (week > 0) HorizontalDivider(color = Tk.Divider, modifier = Modifier.padding(horizontal = 6.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = MONTH_CELL_MIN)) {
                    repeat(7) { col ->
                        val day = week * 7 + col - lead + 1
                        Box(Modifier.weight(1f).heightIn(min = MONTH_CELL_MIN)) {
                            if (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                MonthCell(date, vm.journal.itemsOn(date), date == vm.selectedDate, date == com.tokipin.app.rules.AppClock.today()) { onOpenDay(date) }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MonthCell(date: LocalDate, items: List<Entry>, selected: Boolean, today: Boolean, onClick: () -> Unit) {
    val time = LocalTimeDisplay.current
    val (shown, more) = CalendarRules.preview(items)
    val label = date.fullLabel() + if (items.isEmpty()) "" else ", " + items.joinToString(", ") { time.text(it) }
    Column(
        Modifier
            .pinchTarget(date)
            .testTag("day_$date")
            .fillMaxWidth()
            .heightIn(min = MONTH_CELL_MIN)
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
        shown.forEach {
            Text(time.text(it), style = TkType.small.copy(fontSize = 10.sp, lineHeight = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (more > 0) Text(stringResource(R.string.more_count, more), style = TkType.small.copy(fontSize = 10.sp))
    }
}

// endregion

// region Year

@Composable
private fun YearView(vm: AppViewModel) {
    val haptics = LocalHaptics.current
    val range = vm.range
    val dates = vm.journal.recordedDates()
    val recorded = remember(vm.journal) { dates.toSet() }
    val recordedYears = remember(vm.journal) { dates.map { it.year }.toSet() }
    val rows = remember(range, recordedYears, vm.expandedYearGaps) {
        com.tokipin.app.rules.CalendarFolding.years(range, recordedYears, vm.expandedYearGaps)
    }
    val state = rememberLazyListState()
    TrackScroll(vm, Level.YEAR, state) { y ->
        val idx = rows.indexOfFirst { it is com.tokipin.app.rules.YearRow.Year && it.year == vm.selectedDate.year }
        if (idx >= 0) state.placeAt(idx, y?.let { it - 200f })
    }
    LaunchedEffect(state, rows) {
        snapshotFlow { (rows.getOrNull(state.firstVisibleItemIndex) as? com.tokipin.app.rules.YearRow.Year)?.year }
            .distinctUntilChanged()
            .collect { yr -> if (yr != null && state.isScrollInProgress) vm.scrolledTo(YearMonth.of(yr, 1)) }
    }
    // Three months per row on phones, four when there is room (tablets, foldables, landscape).
    BoxWithConstraints {
    val perRow = if (maxWidth >= 560.dp) 4 else 3
    LazyColumn(state = state, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp)) {
        itemsIndexed(rows, key = { _, r ->
            when (r) {
                is com.tokipin.app.rules.YearRow.Year -> "y${r.year}"
                is com.tokipin.app.rules.YearRow.Gap -> "gap_${r.from}"
            }
        }) { _, r ->
            when (r) {
                is com.tokipin.app.rules.YearRow.Gap -> GapRow(
                    tag = "yearGap_${r.from}",
                    label = if (r.from == r.to) "${r.from}" else "${r.from} – ${r.to}",
                    description = if (r.from == r.to) stringResource(R.string.cd_gap_year, r.from) else stringResource(R.string.cd_gap_years, r.from, r.to, r.to - r.from + 1),
                    expanded = r.expanded,
                    onToggle = { vm.toggleYearGap(r.from) },
                )
                is com.tokipin.app.rules.YearRow.Year -> {
                    val year = r.year
                    // The year title scrolls together with its twelve months; a dot says the year has records.
                    Column(Modifier.padding(top = 12.dp, bottom = 12.dp).testTag("year_$year")) {
                        val hasRecords = year in recordedYears
                        val yearDescription = stringResource(R.string.cd_year, year) + if (hasRecords) ", " + stringResource(R.string.cd_has_records) else ""
                        Row(
                            Modifier.padding(start = 4.dp, bottom = 8.dp).semantics(mergeDescendants = true) { contentDescription = yearDescription; heading() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(year.toString(), style = TkType.title.copy(fontSize = 30.sp))
                            if (hasRecords) SummaryDot(Modifier.align(Alignment.Top).padding(start = 4.dp, top = 8.dp).testTag("yearDot_$year"), size = 6.dp)
                        }
                        (1..12).chunked(perRow).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { m ->
                                    val month = YearMonth.of(year, m)
                                    MiniMonth(
                                        month, recorded,
                                        selected = YearMonth.from(vm.selectedDate) == month,
                                        enabled = month in range,
                                        modifier = Modifier.weight(1f),
                                    ) { haptics.commitIf(vm.openMonth(month), NavEvent.LEVEL_COMMIT) }
                                }
                                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/** A small dot next to a year or month title: it has at least one record. */
@Composable
private fun SummaryDot(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 5.dp) {
    Box(modifier.size(size).clip(CircleShape).background(Tk.Focus))
}

/**
 * One month in the year view. Three marks show together and never replace each other: (1) the
 * selected day's month keeps its slate background and border, (2) each recorded day is bold with a
 * small dot under its number, (3) the month title gets a summary dot. Months before the range or
 * after the current month are dimmed and cannot be opened.
 */
@Composable
private fun MiniMonth(month: YearMonth, recorded: Set<LocalDate>, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val lead = CalendarRules.firstDayColumn(month, firstDayOfWeek())
    val count = recorded.count { YearMonth.from(it) == month }
    val description = if (!enabled) stringResource(R.string.cd_out_of_range, month.monthValue)
    else stringResource(R.string.cd_year_month, month.year, month.monthValue) +
        (if (count > 0) ", " + stringResource(R.string.cd_recorded_days, count) else "")
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .then(if (enabled) Modifier.pinchTarget(month) else Modifier)
            .testTag("mini_$month")
            .clip(shape)
            .then(if (selected) Modifier.background(Color(0xFF4A5C6B).copy(alpha = 0.8f)).border(1.dp, Tk.Focus.copy(alpha = 0.55f), shape) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description; this.selected = selected }
            .padding(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.monthValue.toString(), style = TkType.label.copy(color = Tk.Primary, fontWeight = FontWeight.Medium))
            if (count > 0) SummaryDot(Modifier.padding(start = 4.dp).testTag("monthDot_$month"), size = 4.dp)
        }
        Spacer(Modifier.height(4.dp))
        val cells = lead + month.lengthOfMonth()
        repeat((cells + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val day = week * 7 + col - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(0.9f), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            val date = month.atDay(day)
                            val has = date in recorded
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    day.toString(),
                                    fontSize = 7.sp,
                                    lineHeight = 8.sp,
                                    color = if (has) Tk.Primary else Tk.Muted,
                                    fontWeight = if (has) FontWeight.Bold else FontWeight.Normal,
                                )
                                // The record-day dot: shape, not only colour, marks a recorded day.
                                Box(
                                    Modifier.size(2.5.dp).clip(CircleShape)
                                        .background(if (has) Tk.Focus else Color.Transparent)
                                        .then(if (has) Modifier.testTag("dayDot_$date") else Modifier),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// endregion
