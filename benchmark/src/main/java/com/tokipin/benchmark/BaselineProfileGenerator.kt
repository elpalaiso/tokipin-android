package com.tokipin.benchmark

import android.content.Intent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes the baseline profile (app/src/main/baseline-prof.txt) from the journeys people use most:
 * start, every view, scrolling the calendars, paging 기록한 날, search and the editor. Needs a
 * rooted emulator (google_apis image) or API 33+:
 *   ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.tokipin.benchmark.BaselineProfileGenerator
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = "com.tokipin.app", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait(Intent().setClassName(packageName, "$packageName.MainActivity").putExtra("tokipin.seed", "large"))
        device.wait(Until.hasObject(By.res("level_RECORDS")), 30_000)
        device.waitForIdle()

        device.findObject(By.res("recordsBody"))?.let { body ->
            body.setGestureMargin(device.displayWidth / 10)
            repeat(3) { body.swipe(Direction.RIGHT, 0.6f, 1800); device.waitForIdle() }
        }
        device.findObject(By.res("monthRail"))?.fling(Direction.RIGHT)
        device.waitForIdle()

        for (level in listOf("MONTH", "YEAR", "DAILY", "RECORDS")) {
            device.findObject(By.desc("탐색 보기"))?.click()
            device.wait(Until.hasObject(By.text(MENU_LABELS.getValue(level))), 5_000)
            device.findObject(By.text(MENU_LABELS.getValue(level)))?.click()
            device.wait(Until.hasObject(By.res("level_$level")), 10_000)
            device.waitForIdle()
            device.findObject(By.res("calendarList"))?.let { list ->
                list.setGestureMargin(device.displayWidth / 5)
                repeat(2) { list.fling(Direction.UP); device.waitForIdle() }
                list.fling(Direction.DOWN); device.waitForIdle()
            }
            if (level == "DAILY") {
                device.findObject(By.res("editDay"))?.click()
                device.wait(Until.hasObject(By.res("editorList")), 5_000)
                val rows = device.findObjects(By.res(java.util.regex.Pattern.compile("editRow_.*")))
                if (rows.size > 1) rows.first().drag(rows.last().visibleCenter, 1500)
                device.waitForIdle()
                device.pressBack()
                device.waitForIdle()
            }
        }

        device.findObject(By.desc("기록 검색"))?.click()
        device.wait(Until.hasObject(By.res("searchField")), 5_000)
        device.findObject(By.res("searchField"))?.let { f -> f.text = "산책"; device.waitForIdle(); f.text = "공원" }
        device.waitForIdle()
        device.pressBack()
    }
}
