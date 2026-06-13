package com.personal.apptruyen

import android.app.Application
import com.personal.apptruyen.util.CrashHandler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AppTruyenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
