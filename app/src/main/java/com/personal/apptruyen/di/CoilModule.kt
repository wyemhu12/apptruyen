package com.personal.apptruyen.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
    ): ImageLoader =
        ImageLoader
            .Builder(context)
            .crossfade(300)
            .memoryCache {
                MemoryCache
                    .Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }.diskCache {
                DiskCache
                    .Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }.build()
}
