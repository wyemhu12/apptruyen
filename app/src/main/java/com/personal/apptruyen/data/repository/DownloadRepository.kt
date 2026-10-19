package com.personal.apptruyen.data.repository

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
import timber.log.Timber
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
            const val WIKIDICH_SOURCE_ID = "wikidich"
            const val WIKIDICH_BATCH_SIZE = 1
            const val WIKIDICH_MIN_DELAY_MS = 15_000L
            const val WIKIDICH_MAX_DELAY_MS = 20_000L
            const val WIKIDICH_RETRY_DELAY_MS = 5_000L
            val RATE_LIMIT_HTTP_CODES = setOf(403, 429, 503)
            private val DUMMY_TITLE_REGEX = Regex("^Chương \\d+$")
        }

        internal var wikidichDelayProvider: () -> Long = {
            kotlin.random.Random.nextLong(WIKIDICH_MIN_DELAY_MS, WIKIDICH_MAX_DELAY_MS + 1)
        }

        internal fun isRateLimitHttpError(error: Throwable?): Boolean =
            error is ScraperException.HttpException && error.statusCode in RATE_LIMIT_HTTP_CODES

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
                        id = existing.id,
                        slug = fresh.slug,
                        title = fresh.title,
                        author = fresh.author,
                        genres = fresh.genres.joinToString(","),
                        description = fresh.description,
                        url = fresh.url,
                        totalChapters = resolveSafeTotalChapters(fresh.totalChapters, existing.totalChapters),
                        coverImageUrl = resolveSafeCoverUrl(fresh.coverImageUrl, existing.coverImageUrl),
                        sourceId = fresh.sourceId,
                        status = fresh.status,
                        isInLibrary = existing.isInLibrary,
                        addedTimestamp = existing.addedTimestamp,
                        storyReplacementsEnabled = existing.storyReplacementsEnabled,
                        storyCustomReplacements = existing.storyCustomReplacements,
                    )
                storyDao.insertStory(entity)
                Timber.tag("DownloadRepo").d("Snapshot metadata saved for %s", storyId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.tag("DownloadRepo").d(e, "Snapshot metadata failed for %s (offline?)", storyId)
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
                    if (source.sourceId == WIKIDICH_SOURCE_ID && isRateLimitHttpError(e)) {
                        return ChapterFetchOutcome(chapter = chapter, error = e)
                    }
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
                val isWikidich = source.sourceId == WIKIDICH_SOURCE_ID
                val batchSize = if (isWikidich) WIKIDICH_BATCH_SIZE else DOWNLOAD_BATCH_SIZE
                val batches = sortedChapters.chunked(batchSize)

                for ((batchIndex, batch) in batches.withIndex()) {
                    currentCoroutineContext().ensureActive()
                    emit(DownloadProgress(downloaded, sortedChapters.size, batch.first().title, errors))

                    val retryDelayMs = if (isWikidich) WIKIDICH_RETRY_DELAY_MS else currentDelay
                    val batchOutcomes =
                        coroutineScope {
                            batch
                                .map { chapter ->
                                    async {
                                        fetchChapterWithRetry(source, chapter, retryDelayMs)
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
                            val isDummy = chapter.title.matches(DUMMY_TITLE_REGEX)
                            val titleToSave =
                                if (!result.title.isNullOrBlank() && (isDummy || chapter.title.isBlank())) {
                                    result.title
                                } else {
                                    chapter.title
                                }
                            chapterDao.saveChapterContent(
                                storyId = storyId,
                                chapterNumber = chapter.chapterNumber,
                                content = result.content,
                                title = titleToSave,
                                url = chapter.url,
                            )

                            if (!result.title.isNullOrBlank() && (isDummy || chapter.title.isBlank())) {
                                chapterDao.updateChapterMetadata(
                                    storyId = storyId,
                                    chapterNumber = chapter.chapterNumber,
                                    title = result.title,
                                    url = chapter.url,
                                )
                            }
                            downloaded++
                        } else {
                            errors++
                            batchErrors++
                            Timber.tag("DownloadRepo").e(outcome.error, "Download error for %s", chapter.title)
                            if (
                                fatalError == null &&
                                (
                                    outcome.error is ScraperException.LoginRequiredException ||
                                        outcome.error is ScraperException.CircuitOpenException ||
                                        (isWikidich && isRateLimitHttpError(outcome.error))
                                )
                            ) {
                                fatalError = outcome.error as? ScraperException
                            }
                        }

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
                        Timber.tag("DownloadRepo").w(
                            "Aborting download after %d consecutive failed batches for %s",
                            consecutiveFailedBatches,
                            storyId,
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
                        if (isWikidich) {
                            val randomDelay = wikidichDelayProvider().coerceIn(WIKIDICH_MIN_DELAY_MS, WIKIDICH_MAX_DELAY_MS)
                            delay(randomDelay)
                        } else {
                            delay(currentDelay)
                        }
                    }
                }
            }

        override fun getDownloadedStories(): Flow<List<Story>> =
            storyDao.getDownloadedStories().map { list ->
                list.map { it.toDomain() }
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
