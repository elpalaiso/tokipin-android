package com.tokipin.app

import com.tokipin.app.data.Commit
import com.tokipin.app.data.Draft
import com.tokipin.app.data.DraftRules
import com.tokipin.app.data.Entry
import com.tokipin.app.data.EntryKind
import com.tokipin.app.data.EntryRules
import com.tokipin.app.data.Journal
import com.tokipin.app.data.JournalStore
import com.tokipin.app.rules.CalendarRules
import com.tokipin.app.rules.DateHeaders
import com.tokipin.app.rules.RecordedMonths
import com.tokipin.app.rules.EmojiRules
import com.tokipin.app.rules.HapticRules
import com.tokipin.app.rules.Level
import com.tokipin.app.rules.NavEvent
import com.tokipin.app.rules.PinchAnchor
import com.tokipin.app.rules.PinchRules
import com.tokipin.app.rules.SwipeRules
import com.tokipin.app.rules.TimeRules
import com.tokipin.app.rules.UxSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Runs every case of the shared Tokipin-Native-UX-v0.3.json (the same file the iOS tests read)
 * against the Android rules. Cases that need a running UI are listed in [ON_DEVICE] and run in
 * androidTest (UxSpecDeviceTest); a case missing from both fails this test.
 */
class UxSpecCasesTest {
    private val spec: JsonObject = Json.parseToJsonElement(
        javaClass.classLoader!!.getResource("Tokipin-Native-UX-v0.3.json")!!.readText(),
    ).jsonObject

    private val cases: List<JsonObject> = spec["tests"]!!.jsonArray.map { it.jsonObject }

    private fun JsonObject.s(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.f(key: String) = this[key]!!.jsonPrimitive.float
    private fun JsonObject.b(key: String, default: Boolean = false) = this[key]?.jsonPrimitive?.booleanOrNull ?: default
    private fun JsonElement.strings() = jsonArray.map { it.jsonPrimitive.content }

    @Test fun specVersionMatchesTheRulesInTheApp() {
        assertEquals(UxSpec.VERSION, spec.s("version"))
        val swipe = spec["swipe"]!!.jsonObject
        assertEquals(UxSpec.DISTANCE_COMMIT_W, swipe.f("distanceCommitFractionOfW"))
        assertEquals(UxSpec.FLICK_MIN_DISTANCE_W, swipe.f("flickMinDistanceFractionOfW"))
        assertEquals(UxSpec.FLICK_MIN_SPEED_W_PER_S, swipe.f("flickMinSameDirectionSpeedInWidthsPerSecond"))
        assertEquals(UxSpec.RECORDED_SLOT_STEP, swipe["stepsInVisibleMonthSlots"]!!.jsonObject["recordedDaysBody"]!!.jsonPrimitive.int)
        assertTrue("v0.3 has no calendar-month step for 기록한 날", swipe["stepsInMonths"] == null)
        assertEquals(UxSpec.AXIS_LOCK_RATIO, swipe["axisLock"]!!.jsonObject.f("horizontalWhenAbsDxAtLeastAbsDyTimes"))
        val pinch = spec["pinch"]!!.jsonObject
        assertEquals(UxSpec.ZOOM_IN_AT_OR_ABOVE, pinch.f("enterDetailAtOrAbove"))
        assertEquals(UxSpec.ZOOM_OUT_AT_OR_BELOW, pinch.f("enterOverviewAtOrBelow"))
        val settling = spec["settling"]!!.jsonObject
        val commit = settling["commitMs"]!!.jsonArray.map { it.jsonPrimitive.int }
        val cancel = settling["cancelMs"]!!.jsonArray.map { it.jsonPrimitive.int }
        val reduced = settling["reduceMotionMs"]!!.jsonArray.map { it.jsonPrimitive.int }
        assertTrue(UxSpec.COMMIT_MS in commit[0]..commit[1])
        assertTrue(UxSpec.CANCEL_MS in cancel[0]..cancel[1])
        assertTrue(UxSpec.REDUCED_MOTION_FADE_MS in reduced[0]..reduced[1])
        assertEquals(listOf("year", "month", "recordedDays", "day"), pinch["orderFromWideToDetail"]!!.strings())
        assertEquals(Level.entries.map { it.specName }, pinch["orderFromWideToDetail"]!!.strings())
    }

    @Test fun everyCaseIsCovered() {
        val failures = mutableListOf<String>()
        var ran = 0
        for (case in cases) {
            val id = case.s("id")
            if (id in ON_DEVICE || id in OTHER_PLATFORM) continue
            ran++
            runCatching { run(case) }.onFailure { failures += "$id: ${it.message}" }
        }
        assertTrue("failed cases:\n" + failures.joinToString("\n"), failures.isEmpty())
        val unknown = (ON_DEVICE + OTHER_PLATFORM) - cases.map { it.s("id") }.toSet()
        assertTrue("stale case ids: $unknown", unknown.isEmpty())
        println("UX v${UxSpec.VERSION}: $ran cases on JVM, ${ON_DEVICE.size} on device, ${OTHER_PLATFORM.size} iOS-only, ${cases.size} total")
    }

    private fun run(case: JsonObject) {
        val given = case["given"]!!.jsonObject
        val expected = case["expected"]!!.jsonObject
        when (case.s("group")) {
            "swipe" -> swipe(given, expected)
            "pinch" -> pinch(given, expected)
            "pinchAnchor" -> pinchAnchor(given, expected)
            "calendar" -> calendar(given, expected)
            "haptic" -> haptic(given, expected)
            "timeDisplay" -> timeDisplay(given, expected)
            "timeEdit" -> timeEdit(given, expected)
            "emojiCommit", "migration" -> emojiCommit(given, expected)
            "emojiComposition" -> emojiComposition(given, expected)
            "emojiSelection" -> emojiSelection(given, expected)
            "emojiEdit" -> emojiEdit(given, expected)
            "capability" -> capability(given, expected)
            "recordedMonths" -> recordedMonths(case.s("id"), given, expected)
            "visibleMonthIndex" -> visibleMonthIndex(case.s("id"), given, expected)
            "monthYearHints" -> monthYearHints(case.s("id"), given, expected)
            "dateHeaders" -> dateHeaders(case.s("id"), given, expected)
            else -> error("unknown group")
        }
    }

    private fun swipe(g: JsonObject, e: JsonObject) {
        val step = if (g.s("region") == "dayWeekRail") UxSpec.WEEK_STEP else UxSpec.DAY_STEP
        val delta = SwipeRules.decide(
            width = g.f("W"), dx = g.f("dx"), dy = g.f("dy"), vx = g.f("vx"), stepDays = step,
            systemEdgeBack = g.b("systemEdgeBack"), osCancel = g.b("osCancel"), slopExceeded = g.b("platformSlopExceeded", true),
        )
        assertEquals(e["dayDelta"]!!.jsonPrimitive.int, delta)
        val event = if (delta == 0) NavEvent.CANCEL else if (step == 7) NavEvent.WEEK_SWIPE_COMMIT else NavEvent.DAY_SWIPE_COMMIT
        assertEquals(e["appTicks"]!!.jsonPrimitive.int, HapticRules.ticks(listOf(NavEvent.DRAG_PROGRESS, event), true))
    }

    private fun pinch(g: JsonObject, e: JsonObject) {
        val from = Level.fromSpec(g.s("level"))
        val to = PinchRules.decide(from, g.f("scaleAtRelease"), osCancel = g.b("osCancel"))
        assertEquals(Level.fromSpec(e.s("level")), to)
        val events = listOf(NavEvent.PINCH_PROGRESS, if (to != from) NavEvent.LEVEL_COMMIT else if (to == from && g.f("scaleAtRelease") >= 1.25f) NavEvent.LEVEL_LIMIT else NavEvent.CANCEL)
        assertEquals(e["appTicks"]!!.jsonPrimitive.int, HapticRules.ticks(events, true))
    }

    private fun pinchAnchor(g: JsonObject, e: JsonObject) {
        val anchor = PinchAnchor()
        anchor.hit(LocalDate.parse(g.s("firstHit")))
        anchor.hit(LocalDate.parse(g.s("lastHit")))
        assertEquals(LocalDate.parse(e.s("anchor")), anchor.target)
        pinch(g, e)
    }

    private fun calendar(g: JsonObject, e: JsonObject) {
        val month = YearMonth.parse(g.s("month"))
        val firstDay = DayOfWeek.valueOf(g.s("firstWeekday").uppercase())
        val column = CalendarRules.firstDayColumn(month, firstDay)
        assertEquals(e["firstDayColumnIndexZeroBased"]!!.jsonPrimitive.int, column)
        assertEquals(DayOfWeek.valueOf(e.s("monthLabelAboveWeekday").uppercase()), firstDay.plus(column.toLong()))
        g["entries"]?.let { entries ->
            val (shown, more) = CalendarRules.preview(entries.strings())
            assertEquals(e["shown"]!!.strings(), shown)
            assertEquals(e.s("overflow"), "+$more")
        }
    }

    private fun haptic(g: JsonObject, e: JsonObject) {
        val events = g["eventsFromOneAction"]!!.strings().map(NavEvent::fromSpec)
        assertEquals(e["appTicks"]!!.jsonPrimitive.int, HapticRules.ticks(events, g.b("enabled", true)))
    }

    private fun timeDisplay(g: JsonObject, e: JsonObject) {
        val saved = g.s("saved")
        val is24 = g["hourCyclePreference"]!!.jsonPrimitive.int == 24
        val locale = Locale.forLanguageTag(g.s("locale"))
        // Stored value never changes; only its presentation follows the device setting.
        val draft = DraftRules.commit(Draft(date = "2026-11-26", kind = EntryKind.TIME, raw = saved)) as Commit.Ok
        assertEquals(e.s("storedUnchanged"), draft.draft.items.single().value)
        val shown = TimeRules.display(saved, is24, locale)
        val minute = "%02d".format(e["displayMinute"]!!.jsonPrimitive.int)
        val hour = e["displayHour"]!!.jsonPrimitive.int
        val expected = when (val period = e["dayPeriod"]) {
            null, is JsonNull -> "%02d:%s".format(hour, minute)
            else -> (if (period.jsonPrimitive.content == "PM") "오후" else "오전") + " $hour:$minute"
        }
        assertEquals(expected, shown)
    }

    /** Replays the editor's time actions on the draft model the screen uses. */
    private fun timeEdit(g: JsonObject, e: JsonObject) {
        var d = Draft(
            date = "2026-11-26", kind = EntryKind.TIME,
            items = g["before"]?.strings().orEmpty().map { Entry(kind = EntryKind.TIME, value = it) },
        )
        var saved: List<String>? = null
        for (action in g["actions"]!!.strings()) {
            val parts = action.split(" ")
            when (parts[0]) {
                "choose", "select" -> d = d.copy(raw = parts[1]) // an explicit choice in the picker
                "open" -> Unit // opening the picker alone keeps nothing pending
                "edit" -> d = d.items.first().let { d.copy(editId = it.id, raw = it.value) }
                "cancel" -> d = DraftRules.cancelPending(d)
                "+" -> d = (DraftRules.commit(d) as Commit.Ok).draft
                "✓" -> { d = (DraftRules.commit(d) as Commit.Ok).draft; saved = d.items.map { it.value } }
                else -> error("action $action")
            }
        }
        assertEquals(e["fragmentValues"]!!.strings(), saved)
    }

    private fun emojiCommit(g: JsonObject, e: JsonObject) {
        if (g["saved"] != null) {
            // Already saved records are read and exported exactly as stored.
            val journal = Journal().withDay(LocalDate.of(2026, 11, 1), listOf(Entry(kind = EntryKind.EMOJI, value = g.s("saved"))))
            assertTrue(JournalStore.exportJson(journal).contains("\"value\": \"${e.s("saved")}\""))
            return
        }
        val raw = g.s("raw")
        assertEquals(e.s("acceptedText"), EmojiRules.filter(raw))
        val result = DraftRules.commit(Draft(date = "2026-11-26", kind = EntryKind.EMOJI, raw = raw))
        val appended = result is Commit.Ok && result.draft.items.isNotEmpty()
        assertEquals(e["appendIfExplicitlyConfirmed"]!!.jsonPrimitive.boolean, appended)
        if (appended) assertEquals(e.s("acceptedText"), (result as Commit.Ok).draft.items.single().value)
        assertEquals(EmojiRules.filter(raw).ifEmpty { null }, EntryRules.normalized(EntryKind.EMOJI, raw))
    }

    private fun emojiComposition(g: JsonObject, e: JsonObject) {
        val raw = g.s("raw")
        val before = EmojiRules.Edit("", 0, 0)
        val during = EmojiRules.Edit(raw, raw.length, raw.length)
        val after = EmojiRules.fieldChange(before, during, composing = g.b("composing"))
        assertEquals(!e["rewriteComposition"]!!.jsonPrimitive.boolean, after == during)
        // Return during a composition is handled by the IME (it ends the composition); no item is added.
        assertEquals(e["append"]!!.jsonPrimitive.boolean, false)
    }

    private fun emojiSelection(g: JsonObject, e: JsonObject) {
        val text = g.s("before")
        val range = g["selectionGraphemeRange"]!!.jsonArray.map { it.jsonPrimitive.int }
        val start = text.offsetByCodePoints(0, range[0])
        val end = text.offsetByCodePoints(0, range[1])
        val paste = g.s("paste")
        val replaced = text.substring(0, start) + paste + text.substring(end)
        val result = EmojiRules.applyEdit(
            EmojiRules.Edit(text, start, end),
            EmojiRules.Edit(replaced, start + paste.length, start + paste.length),
        )
        assertEquals(e.s("text"), result.text)
        val exp = e["selectionGraphemeRange"]!!.jsonArray.map { it.jsonPrimitive.int }
        assertEquals(listOf(exp[0], exp[1]), listOf(result.text.codePointCount(0, result.selectionStart), result.text.codePointCount(0, result.selectionEnd)))
    }

    private fun emojiEdit(g: JsonObject, e: JsonObject) {
        var d = Draft(date = "2026-11-26", kind = EntryKind.EMOJI)
        for (action in g["actions"]!!.strings()) {
            val parts = action.split(" ", limit = 2)
            d = when (parts[0]) {
                "type" -> d.copy(raw = d.raw + parts[1])
                "confirm" -> (DraftRules.commit(d) as Commit.Ok).draft
                else -> error("action $action")
            }
        }
        assertEquals(e["fragmentValues"]!!.strings(), d.items.map { it.value })
    }

    /** The shared "달력 범위 예시" (calendarRangeArchive): the same file the debug build loads. */
    private val archiveDates: List<LocalDate> by lazy {
        Json.parseToJsonElement(java.io.File("src/debug/assets/calendar-range-records.json").readText())
            .jsonObject["days"]!!.jsonArray.map { it.jsonObject }
            .filter { it["items"]!!.jsonArray.isNotEmpty() }
            .map { LocalDate.parse(it["date"]!!.jsonPrimitive.content) }
    }

    private fun recordDates(g: JsonObject): List<LocalDate> =
        if (g["fixture"]?.jsonPrimitive?.content == "calendarRangeArchive") archiveDates
        else g["availableRecordDates"]?.strings()?.map(LocalDate::parse).orEmpty()

    private fun model(g: JsonObject, dates: List<LocalDate> = recordDates(g)): RecordedMonths {
        val committed = YearMonth.parse(g.s("committedMonth"))
        val selected = g["selectedDay"]?.let { LocalDate.parse(it.jsonPrimitive.content) } ?: committed.atDay(1)
        val today = LocalDate.parse(g.s("deviceToday"))
        return RecordedMonths(committed, selected, RecordedMonths.visibleMonths(dates, today))
    }

    private fun JsonElement.monthsOrNull(): List<String?> = jsonArray.map { if (it is JsonNull) null else it.jsonPrimitive.content }

    /** Checks shared by the 기록한 날 groups after one action on [model]. */
    private fun checkRecorded(id: String, g: JsonObject, e: JsonObject, model: RecordedMonths, events: List<NavEvent>, dates: List<LocalDate>) {
        e["committedMonth"]?.let { assertEquals(id, YearMonth.parse(it.jsonPrimitive.content), model.committed) }
        e["centeredMonth"]?.let { assertEquals(id, YearMonth.parse(it.jsonPrimitive.content), model.centered) }
        e["headerYear"]?.let { assertEquals(id, it.jsonPrimitive.int, model.headerYear) }
        e["selectedDay"]?.let { assertEquals(id, LocalDate.parse(it.jsonPrimitive.content), model.selectedDay) }
        e["visibleMonths"]?.let { assertEquals(id, it.strings(), model.months.map(YearMonth::toString)) }
        e["visibleCardDates"]?.let { exp -> assertEquals(id, exp.strings(), dates.filter { YearMonth.from(it) == model.committed }.map { it.toString() }) }
        e["showMonthEmptyState"]?.let { assertEquals(id, it.jsonPrimitive.boolean, dates.none { YearMonth.from(it) == model.committed }) }
        e["spatialTransition"]?.let { assertEquals(id, it.jsonPrimitive.boolean, !g.b("reduceMotion")) }
        // Hints and the capsule live in a fixed-height rail: the device test measures both shifts.
        e["baselineShift"]?.let { assertEquals(id, 0, it.jsonPrimitive.int) }
        e["capsuleCenterShift"]?.let { assertEquals(id, 0, it.jsonPrimitive.int) }
        e["level"]?.let { if (it.jsonPrimitive.content == "recordedDays") assertEquals("today", g.s("action")) }
        val ticks = (e["appTicks"] ?: e["monthTicks"])!!.jsonPrimitive.int
        assertEquals(id, ticks, HapticRules.ticks(events, g.b("appHapticsEnabled", true)))
    }

    /** 기록한 날 cases, driven through the same RecordedMonths model the screen uses. */
    private fun recordedMonths(id: String, g: JsonObject, e: JsonObject) {
        val dates = recordDates(g)
        val model = model(g, dates)
        val committed = model.committed
        val events: List<NavEvent> = when (g["action"]?.jsonPrimitive?.content ?: "pinch") {
            "bodySwipeRelease" -> model.bodyRelease(
                g.f("W"), g.f("dx"), g.f("dy"), g.f("vx"),
                systemEdgeBack = g.b("systemEdgeBack"), osCancel = g.b("osCancel"), slopExceeded = g.b("platformSlopExceeded", true),
            )
            "bodyDrag" -> {
                val neighbor = YearMonth.parse(g.s("neighborMonth"))
                val fraction = g.f("dragFraction") * (if (neighbor > committed) -1 else 1)
                val ev = model.dragging(fraction)
                assertEquals(YearMonth.parse(e.s("previewMonth")), model.preview)
                // The rail is driven by the body's own drag fraction, so both progress values are equal.
                assertEquals(e.f("bodyProgress"), kotlin.math.abs(fraction))
                assertEquals(e.f("railProgress"), kotlin.math.abs(fraction))
                ev
            }
            "verticalDragAtBottom" -> listOf(NavEvent.SCROLL)
            "renderRail" -> {
                val window = model.window(g["sideSlotsInThisFixture"]!!.jsonPrimitive.int)
                assertEquals(e["visibleMonthKeys"]!!.monthsOrNull(), window.map { it?.toString() })
                assertEquals(e["visibleMonthNumbers"]!!.jsonArray.map { it.jsonPrimitive.int }, window.map { it?.monthValue })
                emptyList()
            }
            "tapRailMonth" -> model.tap(YearMonth.parse(g.s("tappedMonth")))
            "railCoast" -> {
                val ev = model.railCoast(YearMonth.parse(g.s("nearestCandidate")))
                assertEquals(YearMonth.parse(e.s("previewMonth")), model.preview)
                ev
            }
            "railSettle" -> model.railSettle(g.f("projectedSlotOffsetAtRest"))
            "cancelRail" -> { model.railCoast(YearMonth.parse(g.s("nearestCandidate"))); model.cancel() }
            "today" -> model.today(LocalDate.parse(g.s("deviceToday")))
            "pinch" -> {
                // Body drag begins, a second finger turns it into a pinch: the pending month move is
                // cancelled (SwipePager gives up on two pointers) and the pinch decides the level.
                val anchor = PinchAnchor()
                anchor.hit(LocalDate.parse(g.s("firstHit"))); anchor.hit(LocalDate.parse(g.s("lastHit")))
                assertEquals(LocalDate.parse(e.s("anchor")), anchor.target)
                val to = PinchRules.decide(Level.fromSpec(g.s("level")), g.f("scaleAtRelease"))
                assertEquals(Level.fromSpec(e.s("level")), to)
                assertEquals(e["monthCommitCount"]!!.jsonPrimitive.int, 0)
                assertEquals(committed, model.committed)
                model.cancel() + NavEvent.LEVEL_COMMIT
            }
            else -> error("action")
        }
        checkRecorded(id, g, e, model, events, dates)
    }

    /** v0.3 visible-month list: building it, stepping one slot, resolving and recalculating. */
    private fun visibleMonthIndex(id: String, g: JsonObject, e: JsonObject) {
        val dates = recordDates(g)
        val today = LocalDate.parse(g.s("deviceToday"))
        when (g.s("action")) {
            "buildVisibleMonths" -> {
                // Draft-only dates are never passed in: drafts live apart from the saved journal.
                val months = RecordedMonths.visibleMonths(dates, today)
                assertEquals(id, e["visibleMonths"]!!.strings(), months.map(YearMonth::toString))
                e["showMonthEmptyState"]?.let { assertEquals(id, it.jsonPrimitive.boolean, dates.none { YearMonth.from(it) == YearMonth.from(today) }) }
            }
            "bodySlotStep" -> {
                val model = model(g, dates)
                checkRecorded(id, g, e, model, model.step(g["direction"]!!.jsonPrimitive.int), dates)
            }
            "resolveAnchor" -> {
                val selected = LocalDate.parse(g.s("selectedDay"))
                val model = RecordedMonths(YearMonth.from(today), selected, RecordedMonths.visibleMonths(dates, today))
                val events = model.resolve(YearMonth.parse(g.s("requestedMonth")))
                checkRecorded(id, g, e, model, events, dates)
            }
            "refresh" -> {
                val model = model(g, dates)
                val removed = g["remove"]?.strings()?.map(LocalDate::parse).orEmpty().toSet()
                val inserted = g["insert"]?.strings()?.map(LocalDate::parse).orEmpty()
                val after = dates.filterNot { it in removed } + inserted
                val newToday = g["newDeviceToday"]?.let { LocalDate.parse(it.jsonPrimitive.content) } ?: today
                checkRecorded(id, g, e, model, model.refresh(RecordedMonths.visibleMonths(after, newToday)), after)
            }
            "cancelBodyDrag" -> {
                val model = model(g, dates)
                val preview = YearMonth.parse(g.s("previewMonth"))
                model.dragging(if (preview > model.committed) -0.6f else 0.6f)
                assertEquals(id, preview, model.preview)
                checkRecorded(id, g, e, model, model.cancel(), dates)
            }
            else -> error("action")
        }
    }

    /** v0.3 small years above the rail's month numbers. */
    private fun monthYearHints(id: String, g: JsonObject, e: JsonObject) {
        val dates = recordDates(g)
        val model = model(g, dates)
        when (g.s("action")) {
            "renderHints" -> {
                val side = g["sideSlotsInThisFixture"]!!.jsonPrimitive.int
                val window = model.window(side)
                assertEquals(id, e["visibleMonthKeys"]!!.monthsOrNull(), window.map { it?.toString() })
                assertEquals(id, e["visibleMonthNumbers"]!!.jsonArray.map { if (it is JsonNull) null else it.jsonPrimitive.int }, window.map { it?.monthValue })
                assertEquals(id, e["hintsBySlot"]!!.monthsOrNull(), model.hints(side))
                checkRecorded(id, g, e, model, emptyList(), dates)
            }
            "inspectRailAccessibility" -> {
                val target = YearMonth.parse(g.s("targetMonth"))
                assertTrue(id, target in model.months)
                val value = e["monthValue"]!!.jsonObject
                assertEquals(id, YearMonth.of(value["year"]!!.jsonPrimitive.int, value["month"]!!.jsonPrimitive.int), target)
                assertEquals(id, e["isSelected"]!!.jsonPrimitive.boolean, target == model.committed)
                assertEquals(id, e["hintIsSeparateAccessibleElement"]!!.jsonPrimitive.boolean, !UxSpec.RAIL_HINT_MERGED_INTO_SLOT)
            }
            "tapRailMonth" -> checkRecorded(id, g, e, model, model.tap(YearMonth.parse(g.s("tappedMonth"))), dates)
            else -> error("action")
        }
    }

    private fun dateHeaders(id: String, g: JsonObject, e: JsonObject) {
        val title: String? = when (g.s("level")) {
            "day" -> DateHeaders.day(LocalDate.parse(g.s("selectedDay")))
            "recordedDays" -> DateHeaders.recordedDays(YearMonth.parse(g.s("committedMonth")))
            "month" -> DateHeaders.month(YearMonth.parse(g.s("visibleMonth")))
            "year" -> DateHeaders.year(g.s("yearBlock").toInt())
            "editor" -> DateHeaders.editor(LocalDate.parse(g.s("draftDay")))
            else -> error("level")
        }
        val expectedTitle = e["globalTitle"]!!.let { if (it is JsonNull) null else it.jsonPrimitive.content }
        assertEquals(id, expectedTitle, title)
        val center = e["titleCenterX"]!!.let { if (it is JsonNull) null else it.jsonPrimitive.float }
        if (center != null) {
            assertEquals(id, center, DateHeaders.titleCenterX(g.f("width"), g.f("leadingActionsWidth"), g.f("trailingActionsWidth")))
            assertEquals(id, "viewportCenter", e.s("anchor"))
        } else {
            assertEquals(id, "blockLeading", e.s("anchor"))
        }
    }

    private fun capability(g: JsonObject, e: JsonObject) {
        assertEquals("Android", g.s("platform"))
        assertEquals(e.b("requestAvailableSystemKeyboard"), UxSpec.EMOJI_REQUESTS_SYSTEM_KEYBOARD)
        assertEquals(e.b("guaranteePanelSwitch"), UxSpec.EMOJI_GUARANTEES_PANEL_SWITCH)
        assertEquals(e.b("appSuggestedPalette"), UxSpec.EMOJI_APP_PALETTE)
        assertEquals(e.b("acceptEmojiPaste"), UxSpec.EMOJI_ACCEPTS_PASTE)
    }

    companion object {
        /** Navigation-state cases that need the real screen; see androidTest/UxSpecDeviceTest. */
        val ON_DEVICE = setOf(
            "calendar.scrollYearBoundary", "calendar.backPosition", "calendar.todayWhenSelectedElsewhere",
            "recordedMonths.detail.restore",
        )
        val OTHER_PLATFORM = setOf("emoji.keyboardUnavailable")
    }
}
