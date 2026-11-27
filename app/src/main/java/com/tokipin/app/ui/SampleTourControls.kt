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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tokipin.app.R
import kotlinx.coroutines.delay

private val accent = Color(0xFFD8E6CD)
private val ink = Color(0xFF203127)
private val shape = RoundedCornerShape(14.dp)

internal fun Modifier.tourTarget(active: Boolean) = if (active) border(2.dp, accent, shape) else this

@Composable
internal fun SampleControls(vm: AppViewModel, inline: Boolean = false, onNext: () -> Unit = { vm.tour?.next(vm) }) {
    val tour = vm.tour ?: return
    if (tour.sheet == TourSheet.Invitation) return
    val rail = if (inline) Modifier else Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
    Row(rail.testTag("onboarding"),
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (tour.guided) {
            if (vm.screen == Screen.Search) RoundIconButton(R.drawable.ic_close, stringResource(R.string.onboarding_dismiss_guide), tour::exitOptions, glass = false)
            val screen = vm.screen
            val currentCue = tour.currentCue(vm)
            SampleAction(stringResource(R.string.onboarding_next), {
                // A real gesture or IME commit may already have advanced the button that received this tap.
                if (vm.screen == screen && currentCue == tour.currentCue(vm)) onNext()
            }, Modifier.testTag("sampleNext"), actionLabel = stringResource(R.string.onboarding_run_action))
        } else {
            val replay = if (vm.screen == Screen.Editor) R.string.onboarding_replay_editor else R.string.onboarding_replay_explore
            RoundIconButton(R.drawable.ic_bubble, stringResource(replay), { tour.replay(vm) }, Modifier.testTag("sampleReplay"), glass = false)
            SampleAction(stringResource(R.string.onboarding_start), tour::invite, Modifier.testTag("sampleEarlyStart"))
        }
    }
}

@Composable
internal fun SampleHint(vm: AppViewModel, message: Int, modifier: Modifier = Modifier) {
    val tour = vm.tour ?: return
    if (!tour.guided || tour.sheet == TourSheet.Invitation) return
    Row(modifier.padding(horizontal = 12.dp, vertical = 2.dp).widthIn(max = 380.dp).background(accent, shape).testTag("sampleGuide"),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(message), style = TkType.label.copy(color = ink),
            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp, top = 7.dp, bottom = 7.dp)
                .semantics { liveRegion = LiveRegionMode.Polite })
        val close = if (vm.screen == Screen.Editor) R.string.onboarding_dismiss_editor else R.string.onboarding_dismiss_guide
        RoundIconButton(R.drawable.ic_close, stringResource(close), tour::exitOptions, Modifier.testTag("sampleDismiss"), glass = false, tint = ink)
    }
}

@Composable
internal fun SampleReceipt(tour: SampleTour) {
    val text = tour.receipt
    if (text == 0) return
    val a11y = LocalAccessibilityManager.current
    LaunchedEffect(text) {
        delay(a11y?.calculateRecommendedTimeoutMillis(4_000, containsText = true) ?: 4_000)
        tour.clearReceipt()
    }
    Text(stringResource(text), style = TkType.label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }.testTag("sampleReceipt"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SampleExitSheet(vm: AppViewModel, onStart: () -> Unit) {
    val tour = vm.tour ?: return
    val sheet = tour.sheet ?: return
    val finish = sheet == TourSheet.Invitation
    val dismiss = if (finish) tour::explore else tour::cancelOptions
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(sheet) { keyboard?.hide() }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tk.Sheet, dragHandle = null) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = if (finish) R.string.onboarding_invitation else if (vm.screen == Screen.Editor) R.string.onboarding_dismiss_editor else R.string.onboarding_dismiss_guide
                Text(stringResource(title), style = TkType.title, modifier = Modifier.weight(1f).semantics { heading() })
                RoundIconButton(R.drawable.ic_close, stringResource(R.string.cd_close), dismiss, Modifier.testTag("sampleSheetClose"), glass = false)
            }
            if (!finish) SampleAction(stringResource(R.string.onboarding_keep_exploring), tour::explore, Modifier.fillMaxWidth().testTag("sampleExplore"), primary = false)
            SampleAction(stringResource(R.string.onboarding_start), if (finish) onStart else tour::invite,
                Modifier.fillMaxWidth().testTag(if (finish) "onboardingStart" else "sampleEarlyStart"))
        }
    }
}

@Composable
private fun SampleAction(text: String, click: () -> Unit, modifier: Modifier, primary: Boolean = true, actionLabel: String? = null) {
    Box(modifier.heightIn(min = 48.dp).clip(shape).background(if (primary) accent else Tk.SheetRow)
        .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = click).padding(horizontal = 15.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TkType.label.copy(color = if (primary) ink else Tk.Primary), textAlign = TextAlign.Center)
    }
}
