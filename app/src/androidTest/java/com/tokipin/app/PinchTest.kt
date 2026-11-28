package com.tokipin.app

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Real two-finger pinches through the four levels (Figma 04 · 네 단계로 확대하고 축소하기,
 * 핀치 중에는 날짜를 기준으로): year ↔ month ↔ recorded days ↔ day.
 */
@RunWith(AndroidJUnit4::class)
class PinchTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    private val records = """
        {"version":1,"days":[
          {"date":"2026-09-06","updatedAt":"2026-09-06T10:00:00Z","items":[{"id":"a","kind":"time","value":"17:40"},{"id":"b","kind":"place","value":"서점"}]},
          {"date":"2026-11-18","updatedAt":"2026-11-18T10:00:00Z","items":[{"id":"c","kind":"word","value":"산책"},{"id":"d","kind":"place","value":"한강"}]},
          {"date":"2026-11-25","updatedAt":"2026-11-25T10:00:00Z","items":[{"id":"e","kind":"word","value":"살구잼"},{"id":"f","kind":"place","value":"연남동"}]}
        ]}
    """.trimIndent()

    @Before fun seed() {
        com.tokipin.app.rules.AppClock.override = java.time.LocalDate.of(2027, 3, 15)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "tokipin").apply { deleteRecursively(); mkdirs() }.resolve("records.json").writeText(records)
        context.getSharedPreferences("tokipin.settings", Context.MODE_PRIVATE).edit().putBoolean("launched", true).commit()
        TestLocale.set("ko")
        UsageSharing(context).markPresented() // Existing installation with optional sharing off.
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(5_000) { level("RECORDS").exists() }
        // One-column pinch; the calendar pane's pinch is in PaneDeviceTest.
        org.junit.Assume.assumeFalse("two-pane window", compose.onAllNodes(androidx.compose.ui.test.hasTestTag("calendarPane")).fetchSemanticsNodes().isNotEmpty())
    }

    @After fun close() = scenario.close()

    private fun level(name: String) = compose.onNodeWithTag("level_$name")
    private fun SemanticsNodeInteraction.exists() = runCatching { fetchSemanticsNode() }.isSuccess

    private fun pinchAt(center: Offset, zoomIn: Boolean, spread: Float = 3f) {
        val near = 60f
        val far = near * spread
        val (from, to) = if (zoomIn) near to far else far to near
        compose.onRoot().performTouchInput {
            pinch(
                center - Offset(from, from), center - Offset(to, to),
                center + Offset(from, from), center + Offset(to, to),
                durationMillis = 300,
            )
        }
        compose.waitForIdle()
    }

    private fun centerOf(description: String): Offset {
        val node = compose.onNode(hasContentDescription(description, substring = true), useUnmergedTree = false)
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val y = node.fetchSemanticsNode().boundsInRoot.center.y
        // Keep the pinch over the calendar, clear of the floating 오늘/+ bar on short screens.
        if (y > root.height * 0.55f) {
            compose.onNode(androidx.compose.ui.test.hasScrollAction()).performTouchInput {
                swipeUp(startY = centerY, endY = centerY - (y - root.height * 0.45f), durationMillis = 600)
            }
            compose.waitForIdle()
        }
        return node.fetchSemanticsNode().boundsInRoot.center
    }

    /** The date as TalkBack reads it (cells and cards start with it). */
    private fun sp(date: String) = com.tokipin.app.ui.DateWords.spoken(java.time.LocalDate.parse(date))

    private fun screenCenter(): Offset = compose.onRoot().fetchSemanticsNode().boundsInRoot.center

    @Test fun pinchWalksAllFourLevelsAroundTheDateBetweenTheFingers() {
        // Out: recorded days → month → year.
        pinchAt(screenCenter(), zoomIn = false)
        compose.waitUntil(3_000) { level("MONTH").exists() }
        pinchAt(screenCenter(), zoomIn = false)
        compose.waitUntil(3_000) { level("YEAR").exists() }

        // In over September in the year view → the September month calendar.
        pinchAt(centerOf("2026년 9월, 기록한 날"), zoomIn = true)
        compose.waitUntil(3_000) { level("MONTH").exists() }
        compose.onNode(hasContentDescription(sp("2026-09-06"), substring = true)).assertIsDisplayed()

        // In over 6 Sep → recorded days, then in over its card → that day.
        pinchAt(centerOf(sp("2026-09-06")), zoomIn = true)
        compose.waitUntil(3_000) { level("RECORDS").exists() }
        pinchAt(centerOf(sp("2026-09-06")), zoomIn = true)
        compose.waitUntil(3_000) { level("DAILY").exists() }
        compose.onNodeWithText("서점").assertIsDisplayed()

        // Day is the closest level: pinching in again stays; pinching out returns to recorded days.
        pinchAt(screenCenter(), zoomIn = true)
        level("DAILY").assertIsDisplayed()
        pinchAt(screenCenter(), zoomIn = false)
        compose.waitUntil(3_000) { level("RECORDS").exists() }
    }

    @Test fun aSmallPinchChangesNothing() {
        pinchAt(screenCenter(), zoomIn = false, spread = 1.1f)
        level("RECORDS").assertIsDisplayed()
        pinchAt(screenCenter(), zoomIn = true, spread = 1.1f)
        level("RECORDS").assertIsDisplayed()
    }

    @Test fun pinchInOnMonthCellOpensThatDayInRecordedDays() {
        pinchAt(screenCenter(), zoomIn = false)
        compose.waitUntil(3_000) { level("MONTH").exists() }
        pinchAt(centerOf(sp("2026-11-18")), zoomIn = true)
        compose.waitUntil(3_000) { level("RECORDS").exists() }
        pinchAt(centerOf(sp("2026-11-18")), zoomIn = true)
        compose.waitUntil(3_000) { level("DAILY").exists() }
        compose.onNodeWithText("한강").assertIsDisplayed()
    }
}
