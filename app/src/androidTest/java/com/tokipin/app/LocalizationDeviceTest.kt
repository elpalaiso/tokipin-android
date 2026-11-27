package com.tokipin.app

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The 17 languages on a device: which resources a preference list selects, plurals, and switching. */
@RunWith(AndroidJUnit4::class)
class LocalizationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun close() { scenario?.close(); TestLocale.set("ko") }

    /** Resources the app would use for a device whose language list is [tags]. */
    private fun resFor(tags: String): android.content.res.Resources {
        AppLocale.override = null
        val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tags)) }
        return AppLocale.wrap(context.createConfigurationContext(config)).resources
    }

    @Test fun preferenceListsPickTheIntendedLanguageAndRegionalVariant() {
        assertEquals("es-419", AppLocale.resolve(LocaleList.forLanguageTags("it-IT,es-MX,en-US")))
        assertEquals("fr", AppLocale.resolve(LocaleList.forLanguageTags("it-IT,fr-CA")))
        assertEquals("en", AppLocale.resolve(LocaleList.forLanguageTags("it-IT,pl-PL")))
        assertEquals("zh-Hant-HK", AppLocale.resolve(LocaleList.forLanguageTags("zh-HK,en")))
        assertEquals("ko", AppLocale.resolve(LocaleList.forLanguageTags("ko-KR,ja-JP")))
        // The chosen tag reaches the right resource folder, regional variants included.
        assertEquals("No se ha podido exportar. Vuelve a intentarlo.", resFor("es-ES").getString(R.string.settings_export_failed))
        assertEquals("No se pudo exportar. Vuelve a intentarlo.", resFor("es-AR").getString(R.string.settings_export_failed))
        assertEquals("还没有记录。", resFor("zh-CN").getString(R.string.home_empty))
        assertEquals("還沒有紀錄。", resFor("zh-TW").getString(R.string.home_empty))
        assertEquals("未有記錄。", resFor("zh-Hant-HK").getString(R.string.home_empty))
        assertEquals("Vibration", resFor("it-IT,pl-PL").getString(R.string.settings_haptics))
        assertEquals("진동", resFor("ko-KR").getString(R.string.settings_haptics))
        // Even without AppLocale (the system's own resource lookup), an unsupported language gets English.
        val plain = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocales(LocaleList.forLanguageTags("it-IT")) })
        assertEquals("Vibration", plain.resources.getString(R.string.settings_haptics))
        assertEquals("Getaran", resFor("in-ID").getString(R.string.settings_haptics))
    }

    @Test fun pluralsFollowEachLanguage() {
        val en = resFor("en")
        assertEquals("Delete 1 item from this day", en.getQuantityString(R.plurals.delete_day, 1, 1))
        assertEquals("Delete all 5 items from this day", en.getQuantityString(R.plurals.delete_day, 5, 5))
        val ru = resFor("ru")
        assertEquals("Удалить 1 элемент за этот день", ru.getQuantityString(R.plurals.delete_day, 1, 1))
        assertEquals("Удалить 2 элемента за этот день", ru.getQuantityString(R.plurals.delete_day, 2, 2))
        assertEquals("Удалить 5 элементов за этот день", ru.getQuantityString(R.plurals.delete_day, 5, 5))
        assertEquals("Удалить 21 элемент за этот день", ru.getQuantityString(R.plurals.delete_day, 21, 21))
        assertEquals("この日の項目2件を削除", resFor("ja").getQuantityString(R.plurals.delete_day, 2, 2))
        assertEquals("이날 기록 0개 삭제", resFor("ko").getQuantityString(R.plurals.delete_day, 0, 0))
    }

    /** TP-0231/0232: the count on the day-delete button is items (fragments), not notes. */
    @Test fun deleteDayCountsItemsInEnglish() {
        val en = resFor("en-US")
        assertEquals("Delete all 3 items from this day", en.getQuantityString(R.plurals.delete_day, 3, 3))
        assertEquals("Deleted 3 items from this day.", en.getQuantityString(R.plurals.day_deleted, 3, 3))
        assertEquals("Delete 1 item from this day", en.getQuantityString(R.plurals.delete_day, 1, 1))
        assertEquals("ลบ 3 รายการของวันที่เลือก", resFor("th").getQuantityString(R.plurals.delete_day, 3, 3))
    }

    @Test fun britishEnglishKeepsItsOwnHourCycleAndDates() {
        val gb = AppLocale.formatFor(java.util.Locale.forLanguageTag("en"), java.util.Locale.forLanguageTag("en-GB"))
        val ctx = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag("en-GB")) })
        val us = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag("en-US")) })
        val auto = android.provider.Settings.System.getString(context.contentResolver, android.provider.Settings.System.TIME_12_24) == null
        if (auto) {
            assertEquals(true, android.text.format.DateFormat.is24HourFormat(ctx))
            assertEquals(false, android.text.format.DateFormat.is24HourFormat(us))
        }
        // Day before month (older CLDR on API 28 adds a comma after the weekday).
        val text = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(gb).format(java.time.LocalDate.of(2026, 11, 25))
        assertTrue(text, Regex("^Wednesday,? 25 November 2026$").matches(text))
    }

    private fun item(kind: String, value: String, id: String) = """{"id":"$id","kind":"$kind","value":"$value"}"""

    @Test fun switchingTheLanguageKeepsRecordsAndTheDraftAsWritten() {
        // Switching the per-app language while the app runs exists from Android 13.
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        com.tokipin.app.rules.AppClock.override = java.time.LocalDate.of(2026, 11, 26)
        File(context.filesDir, "tokipin").apply { deleteRecursively(); mkdirs() }.resolve("records.json").writeText(
            """{"version":1,"days":[{"date":"2026-11-25","updatedAt":"2026-11-25T10:00:00Z","items":[${item("word", "바닷가", "s1")},${item("place", "연남동", "s2")}]}]}""",
        )
        context.getSharedPreferences("tokipin.settings", Context.MODE_PRIVATE).edit().putBoolean("launched", true).commit()
        TestLocale.set("ko")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("level_RECORDS").fetchSemanticsNode() }.isSuccess }
        compose.onNodeWithTag("addRecord").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("단어 입력").performTextInput("파도")
        compose.waitForIdle(); Thread.sleep(500) // the draft is written

        TestLocale.set("en") // the activity is recreated in English
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithContentDescription("Enter a word").fetchSemanticsNode() }.isSuccess }
        compose.onNodeWithText("파도").assertExists() // typed text untouched
        compose.onNodeWithContentDescription("Save note").assertExists()
        var saved = ""
        scenario!!.onActivity { saved = it.vm.journal.itemsOn(java.time.LocalDate.parse("2026-11-25")).joinToString { e -> e.value } }
        assertEquals("바닷가, 연남동", saved) // saved records keep their own words
        assertTrue(File(context.filesDir, "tokipin/draft.json").readText().contains("파도"))
    }
}
