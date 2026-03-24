package com.optix.app.core.util

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.optix.app.BuildConfig

/**
 * Centralized Firebase Analytics wrapper for the Optix app.
 *
 * Every event automatically includes platform metadata (OS, app version, build number)
 * via [basePlatformBundle], ensuring consistent dimensions across all analytics reports.
 *
 * Usage: Call convenience methods (e.g. [logLogin], [logOptionChainView]) from UI or
 * repository layers. For ad-hoc events, use the generic [logEvent].
 */
object AnalyticsHelper {
    private val analytics: FirebaseAnalytics by lazy { Firebase.analytics }

    /** Platform params injected into every event */
    private fun basePlatformBundle(): Bundle = Bundle().apply {
        putString("platform", "android")
        putString("app_version", BuildConfig.VERSION_NAME)
        putString("build_number", BuildConfig.VERSION_CODE.toString())
    }

    // Merges caller-supplied params with base platform metadata before dispatching
    fun logEvent(name: String, params: Bundle? = null) {
        val merged = basePlatformBundle().apply {
            params?.let { putAll(it) }
        }
        analytics.logEvent(name, merged)
    }

    fun logScreenView(screenName: String, screenClass: String? = null) {
        val bundle = basePlatformBundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass ?: screenName)
        }
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
    }

    // Binds (or clears) the Firebase user ID so all subsequent events are attributed.
    // Also sets persistent user properties for cross-platform segmentation.
    fun setUserId(userId: String?) {
        analytics.setUserId(userId)
        analytics.setUserProperty("platform", "android")
        analytics.setUserProperty("app_version", BuildConfig.VERSION_NAME)
    }

    fun setUserProperty(name: String, value: String?) {
        analytics.setUserProperty(name, value)
    }

    // --- Convenience methods for common app events ---

    fun logLogin(method: String) {
        logEvent(FirebaseAnalytics.Event.LOGIN, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        })
    }

    fun logSignUp(method: String) {
        logEvent(FirebaseAnalytics.Event.SIGN_UP, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        })
    }

    fun logOptionChainView(index: String) {
        logEvent("option_chain_view", Bundle().apply {
            putString("index", index)
        })
    }

    fun logAIInsightView() {
        logEvent("ai_insight_view")
    }

    fun logPaperTrade(action: String, symbol: String) {
        logEvent("paper_trade", Bundle().apply {
            putString("action", action)
            putString("symbol", symbol)
        })
    }

    fun logCalculatorUse(type: String) {
        logEvent("calculator_use", Bundle().apply {
            putString("calculator_type", type)
        })
    }

    fun logFeatureUsed(feature: String) {
        logEvent("feature_used", Bundle().apply {
            putString("feature", feature)
        })
    }
}
