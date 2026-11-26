package com.tokipin.app.data

import java.time.LocalDate

/** Release builds carry no sample data (see src/debug). */
object SampleData {
    const val AVAILABLE = false

    fun journal(@Suppress("UNUSED_PARAMETER") today: LocalDate = LocalDate.now()): Journal = Journal()
}
