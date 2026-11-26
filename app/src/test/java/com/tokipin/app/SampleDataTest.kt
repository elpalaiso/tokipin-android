package com.tokipin.app

import com.tokipin.app.data.SampleData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Debug sample data must equal the homepage generator (../tokipin trial.js) day for day. The web
 * output is dumped by node at test time (:app:dumpWebSamples, tools/web-samples.js).
 */
class SampleDataTest {
    @Test fun everyDayKindAndValueMatchesTheWeb() {
        val resource = javaClass.classLoader!!.getResource("web-samples.json")
        assumeTrue("node not available", resource != null)
        val web = Json.parseToJsonElement(resource!!.readText()).jsonObject
        val today = LocalDate.parse(web["today"]!!.jsonPrimitive.content)
        assertEquals(web["start"]!!.jsonPrimitive.content, SampleData.start(today).toString())

        val expected = web["days"]!!.jsonArray.associate { day ->
            day.jsonObject["date"]!!.jsonPrimitive.content to day.jsonObject["items"]!!.jsonArray.map {
                it.jsonObject["kind"]!!.jsonPrimitive.content + ":" + it.jsonObject["value"]!!.jsonPrimitive.content
            }
        }
        val actual = SampleData.journal(today).days.mapValues { (_, items) ->
            items.map { it.kind.name.lowercase() + ":" + it.value }
        }
        assertEquals(expected.keys.sorted(), actual.keys.sorted())
        expected.forEach { (date, items) -> assertEquals(date, items, actual[date]) }
    }

    @Test fun idsMatchIos() {
        val items = SampleData.itemsOn(LocalDate.of(2026, 11, 25))
        val day = LocalDate.of(2026, 11, 25).toEpochDay()
        items.forEachIndexed { i, e -> assertEquals("00000000-0000-4000-8000-%012x".format(day * 16 + i), e.id) }
    }
}
