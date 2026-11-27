package com.tokipin.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokipin.app.R
import com.tokipin.app.data.Entry
import com.tokipin.app.data.EntryKind
import com.tokipin.app.rules.EmojiRules
import androidx.compose.material3.TimeInput


fun EntryKind.textStyle(): TextStyle = when (this) {
    EntryKind.WORD, EntryKind.EMOJI -> TkType.word
    EntryKind.PLACE, EntryKind.TIME -> TkType.itemSecondary
}

/**
 * Figma 211-3000: saved fragments all read at the same size (like the day view). Only the very
 * first fragment typed on an empty day ([large]) stays large while it is being written.
 */
fun EntryKind.editorStyle(large: Boolean): TextStyle = if (large) textStyle() else TkType.title

@Composable
fun EditorScreen(vm: AppViewModel, showUsageHint: Boolean) {
    val draft = vm.draft ?: return
    val listState = rememberLazyListState()
    val selection = vm.selection
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val haptics = LocalHaptics.current
    val focus = remember { FocusRequester() }
    var revealedId by remember { mutableStateOf<String?>(null) }
    var emojiBlocked by remember { mutableStateOf(false) }
    var field by remember(draft.editId, draft.kind) {
        mutableStateOf(TextFieldValue(draft.raw, TextRange(draft.raw.length)))
    }
    if (field.text != draft.raw && field.composition == null) field = TextFieldValue(draft.raw, TextRange(draft.raw.length))

    fun focusField() {
        if (draft.kind == EntryKind.TIME) return
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }

    fun onFieldChange(value: TextFieldValue) {
        // Emoji tool (UX v0.1 §4): an IME composition (e.g. Hangul) is left alone; once committed,
        // typed or pasted text keeps only its emoji, and a change that adds nothing valid keeps the
        // previous text and selection as they were.
        val next = if (draft.kind == EntryKind.EMOJI) {
            val before = EmojiRules.Edit(field.text, field.selection.start, field.selection.end)
            val after = EmojiRules.Edit(value.text, value.selection.start, value.selection.end)
            val result = EmojiRules.fieldChange(before, after, composing = value.composition != null)
            emojiBlocked = result != after
            when (result) {
                after -> value
                before -> field
                else -> TextFieldValue(result.text, TextRange(result.selectionStart, result.selectionEnd))
            }
        } else value
        vm.tour?.composing = next.composition != null
        field = next
        if (next.text != draft.raw) vm.updateRaw(next.text)
    }

    BackHandler {
        when {
            selection != null -> vm.endSelection()
            revealedId != null -> revealedId = null
            else -> vm.leaveEditor()
        }
    }

    // "+" opens with the keyboard; the pencil opens with it closed until a fragment or tool is tapped.
    var allowFocus by remember { mutableStateOf(vm.editorAutoFocus) }
    val tour = vm.tour
    val cue = tour?.takeIf { it.guided && selection == null }?.editorCue(draft)
    val imeHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    val heightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val compactTour = tour != null && selection == null && imeHeight > 0 &&
        heightDp - with(LocalDensity.current) { imeHeight.toDp().value } < 470f
    fun nextGuideAction() {
        if (selection != null) vm.endSelection()
        if (field.composition != null || tour?.composing == true || (draft.kind == EntryKind.EMOJI && EmojiRules.filter(field.text) != field.text)) {
            // Explicitly finish the IME composition before using the committed value as a guide action.
            val committed = if (draft.kind == EntryKind.EMOJI) EmojiRules.filter(field.text) else field.text
            allowFocus = false
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            field = TextFieldValue(committed, TextRange(committed.length))
            tour?.composing = false
            vm.updateRaw(committed)
            if (committed.isNotBlank()) return
        }
        if (cue == R.string.onboarding_emoji_tool) allowFocus = true
        tour?.next(vm)
    }
    val editingIndex = draft.items.indexOfFirst { it.id == draft.editId }.takeIf { it >= 0 } ?: draft.items.size
    LaunchedEffect(tour, imeHeight > 0, draft.kind) {
        if (tour != null && imeHeight > 0 && selection == null && draft.kind != EntryKind.TIME) listState.scrollToItem(editingIndex)
    }
    LaunchedEffect(allowFocus, draft.kind, draft.editId, selection == null, vm.usagePromptOpen) {
        if (vm.usagePromptOpen) return@LaunchedEffect
        if (draft.editId != null) allowFocus = true
        if (allowFocus && selection == null && draft.kind != EntryKind.TIME) {
            if (tour != null) listState.scrollToItem(editingIndex)
            // The tool's own field is laid out on the next frame; focus it then.
            androidx.compose.runtime.withFrameNanos { }
            focusField()
        }
    }

    TkBackground {
        Column(Modifier.fillMaxSize().contentColumn().statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (!compactTour) SampleControls(vm, onNext = ::nextGuideAction)
            if (selection != null) SelectionHeader(vm, selection) else EditorHeader(vm, draft.items.isNotEmpty(), compactTour, ::nextGuideAction)
            if (showUsageHint) UsageLaterHint()
            if (cue == R.string.onboarding_save_guide) SampleHint(vm, cue, Modifier.align(Alignment.End))

            val edge = with(LocalDensity.current) { 64.dp.toPx() }
            val listBounds = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
            fun edgeScroll(windowY: Float): Float {
                val b = listBounds[0] ?: return 0f
                val delta = when {
                    windowY < b.top + edge -> -(b.top + edge - windowY) * 0.3f
                    windowY > b.bottom - edge -> (windowY - (b.bottom - edge)) * 0.3f
                    else -> 0f
                }
                return if (delta == 0f) 0f else listState.dispatchRawDelta(delta)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("editorList")
                    .onGloballyPositioned { listBounds[0] = it.boundsInWindow() },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = if (compactTour) 2.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(draft.items, key = { it.id }) { item ->
                    if (item.id == draft.editId && selection == null && draft.kind == EntryKind.TIME) {
                        PendingTime(draft.raw.ifEmpty { item.value }, large = false)
                    } else if (item.id == draft.editId && selection == null) {
                        Composer(draft.kind, field, focus, large = draft.items.isEmpty(), onChange = { onFieldChange(it) }, onDone = {
                            if (vm.commitRaw()) focusField()
                        })
                    } else {
                        EditorRow(
                            item = item,
                            index = draft.items.indexOf(item),
                            count = draft.items.size,
                            selecting = selection != null,
                            checked = selection?.contains(item.id) == true,
                            revealed = revealedId == item.id,
                            onReveal = { revealedId = if (it) item.id else null },
                            onClick = {
                                revealedId = null
                                allowFocus = true
                                when {
                                    selection != null -> vm.toggleSelected(item.id)
                                    else -> vm.startEditing(item)
                                }
                            },
                            onDelete = { revealedId = null; vm.deleteDraftItems(setOf(item.id)) },
                            onMove = { to -> revealedId = null; vm.moveDraftItem(item.id, to) },
                            onPickUp = { haptics.report(com.tokipin.app.rules.NavEvent.FRAGMENT_PICK_UP) },
                            edgeScroll = { y -> edgeScroll(y) },
                            // Heights (not positions) of the rows around the held one: sizes do not
                            // change when rows trade places, so they are never stale after a move.
                            heightAt = { i ->
                                val id = vm.draft?.items?.getOrNull(i)?.id
                                if (id == null) null else listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.size?.toFloat()
                            },
                        )
                    }
                }
                if (draft.editId == null && selection == null && draft.kind != EntryKind.TIME) {
                    item(key = "composer_${draft.kind}") {
                        Composer(draft.kind, field, focus, large = draft.items.isEmpty(), onChange = { onFieldChange(it) }, onDone = {
                            if (vm.commitRaw()) focusField()
                        })
                    }
                }
                if (draft.editId == null && selection == null && draft.kind == EntryKind.TIME) {
                    // The time tool has no text field (the time is chosen below), so the waiting row shows
                    // the same "시간" placeholder as the other tools, or the time chosen but not yet added.
                    item(key = "composer_TIME") { TimeSlot(draft.raw, large = draft.items.isEmpty()) }
                }
            }
            LaunchedEffect(draft.items.size) {
                if (draft.items.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
            }

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.editorMessage?.let {
                    Text(it, style = TkType.label.copy(color = Tk.DangerText), modifier = Modifier.padding(horizontal = 6.dp))
                }
                if (vm.saveFailed) SaveFailedBanner(onRetry = vm::saveDraft)
                vm.undo?.let { UndoBar(it) }
                if (cue != null && cue != R.string.onboarding_save_guide) SampleHint(vm, cue)
                if (tour == null && draft.kind == EntryKind.EMOJI && selection == null && (field.text.isEmpty() || emojiBlocked)) {
                    // Android cannot open an arbitrary keyboard on its emoji panel, so a short hint shows
                    // only while it helps: the field is empty, or something that is not an emoji was typed.
                    EmojiKeyHint()
                }
                if (draft.kind == EntryKind.TIME && selection == null) {
                    TimePanel(
                        key = Triple(draft.editId, draft.items.size, draft.raw.isEmpty()),
                        initial = draft.raw.takeIf { it.isNotEmpty() },
                        onDisplayed = { tour?.displayedTime = it },
                        onChoose = { vm.updateRaw(it) },
                        onAdd = { value -> vm.updateRaw(value); vm.commitRaw() },
                        onCancel = vm::cancelPending,
                    )
                }
                KindToolbar(draft.kind, guideEmoji = cue == R.string.onboarding_emoji_tool) { kind ->
                    revealedId = null
                    allowFocus = true
                    if (vm.setKind(kind)) {
                        when (kind) {
                            EntryKind.TIME -> keyboard?.hide()
                            else -> focusField()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

}

@Composable
private fun EditorHeader(vm: AppViewModel, hasItems: Boolean, compactTour: Boolean, nextAction: () -> Unit) {
    val draft = vm.draft ?: return
    // v0.3: the draft's whole date ("2026.11.25 ⌄") on the screen's centre line, one button with
    // its chevron; choosing a date in the picker moves the draft only, ✓ saves.
    var picking by remember { mutableStateOf(false) }
    val dateLabel = com.tokipin.app.rules.DateHeaders.editor(draft.localDate)
    val spoken = DateWords.spoken(draft.localDate)
    val changeDate = stringResource(R.string.cd_change_date)
    @Composable fun EditorActions() {
        if (hasItems) RoundIconButton(R.drawable.ic_more, stringResource(R.string.cd_select_items), vm::beginSelection)
        RoundIconButton(R.drawable.ic_check, stringResource(R.string.cd_save_record), vm::saveDraft,
            Modifier.tourTarget(vm.tour?.let { it.guided && it.editorCue(draft) == R.string.onboarding_save_guide } == true))
    }
    if (compactTour) {
        // Leave space for the actual composing line above a tall IME on a short screen.
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).testTag("editorTopBar"), verticalAlignment = Alignment.CenterVertically) {
            val closeReplay = LocalCloseDeveloperReplay.current
            if (closeReplay != null) DeveloperReplayClose(closeReplay)
            else RoundIconButton(R.drawable.ic_back, stringResource(R.string.cd_back_keep_draft), vm::leaveEditor)
            RoundIconButton(R.drawable.ic_view_month, "$spoken, $changeDate", { picking = true }, Modifier.testTag("editorDate"))
            Spacer(Modifier.weight(1f))
            EditorActions()
            SampleControls(vm, inline = true, onNext = nextAction)
        }
    } else CenteredTitleBar(
        modifier = Modifier.testTag("editorTopBar"),
        edge = 12.dp,
        leading = { RoundIconButton(R.drawable.ic_back, stringResource(R.string.cd_back_keep_draft), vm::leaveEditor) },
        title = {
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = changeDate, role = Role.Button) { picking = true }
                    .testTag("editorDate")
                    .semantics(mergeDescendants = true) { contentDescription = spoken }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(dateLabel, style = TkType.title, maxLines = 1)
                Spacer(Modifier.width(4.dp))
                TkIcon(R.drawable.ic_chevron_right, size = 12.dp, tint = Tk.Secondary, modifier = Modifier.graphicsLayer { rotationZ = 90f })
            }
        },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                EditorActions()
            }
        },
    )
    if (picking) {
        EntryDatePicker(
            initial = draft.localDate,
            lastMonth = vm.range.last,
            onDismiss = { picking = false },
            onConfirm = { picking = false; vm.changeDraftDate(it) },
        )
    }
}

/**
 * Native (Material) date picker for the entry date. Past years and months can be chosen directly;
 * nothing after the current month can be a new record's date. Confirming only moves the draft;
 * ✓ in the editor saves. Cancel changes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDatePicker(initial: java.time.LocalDate, lastMonth: java.time.YearMonth, onDismiss: () -> Unit, onConfirm: (java.time.LocalDate) -> Unit) {
    val utc = java.time.ZoneOffset.UTC
    val lastDay = lastMonth.atEndOfMonth()
    val state = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(utc).toInstant().toEpochMilli(),
        yearRange = 1900..lastMonth.year,
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                !java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(utc).toLocalDate().isAfter(lastDay)
            override fun isSelectableYear(year: Int) = year <= lastMonth.year
        },
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(java.time.Instant.ofEpochMilli(it).atZone(utc).toLocalDate()) } ?: onDismiss()
                },
                modifier = Modifier.testTag("datePickerOk"),
            ) { Text(stringResource(R.string.time_picker_ok), color = Tk.Focus) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.time_picker_cancel), color = Tk.Secondary) } },
        colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = Tk.Sheet),
    ) {
        androidx.compose.material3.DatePicker(
            state = state,
            colors = androidx.compose.material3.DatePickerDefaults.colors(
                containerColor = Tk.Sheet,
                selectedDayContainerColor = Tk.Focus,
                selectedDayContentColor = Tk.OnFocus,
                todayDateBorderColor = Tk.Secondary,
                selectedYearContainerColor = Tk.Focus,
                selectedYearContentColor = Tk.OnFocus,
            ),
        )
    }
}

@Composable
private fun SelectionHeader(vm: AppViewModel, selection: Set<String>) {
    val draft = vm.draft ?: return
    Box(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 12.dp)) {
        TextButton(onClick = vm::endSelection, modifier = Modifier.align(Alignment.CenterStart)) {
            Text(stringResource(R.string.editor_cancel), style = TkType.body.copy(color = Tk.Secondary))
        }
        Text(
            pluralStringResource(R.plurals.editor_selected_count, selection.size, selection.size),
            style = TkType.body.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.align(Alignment.Center),
        )
        RoundIconButton(
            R.drawable.ic_trash, stringResource(R.string.cd_delete),
            onClick = { vm.deleteDraftItems(selection.filter { id -> draft.items.any { it.id == id } }.toSet()) },
            enabled = selection.isNotEmpty(), glass = false, background = Tk.Surface.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun Composer(
    kind: EntryKind,
    value: TextFieldValue,
    focus: FocusRequester,
    large: Boolean,
    onChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
) {
    val label = stringResource(kind.label())
    // Each tool uses the web's own phrase (TP-0108…0111) so word order fits every language.
    val description = stringResource(
        when (kind) {
            EntryKind.WORD -> R.string.cd_tool_word
            EntryKind.EMOJI -> R.string.cd_tool_emoji
            EntryKind.PLACE -> R.string.cd_tool_place
            EntryKind.TIME -> R.string.cd_tool_time
        },
    )
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = kind.editorStyle(large),
        cursorBrush = SolidColor(Tk.Primary),
        singleLine = true,
        // Each tool is its own input session with its own options, so switching tools makes the
        // keyboard restart: word and place come back to letters (even if the emoji panel was open),
        // and the emoji tool starts a session without suggestions for emoji input.
        keyboardOptions = if (kind == EntryKind.EMOJI) {
            KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = false, imeAction = ImeAction.Done)
        } else {
            KeyboardOptions(keyboardType = KeyboardType.Text, autoCorrectEnabled = true, imeAction = ImeAction.Done)
        },
        // Enter confirms one item. The IME finishes a Hangul composition before it sends the action.
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .focusRequester(focus)
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) Text(label, style = kind.editorStyle(large).copy(color = Tk.Muted.copy(alpha = 0.6f)))
                inner()
            }
        },
    )
}

/**
 * One fragment in the editor. Tap: edit it. Long press and release in place: a 삭제 menu (delete
 * only when chosen). Long press and drag up/down: carry it to another place, across kinds; a
 * dropped drag never opens the menu, and a cancelled one returns to where it started. Swipe left
 * still uncovers delete. TalkBack gets 위로 이동 / 아래로 이동 / 삭제 actions.
 */
@Composable
private fun EditorRow(
    item: Entry,
    index: Int,
    count: Int,
    selecting: Boolean,
    checked: Boolean,
    revealed: Boolean,
    onReveal: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onPickUp: () -> Unit,
    edgeScroll: (Float) -> Float,
    heightAt: (Int) -> Float?,
) {
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    val revealWidth = with(density) { 76.dp.toPx() }
    val spacing = with(density) { 4.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val target = if (revealed) -revealWidth else 0f
    val animated by animateFloatAsState(if (drag != 0f) drag else target, label = "reveal")
    val draggableState = rememberDraggableState { delta -> drag = ((if (drag == 0f) target else drag) + delta).coerceIn(-revealWidth, 0f) }
    val kindLabel = stringResource(item.kind.label())
    val shownText = item.shown()
    val selectedText = stringResource(R.string.cd_selected)
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    val deleteText = stringResource(R.string.item_delete)
    // The swipe-revealed button deletes this one fragment: TalkBack hears the action and its target.
    val deleteTarget = stringResource(R.string.cd_delete_item, "$kindLabel $shownText")

    var lifted by remember { mutableStateOf(false) }
    var liftY by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableFloatStateOf(0f) }
    var menuOpen by remember { mutableStateOf(false) }
    val currentIndex by androidx.compose.runtime.rememberUpdatedState(index)
    val currentCount by androidx.compose.runtime.rememberUpdatedState(count)
    // The row's own coordinates, inside the lifted layer, so a finger position maps to the window exactly.
    val coords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }

    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .onGloballyPositioned { rowHeight = it.size.height.toFloat() }
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer {
                translationY = liftY
                if (lifted && !reduceMotion) { scaleX = 1.02f; scaleY = 1.02f; shadowElevation = 12f }
            },
    ) {
        if (!selecting && (revealed || drag != 0f)) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = 60.dp, height = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Tk.Danger)
                    .clickable(role = Role.Button, onClick = onDelete)
                    .semantics { contentDescription = deleteTarget },
                contentAlignment = Alignment.Center,
            ) { TkIcon(R.drawable.ic_trash) }
        }
        Row(
            Modifier
                // The row keeps its text in place and its edge pulls back to uncover the delete button.
                .fillMaxWidth()
                .padding(end = with(density) { (if (selecting) 0f else -animated).toDp() })
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        lifted || menuOpen -> Tk.SheetRow.copy(alpha = 0.95f)
                        selecting && checked -> Tk.SheetRow.copy(alpha = 0.85f)
                        revealed || drag != 0f -> Tk.Surface.copy(alpha = 0.5f)
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    },
                )
                .testTag("editRow_${item.id}")
                .onGloballyPositioned { coords[0] = it }
                // One gesture handler for tap, long press in place (menu) and long-press drag (move).
                .pointerInput(item.id, selecting) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val long = awaitLongPressOrCancellation(down.id)
                        if (long == null) {
                            // Lifted before the long-press time without anyone taking it: a tap.
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed) { up.consume(); onClick() }
                            return@awaitEachGesture
                        }
                        if (selecting) { onClick(); return@awaitEachGesture }
                        lifted = true
                        val start = currentIndex
                        var at = start
                        var travelled = 0f
                        liftY = 0f
                        // The held row stays pinned to the finger: its offset is the finger's travel in
                        // the window, plus what the list scrolled, minus how far its slot has moved.
                        // Slot moves are summed from the neighbours' heights, never read back from a
                        // layout that may not have caught up with the last move yet.
                        fun windowY(p: androidx.compose.ui.geometry.Offset) = coords[0]?.takeIf { it.isAttached }?.localToWindow(p)?.y
                        val startY = windowY(down.position) ?: 0f
                        var lastY = startY
                        var scrolled = 0f
                        var slotShift = 0f
                        onPickUp() // one light tick, only when picked up
                        var cancelled = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || change.isConsumed) { cancelled = true; break }
                            if (!change.pressed) { change.consume(); break }
                            change.consume()
                            val fingerY = windowY(change.position) ?: lastY
                            travelled += kotlin.math.abs(fingerY - lastY)
                            lastY = fingerY
                            // Near the list's top or bottom edge, scroll so far places can be reached.
                            scrolled += edgeScroll(fingerY)
                            liftY = fingerY - startY + scrolled - slotShift
                            // Trade places once the row's centre passes a neighbour's middle; a fast drag
                            // may pass several rows in one event.
                            while (at < currentCount - 1) {
                                val next = (heightAt(at + 1) ?: rowHeight) + spacing
                                if (liftY <= next / 2) break
                                at++; onMove(at); slotShift += next; liftY -= next
                            }
                            while (at > 0) {
                                val prev = (heightAt(at - 1) ?: rowHeight) + spacing
                                if (liftY >= -prev / 2) break
                                at--; onMove(at); slotShift -= prev; liftY += prev
                            }
                        }
                        lifted = false
                        liftY = 0f
                        when {
                            cancelled -> if (at != start) onMove(start) // back to where it began
                            at == start && travelled < viewConfiguration.touchSlop -> menuOpen = true
                        }
                    }
                }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = !selecting && !lifted,
                    onDragStopped = {
                        onReveal(drag < -revealWidth / 2)
                        drag = 0f
                    },
                )
                .semantics {
                    contentDescription = "$kindLabel, $shownText"
                    onClick { onClick(); true }
                    if (selecting) {
                        selected = checked
                        stateDescription = if (checked) selectedText else ""
                    } else {
                        customActions = buildList {
                            if (currentIndex > 0) add(CustomAccessibilityAction(moveUp) { onMove(currentIndex - 1); true })
                            if (currentIndex < currentCount - 1) add(CustomAccessibilityAction(moveDown) { onMove(currentIndex + 1); true })
                            add(CustomAccessibilityAction(deleteText) { onDelete(); true })
                        }
                    }
                }
                .padding(horizontal = if (selecting) 12.dp else 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (checked) Tk.Secondary else androidx.compose.ui.graphics.Color.Transparent)
                        .then(if (!checked) Modifier.glass() else Modifier),
                    contentAlignment = Alignment.Center,
                ) { if (checked) TkIcon(R.drawable.ic_check, tint = Tk.Surface, size = 14.dp) }
                Spacer(Modifier.width(14.dp))
            }
            Text(shownText, style = TkType.title)
        }
        Box(Modifier.align(Alignment.BottomStart)) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = Tk.Sheet,
                shape = RoundedCornerShape(14.dp),
            ) {
                DropdownMenuItem(
                    text = { Text(deleteText, style = TkType.body.copy(color = Tk.DangerText)) },
                    leadingIcon = { TkIcon(R.drawable.ic_trash, size = 18.dp, tint = Tk.DangerText) },
                    onClick = { menuOpen = false; onDelete() },
                    modifier = Modifier.testTag("deleteMenu"),
                )
            }
        }
    }
}

@Composable
private fun SaveFailedBanner(onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .background(Tk.Surface.copy(alpha = 0.6f))
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.editor_save_failed), style = TkType.small.copy(color = Tk.Primary), modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.editor_retry), color = Tk.Focus) }
    }
}

/** The four tools stay above the keyboard (Figma 02 · 고정 입력도구의 활성 상태). */
@Composable
fun KindToolbar(current: EntryKind, guideEmoji: Boolean = false, onSelect: (EntryKind) -> Unit) {
    val selectedText = stringResource(R.string.cd_selected)
    Row(
        Modifier.fillMaxWidth().height(56.dp).glass(RoundedCornerShape(28.dp)).padding(5.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryKind.entries.forEach { kind ->
            val active = kind == current
            val label = stringResource(kind.label())
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .padding(horizontal = 10.dp)
                    .tourTarget(guideEmoji && kind == EntryKind.EMOJI)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) Tk.Focus else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(kind) }
                    .semantics {
                        contentDescription = label
                        selected = active
                        if (active) stateDescription = selectedText
                    },
                contentAlignment = Alignment.Center,
            ) { TkIcon(kind.icon(), tint = if (active) Tk.OnFocus else Tk.Primary) }
        }
    }
}

/**
 * Android has no API that opens another app's keyboard (Gboard, Samsung Keyboard…) on its emoji
 * panel, so the emoji tool opens the person's keyboard and this bubble, tied to the emoji tool,
 * tells them to switch it themselves. It names no key position, so it fits any keyboard.
 */
@Composable
private fun EmojiKeyHint() {
    Row(
        Modifier
            .padding(start = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Tk.Focus)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TkIcon(R.drawable.ic_emoji, tint = Tk.OnFocus, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.editor_emoji_prompt), style = TkType.label.copy(color = Tk.OnFocus, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun TimeSlot(pending: String, large: Boolean) {
    if (pending.isNotEmpty()) { PendingTime(pending, large); return }
    val label = stringResource(EntryKind.TIME.label())
    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = label }, contentAlignment = Alignment.CenterStart) {
        Text(label, style = EntryKind.TIME.editorStyle(large).copy(color = Tk.Muted.copy(alpha = 0.6f)))
    }
}

@Composable
private fun PendingTime(value: String, large: Boolean) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
            .background(Tk.SheetRow.copy(alpha = 0.8f)).padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(LocalTimeDisplay.current.format(value), style = EntryKind.TIME.editorStyle(large)) }
}

/**
 * Time tool (UX v0.1 §4 · 시간): Material's numeric time input inline above the tools, following the
 * device 12/24-hour setting. Only an explicit choice becomes pending (so ✓ never adds the default
 * time of an untouched picker); "+" confirms one time fragment (the current value, if untouched);
 * ✕ leaves everything as it was. Stored values are ASCII "HH:mm".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePanel(
    key: Any,
    initial: String?,
    onDisplayed: (String) -> Unit,
    onChoose: (String) -> Unit,
    onAdd: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val is24 = LocalTimeDisplay.current.is24Hour
    val start = initial?.let { com.tokipin.app.rules.TimeRules.parse(it) } ?: java.time.LocalTime.now()
    val state = androidx.compose.runtime.key(key, is24) {
        rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = is24)
    }
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.hour to state.minute }
            .collect { (h, m) ->
                val value = com.tokipin.app.rules.TimeRules.canonical(h, m)
                onDisplayed(value)
                if (value != initial && !(initial == null && h == start.hour && m == start.minute)) onChoose(value)
            }
    }
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).background(Tk.Surface.copy(alpha = 0.6f))
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeInput(
            state = state,
            modifier = Modifier.weight(1f),
            colors = TimePickerDefaults.colors(
                timeSelectorSelectedContainerColor = Tk.Focus,
                timeSelectorSelectedContentColor = Tk.OnFocus,
                timeSelectorUnselectedContainerColor = Tk.SheetRow,
                timeSelectorUnselectedContentColor = Tk.Primary,
                periodSelectorSelectedContainerColor = Tk.Focus,
                periodSelectorSelectedContentColor = Tk.OnFocus,
                periodSelectorUnselectedContentColor = Tk.Primary,
                periodSelectorBorderColor = Tk.GlassBorder,
            ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundIconButton(
                R.drawable.ic_plus, stringResource(R.string.cd_add_time),
                { onAdd(com.tokipin.app.rules.TimeRules.canonical(state.hour, state.minute)) },
                background = Tk.SheetRow,
            )
            RoundIconButton(R.drawable.ic_close, stringResource(R.string.time_picker_cancel), onCancel, glass = false)
        }
    }
}
