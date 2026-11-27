package com.tokipin.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tokipin.app.data.Draft
import com.tokipin.app.data.DraftsFile
import com.tokipin.app.data.JournalStore
import com.tokipin.app.rules.AppClock
import com.tokipin.app.ui.Screen
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SampleCalendarTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val root get() = File(app.filesDir, "tokipin")
    private var scenario: ActivityScenario<MainActivity>? = null
    private val today = LocalDate.of(2026, 3, 1)

    @Before fun fresh() {
        root.deleteRecursively()
        for (name in listOf("tokipin.usage", "tokipin.settings")) app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        TestLocale.set("en")
        AppClock.override = today
    }

    @After fun teardown() {
        scenario?.close()
        AppClock.override = null
        TestLocale.set("ko")
    }

    private fun launch(tag: String) {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(tag)
    }

    private fun waitFor(tag: String) = compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun tapDay(date: LocalDate) {
        val day = "day_$date"
        compose.onNodeWithTag("sampleGrid").performScrollToNode(hasTestTag(day))
        compose.onNodeWithTag(day).performClick()
    }

    @Test fun guidedPracticeOnFirstOfMonthIsIsolatedAndRequiresAChoiceToSave() {
        launch("onboardingStart")
        tapDay(today.withDayOfMonth(15))
        compose.onNodeWithText(AppLocale.resources(app).getString(R.string.sample_pottery)).assertIsDisplayed()
        compose.onNodeWithTag("samplePractice").performClick()
        compose.onNodeWithTag("sampleSave").assertIsNotEnabled()
        compose.onNodeWithTag("sampleChip_✨").performClick()
        compose.onNodeWithText(AppLocale.resources(app).getString(R.string.onboarding_save_guide)).assertIsDisplayed()
        compose.onNodeWithTag("sampleSave").performClick()
        compose.onNodeWithTag("sampleReceipt").assertIsDisplayed()
        compose.onNodeWithTag("day_$today").assertIsDisplayed().assert(hasContentDescription("✨", substring = true))
        compose.onNodeWithTag("sampleGuide").assertDoesNotExist()
        assertFalse(JournalStore(app).hasLaunchedBefore)
        assertFalse(UsageSharing(app).hasChoice)
        assertFalse(File(root, "records.json").exists())
        assertFalse(File(root, "draft.json").exists())
        scenario!!.onActivity {
            assertTrue(it.vm.journal.isEmpty)
            assertNull(it.vm.draft)
            assertFalse(JournalStore.exportJson(it.vm.journal).contains("✨"))
        }
        scenario!!.close()
        launch("onboardingStart")
        compose.onNodeWithTag("sampleGrid").performScrollToNode(hasTestTag("day_$today"))
        compose.onNodeWithTag("day_$today").assert(!hasContentDescription("✨", substring = true))
    }

    @Test fun canDismissHintsBrowseAnotherMonthAndStartFromUnfinishedPractice() {
        launch("onboardingStart")
        compose.onNodeWithTag("sampleDismiss").performScrollTo().performClick()
        compose.onNodeWithTag("samplePrevious").performClick()
        tapDay(LocalDate.of(2026, 2, 7))
        compose.onNodeWithTag("sampleNext").performClick()
        compose.onNodeWithTag("samplePractice").performClick()
        compose.onNodeWithTag("sampleGuide").assertDoesNotExist()
        compose.onNodeWithTag("sampleBack").performClick()
        compose.onNodeWithTag("samplePractice").performClick()
        compose.onNodeWithTag("sampleChip_☕").performClick()
        compose.onNodeWithTag("onboardingStart").performClick()
        if (BuildConfig.USAGE_SHARING_AVAILABLE) {
            waitFor("usageLater")
            compose.onNodeWithTag("usageLater").performClick()
        }
        scenario!!.onActivity {
            assertEquals(Screen.Editor, it.vm.screen)
            assertTrue(it.vm.journal.isEmpty)
            assertTrue(it.vm.draft!!.items.isEmpty())
            assertEquals(today, it.vm.draft!!.localDate)
        }
        assertTrue(JournalStore(app).hasLaunchedBefore)
        scenario!!.close()
        launch("level_RECORDS")
        compose.onNodeWithTag("onboardingStart").assertDoesNotExist()
        compose.onNodeWithTag("usageInvitation").assertDoesNotExist()
    }

    @Test fun restoredJournalAndNonActiveDraftBypassSampleEvenWithoutLaunchFlag() {
        val file = File(root.apply { mkdirs() }, "records.json")
        val original = """{"version":1,"days":[{"date":"2026-02-20","items":[{"id":"old","kind":"word","value":"kept"}]}]}"""
        file.writeText(original)
        launch("level_RECORDS")
        compose.onNodeWithTag("onboardingStart").assertDoesNotExist()
        assertEquals(original, file.readText())
        scenario!!.close()
        file.delete()
        JournalStore(app).apply {
            hasLaunchedBefore = false
            writeDrafts(DraftsFile(drafts = listOf(Draft(date = "2026-02-18", raw = "unsent"))))
        }
        launch("level_RECORDS")
        compose.onNodeWithTag("onboardingStart").assertDoesNotExist()
        assertTrue(File(root, "draft.json").readText().contains("unsent"))
    }
}
