package com.personal.apptruyen.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.personal.apptruyen.data.local.AppDatabase
import com.personal.apptruyen.data.local.ChapterDao
import com.personal.apptruyen.data.local.ReadingProgressDao
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.remote.StorySource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository cho download operations.
 * Tách từ StoryRepository để giảm coupling — quản lý tải/xóa chapters offline.
 */
@Singleton
class DownloadRepository
    @Inject
    constructor(
        private val sources: Set<@JvmSuppressWildcards StorySource>,
        private val storyDao: StoryDao,
        private val chapterDao: ChapterDao,
        private val progressDao: ReadingProgressDao,
        private val database: AppDatabase,
    ) : IDownloadRepository {

        private fun sourceFor(url: String): StorySource =
            sources.firstOrNull { url.contains(it.baseUrl.substringAfter("://")) }
                ?: sources.first()

        override suspend fun getChaptersByNumbers(
            storyId: String,
            chapterNumbers: List<Int>,
        ): List<Chapter> =
            chapterDao.getChaptersByNumbers(storyId, chapterNumbers).map { entity ->
                Chapter(
                    storyId = entity.storyId,
                    chapterNumber = entity.chapterNumber,
                    title = entity.title,
                    url = entity.url,
                    isDownloaded = entity.isDownloaded,
                )
            }

        override suspend fun snapshotStoryMetadata(storyId: String) {
            val existing = storyDao.getStoryById(storyId) ?: return
            try {
                val source = sourceFor(existing.url)
                val fresh = source.getStoryDetail(existing.url)
                val entity =
                    com.personal.apptruyen.data.local.entity.StoryEntity(
                        id = fresh.id,
                        slug = fresh.slug,
                        title = fresh.title,
                        author = fresh.author,
                        genres = fresh.genres.joinToString(","),
                        description = fresh.description,
                        url = fresh.url,
                        totalChapters = fresh.totalChapters,
                        coverImageUrl = fresh.coverImageUrl,
                        sourceId = fresh.sourceId,
                        status = fresh.status,
                        isInLibrary = existing.isInLibrary,
                        addedTimestamp = existing.addedTimestamp,
                        storyReplacementsEnabled = existing.storyReplacementsEnabled,
                        storyCustomReplacements = existing.storyCustomReplacements,
                    )
                storyDao.insertStory(entity)
                Log.d("DownloadRepo", "Snapshot metadata saved for $storyId")
            } catch (e: Exception) {
                Log.d("DownloadRepo", "Snapshot metadata failed for $storyId (offline?)", e)
            }
        }

        override fun downloadChapters(
            storyId: String,
            chapters: List<Chapter>,
        ): Flow<DownloadProgress> =
            flow {
                var downloaded = 0
                var errors = 0
                var currentDelay = 500L
                val maxDelay = 3000L
                val baseDelay = 500L

                val source = if (chapters.isNotEmpty()) sourceFor(chapters.first().url) else sources.first()

                for (chapter in chapters) {
                    currentCoroutineContext().ensureActive()
                    emit(DownloadProgress(downloaded, chapters.size, chapter.title, errors))

                    var success = false
                    for (attempt in 1..2) {
                        try {
                            val content = source.getChapterContent(chapter.url)
                            chapterDao.saveChapterContent(storyId, chapter.chapterNumber, content)
                            success = true
                            break
                        } catch (e: Exception) {
                            if (attempt == 2) {
                                errors++
                                Log.e("DownloadRepo", "Download error for ${chapter.title}", e)
                            } else {
                                delay(currentDelay)
                            }
                        }
                    }

                    downloaded++
                    emit(DownloadProgress(downloaded, chapters.size, chapter.title, errors))

                    currentDelay =
                        if (success) {
                            baseDelay
                        } else {
                            (currentDelay * 2).coerceAtMost(maxDelay)
                        }
                    delay(currentDelay)
                }
            }

        override fun getDownloadedStories(): Flow<List<Story>> =
            storyDao.getDownloadedStories().map { list ->
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

        override fun getDownloadedChapters(storyId: String): Flow<List<Chapter>> =
            chapterDao.getDownloadedChapters(storyId).map { list ->
                list.map {
                    Chapter(
                        storyId = it.storyId,
                        chapterNumber = it.chapterNumber,
                        title = it.title,
                        url = it.url,
                        content = it.content,
                        isDownloaded = it.isDownloaded,
                    )
                }
            }

        override fun getDownloadedChapterCount(storyId: String): Flow<Int> = chapterDao.getDownloadedChapterCount(storyId)

        override fun getEstimatedSize(storyId: String): Flow<Long?> = chapterDao.getEstimatedSize(storyId)

        override fun getDownloadStats(): Flow<List<com.personal.apptruyen.data.local.entity.DownloadStats>> = chapterDao.getDownloadStats()

        override suspend fun deleteChapterContent(
            storyId: String,
            chapterNumber: Int,
        ) {
            chapterDao.deleteChapterContent(storyId, chapterNumber)
        }

        override suspend fun deleteAllDownloads(storyId: String) {
            chapterDao.deleteAllChapterContent(storyId)
        }

        override suspend fun deleteStory(storyId: String) {
            database.withTransaction {
                progressDao.deleteProgress(storyId)
                chapterDao.deleteChaptersByStory(storyId)
                storyDao.deleteStory(storyId)
            }
        }
    }
