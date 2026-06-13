package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.local.ReadingProgressDao
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.local.entity.ReadingProgressEntity
import com.personal.apptruyen.data.model.ReadingProgress
import com.personal.apptruyen.data.model.Story
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository cho reading progress (vị trí đọc, lịch sử đọc gần đây).
 * Tách từ StoryRepository để giảm coupling.
 *
 * NOTE: Khác với ReadingStatsRepository (thống kê đọc: thời gian, streak).
 */
@Singleton
class ReadingProgressRepository
    @Inject
    constructor(
        private val progressDao: ReadingProgressDao,
        private val storyDao: StoryDao,
    ) : IReadingProgressRepository {

        override suspend fun getReadingProgress(storyId: String): ReadingProgress? =
            progressDao.getProgress(storyId)?.let {
                ReadingProgress(it.storyId, it.lastChapterNumber, it.scrollPosition, it.lastReadTimestamp)
            }

        override fun observeReadingProgress(storyId: String): Flow<ReadingProgress?> =
            progressDao.observeProgress(storyId).map { entity ->
                entity?.let {
                    ReadingProgress(it.storyId, it.lastChapterNumber, it.scrollPosition, it.lastReadTimestamp)
                }
            }

        override suspend fun saveReadingProgress(progress: ReadingProgress) {
            progressDao.saveProgress(
                ReadingProgressEntity(
                    storyId = progress.storyId,
                    lastChapterNumber = progress.lastChapterNumber,
                    scrollPosition = progress.scrollPosition,
                    lastReadTimestamp = progress.lastReadTimestamp,
                ),
            )
        }

        override suspend fun deleteReadingProgress(storyId: String) {
            progressDao.deleteProgress(storyId)
        }

        override fun getRecentlyReadStories(): Flow<List<Story>> =
            storyDao.getRecentlyReadStories().map { list ->
                list.map {
                    Story(
                        id = it.id,
                        slug = it.slug,
                        title = it.title,
                        author = it.author,
                        genres = it.genres.split(",").filter { g -> g.isNotBlank() },
                        description = it.description,
                        url = it.url,
                        totalChapters = it.totalChapters,
                        coverImageUrl = it.coverImageUrl,
                        sourceId = it.sourceId,
                        status = it.status,
                    )
                }
            }

        override fun getAllReadingProgress(): Flow<List<ReadingProgress>> =
            progressDao.getAllProgress().map { list ->
                list.map {
                    ReadingProgress(
                        storyId = it.storyId,
                        lastChapterNumber = it.lastChapterNumber,
                        scrollPosition = it.scrollPosition,
                        lastReadTimestamp = it.lastReadTimestamp,
                    )
                }
            }
    }
