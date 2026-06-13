package com.personal.apptruyen.di

import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.personal.apptruyen.data.local.AppDatabase
import com.personal.apptruyen.data.local.ChapterDao
import com.personal.apptruyen.data.local.ReadingProgressDao
import com.personal.apptruyen.data.local.ReadingStatsDao
import com.personal.apptruyen.data.local.SearchHistoryDao
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.local.settingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val cookieJar =
            object : okhttp3.CookieJar {
                private val store = java.util.concurrent.ConcurrentHashMap<String, List<okhttp3.Cookie>>()

                override fun saveFromResponse(
                    url: okhttp3.HttpUrl,
                    cookies: List<okhttp3.Cookie>,
                ) {
                    store[url.host] = cookies
                }

                override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> = store[url.host] ?: emptyList()
            }

        return OkHttpClient
            .Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(5, 30, TimeUnit.SECONDS))
            .followRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(
                context,
                AppDatabase::class.java,
                "apptruyen.db",
            )
            // Allow destructive migration from legacy versions (v1-v4)
            .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4)
            .addMigrations(
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
            ).build()

    @Provides
    fun provideStoryDao(db: AppDatabase): StoryDao = db.storyDao()

    @Provides
    fun provideChapterDao(db: AppDatabase): ChapterDao = db.chapterDao()

    @Provides
    fun provideReadingProgressDao(db: AppDatabase): ReadingProgressDao = db.readingProgressDao()

    @Provides
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    fun provideReadingStatsDao(db: AppDatabase): ReadingStatsDao = db.readingStatsDao()

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
    ): NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Provides
    @Singleton
    fun provideScraperCircuitBreaker(): com.personal.apptruyen.data.remote.ScraperCircuitBreaker =
        com.personal.apptruyen.data.remote.ScraperCircuitBreaker(
            failureThreshold = 5,
            resetTimeoutMs = 60_000L, // 1 phút
        )

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore
}
