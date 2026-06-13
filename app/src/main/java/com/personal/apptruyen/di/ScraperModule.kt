package com.personal.apptruyen.di

import com.personal.apptruyen.data.remote.SsTruyenScraper
import com.personal.apptruyen.data.remote.StorySource
import com.personal.apptruyen.data.remote.TangThuVienScraper
import com.personal.apptruyen.data.remote.TruyenComScraper
import com.personal.apptruyen.data.remote.TruyenFullScraper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Hilt module để inject tất cả StorySource implementations vào Set<StorySource>.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScraperModule {

    @Binds
    @IntoSet
    abstract fun bindTruyenCom(impl: TruyenComScraper): StorySource

    @Binds
    @IntoSet
    abstract fun bindTangThuVien(impl: TangThuVienScraper): StorySource

    @Binds
    @IntoSet
    abstract fun bindSsTruyen(impl: SsTruyenScraper): StorySource

    @Binds
    @IntoSet
    abstract fun bindTruyenFull(impl: TruyenFullScraper): StorySource
}
