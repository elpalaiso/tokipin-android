package com.tokipin.benchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.tokipin.app"

/** The view menu is a popup (its own window, without resource ids), so items are found by label. */
internal val MENU_LABELS = mapOf("DAILY" to "일간", "RECORDS" to "기록한 날", "MONTH" to "월간 달력", "YEAR" to "연간")


/**
 * Same scenarios before and after each optimisation, on the ten-year journal (3,648 days,
 * 20,500 fragments). CompilationMode.None is the worst case: a fresh install on an old device
 * before any profile has been applied. Pass `-e compilation partial` for the baseline profile.
 */
@RunWith(AndroidJUnit4::class)
class TokipinBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()

    private val compilation: CompilationMode
        get() = when (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("compilation")) {
            "partial" -> CompilationMode.Partial()
            "full" -> CompilationMode.Full()
            else -> CompilationMode.None()
        }

    private val iterations: Int
        get() = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("iterations")?.toInt() ?: 5

    /** Replaces the journal with the large sample and waits until it is on screen. */
    private fun MacrobenchmarkScope.seed() {
        pressHome()
        startActivityAndWait(Intent().setClassName(PACKAGE, "$PACKAGE.MainActivity").putExtra("tokipin.seed", "large"))
        device.wait(Until.hasObject(By.res("level_RECORDS")), 30_000)
        killProcess()
    }

    private fun MacrobenchmarkScope.open() {
        startActivityAndWait()
        device.wait(Until.hasObject(By.res("level_RECORDS")), 20_000)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.menu(level: String) {
        device.findObject(By.desc("탐색 보기")).click()
        device.wait(Until.hasObject(By.text(MENU_LABELS.getValue(level))), 5_000)
        device.findObject(By.text(MENU_LABELS.getValue(level))).click()
        device.wait(Until.hasObject(By.res("level_$level")), 10_000)
        device.waitForIdle()
    }

    /**
     * Seeds before every iteration: resetting compilation between iterations can clear app data on
     * older Android versions, and a first launch on an empty journal opens the editor instead.
     */
    private fun MacrobenchmarkScope.seedOnce() = seed()

    @Test fun coldStart() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        iterations = iterations,
        startupMode = StartupMode.COLD,
        setupBlock = { seedOnce() },
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.res("level_RECORDS")), 20_000)
    }

    @OptIn(ExperimentalMetricApi::class)
    private fun frames(name: String, setup: MacrobenchmarkScope.() -> Unit, measure: MacrobenchmarkScope.() -> Unit) =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilation,
            iterations = iterations,
            // Not StartupMode.COLD: that would kill the process after setup. Each iteration starts
            // the app fresh here instead, then measures only the gesture.
            startupMode = null,
            setupBlock = { seedOnce(); killProcess(); open(); setup() },
            measureBlock = measure,
        )

    private fun MacrobenchmarkScope.flingList(times: Int) {
        val list = device.findObject(By.scrollable(true).res("calendarList"))
        list.setGestureMargin(device.displayWidth / 5)
        repeat(times) { list.fling(Direction.UP); device.waitForIdle() }
        repeat(times) { list.fling(Direction.DOWN); device.waitForIdle() }
    }

    @Test fun monthCalendarScroll() = frames("month", { menu("MONTH") }) { flingList(4) }

    @Test fun yearCalendarScroll() = frames("year", { menu("YEAR") }) { flingList(4) }

    @Test fun recordedDaysPaging() = frames("records", {}) {
        val body = device.findObject(By.res("recordsBody"))
        body.setGestureMargin(device.displayWidth / 10)
        repeat(6) { body.swipe(Direction.RIGHT, 0.6f, 1800); device.waitForIdle() }
        repeat(6) { body.swipe(Direction.LEFT, 0.6f, 1800); device.waitForIdle() }
    }

    @Test fun recordedDaysRailFling() = frames("rail", {}) {
        val rail = device.findObject(By.res("monthRail"))
        repeat(3) { rail.fling(Direction.RIGHT); device.waitForIdle() }
        repeat(3) { rail.fling(Direction.LEFT); device.waitForIdle() }
    }

    @Test fun searchTyping() = frames("search", {
        device.findObject(By.desc("기록 검색")).click()
        device.wait(Until.hasObject(By.res("searchField")), 5_000)
    }) {
        val field = device.findObject(By.res("searchField"))
        listOf("산", "산책", "공", "공원", "책").forEach { q ->
            field.text = q
            device.waitForIdle()
        }
        device.findObject(By.res("searchResults"))?.let { it.setGestureMargin(device.displayWidth / 5); it.fling(Direction.UP) }
        device.waitForIdle()
    }

    @Test fun editorDrag() = frames("editor", {
        menu("DAILY")
        device.findObject(By.res("editDay")).click()
        device.wait(Until.hasObject(By.res("editorList")), 5_000)
        device.waitForIdle()
    }) {
        val rows = device.findObjects(By.res(java.util.regex.Pattern.compile("editRow_.*")))
        val firstAt = rows.first().visibleCenter
        val lastAt = rows.last().visibleCenter
        // UiObject2.drag holds for the long-press time first, then moves: a quick carry across all rows and back.
        rows.first().drag(lastAt, 1500)
        device.waitForIdle()
        device.findObjects(By.res(java.util.regex.Pattern.compile("editRow_.*"))).last().drag(firstAt, 1500)
        device.waitForIdle()
    }
}
