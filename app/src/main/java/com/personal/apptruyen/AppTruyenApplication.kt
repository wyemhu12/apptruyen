package com.personal.apptruyen

import android.app.Application
import com.personal.apptruyen.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class AppTruyenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)

        // Structured logging — chỉ log trong debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
