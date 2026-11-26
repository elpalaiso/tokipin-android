package com.tokipin.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.tokipin.app.R
import com.tokipin.app.data.EntryKind

@DrawableRes
fun EntryKind.icon(): Int = when (this) {
    EntryKind.WORD -> R.drawable.ic_word
    EntryKind.EMOJI -> R.drawable.ic_emoji
    EntryKind.PLACE -> R.drawable.ic_place
    EntryKind.TIME -> R.drawable.ic_time
}

@StringRes
fun EntryKind.label(): Int = when (this) {
    EntryKind.WORD -> R.string.kind_word
    EntryKind.EMOJI -> R.string.kind_emoji
    EntryKind.PLACE -> R.string.kind_place
    EntryKind.TIME -> R.string.kind_time
}

@Composable
fun TkIcon(@DrawableRes id: Int, modifier: Modifier = Modifier, tint: Color = Tk.Primary, size: Dp = 22.dp) {
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** 48dp touch target (Figma 04 · 터치 영역 Android 48dp 이상). */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glass: Boolean = true,
    enabled: Boolean = true,
    tint: Color = Tk.Primary,
    background: Color? = null,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .then(if (background != null) Modifier.clip(CircleShape).background(background).then(if (glass) Modifier.glass() else Modifier) else if (glass) Modifier.glass() else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            TkIcon(icon, tint = if (enabled) tint else tint.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun GlassPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes leading: Int? = null) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .glass(RoundedCornerShape(24.dp))
            .background(Tk.SheetRow.copy(alpha = 0.9f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            TkIcon(leading, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = TkType.label.copy(color = Tk.Primary))
    }
}

/** The bar that follows a delete: "항목을 삭제했어요. · 실행 취소". */
@Composable
fun UndoBar(undo: Undo, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(18.dp))
            .background(Tk.Surface.copy(alpha = 0.7f))
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(undo.message, style = TkType.label.copy(color = Tk.Primary), modifier = Modifier.weight(1f))
        TextButton(onClick = undo.action) {
            Text(stringResource(R.string.undo), color = Tk.Focus, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** A small "…" target used on cards and rows. */
@Composable
fun MoreButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { TkIcon(R.drawable.ic_more, tint = Tk.Secondary, size = 20.dp) }
}
