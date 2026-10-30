package com.personal.apptruyen

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.personal.apptruyen.util.CrashHandler
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class AppTruyenApplication :
    Application(),
    SingletonImageLoader.Factory {

    @Inject
    lateinit var imageLoader: Lazy<ImageLoader>

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)

        // Structured logging — chỉ log trong debug builds
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        Timber.tag("CoilInit").d("SingletonImageLoader initialized via AppTruyenApplication")
        return imageLoader.get()
    }
}
