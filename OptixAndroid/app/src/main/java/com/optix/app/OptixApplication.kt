package com.optix.app

import android.app.Application
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OptixApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Disable Crashlytics collection in debug builds
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
    }

    companion object {
        lateinit var instance: OptixApplication
            private set
    }
}
