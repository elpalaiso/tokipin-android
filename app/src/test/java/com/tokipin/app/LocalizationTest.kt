package com.tokipin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** The 17 app languages: locale matching like the web, and complete, well-formed resources. */
private fun fullLabelOf(d: java.time.LocalDate, l: Locale): String = with(d) { com.tokipin.app.ui.fullLabelFor(this, l) }

class LocalizationTest {
    private fun m(tag: String) = AppLocale.match(Locale.forLanguageTag(tag))

    @Test fun regionalVariantsAreKeptApart() {
        assertEquals("es-ES", m("es"))
        assertEquals("es-ES", m("es-ES"))
        assertEquals("es-419", m("es-419"))
        assertEquals("es-419", m("es-MX"))
        assertEquals("es-419", m("es-US"))
        assertEquals("zh-Hans", m("zh"))
        assertEquals("zh-Hans", m("zh-CN"))
        assertEquals("zh-Hans", m("zh-Hans-HK"))
        assertEquals("zh-Hant-TW", m("zh-TW"))
        assertEquals("zh-Hant-TW", m("zh-Hant"))
        assertEquals("zh-Hant-HK", m("zh-HK"))
        assertEquals("zh-Hant-HK", m("zh-Hant-HK"))
        assertEquals("zh-Hant-HK", m("zh-MO"))
        assertEquals("pt-BR", m("pt-PT"))
        assertEquals("id", m("id-ID"))
        assertEquals("id", m("in-ID"))
        assertEquals("ko", m("ko-KR"))
        assertEquals("en", m("en-GB"))
        assertEquals(null, m("it-IT"))
        assertEquals(17, AppLocale.supported.size)
    }

    @Test fun datesUseTheDeviceRegionWhenItSpeaksTheAppLanguage() {
        val l = { a: String, d: String -> AppLocale.formatFor(Locale.forLanguageTag(a), Locale.forLanguageTag(d)).toLanguageTag() }
        assertEquals("en-GB", l("en", "en-GB"))
        assertEquals("en", l("en", "it-IT")) // device language not supported: English words, no Italian region
        assertEquals("es-MX", l("es-419", "es-MX"))
        assertEquals("zh-Hant-HK", l("zh-Hant-HK", "zh-Hans-CN")) // another script: keep the app's own
        assertEquals("ko-KR", l("ko", "ko-KR"))
        // British English formats with the British pattern.
        val gb = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag("en-GB"))
        assertEquals("25 Nov 2026", gb.format(java.time.LocalDate.of(2026, 11, 25)))
    }

    @Test fun searchMatchesLocalizedKindNamesAndTheWrittenDate() {
        val day = java.time.LocalDate.of(2026, 11, 25)
        val journal = com.tokipin.app.data.Journal().withDay(day, listOf(
            com.tokipin.app.data.Entry(kind = com.tokipin.app.data.EntryKind.WORD, value = "Meer"),
            com.tokipin.app.data.Entry(kind = com.tokipin.app.data.EntryKind.PLACE, value = "Hafen"),
        ))
        val german = mapOf(
            com.tokipin.app.data.EntryKind.WORD to "Wort", com.tokipin.app.data.EntryKind.EMOJI to "Emoji",
            com.tokipin.app.data.EntryKind.PLACE to "Ort", com.tokipin.app.data.EntryKind.TIME to "Uhrzeit",
        )
        // "Ort", "ort", "ORT": every place of the journal.
        listOf("Ort", "ort", "ORT").forEach { q ->
            assertEquals(q, listOf("Hafen"), com.tokipin.app.ui.search(journal, q, german).map { it.entry.value })
        }
        // The date as the app writes it, including the weekday in the app language.
        assertEquals(2, com.tokipin.app.ui.search(journal, "2026.11.25", german).size)
        val weekday = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, AppLocale.format)
        assertEquals(2, com.tokipin.app.ui.search(journal, "($weekday)", german).size)
    }

    @Test fun turkishCaseAndShownTimesAreSearchable() {
        val tr = Locale.forLanguageTag("tr")
        val day = java.time.LocalDate.of(2026, 11, 26)
        val journal = com.tokipin.app.data.Journal().withDay(day, listOf(
            com.tokipin.app.data.Entry(kind = com.tokipin.app.data.EntryKind.PLACE, value = "İstanbul"),
            com.tokipin.app.data.Entry(kind = com.tokipin.app.data.EntryKind.TIME, value = "18:45"),
        ))
        val names = mapOf(
            com.tokipin.app.data.EntryKind.WORD to "Kelime", com.tokipin.app.data.EntryKind.EMOJI to "Emoji",
            com.tokipin.app.data.EntryKind.PLACE to "Yer", com.tokipin.app.data.EntryKind.TIME to "Saat",
        )
        val shown = { e: com.tokipin.app.data.Entry -> if (e.value == "18:45") "6:45 PM" else e.value }
        listOf("İstanbul", "istanbul", "İSTANBUL").forEach { q ->
            assertEquals(q, listOf("İstanbul"), com.tokipin.app.ui.search(journal, q, names, tr, shown).map { it.entry.value })
        }
        assertEquals(listOf("İstanbul"), com.tokipin.app.ui.search(journal, "YER", names, tr, shown).map { it.entry.value })
        // Dotted and dotless i are different letters in Turkish: kir and kır never find each other.
        val words = com.tokipin.app.data.Journal().withDay(day, listOf(
            com.tokipin.app.data.Entry(kind = com.tokipin.app.data.EntryKind.WORD, value = "kir"),
            com.tokipin.app.data.Entry(kind = com.tokipin.app.data.EntryKind.WORD, value = "kır"),
        ))
        assertEquals(listOf("kir"), com.tokipin.app.ui.search(words, "kir", names, tr).map { it.entry.value })
        assertEquals(listOf("kır"), com.tokipin.app.ui.search(words, "kır", names, tr).map { it.entry.value })
        assertEquals(listOf("kır"), com.tokipin.app.ui.search(words, "KIR", names, tr).map { it.entry.value })
        // The time as stored and as shown.
        assertEquals(listOf("18:45"), com.tokipin.app.ui.search(journal, "18:45", names, tr, shown).map { it.entry.value })
        assertEquals(listOf("18:45"), com.tokipin.app.ui.search(journal, "6:45 pm", names, tr, shown).map { it.entry.value })
        // Formatters put a narrow no-break space (U+202F) in "6:45 PM"; a keyboard types an ordinary one.
        for (tag in listOf("en-GB", "en-US", "fr", "es-419")) {
            val l = Locale.forLanguageTag(tag)
            val pattern = java.time.format.DateTimeFormatter.ofPattern("h:mm\u202Fa", l)
            val onScreen = pattern.format(java.time.LocalTime.of(18, 45))
            val typed = onScreen.replace('\u202F', ' ')
            val show = { e: com.tokipin.app.data.Entry -> if (e.value == "18:45") onScreen else e.value }
            assertEquals(tag, listOf("18:45"), com.tokipin.app.ui.search(journal, typed, names, l, show).map { it.entry.value })
            assertEquals(tag, listOf("18:45"), com.tokipin.app.ui.search(journal, onScreen, names, l, show).map { it.entry.value })
        }
        // The date as stored and as written in the app.
        assertEquals(2, com.tokipin.app.ui.search(journal, "2026-11-26", names, tr, shown).size)
        assertEquals(2, com.tokipin.app.ui.search(journal, fullLabelOf(day, tr), names, tr, shown).size)
    }

    private val res = File("src/main/res")
    private val folders = mapOf(
        "en" to "values", "ja" to "values-ja", "de" to "values-de", "fr" to "values-fr", "id" to "values-in",
        "vi" to "values-vi", "tr" to "values-tr", "ru" to "values-ru", "hi" to "values-hi", "th" to "values-th",
        "pt-BR" to "values-pt-rBR", "es-ES" to "values-es-rES", "es-419" to "values-b+es+419", "zh-Hans" to "values-b+zh+Hans",
        "zh-Hant-TW" to "values-b+zh+Hant+TW", "zh-Hant-HK" to "values-b+zh+Hant+HK",
    )

    /** name → (is plural, list of texts) */
    private fun read(folder: String): Map<String, Pair<Boolean, List<String>>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(res, "$folder/strings.xml"))
        val out = linkedMapOf<String, Pair<Boolean, List<String>>>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val e = strings.item(i) as org.w3c.dom.Element
            if (e.getAttribute("translatable") == "false") continue
            out[e.getAttribute("name")] = false to listOf(e.textContent)
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val e = plurals.item(i) as org.w3c.dom.Element
            val items = e.getElementsByTagName("item")
            out[e.getAttribute("name")] = true to (0 until items.length).map { items.item(it).textContent }
        }
        return out
    }

    private fun args(text: String) = Regex("%(\\d)\\$([sd])").findAll(text).map { it.value }.toSortedSet()

    @Test fun theDefaultFolderIsEnglishAndKoreanHasItsOwn() {
        // A language outside the 17 (or anything read without AppLocale) falls back to English.
        val default = File(res, "values/strings.xml").readText()
        assertTrue(default.contains("<string name=\"settings_haptics\">Vibration</string>"))
        assertTrue(File(res, "values-ko/strings.xml").readText().contains("<string name=\"settings_haptics\">진동</string>"))
        assertEquals("en", AppLocale.FALLBACK)
    }

    @Test fun everyLanguageHasEveryUserFacingStringWithTheSameArguments() {
        val base = read("values-ko").filterKeys { !it.startsWith("debug_") }
        // Korean itself has every string the default (English) has: no Korean phrase falls back to English.
        val english = read("values").filterKeys { !it.startsWith("debug_") }
        assertTrue("Korean missing: ${english.keys - base.keys}", (english.keys - base.keys).isEmpty())
        base.filterValues { it.first }.forEach { (name, v) -> assertTrue("ko $name plural has other", v.second.isNotEmpty()) }
        assertEquals("folders = locales_config minus ko", AppLocale.supported - "ko", folders.keys.toList().let { k -> AppLocale.supported.filter { it in k } })
        val config = File(res, "xml/locales_config.xml").readText()
        AppLocale.supported.forEach { assertTrue("$it in locales_config", config.contains("android:name=\"$it\"")) }
        for ((lang, folder) in folders) {
            val tr = read(folder)
            val missing = base.keys - tr.keys
            assertTrue("$lang missing: $missing", missing.isEmpty())
            for ((name, value) in base) {
                val (plural, texts) = tr.getValue(name)
                assertEquals("$lang $name plural", value.first, plural)
                val expected = args(value.second.first())
                texts.forEach { t ->
                    // A plural item may leave out the count (e.g. "one" written as a word); never add one.
                    val got = args(t)
                    if (plural) assertTrue("$lang $name: $t", expected.containsAll(got) && got.containsAll(expected - "%1\$d" - "%3\$d"))
                    else assertEquals("$lang $name: $t", expected, got)
                }
                if (plural) assertTrue("$lang $name has other", texts.isNotEmpty())
            }
        }
    }
}
