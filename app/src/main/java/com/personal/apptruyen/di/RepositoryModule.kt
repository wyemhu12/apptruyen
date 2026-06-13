package com.personal.apptruyen.di

import com.personal.apptruyen.data.repository.DownloadRepository
import com.personal.apptruyen.data.repository.IDownloadRepository
import com.personal.apptruyen.data.repository.IReadingProgressRepository
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.ITextReplacementRepository
import com.personal.apptruyen.data.repository.ReadingProgressRepository
import com.personal.apptruyen.data.repository.StoryRepository
import com.personal.apptruyen.data.repository.TextReplacementRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module: bind repository interfaces → concrete implementations.
 * Cho phép ViewModels inject interface thay vì concrete class → dễ mock khi test.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStoryRepository(impl: StoryRepository): IStoryRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepository): IDownloadRepository

    @Binds
    @Singleton
    abstract fun bindTextReplacementRepository(impl: TextReplacementRepository): ITextReplacementRepository

    @Binds
    @Singleton
    abstract fun bindReadingProgressRepository(impl: ReadingProgressRepository): IReadingProgressRepository
}
