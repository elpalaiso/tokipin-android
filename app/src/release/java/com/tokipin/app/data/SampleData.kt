package com.tokipin.app.data

import java.time.LocalDate

/** Release builds omit debug commands that replace the personal journal. */
object SampleData {
    const val AVAILABLE = false

    fun journal(@Suppress("UNUSED_PARAMETER") today: LocalDate = LocalDate.now(), @Suppress("UNUSED_PARAMETER") years: Long = 3): Journal = Journal()

    fun large(): Journal = Journal()
}
