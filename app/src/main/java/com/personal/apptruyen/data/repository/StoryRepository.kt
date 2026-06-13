package com.personal.apptruyen.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.personal.apptruyen.data.local.ChapterDao
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.local.entity.ChapterEntity
import com.personal.apptruyen.data.local.entity.StoryEntity
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.remote.ScraperCircuitBreaker
import com.personal.apptruyen.data.remote.StorySource
import com.personal.apptruyen.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoryRepository
    @Inject
    constructor(
        private val sources: Set<@JvmSuppressWildcards StorySource>,
        private val storyDao: StoryDao,
        private val chapterDao: ChapterDao,
        @ApplicationScope private val appScope: CoroutineScope,
        private val dataStore: DataStore<Preferences>,
        private val circuitBreaker: ScraperCircuitBreaker,
    ) : IStoryRepository {
        // ---- Genre cache (in-memory, 1-hour TTL) ----
        private var cachedGenres: List<Genre>? = null
        private var cachedGenresTimestamp: Long = 0L
        private val genreCacheTtlMs = 60 * 60 * 1000L // 1 hour

        // Timeout per nguồn: tránh 1 nguồn chậm kéo chậm tất cả
        private val perSourceTimeoutMs = 10_000L

        // Số trang load cùng lúc từ mỗi source mỗi lần "load more"
        // Giúp có đủ truyện unique sau dedup (4 sources × 20 truyện/trang × 3 trang = ~240 → dedup ~40-60 unique)
        private val pagesPerBatch = 3

        // ---- Multi-source helper ----

        /**
         * Lấy danh sách nguồn đang bật từ DataStore preferences.
         * Nếu chưa có config, mặc định bật tất cả.
         */
        private suspend fun activeSources(): Set<@JvmSuppressWildcards StorySource> {
            val enabledIds =
                try {
                    dataStore.data
                        .first()[SettingsKeys.ENABLED_SOURCES]
                        ?.split(",")
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                } catch (_: Exception) {
                    null
                } ?: return sources
            return sources.filter { it.sourceId in enabledIds }.ifEmpty { sources }.toSet()
        }

        /**
         * Find the source that owns a given URL based on baseUrl matching.
         * Throws if no source matches — prevents fetching from wrong source.
         */
        private fun sourceFor(url: String): StorySource =
            sources.firstOrNull { url.contains(it.baseUrl.removePrefix("https://")) }
                ?: throw IllegalArgumentException("Không tìm được nguồn phù hợp cho URL: $url")

        /**
         * Smart dedup: cùng truyện từ nhiều nguồn (slug trùng) → giữ bản có nhiều chương nhất.
         * Tránh hiện trùng kết quả trong search/browse.
         */
        private fun smartDedup(stories: List<Story>): List<Story> =
            stories
                .groupBy { it.slug.ifBlank { it.id } }
                .map { (_, group) ->
                    group.maxByOrNull { it.totalChapters } ?: group.first()
                }

        // ---- Browse: Completed stories (aggregated, batch-loaded) ----

        /**
         * Load truyện hoàn thành. `page` là logical page (1, 2, 3...).
         * Mỗi logical page load [pagesPerBatch] pages thực từ mỗi source song song.
         * Ví dụ: page=1 → load source pages 1,2,3. page=2 → load source pages 4,5,6.
         */
        override suspend fun getCompletedStories(page: Int): List<Story> =
            supervisorScope {
                val startPage = (page - 1) * pagesPerBatch + 1
                val pageRange = startPage until (startPage + pagesPerBatch)

                activeSources()
                    .filter {
                        // Skip sources with open circuit breaker
                        circuitBreaker.getState(it.sourceId) != ScraperCircuitBreaker.State.OPEN
                    }.ifEmpty { activeSources() }
                    .map { source ->
                        async {
                            try {
                                withTimeoutOrNull(perSourceTimeoutMs) {
                                    // Fetch multiple pages from this source in parallel
                                    val pages =
                                        pageRange.map { p ->
                                            async {
                                                try {
                                                    source.getCompletedStories(p)
                                                } catch (_: Exception) {
                                                    emptyList()
                                                }
                                            }
                                        }
                                    pages.awaitAll().flatten()
                                }?.also { circuitBreaker.recordSuccess(source.sourceId) } ?: run {
                                    Log.d("StoryRepo", "${source.sourceName} completedStories timeout")
                                    circuitBreaker.recordFailure(source.sourceId)
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                Log.d("StoryRepo", "${source.sourceName} completedStories failed", e)
                                circuitBreaker.recordFailure(source.sourceId)
                                emptyList()
                            }
                        }
                    }.awaitAll()
                    .flatten()
                    .let { smartDedup(it) }
                // NOTE: Không cần lọc status ở đây — mỗi source đã force status="Hoàn thành"
                // hoặc lọc server-side (TC/SST .copy(status="Hoàn thành"), TTV fns=ht)
            }

        /**
         * Load truyện theo thể loại. Batch-load giống getCompletedStories.
         *
         * minChapters: lọc ở repo level (không phải scraper) vì genre page
         * thường không hiển thị số chương → totalChapters=0 → bị lọc nhầm.
         * Chỉ lọc story có totalChapters > 0 (có data).
         */
        override suspend fun getStoriesByGenre(
            genreSlug: String,
            page: Int,
            minChapters: Int,
            completedOnly: Boolean,
        ): List<Story> =
            supervisorScope {
                val startPage = (page - 1) * pagesPerBatch + 1
                val pageRange = startPage until (startPage + pagesPerBatch)

                val allResults =
                    activeSources()
                        .map { source ->
                            async {
                                try {
                                    withTimeoutOrNull(perSourceTimeoutMs) {
                                        val pages =
                                            pageRange.map { p ->
                                                async {
                                                    try {
                                                        // Forward completedOnly cho từng scraper — mỗi nguồn xử lý khác nhau:
                                                        // TruyenCom: /truyen-{slug}/ vs /truyen-{slug}/full/
                                                        // TTV: bỏ/thêm &fns=ht
                                                        // SST: bỏ/thêm ?loc=hoan-thanh
                                                        // TruyenFull: /the-loai/{slug}/ vs /the-loai/{slug}/hoan/
                                                        source.getStoriesByGenre(genreSlug, p, 0, completedOnly)
                                                    } catch (_: Exception) {
                                                        emptyList()
                                                    }
                                                }
                                            }
                                        pages.awaitAll().flatten()
                                    } ?: run {
                                        Log.d("StoryRepo", "${source.sourceName} getStoriesByGenre timeout")
                                        emptyList()
                                    }
                                } catch (e: Exception) {
                                    Log.d("StoryRepo", "${source.sourceName} getStoriesByGenre failed", e)
                                    emptyList()
                                }
                            }
                        }.awaitAll()
                        .flatten()
                        .let { smartDedup(it) }

                // Lọc minChapters ở repo level: chỉ lọc story có totalChapters > 0
                // (story có totalChapters=0 = genre page không hiển thị số chương → giữ lại)
                if (minChapters > 0) {
                    allResults.filter { it.totalChapters == 0 || it.totalChapters >= minChapters }
                } else {
                    allResults
                }
            }

        // ---- Genre list (merged, deduplicated by slug) ----

        override suspend fun getGenreList(): List<Genre> {
            val now = System.currentTimeMillis()
            cachedGenres?.takeIf { now - cachedGenresTimestamp < genreCacheTtlMs }?.let { return it }

            val genres =
                supervisorScope {
                    activeSources()
                        .map { source ->
                            async {
                                try {
                                    withTimeoutOrNull(perSourceTimeoutMs) {
                                        val result = source.getGenreList()
                                        Log.d("StoryRepo", "${source.sourceName} returned ${result.size} genres")
                                        result
                                    } ?: run {
                                        Log.d("StoryRepo", "${source.sourceName} getGenreList timeout")
                                        emptyList()
                                    }
                                } catch (e: Exception) {
                                    Log.w("StoryRepo", "${source.sourceName} getGenreList failed", e)
                                    emptyList()
                                }
                            }
                        }.awaitAll()
                        .flatten()
                        // Dedup by slug, ưu tiên giữ Genre có categoryId > 0 (từ TruyenCom)
                        .groupBy { it.slug }
                        .map { (_, genres) -> genres.maxByOrNull { it.categoryId } ?: genres.first() }
                }

            Log.d("StoryRepo", "Total genres after merge: ${genres.size}")
            cachedGenres = genres
            cachedGenresTimestamp = now
            return genres
        }

        // ---- Search (aggregated from all sources) ----

        override suspend fun searchStories(
            keyword: String,
            completedOnly: Boolean,
        ): List<Story> =
            supervisorScope {
                activeSources()
                    .filter {
                        circuitBreaker.getState(it.sourceId) != ScraperCircuitBreaker.State.OPEN
                    }.ifEmpty { activeSources() }
                    .map { source ->
                        async {
                            try {
                                withTimeoutOrNull(perSourceTimeoutMs) {
                                    val result = source.search(keyword, completedOnly)
                                    Log.d(
                                        "StoryRepo",
                                        "${source.sourceName} search '$keyword' returned ${result.size} results",
                                    )
                                    result
                                }?.also { circuitBreaker.recordSuccess(source.sourceId) } ?: run {
                                    Log.w(
                                        "StoryRepo",
                                        "${source.sourceName} search timeout after ${perSourceTimeoutMs}ms",
                                    )
                                    circuitBreaker.recordFailure(source.sourceId)
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                Log.w("StoryRepo", "${source.sourceName} search failed", e)
                                circuitBreaker.recordFailure(source.sourceId)
                                emptyList()
                            }
                        }
                    }.awaitAll()
                    .flatten()
                    .let { smartDedup(it) }
            }

        // ---- Story Detail ----

        override suspend fun getStoryDetail(storyUrl: String): Story {
            // Try local cache first (offline-first)
            val storyId = extractStoryId(storyUrl)
            val cached = if (storyId != null) storyDao.getStoryById(storyId) else null

            if (cached != null) {
                // Fire-and-forget background refresh — uses app-scoped coroutine
                appScope.launch {
                    try {
                        val source = sourceFor(storyUrl)
                        val fresh = source.getStoryDetail(storyUrl)
                        // Guard: không ghi đè nếu fresh data có totalChapters=0 (lỗi parse)
                        // mà cached đã có totalChapters > 0
                        val safeTotalChapters =
                            if (fresh.totalChapters == 0 && cached.totalChapters > 0) {
                                cached.totalChapters
                            } else {
                                fresh.totalChapters
                            }
                        val entity =
                            fresh.toEntity().copy(
                                isInLibrary = cached.isInLibrary,
                                addedTimestamp = cached.addedTimestamp,
                                storyReplacementsEnabled = cached.storyReplacementsEnabled,
                                storyCustomReplacements = cached.storyCustomReplacements,
                                totalChapters = safeTotalChapters,
                            )
                        storyDao.insertStory(entity)
                    } catch (e: Exception) {
                        Log.d("StoryRepo", "Background refresh failed", e)
                    }
                }
                return cached.toDomain()
            }

            // No cache — fetch from web (blocking)
            val source = sourceFor(storyUrl)
            val story = source.getStoryDetail(storyUrl)
            val existingEntity = storyDao.getStoryById(story.id)
            val entity =
                story.toEntity().let { e ->
                    if (existingEntity != null) {
                        e.copy(
                            isInLibrary = existingEntity.isInLibrary,
                            addedTimestamp = existingEntity.addedTimestamp,
                            storyReplacementsEnabled = existingEntity.storyReplacementsEnabled,
                            storyCustomReplacements = existingEntity.storyCustomReplacements,
                        )
                    } else {
                        e
                    }
                }
            storyDao.insertStory(entity)
            return story
        }

        private fun extractStoryId(url: String): String? =
            try {
                val path = url.trimEnd('/').substringAfterLast("/")
                path.takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                null
            }

        override fun observeStory(storyId: String): Flow<Story?> = storyDao.observeStory(storyId).map { it?.toDomain() }

        // ---- Chapter List ----

        /**
         * Merge scraped chapters with existing Room data (preserve downloaded content).
         * Uses IGNORE insert for new chapters + selective metadata update for existing ones.
         * NEVER overwrites downloaded content.
         */
        private suspend fun mergeAndInsertChapters(
            chapters: List<Chapter>,
            storyId: String,
        ): List<Chapter> {
            val existingMap =
                chapterDao
                    .getChaptersByStoryOnce(storyId)
                    .associateBy { it.chapterNumber }

            // Insert only NEW chapters (IGNORE = skip if already exists)
            val newChapters = chapters.filter { ch -> existingMap[ch.chapterNumber] == null }
            if (newChapters.isNotEmpty()) {
                val entities =
                    newChapters.map { ch ->
                        ChapterEntity(
                            storyId = storyId,
                            chapterNumber = ch.chapterNumber,
                            title = ch.title,
                            url = ch.url,
                            content = null,
                            isDownloaded = false,
                            downloadedTimestamp = null,
                        )
                    }
                chapterDao.insertChaptersIgnore(entities)
            }

            // Update metadata (title, url) for existing chapters — NEVER touch content
            val existingChapters = chapters.filter { ch -> existingMap[ch.chapterNumber] != null }
            for (ch in existingChapters) {
                chapterDao.updateChapterMetadata(storyId, ch.chapterNumber, ch.title, ch.url)
            }

            return chapters
        }

        /**
         * Progressive loading — Phase 1: Get first page of chapters only.
         * Returns immediately (~0.5s) so UI can display story details + first batch.
         * Also returns totalPages for background crawling in Phase 2.
         */
        override suspend fun getFirstPageChapterList(
            storyUrl: String,
            storyId: String,
        ): Pair<List<Chapter>, Int> =
            try {
                val source = sourceFor(storyUrl)
                val result = source.getFirstPageChapters(storyUrl)
                val chapters = mergeAndInsertChapters(result.chapters, storyId)
                Pair(chapters, result.totalPages)
            } catch (_: Exception) {
                val cached = chapterDao.getChaptersByStoryOnce(storyId)
                if (cached.isNotEmpty()) {
                    Pair(cached.map { it.toDomain() }, 1)
                } else {
                    throw Exception("Không có kết nối mạng và chưa có dữ liệu cache")
                }
            }

        /**
         * Progressive loading — Phase 2: Crawl remaining chapter pages in background.
         * Inserts batches into Room progressively — observeChapters Flow auto-updates UI.
         */
        override suspend fun loadRemainingChapters(
            storyUrl: String,
            storyId: String,
            totalPages: Int,
        ) {
            if (totalPages <= 1) return
            val source = sourceFor(storyUrl)
            source.getRemainingChapters(storyUrl, totalPages) { batch ->
                mergeAndInsertChapters(batch, storyId)
            }
        }

        override suspend fun getChapterList(
            storyUrl: String,
            storyId: String,
        ): List<Chapter> =
            try {
                val source = sourceFor(storyUrl)
                val chapters = source.getChapterList(storyUrl)
                mergeAndInsertChapters(chapters, storyId)
            } catch (_: Exception) {
                val cached = chapterDao.getChaptersByStoryOnce(storyId)
                if (cached.isNotEmpty()) {
                    cached.map { it.toDomain() }
                } else {
                    throw Exception("Không có kết nối mạng và chưa có dữ liệu cache")
                }
            }

        override fun observeChapters(storyId: String): Flow<List<Chapter>> =
            chapterDao.getChaptersByStory(storyId).map { list ->
                list.map { it.toDomain() }
            }

        override suspend fun getChaptersPaged(
            storyId: String,
            limit: Int,
            offset: Int,
        ): List<Chapter> =
            chapterDao.getChaptersPaged(storyId, limit, offset).map {
                it.toDomain()
            }

        override suspend fun getChapterCount(storyId: String): Int = chapterDao.getChapterCount(storyId)

        // ---- Chapter Content ----

        override suspend fun getChapterContent(
            storyId: String,
            chapterNumber: Int,
            chapterUrl: String,
        ): String {
            // Try offline first
            val cached = chapterDao.getChapter(storyId, chapterNumber)
            if (cached?.isDownloaded == true && !cached.content.isNullOrBlank()) {
                return cached.content
            }
            // Try fetching from web — route to correct source
            return try {
                val source = sourceFor(chapterUrl)
                source.getChapterContent(chapterUrl)
            } catch (e: Exception) {
                // Offline fallback: if chapter has any cached content, use it
                if (!cached?.content.isNullOrBlank()) {
                    cached?.content
                        ?: throw Exception(
                            "Chương này chưa được tải. Vui lòng kết nối mạng hoặc tải chương trước khi đọc offline.",
                        )
                } else {
                    throw Exception(
                        "Chương này chưa được tải. Vui lòng kết nối mạng hoặc tải chương trước khi đọc offline.",
                    )
                }
            }
        }

        override suspend fun addToLibrary(storyId: String) {
            storyDao.addToLibrary(storyId)
        }

        // ---- Mappers ----

        private fun Story.toEntity() =
            StoryEntity(
                id = id,
                slug = slug,
                title = title,
                author = author,
                genres = genres.joinToString(","),
                description = description,
                url = url,
                totalChapters = totalChapters,
                coverImageUrl = coverImageUrl,
                sourceId = sourceId,
                status = status,
            )

        private fun StoryEntity.toDomain() =
            Story(
                id = id,
                slug = slug,
                title = title,
                author = author,
                genres = genres.split(",").filter { it.isNotBlank() },
                description = description,
                url = url,
                totalChapters = totalChapters,
                coverImageUrl = coverImageUrl,
                sourceId = sourceId,
                status = status,
            )

        private fun com.personal.apptruyen.data.local.entity.ChapterEntity.toDomain() =
            Chapter(
                storyId = storyId,
                chapterNumber = chapterNumber,
                title = title,
                url = url,
                content = content,
                isDownloaded = isDownloaded,
            )

        // ---- Refresh ----

        override suspend fun refreshLibraryStories() =
            coroutineScope {
                val semaphore = Semaphore(3)
                val libraryStories = storyDao.getLibraryStoriesOnce()
                libraryStories
                    .map { story ->
                        launch {
                            semaphore.withPermit {
                                try {
                                    val source = sourceFor(story.url)
                                    val fresh = source.getStoryDetail(story.url)
                                    val entity =
                                        fresh.toEntity().copy(
                                            isInLibrary = story.isInLibrary,
                                            addedTimestamp = story.addedTimestamp,
                                            storyReplacementsEnabled = story.storyReplacementsEnabled,
                                            storyCustomReplacements = story.storyCustomReplacements,
                                        )
                                    storyDao.insertStory(entity)
                                } catch (_: Exception) {
                                    // Offline — skip
                                }
                                delay(200)
                            }
                        }
                    }.joinAll()
            }
    }

/**
 * Download progress data.
 */
data class DownloadProgress(
    val current: Int,
    val total: Int,
    val currentChapterTitle: String = "",
    val errors: Int = 0,
)
