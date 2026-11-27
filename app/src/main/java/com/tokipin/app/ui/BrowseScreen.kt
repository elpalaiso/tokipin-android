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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.semantics.clearAndSetSemantics
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
private fun DayOfWeek.short(): String = getDisplayName(DateTextStyle.NARROW, AppLocale.format)

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
                // Deletes every fragment of this day at once; TalkBack hears the date with it, so it is
                // never confused with deleting one fragment in the editor.
                val deleteText = pluralStringResource(R.plurals.delete_day, count, count)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(Tk.DangerSoft)
                        .clickable(role = Role.Button) { vm.deleteDay(date); dayMenuFor = null }
                        .semantics(mergeDescendants = true) { contentDescription = DateWords.spoken(date) + ", " + deleteText }
                        .testTag("deleteDay")
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TkIcon(R.drawable.ic_trash, tint = Tk.DangerText, size = 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(deleteText, style = TkType.body.copy(color = Tk.DangerText))
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

private fun Level.viewName(): Int = when (this) {
    Level.DAILY -> R.string.view_daily
    Level.RECORDS -> R.string.view_records
    Level.MONTH -> R.string.view_month
    Level.YEAR -> R.string.view_year
}

@Composable
private fun BrowseTopBar(vm: AppViewModel, onDayMenu: () -> Unit) {
    val haptics = LocalHaptics.current
    var menuOpen by remember { mutableStateOf(false) }
    // v0.3: one date on the screen's centre line. Day: the selected date's year.month; 기록한 날:
    // the committed month's year (it changes only when a month settles); month calendar: the year
    // in view; year calendar: none (each block carries its own year).
    val title: String? = when (vm.level) {
        Level.DAILY -> com.tokipin.app.rules.DateHeaders.day(vm.selectedDate)
        Level.RECORDS -> com.tokipin.app.rules.DateHeaders.recordedDays(vm.recordedMonth)
        Level.MONTH -> com.tokipin.app.rules.DateHeaders.month(vm.visibleMonth)
        Level.YEAR -> com.tokipin.app.rules.DateHeaders.year(vm.visibleMonth.year)
    }
    val spoken = when (vm.level) {
        Level.DAILY -> DateWords.yearMonth(YearMonth.from(vm.selectedDate))
        Level.RECORDS -> DateWords.year(vm.recordedMonth.year)
        Level.MONTH -> DateWords.year(vm.visibleMonth.year)
        Level.YEAR -> ""
    }
    val upName = vm.upTarget?.let { stringResource(it.viewName()) }
    CenteredTitleBar(
        modifier = Modifier.testTag("topBar"),
        leading = { compact ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (upName != null) {
                    RoundIconButton(
                        R.drawable.ic_back, stringResource(R.string.cd_back_to, upName),
                        { haptics.commitIf(vm.goUp(), NavEvent.LEVEL_COMMIT) },
                        glass = false, modifier = Modifier.testTag("backButton"),
                    )
                }
                if (vm.openedFromSearch && vm.level == Level.DAILY) {
                    if (compact) RoundIconButton(R.drawable.ic_search, stringResource(R.string.search_results), { vm.goUp() }, glass = false, modifier = Modifier.testTag("searchReturn"))
                    else GlassPill(stringResource(R.string.search_results), onClick = { vm.goUp() }, leading = R.drawable.ic_search, modifier = Modifier.testTag("searchReturn"))
                }
            }
        },
        title = {
            if (title != null) {
                Text(
                    title, style = TkType.title, maxLines = 1,
                    modifier = Modifier.testTag("headerTitle").semantics { heading(); contentDescription = spoken },
                )
            }
        },
        trailing = { Row(verticalAlignment = Alignment.CenterVertically) {
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
    } },
    )
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
        items.forEach { entry ->
            DailyItem(entry = entry, highlighted = vm.highlightId == entry.id)
        }
    }
}

@Composable
private fun WeekRow(start: LocalDate, date: LocalDate, onSelect: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        (0L until 7L).map { start.plusDays(it) }.forEach { day ->
            val active = day == date
            val label = DateWords.spoken(day)
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
private fun DailyItem(entry: Entry, highlighted: Boolean) {
    // Figma 5-2105: every fragment has the same weight, the first one included: one row each, an
    // icon only for place and time, a divider under each.
    val text = entry.shown()
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.testTag("dayItem_${entry.id}")) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(if (highlighted) Modifier.clip(shape).background(Tk.SheetRow.copy(alpha = 0.7f)).border(1.dp, Tk.Focus.copy(alpha = 0.7f), shape) else Modifier)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.kind == EntryKind.PLACE || entry.kind == EntryKind.TIME) {
                TkIcon(entry.kind.icon(), tint = Tk.Secondary, size = 16.dp)
                Spacer(Modifier.width(12.dp))
            }
            Text(text, style = TkType.title, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = Tk.Divider, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// endregion

// region Recorded days (bento)

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
    val emptyText = stringResource(R.string.month_empty_cd, DateWords.month(vm.recordedMonth))
    LaunchedEffect(vm.recordedMonth) {
        // Only the settled month is announced; months passed while dragging or cancelled are not.
        val empty = vm.journal.datesIn(vm.recordedMonth).isEmpty()
        if (empty) view.announceForAccessibility(emptyText)
    }
    // v0.3: pages are the visible-month list (months with records + this month), one slot per swipe.
    val list = remember(vm.journal, vm.today) { vm.visibleRecordMonths }
    fun slot(m: YearMonth, d: Int) = list.getOrNull(list.indexOf(m) + d)
    Column(Modifier.fillMaxSize()) {
        MonthRail(vm, list, dragFraction)
        SwipePager(
            page = vm.recordedMonth,
            neighbor = { m, d -> slot(m, d) ?: m },
            onCommit = { dir -> haptics.report(*vm.recorded { step(dir) }.toTypedArray()) },
            reduceMotion = reduceMotion,
            onProgress = { dragFraction = it },
            canMove = { m, d -> slot(m, d) != null },
            modifier = Modifier.fillMaxSize().testTag("recordsBody"),
        ) { month -> MonthPage(vm, month, onLongPress) }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MonthRail(vm: AppViewModel, list: List<YearMonth>, dragFraction: Float) {
    val haptics = LocalHaptics.current
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    // v0.3: the rail lists only the visible months; the padding past either end is empty space
    // (no focus, tap or tick). Small years over the numbers are keyed to the committed month.
    val count = list.size
    fun indexOf(m: YearMonth) = list.indexOf(m).coerceAtLeast(0)
    fun monthAt(i: Int): YearMonth = list[i.coerceIn(0, count - 1)]
    val hints = remember(list, vm.recordedMonth) { com.tokipin.app.rules.RecordedMonths.listHints(list, vm.recordedMonth) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(66.dp).testTag("monthRail")) {
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
        LaunchedEffect(vm.recordedMonth, dragFraction, list) {
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
                val name = DateWords.yearMonth(month)
                Box(
                    Modifier
                        .width(slot)
                        .fillMaxSize()
                        .testTag("rail_$month")
                        .clickable(role = Role.Tab) { haptics.report(*vm.recorded { tap(month) }.toTypedArray()) }
                        // One TalkBack stop per slot: the full year and month and whether it is selected;
                        // the small year is part of it, never its own element.
                        .semantics(mergeDescendants = true) { contentDescription = name; selected = current },
                ) {
                    // Fixed bands: the hint line on top, the month number and capsule below. A hint
                    // appearing never moves the number's baseline or the capsule.
                    Box(Modifier.fillMaxWidth().height(RAIL_HINT_BAND).align(Alignment.TopCenter), contentAlignment = Alignment.BottomCenter) {
                        androidx.compose.animation.Crossfade(
                            targetState = hints.getOrNull(i),
                            animationSpec = tween(if (reduceMotion) UxSpec.REDUCED_MOTION_FADE_MS else UxSpec.COMMIT_MS),
                            label = "railHint",
                        ) { hint ->
                            if (hint != null) Text(
                                hint, maxLines = 1,
                                style = TkType.small.copy(fontSize = 10.sp, color = Tk.Muted),
                                modifier = Modifier.testTag("railHint_$month").clearAndSetSemantics { },
                            )
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().padding(top = RAIL_HINT_BAND).fillMaxSize().testTag("railNumber_$month"),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (current) Box(Modifier.size(40.dp).clip(CircleShape).background(Tk.Focus.copy(alpha = 0.18f)).testTag("railCapsule"))
                        Text(
                            month.monthValue.toString(),
                            style = TkType.title.copy(
                                fontSize = if (current) 20.sp else 16.sp,
                                color = if (current) Tk.Primary else Tk.Muted,
                            ),
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
            }
        }
    }
}

private val RAIL_HINT_BAND = 14.dp

@Composable
private fun MonthPage(vm: AppViewModel, month: YearMonth, onLongPress: (LocalDate) -> Unit) {
    val haptics = LocalHaptics.current
    val dates = vm.journal.datesIn(month)
    if (dates.isEmpty()) {
        // Empty month: the same rabbit card inside this month's page, so it slides with the page
        // and comes back with it on cancel. No words on screen; TalkBack hears it once on settling.
        EmptyStateArea(stringResource(R.string.month_empty_cd, DateWords.month(month)), tag = "monthEmpty")
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Figma 5-2183: 14 at each edge of a 390 body (the 12–18 range), so 362 for the rows.
        val edge = BENTO_EDGE
        val rowWidth = maxWidth - edge * 2
        val measure = rememberBentoMeasure()
        val time = LocalTimeDisplay.current
        val journal = vm.journal
        val rows = remember(dates, journal, rowWidth, measure, time) {
            val laid = dates.map { measure.card(it, journal.itemsOn(it), time, rowWidth.value) }
            val geometry = com.tokipin.app.rules.Bento.rows(laid.map { it.size }, rowWidth.value)
            geometry.map { r -> BentoRowUi(r.cards.map { dates[it] }, r.cards.map { laid[it] }, r) }
        }
        val state = rememberLazyListState()
        LaunchedEffect(vm.focusRequest, month) {
            if (month != vm.recordedMonth) return@LaunchedEffect
            val restore = vm.recordedRestore
            val entry = vm.entry
            if (entry is AppViewModel.ListEntry.Restore && restore != null && restore.month == month) {
                // Back from a day: same top card and offset; if that card is gone, the nearest one.
                val idx = rows.indexOfFirst { restore.topCard in it.dates }.takeIf { it >= 0 }
                    ?: rows.indexOfFirst { r -> r.dates.any { restore.topCard == null || it <= restore.topCard } }.coerceAtLeast(0)
                if (rows.isNotEmpty()) state.scrollToItem(idx, if (restore.topCard in rows[idx].dates) restore.offset else 0)
            } else {
                val idx = rows.indexOfFirst { vm.selectedDate in it.dates }
                if (idx > 0) state.placeAt(idx, (entry as? AppViewModel.ListEntry.Anchor)?.y) else state.scrollToItem(0)
            }
        }
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(start = edge, end = edge, top = 4.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(com.tokipin.app.rules.Bento.GAP.dp),
            modifier = Modifier.fillMaxSize().testTag("monthPage_$month"),
        ) {
            items(rows, key = { "c${it.dates.first()}" }) { row ->
                val height = row.cards.maxOf { it.height }
                Row(Modifier.fillMaxWidth().height(height.dp), horizontalArrangement = Arrangement.spacedBy(com.tokipin.app.rules.Bento.GAP.dp)) {
                    if (row.geometry.bentoSide == com.tokipin.app.rules.Bento.Side.LEFT) EmptyBento(row.geometry.bento!!)
                    row.dates.forEachIndexed { i, date ->
                        DayCard(
                            date = date,
                            layout = row.cards[i],
                            width = row.geometry.widths[i],
                            selected = date == vm.selectedDate,
                            onClick = {
                                // The list holds only card rows (the month is named by the rail and header).
                                val top = rows.getOrNull(state.firstVisibleItemIndex)?.dates?.first()
                                val offset = if (top != null) state.firstVisibleItemScrollOffset else 0
                                haptics.commitIf(vm.openDayFromRecords(date, top, offset), NavEvent.TAP_DATE)
                            },
                            onLongClick = { onLongPress(date) },
                        )
                    }
                    if (row.geometry.bentoSide == com.tokipin.app.rules.Bento.Side.RIGHT) EmptyBento(row.geometry.bento!!)
                }
            }
        }
    }
}

private val BENTO_EDGE = 14.dp
private val BENTO_SHAPE = RoundedCornerShape(16.dp)

private class BentoRowUi(val dates: List<LocalDate>, val cards: List<BentoCard>, val geometry: com.tokipin.app.rules.Bento.Row)

/** One piece of a card's flowing line: a fragment (icon for place and time) or the " · " between. */
private class BentoPiece(val entry: Entry?, val text: String, val width: Float)

/** A measured card: its lines of pieces, natural size and height, all in dp. */
private class BentoCard(val label: String, val lines: List<List<BentoPiece>>, val size: com.tokipin.app.rules.Bento.Card, val height: Float)

/**
 * Measures cards with the real font and font scale (Figma 5-2183): label 12/16 on top, fragments
 * 15/24 below in entered order, " · " between them (2 + 9 + 4), a 13dp icon and 2dp before place
 * and time text, 13 leading and 18 trailing padding.
 */
private class BentoMeasure(
    private val measurer: androidx.compose.ui.text.TextMeasurer,
    private val density: androidx.compose.ui.unit.Density,
    val fragment: androidx.compose.ui.text.TextStyle,
    val label: androidx.compose.ui.text.TextStyle,
) {
    private fun widthOf(text: String, style: androidx.compose.ui.text.TextStyle): Float =
        with(density) { measurer.measure(text, style, softWrap = false, maxLines = 1).size.width.toDp().value }

    private val lineHeight: Float = with(density) { measurer.measure("가", fragment).size.height.toDp().value }
    private val labelHeight: Float = with(density) { measurer.measure("26 / 목", label).size.height.toDp().value }

    fun card(date: LocalDate, items: List<Entry>, time: TimeDisplay, rowWidth: Float): BentoCard {
        val label = date.cardLabel()
        val pieces = mutableListOf<BentoPiece>()
        items.forEachIndexed { i, e ->
            if (i > 0) pieces += BentoPiece(null, "·", SEP_BEFORE + SEP + SEP_AFTER)
            val icon = if (e.kind == EntryKind.PLACE || e.kind == EntryKind.TIME) ICON + ICON_GAP else 0f
            val text = time.text(e)
            pieces += BentoPiece(e, text, icon + widthOf(text, fragment))
        }
        val maxContent = rowWidth - com.tokipin.app.rules.Bento.PAD_LEADING - com.tokipin.app.rules.Bento.PAD_TRAILING
        // Wrap between fragments; a separator never starts a line.
        val lines = mutableListOf(mutableListOf<BentoPiece>())
        var used = 0f
        var k = 0
        while (k < pieces.size) {
            val p = pieces[k]
            if (p.entry == null) {
                val next = pieces[k + 1]
                if (used + p.width + next.width > maxContent && lines.last().isNotEmpty()) {
                    lines += mutableListOf<BentoPiece>(); used = 0f
                    k++ // drop the separator at the break
                    continue
                }
            }
            lines.last() += p; used += p.width
            k++
        }
        val widest = lines.maxOf { l -> l.sumOf { it.width.toDouble() }.toFloat() }
        val labelWidth = widthOf(label, this.label)
        val natural = com.tokipin.app.rules.Bento.natural(maxOf(widest, labelWidth), rowWidth)
        val height = TOP + labelHeight + LABEL_GAP + lineHeight * lines.size + BOTTOM
        return BentoCard(label, lines, com.tokipin.app.rules.Bento.Card(natural, lines.size > 1), height)
    }

    companion object {
        const val SEP_BEFORE = 2f
        const val SEP = 9f
        const val SEP_AFTER = 4f
        const val ICON = 13f
        const val ICON_GAP = 2f
        const val TOP = 10f
        const val LABEL_GAP = 5f
        const val BOTTOM = 11f
    }
}

@Composable
private fun rememberBentoMeasure(): BentoMeasure {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 256)
    val density = LocalDensity.current
    return remember(measurer, density) {
        BentoMeasure(
            measurer, density,
            fragment = TkType.body.copy(fontSize = 15.sp, lineHeight = 24.sp, color = Tk.Primary),
            label = TkType.small,
        )
    }
}

/** The empty bento: decoration only, never touched, focused or read. */
@Composable
private fun EmptyBento(width: Float) {
    Box(
        Modifier.width(width.dp).fillMaxHeight()
            .clip(BENTO_SHAPE)
            // A soft frosted tile, lighter than the cards and without an outline (Figma 5-2183).
            .background(Tk.Primary.copy(alpha = 0.11f))
            .clearAndSetSemantics { }
            .testTag("emptyBento"),
    )
}

@Composable
private fun DayCard(
    date: LocalDate,
    layout: BentoCard,
    width: Float,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val time = LocalTimeDisplay.current
    val measure = rememberBentoMeasure()
    // Extra width beyond the natural one goes to the leading side; the content keeps its 18 trailing.
    val extra = (width - layout.size.natural).coerceAtLeast(0f)
    Column(
        Modifier
            .width(width.dp)
            .fillMaxHeight()
            .pinchTarget(date)
            .testTag("card_$date")
            .clip(BENTO_SHAPE)
            .background(Tk.Card)
            .border(if (selected) 1.dp else 0.8.dp, if (selected) Tk.Secondary.copy(alpha = 0.7f) else Tk.CardBorder, BENTO_SHAPE)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) {
                contentDescription = DateWords.spoken(date) + ", " + layout.lines.flatten().mapNotNull { it.entry }.joinToString(", ") { time.text(it) }
            }
            .padding(
                start = (com.tokipin.app.rules.Bento.PAD_LEADING + extra).dp,
                end = com.tokipin.app.rules.Bento.PAD_TRAILING.dp,
                top = BentoMeasure.TOP.dp,
            ),
    ) {
        Text(layout.label, style = measure.label, maxLines = 1)
        Spacer(Modifier.height(BentoMeasure.LABEL_GAP.dp))
        layout.lines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                line.forEach { piece ->
                    val e = piece.entry
                    if (e == null) {
                        Spacer(Modifier.width(BentoMeasure.SEP_BEFORE.dp))
                        Text("·", style = measure.fragment.copy(color = Tk.Secondary), textAlign = TextAlign.Center, modifier = Modifier.width(BentoMeasure.SEP.dp))
                        Spacer(Modifier.width(BentoMeasure.SEP_AFTER.dp))
                    } else {
                        if (e.kind == EntryKind.PLACE || e.kind == EntryKind.TIME) {
                            TkIcon(e.kind.icon(), size = BentoMeasure.ICON.dp, tint = Tk.Secondary)
                            Spacer(Modifier.width(BentoMeasure.ICON_GAP.dp))
                        }
                        Text(piece.text, style = measure.fragment, maxLines = 1, softWrap = false)
                    }
                }
            }
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
        LazyColumn(state = state, modifier = Modifier.testTag("calendarList"), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 110.dp)) {
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
                        description = pluralStringResource(R.plurals.cd_gap_months, r.months, DateWords.yearMonth(r.from), DateWords.yearMonth(r.to), r.months),
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
    val monthDescription = DateWords.yearMonth(month)
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
                                .semantics { contentDescription = monthDescription; heading() },
                        )
                    }
                }
            }
        }
        val shape = RoundedCornerShape(20.dp)
        val today = com.tokipin.app.rules.AppClock.today()
        Column(Modifier.padding(top = 6.dp).clip(shape).background(Tk.Surface.copy(alpha = 0.55f)).border(0.8.dp, Tk.CardBorder, shape).padding(4.dp)) {
            repeat(weeks) { week ->
                if (week > 0) HorizontalDivider(color = Tk.Divider, modifier = Modifier.padding(horizontal = 6.dp))
                Row(Modifier.fillMaxWidth().heightIn(min = MONTH_CELL_MIN)) {
                    repeat(7) { col ->
                        val day = week * 7 + col - lead + 1
                        Box(Modifier.weight(1f).heightIn(min = MONTH_CELL_MIN)) {
                            if (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                MonthCell(date, vm.journal.itemsOn(date), date == vm.selectedDate, date == today) { onOpenDay(date) }
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
            // Built only when semantics are read (TalkBack, tests), not for every cell on every scroll.
            .semantics(mergeDescendants = true) {
                contentDescription = DateWords.spoken(date) + if (items.isEmpty()) "" else ", " + items.joinToString(", ") { time.text(it) }
                this.selected = selected
            }
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
    val journal = vm.journal
    val recordedYears = journal.years
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
    val miniText = androidx.compose.ui.text.rememberTextMeasurer(cacheSize = 0).let { m ->
        val d = LocalDensity.current
        remember(m, d) { MiniDayText(m, d) }
    }
    // Three months per row on phones, four when there is room (tablets, foldables, landscape).
    CompositionLocalProvider(LocalMiniDayText provides miniText) {
    BoxWithConstraints {
    val perRow = if (maxWidth >= 560.dp) 4 else 3
    LazyColumn(state = state, modifier = Modifier.testTag("calendarList"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp)) {
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
                    description = if (r.from == r.to) stringResource(R.string.cd_gap_year, DateWords.year(r.from))
                    else pluralStringResource(R.plurals.cd_gap_years, r.to - r.from + 1, DateWords.year(r.from), DateWords.year(r.to), r.to - r.from + 1),
                    expanded = r.expanded,
                    onToggle = { vm.toggleYearGap(r.from) },
                )
                is com.tokipin.app.rules.YearRow.Year -> {
                    val year = r.year
                    // The year title scrolls together with its twelve months; a dot says the year has records.
                    Column(Modifier.padding(top = 12.dp, bottom = 12.dp).testTag("year_$year")) {
                        val hasRecords = year in recordedYears
                        val yearDescription = DateWords.year(year) + if (hasRecords) ", " + stringResource(R.string.cd_has_records) else ""
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
                                        month, journal,
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
private fun MiniMonth(month: YearMonth, journal: com.tokipin.app.data.Journal, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val lead = CalendarRules.firstDayColumn(month, firstDayOfWeek())
    val recorded = journal.dateSet
    val count = journal.datesIn(month).size
    val description = if (!enabled) stringResource(R.string.cd_out_of_range, DateWords.month(month))
    else DateWords.yearMonth(month) +
        (if (count > 0) ", " + pluralStringResource(R.plurals.cd_recorded_days, count, count) else "")
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .then(if (enabled) Modifier.pinchTarget(month) else Modifier)
            .testTag("mini_$month")
            .clip(shape)
            .then(if (selected) Modifier.background(Color(0xFF4A5C6B).copy(alpha = 0.8f)).border(1.dp, Tk.Focus.copy(alpha = 0.55f), shape) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else 0.35f }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description; this.selected = selected
                recordedDays = journal.datesIn(month)
            }
            .padding(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(month.monthValue.toString(), style = TkType.label.copy(color = Tk.Primary, fontWeight = FontWeight.Medium))
            if (count > 0) SummaryDot(Modifier.padding(start = 4.dp).testTag("monthDot_$month"), size = 4.dp)
        }
        Spacer(Modifier.height(4.dp))
        // The days are drawn in one Canvas rather than ~40 small composables each: a year row has
        // twelve of these, and composing ~2,000 nodes per year made the year view stutter on long
        // journals. Same layout as before: 7 columns, cells 0.9 wide per 1 tall, the number (7sp,
        // bold when recorded) above a 2.5dp dot that marks a recorded day.
        val weeks = (lead + month.lengthOfMonth() + 6) / 7
        val measurer = LocalMiniDayText.current
        val primary = Tk.Primary
        val muted = Tk.Muted
        val dot = Tk.Focus
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxWidth().aspectRatio(7 * 0.9f / weeks).testTag("miniDays_$month"),
        ) {
            val cellW = size.width / 7
            val cellH = size.height / weeks
            val dotR = 2.5.dp.toPx() / 2
            for (day in 1..month.lengthOfMonth()) {
                val i = lead + day - 1
                val has = month.atDay(day) in recorded
                val text = measurer.layout(day, has, if (has) primary else muted)
                val blockH = text.size.height + dotR * 2
                val cx = (i % 7) * cellW + cellW / 2
                val top = (i / 7) * cellH + (cellH - blockH) / 2
                drawText(text, topLeft = androidx.compose.ui.geometry.Offset(cx - text.size.width / 2f, top))
                if (has) drawCircle(dot, dotR, androidx.compose.ui.geometry.Offset(cx, top + text.size.height + dotR))
            }
        }
    }
}

/** The recorded days a mini month marks with a dot (its pixels are drawn, so tests read this). */
val RecordedDaysKey = androidx.compose.ui.semantics.SemanticsPropertyKey<List<LocalDate>>("RecordedDays")
private var androidx.compose.ui.semantics.SemanticsPropertyReceiver.recordedDays by RecordedDaysKey

/** Day numbers 1–31 of the mini months, measured once per style and colour. */
private class MiniDayText(private val measurer: androidx.compose.ui.text.TextMeasurer, private val density: androidx.compose.ui.unit.Density) {
    private val cache = HashMap<Long, androidx.compose.ui.text.TextLayoutResult>()
    fun layout(day: Int, bold: Boolean, color: Color): androidx.compose.ui.text.TextLayoutResult =
        cache.getOrPut((color.value.toLong() shl 8) xor (day.toLong() shl 1) xor (if (bold) 1L else 0L)) {
            measurer.measure(
                day.toString(),
                androidx.compose.ui.text.TextStyle(
                    color = color, fontSize = 7.sp, lineHeight = 8.sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                ),
                density = density,
            )
        }
}

private val LocalMiniDayText = androidx.compose.runtime.staticCompositionLocalOf<MiniDayText> { error("no MiniDayText") }

// endregion
