package com.tokipin.app

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The UX tests read Korean strings: pin the app language so they do not depend on the emulator's
 * system language. Android 13+ uses the real per-app language (LocaleManager); older versions,
 * which have none, use AppLocale.override. Localization tests set other languages.
 */
object TestLocale {
    fun set(tags: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            AppLocale.override = null
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val manager = context.getSystemService(LocaleManager::class.java)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { manager.applicationLocales = LocaleList.forLanguageTags(tags) }
        } else {
            AppLocale.override = LocaleList.forLanguageTags(tags)
        }
    }
}
