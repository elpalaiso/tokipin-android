package com.tokipin.app

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tokipin.app.rules.HapticRules
import com.tokipin.app.rules.Level
import com.tokipin.app.rules.PinchRules
import com.tokipin.app.ui.AppViewModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

/**
 * The 20 shared paneNavigation cases replayed on the app's real AppViewModel in two panes (not on
 * the Adaptive port): the start state is set, the matching app action runs, and level, calendar
 * level, selected date, committed 기록한 날 month and the tick count are compared with the JSON.
 */
@RunWith(AndroidJUnit4::class)
class PaneNavigationReplayTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val cases by lazy {
        Json.parseToJsonElement(
            InstrumentationRegistry.getInstrumentation().context.assets.open("Tokipin-Native-UX-v0.4.json").reader().readText(),
        ).jsonObject["tests"]!!.jsonArray.map { it.jsonObject }.filter { it["group"]!!.jsonPrimitive.content == "paneNavigation" }
    }

    @Before fun launch() {
        // Records in the months the cases use, so 기록한 날 lists them.
        val days = listOf("2026-09-01", "2026-11-12", "2026-11-25", "2026-11-26").joinToString(",") {
            """{"date":"$it","updatedAt":"${it}T10:00:00Z","items":[{"id":"$it","kind":"word","value":"기록"}]}"""
        }
        com.tokipin.app.rules.AppClock.override = LocalDate.of(2026, 11, 28)
        File(context.filesDir, "tokipin").apply { deleteRecursively(); mkdirs() }.resolve("records.json").writeText("""{"version":1,"days":[$days]}""")
        context.getSharedPreferences("tokipin.settings", Context.MODE_PRIVATE).edit().putBoolean("launched", true).commit()
        TestLocale.set("ko")
        UsageSharing(context).markPresented()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("calendarPane") or hasTestTag("level_RECORDS")).fetchSemanticsNodes().isNotEmpty() }
        assumeTrue("needs a window with two panes", compose.onAllNodes(hasTestTag("calendarPane")).fetchSemanticsNodes().isNotEmpty())
    }

    @After fun close() {
        scenario?.close()
        com.tokipin.app.rules.AppClock.override = null
    }

    private fun JsonObject.s(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.sOrNull(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    private fun level(name: String) = Level.fromSpec(name)

    @Test fun everyNavigationCaseMatchesOnTheRealViewModel() {
        assertTrue(cases.size == 20)
        val failures = mutableListOf<String>()
        for (case in cases) {
            val id = case.s("id")
            val g = case["given"]!!.jsonObject
            val e = case["expected"]!!.jsonObject
            val st = g["state"]!!.jsonObject
            val a = g["action"]!!.jsonObject
            val haptics = g["hapticsEnabled"]?.jsonPrimitive?.booleanOrNull ?: true
            var actual = ""
            scenario!!.onActivity { activity ->
                val vm: AppViewModel = activity.vm
                assertTrue("$id: not in two panes", vm.twoPane)
                vm.setNavigationStateForTest(level(st.s("level")), level(st.s("dayReturnLevel")), LocalDate.parse(st.s("selectedDay")), st.sOrNull("recordedMonth")?.let(YearMonth::parse))
                val changed: Boolean = when (a.s("type")) {
                    "tapDate" -> vm.openDay(LocalDate.parse(a.s("date")))
                    "weekRailTap" -> vm.selectDay(LocalDate.parse(a.s("date")))
                    "daySwipe" -> vm.selectDay(vm.selectedDate.plusDays(a["days"]!!.jsonPrimitive.long))
                    "viewMenu" -> vm.changeLevel(level(a.s("level")))
                    "tapMonth" -> vm.openMonth(YearMonth.parse(a.s("month")))
                    "recordedMonthCommit" -> HapticRules.ticks(vm.recorded { tap(YearMonth.parse(a.s("month"))) }, true) == 1
                    "today" -> { com.tokipin.app.rules.AppClock.override = LocalDate.parse(a.s("today")); vm.goToday(); false }
                    "pinch" -> {
                        val next = PinchRules.decide(vm.calendarLevel, a["scaleAtRelease"]!!.jsonPrimitive.float)
                        if (a.s("pane") != "calendar") false
                        else vm.pinchTo(next, a.sOrNull("anchorDay")?.let(LocalDate::parse), null)
                    }
                    else -> error("action ${a.s("type")}")
                }
                val ticks = if (changed && haptics) 1 else 0
                val got = mapOf(
                    "level" to vm.level.specName, "dayReturnLevel" to (if (vm.level == Level.DAILY) vm.calendarLevel.specName else null),
                    "selectedDay" to vm.selectedDate.toString(), "calendarLevel" to vm.calendarLevel.specName, "appTicks" to ticks.toString(),
                    "recordedMonth" to vm.recordedMonth.toString(),
                )
                val want = mapOf(
                    "level" to e.s("level"), "dayReturnLevel" to (if (e.s("level") == "day") e.s("dayReturnLevel") else null),
                    "selectedDay" to e.s("selectedDay"), "calendarLevel" to e.s("calendarLevel"), "appTicks" to e["appTicks"]!!.jsonPrimitive.int.toString(),
                    "recordedMonth" to (e.sOrNull("recordedMonth") ?: vm.recordedMonth.toString()),
                )
                if (got != want) actual = "$id\n  expected $want\n  actual   $got"
            }
            com.tokipin.app.rules.AppClock.override = LocalDate.of(2026, 11, 28)
            if (actual.isNotEmpty()) failures += actual
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
