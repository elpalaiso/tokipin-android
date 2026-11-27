package com.tokipin.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tokipin.app.AppLocale
import com.tokipin.app.R
import com.tokipin.app.rules.CalendarRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import java.time.YearMonth
import java.time.format.TextStyle

private val sampleAccent = Color(0xFFD8E6CD)
private val onSampleAccent = Color(0xFF203127)
private val sampleShape = RoundedCornerShape(14.dp)

@Composable
internal fun SampleCalendarScreen(sample: SampleCalendar, onStart: () -> Unit) {
    BackHandler(enabled = sample.page != SamplePage.Month, onBack = sample::back)
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(sample.showReceipt) {
        if (sample.showReceipt) {
            delay(accessibility?.calculateRecommendedTimeoutMillis(4_000, containsText = true) ?: 4_000)
            sample.clearReceipt()
        }
    }
    TkBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("onboarding")) {
            SampleHeader(sample, onStart)
            SampleNavigation(sample)
            when (sample.page) {
                SamplePage.Month -> key(sample.month) { SampleMonth(sample, Modifier.weight(1f, fill = false)) }
                SamplePage.Day -> key(sample.day) { SampleDay(sample) }
                SamplePage.Practice -> SamplePractice(sample)
            }
            if (sample.showReceipt) {
                Text(stringResource(R.string.onboarding_emoji_saved), style = TkType.label,
                    modifier = Modifier.testTag("sampleReceipt").padding(horizontal = 20.dp, vertical = 10.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}

@Composable
private fun SampleHeader(sample: SampleCalendar, onStart: () -> Unit) {
    val title = stringResource(R.string.onboarding_sample_calendar)
    val action = stringResource(R.string.onboarding_start_journal)
    val measure = rememberTextMeasurer()
    val density = LocalDensity.current
    val showBack = sample.page != SamplePage.Month
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        val textWidth = with(density) {
            (measure.measure(title, TkType.body).size.width + measure.measure(action, TkType.label).size.width).toDp()
        }
        val stacked = maxWidth < textWidth + 36.dp + (if (showBack) 48.dp else 0.dp)
        @Composable fun Title(modifier: Modifier) {
            Row(modifier, verticalAlignment = Alignment.CenterVertically) {
                if (showBack) {
                    val destination = if (sample.page == SamplePage.Day) R.string.view_month else R.string.view_daily
                    RoundIconButton(R.drawable.ic_back, stringResource(R.string.cd_back_to, stringResource(destination)),
                        sample::back, Modifier.testTag("sampleBack"), glass = false)
                }
                Text(title, style = TkType.body, modifier = Modifier.semantics { heading() })
            }
        }
        if (stacked) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Title(Modifier)
                SampleButton(action, onStart, Modifier.fillMaxWidth().testTag("onboardingStart"), filled = true)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Title(Modifier.weight(1f))
                SampleButton(action, onStart, Modifier.testTag("onboardingStart"), filled = true)
            }
        }
    }
}

@Composable
private fun SampleNavigation(sample: SampleCalendar) {
    val date = if (sample.page == SamplePage.Practice) sample.practiceDay else sample.day
    val isMonth = sample.page == SamplePage.Month
    val description = if (isMonth) DateWords.yearMonth(sample.month) else DateWords.spoken(date)
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun Arrow(previous: Boolean) {
            val target = sample.adjacent(if (previous) -1 else 1)
            val label = target?.let { if (isMonth) DateWords.yearMonth(YearMonth.from(it)) else DateWords.spoken(it) }
            val icon = if (previous) R.drawable.ic_back else R.drawable.ic_chevron_right
            RoundIconButton(icon, label.orEmpty(), { target?.let(sample::navigate) },
                Modifier.testTag(if (previous) "samplePrevious" else "sampleNext"), glass = false, enabled = target != null)
        }
        Arrow(true)
        Text(if (isMonth) sample.month.label() else searchableDate(date), style = TkType.title,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f).semantics { contentDescription = description; heading() })
        Arrow(false)
    }
}

@Composable
private fun SampleMonth(sample: SampleCalendar, modifier: Modifier) {
    val month = sample.month
    val firstDay = AppLocale.firstDayOfWeek
    val lead = CalendarRules.firstDayColumn(month, firstDay)
    val weeks = (lead + month.lengthOfMonth() + 6) / 7
    val featured = sample.featured(month)
    val focus = if (sample.showGuide) featured else sample.savedDay?.takeIf { YearMonth.from(it) == month }
    val targetWeek = focus?.let { (lead + it.dayOfMonth - 1) / 7 }
    val calendarState = rememberLazyListState()
    // On smaller screens, keep the indicated date visible without locking the calendar's scroll.
    LaunchedEffect(focus) {
        if (targetWeek != null) {
            val visible = snapshotFlow { calendarState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
            val item = visible.firstOrNull { it.index == targetWeek }
            if (item == null || item.offset + item.size > calendarState.layoutInfo.viewportEndOffset) {
                calendarState.scrollToItem(targetWeek)
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        repeat(7) { index ->
            Text(firstDay.plus(index.toLong()).getDisplayName(TextStyle.NARROW, AppLocale.format), style = TkType.small,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
    }
    val panel = RoundedCornerShape(20.dp)
    val res = LocalResources.current
    LazyColumn(modifier.padding(horizontal = 10.dp).clip(panel).background(Tk.Surface.copy(alpha = .65f))
        .border(1.dp, Tk.CardBorder, panel).testTag("sampleGrid"), state = calendarState,
        contentPadding = PaddingValues(4.dp)) {
        items(weeks) { week ->
            if (week > 0) HorizontalDivider(color = Tk.Divider)
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val number = week * 7 + col - lead + 1
                    Box(Modifier.weight(1f)) {
                        if (number in 1..month.lengthOfMonth()) {
                            val date = month.atDay(number)
                            val outline = if (date == focus) Modifier.border(2.dp, sampleAccent, RoundedCornerShape(10.dp)) else Modifier
                            MonthCell(date, sample.entries(date, res), selected = date == sample.savedDay, today = date == sample.today,
                                modifier = outline, minHeight = 82.dp) { sample.openDay(date) }
                        }
                    }
                }
            }
            if (sample.showGuide && week == targetWeek) {
                SampleGuide(stringResource(R.string.onboarding_open_day), sample::dismissGuide, Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

@Composable
private fun SampleDay(sample: SampleCalendar) {
    val entries = sample.entries(sample.day, LocalResources.current)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 24.dp)) {
        if (entries.isEmpty()) Text(stringResource(R.string.day_empty), style = TkType.body)
        entries.forEach { DailyItem(it, highlighted = false) }
        Spacer(Modifier.height(30.dp))
        SampleButton(stringResource(R.string.onboarding_practice_emoji), sample::startPractice, Modifier.testTag("samplePractice"),
            outlined = sample.showGuide, enabled = sample.canPractice())
    }
}

@Composable
private fun SamplePractice(sample: SampleCalendar) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)) {
        if (sample.showGuide && sample.choice == null) {
            SampleGuide(stringResource(R.string.onboarding_choose_emoji), sample::dismissGuide)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (emoji in SampleCalendar.choices) {
                val active = sample.choice == emoji
                val outlined = active || (sample.showGuide && sample.choice == null && emoji == SampleCalendar.choices.first())
                Box(Modifier.weight(1f).heightIn(min = 60.dp).clip(sampleShape).background(Tk.SheetRow)
                    .border(if (outlined) 2.dp else 1.dp, if (outlined) sampleAccent else Tk.CardBorder, sampleShape)
                    .clickable(role = Role.RadioButton) { sample.select(emoji) }.semantics { selected = active }
                    .testTag("sampleChip_$emoji"), contentAlignment = Alignment.Center) {
                    Text(emoji, fontSize = 24.sp, modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        val enabled = sample.choice != null
        val saveLabel = stringResource(R.string.cd_save_record)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (enabled && sample.showGuide) {
                SampleGuide(stringResource(R.string.onboarding_save_guide), sample::dismissGuide, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.size(48.dp).clip(sampleShape)
                .background(if (enabled) sampleAccent else Tk.SheetRow)
                .clickable(enabled = enabled, role = Role.Button, onClick = sample::savePractice)
                .testTag("sampleSave").semantics { contentDescription = saveLabel },
                contentAlignment = Alignment.Center) {
                TkIcon(R.drawable.ic_check, tint = if (enabled) onSampleAccent else Tk.Muted)
            }
        }
    }
}

@Composable
private fun SampleGuide(text: String, dismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.widthIn(max = 420.dp).padding(vertical = 4.dp).background(sampleAccent, sampleShape)
        .testTag("sampleGuide"), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = TkType.label.copy(color = onSampleAccent), modifier = Modifier.weight(1f, fill = false)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
        RoundIconButton(R.drawable.ic_close, stringResource(R.string.onboarding_dismiss_guide), dismiss,
            Modifier.testTag("sampleDismiss"), glass = false, tint = onSampleAccent)
    }
}

@Composable
private fun SampleButton(
    text: String, click: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false,
    outlined: Boolean = false, enabled: Boolean = true,
) {
    Box(modifier.heightIn(min = 48.dp).clip(sampleShape)
        .background(if (filled) sampleAccent else Tk.SheetRow)
        .then(if (outlined) Modifier.border(2.dp, sampleAccent, sampleShape) else Modifier)
        .clickable(enabled = enabled, role = Role.Button, onClick = click).padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = TkType.label.copy(color = if (!enabled) Tk.Muted else if (filled) onSampleAccent else Tk.Primary), textAlign = TextAlign.Center)
    }
}
