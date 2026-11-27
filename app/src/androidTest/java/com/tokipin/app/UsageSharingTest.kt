package com.tokipin.app

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tokipin.app.ui.AppViewModel
import com.tokipin.app.ui.Screen
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UsageSharingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private var scenario: ActivityScenario<MainActivity>? = null
    private fun main(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)

    @Before fun fresh() {
        File(app.filesDir, "tokipin").deleteRecursively()
        for (name in listOf("tokipin.usage", "tokipin.settings")) {
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        TestLocale.set("en")
    }

    @After fun close() {
        scenario?.close()
        TestLocale.set("ko")
    }

    @Test fun launchOffersOnceBeforeKeyboardAndSettingsStartsOff() {
        assumeTrue(BuildConfig.USAGE_SHARING_AVAILABLE)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(8_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("usageInvitation")).fetchSemanticsNodes().isNotEmpty() }
        val resources = AppLocale.resources(app)
        compose.onNodeWithText(resources.getString(R.string.usage_prompt)).assertIsDisplayed()
        val laterHint = resources.getString(R.string.usage_later_hint)
        compose.onNodeWithText(laterHint).assertDoesNotExist()
        compose.onNodeWithContentDescription("Enter a word").assertIsNotFocused()
        compose.onNodeWithTag("usageLater").performClick()
        compose.onNodeWithTag("usageInvitation").assertDoesNotExist()
        compose.onNodeWithText(laterHint).assertIsDisplayed()
        compose.onNodeWithContentDescription("Enter a word").assertIsFocused()
        scenario!!.onActivity { it.vm.updateRaw("kept draft"); it.vm.leaveEditor(); it.vm.openSettings() }
        compose.onNodeWithTag("usageRow").performScrollTo().assertIsOff()
        compose.onNodeWithText(laterHint).assertDoesNotExist()

        assertTrue(UsageSharing(app).hasChoice)
        assertFalse(UsageSharing(app).enabled)
    }

    @Test fun launchAfterDecliningDoesNotRepeatInvitationOrHint() {
        assumeTrue(BuildConfig.USAGE_SHARING_AVAILABLE)
        UsageSharing(app).setEnabled(false)
        app.getSharedPreferences("tokipin.settings", Context.MODE_PRIVATE).edit().putBoolean("launched", true).commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(8_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("level_RECORDS")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("usageInvitation").assertDoesNotExist()
        compose.onNodeWithText(AppLocale.resources(app).getString(R.string.usage_later_hint)).assertDoesNotExist()
        assertFalse(UsageSharing(app).enabled)
    }


    @Test fun upgradeCanDismissWithBackWithoutChangingSavedData() {
        File(app.filesDir, "tokipin").apply { mkdirs() }.resolve("records.json").writeText(
            """{"version":1,"days":[{"date":"2026-01-01","items":[{"id":"w","kind":"word","value":"kept"}]}]}""",
        )
        app.getSharedPreferences("tokipin.settings", Context.MODE_PRIVATE).edit().putBoolean("launched", true).commit()
        assumeTrue(BuildConfig.USAGE_SHARING_AVAILABLE)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(8_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("usageInvitation")).fetchSemanticsNodes().isNotEmpty() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(8_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag("usageInvitation")).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(AppLocale.resources(app).getString(R.string.usage_later_hint)).assertDoesNotExist()
        scenario!!.onActivity {
            assertEquals(Screen.Browse, it.vm.screen)
            assertEquals("kept", it.vm.journal.itemsOn(java.time.LocalDate.parse("2026-01-01")).single().value)
            assertFalse(it.vm.usageEnabled)
        }
    }

    @Test fun consentGatesSuccessfulManualSavesAndPersistsAcrossLaunches() {
        val changes = mutableListOf<Boolean>()
        var saves = 0
        val sink = object : UsageAnalytics {
            override fun setCollection(enabled: Boolean) { changes += enabled }
            override fun recordSaved() { saves++ }
        }
        lateinit var vm: AppViewModel
        main { vm = AppViewModel(app, UsageSharing(app, sink, available = true)) }
        compose.waitUntil(8_000) { vm.screen == Screen.Editor }
        main {
            assertTrue(vm.usagePromptOpen)
            vm.usagePromptPresented()
            assertTrue(changes.isEmpty())
            vm.chooseUsageSharing(false)
            vm.updateRaw("private")
            vm.saveDraft()
        }
        compose.waitUntil(8_000) { vm.screen == Screen.Browse }
        main {
            assertEquals(0, saves)
            vm.chooseUsageSharing(true)
            vm.openEditor()
            vm.updateRaw("add")
            vm.saveDraft()
            vm.saveDraft()
        }
        compose.waitUntil(8_000) { vm.screen == Screen.Browse }
        main {
            assertEquals(1, saves)
            vm.openEditor()
            vm.saveDraft() // no changes
            vm.openEditor()
            vm.deleteDraftItems(setOf(vm.draft!!.items.first().id))
            vm.saveDraft() // deletion only, despite a nonempty remainder
        }
        compose.waitUntil(8_000) { vm.screen == Screen.Browse }
        main {
            assertEquals(1, saves)
            val restored = UsageSharing(app, sink, available = true)
            assertTrue(restored.enabled)
            vm.chooseUsageSharing(false)
            vm.openEditor()
            vm.updateRaw("after off")
            vm.saveDraft()
        }
        compose.waitUntil(8_000) { vm.screen == Screen.Browse }
        assertEquals(1, saves)
        assertFalse(UsageSharing(app, sink, available = true).enabled)
        assertEquals(listOf(false, true, true, false), changes)
    }
}
