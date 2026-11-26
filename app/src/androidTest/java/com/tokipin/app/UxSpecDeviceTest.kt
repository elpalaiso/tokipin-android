package com.tokipin.app

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tokipin.app.ui.Haptics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * UX v0.1 cases that need the real screen and real touch input: calendar state and return position,
 * day/week swipes with 1:1 drags and flicks, date-anchored pinches, and app haptic counts.
 * Thresholds come from the same shared JSON the unit tests and the iOS app read.
 */
@RunWith(AndroidJUnit4::class)
class UxSpecDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val density get() = context.resources.displayMetrics.density

    private val spec by lazy {
        Json.parseToJsonElement(
            InstrumentationRegistry.getInstrumentation().context.assets.open("Tokipin-Native-UX-v0.1.json").reader().readText(),
        ).jsonObject
    }
    private val swipeSpec get() = spec["swipe"]!!.jsonObject
    private fun case(id: String) = spec["tests"]!!.jsonArray.map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content == id }

    private fun item(kind: String, value: String, id: String) = """{"id":"$id","kind":"$kind","value":"$value"}"""
    private val records = """
        {"version":1,"days":[
          {"date":"2024-03-01","updatedAt":"2024-03-01T10:00:00Z","items":[${item("word", "봄", "y")}]},
          {"date":"2026-10-01","updatedAt":"2026-10-01T10:00:00Z","items":[
            ${item("word", "산책", "a")},${item("place", "공원", "b")},${item("emoji", "🌿", "c")},${item("time", "16:20", "d")},${item("word", "바람", "e")}]},
          {"date":"2026-11-25","updatedAt":"2026-11-25T10:00:00Z","items":[${item("word", "살구잼", "f")},${item("place", "연남동", "g")}]},
          {"date":"2026-11-26","updatedAt":"2026-11-26T10:00:00Z","items":[${item("word", "귤", "h")}]},
          {"date":"2026-12-25","updatedAt":"2026-12-25T10:00:00Z","items":[${item("word", "크리스마스", "i")}]},
          {"date":"2027-01-05","updatedAt":"2027-01-05T10:00:00Z","items":[${item("word", "새해", "j")}]}
        ]}
    """.trimIndent()

    private fun launch(haptics: Boolean = true) {
        File(context.filesDir, "tokipin").apply { deleteRecursively(); mkdirs() }.resolve("records.json").writeText(records)
        context.getSharedPreferences("tokipin.settings", Context.MODE_PRIVATE).edit()
            .putBoolean("launched", true).putBoolean("haptics", haptics).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(5_000) { exists(level("RECORDS")) }
    }

    @After fun close() { scenario?.close() }

    private fun level(name: String) = compose.onNodeWithTag("level_$name")
    private fun exists(node: SemanticsNodeInteraction) = runCatching { node.fetchSemanticsNode() }.isSuccess
    private fun bounds(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().boundsInRoot
    private fun ticks() = Haptics.totalRequested
    private val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    private fun menu(levelName: String) {
        compose.onNodeWithContentDescription("탐색 보기").performClick()
        compose.onNodeWithTag("menu_$levelName").performClick()
        compose.waitUntil(3_000) { exists(level(levelName)) }
    }

    private fun scrollTo(tag: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
        // Bring it clear of the floating 오늘/+ bar so touches land on the calendar.
        val root = bounds(compose.onRoot())
        val y = bounds(compose.onNodeWithTag(tag)).center.y
        if (y > root.height * 0.55f) {
            compose.onNode(hasScrollAction()).performTouchInput {
                swipeUp(startY = centerY, endY = centerY - (y - root.height * 0.45f), durationMillis = 600)
            }
            compose.waitForIdle()
        }
    }

    private fun openDayFromRecords(date: String) {
        scrollTo("card_$date")
        compose.onNodeWithTag("card_$date").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
    }

    private fun back() {
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun railDaySelected(label: String) =
        exists(compose.onNode(hasContentDescription(label) and selected and hasAnyAncestor(hasTestTag("weekRail"))))

    /** A one-finger drag in [tag]; [slow] holds still before lifting so the release speed is ~0. */
    private fun drag(tag: String, dxDp: Float, dyDp: Float, durationMs: Long, slow: Boolean) {
        val node = compose.onNodeWithTag(tag)
        val box = bounds(node)
        node.performTouchInput {
            val start = Offset(box.width * 0.7f, minOf(box.height * 0.3f, 60f * density))
            down(start)
            val steps = 12
            repeat(steps) { i ->
                moveTo(start + Offset(dxDp * density * (i + 1) / steps, dyDp * density * (i + 1) / steps), durationMs / steps)
            }
            if (slow) repeat(6) { moveBy(Offset.Zero, 60) }
            up()
        }
        compose.waitForIdle()
        Thread.sleep(400)
        compose.waitForIdle()
    }

    // region calendar

    @Test fun monthNumberSitsAboveTheFirstAndCellsShowThreePlusRest() {
        launch()
        val case = case("calendar.monthOverflow")["expected"]!!.jsonObject
        menu("MONTH")
        compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("monthLabel_2026-10"))
        compose.waitForIdle()
        val label = bounds(compose.onNodeWithTag("monthLabel_2026-10"))
        val first = bounds(compose.onNodeWithTag("day_2026-10-01"))
        assertTrue("month number over the column of the 1st: label=$label first=$first", label.center.x in first.left..first.right)
        val inCell = { text: String -> hasText(text) and hasAnyAncestor(hasTestTag("day_2026-10-01")) }
        case["shown"]!!.jsonArray.forEach { compose.onNode(inCell(it.jsonPrimitive.content), useUnmergedTree = true).assertExists() }
        compose.onNode(inCell(case["overflow"]!!.jsonPrimitive.content), useUnmergedTree = true).assertExists()
        compose.onNode(inCell("바람"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun scrollingAcrossTheYearKeepsTheSelectedDayAndIsSilent() {
        launch()
        menu("MONTH")
        scrollTo("day_2026-12-25")
        compose.onNodeWithTag("day_2026-12-25").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        back()
        compose.waitUntil(3_000) { exists(level("MONTH")) }
        val before = ticks()
        scrollTo("month_2027-03") // scroll through December → January
        compose.waitUntil(3_000) { exists(compose.onNodeWithText("2027")) }
        assertEquals("scrolling is silent", before, ticks())
        scrollTo("day_2026-12-25")
        assertTrue(exists(compose.onNode(hasTestTag("day_2026-12-25") and selected)))
    }

    @Test fun closingADayReturnsToTheSameMonthPosition() {
        launch()
        menu("MONTH")
        scrollTo("day_2026-10-01")
        val top = bounds(compose.onNodeWithTag("month_2026-10")).top
        compose.onNodeWithTag("day_2026-10-01").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        back()
        compose.waitUntil(3_000) { exists(level("MONTH")) }
        compose.waitForIdle()
        assertEquals(top, bounds(compose.onNodeWithTag("month_2026-10")).top, 2f)
        assertTrue(exists(compose.onNode(hasTestTag("day_2026-10-01") and selected)))
    }

    @Test fun todayInTheYearViewScrollsBackWithoutHaptics() {
        launch()
        menu("YEAR")
        scrollTo("year_2024")
        val before = ticks()
        compose.onNodeWithText("오늘").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("year_${java.time.LocalDate.now().year}").assertIsDisplayed()
        level("YEAR").assertExists()
        assertEquals(before, ticks())
    }

    // endregion

    // region swipe

    @Test fun daySwipeCommitsAt22PercentAndSettlesBackBelow() {
        launch()
        openDayFromRecords("2026-11-25")
        val w = bounds(compose.onNodeWithTag("dayBody")).width / density
        val commit = swipeSpec["distanceCommitFractionOfW"]!!.jsonPrimitive.content.toFloat()
        val before = ticks()
        drag("dayBody", -(commit - 0.01f) * w, 0f, 900, slow = true)
        assertTrue("21%: stays on the 25th", railDaySelected("2026.11.25(수)"))
        assertEquals(before, ticks())
        drag("dayBody", -(commit + 0.01f) * w, 0f, 900, slow = true)
        assertTrue("23%: next day", railDaySelected("2026.11.26(목)"))
        assertEquals(before + 1, ticks())
        drag("dayBody", (commit + 0.03f) * w, 0f, 900, slow = true)
        assertTrue("back to the 25th", railDaySelected("2026.11.25(수)"))
        assertEquals(before + 2, ticks())
    }

    @Test fun diagonalReadingDoesNotChangeTheDay() {
        launch()
        openDayFromRecords("2026-11-25")
        val g = case("swipe.diagonal.verticalWins")["given"]!!.jsonObject
        val before = ticks()
        drag("dayBody", g["dx"]!!.jsonPrimitive.content.toFloat(), g["dy"]!!.jsonPrimitive.content.toFloat(), 220, slow = false)
        assertTrue(railDaySelected("2026.11.25(수)"))
        assertEquals(before, ticks())
    }

    @Test fun shortFastFlickOnTheRailMovesOneWeek() {
        launch()
        openDayFromRecords("2026-11-25")
        val w = bounds(compose.onNodeWithTag("weekRail")).width / density
        val before = ticks()
        drag("weekRail", -0.12f * w, 0f, 60, slow = false)
        assertTrue("same weekday, next week", railDaySelected("2026.12.02(수)"))
        assertEquals(before + 1, ticks())
    }

    @Test fun hapticsOffMeansNoAppTicks() {
        launch(haptics = false)
        openDayFromRecords("2026-11-25")
        val w = bounds(compose.onNodeWithTag("dayBody")).width / density
        val before = ticks()
        drag("dayBody", -0.3f * w, 0f, 600, slow = true)
        assertTrue(railDaySelected("2026.11.26(목)"))
        assertEquals(before, ticks())
    }

    // endregion

    // region pinch

    /** Two fingers around [center]; the spacing goes through [scales] while the midpoint drifts by [drift]. */
    private fun pinch(center: Offset, scales: List<Float>, drift: Offset = Offset.Zero, liftOneFirst: Boolean = false) {
        val half = 70f * density
        compose.onRoot().performTouchInput {
            down(0, center - Offset(half, 0f))
            down(1, center + Offset(half, 0f))
            scales.forEachIndexed { i, s ->
                val c = center + drift * ((i + 1f) / scales.size)
                updatePointerTo(0, c - Offset(half * s, 0f))
                updatePointerTo(1, c + Offset(half * s, 0f))
                move(30)
            }
            if (liftOneFirst) {
                up(1)
                move(80)
            }
            up(if (liftOneFirst) 0 else 1)
            if (!liftOneFirst) up(0)
        }
        compose.waitForIdle()
        Thread.sleep(400)
        compose.waitForIdle()
    }

    private fun ramp(from: Float, to: Float, steps: Int = 8) = (1..steps).map { from + (to - from) * it / steps }

    @Test fun pinchZoomsAroundTheFirstDateEvenIfTheFingersDrift() {
        launch()
        menu("MONTH")
        scrollTo("day_2026-11-25")
        val start = bounds(compose.onNodeWithTag("day_2026-11-25")).center
        val next = bounds(compose.onNodeWithTag("day_2026-11-26")).center
        val before = ticks()
        pinch(start, ramp(1f, 1.3f), drift = next - start)
        compose.waitUntil(3_000) { exists(level("RECORDS")) }
        assertEquals("one level, one tick", before + 1, ticks())
        menu("DAILY")
        assertTrue("anchored on the 25th", railDaySelected("2026.11.25(수)"))
    }

    @Test fun pinchReturnedToNeutralCancels() {
        launch()
        menu("MONTH")
        scrollTo("day_2026-11-25")
        val c = bounds(compose.onNodeWithTag("day_2026-11-25")).center
        val before = ticks()
        pinch(c, ramp(1f, 1.26f, 5) + ramp(1.26f, 1.05f, 5))
        level("MONTH").assertExists()
        assertEquals(before, ticks())
    }

    @Test fun theLastFingerOfAPinchIsNotATap() {
        launch()
        menu("MONTH")
        scrollTo("day_2026-11-25")
        val c = bounds(compose.onNodeWithTag("day_2026-11-25")).center
        val before = ticks()
        pinch(c, ramp(1f, 1.02f, 3), liftOneFirst = true)
        level("MONTH").assertExists()
        assertEquals(before, ticks())
    }

    // endregion

    // region time

    @Test fun timesFollowTheDevice12Or24HourSetting() {
        launch()
        openDayFromRecords("2026-10-01")
        val is24 = android.text.format.DateFormat.is24HourFormat(context)
        compose.onNodeWithText(if (is24) "16:20" else "오후 4:20").assertExists()
    }

    private fun dayItems(date: String): List<String> {
        val text = File(context.filesDir, "tokipin/records.json").readText()
        val day = Json.parseToJsonElement(text).jsonObject["days"]!!.jsonArray.map { it.jsonObject }
            .firstOrNull { it["date"]!!.jsonPrimitive.content == date } ?: return emptyList()
        return day["items"]!!.jsonArray.map { it.jsonObject["value"]!!.jsonPrimitive.content }
    }

    @Test fun openingTheTimeToolAloneAddsNothing() {
        launch()
        openDayFromRecords("2026-11-26")
        compose.onNodeWithContentDescription("기록 추가").performClick()
        compose.onNodeWithContentDescription("시간").performClick()
        compose.onNodeWithContentDescription("기록 저장").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        assertEquals(listOf("귤"), dayItems("2026-11-26"))
    }

    @Test fun plusThenCheckAddsTheTimeOnce() {
        launch()
        openDayFromRecords("2026-11-26")
        compose.onNodeWithContentDescription("기록 추가").performClick()
        compose.onNodeWithContentDescription("시간").performClick()
        compose.onNodeWithContentDescription("시간 추가").performClick()
        compose.onNodeWithContentDescription("기록 저장").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        val items = dayItems("2026-11-26")
        assertEquals(2, items.size)
        assertTrue("canonical HH:mm", Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(items[1]))
    }

    // endregion

    @Test fun viewMenuReachesEveryLevelForTheSameDate() {
        launch()
        openDayFromRecords("2026-11-25")
        menu("YEAR")
        assertTrue(exists(compose.onNode(hasTestTag("mini_2026-11") and selected)))
        menu("DAILY")
        assertTrue(railDaySelected("2026.11.25(수)"))
        compose.onNode(hasTestTag("weekRail")).assertExists()
    }

    @Suppress("unused")
    private fun near(a: Float, b: Float) = abs(a - b) < 2f
}
