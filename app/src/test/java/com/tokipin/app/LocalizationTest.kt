package com.tokipin.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** The 17 app languages: locale matching like the web, and complete, well-formed resources. */
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
