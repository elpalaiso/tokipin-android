package com.tokipin.app

import android.app.UiAutomation
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tokipin.app.rules.Level
import com.tokipin.app.ui.Haptics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * UX v0.4 §1–5 on a window wide enough for two panes (a tablet or an unfolded foldable): what each
 * pane shows, where the editor and search open, the tick count, and the editor surviving a change
 * to one column and back. Skipped on phone-sized windows.
 */
@RunWith(AndroidJUnit4::class)
class PaneDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val ui: UiAutomation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun item(kind: String, value: String, id: String) = """{"id":"$id","kind":"$kind","value":"$value"}"""

    @Before fun launch() {
        com.tokipin.app.rules.AppClock.override = LocalDate.of(2026, 11, 27)
        File(context.filesDir, "tokipin").apply { deleteRecursively(); mkdirs() }.resolve("records.json").writeText(
            """{"version":1,"days":[
              {"date":"2026-11-12","updatedAt":"2026-11-12T10:00:00Z","items":[${item("word", "단풍", "a")}]},
              {"date":"2026-11-25","updatedAt":"2026-11-25T10:00:00Z","items":[${item("word", "살구잼", "f")},${item("place", "연남동", "g")}]}
            ]}""",
        )
        context.getSharedPreferences("tokipin.settings", Context.MODE_PRIVATE).edit().putBoolean("launched", true).putBoolean("haptics", true).commit()
        TestLocale.set("ko")
        UsageSharing(context).markPresented()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(5_000) { exists("calendarPane") || exists("level_RECORDS") }
        assumeTrue("the window is too small for two panes", exists("calendarPane"))
    }

    @After fun close() {
        shell("wm size reset")
        scenario?.close()
        com.tokipin.app.rules.AppClock.override = null
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)).reader().readText().trim()

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun inPane(pane: String, matcher: SemanticsMatcher) = matcher and hasAnyAncestor(hasTestTag(pane))
    private fun existsIn(pane: String, tag: String) = compose.onAllNodes(inPane(pane, hasTestTag(tag))).fetchSemanticsNodes().isNotEmpty()
    private fun vm(block: (com.tokipin.app.ui.AppViewModel) -> Unit) = scenario!!.onActivity { block(it.vm) }
    private fun activity(): MainActivity { lateinit var a: MainActivity; scenario!!.onActivity { a = it }; return a }

    @Test fun calendarAndDayPanesShowTheSameState() {
        assertTrue(existsIn("calendarPane", "level_RECORDS"))
        assertTrue(existsIn("dayPane", "level_DAILY"))
        // The calendar pane's view menu has no 일간.
        compose.onNode(inPane("calendarPane", SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("탐색 보기")))).performClick()
        compose.waitUntil(3_000) { exists("menu_MONTH") }
        assertTrue(!exists("menu_DAILY"))
        compose.onNode(hasTestTag("menu_MONTH")).performClick()
        compose.waitUntil(3_000) { existsIn("calendarPane", "level_MONTH") }
        assertTrue(existsIn("dayPane", "level_DAILY"))
    }

    @Test fun tappingADateOpensItInTheDayPaneWithOneTick() {
        vm { it.changeLevel(Level.MONTH) }
        compose.waitUntil(3_000) { existsIn("calendarPane", "level_MONTH") }
        val before = Haptics.totalRequested
        compose.onNode(inPane("calendarPane", hasTestTag("day_2026-11-12"))).performClick()
        compose.waitForIdle()
        vm {
            assertEquals(LocalDate.of(2026, 11, 12), it.selectedDate)
            assertEquals(Level.DAILY, it.level)
            assertEquals(Level.MONTH, it.calendarLevel)
        }
        assertEquals(1, Haptics.totalRequested - before)
        assertTrue("the calendar pane keeps its level", existsIn("calendarPane", "level_MONTH"))
        // The date already shown: no tick.
        compose.onNode(inPane("calendarPane", hasTestTag("day_2026-11-12"))).performClick()
        compose.waitForIdle()
        assertEquals(1, Haptics.totalRequested - before)
    }

    @Test fun theEditorOpensInTheDayPaneAndTheCalendarIsInert() {
        vm { it.openDay(LocalDate.of(2026, 11, 25)) }
        compose.waitUntil(3_000) { existsIn("dayPane", "editDay") }
        compose.onNode(inPane("dayPane", hasTestTag("editDay"))).performClick()
        compose.waitUntil(3_000) { existsIn("dayPane", "editorTopBar") }
        assertTrue(exists("calendarScrim"))
        assertTrue("the calendar is out of reach of assistive tech", !existsIn("calendarPane", "level_RECORDS"))
        vm { assertEquals(LocalDate.of(2026, 11, 25).toString(), it.draft?.date) }
    }

    @Test fun plusOpensTheDateTheDayPaneShows() {
        vm { it.openDay(LocalDate.of(2026, 11, 10)) }
        compose.waitUntil(3_000) { existsIn("dayPane", "addRecord") }
        compose.onNode(inPane("dayPane", hasTestTag("addRecord"))).performClick()
        compose.waitUntil(3_000) { existsIn("dayPane", "editorTopBar") }
        vm { assertEquals("2026-11-10", it.draft?.date) }
    }

    @Test fun theSearchListStaysWhileAResultIsOpen() {
        vm { it.openSearch() }
        compose.waitUntil(3_000) { existsIn("calendarPane", "searchField") }
        compose.onNode(hasTestTag("searchField")).performTextInput("살구")
        compose.waitUntil(3_000) { compose.onAllNodes(inPane("calendarPane", hasText("살구잼") and !hasTestTag("searchField"))).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(inPane("calendarPane", hasText("살구잼") and !hasTestTag("searchField"))).performClick()
        compose.waitForIdle()
        assertTrue("the list stays", existsIn("calendarPane", "searchField"))
        vm { assertEquals(LocalDate.of(2026, 11, 25), it.selectedDate); assertTrue(it.openedFromSearch) }
        assertTrue(existsIn("dayPane", "level_DAILY"))
    }

    @Test fun theEditorStaysThroughOneColumnAndBack() {
        vm { it.openDay(LocalDate.of(2026, 11, 27)) }
        compose.waitUntil(3_000) { existsIn("dayPane", "addRecord") }
        compose.onNode(inPane("dayPane", hasTestTag("addRecord"))).performClick()
        val composer = compose.onNode(hasSetTextAction())
        compose.waitUntil(5_000) { runCatching { composer.assertIsFocused() }.isSuccess }
        composer.performTextInput("봄바람")
        composer.performTextInputSelection(TextRange(1))
        val before = activity()
        shell("wm size 1000x1600") // narrower than two panes need
        compose.waitUntil(5_000) { !exists("dayPane") }
        compose.waitUntil(5_000) { runCatching { composer.assertIsFocused() }.isSuccess }
        shell("wm size reset")
        compose.waitUntil(5_000) { exists("dayPane") }
        compose.waitUntil(5_000) { runCatching { composer.assertIsFocused() }.isSuccess }
        assertSame(before, activity())
        val node = composer.fetchSemanticsNode()
        assertEquals("봄바람", node.config[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(1), node.config[SemanticsProperties.TextSelectionRange])
    }

    @Test fun backGoesUpFromTheCalendarLevel() {
        vm { it.changeLevel(Level.MONTH); it.openDay(LocalDate.of(2026, 11, 12)) }
        compose.waitForIdle()
        vm { assertEquals(Level.MONTH, it.calendarLevel) }
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        compose.waitUntil(3_000) { existsIn("calendarPane", "level_YEAR") }
        assertTrue(existsIn("dayPane", "level_DAILY"))
    }
}
