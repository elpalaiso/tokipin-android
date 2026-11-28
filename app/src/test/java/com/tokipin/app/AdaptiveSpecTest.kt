package com.tokipin.app

import com.tokipin.app.rules.Adaptive
import com.tokipin.app.rules.Level
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * UX v0.4 groups (paneLayout, paneRender, paneNavigation, paneTransition, paneChrome, yearColumns)
 * run against [Adaptive], the port of ../tokipin/native-spec/v0.4/adaptive-layout.cjs. Each result
 * is turned into the JSON shape the reference returns and compared with the case as a whole, the
 * way tests/adaptive-layout.test.cjs does.
 */
class AdaptiveSpecTest {
    private val spec: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("Tokipin-Native-UX-v0.4.json")!!.readText(),
    ).jsonObject
    private val tests = spec["tests"]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.s(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.sOrNull(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.b(key: String, default: Boolean = false) = this[key]?.jsonPrimitive?.booleanOrNull ?: default
    private fun level(name: String) = Level.fromSpec(name)

    @Test fun numbersMatchTheDocument() {
        val adaptive = spec["adaptive"]!!.jsonObject
        assertEquals(Adaptive.TWO_PANE_MIN_WIDTH, adaptive["twoPaneMinWidth"]!!.jsonPrimitive.float)
        assertEquals(Adaptive.TWO_PANE_MIN_HEIGHT, adaptive["twoPaneMinHeight"]!!.jsonPrimitive.float)
        assertEquals(Adaptive.REGION_MIN, adaptive["regionMin"]!!.jsonPrimitive.float)
        assertEquals(Adaptive.PANE_MAX_WIDTH, adaptive["paneMaxWidth"]!!.jsonPrimitive.float)
        assertEquals(Adaptive.YEAR_FOUR_COLUMNS_FROM, adaptive["yearFourColumnsFrom"]!!.jsonPrimitive.float)
        assertEquals(com.tokipin.app.ui.CONTENT_MAX_WIDTH.value, Adaptive.PANE_MAX_WIDTH)
    }

    @Test fun everyV04CaseMatchesTheReference() {
        val groups = setOf("paneLayout", "paneRender", "paneNavigation", "paneTransition", "paneChrome", "yearColumns")
        val added = tests.filter { it.s("group") in groups }
        assertEquals(85, added.size)
        val failures = added.mapNotNull { case ->
            val actual = runCatching { run(case) }.getOrElse { return@mapNotNull "${case.s("id")}: ${it.message}" }
            if (same(actual, case["expected"]!!)) null else "${case.s("id")}:\n  expected ${case["expected"]}\n  actual   $actual"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun run(case: JsonObject): JsonElement {
        val g = case["given"]!!.jsonObject
        val e = case["expected"]!!.jsonObject
        return when (case.s("group")) {
            "paneLayout" -> layout(g).json()
            "paneRender" -> render(g.s("mode"), g)
            "paneNavigation" -> navigation(g)
            "paneTransition" -> transition(g, e)
            "paneChrome" -> chrome(g)
            "yearColumns" -> buildJsonObject { put("columns", Adaptive.yearColumns(g["paneWidth"]!!.jsonPrimitive.float)) }
            else -> error("group ${case.s("group")}")
        }
    }

    private fun layout(g: JsonObject): Adaptive.Layout {
        val fold = g["fold"]?.takeUnless { it is JsonNull }?.jsonObject?.let {
            Adaptive.Fold(
                if (it.s("orientation") == "vertical") Adaptive.FoldOrientation.VERTICAL else Adaptive.FoldOrientation.HORIZONTAL,
                it["start"]!!.jsonPrimitive.float, it["end"]!!.jsonPrimitive.float, it.b("separating"),
            )
        }
        return Adaptive.layout(g["width"]!!.jsonPrimitive.float, g["height"]!!.jsonPrimitive.float, fold, g.b("journalEmpty"), g.b("sampleTour"))
    }

    private val Adaptive.Layout.mode get() = when (this) {
        is Adaptive.Layout.Single -> "single"
        is Adaptive.Layout.SideBySide -> "sideBySide"
        is Adaptive.Layout.Stacked -> "stacked"
    }

    private fun Adaptive.Rect.json() = buildJsonObject { put("x", x); put("y", y); put("width", width); put("height", height) }

    private fun Adaptive.Layout.json() = buildJsonObject {
        put("mode", mode)
        when (val l = this@json) {
            is Adaptive.Layout.Single -> put("content", l.content.json())
            is Adaptive.Layout.SideBySide -> { put("calendar", l.calendar.json()); put("day", l.day.json()) }
            is Adaptive.Layout.Stacked -> { put("day", l.day.json()); put("calendar", l.calendar.json()) }
        }
    }

    private fun render(mode: String, s: JsonObject): JsonObject {
        val levelName = s.s("level")
        val selected = LocalDate.parse(s.s("selectedDay"))
        val searching = s.b("searching")
        val highlight = s.sOrNull("searchHighlight") != null
        val editor = s.sOrNull("editorDay")?.let(LocalDate::parse)
        if (mode == "single") {
            return when (val screen = Adaptive.singleScreen(level(levelName), selected, searching, highlight, editor)) {
                is Adaptive.SingleScreen.Editor -> buildJsonObject { put("screen", "editor"); put("date", screen.date.toString()) }
                Adaptive.SingleScreen.Search -> buildJsonObject { put("screen", "search") }
                is Adaptive.SingleScreen.Day -> buildJsonObject {
                    put("screen", "day"); put("date", screen.date.toString()); put("returnToSearchResults", screen.returnToSearchResults)
                }
                is Adaptive.SingleScreen.Calendar -> buildJsonObject { put("screen", screen.level.specName) }
            }
        }
        val panes = Adaptive.paneScreens(level(levelName), level(s.s("dayReturnLevel")), selected, searching, highlight, editor)
        return buildJsonObject {
            put("calendar", panes.calendar?.specName ?: "search")
            put("calendarInert", panes.calendarInert)
            put("day", if (panes.dayIsEditor) "editor" else "day")
            put("dayDate", panes.dayDate.toString())
        }
    }

    private fun navigation(g: JsonObject): JsonObject {
        val st = g["state"]!!.jsonObject
        val state = Adaptive.PaneState(
            level(st.s("level")), level(st.s("dayReturnLevel")), LocalDate.parse(st.s("selectedDay")),
            st.sOrNull("recordedMonth")?.let(YearMonth::parse),
        )
        val a = g["action"]!!.jsonObject
        val action = when (a.s("type")) {
            "tapDate" -> Adaptive.PaneAction.TapDate(LocalDate.parse(a.s("date")))
            "weekRailTap" -> Adaptive.PaneAction.WeekRailTap(LocalDate.parse(a.s("date")))
            "daySwipe" -> Adaptive.PaneAction.DaySwipe(a["days"]!!.jsonPrimitive.long)
            "viewMenu" -> Adaptive.PaneAction.ViewMenu(level(a.s("level")))
            "tapMonth" -> Adaptive.PaneAction.TapMonth(LocalDate.parse(a.s("selectedDayAfter")))
            "recordedMonthCommit" -> Adaptive.PaneAction.RecordedMonthCommit(YearMonth.parse(a.s("month")))
            "today" -> Adaptive.PaneAction.Today(LocalDate.parse(a.s("today")))
            "pinch" -> Adaptive.PaneAction.Pinch(a.s("pane") == "calendar", a["scaleAtRelease"]!!.jsonPrimitive.float, a.sOrNull("anchorDay")?.let(LocalDate::parse))
            else -> error("action ${a.s("type")}")
        }
        val r = Adaptive.navigate(state, action, hapticsEnabled = g.b("hapticsEnabled", true))
        return buildJsonObject {
            put("level", r.state.level.specName)
            put("dayReturnLevel", r.state.dayReturnLevel.specName)
            put("selectedDay", r.state.selectedDay.toString())
            r.state.recordedMonth?.let { put("recordedMonth", it.toString()) }
            put("calendarLevel", r.calendarLevel.specName)
            put("appTicks", r.appTicks)
        }
    }

    /** A window change only re-reads the same state for the new layout: nothing is rewritten. */
    private fun transition(g: JsonObject, e: JsonObject): JsonObject {
        val state = g["state"]!!.jsonObject
        val before = state.toString()
        val from = layout(g["from"]!!.jsonObject).mode
        val to = layout(g["to"]!!.jsonObject).mode
        return buildJsonObject {
            put("fromMode", from)
            put("toMode", to)
            put("stateUnchanged", state.toString() == before)
            put("appTicks", 0)
            put("before", render(from, state))
            put("after", render(to, state))
            if (e["editor"] != null) put("editor", g["editor"]!!)
        }
    }

    private fun chrome(g: JsonObject): JsonObject {
        val layout = layout(g)
        val (calendarPane, dayPane) = when (layout) {
            is Adaptive.Layout.SideBySide -> layout.calendar to layout.day
            is Adaptive.Layout.Stacked -> layout.calendar to layout.day
            is Adaptive.Layout.Single -> error("chrome cases are two-pane")
        }
        val c = Adaptive.calendarChrome(calendarPane, level(g.s("level")), level(g.s("dayReturnLevel")), YearMonth.parse(g.s("visibleMonth")), YearMonth.parse(g.s("recordedMonth")))
        val selected = LocalDate.parse(g.s("selectedDay"))
        val d = Adaptive.dayChrome(dayPane, selected, g.b("dayHasRecords"))
        return buildJsonObject {
            put("calendar", buildJsonObject {
                put("back", c.back?.specName?.let(::JsonPrimitive) ?: JsonNull)
                put("title", c.title?.let(::JsonPrimitive) ?: JsonNull)
                put("titleCenterX", c.titleCenterX?.let(::JsonPrimitive) ?: JsonNull)
                put("search", true)
                put("viewMenu", JsonArray(c.viewMenu.map { JsonPrimitive(it.specName) }))
                put("today", true)
            })
            put("day", buildJsonObject {
                put("back", JsonNull)
                put("title", d.title)
                put("titleCenterX", d.titleCenterX)
                put("more", d.more)
                put("action", if (d.editsExisting) "edit" else "add")
                put("actionOpens", buildJsonObject { put("editorDay", d.opens.toString()); put("keyboard", d.keyboard) })
            })
        }
    }

    /** JSON equality where numbers compare by value (512 == 512.0). */
    private fun same(a: JsonElement, b: JsonElement): Boolean = when {
        a is JsonObject && b is JsonObject -> a.keys == b.keys && a.keys.all { same(a[it]!!, b[it]!!) }
        a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { same(a[it], b[it]) }
        a is JsonNull || b is JsonNull -> a is JsonNull && b is JsonNull
        a is JsonPrimitive && b is JsonPrimitive -> {
            val x = a.doubleOrNull; val y = b.doubleOrNull
            if (!a.isString && !b.isString && x != null && y != null) kotlin.math.abs(x - y) < 1e-3 else a.content == b.content && a.isString == b.isString
        }
        else -> false
    }
}
