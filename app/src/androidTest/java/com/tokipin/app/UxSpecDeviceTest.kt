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
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
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
            InstrumentationRegistry.getInstrumentation().context.assets.open("Tokipin-Native-UX-v0.2.json").reader().readText(),
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

    private fun launch(haptics: Boolean = true, data: String = records) {
        File(context.filesDir, "tokipin").apply { deleteRecursively(); mkdirs() }.resolve("records.json").writeText(data)
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

    /** The vertical list on screen (not the month rail). */
    private val list = hasScrollAction() and !hasAnyAncestor(hasTestTag("monthRail"))

    private fun scrollTo(tag: String) {
        compose.onNode(list).performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
        // Bring it clear of the floating 오늘/+ bar so touches land on the calendar.
        val root = bounds(compose.onRoot())
        val y = bounds(compose.onNodeWithTag(tag)).center.y
        if (y > root.height * 0.55f) {
            compose.onNode(list).performTouchInput {
                swipeUp(startY = centerY, endY = centerY - (y - root.height * 0.45f), durationMillis = 600)
            }
            compose.waitForIdle()
        }
    }

    /** 기록한 날 shows one month per page: go to the card's month on the rail first. */
    private fun showRecordedMonth(month: String) {
        if (!exists(compose.onNode(hasTestTag("rail_$month") and selected))) {
            compose.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("monthRail"))).performScrollToNode(hasTestTag("rail_$month"))
            compose.onNodeWithTag("rail_$month").performClick()
            compose.waitForIdle()
            Thread.sleep(500)
            compose.waitForIdle()
        }
    }

    private fun openDayFromRecords(date: String) {
        showRecordedMonth(date.substring(0, 7))
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
        compose.onNode(list).performScrollToNode(hasTestTag("monthLabel_2026-10"))
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


    // region 기록한 날 v0.2

    /** Nov 2026 with cards, an empty December, January 2027, and a long October for reading. */
    private val monthsData: String by lazy {
        val days = mutableListOf<String>()
        days += """{"date":"2026-11-25","updatedAt":"2026-11-25T10:00:00Z","items":[${item("word", "살구잼", "n1")}]}"""
        days += """{"date":"2026-11-26","updatedAt":"2026-11-26T10:00:00Z","items":[${item("word", "귤", "n2")}]}"""
        days += """{"date":"2027-01-03","updatedAt":"2027-01-03T10:00:00Z","items":[${item("word", "새해", "j1")}]}"""
        for (d in 1..28) {
            val date = "2026-10-%02d".format(d)
            days += """{"date":"$date","updatedAt":"${date}T10:00:00Z","items":[${item("word", "시월 $d", "o$d")},${item("place", "공원", "op$d")}]}"""
        }
        """{"version":1,"days":[${days.joinToString(",")}]}"""
    }

    private fun header(year: Int) = exists(compose.onNode(hasText(year.toString()) and !hasAnyAncestor(hasTestTag("monthRail"))))
    private fun railSelected(month: String) = exists(compose.onNode(hasTestTag("rail_$month") and selected))

    @Test fun bodySwipesOneMonthThroughAnEmptyDecemberIntoTheNextYear() {
        launch(data = monthsData)
        showRecordedMonth("2026-11")
        val w = bounds(compose.onNodeWithTag("recordsBody")).width / density
        var before = ticks()
        drag("recordsBody", -0.23f * w, 0f, 900, slow = true)
        assertTrue("December", railSelected("2026-12"))
        compose.onNodeWithTag("monthEmpty").assertExists()
        assertTrue(header(2026))
        assertEquals(before + 1, ticks())
        // Half way to January and back: cancelled, still 2026, no tick.
        before = ticks()
        compose.onNodeWithTag("recordsBody").performTouchInput {
            val start = Offset(width * 0.7f, height * 0.3f)
            down(start)
            repeat(10) { i -> moveTo(start + Offset(-0.5f * width * (i + 1) / 10, 0f), 40) }
            repeat(10) { i -> moveTo(start + Offset(-0.5f * width + 0.48f * width * (i + 1) / 10, 0f), 40) }
            repeat(6) { moveBy(Offset.Zero, 60) }
            up()
        }
        compose.waitForIdle(); Thread.sleep(400); compose.waitForIdle()
        assertTrue(railSelected("2026-12"))
        assertTrue(header(2026))
        assertEquals(before, ticks())
        // All the way: January 2027, header 2027, one tick.
        drag("recordsBody", -0.4f * w, 0f, 900, slow = true)
        assertTrue(railSelected("2027-01"))
        assertTrue(header(2027))
        assertEquals(before + 1, ticks())
        // A long fling still moves one month only.
        drag("recordsBody", 0.9f * w, 0f, 120, slow = false)
        assertTrue("one page even for a long fling", railSelected("2026-12"))
        assertEquals(before + 2, ticks())
        assertEquals("selected day untouched", true, exists(compose.onNode(hasTestTag("rail_2026-12"))))
    }

    @Test fun railTapAndFlingCommitOnceEach() {
        launch(data = monthsData)
        showRecordedMonth("2026-11")
        var before = ticks()
        compose.onNodeWithTag("rail_2027-01").performClick()
        compose.waitForIdle(); Thread.sleep(500)
        assertTrue(railSelected("2027-01"))
        assertEquals(before + 1, ticks())
        // Re-selecting the centre month is silent.
        compose.onNodeWithTag("rail_2027-01").performClick()
        compose.waitForIdle()
        assertEquals(before + 1, ticks())
        // A fast fling to the left passes several months and lands on one: one tick in total.
        before = ticks()
        compose.onNodeWithTag("monthRail").performTouchInput { swipeLeft(startX = width * 0.8f, endX = width * 0.2f, durationMillis = 80) }
        compose.waitForIdle(); Thread.sleep(1200); compose.waitForIdle()
        assertEquals(before + 1, ticks())
        assertTrue("moved forward", !railSelected("2027-01"))
    }

    @Test fun closingADayReturnsToTheSameMonthCardAndOffset() {
        launch(data = monthsData)
        showRecordedMonth("2026-10")
        compose.onNodeWithTag("monthPage_2026-10").performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.3f, durationMillis = 700) }
        compose.waitForIdle(); Thread.sleep(300)
        val card = "card_2026-10-12"
        scrollTo(card)
        val top = bounds(compose.onNodeWithTag(card)).top
        compose.onNodeWithTag(card).performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        back()
        compose.waitUntil(3_000) { exists(level("RECORDS")) }
        compose.waitForIdle(); Thread.sleep(300)
        assertTrue(railSelected("2026-10"))
        assertEquals(top, bounds(compose.onNodeWithTag(card)).top, 3f)
    }

    @Test fun verticalReadingToTheEndKeepsTheMonth() {
        launch(data = monthsData)
        showRecordedMonth("2026-10")
        val before = ticks()
        repeat(4) {
            compose.onNodeWithTag("monthPage_2026-10").performTouchInput { swipeUp(startY = height * 0.85f, endY = height * 0.15f, durationMillis = 250) }
        }
        compose.waitForIdle(); Thread.sleep(500)
        assertTrue(railSelected("2026-10"))
        assertEquals(before, ticks())
    }

    @Test fun todayGoesToTodaysMonthSilently() {
        launch(data = monthsData)
        showRecordedMonth("2026-10")
        val before = ticks()
        compose.onNodeWithText("오늘").performClick()
        compose.waitForIdle(); Thread.sleep(400)
        assertTrue(railSelected(java.time.YearMonth.now().toString()))
        assertEquals(before, ticks())
    }

    @Test fun aSecondFingerTurnsAMonthDragIntoAPinch() {
        launch(data = monthsData)
        showRecordedMonth("2026-11")
        val card = bounds(compose.onNodeWithTag("card_2026-11-25")).center
        val before = ticks()
        val half = 70f * density
        compose.onRoot().performTouchInput {
            down(0, card)
            repeat(4) { updatePointerTo(0, card + Offset(-12f * density * (it + 1), 0f)); move(30) } // body drag starts
            down(1, card + Offset(half, 0f))
            ramp(1f, 1.3f).forEach { sc ->
                updatePointerTo(0, card - Offset(half * sc, 0f)); updatePointerTo(1, card + Offset(half * sc, 0f)); move(30)
            }
            up(1); move(60); up(0)
        }
        compose.waitForIdle(); Thread.sleep(500); compose.waitForIdle()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        assertEquals("one tick for the level, none for a month", before + 1, ticks())
        assertTrue(railDaySelected("2026.11.25(수)"))
    }

    @Test fun railMonthsReadTheirYearForTalkBack() {
        launch(data = monthsData)
        showRecordedMonth("2026-12")
        compose.onNode(hasContentDescription("2027년 1월") and hasTestTag("rail_2027-01")).assertExists()
        assertTrue(exists(compose.onNode(hasContentDescription("2026년 12월") and selected)))
    }

    // endregion


    // region 일간 읽기·편집

    private val editData: String by lazy {
        val days = listOf(
            """{"date":"2026-10-01","updatedAt":"2026-10-01T10:00:00Z","items":[${item("word", "산책", "a")},${item("place", "공원", "b")},${item("emoji", "🌿", "c")},${item("time", "16:20", "d")},${item("word", "산책", "e")}]}""",
            """{"date":"2026-11-25","updatedAt":"2026-11-25T10:00:00Z","items":[${item("word", "살구잼", "f")},${item("place", "연남동", "g")}]}""",
            """{"date":"2026-11-26","updatedAt":"2026-11-26T10:00:00Z","items":[${item("word", "귤", "h")}]}""",
        )
        """{"version":1,"days":[${days.joinToString(",")}]}"""
    }

    private fun savedValues(date: String) = dayItems(date)

    /** Runs a TalkBack custom action ("위로 이동", "아래로 이동", "삭제") on a node. */
    private fun customAction(tag: String, label: String) {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        val action = node.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
    }
    private fun noFocusedField() = compose.onAllNodes(androidx.compose.ui.test.isFocused() and androidx.compose.ui.test.hasSetTextAction())
        .fetchSemanticsNodes().isEmpty()

    private fun openDayOnRail(fromCard: String, day: String) {
        openDayFromRecords(fromCard)
        compose.onNode(hasContentDescription(day) and hasAnyAncestor(hasTestTag("weekRail"))).performClick()
        compose.waitForIdle()
    }

    private fun centerFraction(tag: String, area: String): Float {
        val a = bounds(compose.onNodeWithTag(area))
        val c = bounds(compose.onNodeWithTag(tag)).center.y
        return (c - a.top) / a.height
    }

    @Test fun emptyDayPlusSavesTheFirstFragmentAndPencilReopensItWithoutKeyboard() {
        launch(data = editData)
        openDayOnRail("2026-11-25", "2026.11.24(화)")
        val empty = compose.onNodeWithTag("dayEmpty_2026-11-24")
        empty.assertExists()
        assertEquals("이날 남긴 기록이 없어요", empty.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single())
        // Picture centre at about 30% of the space between the date band and the bottom buttons.
        assertTrue(centerFraction("dayEmpty_2026-11-24", "dayBody") in 0.18f..0.36f)
        compose.onNodeWithTag("addRecord").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("단어 입력").performTextInput("산책")
        compose.onNodeWithContentDescription("기록 저장").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        assertTrue(railDaySelected("2026.11.24(화)"))
        compose.onNodeWithTag("dayEmpty_2026-11-24").assertDoesNotExist()
        compose.onNodeWithText("산책").assertExists()
        compose.onNodeWithTag("editDay").performClick()
        compose.waitForIdle(); Thread.sleep(400)
        assertTrue("pencil opens with the keyboard closed", noFocusedField())
        compose.onNodeWithText("산책").performClick()
        compose.waitForIdle(); Thread.sleep(300)
        assertTrue("tapping a fragment edits it", !noFocusedField())
    }

    @Test fun deletingTheLastFragmentAndSavingShowsTheEmptyDayAgain() {
        launch(data = editData)
        openDayFromRecords("2026-11-26")
        compose.onNodeWithTag("editDay").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("editRow_h").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("deleteMenu").performClick()
        compose.onNodeWithContentDescription("기록 저장").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        compose.onNodeWithTag("dayEmpty_2026-11-26").assertExists()
        compose.onNodeWithTag("addRecord").assertExists()
        assertEquals(emptyList<String>(), savedValues("2026-11-26"))
    }

    @Test fun longPressInPlaceOpensDeleteButDoesNotDeleteUntilChosen() {
        launch(data = editData)
        openDayFromRecords("2026-11-25")
        compose.onNodeWithTag("editDay").performClick()
        compose.onNodeWithTag("editRow_f").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("deleteMenu").assertExists()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("editRow_f").assertExists()
    }

    @Test fun dragReordersAcrossKindsAndTheOrderSurvivesARelaunch() {
        launch(data = editData)
        openDayFromRecords("2026-10-01")
        compose.onNodeWithTag("editDay").performClick()
        compose.waitForIdle()
        val first = bounds(compose.onNodeWithTag("editRow_a"))
        val last = bounds(compose.onNodeWithTag("editRow_e"))
        val before = ticks()
        compose.onNodeWithTag("editRow_a").performTouchInput {
            down(center)
            advanceEventTime(800) // long press: pick up
            val dy = last.center.y - first.center.y + 20f
            repeat(20) { moveBy(androidx.compose.ui.geometry.Offset(0f, dy / 20), 30) }
            up()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("deleteMenu").assertDoesNotExist()
        assertEquals("one tick when picked up", before + 1, ticks())
        compose.onNodeWithContentDescription("기록 저장").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        assertEquals(listOf("공원", "🌿", "16:20", "산책", "산책"), savedValues("2026-10-01"))
        scenario!!.close()
        launch(data = File(context.filesDir, "tokipin/records.json").readText())
        openDayFromRecords("2026-10-01")
        val ids = compose.onAllNodes(hasTestTag("dayItem_b") or hasTestTag("dayItem_a")).fetchSemanticsNodes()
        assertEquals(2, ids.size)
        assertTrue("공원 now first", bounds(compose.onNodeWithTag("dayItem_b")).top < bounds(compose.onNodeWithTag("dayItem_a")).top)
    }

    @Test fun talkBackActionsMoveAndDelete() {
        launch(data = editData)
        openDayFromRecords("2026-11-25")
        compose.onNodeWithTag("editDay").performClick()
        compose.waitForIdle()
        customAction("editRow_f", "아래로 이동")
        compose.waitForIdle()
        assertTrue(bounds(compose.onNodeWithTag("editRow_g")).top < bounds(compose.onNodeWithTag("editRow_f")).top)
        customAction("editRow_f", "삭제")
        compose.waitForIdle()
        compose.onNodeWithTag("editRow_f").assertDoesNotExist()
        compose.onNodeWithContentDescription("기록 저장").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        assertEquals(listOf("연남동"), savedValues("2026-11-25"))
    }

    @Test fun undoBringsBackOnlyTheDeletedFragment() {
        launch(data = editData)
        openDayFromRecords("2026-10-01")
        compose.onNodeWithTag("editDay").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("editRow_b").performTouchInput { longClick() }
        compose.onNodeWithTag("deleteMenu").performClick()
        // Reorder after deleting: move the last 산책 (e) to the top.
        customAction("editRow_e", "위로 이동")
        customAction("editRow_e", "위로 이동")
        compose.onNodeWithText("실행 취소").performClick()
        compose.onNodeWithContentDescription("기록 저장").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        // 공원 is back at its old place; the later move of e is kept.
        assertEquals(listOf("산책", "공원", "산책", "🌿", "16:20"), savedValues("2026-10-01"))
    }

    @Test fun aDraftLeftOnOneDayStaysWithThatDay() {
        launch(data = editData)
        openDayFromRecords("2026-11-25")
        compose.onNodeWithTag("editDay").performClick()
        compose.onNodeWithContentDescription("단어").performClick()
        compose.onNodeWithContentDescription("단어 입력").performTextInput("초안A")
        back()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        compose.onNode(hasContentDescription("2026.11.24(화)") and hasAnyAncestor(hasTestTag("weekRail"))).performClick()
        compose.onNodeWithTag("addRecord").performClick()
        compose.onNodeWithContentDescription("단어 입력").performTextInput("초안B")
        back()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        compose.onNode(hasContentDescription("2026.11.25(수)") and hasAnyAncestor(hasTestTag("weekRail"))).performClick()
        compose.onNodeWithTag("editDay").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("초안A").assertExists()
        compose.onNodeWithText("초안B").assertDoesNotExist()
        assertEquals("saved records untouched", listOf("살구잼", "연남동"), savedValues("2026-11-25"))
    }

    @Test fun emptyMonthPictureSitsInThePageAndTheCalendarStillShowsEveryDay() {
        launch(data = editData)
        showRecordedMonth("2026-12")
        compose.onNodeWithTag("monthEmpty").assertExists()
        assertEquals("12월에 남긴 기록이 없어요", compose.onNodeWithTag("monthEmpty").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single())
        assertTrue(centerFraction("monthEmpty", "recordsBody") in 0.18f..0.36f)
        compose.onNodeWithText("이달에 남긴 기록이 없어요.").assertDoesNotExist()
        menu("MONTH")
        scrollTo("day_2026-12-15")
        compose.onNodeWithTag("day_2026-12-15").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        compose.onNodeWithTag("dayEmpty_2026-12-15").assertExists()
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
        compose.onNodeWithContentDescription("이날 기록 편집").performClick()
        compose.onNodeWithContentDescription("시간").performClick()
        compose.onNodeWithContentDescription("기록 저장").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        assertEquals(listOf("귤"), dayItems("2026-11-26"))
    }

    @Test fun plusThenCheckAddsTheTimeOnce() {
        launch()
        openDayFromRecords("2026-11-26")
        compose.onNodeWithContentDescription("이날 기록 편집").performClick()
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
