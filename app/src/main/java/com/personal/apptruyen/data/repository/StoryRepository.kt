package com.personal.apptruyen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.personal.apptruyen.data.local.ChapterDao
import com.personal.apptruyen.data.local.DatabaseTransactionRunner
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.local.entity.ChapterEntity
import com.personal.apptruyen.data.local.entity.StoryEntity
import com.personal.apptruyen.data.local.migrateWikidichEnabledSourcesIfNeeded
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.ChapterContentResult
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.remote.ScraperCircuitBreaker
import com.personal.apptruyen.data.remote.ScraperException
import com.personal.apptruyen.data.remote.StorySource
import com.personal.apptruyen.data.remote.matchesUrl
import com.personal.apptruyen.data.repository.ISourceAuthRepository
import com.personal.apptruyen.di.ApplicationScope
import kotlinx.coroutines.CancellationException
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
import timber.log.Timber
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
        private val authRepository: ISourceAuthRepository,
        private val transactionRunner: DatabaseTransactionRunner,
    ) : IStoryRepository {
        // ---- Genre cache (in-memory, 1-hour TTL, keyed by active source IDs) ----
        private var cachedGenres: List<Genre>? = null
        private var cachedGenreSourceIds: Set<String>? = null
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
            dataStore.migrateWikidichEnabledSourcesIfNeeded()
            val enabledIds =
                try {
                    dataStore.data
                        .first()[SettingsKeys.ENABLED_SOURCES]
                        ?.split(",")
                        ?.filter { it.isNotBlank() }
                        ?.toSet()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                } ?: return sources
            return sources.filter { it.sourceId in enabledIds }.ifEmpty { sources }.toSet()
        }

        /**
         * Find the source that owns a given URL based on baseUrl matching.
         * Throws if no source matches — prevents fetching from wrong source.
         */
        private fun sourceFor(url: String): StorySource =
            sources.firstOrNull { it.matchesUrl(url) }
                ?: throw ScraperException.ParseException("Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ", url)

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
        private fun timeoutFor(source: StorySource): Long = if (source.sourceId == "wikidich") 25_000L else perSourceTimeoutMs

        private fun sourcePagesFor(
            source: StorySource,
            logicalPage: Int,
        ): Iterable<Int> {
            if (source.sourceId == "wikidich") {
                return listOf(logicalPage)
            }
            val startPage = (logicalPage - 1) * pagesPerBatch + 1
            return startPage until (startPage + pagesPerBatch)
        }

        override suspend fun getCompletedStories(page: Int): List<Story> =
            supervisorScope {
                activeSources()
                    .filter {
                        // Skip sources with open circuit breaker
                        circuitBreaker.getState(it.sourceId) != ScraperCircuitBreaker.State.OPEN
                    }.ifEmpty { activeSources() }
                    .map { source ->
                        async {
                            try {
                                val pageRange = sourcePagesFor(source, page)
                                val timeoutMs = timeoutFor(source)
                                withTimeoutOrNull(timeoutMs + 5_000L) {
                                    // Fetch pages from this source in parallel
                                    val pages =
                                        pageRange.map { p ->
                                            async {
                                                try {
                                                    withTimeoutOrNull(timeoutMs) {
                                                        source.getCompletedStories(p)
                                                    } ?: emptyList()
                                                } catch (e: Exception) {
                                                    if (e is CancellationException) throw e
                                                    if (e is ScraperException.LoginRequiredException) throw e
                                                    emptyList()
                                                }
                                            }
                                        }
                                    pages.awaitAll().flatten()
                                }?.also {
                                    Timber.tag("StoryRepo").d("%s completedStories returned %d results", source.sourceName, it.size)
                                    circuitBreaker.recordSuccess(source.sourceId)
                                    if (it.isNotEmpty()) {
                                        authRepository.clearLoginRequired(source.sourceId)
                                    }
                                } ?: run {
                                    Timber.tag("StoryRepo").d("%s completedStories timeout", source.sourceName)
                                    circuitBreaker.recordFailure(source.sourceId)
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                if (e is ScraperException.LoginRequiredException) {
                                    Timber.tag("StoryRepo").w(e, "%s completedStories requires login", source.sourceName)
                                    authRepository.markLoginRequired(source.sourceId)
                                } else {
                                    Timber.tag("StoryRepo").d(e, "%s completedStories failed", source.sourceName)
                                    circuitBreaker.recordFailure(source.sourceId)
                                }
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
                val allResults =
                    activeSources()
                        .map { source ->
                            async {
                                try {
                                    val pageRange = sourcePagesFor(source, page)
                                    val timeoutMs = timeoutFor(source)
                                    withTimeoutOrNull(timeoutMs + 5_000L) {
                                        val pages =
                                            pageRange.map { p ->
                                                async {
                                                    try {
                                                        // Forward completedOnly cho từng scraper — mỗi nguồn xử lý khác nhau:
                                                        // TTV: bỏ/thêm &fns=ht
                                                        // SST: bỏ/thêm ?loc=hoan-thanh
                                                        // Wikidich: /tim-kiem?... (kèm session nếu có)
                                                        withTimeoutOrNull(timeoutMs) {
                                                            source.getStoriesByGenre(genreSlug, p, 0, completedOnly)
                                                        } ?: emptyList()
                                                    } catch (e: Exception) {
                                                        if (e is CancellationException) throw e
                                                        if (e is ScraperException.LoginRequiredException) throw e
                                                        emptyList()
                                                    }
                                                }
                                            }
                                        pages.awaitAll().flatten()
                                    }?.also {
                                        Timber.tag("StoryRepo").d(
                                            "%s getStoriesByGenre(%s) returned %d results",
                                            source.sourceName,
                                            genreSlug,
                                            it.size,
                                        )
                                        if (it.isNotEmpty()) {
                                            authRepository.clearLoginRequired(source.sourceId)
                                        }
                                    } ?: run {
                                        Timber.tag("StoryRepo").d("%s getStoriesByGenre timeout", source.sourceName)
                                        emptyList()
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    if (e is ScraperException.LoginRequiredException) {
                                        Timber.tag("StoryRepo").w(e, "%s getStoriesByGenre requires login", source.sourceName)
                                        authRepository.markLoginRequired(source.sourceId)
                                    } else {
                                        Timber.tag("StoryRepo").d(e, "%s getStoriesByGenre failed", source.sourceName)
                                    }
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
            val activeSrcs = activeSources()
            if (activeSrcs.isEmpty()) return emptyList()
            val activeIds = activeSrcs.map { it.sourceId }.toSet()
            val now = System.currentTimeMillis()
            cachedGenres
                ?.takeIf { now - cachedGenresTimestamp < genreCacheTtlMs && cachedGenreSourceIds == activeIds }
                ?.let { return it }

            val genres =
                supervisorScope {
                    val sourceGenres =
                        activeSrcs
                            .map { source ->
                                async {
                                    try {
                                        withTimeoutOrNull(perSourceTimeoutMs) {
                                            val result = source.getGenreList()
                                            Timber.tag("StoryRepo").d("%s returned %d genres", source.sourceName, result.size)
                                            result
                                        } ?: run {
                                            Timber.tag("StoryRepo").d("%s getGenreList timeout", source.sourceName)
                                            emptyList()
                                        }
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        Timber.tag("StoryRepo").w(e, "%s getGenreList failed", source.sourceName)
                                        emptyList()
                                    }
                                }
                            }.awaitAll()

                    // Gom union tất cả thể loại theo slug và sắp xếp theo tên
                    sourceGenres
                        .flatten()
                        .groupBy { it.slug }
                        .map { (_, list) -> list.first() }
                        .sortedBy { it.name }
                }

            Timber.tag("StoryRepo").d("Total genres after merge: %d", genres.size)
            cachedGenres = genres
            cachedGenreSourceIds = activeIds
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
                                val timeoutMs = timeoutFor(source)
                                withTimeoutOrNull(timeoutMs) {
                                    val result = source.search(keyword, completedOnly)
                                    Timber.tag("StoryRepo").d(
                                        "%s search '%s' returned %d results",
                                        source.sourceName,
                                        keyword,
                                        result.size,
                                    )
                                    result
                                }?.also {
                                    circuitBreaker.recordSuccess(source.sourceId)
                                    authRepository.clearLoginRequired(source.sourceId)
                                } ?: run {
                                    Timber.tag("StoryRepo").w(
                                        "%s search timeout after %dms",
                                        source.sourceName,
                                        timeoutMs,
                                    )
                                    circuitBreaker.recordFailure(source.sourceId)
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                if (e is ScraperException.LoginRequiredException) {
                                    Timber.tag("StoryRepo").w(e, "%s search requires login", source.sourceName)
                                    authRepository.markLoginRequired(source.sourceId)
                                } else {
                                    Timber.tag("StoryRepo").w(e, "%s search failed", source.sourceName)
                                    circuitBreaker.recordFailure(source.sourceId)
                                }
                                emptyList()
                            }
                        }
                    }.awaitAll()
                    .flatten()
                    .let { smartDedup(it) }
            }

        // ---- Story Detail ----

        override suspend fun getStoryDetail(storyUrl: String): Story {
            // Try local cache first (offline-first) via URL, canonical sourceId_slug, or same-source legacy slug
            val cached = findCachedStory(storyUrl)

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
                        val safeCoverImageUrl = resolveSafeCoverUrl(fresh.coverImageUrl, cached.coverImageUrl)
                        val entity =
                            fresh.toEntity().copy(
                                id = cached.id,
                                coverImageUrl = safeCoverImageUrl,
                                isInLibrary = cached.isInLibrary,
                                addedTimestamp = cached.addedTimestamp,
                                storyReplacementsEnabled = cached.storyReplacementsEnabled,
                                storyCustomReplacements = cached.storyCustomReplacements,
                                totalChapters = safeTotalChapters,
                            )
                        storyDao.insertStory(entity)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Timber.tag("StoryRepo").d(e, "Background refresh failed")
                    }
                }
                val domainStory = cached.toDomain()
                return if (
                    domainStory.coverImageUrl.contains("data:", ignoreCase = true) ||
                    domainStory.coverImageUrl.contains("base64", ignoreCase = true)
                ) {
                    domainStory.copy(coverImageUrl = "")
                } else {
                    domainStory
                }
            }

            // No cache — fetch from web (blocking)
            val source = sourceFor(storyUrl)
            val story = source.getStoryDetail(storyUrl)
            val existingEntity =
                storyDao.getStoryById(story.id)?.takeIf { it.id.isNotBlank() }
                    ?: storyDao.getStoryById(story.slug)?.takeIf {
                        it.id.isNotBlank() && it.sourceId == story.sourceId
                    }
            val resolvedId = existingEntity?.id ?: story.id
            val resolvedStory = story.copy(id = resolvedId)
            val entity =
                resolvedStory.toEntity().let { e ->
                    if (existingEntity != null) {
                        e.copy(
                            totalChapters = resolveSafeTotalChapters(resolvedStory.totalChapters, existingEntity.totalChapters),
                            coverImageUrl = resolveSafeCoverUrl(resolvedStory.coverImageUrl, existingEntity.coverImageUrl),
                            isInLibrary = existingEntity.isInLibrary,
                            addedTimestamp = existingEntity.addedTimestamp,
                            storyReplacementsEnabled = existingEntity.storyReplacementsEnabled,
                            storyCustomReplacements = existingEntity.storyCustomReplacements,
                        )
                    } else {
                        e.copy(
                            totalChapters = resolveSafeTotalChapters(resolvedStory.totalChapters, 0),
                            coverImageUrl = resolveSafeCoverUrl(resolvedStory.coverImageUrl, null),
                        )
                    }
                }
            storyDao.insertStory(entity)
            return resolvedStory
        }

        private suspend fun findCachedStory(storyUrl: String): StoryEntity? {
            val normalizedUrl = storyUrl.trim()
            storyDao.getStoryByUrl(normalizedUrl)?.takeIf { it.id.isNotBlank() }?.let { return it }
            val trimmedUrl = normalizedUrl.trimEnd('/')
            if (trimmedUrl != normalizedUrl) {
                storyDao.getStoryByUrl(trimmedUrl)?.takeIf { it.id.isNotBlank() }?.let { return it }
            }

            val rawSlug = extractStoryId(normalizedUrl) ?: return null
            val matchedSource = sources.firstOrNull { it.matchesUrl(normalizedUrl) }

            if (matchedSource != null) {
                val canonicalId = "${matchedSource.sourceId}_$rawSlug"
                storyDao.getStoryById(canonicalId)?.takeIf { it.id.isNotBlank() }?.let { return it }

                // Tương thích ngược với truyện đã lưu từ phiên bản cũ (id = rawSlug không có tiền tố nguồn),
                // nhưng CHỈ khớp khi cùng sourceId để tránh ghi đè chéo giữa Tàng Thư Viện và SsTruyện.
                val legacyCached = storyDao.getStoryById(rawSlug)?.takeIf { it.id.isNotBlank() }
                if (legacyCached != null && legacyCached.sourceId == matchedSource.sourceId) {
                    return legacyCached
                }
                return null
            }

            // Fallback cho các nguồn cũ đã ngừng cào trực tuyến (truyencom, truyenfull...) chỉ đọc offline
            return storyDao.getStoryById(rawSlug)?.takeIf { it.id.isNotBlank() }
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
        ): List<Chapter> =
            transactionRunner.withWriteTransaction {
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

                // Update metadata (title, url) for existing chapters — do not touch content
                // Bảo toàn title của chương đã tải (isDownloaded == true), chỉ cập nhật url sang domain mới
                val existingChapters = chapters.filter { ch -> existingMap[ch.chapterNumber] != null }
                for (ch in existingChapters) {
                    val existing = existingMap[ch.chapterNumber] ?: continue
                    val titleToUpdate =
                        if (existing.isDownloaded && existing.title.isNotBlank()) {
                            existing.title
                        } else {
                            ch.title.ifBlank { existing.title }
                        }
                    val urlToUpdate = ch.url.ifBlank { existing.url }
                    if (titleToUpdate != existing.title || urlToUpdate != existing.url) {
                        chapterDao.updateChapterMetadata(storyId, ch.chapterNumber, titleToUpdate, urlToUpdate)
                    }
                }

                chapters.map { if (it.storyId == storyId) it else it.copy(storyId = storyId) }
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
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
        ): ChapterContentResult {
            // Try offline first
            val cached = chapterDao.getChapter(storyId, chapterNumber)
            val hasBoilerplateFooter =
                cached?.content?.contains("Nếu truyện không hiện chữ", ignoreCase = true) == true
            val isCorruptedBoilerplate =
                hasBoilerplateFooter && (cached.content?.length ?: 0) < 200
            if (cached?.isDownloaded == true && !cached.content.isNullOrBlank() && !isCorruptedBoilerplate) {
                val finalContent =
                    if (hasBoilerplateFooter) {
                        val stripped = stripBoilerplateFooter(cached.content)
                        chapterDao.saveChapterContent(
                            storyId = storyId,
                            chapterNumber = chapterNumber,
                            content = stripped,
                            title = cached.title,
                            url = cached.url.ifBlank { chapterUrl },
                        )
                        stripped
                    } else {
                        cached.content
                    }
                return ChapterContentResult(content = finalContent, title = cached.title)
            }
            // Try fetching from web — route to correct source
            val cachedChapterUrl = cached?.url?.takeIf { it.isNotBlank() }
            val effectiveChapterUrl =
                if (cachedChapterUrl != null && (chapterUrl.isBlank() || !chapterUrl.contains("chuong", ignoreCase = true))) {
                    cachedChapterUrl
                } else {
                    cachedChapterUrl ?: chapterUrl
                }
            return try {
                val source = sourceFor(effectiveChapterUrl)
                val result = source.getChapterContentWithTitle(effectiveChapterUrl)

                if (cached?.isDownloaded == true && isCorruptedBoilerplate) {
                    chapterDao.saveChapterContent(
                        storyId = storyId,
                        chapterNumber = chapterNumber,
                        content = result.content,
                        title = result.title?.takeIf { it.isNotBlank() } ?: cached.title,
                        url = effectiveChapterUrl,
                    )
                }

                // Update chapter title in DB if a real title was extracted
                if (!result.title.isNullOrBlank()) {
                    val existing = cached?.title ?: ""
                    val isDummy = existing.matches(Regex("^Chương \\d+$"))
                    if (isDummy || existing.isBlank()) {
                        chapterDao.updateChapterMetadata(
                            storyId = storyId,
                            chapterNumber = chapterNumber,
                            title = result.title,
                            url = effectiveChapterUrl,
                        )
                    }
                }

                result
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Offline fallback: if chapter has any cached content, use it
                if (!cached?.content.isNullOrBlank()) {
                    ChapterContentResult(content = cached.content, title = cached.title)
                } else if (e is ScraperException.ParseException && e.message?.contains("không còn hoạt động") == true) {
                    throw e
                } else {
                    throw Exception(
                        "Chương này chưa được tải. Vui lòng kết nối mạng hoặc tải chương trước khi đọc offline.",
                    )
                }
            }
        }

        private fun stripBoilerplateFooter(content: String): String =
            content
                .lines()
                .filter { line ->
                    val trimmed = line.trim()
                    trimmed.isNotBlank() &&
                        !trimmed.contains("Nếu truyện không hiện chữ", ignoreCase = true) &&
                        !trimmed.matches(Regex("^[=\\-*_~]{3,}$"))
                }.joinToString("\n\n")

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
                                            id = story.id,
                                            totalChapters = resolveSafeTotalChapters(fresh.totalChapters, story.totalChapters),
                                            coverImageUrl = resolveSafeCoverUrl(fresh.coverImageUrl, story.coverImageUrl),
                                            isInLibrary = story.isInLibrary,
                                            addedTimestamp = story.addedTimestamp,
                                            storyReplacementsEnabled = story.storyReplacementsEnabled,
                                            storyCustomReplacements = story.storyCustomReplacements,
                                        )
                                    storyDao.insertStory(entity)
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
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
) {
    val processed: Int
        get() = (current + errors).coerceAtMost(total)
}

internal fun StoryEntity.toDomain(): Story =
    Story(
        id = id,
        slug = slug,
        title = title,
        author = author,
        genres = genres.split(",").filter { it.isNotBlank() },
        description = description,
        url = url,
        totalChapters = totalChapters,
        coverImageUrl = upgradeToHttps(coverImageUrl),
        sourceId = sourceId,
        status = status,
    )

internal fun upgradeToHttps(url: String): String {
    val trimmed = url.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) &&
        !trimmed.startsWith("http://localhost", ignoreCase = true) &&
        !trimmed.startsWith("http://127.0.0.1")
    ) {
        return "https://" + trimmed.substring(7)
    }
    return trimmed
}

internal fun resolveSafeCoverUrl(
    freshCoverUrl: String,
    existingCoverUrl: String?,
): String {
    val trimmedFresh = freshCoverUrl.trim()
    val isFreshValid =
        trimmedFresh.isNotEmpty() &&
            !trimmedFresh.startsWith("data:", ignoreCase = true) &&
            !trimmedFresh.contains("base64", ignoreCase = true)
    if (isFreshValid) return upgradeToHttps(trimmedFresh)

    val trimmedExisting = existingCoverUrl?.trim().orEmpty()
    val isExistingValid =
        trimmedExisting.isNotEmpty() &&
            !trimmedExisting.startsWith("data:", ignoreCase = true) &&
            !trimmedExisting.contains("base64", ignoreCase = true)
    return if (isExistingValid) upgradeToHttps(trimmedExisting) else ""
}

internal fun resolveSafeTotalChapters(
    freshTotal: Int,
    existingTotal: Int,
): Int =
    when {
        freshTotal > 0 -> freshTotal
        existingTotal > 0 -> existingTotal
        else -> 0
    }
