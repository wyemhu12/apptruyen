package com.personal.apptruyen.data.repository

import android.util.Log
import com.personal.apptruyen.data.local.ChapterDao
import com.personal.apptruyen.data.local.DatabaseTransactionRunner
import com.personal.apptruyen.data.local.ReadingProgressDao
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.ChapterContentResult
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.remote.ScraperException
import com.personal.apptruyen.data.remote.StorySource
import com.personal.apptruyen.data.remote.matchesUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        private val transactionRunner: DatabaseTransactionRunner,
    ) : IDownloadRepository {

        private data class ChapterFetchOutcome(
            val chapter: Chapter,
            val result: ChapterContentResult? = null,
            val error: Exception? = null,
        )

        companion object {
            const val DOWNLOAD_BATCH_SIZE = 4
            const val MAX_RETRY_ATTEMPTS = 2
            const val BASE_BATCH_DELAY_MS = 250L
            const val MAX_BATCH_DELAY_MS = 3000L
            const val MAX_CONSECUTIVE_FAILED_BATCHES = 3
            const val SQLITE_MAX_BIND_ARGS = 900
            private val DUMMY_TITLE_REGEX = Regex("^Chương \\d+$")
        }

        private fun sourceFor(url: String): StorySource =
            sources.firstOrNull { it.matchesUrl(url) }
                ?: throw ScraperException.ParseException("Nguồn truyện không được hỗ trợ", url)

        override suspend fun getChaptersByNumbers(
            storyId: String,
            chapterNumbers: List<Int>,
        ): List<Chapter> {
            if (chapterNumbers.isEmpty()) return emptyList()
            return chapterNumbers
                .distinct()
                .chunked(SQLITE_MAX_BIND_ARGS)
                .flatMap { chunk -> chapterDao.getChaptersByNumbers(storyId, chunk) }
                .sortedBy { it.chapterNumber }
                .map { entity ->
                    Chapter(
                        storyId = entity.storyId,
                        chapterNumber = entity.chapterNumber,
                        title = entity.title,
                        url = entity.url,
                        isDownloaded = entity.isDownloaded,
                    )
                }
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

        private suspend fun fetchChapterWithRetry(
            source: StorySource,
            chapter: Chapter,
            retryDelayMs: Long,
        ): ChapterFetchOutcome {
            if (chapter.url.isBlank()) {
                return ChapterFetchOutcome(
                    chapter = chapter,
                    error = ScraperException.ParseException("URL chương rỗng", ""),
                )
            }
            var lastError: Exception? = null
            for (attempt in 1..MAX_RETRY_ATTEMPTS) {
                currentCoroutineContext().ensureActive()
                try {
                    val result = source.getChapterContentWithTitle(chapter.url)
                    return ChapterFetchOutcome(chapter = chapter, result = result)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ScraperException.LoginRequiredException) {
                    return ChapterFetchOutcome(chapter = chapter, error = e)
                } catch (e: ScraperException.CircuitOpenException) {
                    return ChapterFetchOutcome(chapter = chapter, error = e)
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < MAX_RETRY_ATTEMPTS) {
                        delay(retryDelayMs)
                    }
                }
            }
            return ChapterFetchOutcome(chapter = chapter, error = lastError)
        }

        override fun downloadChapters(
            storyId: String,
            chapters: List<Chapter>,
        ): Flow<DownloadProgress> =
            flow {
                if (chapters.isEmpty()) return@flow

                var downloaded = 0
                var errors = 0
                var currentDelay = BASE_BATCH_DELAY_MS
                var consecutiveFailedBatches = 0

                val sortedChapters = chapters.sortedBy { it.chapterNumber }
                val firstValidUrl = sortedChapters.firstOrNull { it.url.isNotBlank() }?.url ?: ""
                val source = sourceFor(firstValidUrl)
                val batches = sortedChapters.chunked(DOWNLOAD_BATCH_SIZE)

                for ((batchIndex, batch) in batches.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    emit(DownloadProgress(downloaded, sortedChapters.size, batch.first().title, errors))

                    val batchOutcomes =
                        coroutineScope {
                            batch
                                .map { chapter ->
                                    async {
                                        fetchChapterWithRetry(source, chapter, currentDelay)
                                    }
                                }.awaitAll()
                        }

                    val orderedOutcomes = batchOutcomes.sortedBy { it.chapter.chapterNumber }
                    var batchErrors = 0
                    var fatalError: ScraperException? = null

                    for (outcome in orderedOutcomes) {
                        currentCoroutineContext().ensureActive()
                        val chapter = outcome.chapter
                        val result = outcome.result
                        if (result != null) {
                            chapterDao.saveChapterContent(storyId, chapter.chapterNumber, result.content)

                            if (!result.title.isNullOrBlank()) {
                                val isDummy = chapter.title.matches(DUMMY_TITLE_REGEX)
                                if (isDummy || chapter.title.isBlank()) {
                                    chapterDao.updateChapterMetadata(
                                        storyId = storyId,
                                        chapterNumber = chapter.chapterNumber,
                                        title = result.title,
                                        url = chapter.url,
                                    )
                                }
                            }
                        } else {
                            errors++
                            batchErrors++
                            Log.e("DownloadRepo", "Download error for ${chapter.title}", outcome.error)
                            if (
                                fatalError == null &&
                                (
                                    outcome.error is ScraperException.LoginRequiredException ||
                                        outcome.error is ScraperException.CircuitOpenException
                                )
                            ) {
                                fatalError = outcome.error
                            }
                        }

                        downloaded++
                        emit(DownloadProgress(downloaded, sortedChapters.size, chapter.title, errors))
                    }

                    if (fatalError != null) {
                        throw fatalError
                    }

                    consecutiveFailedBatches =
                        if (batchErrors == batch.size) {
                            consecutiveFailedBatches + 1
                        } else {
                            0
                        }

                    if (consecutiveFailedBatches >= MAX_CONSECUTIVE_FAILED_BATCHES) {
                        Log.w(
                            "DownloadRepo",
                            "Aborting download after $consecutiveFailedBatches consecutive failed batches for $storyId",
                        )
                        break
                    }

                    currentDelay =
                        if (batchErrors == 0) {
                            BASE_BATCH_DELAY_MS
                        } else {
                            (currentDelay * 2).coerceAtMost(MAX_BATCH_DELAY_MS)
                        }

                    if (batchIndex < batches.lastIndex) {
                        delay(currentDelay)
                    }
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
            transactionRunner.withWriteTransaction {
                progressDao.deleteProgress(storyId)
                chapterDao.deleteChaptersByStory(storyId)
                storyDao.deleteStory(storyId)
            }
        }
    }
