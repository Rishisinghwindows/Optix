package com.optix.app.core.util

import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

/**
 * Lightweight wrapper around Firebase Crashlytics for consistent error reporting.
 */
object CrashlyticsHelper {

    /** Set the current user identifier for crash reports */
    fun setUser(userId: String?) {
        Firebase.crashlytics.setUserId(userId ?: "")
    }

    /** Record a non-fatal exception with optional context */
    fun record(error: Throwable, context: Map<String, String>? = null) {
        val crashlytics = Firebase.crashlytics
        context?.forEach { (key, value) ->
            crashlytics.setCustomKey(key, value)
        }
        crashlytics.recordException(error)
    }

    /** Log a message (appears in crash report breadcrumbs) */
    fun log(message: String) {
        Firebase.crashlytics.log(message)
    }

    /** Set a custom key-value pair for crash context */
    fun setCustomKey(key: String, value: String) {
        Firebase.crashlytics.setCustomKey(key, value)
    }
}
