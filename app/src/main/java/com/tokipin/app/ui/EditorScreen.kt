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
import com.tokipin.app.data.EntryRules


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
    var showTimePicker by remember { mutableStateOf(false) }
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
        // The emoji tool accepts only emoji; letters, digits and Hangul are dropped as they arrive.
        val next = if (draft.kind == EntryKind.EMOJI && value.composition == null) {
            val kept = EntryRules.emojiOnly(value.text)
            if (kept == value.text) value else TextFieldValue(kept, TextRange(kept.length))
        } else value
        field = next
        vm.updateRaw(next.text)
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
                    if (item.id == draft.editId && selection == null) {
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
                                    item.kind == EntryKind.TIME -> { vm.startEditing(item); showTimePicker = true }
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
                if (draft.kind == EntryKind.EMOJI && selection == null) {
                    // Android has no API to open a keyboard on its emoji panel, so the hint points at the
                    // keyboard's own emoji key; anything that is not an emoji is dropped as it is typed.
                    Text(stringResource(R.string.editor_emoji_prompt), style = TkType.label, modifier = Modifier.padding(horizontal = 6.dp))
                }
                KindToolbar(draft.kind) { kind ->
                    revealedId = null
                    if (vm.setKind(kind)) {
                        when (kind) {
                            EntryKind.TIME -> { keyboard?.hide(); showTimePicker = true }
                                                        else -> focusField()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showTimePicker) {
        val editing = draft.items.firstOrNull { it.id == draft.editId && it.kind == EntryKind.TIME }
        TimeDialog(
            initial = editing?.value,
            onDismiss = {
                showTimePicker = false
                if (editing != null) vm.commitRaw()
            },
            onConfirm = { value ->
                showTimePicker = false
                if (editing != null) { vm.updateRaw(value); vm.commitRaw() } else vm.addValue(EntryKind.TIME, value)
            },
        )
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
                    .semantics { contentDescription = "$kindLabel ${item.value}" },
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
                    contentDescription = "$kindLabel, ${item.value}"
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
            Text(item.value, style = if (selecting) TkType.title else item.kind.textStyle())
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: String?, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val now = java.time.LocalTime.now()
    val (h, m) = initial?.split(":")?.map { it.toInt() } ?: listOf(now.hour, now.minute)
    val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Tk.Sheet,
        confirmButton = {
            TextButton(onClick = { onConfirm("%02d:%02d".format(state.hour, state.minute)) }) {
                Text(stringResource(R.string.time_picker_ok), color = Tk.Focus)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.time_picker_cancel), color = Tk.Secondary) }
        },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Tk.SheetRow,
                    selectorColor = Tk.Focus,
                    timeSelectorSelectedContainerColor = Tk.Focus,
                    timeSelectorSelectedContentColor = Tk.OnFocus,
                    timeSelectorUnselectedContainerColor = Tk.SheetRow,
                    timeSelectorUnselectedContentColor = Tk.Primary,
                ),
            )
        },
    )
}
