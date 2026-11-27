package com.tokipin.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokipin.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsageSharingSheet(onPresented: () -> Unit, onChoice: (Boolean) -> Unit, onNotNow: () -> Unit) {
    val sheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ModalBottomSheet(
        onDismissRequest = { onChoice(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tk.Sheet,
        shape = sheetShape,
        dragHandle = null,
    ) {
        LaunchedEffect(Unit) { onPresented() }
        Column(
            Modifier.fillMaxWidth().testTag("usageInvitation")
                .background(Brush.verticalGradient(listOf(Color(0xFF343F39), Color(0xFF28332E))))
                .border(1.dp, Color(0x995C6A61), sheetShape)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 28.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp)
                .background(Color(0xFF8C9A90), RoundedCornerShape(2.dp)))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.usage_prompt), style = TkType.title.copy(fontSize = 23.sp, lineHeight = 33.sp),
                modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.usage_body), style = TkType.label.copy(fontSize = 15.sp, lineHeight = 23.sp))
            Spacer(Modifier.height(20.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 280.dp || LocalDensity.current.fontScale >= 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceButton(stringResource(R.string.usage_later), Modifier.fillMaxWidth().testTag("usageLater"), onNotNow)
                        ChoiceButton(stringResource(R.string.usage_share), Modifier.fillMaxWidth()) { onChoice(true) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChoiceButton(stringResource(R.string.usage_later), Modifier.weight(1f).testTag("usageLater"), onNotNow)
                        ChoiceButton(stringResource(R.string.usage_share), Modifier.weight(1f)) { onChoice(true) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun UsageLaterHint() {
    Text(
        text = stringResource(R.string.usage_later_hint),
        style = TkType.label,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            .background(Tk.Surface, RoundedCornerShape(12.dp))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun ChoiceButton(label: String, modifier: Modifier, action: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.heightIn(min = 48.dp).clip(shape)
            .background(Color(0xFF414E46)).border(1.dp, Color(0xFF69766E), shape)
            .clickable(role = Role.Button, onClick = action).padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TkType.body.copy(fontWeight = FontWeight.Medium), textAlign = TextAlign.Center)
    }
}
