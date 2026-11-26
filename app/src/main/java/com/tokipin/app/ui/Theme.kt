package com.tokipin.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.tokipin.app.R
import com.tokipin.app.rules.HapticRules
import com.tokipin.app.rules.NavEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle
import com.tokipin.app.AppLocale
import java.util.Locale

/** Figma 01 · 기본 팔레트 (세이지 차콜 / 다크 퍼스트). */
object Tk {
    val Canvas = Color(0xFF39453D)
    val Surface = Color(0xFF252C29)
    val Primary = Color(0xFFF3F3EC)
    val Secondary = Color(0xFFC3CDC5)
    val Focus = Color(0xFFBBD2F2)
    val OnFocus = Color(0xFF263746)
    val Muted = Color(0xFF9AA59D)
    val Card = Color(0xFF262D2A).copy(alpha = 0.92f)
    val CardBorder = Color(0x33D2DAD1)
    val GlassBorder = Color(0xA1D2DAD1)
    val Glass = Color(0x2E64716A)
    val Divider = Color(0x2ED2DAD1)
    val Danger = Color(0xFF7E4A45)
    val DangerSoft = Color(0xFF4A3632)
    val DangerText = Color(0xFFF0B8AE)
    val Sheet = Color(0xFF2E3833)
    val SheetRow = Color(0xFF3A4640)
}

/** Sage on the left, slate at the top right, warm charcoal at the bottom (Figma 01 · 세이지와 슬레이트). */
@Composable
fun TkBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF39453D), Color(0xFF34413B), Color(0xFF3B4A55))))
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x00000000), Color(0x9E443E39)))),
        content = content,
    )
}

@Composable
fun TokipinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Tk.Focus,
            onPrimary = Tk.OnFocus,
            background = Tk.Canvas,
            surface = Tk.Sheet,
            onSurface = Tk.Primary,
            onBackground = Tk.Primary,
            surfaceContainerHigh = Tk.Sheet,
            surfaceContainerHighest = Tk.SheetRow,
            onSurfaceVariant = Tk.Secondary,
            secondaryContainer = Tk.Focus,
            onSecondaryContainer = Tk.OnFocus,
        ),
        content = content,
    )
}

/**
 * DM Sans (variable, SIL OFL) for numbers and Latin. Android falls back to the system Noto Sans CJK
 * for Hangul, which matches Noto Sans KR without bundling the 10 MB font the iOS app carries.
 */
@OptIn(ExperimentalTextApi::class)
val DmSans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(R.font.dm_sans, weight = w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    },
)

object TkType {
    private val base = TextStyle(fontFamily = DmSans)
    val word = base.merge(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, color = Tk.Primary)
    val itemSecondary = base.merge(fontSize = 24.sp, lineHeight = 34.sp, color = Tk.Primary)
    val title = base.merge(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, color = Tk.Primary)
    val body = base.merge(fontSize = 16.sp, lineHeight = 24.sp, color = Tk.Primary)
    val label = base.merge(fontSize = 14.sp, lineHeight = 20.sp, color = Tk.Secondary)
    val small = base.merge(fontSize = 12.sp, lineHeight = 16.sp, color = Tk.Secondary)
}

/** Readable content width on tablets, foldables and landscape: centered, at most 640dp wide. */
val CONTENT_MAX_WIDTH = 640.dp

fun Modifier.contentColumn(): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = CONTENT_MAX_WIDTH)

fun Modifier.glass(shape: Shape = CircleShape): Modifier = this
    .clip(shape)
    .background(Tk.Glass, shape)
    .border(0.8.dp, Tk.GlassBorder, shape)

// region Dates — Figma 04 · 화면 속 표기: YYYY.MM, YYYY.MM.DD(요일), cards "DD / 요일"; weekday from the locale.

fun LocalDate.fullLabel(locale: Locale = AppLocale.current): String =
    "%04d.%02d.%02d(%s)".format(year, monthValue, dayOfMonth, dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale))

fun LocalDate.cardLabel(locale: Locale = AppLocale.current): String =
    "%d / %s".format(dayOfMonth, dayOfWeek.getDisplayName(DateTextStyle.SHORT, locale))

fun YearMonth.label(): String = "%04d.%02d".format(year, monthValue)

fun searchableDate(date: LocalDate): String = "%04d.%02d.%02d".format(date.year, date.monthValue, date.dayOfMonth)

// endregion

/**
 * The only place the app asks for vibration (UX v0.1 §3). Each user action reports what it did;
 * [HapticRules] turns that into at most one light selection tick, and none when the app setting is
 * off. performHapticFeedback also respects the system touch-feedback setting. Keyboards and the time
 * picker give their own feedback; the app never adds to it.
 */
class Haptics(private val view: View, private val enabled: () -> Boolean) {
    var requested = 0
        private set

    fun report(vararg events: NavEvent) {
        repeat(HapticRules.ticks(events.toList(), enabled())) {
            requested++
            totalRequested++
            val constant = if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK
            view.performHapticFeedback(constant)
        }
    }

    companion object {
        /** Ticks the app asked for since launch; read by the instrumented tests. */
        @Volatile var totalRequested = 0
    }

    /** Convenience: a committed navigation when [changed], silent otherwise. */
    fun commitIf(changed: Boolean, event: NavEvent) {
        if (changed) report(event) else report(NavEvent.RESELECT)
    }
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { error("no haptics") }

/** System "remove animations": transitions snap or fade within 100 ms (UX v0.1 · settling). */
val LocalReduceMotion = staticCompositionLocalOf { false }
