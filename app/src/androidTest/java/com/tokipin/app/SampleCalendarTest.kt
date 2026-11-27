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

    private fun checkPersonalUntouched() {
        assertFalse(JournalStore(app).hasLaunchedBefore)
        assertFalse(UsageSharing(app).hasChoice)
        assertFalse(File(root, "records.json").exists())
        assertFalse(File(root, "draft.json").exists())
        scenario!!.onActivity {
            assertTrue(it.vm.journal.isEmpty)
            assertNull(it.vm.draft)
        }
    }

    @Test fun nextAloneRunsTheRealEditorAndAllEightBrowseActionsBeforeConsent() {
        launch("sampleNext")
        val res = AppLocale.resources(app)
        fun next(message: Int) {
            val text = res.getString(message)
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("sampleNext").performClick()
        }
        next(R.string.onboarding_emoji_tool)
        compose.onNodeWithContentDescription("Enter an emoji").assertIsFocused()
        next(R.string.editor_emoji_prompt)
        next(R.string.onboarding_save_guide)
        next(R.string.onboarding_day_swipe)
        next(R.string.onboarding_day_zoom)
        next(R.string.onboarding_records_swipe)
        lateinit var railMonth: java.time.YearMonth
        scenario!!.onActivity { railMonth = it.vm.sample!!.recordedMonth }
        next(R.string.onboarding_records_zoom)
        scenario!!.onActivity { assertEquals(railMonth, it.vm.sample!!.visibleMonth) }
        next(R.string.onboarding_month_scroll)
        next(R.string.onboarding_month_zoom)
        next(R.string.onboarding_year_open)
        next(R.string.onboarding_open_day)
        waitFor("onboardingStart")
        compose.onNodeWithText(res.getString(R.string.onboarding_invitation)).assertIsDisplayed()
        compose.onNodeWithTag("usageInvitation").assertDoesNotExist()
        checkPersonalUntouched()
        compose.onNodeWithTag("onboardingStart").performClick()
        if (BuildConfig.USAGE_SHARING_AVAILABLE) {
            waitFor("usageLater")
            compose.onNodeWithTag("usageLater").performClick()
        }
        scenario!!.onActivity {
            assertEquals(Screen.Editor, it.vm.screen)
            assertNull(it.vm.sample)
            assertTrue(it.vm.journal.isEmpty)
            assertTrue(it.vm.draft!!.items.isEmpty())
            assertEquals(today, it.vm.draft!!.localDate)
        }
    }

    @Test fun typedTextSurvivesNextAndFreeBrowsingCanReplayOrExit() {
        launch("sampleNext")
        val res = AppLocale.resources(app)
        compose.onNodeWithContentDescription("Enter a word").performTextInput("personal-looking practice")
        // Some system keyboards reopen a word as a composing suggestion. Next finishes that
        // composition first; an already committed value is ready for the checkmark immediately.
        compose.onNodeWithTag("sampleNext").performClick()
        val save = res.getString(R.string.onboarding_save_guide)
        compose.waitUntil(8_000) {
            compose.onAllNodes(hasTestTag("level_DAILY") or hasText(save)).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodes(hasText(save)).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("sampleNext").performClick()
        waitFor("level_DAILY")
        compose.waitForIdle()
        compose.onNodeWithTag("dayBody").performTouchInput {
            swipe(start = androidx.compose.ui.geometry.Offset(width * .72f, height * .3f),
                end = androidx.compose.ui.geometry.Offset(width * .22f, height * .3f), durationMillis = 500)
        }
        val zoomTip = res.getString(R.string.onboarding_day_zoom)
        compose.waitUntil(8_000) { compose.onAllNodes(hasText(zoomTip)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(zoomTip).assertIsDisplayed()
        compose.onNodeWithTag("sampleDismiss").performClick()
        compose.onNodeWithTag("sampleExplore").performClick()
        compose.onNodeWithTag("sampleNext").assertDoesNotExist()
        compose.onNodeWithTag("sampleEarlyStart").performClick()
        compose.onNodeWithTag("sampleSheetClose").performClick()
        compose.onNodeWithTag("sampleReplay").performClick()
        compose.onNodeWithText(res.getString(R.string.onboarding_day_swipe)).assertIsDisplayed()
        scenario!!.onActivity {
            val sample = it.vm.sample!!
            assertEquals("personal-looking practice", sample.journal.itemsOn(today).last().value)
            assertEquals(1, sample.journal.itemsOn(today).count { e -> e.value == "personal-looking practice" })
            assertFalse(it.vm.journal.days.values.flatten().any { e -> e.value.contains("practice") })
        }
        checkPersonalUntouched()
    }

    @Test fun directPinchesAndCalendarDragAdvanceTheirOwnCues() {
        launch("sampleNext")
        val res = AppLocale.resources(app)
        fun waitGuide(message: Int) {
            val label = res.getString(message)
            compose.waitUntil(8_000) { compose.onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty() }
        }
        fun pinch(level: String, inward: Boolean) {
            compose.onNodeWithTag("level_$level").performTouchInput {
                val near = minOf(width, height) * .06f
                val far = near * 3f
                val (from, to) = if (inward) far to near else near to far
                val spreadFrom = androidx.compose.ui.geometry.Offset(from, from)
                val spreadTo = androidx.compose.ui.geometry.Offset(to, to)
                pinch(center - spreadFrom, center - spreadTo, center + spreadFrom, center + spreadTo, 300)
            }
        }
        repeat(3) { compose.onNodeWithTag("sampleNext").performClick() }
        waitGuide(R.string.onboarding_day_swipe)
        compose.onNodeWithTag("sampleNext").performClick()
        waitGuide(R.string.onboarding_day_zoom)
        pinch("DAILY", inward = true)
        waitGuide(R.string.onboarding_records_swipe)
        lateinit var previousMonth: java.time.YearMonth
        scenario!!.onActivity { previousMonth = it.vm.sample!!.recordedMonth.minusMonths(1) }
        compose.onNodeWithTag("rail_$previousMonth").performClick()
        waitGuide(R.string.onboarding_records_zoom)
        pinch("RECORDS", inward = true)
        waitGuide(R.string.onboarding_month_scroll)
        compose.onNodeWithTag("calendarList").performTouchInput { swipeDown(startY = height * .2f, endY = height * .7f, durationMillis = 400) }
        waitGuide(R.string.onboarding_month_zoom)
        pinch("MONTH", inward = true)
        waitGuide(R.string.onboarding_year_open)
        pinch("YEAR", inward = false)
        waitGuide(R.string.onboarding_open_day)
        checkPersonalUntouched()
    }

    @Test fun openingASearchResultDuringTheTourShowsTheDailyGuideForThatResult() {
        launch("sampleNext")
        lateinit var word: String
        scenario!!.onActivity {
            val sample = it.vm.sample!!
            sample.leaveEditor()
            sample.changeLevel(com.tokipin.app.rules.Level.MONTH)
            word = sample.journal.days.values.asSequence().flatten().first { e -> e.kind == com.tokipin.app.data.EntryKind.WORD }.value
            sample.openSearch()
        }
        compose.onNodeWithTag("searchField").performTextInput(word)
        val result = hasText(word) and hasClickAction() and hasAnyAncestor(hasTestTag("searchResults"))
        compose.waitUntil(10_000) { compose.onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(result)[0].performClick()
        waitFor("level_DAILY")
        compose.onNodeWithText(AppLocale.resources(app).getString(R.string.onboarding_day_swipe)).assertIsDisplayed()
        compose.onNodeWithTag("sampleNext").performClick()
        compose.onNodeWithText(AppLocale.resources(app).getString(R.string.onboarding_day_zoom)).assertIsDisplayed()
        checkPersonalUntouched()
    }

    @Test fun koreanImeCompositionInEmojiModeDoesNotAdvanceToSaveUntilCommitted() {
        launch("sampleNext")
        compose.onNodeWithTag("sampleNext").performClick()
        compose.onNodeWithContentDescription("Enter an emoji").assertIsFocused()
        scenario!!.onActivity {
            val input = requireNotNull(it.currentFocus?.onCreateInputConnection(android.view.inputmethod.EditorInfo()))
            input.setComposingText("✨ㅎ", 1)
        }
        val res = AppLocale.resources(app)
        compose.waitUntil(8_000) {
            var hasInput = false
            scenario!!.onActivity { hasInput = it.vm.sample!!.draft!!.raw.contains("✨") }
            hasInput
        }
        // Gboard can commit and filter the supplied composition before the UI thread renders
        // another frame. Both paths must lead to the same single, valid emoji.
        val inputHelp = res.getString(R.string.editor_emoji_prompt)
        if (compose.onAllNodes(hasText(inputHelp)).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("sampleNext").performClick()
        compose.onNodeWithText(res.getString(R.string.onboarding_save_guide)).assertIsDisplayed()
        compose.onNodeWithTag("sampleNext").performClick()
        waitFor("level_DAILY")
        scenario!!.onActivity {
            assertEquals("✨", it.vm.sample!!.journal.itemsOn(today).last().value)
        }
        checkPersonalUntouched()
    }

    @Test fun chosenTimeIsAddedOnceAndNextThenSavesTheCurrentNativeDraft() {
        launch("sampleNext")
        val res = AppLocale.resources(app)
        compose.onNodeWithContentDescription(res.getString(R.string.kind_time)).performClick()
        scenario!!.onActivity { it.vm.sample!!.updateRaw("17:35") }
        compose.onNodeWithTag("sampleNext").performClick()
        compose.onNodeWithText(res.getString(R.string.onboarding_save_guide)).assertIsDisplayed()
        compose.onNodeWithTag("sampleNext").performClick()
        waitFor("level_DAILY")
        scenario!!.onActivity {
            val sample = it.vm.sample!!
            assertEquals(sample.tour!!.starter + sample.journal.itemsOn(today).last(), sample.journal.itemsOn(today))
            assertEquals("17:35", sample.journal.itemsOn(today).last().value)
        }
        checkPersonalUntouched()
    }

    @Test fun developerReplayReturnsToThePersonalSettingsWithTheSameDataAndChoice() {
        val original = """{"version":1,"days":[{"date":"2026-02-20","items":[{"id":"personal","kind":"word","value":"kept"}]}]}"""
        root.mkdirs()
        val records = File(root, "records.json").apply { writeText(original) }
        val draft = Draft(date = "2026-02-18", raw = "private unfinished words")
        val store = JournalStore(app)
        store.hasLaunchedBefore = true
        store.writeDrafts(DraftsFile(drafts = listOf(draft)))
        val draftFile = File(root, "draft.json")
        val draftBytes = draftFile.readBytes()
        val sharing = app.getSharedPreferences("tokipin.usage", Context.MODE_PRIVATE)
        sharing.edit().putBoolean("enabled", false).commit()
        val res = AppLocale.resources(app)
        val launcher = compose.onNodeWithText(res.getString(R.string.debug_replay_onboarding))
        launch("level_RECORDS")
        scenario!!.onActivity { it.vm.openSettings() }

        fun openReplay() {
            launcher.performScrollTo().performClick()
            waitFor("sampleNext")
            compose.onNodeWithText(res.getString(R.string.onboarding_emoji_tool)).assertIsDisplayed()
            compose.onNodeWithTag("usageInvitation").assertDoesNotExist()
        }
        fun checkReturned() {
            compose.waitUntil(8_000) { compose.onAllNodes(hasText(res.getString(R.string.debug_replay_onboarding))).fetchSemanticsNodes().isNotEmpty() }
            launcher.assertIsDisplayed()
            scenario!!.onActivity {
                assertTrue(it.vm.settingsOpen)
                assertNull(it.vm.sample)
                assertFalse(it.vm.usagePromptOpen)
                assertEquals(Screen.Browse, it.vm.screen)
                assertEquals(com.tokipin.app.ui.AppViewModel.ListEntry.Restore, it.vm.entry)
                assertEquals("kept", it.vm.journal.itemsOn(LocalDate.parse("2026-02-20")).single().value)
            }
            assertEquals(original, records.readText())
            assertArrayEquals(draftBytes, draftFile.readBytes())
            assertTrue(store.hasLaunchedBefore)
            assertEquals(mapOf("enabled" to false), sharing.all)
        }

        openReplay()
        compose.onNodeWithTag("sampleNext").performClick()
        compose.onNodeWithTag("sampleNext").performClick()
        compose.onNodeWithTag("sampleNext").performClick()
        waitFor("level_DAILY")
        compose.onNodeWithTag("developerReplayClose").performClick()
        checkReturned()

        val choices = if (BuildConfig.USAGE_SHARING_AVAILABLE) listOf("share", "later", "back") else listOf("unconfigured")
        for (choice in choices) {
            openReplay()
            compose.onNodeWithTag("sampleDismiss").performClick()
            compose.onNodeWithTag("sampleEarlyStart").performClick()
            compose.onNodeWithTag("onboardingStart").performClick()
            if (BuildConfig.USAGE_SHARING_AVAILABLE) {
                waitFor("usageInvitation")
                when (choice) {
                    "share" -> compose.onNodeWithText(res.getString(R.string.usage_share)).performClick()
                    "later" -> compose.onNodeWithTag("usageLater").performClick()
                    "back" -> InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                }
            }
            checkReturned()
        }
    }

    @Test fun developerReplayCanCloseWithActivePersonalDraftAndRetainsTheEditor() {
        val personal = Draft(date = "2026-02-18", raw = "personal draft")
        val store = JournalStore(app)
        store.hasLaunchedBefore = true
        store.writeDrafts(DraftsFile(personal.date, listOf(personal)))
        app.getSharedPreferences("tokipin.usage", Context.MODE_PRIVATE).edit().putBoolean("enabled", false).commit()
        launch("editorTopBar")
        scenario!!.onActivity { it.vm.openSettings() }
        val label = AppLocale.resources(app).getString(R.string.debug_replay_onboarding)
        compose.onNodeWithText(label).performScrollTo().performClick()
        waitFor("sampleNext")
        compose.onNodeWithContentDescription("Enter a word").performTextInput("throwaway")
        compose.onNodeWithTag("developerReplayClose").performClick()
        compose.waitUntil(8_000) { compose.onAllNodes(hasText(label)).fetchSemanticsNodes().isNotEmpty() }
        scenario!!.onActivity {
            assertNull(it.vm.sample)
            assertTrue(it.vm.settingsOpen)
            assertEquals(Screen.Editor, it.vm.screen)
            assertEquals(personal, it.vm.draft)
        }
        assertEquals(personal, kotlinx.coroutines.runBlocking { store.loadDrafts() }.drafts.single())
    }

    @Test fun restoredJournalBypassesSampleEvenWithoutLaunchFlag() {
        val file = File(root.apply { mkdirs() }, "records.json")
        val original = """{"version":1,"days":[{"date":"2026-02-20","items":[{"id":"old","kind":"word","value":"kept"}]}]}"""
        file.writeText(original)
        launch("level_RECORDS")
        compose.onNodeWithTag("onboardingStart").assertDoesNotExist()
        assertEquals(original, file.readText())
    }

    @Test fun restoredActiveDraftOpensItsPersonalEditor() {
        val restored = Draft(date = "2026-02-18", raw = "unsent")
        JournalStore(app).writeDrafts(DraftsFile(restored.date, listOf(restored)))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(8_000) {
            var ready = false
            scenario!!.onActivity { ready = it.vm.screen == Screen.Editor }
            ready
        }
        scenario!!.onActivity {
            assertEquals(restored, it.vm.draft)
            assertNull(it.vm.sample)
        }
    }

    @Test fun restoredNonActiveDraftBypassesSampleAndStaysOnItsOwnDay() {
        JournalStore(app).writeDrafts(DraftsFile(drafts = listOf(Draft(date = "2026-02-18", raw = "unsent"))))
        launch("level_RECORDS")
        compose.onNodeWithTag("onboardingStart").assertDoesNotExist()
        assertTrue(File(root, "draft.json").readText().contains("unsent"))
    }
}
