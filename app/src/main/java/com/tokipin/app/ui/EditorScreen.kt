package com.tokipin.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

@Composable
fun EditorScreen(vm: AppViewModel) {
    val draft = vm.draft ?: return
    val selection = vm.selection
    val keyboard = LocalSoftwareKeyboardController.current
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

    LaunchedEffect(draft.kind, draft.editId, selection == null) {
        if (selection == null && draft.kind != EntryKind.TIME) focusField()
    }

    TkBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            if (selection != null) SelectionHeader(vm, selection) else EditorHeader(vm, draft.items.isNotEmpty())

            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(draft.items, key = { it.id }) { item ->
                    if (item.id == draft.editId && selection == null && draft.kind == EntryKind.TIME) {
                        PendingTime(draft.raw.ifEmpty { item.value })
                    } else if (item.id == draft.editId && selection == null) {
                        Composer(draft.kind, field, focus, onChange = { onFieldChange(it) }, onDone = {
                            if (vm.commitRaw()) focusField()
                        })
                    } else {
                        EditorRow(
                            item = item,
                            selecting = selection != null,
                            checked = selection?.contains(item.id) == true,
                            revealed = revealedId == item.id,
                            onReveal = { revealedId = if (it) item.id else null },
                            onClick = {
                                revealedId = null
                                when {
                                    selection != null -> vm.toggleSelected(item.id)
                                    else -> vm.startEditing(item)
                                }
                            },
                            onLongClick = {
                                revealedId = null
                                if (selection == null) vm.beginSelection()
                                vm.toggleSelected(item.id)
                            },
                            onDelete = { revealedId = null; vm.deleteDraftItems(setOf(item.id)) },
                        )
                    }
                }
                if (draft.editId == null && selection == null && draft.kind != EntryKind.TIME) {
                    item(key = "composer") {
                        Composer(draft.kind, field, focus, onChange = { onFieldChange(it) }, onDone = {
                            if (vm.commitRaw()) focusField()
                        })
                    }
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
                if (draft.kind == EntryKind.EMOJI && selection == null && (field.text.isEmpty() || emojiBlocked)) {
                    // Android cannot open an arbitrary keyboard on its emoji panel, so a short hint shows
                    // only while it helps: the field is empty, or something that is not an emoji was typed.
                    Text(stringResource(R.string.editor_emoji_prompt), style = TkType.label, modifier = Modifier.padding(horizontal = 6.dp))
                }
                if (draft.kind == EntryKind.TIME && selection == null) {
                    TimePanel(
                        key = Triple(draft.editId, draft.items.size, draft.raw.isEmpty()),
                        initial = draft.raw.takeIf { it.isNotEmpty() },
                        onChoose = { vm.updateRaw(it) },
                        onAdd = { value -> vm.updateRaw(value); vm.commitRaw() },
                        onCancel = vm::cancelPending,
                    )
                }
                KindToolbar(draft.kind) { kind ->
                    revealedId = null
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
private fun EditorHeader(vm: AppViewModel, hasItems: Boolean) {
    val draft = vm.draft ?: return
    Box(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 12.dp)) {
        RoundIconButton(R.drawable.ic_back, stringResource(R.string.cd_back_keep_draft), vm::leaveEditor, Modifier.align(Alignment.CenterStart))
        Text(draft.localDate.fullLabel(), style = TkType.label, modifier = Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (hasItems) RoundIconButton(R.drawable.ic_more, stringResource(R.string.cd_select_items), vm::beginSelection)
            RoundIconButton(R.drawable.ic_check, stringResource(R.string.cd_save_record), vm::saveDraft)
        }
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
            stringResource(R.string.editor_selected_count, selection.size),
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
    onChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
) {
    val label = stringResource(kind.label())
    val description = stringResource(R.string.cd_kind_tool, label)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = kind.textStyle(),
        cursorBrush = SolidColor(Tk.Primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        // Enter confirms one item. The IME finishes a Hangul composition before it sends the action.
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .focusRequester(focus)
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) Text(label, style = kind.textStyle().copy(color = Tk.Muted.copy(alpha = 0.6f)))
                inner()
            }
        },
    )
}

@Composable
private fun EditorRow(
    item: Entry,
    selecting: Boolean,
    checked: Boolean,
    revealed: Boolean,
    onReveal: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val revealWidth = with(LocalDensity.current) { 76.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    val target = if (revealed) -revealWidth else 0f
    val animated by animateFloatAsState(if (drag != 0f) drag else target, label = "reveal")
    val draggableState = rememberDraggableState { delta -> drag = ((if (drag == 0f) target else drag) + delta).coerceIn(-revealWidth, 0f) }
    val kindLabel = stringResource(item.kind.label())
    val shownText = item.shown()
    val selectedText = stringResource(R.string.cd_selected)

    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (!selecting && (revealed || drag != 0f)) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(width = 60.dp, height = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Tk.Danger)
                    .clickable(role = Role.Button, onClick = onDelete)
                    .semantics { contentDescription = "$kindLabel $shownText" },
                contentAlignment = Alignment.Center,
            ) { TkIcon(R.drawable.ic_trash) }
        }
        Row(
            Modifier
                // The row keeps its text in place and its edge pulls back to uncover the delete button
                // (Figma 04 · 밀어서 삭제).
                .fillMaxWidth()
                .padding(end = with(LocalDensity.current) { (if (selecting) 0f else -animated).toDp() })
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        selecting && checked -> Tk.SheetRow.copy(alpha = 0.85f)
                        revealed || drag != 0f -> Tk.Surface.copy(alpha = 0.5f)
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    },
                )
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = !selecting,
                    onDragStopped = {
                        onReveal(drag < -revealWidth / 2)
                        drag = 0f
                    },
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .semantics {
                    contentDescription = "$kindLabel, $shownText"
                    if (selecting) {
                        selected = checked
                        stateDescription = if (checked) selectedText else ""
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
            Text(item.shown(), style = if (selecting) TkType.title else item.kind.textStyle())
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
fun KindToolbar(current: EntryKind, onSelect: (EntryKind) -> Unit) {
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

@Composable
private fun PendingTime(value: String) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp))
            .background(Tk.SheetRow.copy(alpha = 0.8f)).padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(LocalTimeDisplay.current.format(value), style = EntryKind.TIME.textStyle()) }
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
