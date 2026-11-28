package com.tokipin.app

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tokipin.app.ui.Screen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * UX v0.4 §5 on today's single-column screens: a rotation, fold/unfold, resize or split screen
 * recreates the activity, and everything the person was in the middle of must come back as it was.
 * `ActivityScenario.recreate()` is the same destroy/recreate the system does for those changes.
 */
@RunWith(AndroidJUnit4::class)
class WindowChangeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun item(kind: String, value: String, id: String) = """{"id":"$id","kind":"$kind","value":"$value"}"""
    private val records = """
        {"version":1,"days":[
          {"date":"2026-11-25","updatedAt":"2026-11-25T10:00:00Z","items":[${item("word", "살구잼", "f")},${item("place", "연남동", "g")}]},
          {"date":"2026-11-26","updatedAt":"2026-11-26T10:00:00Z","items":[${item("word", "귤", "h")}]}
        ]}
    """.trimIndent()

    @Before fun launch() {
        com.tokipin.app.rules.AppClock.override = LocalDate.of(2026, 11, 27)
        File(context.filesDir, "tokipin").apply { deleteRecursively(); mkdirs() }.resolve("records.json").writeText(records)
        context.getSharedPreferences("tokipin.settings", Context.MODE_PRIVATE).edit().putBoolean("launched", true).commit()
        TestLocale.set("ko")
        UsageSharing(context).markPresented()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(5_000) { exists("level_RECORDS") }
    }

    @After fun close() {
        scenario?.close()
        com.tokipin.app.rules.AppClock.override = null
    }

    private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun existsText(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun recreate() {
        scenario!!.recreate()
        compose.waitForIdle()
    }

    private val composer get() = compose.onNode(hasSetTextAction() and !hasTestTag("searchField"))

    @Test fun typedTextAndCursorSurviveAndTheFieldKeepsFocus() {
        compose.onNodeWithTag("addRecord").performClick()
        compose.waitUntil(3_000) { runCatching { composer.assertIsFocused() }.isSuccess }
        composer.performTextInput("봄바람")
        composer.performTextInputSelection(TextRange(1))
        recreate()
        compose.waitUntil(3_000) { runCatching { composer.assertIsFocused() }.isSuccess }
        val node = composer.fetchSemanticsNode()
        assertEquals("봄바람", node.config[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(1), node.config[SemanticsProperties.TextSelectionRange])
        scenario!!.onActivity { assertEquals(Screen.Editor, it.vm.screen) }
    }

    @Test fun editorDatePickerStaysOpen() {
        compose.onNodeWithTag("addRecord").performClick()
        compose.waitUntil(3_000) { exists("editorDate") }
        compose.onNodeWithTag("editorDate").performClick()
        compose.waitUntil(3_000) { exists("datePickerOk") }
        recreate()
        compose.waitUntil(3_000) { exists("datePickerOk") }
    }

    @Test fun viewMenuStaysOpen() {
        compose.onNodeWithContentDescription("탐색 보기").performClick()
        compose.waitUntil(3_000) { exists("menu_YEAR") }
        recreate()
        compose.waitUntil(3_000) { exists("menu_YEAR") }
    }

    @Test fun dayDeleteConfirmationStaysOpen() {
        scenario!!.onActivity { it.vm.openDay(LocalDate.of(2026, 11, 25)) }
        compose.waitUntil(3_000) { exists("level_DAILY") }
        compose.onNodeWithContentDescription("이날 메뉴").performClick()
        compose.waitUntil(3_000) { exists("deleteDay") }
        recreate()
        compose.waitUntil(3_000) { exists("deleteDay") }
    }

    @Test fun searchQueryResultsAndHighlightSurvive() {
        scenario!!.onActivity { it.vm.openSearch() }
        compose.waitUntil(3_000) { exists("searchField") }
        compose.onNodeWithTag("searchField").performTextInput("살구")
        compose.waitUntil(3_000) { existsText("살구잼") }
        recreate()
        compose.waitUntil(3_000) { existsText("살구잼") }
        scenario!!.onActivity { assertEquals("살구", it.vm.searchQuery) }
        compose.onNode(hasText("살구잼") and !hasTestTag("searchField")).performClick()
        compose.waitUntil(3_000) { exists("searchReturn") }
        recreate()
        compose.waitUntil(3_000) { exists("searchReturn") }
        scenario!!.onActivity {
            assertEquals(LocalDate.of(2026, 11, 25), it.vm.selectedDate)
            assertTrue(it.vm.openedFromSearch)
        }
    }

    @Test fun undoAndSettingsSurvive() {
        scenario!!.onActivity { it.vm.deleteDay(LocalDate.of(2026, 11, 26)) }
        compose.waitForIdle()
        recreate()
        scenario!!.onActivity { assertTrue(it.vm.undo != null) }
        scenario!!.onActivity { it.vm.openSettings() }
        compose.waitForIdle()
        recreate()
        scenario!!.onActivity { assertTrue(it.vm.settingsOpen) }
    }
}
