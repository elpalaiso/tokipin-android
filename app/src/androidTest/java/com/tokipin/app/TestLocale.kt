package com.tokipin.app

import android.app.LocaleManager
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The UX tests read Korean strings: pin the app language (per-app language, Android 13+) so they
 * do not depend on the emulator's system language. Localization tests set other languages.
 */
object TestLocale {
    fun set(tags: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(LocaleManager::class.java)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { manager.applicationLocales = LocaleList.forLanguageTags(tags) }
    }
}
