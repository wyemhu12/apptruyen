package com.personal.apptruyen.data.remote

import android.util.Log
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Web scraper cho truyenfull.today.
 * Cùng engine 8cache với truyencom.com nhưng URL patterns khác.
 *
 * URL patterns:
 *   Chi tiết:       /{slug}/
 *   Đọc chương:     /{slug}/chuong-{N}/
 *   Phân trang ch:  /{slug}/trang-{N}/#list-chapter
 *   Thể loại full:  /the-loai/{genre}/hoan/
 *   Thể loại page:  /the-loai/{genre}/hoan/trang-{N}/
 *   Tìm kiếm:      /tim-kiem/?tukhoa={keyword}
 *   Truyện full:    /danh-sach/truyen-full/
 */
@Singleton
class TruyenFullScraper
    @Inject
    constructor(
        client: OkHttpClient,
    ) : BaseScraper(client) {

        override val sourceId = "truyenfull"
        override val sourceName = "TruyenFull"
        override val baseUrl = BASE_URL

        override val contentSelectors =
            listOf(
                "#chapter-c",
                ".chapter-c",
                "#chapter-content",
                ".chapter-content",
                "#content",
                ".content",
            )

        override val adsSelectors =
            "script, .ads, .ad, iframe, noscript, .text-center, #ads-chapter-top, .ads-unlock-container, .ads-unlock-reminder"

        companion object {
            const val BASE_URL = "https://truyenfull.today"

            // Regex patterns
            private val CHAPTER_NUMBER_REGEX = Regex("/chuong-(\\d+)/")
            private val GENRE_SLUG_REGEX = Regex("/the-loai/([a-z0-9-]+)/")
            private val PAGE_NUMBER_REGEX = Regex("trang-(\\d+)")
            private val STORY_SLUG_REGEX = Regex("today/([a-z0-9-]+(?:-\\d+)?)/")
        }

        // =====================================================================
        // StorySource implementation
        // =====================================================================

        /**
         * Tìm kiếm truyện.
         * URL: /tim-kiem/?tukhoa={keyword}
         */
        override suspend fun search(
            keyword: String,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                val url = "$BASE_URL/tim-kiem/?tukhoa=$encoded"
                val doc = fetchDocumentWithRetry(url)

                val results = parseStoryList(doc)

                val filtered =
                    if (completedOnly) {
                        results.filter {
                            it.status.contains("Hoàn thành", ignoreCase = true) ||
                                it.status.contains("Full", ignoreCase = true)
                        }
                    } else {
                        results
                    }

                filtered.map { it.copy(sourceId = sourceId) }
            }

        /**
         * Lấy chi tiết truyện.
         * URL: /{slug}/
         */
        override suspend fun getStoryDetail(storyUrl: String): Story =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(storyUrl)

                // Title: h3.title[itemprop=name]
                val title =
                    doc
                        .select("h3.title[itemprop=name]")
                        .first()
                        ?.text()
                        ?.trim()
                        ?: doc
                            .select("h3.title, h1")
                            .first()
                            ?.text()
                            ?.trim()
                        ?: doc
                            .select("meta[property=og:title]")
                            .attr("content")
                            .trim()
                            .ifBlank { "Đang cập nhật" }

                // Tác giả: a[itemprop=author]
                val author =
                    doc
                        .select("a[itemprop=author]")
                        .first()
                        ?.text()
                        ?.trim()
                        ?: doc
                            .select("a[href*=tac-gia]")
                            .first()
                            ?.text()
                            ?.trim()
                        ?: ""

                // Thể loại: a[itemprop=genre] — scope vào info để tránh nav menu
                val infoHolder = doc.select(".info-holder, .info").first() ?: doc
                val genres =
                    infoHolder
                        .select("a[itemprop=genre], a[href*=/the-loai/]")
                        .map { it.text().trim() }
                        .filter { it.isNotBlank() && it != "Thể loại" }
                        .distinct()

                // Cover image: .book img[itemprop=image]
                val coverImageUrl =
                    validateCoverUrl(
                        doc
                            .select(".book img[itemprop=image], .book img, .books img")
                            .firstOrNull()
                            ?.let { img ->
                                img.attr("abs:src").ifEmpty { img.attr("src") }
                            },
                    ).ifEmpty {
                        validateCoverUrl(doc.select("meta[property=og:image]").attr("content"))
                    }

                // Mô tả: .desc-text[itemprop=description]
                val descEl = doc.select(".desc-text[itemprop=description], .desc-text").first()
                val description = descEl?.let { cleanHtml(it.html()) }?.trim() ?: ""

                // Trạng thái: .info span.text-success chứa "Full"
                val statusText = doc.select(".info span.text-success, .info span.text-primary").first()?.text() ?: ""
                val status = normalizeStatus(statusText)

                // Tổng chương: lấy từ pagination (trang cuối × 50) hoặc đếm links
                val totalPages = getTotalChapterPages(doc)
                val firstPageChapters = parseChapterLinks(doc)
                val totalChapters =
                    if (totalPages > 1) {
                        try {
                            val slug = extractStorySlug(storyUrl)
                            val lastPageUrl = "$BASE_URL/$slug/trang-$totalPages/"
                            val lastDoc = fetchDocumentWithRetry(lastPageUrl)
                            val lastPageChapters = parseChapterLinks(lastDoc)
                            val maxFromLast = lastPageChapters.maxOfOrNull { it.number } ?: 0
                            val maxFromFirst = firstPageChapters.maxOfOrNull { it.number } ?: 0
                            maxOf(maxFromLast, maxFromFirst)
                        } catch (_: Exception) {
                            totalPages * 50
                        }
                    } else {
                        firstPageChapters.size
                    }

                val slug = extractStorySlug(storyUrl)

                Story(
                    id = slug,
                    slug = slug,
                    title = title,
                    author = author,
                    genres = genres,
                    description = description,
                    url = storyUrl.ensureAbsolute(),
                    totalChapters = totalChapters,
                    coverImageUrl = coverImageUrl,
                    sourceId = sourceId,
                    status = status,
                )
            }

        /**
         * Lấy trang đầu danh sách chương.
         * Chapters: #list-chapter .list-chapter li a
         * Pagination: ul.pagination.pagination-sm a[href*=trang-]
         */
        override suspend fun getFirstPageChapters(storyUrl: String): StorySource.FirstPageResult =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(storyUrl)
                val slug = extractStorySlug(storyUrl)
                val seen = mutableSetOf<Int>()

                val chapters =
                    parseChapterLinks(doc)
                        .filter { seen.add(it.number) }
                        .sortedBy { it.number }
                        .map { it.toChapter(slug) }

                val totalPages = getTotalChapterPages(doc)

                StorySource.FirstPageResult(chapters = chapters, totalPages = totalPages)
            }

        /**
         * Crawl các trang chương còn lại.
         * URL: /{slug}/trang-{N}/
         */
        override suspend fun getRemainingChapters(
            storyUrl: String,
            totalPages: Int,
            onBatchReady: suspend (List<Chapter>) -> Unit,
        ) = withContext(Dispatchers.IO) {
            if (totalPages <= 1) return@withContext

            val slug = extractStorySlug(storyUrl)
            val semaphore = Semaphore(3)

            coroutineScope {
                for (page in 2..totalPages) {
                    launch {
                        semaphore.withPermit {
                            try {
                                delay(crawlDelayMs)
                                val pageUrl = "$BASE_URL/$slug/trang-$page/"
                                val pageDoc = fetchDocumentWithRetry(pageUrl)
                                val chapters =
                                    parseChapterLinks(pageDoc)
                                        .sortedBy { it.number }
                                        .map { it.toChapter(slug) }
                                if (chapters.isNotEmpty()) {
                                    onBatchReady(chapters)
                                }
                            } catch (e: Exception) {
                                Log.d("TruyenFull", "Chapter page $page crawl failed", e)
                            }
                        }
                    }
                }
            }
        }

        /**
         * Truyện hoàn thành mới nhất.
         * URL: /danh-sach/truyen-full/ hoặc /danh-sach/truyen-full/trang-{N}/
         */
        override suspend fun getCompletedStories(page: Int): List<Story> =
            withContext(Dispatchers.IO) {
                val url =
                    if (page <= 1) {
                        "$BASE_URL/danh-sach/truyen-full/"
                    } else {
                        "$BASE_URL/danh-sach/truyen-full/trang-$page/"
                    }
                parseStoryListPage(url).map { it.copy(status = "Hoàn thành") }
            }

        /**
         * Truyện hoàn thành theo thể loại.
         * TruyenFull hỗ trợ native filter: /the-loai/{slug}/hoan/
         * URL: /the-loai/{slug}/hoan/ hoặc /the-loai/{slug}/hoan/trang-{N}/
         */
        override suspend fun getStoriesByGenre(
            genreSlug: String,
            page: Int,
            minChapters: Int,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                // TruyenFull: /the-loai/{slug}/hoan/ = chỉ hoàn thành, /the-loai/{slug}/ = tất cả
                val statusPath = if (completedOnly) "hoan/" else ""
                val url =
                    if (page <= 1) {
                        "$BASE_URL/the-loai/$genreSlug/$statusPath"
                    } else {
                        "$BASE_URL/the-loai/$genreSlug/${statusPath}trang-$page/"
                    }
                val stories = parseStoryListPage(url)
                val withStatus = if (completedOnly) stories.map { it.copy(status = "Hoàn thành") } else stories
                if (minChapters > 0) {
                    withStatus.filter { it.totalChapters >= minChapters }
                } else {
                    withStatus
                }
            }

        /**
         * Danh sách thể loại từ nav dropdown menu.
         */
        override suspend fun getGenreList(): List<Genre> =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(BASE_URL)
                val genres = mutableListOf<Genre>()

                doc.select("a[href*=/the-loai/]").forEach { el ->
                    val href = el.attr("href")
                    val name = el.text().trim()

                    val slugMatch = GENRE_SLUG_REGEX.find(href)
                    val slug = slugMatch?.groupValues?.get(1) ?: return@forEach

                    if (name.isBlank() || name == "Thể loại") return@forEach

                    genres.add(Genre(name = name, slug = slug))
                }

                genres.distinctBy { it.slug }
            }

        // =====================================================================
        // Private helpers
        // =====================================================================

        /**
         * Parse danh sách truyện từ trang listing (tìm kiếm, thể loại, truyện full).
         * TruyenFull dùng .row[itemscope itemtype="https://schema.org/Book"]
         */
        private fun parseStoryList(doc: Document): List<Story> {
            val results = mutableListOf<Story>()

            // Primary: .row có itemscope Book (trang thể loại, tìm kiếm)
            val rows =
                doc
                    .select(".list-truyen .row[itemscope], .row[itemscope]")
                    .filter { it.attr("itemtype").contains("Book") || it.select("h3.truyen-title").isNotEmpty() }

            val items =
                rows.ifEmpty {
                    // Fallback: rows chứa .truyen-title
                    doc.select(".row:has(h3.truyen-title)")
                }

            items.forEach { row ->
                val titleEl =
                    row.select("h3.truyen-title a, .truyen-title a").firstOrNull()
                        ?: return@forEach

                val href = titleEl.attr("abs:href").ifEmpty { titleEl.attr("href") }
                val title = titleEl.text().trim()

                if (title.isBlank() || href.isBlank()) return@forEach
                if (href.contains("/chuong-") || href.contains("#")) return@forEach

                val slug = extractStorySlug(href)
                if (slug.isBlank()) return@forEach

                // Author
                val author =
                    row
                        .select("span.author[itemprop=author], a[href*=tac-gia]")
                        .firstOrNull()
                        ?.text()
                        ?.trim() ?: ""

                // Cover: lazyimg data-image
                val coverUrl =
                    row
                        .select("div[data-image], div.lazyimg[data-image]")
                        .firstOrNull()
                        ?.attr("data-image")
                        ?.takeIf { it.startsWith("http") }
                        ?: ""

                // Chapter count: lấy từ text link chương mới nhất "Chương N"
                val chapterText = row.select("a[href*=chuong-]").lastOrNull()?.text() ?: ""
                val totalChapters =
                    Regex("(?:Chương\\s*)(\\d+)", RegexOption.IGNORE_CASE)
                        .find(chapterText)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0

                // Status: label-full badge
                val hasFullLabel = row.select("span.label-full, .label-full").isNotEmpty()
                val status = if (hasFullLabel) "Hoàn thành" else "Đang ra"

                results.add(
                    Story(
                        id = slug,
                        slug = slug,
                        title = title,
                        author = author,
                        genres = emptyList(),
                        url = href.ensureAbsolute(),
                        totalChapters = totalChapters,
                        coverImageUrl = coverUrl,
                        sourceId = sourceId,
                        status = status,
                    ),
                )
            }

            return results.distinctBy { it.id }
        }

        private suspend fun parseStoryListPage(url: String): List<Story> {
            val doc = fetchDocumentWithRetry(url)
            return parseStoryList(doc)
        }

        /**
         * Parse chapter links từ #list-chapter.
         * Selectors: #list-chapter .list-chapter li a[href*=chuong-]
         */
        private fun parseChapterLinks(doc: Document): List<ChapterInfo> {
            val chapters = mutableListOf<ChapterInfo>()
            val seen = mutableSetOf<Int>()

            // Primary: chapter list container
            val container = doc.select("#list-chapter").first() ?: doc
            container.select("a[href*=/chuong-]").forEach { el ->
                val href = el.attr("abs:href").ifEmpty { el.attr("href") }
                val text = el.text().trim()

                // Extract chapter number: /chuong-{N}/
                val numberMatch = CHAPTER_NUMBER_REGEX.find(href)
                val number = numberMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach

                if (number in seen) return@forEach
                seen.add(number)

                // Bỏ qua navigation links
                if (text.contains("Chương trước", ignoreCase = true) ||
                    text.contains("Chương tiếp", ignoreCase = true) ||
                    text.contains("trước", ignoreCase = true) &&
                    text.length < 15
                ) {
                    return@forEach
                }

                val chapterTitle = text.ifBlank { "Chương $number" }
                chapters.add(ChapterInfo(number, chapterTitle, href.ensureAbsolute()))
            }

            return chapters.sortedBy { it.number }
        }

        /**
         * Lấy tổng số trang chapter từ pagination.
         * Selector: ul.pagination.pagination-sm a[href*=trang-]
         * Tìm max(trang-N) từ tất cả pagination links.
         */
        private fun getTotalChapterPages(doc: Document): Int {
            val maxPage =
                doc
                    .select("ul.pagination a[href*=trang-]")
                    .mapNotNull { el ->
                        val href = el.attr("href")
                        PAGE_NUMBER_REGEX
                            .find(href)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                    }.maxOrNull()

            return maxPage ?: 1
        }

        /**
         * Extract story slug từ URL.
         * URL: https://truyenfull.today/{slug}/ hoặc /{slug}/chuong-N/
         */
        private fun extractStorySlug(url: String): String {
            // Try regex first
            val match = STORY_SLUG_REGEX.find(url)
            if (match != null) return match.groupValues[1]

            // Fallback: parse path
            val path = url.trimEnd('/').substringAfterLast("/")
            return if (path.startsWith("chuong-") || path.startsWith("trang-")) {
                // URL is /{slug}/chuong-N/ or /{slug}/trang-N/ — get parent
                val segments = url.trimEnd('/').split("/").filter { it.isNotBlank() }
                segments.dropLast(1).lastOrNull() ?: path
            } else {
                path
            }
        }
    }
