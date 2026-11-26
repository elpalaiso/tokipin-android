package com.tokipin.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokipin.app.R
import java.time.LocalDate

/** Opened from the last entry of the browse view menu (Figma 04 · 설정). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: AppViewModel) {
    var confirmRangeSamples by rememberSaveable { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val canExport = !vm.journal.isEmpty
    ModalBottomSheet(
        onDismissRequest = vm::closeSettings,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tk.Sheet,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings), style = TkType.title, modifier = Modifier.weight(1f).semantics { heading() })
                RoundIconButton(R.drawable.ic_close, stringResource(R.string.cd_close), vm::closeSettings, glass = false, background = Tk.SheetRow)
            }
            Group {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable(role = Role.Switch) { vm.setHaptics(!vm.hapticsEnabled) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_haptics), style = TkType.body, modifier = Modifier.weight(1f))
                    Switch(
                        checked = vm.hapticsEnabled,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = Tk.Focus, checkedThumbColor = Tk.Surface),
                    )
                }
                HorizontalDivider(color = Tk.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp)
                        .clickable(enabled = canExport, role = Role.Button) {
                            exportLauncher.launch("tokipin-records-${com.tokipin.app.rules.AppClock.today()}.json")
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_export), style = TkType.body.copy(color = if (canExport) Tk.Primary else Tk.Muted))
                        Text(
                            stringResource(if (canExport) R.string.settings_export_body else R.string.settings_export_empty),
                            style = TkType.small,
                        )
                    }
                    TkIcon(R.drawable.ic_download, size = 20.dp, tint = if (canExport) Tk.Primary else Tk.Muted)
                }
            }
            if (com.tokipin.app.data.SampleData.AVAILABLE) {
                // Debug builds only (src/debug); release builds have no sample data and no rows.
                Text(stringResource(R.string.debug_section), style = TkType.body.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 4.dp))
                Group {
                    LinkRow(stringResource(R.string.debug_samples)) { vm.replaceWithSamples(); vm.closeSettings() }
                    HorizontalDivider(color = Tk.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    LinkRow(stringResource(R.string.debug_range_samples)) { confirmRangeSamples = true }
                    HorizontalDivider(color = Tk.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                    LinkRow(stringResource(R.string.debug_clear)) { vm.clearAllRecords(); vm.closeSettings() }
                }
            }
            Text(stringResource(R.string.settings_storage_title), style = TkType.body.copy(fontSize = 15.sp), modifier = Modifier.padding(top = 4.dp))
            Text(stringResource(R.string.settings_storage_body), style = TkType.label)
            Group {
                LinkRow(stringResource(R.string.privacy_policy)) { vm.openPolicy(Policy.PRIVACY) }
                HorizontalDivider(color = Tk.Divider, modifier = Modifier.padding(horizontal = 16.dp))
                LinkRow(stringResource(R.string.terms)) { vm.openPolicy(Policy.TERMS) }
            }
        }
    }
    if (confirmRangeSamples && com.tokipin.app.data.SampleData.AVAILABLE) {
        AlertDialog(
            onDismissRequest = { confirmRangeSamples = false },
            title = { Text(stringResource(R.string.debug_range_samples)) },
            text = { Text(stringResource(R.string.debug_range_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRangeSamples = false
                    vm.replaceWithCalendarRangeSamples()
                    vm.closeSettings()
                }) { Text(stringResource(R.string.debug_range_replace)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRangeSamples = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Tk.SheetRow.copy(alpha = 0.7f))) { content() }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = TkType.body, modifier = Modifier.weight(1f))
        TkIcon(R.drawable.ic_chevron_right, size = 18.dp)
    }
}

@Composable
fun PolicyScreen(policy: Policy, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val (title, lead, sections) = when (policy) {
        Policy.PRIVACY -> Triple(
            R.string.privacy_policy, R.string.privacy_lead,
            listOf(
                R.string.privacy_h1 to R.string.privacy_b1, R.string.privacy_h2 to R.string.privacy_b2,
                R.string.privacy_h3 to R.string.privacy_b3, R.string.privacy_h4 to R.string.privacy_b4,
                R.string.privacy_h5 to R.string.privacy_b5,
            ),
        )
        Policy.TERMS -> Triple(
            R.string.terms, R.string.terms_lead,
            listOf(
                R.string.terms_h1 to R.string.terms_b1, R.string.terms_h2 to R.string.terms_b2,
                R.string.terms_h3 to R.string.terms_b3, R.string.terms_h4 to R.string.terms_b4,
            ),
        )
    }
    TkBackground {
        Column(Modifier.fillMaxSize().contentColumn().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(R.drawable.ic_back, stringResource(R.string.cd_close), onBack)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(title), style = TkType.title, modifier = Modifier.semantics { heading() })
            }
            HorizontalDivider(color = Tk.Divider, modifier = Modifier.padding(horizontal = 20.dp))
            Column(
                Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.policy_draft_badge), style = TkType.small.copy(color = Tk.Focus))
                Text(stringResource(lead), style = TkType.body.copy(fontSize = 17.sp, lineHeight = 27.sp))
                sections.forEach { (h, b) ->
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(h), style = TkType.body.copy(fontSize = 17.sp), modifier = Modifier.semantics { heading() })
                    Text(stringResource(b), style = TkType.label.copy(fontSize = 15.sp, lineHeight = 24.sp, color = Tk.Secondary))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Shown only when the read takes long enough to notice (Figma 04 · 기록 불러오기, 벤토 스켈레톤). */
@Composable
fun LoadingSkeleton() {
    val description = stringResource(R.string.loading)
    TkBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).semantics(mergeDescendants = true) {
                contentDescription = description
            },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row { Bone(Modifier.width(76.dp).height(34.dp)); Spacer(Modifier.weight(1f)); Bone(Modifier.width(34.dp).height(34.dp)); Spacer(Modifier.width(18.dp)); Bone(Modifier.width(34.dp).height(34.dp)) }
            Bone(Modifier.width(74.dp).height(12.dp))
            Bone(Modifier.fillMaxWidth().height(150.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Bone(Modifier.weight(1f).height(130.dp)); Bone(Modifier.weight(1f).height(130.dp))
            }
        }
    }
}

@Composable
private fun Bone(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Tk.Primary.copy(alpha = 0.07f)))
}

@Composable
fun LoadFailedScreen(onRetry: () -> Unit) {
    TkBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).padding(top = 48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.load_failed_title), style = TkType.title, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.load_failed_body), style = TkType.label)
            GlassPill(stringResource(R.string.editor_retry), onClick = onRetry)
        }
    }
}
