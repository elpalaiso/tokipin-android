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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performScrollToIndex
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
            InstrumentationRegistry.getInstrumentation().context.assets.open("Tokipin-Native-UX-v0.3.json").reader().readText(),
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

    private fun launch(haptics: Boolean = true, data: String = records, today: java.time.LocalDate = java.time.LocalDate.of(2027, 3, 15)) {
        // Earlier tests use records up to 2027-01, so they run on 2027-03-15; range tests pin 2026-11-26.
        com.tokipin.app.rules.AppClock.override = today
        File(context.filesDir, "tokipin").apply { deleteRecursively(); mkdirs() }.resolve("records.json").writeText(data)
        context.getSharedPreferences("tokipin.settings", Context.MODE_PRIVATE).edit()
            .putBoolean("launched", true).putBoolean("haptics", haptics).commit()
        TestLocale.set("ko")
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

    /** How TalkBack reads a date in the app language (Korean in these tests). */
    private fun sp(date: String) = com.tokipin.app.ui.DateWords.spoken(java.time.LocalDate.parse(date))

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
        compose.onNodeWithTag("year_${com.tokipin.app.rules.AppClock.today().year}").assertIsDisplayed()
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
        assertTrue("21%: stays on the 25th", railDaySelected(sp("2026-11-25")))
        assertEquals(before, ticks())
        drag("dayBody", -(commit + 0.01f) * w, 0f, 900, slow = true)
        assertTrue("23%: next day", railDaySelected(sp("2026-11-26")))
        assertEquals(before + 1, ticks())
        drag("dayBody", (commit + 0.03f) * w, 0f, 900, slow = true)
        assertTrue("back to the 25th", railDaySelected(sp("2026-11-25")))
        assertEquals(before + 2, ticks())
    }

    @Test fun diagonalReadingDoesNotChangeTheDay() {
        launch()
        openDayFromRecords("2026-11-25")
        val g = case("swipe.diagonal.verticalWins")["given"]!!.jsonObject
        val before = ticks()
        drag("dayBody", g["dx"]!!.jsonPrimitive.content.toFloat(), g["dy"]!!.jsonPrimitive.content.toFloat(), 220, slow = false)
        assertTrue(railDaySelected(sp("2026-11-25")))
        assertEquals(before, ticks())
    }

    @Test fun shortFastFlickOnTheRailMovesOneWeek() {
        launch()
        openDayFromRecords("2026-11-25")
        val w = bounds(compose.onNodeWithTag("weekRail")).width / density
        val before = ticks()
        drag("weekRail", -0.12f * w, 0f, 60, slow = false)
        assertTrue("same weekday, next week", railDaySelected(sp("2026-12-02")))
        assertEquals(before + 1, ticks())
    }

    @Test fun hapticsOffMeansNoAppTicks() {
        launch(haptics = false)
        openDayFromRecords("2026-11-25")
        val w = bounds(compose.onNodeWithTag("dayBody")).width / density
        val before = ticks()
        drag("dayBody", -0.3f * w, 0f, 600, slow = true)
        assertTrue(railDaySelected(sp("2026-11-26")))
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
        assertTrue("anchored on the 25th", railDaySelected(sp("2026-11-25")))
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

    @Test fun bodySwipeSkipsTheEmptyDecemberAndEndsOnTheEmptyCurrentMonth() {
        // Listed: 2026-10, 2026-11, 2027-01 and this month 2027-03 (empty). December and February are not pages.
        launch(data = monthsData)
        showRecordedMonth("2026-11")
        compose.onNodeWithTag("rail_2026-12").assertDoesNotExist()
        val w = bounds(compose.onNodeWithTag("recordsBody")).width / density
        var before = ticks()
        // Half way to January and back: cancelled, still 2026, no tick.
        compose.onNodeWithTag("recordsBody").performTouchInput {
            val start = Offset(width * 0.7f, height * 0.3f)
            down(start)
            repeat(10) { i -> moveTo(start + Offset(-0.5f * width * (i + 1) / 10, 0f), 40) }
            repeat(10) { i -> moveTo(start + Offset(-0.5f * width + 0.48f * width * (i + 1) / 10, 0f), 40) }
            repeat(6) { moveBy(Offset.Zero, 60) }
            up()
        }
        compose.waitForIdle(); Thread.sleep(400); compose.waitForIdle()
        assertTrue(railSelected("2026-11"))
        assertTrue(header(2026))
        assertEquals(before, ticks())
        // One slot: straight to January 2027, header 2027, one tick.
        drag("recordsBody", -0.23f * w, 0f, 900, slow = true)
        assertTrue("January, not the empty December", railSelected("2027-01"))
        assertTrue(header(2027))
        compose.onNodeWithTag("card_2027-01-03").assertExists()
        assertEquals(before + 1, ticks())
        // Next slot: this month, empty, with the rabbit card.
        drag("recordsBody", -0.3f * w, 0f, 900, slow = true)
        assertTrue(railSelected("2027-03"))
        compose.onNodeWithTag("monthEmpty").assertExists()
        assertEquals(before + 2, ticks())
        // Nothing after this month: no move, no tick.
        drag("recordsBody", -0.4f * w, 0f, 600, slow = true)
        assertTrue(railSelected("2027-03"))
        assertEquals(before + 2, ticks())
        // A long fling back still moves one slot only.
        drag("recordsBody", 0.9f * w, 0f, 120, slow = false)
        assertTrue("one page even for a long fling", railSelected("2027-01"))
        assertEquals(before + 3, ticks())
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
        assertTrue(railSelected(com.tokipin.app.rules.AppClock.thisMonth().toString()))
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
        assertTrue(railDaySelected(sp("2026-11-25")))
    }

    @Test fun railMonthsReadTheirYearForTalkBack() {
        launch(data = monthsData)
        showRecordedMonth("2026-11")
        compose.onNode(hasContentDescription("2027년 1월") and hasTestTag("rail_2027-01")).assertExists()
        assertTrue(exists(compose.onNode(hasContentDescription("2026년 11월") and selected)))
        // The small 2027 over January is shown, but it is part of January's one TalkBack node.
        compose.onNode(hasTestTag("railHint_2027-01"), useUnmergedTree = true).assertExists()
        val hint = compose.onNode(hasTestTag("railHint_2027-01"), useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("hint is not its own element", !hint.config.contains(SemanticsProperties.Text))
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

    private fun imeVisible(): Boolean {
        var shown = false
        scenario!!.onActivity {
            shown = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        return shown
    }

    /** iOS 806a80e parity: pencil (keyboard closed) → tap the empty row → typing keeps the keyboard. */
    @Test fun tappingTheEmptyRowAfterThePencilKeepsTheKeyboardWhileTyping() {
        val data = """{"version":1,"days":[{"date":"2026-11-20","updatedAt":"2026-11-20T10:00:00Z","items":[
            ${item("word", "산책", "k1")},${item("emoji", "🌿", "k2")}]}]}"""
        launch(data = data, today = rangeToday)
        selectDay("2026-11-20")
        compose.onNodeWithTag("editDay").performClick()
        compose.waitForIdle(); Thread.sleep(500)
        assertTrue("pencil opens with the keyboard closed", noFocusedField() && !imeVisible())
        compose.onNodeWithContentDescription("단어 입력").performClick()
        compose.waitUntil(3_000) { imeVisible() }
        // Type through the real IME connection, one character at a time.
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (c in listOf("b", "a", "m")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("input text $c")).readBytes()
            Thread.sleep(400); compose.waitForIdle()
            assertTrue("keyboard still open after '$c'", imeVisible())
            assertTrue("field still focused after '$c'", !noFocusedField())
        }
        // With the app in Korean the keyboard may compose the keys as Hangul; what matters is that
        // three characters arrived without the keyboard closing.
        scenario!!.onActivity { assertTrue("typed text arrived", it.vm.draft!!.raw.isNotEmpty()) }
    }

    /** Figma 267-1270: "진동" with its explanation, toggling the app's haptics, on every device. */
    @Test fun vibrationRowTogglesHaptics() {
        launch(data = editData)
        compose.onNodeWithContentDescription("탐색 보기").performClick()
        compose.onNodeWithText("설정").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("진동").assertExists()
        compose.onNodeWithText("날짜를 넘기거나 보기를 바꿀 때 가볍게 진동해요.").assertExists()
        compose.onNodeWithTag("hapticsRow").performClick()
        scenario!!.onActivity { assertEquals(false, it.vm.hapticsEnabled) }
        compose.onNodeWithTag("hapticsRow").performClick()
        scenario!!.onActivity { assertEquals(true, it.vm.hapticsEnabled) }
    }

    /** Tokipin에 한마디 (Figma 267-1266): under 기록 내보내기, copy confirms for ~2s, back keeps the sheet. */
    @Test fun feedbackCopiesTheAddressAndBackReturnsToSettings() {
        launch(data = editData)
        compose.onNodeWithContentDescription("탐색 보기").performClick()
        compose.onNodeWithText("설정").performClick()
        compose.waitForIdle()
        val export = bounds(compose.onNodeWithText("기록 내보내기"))
        val row = bounds(compose.onNodeWithTag("feedbackRow"))
        assertTrue("row sits under 기록 내보내기", row.top >= export.bottom)
        compose.onNodeWithTag("feedbackRow").performClick()
        compose.onNodeWithTag("feedbackPage").assertExists()
        compose.onNodeWithText("쓰면서 떠오른 생각이 있나요?\n어떤 이야기든 반가워요.").assertExists()
        compose.onNodeWithTag("feedbackCopy").performClick()
        compose.onNodeWithText("주소를 복사했어요").assertExists()
        var clip: String? = null
        scenario!!.onActivity {
            clip = it.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
        }
        assertEquals("hello@tokipin.com", clip)
        // The confirmation's 2 s runs on the composition clock, which the test drives.
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("주소를 복사했어요").assertExists()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("주소 복사").assertExists()
        compose.onNodeWithTag("feedbackAddress").assertDoesNotExist()
        // System back goes to the sheet's window: it returns to the settings, not out of them.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("feedbackRow").assertExists()
        compose.onNodeWithTag("feedbackRow").performClick()
        compose.onNodeWithContentDescription("설정으로").performClick()
        compose.onNodeWithTag("feedbackRow").assertExists() // the settings sheet, as it was
    }

    @Test fun emptyDayPlusSavesTheFirstFragmentAndPencilReopensItWithoutKeyboard() {
        launch(data = editData)
        openDayOnRail("2026-11-25", sp("2026-11-24"))
        val empty = compose.onNodeWithTag("dayEmpty_2026-11-24")
        empty.assertExists()
        assertEquals("이날 남긴 기록이 없어요.", empty.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single())
        // Picture centre at about 30% of the space between the date band and the bottom buttons.
        assertTrue(centerFraction("dayEmpty_2026-11-24", "dayBody") in 0.18f..0.36f)
        compose.onNodeWithTag("addRecord").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("단어 입력").performTextInput("산책")
        compose.onNodeWithContentDescription("기록 저장").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        assertTrue(railDaySelected(sp("2026-11-24")))
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

    /** iOS e4f5d1e parity: a held fragment never drifts from the finger, also past a taller row. */
    @Test fun aHeldFragmentStaysUnderTheFingerAcrossRowsOfDifferentHeights() {
        val long = "아주 긴 문장을 남겨서 이 줄이 여러 줄로 감기도록 만든 첫 조각입니다 정말 길게 이어지는 문장"
        val data = """{"version":1,"days":[{"date":"2026-11-20","updatedAt":"2026-11-20T10:00:00Z","items":[
            ${item("word", long, "L")},${item("word", "하나", "s1")},${item("place", "둘", "s2")},${item("word", "셋", "s3")},${item("word", "넷", "s4")}]}]}"""
        launch(data = data, today = rangeToday)
        selectDay("2026-11-20")
        compose.onNodeWithTag("editDay").performClick()
        compose.waitForIdle(); Thread.sleep(300)
        val tall = bounds(compose.onNodeWithTag("editRow_L"))
        assertTrue("the first row is taller", tall.height > bounds(compose.onNodeWithTag("editRow_s1")).height * 1.5f)
        fun checkHeld(tag: String, fingerY: Float) {
            compose.waitForIdle()
            val c = bounds(compose.onNodeWithTag(tag)).center.y
            assertEquals("$tag stays under the finger", fingerY, c, 6f * density)
        }
        // Fast drag of the tall row down over several short rows, checked between bursts.
        var finger = tall.center.y
        // Long press is timed by event time within one injection, so pick up with a first small move.
        compose.onNodeWithTag("editRow_L").performTouchInput { down(center); advanceEventTime(800); moveBy(Offset(0f, 2f), 8) }
        finger += 2f
        repeat(4) {
            val step = 45f * density
            compose.onNodeWithTag("editRow_L").performTouchInput { repeat(3) { moveBy(Offset(0f, step / 3), 8) } }
            finger += step
            checkHeld("editRow_L", finger)
        }
        compose.onNodeWithTag("editRow_L").performTouchInput { up() }
        compose.waitForIdle()
        scenario!!.onActivity { assertEquals("L moved down", true, it.vm.draft!!.items.indexOfFirst { e -> e.id == "L" } >= 3) }
        // Fast drag of the last short row up past the tall one to the top.
        val short = bounds(compose.onNodeWithTag("editRow_s4"))
        finger = short.center.y
        compose.onNodeWithTag("editRow_s4").performTouchInput { down(center); advanceEventTime(800); moveBy(Offset(0f, -2f), 8) }
        finger -= 2f
        val travel = short.center.y - bounds(compose.onNodeWithTag("editRow_s1")).top
        repeat(4) {
            compose.onNodeWithTag("editRow_s4").performTouchInput { repeat(3) { moveBy(Offset(0f, -travel / 12), 8) } }
            finger -= travel / 4
            checkHeld("editRow_s4", finger)
        }
        compose.onNodeWithTag("editRow_s4").performTouchInput { up() }
        compose.waitForIdle()
        scenario!!.onActivity { assertEquals("s4 reached the top", "s4", it.vm.draft!!.items.first().id) }
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
        compose.onNode(hasContentDescription(sp("2026-11-24")) and hasAnyAncestor(hasTestTag("weekRail"))).performClick()
        compose.onNodeWithTag("addRecord").performClick()
        compose.onNodeWithContentDescription("단어 입력").performTextInput("초안B")
        back()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        compose.onNode(hasContentDescription(sp("2026-11-25")) and hasAnyAncestor(hasTestTag("weekRail"))).performClick()
        compose.onNodeWithTag("editDay").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("초안A").assertExists()
        compose.onNodeWithText("초안B").assertDoesNotExist()
        assertEquals("saved records untouched", listOf("살구잼", "연남동"), savedValues("2026-11-25"))
    }

    @Test fun emptyMonthPictureSitsInThePageAndTheCalendarStillShowsEveryDay() {
        launch(data = editData)
        showRecordedMonth("2027-03") // this month (pinned 2027-03-15), the only empty page
        compose.onNodeWithTag("monthEmpty").assertExists()
        assertEquals("3월에 남긴 기록이 없어요.", compose.onNodeWithTag("monthEmpty").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single())
        assertTrue(centerFraction("monthEmpty", "recordsBody") in 0.18f..0.36f)
        compose.onNodeWithText("이달에 남긴 기록이 없어요.").assertDoesNotExist()
        menu("MONTH")
        scrollTo("day_2026-12-15")
        compose.onNodeWithTag("day_2026-12-15").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        compose.onNodeWithTag("dayEmpty_2026-12-15").assertExists()
    }

    // endregion


    // region 달력 범위 (Tokipin-Calendar-Range, today = 2026-11-26, shared fixture)

    private val rangeToday = java.time.LocalDate.of(2026, 11, 26)
    private val fixture: String by lazy { context.assets.open("calendar-range-records.json").reader().readText() }

    private fun launchRange() = launch(data = fixture, today = rangeToday)

    private fun selectDay(date: String) {
        scenario!!.onActivity { it.vm.openDay(java.time.LocalDate.parse(date)) }
        compose.waitUntil(3_000) { exists(level("DAILY")) }
    }

    /** Mini months draw their days in one Canvas; the dots they draw are exposed as RecordedDaysKey. */
    private fun dayDot(date: String): Boolean {
        val d = java.time.LocalDate.parse(date)
        val node = compose.onNodeWithTag("mini_${date.substring(0, 7)}").fetchSemanticsNode()
        return d in node.config[com.tokipin.app.ui.RecordedDaysKey]
    }

    @Test fun yearViewShowsTheSelectedMonthFocusAndTheRecordDayDotsTogether() {
        launchRange()
        selectDay("2017-05-14")
        menu("YEAR")
        scrollTo("year_2017")
        assertTrue("① 5월 focus", exists(compose.onNode(hasTestTag("mini_2017-05") and selected)))
        assertTrue(dayDot("2017-05-14")) // ②
        compose.onNode(hasTestTag("monthDot_2017-05"), useUnmergedTree = true).assertExists()  // ③
        compose.onNode(hasTestTag("yearDot_2017"), useUnmergedTree = true).assertExists()
        compose.onNode(list).performScrollToNode(hasTestTag("yearGap_2018"))
        compose.onNodeWithTag("yearGap_2018").assertExists()
        compose.onNodeWithTag("year_2019").assertDoesNotExist()
        compose.onNode(list).performScrollToNode(hasTestTag("year_2026"))
        compose.onNodeWithTag("mini_2026-12").assertIsNotEnabled()
        // Today: November's focus and its 23–26 dots together; 3, 9, 13, 16 have none.
        compose.onNodeWithText("오늘").performClick()
        compose.waitForIdle(); Thread.sleep(300)
        assertTrue(exists(compose.onNode(hasTestTag("mini_2026-11") and selected)))
        (23..26).forEach { assertTrue("dot on 11-$it", dayDot("2026-11-$it")) }
        listOf("03", "09", "13", "16").forEach { assertTrue("no dot on 11-$it", !dayDot("2026-11-$it")) }
    }

    @Test fun yearGapOpensInPlaceAndCloses() {
        launchRange()
        menu("YEAR")
        compose.onNode(list).performScrollToNode(hasTestTag("yearGap_2018"))
        compose.onNodeWithTag("yearGap_2018").performClick()
        compose.waitForIdle()
        compose.onNode(list).performScrollToNode(hasTestTag("year_2020"))
        compose.onNodeWithTag("year_2020").assertExists()
        compose.onNode(list).performScrollToNode(hasTestTag("yearGap_2018"))
        compose.onNodeWithTag("yearGap_2018").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("year_2020").assertDoesNotExist()
    }

    @Test fun monthViewStartsAtTheOldestMonthAndFoldsLongGaps() {
        launchRange()
        selectDay("2017-05-14")
        menu("MONTH")
        compose.onNode(list).performScrollToIndex(0)
        compose.onNodeWithTag("month_2017-05").assertExists()
        compose.onNode(list).performScrollToNode(hasTestTag("gap_2017-06"))
        compose.onNodeWithTag("gap_2017-06").assertExists()
        compose.onNode(list).performScrollToNode(hasTestTag("month_2024-06"))
        compose.onNodeWithTag("month_2024-06").assertExists() // five empty months stay open
        compose.onNode(list).performScrollToNode(hasTestTag("gap_2024-08"))
        compose.onNodeWithTag("month_2024-09").assertDoesNotExist()
        compose.onNodeWithTag("gap_2024-08").performClick()
        compose.waitForIdle()
        compose.onNode(list).performScrollToNode(hasTestTag("month_2025-01"))
        compose.onNodeWithTag("month_2025-01").assertExists()
        compose.onNode(list).performScrollToNode(hasTestTag("gap_2024-08"))
        compose.onNodeWithTag("gap_2024-08").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("month_2024-09").assertDoesNotExist()
        compose.onNode(list).performScrollToNode(hasTestTag("day_2026-10-15"))
        compose.onNodeWithTag("day_2026-10-15").assertExists() // empty recent month: every date
        compose.onNodeWithTag("month_2026-12").assertDoesNotExist()
    }

    @Test fun recordsRailStopsAtTheCurrentMonth() {
        launchRange()
        showRecordedMonth("2026-11")
        compose.onNodeWithTag("rail_2026-12").assertDoesNotExist()
        val w = bounds(compose.onNodeWithTag("recordsBody")).width / density
        val before = ticks()
        drag("recordsBody", -0.4f * w, 0f, 600, slow = true)
        assertTrue("no month after the current one", railSelected("2026-11"))
        assertEquals(before, ticks())
    }

    // region v0.3 기록한 달 목록·연도 힌트·날짜 헤더 (same fixture, today 2026-11-26)

    private fun hint(month: String) = compose.onNode(hasTestTag("railHint_$month"), useUnmergedTree = true)
    private fun unmerged(tag: String) = bounds(compose.onNode(hasTestTag(tag), useUnmergedTree = true))

    @Test fun archiveAprilAndJanuarySwapInOneSlotWithYearHints() {
        launchRange()
        showRecordedMonth("2023-04")
        compose.onNodeWithTag("card_2023-04-08").assertExists()
        compose.onNodeWithText("책방").assertExists()
        assertTrue(header(2023))
        // 2023.04: 2017 over May (left), 2024 over January (right), none over July 2024.
        hint("2017-05").assertExists(); hint("2024-01").assertExists(); hint("2024-07").assertDoesNotExist(); hint("2023-04").assertDoesNotExist()
        val capsule = unmerged("railCapsule").center
        val numberTop = unmerged("railNumber_2023-04").top
        val w = bounds(compose.onNodeWithTag("recordsBody")).width / density
        val before = ticks()
        drag("recordsBody", -0.23f * w, 0f, 900, slow = true)
        assertTrue("2023.04 → 2024.01 in one slot", railSelected("2024-01"))
        assertTrue(header(2024))
        compose.onNodeWithTag("card_2024-01-10").assertExists()
        compose.onNodeWithText("산책").assertExists()
        assertEquals(before + 1, ticks())
        Thread.sleep(400); compose.waitForIdle()
        // 2024.01: 2017 over May, 2023 over April, none over July 2024, 2025 over February.
        hint("2017-05").assertExists(); hint("2023-04").assertExists(); hint("2024-07").assertDoesNotExist(); hint("2025-02").assertExists()
        assertEquals("capsule centre fixed", capsule.x, unmerged("railCapsule").center.x, 1.5f)
        assertEquals("capsule centre fixed", capsule.y, unmerged("railCapsule").center.y, 1.5f)
        assertEquals("number row fixed", numberTop, unmerged("railNumber_2024-01").top, 1.5f)
        drag("recordsBody", 0.23f * w, 0f, 900, slow = true)
        assertTrue("and back", railSelected("2023-04"))
        assertTrue(header(2023))
        assertEquals(before + 2, ticks())
    }

    @Test fun novemberStepsBackToSeptemberOverTheEmptyOctober() {
        launchRange()
        showRecordedMonth("2026-11")
        compose.onNodeWithTag("rail_2026-10").assertDoesNotExist()
        listOf("2026-08", "2026-09", "2026-11").forEach { hint(it).assertDoesNotExist() } // all in 2026
        val w = bounds(compose.onNodeWithTag("recordsBody")).width / density
        val before = ticks()
        drag("recordsBody", 0.23f * w, 0f, 900, slow = true)
        assertTrue(railSelected("2026-09"))
        assertEquals(before + 1, ticks())
        // The month calendar still shows every date of the empty October.
        menu("MONTH")
        scrollTo("day_2026-10-15")
        compose.onNodeWithTag("day_2026-10-15").assertExists()
    }

    @Test fun enteringFromAnEmptyPastMonthOpensTheNearestRecentMonthAndKeepsTheDay() {
        launchRange()
        selectDay("2026-10-12") // empty October; September and November are both one month away
        val before = ticks()
        menu("RECORDS")
        assertTrue("tie goes to the more recent month", railSelected("2026-11"))
        scenario!!.onActivity { assertEquals(java.time.LocalDate.parse("2026-10-12"), it.vm.selectedDate) }
        assertEquals("only the level change ticks", before + 1, ticks())
    }

    @Test fun deletingAMonthsLastRecordMovesToTheNearestMonthSilently() {
        val data = """{"version":1,"days":[
            {"date":"2023-04-08","updatedAt":"2023-04-08T10:00:00Z","items":[${item("word", "책방", "p1")}]},
            {"date":"2026-07-10","updatedAt":"2026-07-10T10:00:00Z","items":[${item("word", "칠월", "p2")}]},
            {"date":"2026-08-10","updatedAt":"2026-08-10T10:00:00Z","items":[${item("word", "팔월", "p3")}]},
            {"date":"2026-09-10","updatedAt":"2026-09-10T10:00:00Z","items":[${item("word", "구월", "p4")}]}]}"""
        launch(data = data, today = rangeToday)
        showRecordedMonth("2026-08")
        var before = ticks()
        scenario!!.onActivity { it.vm.deleteDay(java.time.LocalDate.parse("2026-08-10")) }
        compose.waitUntil(3_000) { !exists(compose.onNodeWithTag("rail_2026-08")) }
        compose.waitForIdle()
        assertTrue("July and September are both one away: the more recent", railSelected("2026-09"))
        compose.onNodeWithTag("card_2026-09-10").assertExists()
        assertEquals(before, ticks())
        // Undo brings August back to the list; the committed month stays where it is.
        compose.onNodeWithText("실행 취소").performClick()
        compose.waitUntil(3_000) { exists(compose.onNodeWithTag("rail_2026-08")) }
        assertTrue(railSelected("2026-09"))
        // The only past record of 2023: its month leaves, the nearest is July 2026.
        showRecordedMonth("2023-04")
        before = ticks()
        scenario!!.onActivity { it.vm.deleteDay(java.time.LocalDate.parse("2023-04-08")) }
        compose.waitUntil(3_000) { !exists(compose.onNodeWithTag("rail_2023-04")) }
        compose.waitForIdle()
        assertTrue(railSelected("2026-07"))
        assertTrue(header(2026))
        assertEquals(before, ticks())
    }

    @Test fun monthBoundaryReplacesAnEmptyCurrentMonthWithTheNewOne() {
        val data = """{"version":1,"days":[{"date":"2026-09-01","updatedAt":"2026-09-01T10:00:00Z","items":[${item("word", "구월", "r1")}]}]}"""
        launch(data = data, today = rangeToday)
        showRecordedMonth("2026-11")
        compose.onNodeWithTag("monthEmpty").assertExists()
        val before = ticks()
        com.tokipin.app.rules.AppClock.override = java.time.LocalDate.of(2026, 12, 1)
        scenario!!.onActivity { it.vm.refreshToday() }
        compose.waitUntil(3_000) { railSelected("2026-12") }
        compose.onNodeWithTag("rail_2026-11").assertDoesNotExist()
        compose.onNodeWithTag("monthEmpty").assertExists()
        assertEquals(before, ticks())
    }

    @Test fun inDecemberTheEmptyCurrentMonthFollowsNovember() {
        launch(data = fixture, today = java.time.LocalDate.of(2026, 12, 15))
        showRecordedMonth("2026-12")
        compose.onNodeWithTag("monthEmpty").assertExists()
        assertTrue(header(2026))
        val w = bounds(compose.onNodeWithTag("recordsBody")).width / density
        val before = ticks()
        drag("recordsBody", 0.23f * w, 0f, 900, slow = true)
        assertTrue("before December comes November", railSelected("2026-11"))
        assertEquals(before + 1, ticks())
        drag("recordsBody", 0.23f * w, 0f, 900, slow = true)
        assertTrue("then September (October is empty)", railSelected("2026-09"))
    }

    private fun assertTitleCentred(tag: String, title: String?) {
        val rootW = bounds(compose.onRoot()).width
        if (title != null) compose.onNode(hasTestTag(tag) and hasText(title, substring = true)).assertExists()
        assertEquals("$tag on the screen's centre line", rootW / 2, bounds(compose.onNodeWithTag(tag)).center.x, 2f)
        // No button overlaps the title.
        val t = bounds(compose.onNodeWithTag(tag))
        compose.onAllNodes(androidx.compose.ui.test.hasClickAction() and hasAnyAncestor(hasTestTag("topBar") or hasTestTag("editorTopBar")) and !hasTestTag(tag))
            .fetchSemanticsNodes().forEach { n ->
                val b = n.boundsInRoot
                assertTrue("button ${n.config} overlaps $tag", b.right <= t.left || b.left >= t.right || b.bottom <= t.top || b.top >= t.bottom)
                assertTrue("touch target ≥ 48dp", b.width >= 47.5f * density && b.height >= 47.5f * density)
            }
    }

    private fun walkHeaders() {
        selectDay("2017-05-14")
        assertTitleCentred("headerTitle", "2017.05")
        menu("RECORDS")
        showRecordedMonth("2024-01")
        assertTitleCentred("headerTitle", "2024")
        menu("MONTH")
        assertTitleCentred("headerTitle", null)
        menu("YEAR")
        compose.onNodeWithTag("headerTitle").assertDoesNotExist()
        // Day opened from a search result: "검색 결과" on the left, title still centred.
        scenario!!.onActivity {
            val date = java.time.LocalDate.parse("2023-04-08")
            it.vm.openSearchResult(date, it.vm.journal.itemsOn(date).first())
        }
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        compose.onNodeWithTag("searchReturn").assertExists()
        assertTitleCentred("headerTitle", "2023.04")
        scenario!!.onActivity { it.vm.openEditor(java.time.LocalDate.parse("2026-11-25")) }
        compose.waitForIdle()
        assertTitleCentred("editorDate", "2026.11.25")
    }

    @Test fun dateHeadersSitOnTheCentreLine() {
        launchRange()
        walkHeaders()
    }

    @Test fun dateHeadersStayCentredAndClearOfButtonsWithLargeText() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val old = ui.executeShellCommand("settings get system font_scale").let { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).reader().readText().trim()
        }
        fun setScale(v: String) = android.os.ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand("settings put system font_scale $v")).readBytes()
        try {
            setScale("2.0")
            Thread.sleep(800)
            launchRange()
            walkHeaders()
        } finally {
            setScale(if (old == "null" || old.isBlank()) "1.0" else old)
        }
    }

    // endregion

    @Test fun pickerCancelKeepsTheDraftAndANewFragmentMovesAfterTheTargetsRecords() {
        launchRange()
        selectDay("2026-11-26")
        val savedToday = savedValues("2026-11-26")
        compose.onNodeWithTag("editDay").performClick()
        compose.onNodeWithContentDescription("단어").performClick()
        compose.onNodeWithContentDescription("단어 입력").performTextInput("파도")
        compose.onNodeWithTag("editorDate").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("취소").performClick() // cancel: nothing changes
        compose.onNodeWithText("파도").assertExists()
        scenario!!.onActivity { it.vm.changeDraftDate(java.time.LocalDate.parse("2017-05-14")) }
        compose.waitForIdle()
        assertTrue(exists(compose.onNodeWithText("2017.05.14")))
        compose.onNodeWithContentDescription("기록 저장").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        assertEquals(listOf("바닷가", "파도"), savedValues("2017-05-14"))
        assertEquals("the other day is untouched", savedToday, savedValues("2026-11-26"))
        // Saving again from the same draft path never duplicates.
        assertEquals(1, savedValues("2017-05-14").count { it == "파도" })
    }

    @Test fun onlySavingAnEarlierDateExtendsTheCalendar() {
        launchRange()
        compose.onNodeWithTag("addRecord").performClick()
        compose.onNodeWithContentDescription("단어 입력").performTextInput("옛날")
        scenario!!.onActivity { it.vm.changeDraftDate(java.time.LocalDate.parse("2015-09-06")) }
        back() // draft kept, nothing saved
        compose.waitForIdle()
        scenario!!.onActivity { assertEquals(java.time.YearMonth.of(2017, 5), it.vm.range.first) }
        scenario!!.onActivity { it.vm.openEditor(java.time.LocalDate.parse("2015-09-06")) }
        compose.onNodeWithContentDescription("기록 저장").performClick()
        compose.waitUntil(3_000) { exists(level("DAILY")) }
        scenario!!.onActivity { assertEquals(java.time.YearMonth.of(2015, 9), it.vm.range.first) }
        assertEquals(listOf("옛날"), savedValues("2015-09-06"))
    }

    @Test fun datePickerOffersNothingAfterThisMonth() {
        launchRange()
        compose.onNodeWithTag("addRecord").performClick()
        compose.onNodeWithTag("editorDate").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("datePickerOk").assertExists()
        scenario!!.onActivity {
            assertTrue(it.vm.range.canWrite(java.time.LocalDate.of(2026, 11, 30)))
            assertTrue(!it.vm.range.canWrite(java.time.LocalDate.of(2026, 12, 1)))
        }
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
        assertTrue(railDaySelected(sp("2026-11-25")))
        compose.onNode(hasTestTag("weekRail")).assertExists()
    }

    @Suppress("unused")
    private fun near(a: Float, b: Float) = abs(a - b) < 2f
}
