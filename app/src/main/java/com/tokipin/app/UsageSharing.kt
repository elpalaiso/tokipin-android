package com.tokipin.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType

/** The choice is per installation. Only tokipin.settings (journal preferences) is backed up. */
internal class UsageSharing(
    context: Context,
    private val analytics: UsageAnalytics = FirebaseUsageAnalytics(context.applicationContext),
    val available: Boolean = BuildConfig.USAGE_SHARING_AVAILABLE,
) {
    private val prefs = context.getSharedPreferences("tokipin.usage", Context.MODE_PRIVATE)
    val enabled: Boolean get() = available && prefs.getBoolean("enabled", false)
    val hasChoice: Boolean get() = prefs.contains("enabled")

    init {
        if (enabled) analytics.setCollection(true)
    }

    fun markPresented() {
        // Persist once when the sheet appears, so even process death counts as a dismissed invitation.
        if (available && !hasChoice) prefs.edit().putBoolean("enabled", false).commit()
    }

    fun setEnabled(value: Boolean) {
        if (!available) return
        prefs.edit().putBoolean("enabled", value).commit()
        analytics.setCollection(value)
    }

    fun recordSaved() {
        if (enabled) analytics.recordSaved()
    }
}

/** No content-bearing analytics interface: even its caller cannot pass journal data. */
internal interface UsageAnalytics {
    fun setCollection(enabled: Boolean)
    fun recordSaved()
}

private class FirebaseUsageAnalytics(private val app: Context) : UsageAnalytics {
    private var analytics: FirebaseAnalytics? = null

    override fun setCollection(enabled: Boolean) {
        if (enabled) startCollection() else {
            analytics?.run {
                setAnalyticsCollectionEnabled(false)
                setConsent(consent(false))
                resetAnalyticsData()
            }
            analytics = null
        }
    }

    private fun startCollection() {
        // Debug source builds can run without a Firebase config. Releases require it at build time.
        if (FirebaseApp.initializeApp(app) == null) return
        analytics = FirebaseAnalytics.getInstance(app).also {
            it.setConsent(consent(true))
            it.setAnalyticsCollectionEnabled(true)
        }
    }

    private fun consent(share: Boolean) = mapOf(
        ConsentType.ANALYTICS_STORAGE to if (share) ConsentStatus.GRANTED else ConsentStatus.DENIED,
        ConsentType.AD_STORAGE to ConsentStatus.DENIED,
        ConsentType.AD_USER_DATA to ConsentStatus.DENIED,
        ConsentType.AD_PERSONALIZATION to ConsentStatus.DENIED,
    )

    override fun recordSaved() {
        analytics?.logEvent("record_saved", null)
    }
}
