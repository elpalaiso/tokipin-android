package com.tokipin.app

import android.app.UiAutomation
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * UX v0.4 §5 with real window changes: rotation, a new window size and fold/unfold reach the same
 * MainActivity (configChanges), and the editor keeps its focus, keyboard, text and cursor. The
 * configuration the screens read follows the new window (AppLocale overrides the language only).
 * Recreation, which language and font-size changes still cause, is covered by [WindowChangeTest].
 */
@RunWith(AndroidJUnit4::class)
class WindowResizeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val ui: UiAutomation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    @Before fun launch() {
        com.tokipin.app.rules.AppClock.override = LocalDate.of(2026, 11, 27)
        File(context.filesDir, "tokipin").apply { deleteRecursively(); mkdirs() }.resolve("records.json").writeText(
            """{"version":1,"days":[{"date":"2026-11-25","updatedAt":"2026-11-25T10:00:00Z","items":[{"id":"f","kind":"word","value":"살구잼"}]}]}""",
        )
        context.getSharedPreferences("tokipin.settings", Context.MODE_PRIVATE).edit().putBoolean("launched", true).commit()
        TestLocale.set("ko")
        UsageSharing(context).markPresented()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("level_RECORDS")).fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun restore() {
        ui.setRotation(UiAutomation.ROTATION_UNFREEZE)
        shell("wm size reset")
        if (canFold()) shell("cmd device_state state reset")
        scenario?.close()
        com.tokipin.app.rules.AppClock.override = null
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(ui.executeShellCommand(command)).reader().readText().trim()

    private fun activity(): MainActivity {
        lateinit var current: MainActivity
        scenario!!.onActivity { current = it }
        return current
    }

    private fun widthDp(): Int = activity().resources.configuration.screenWidthDp

    private fun imeVisible(): Boolean {
        var visible = false
        scenario!!.onActivity {
            visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    private val composer get() = compose.onNode(hasSetTextAction())

    /** Opens + and types with the keyboard up; returns the activity that holds it. */
    private fun startTyping(): MainActivity {
        // An empty day shows "+" in one column and in the day pane alike.
        scenario!!.onActivity { it.vm.openDay(LocalDate.of(2026, 11, 27)) }
        compose.waitUntil(3_000) { compose.onAllNodes(hasTestTag("addRecord")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("addRecord").performClick()
        compose.waitUntil(5_000) { imeVisible() }
        composer.performTextInput("봄바람")
        composer.performTextInputSelection(TextRange(1))
        compose.waitForIdle()
        return activity()
    }

    private fun assertStillTyping(before: MainActivity) {
        compose.waitForIdle()
        assertSame("the activity was recreated", before, activity())
        compose.waitUntil(5_000) { imeVisible() }
        composer.assertIsFocused()
        val node = composer.fetchSemanticsNode()
        assertEquals("봄바람", node.config[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(1), node.config[SemanticsProperties.TextSelectionRange])
    }

    @Test fun rotationKeepsTheActivityFieldAndKeyboard() {
        val before = startTyping()
        val width = widthDp()
        ui.setRotation(UiAutomation.ROTATION_FREEZE_90)
        compose.waitUntil(5_000) { widthDp() != width }
        assertStillTyping(before)
        ui.setRotation(UiAutomation.ROTATION_FREEZE_0)
        compose.waitUntil(5_000) { widthDp() == width }
        assertStillTyping(before)
    }

    @Test fun windowSizeChangeKeepsTheActivityFieldAndKeyboard() {
        val before = startTyping()
        val width = widthDp()
        shell("wm size 1200x1600")
        compose.waitUntil(5_000) { widthDp() != width }
        assertNotEquals(width, widthDp())
        assertStillTyping(before)
        shell("wm size reset")
        compose.waitUntil(5_000) { widthDp() == width }
        assertStillTyping(before)
    }

    private fun canFold() = shell("cmd device_state print-states").contains("CLOSED")

    @Test fun foldAndUnfoldKeepTheActivityFieldAndKeyboard() {
        assumeTrue("not a foldable", canFold())
        val before = startTyping()
        val width = widthDp()
        shell("cmd device_state state 0") // CLOSED: the outer screen
        compose.waitUntil(8_000) { widthDp() != width }
        assertStillTyping(before)
        shell("cmd device_state state reset")
        compose.waitUntil(8_000) { widthDp() == width }
        assertStillTyping(before)
        shell("cmd device_state state 1") // HALF_OPENED: tabletop or book posture
        Thread.sleep(1_500)
        assertStillTyping(before)
    }
}
