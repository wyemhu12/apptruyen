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
 * Web scraper cho sstruyen.net.
 * Chapter URL sạch: /truyen/{slug}/chuong-{N}
 * Pagination: /truyen/{slug}/page/{N}
 */
@Singleton
class SsTruyenScraper
    @Inject
    constructor(
        client: OkHttpClient,
    ) : BaseScraper(client) {

        override val sourceId = "sstruyen"
        override val sourceName = "SsTruyen"
        override val baseUrl = BASE_URL

        override val contentSelectors =
            listOf(
                "#chapter-c",
                ".chapter-c",
                "#chapter-content",
                ".chapter-content",
                "#content",
                ".content",
                "div.text-justify",
                ".reading-content",
                ".truyen-content",
            )

        companion object {
            const val BASE_URL = "https://sstruyen.net"

            // Regex patterns
            private val CHAPTER_NUMBER_REGEX = Regex("/chuong-(\\d+)")
            private val GENRE_SLUG_REGEX = Regex("/the-loai/([a-z0-9-]+)")
            private val CHAPTER_COUNT_REGEX = Regex("(\\d+)\\s*chương", RegexOption.IGNORE_CASE)
            private val PAGE_NUMBER_REGEX = Regex("/page/(\\d+)")
            private val STORY_SLUG_REGEX = Regex("/truyen/([a-z0-9-]+)")
        }

        /**
         * Tìm kiếm truyện.
         * URL: /tim-kiem?tukhoa={keyword}
         */
        override suspend fun search(
            keyword: String,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                val url = "$BASE_URL/search?s=$encoded"
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
         * URL: /truyen/{slug}
         * Metadata từ OG tags + HTML.
         */
        override suspend fun getStoryDetail(storyUrl: String): Story =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(storyUrl)

                // Title từ breadcrumb hoặc h1
                val title =
                    doc
                        .select("h1, .title")
                        .first()
                        ?.text()
                        ?.trim()
                        ?: doc
                            .select("meta[property=og:title]")
                            .attr("content")
                            .substringBefore(" - ")
                            .trim()
                            .ifBlank { "Đang cập nhật" }

                // Tác giả
                val author =
                    doc
                        .select("a[href*=tac-gia]")
                        .first()
                        ?.text()
                        ?.trim() ?: ""

                // Thể loại — scope vào info container để tránh lấy nhầm từ nav menu
                val infoContainer = doc.select(".info, .book-info, .story-info, .each_truyen").first() ?: doc
                val genres =
                    infoContainer
                        .select("a[href*=/the-loai/]")
                        .map { it.text().trim() }
                        .filter {
                            it.isNotBlank() && it != "Thể loại"
                        }.distinct()

                // Cover image: SST dùng CSS background trong .wallpaper, KHÔNG có <img> tag
                // <div class="wallpaper" style="background:url(https://...cover.webp) center no-repeat;...">
                val wallpaperStyle = doc.select(".wallpaper[style]").first()?.attr("style") ?: ""
                val bgUrlMatch = Regex("""url\(([^)]+)\)""").find(wallpaperStyle)
                val coverImageUrl =
                    validateCoverUrl(bgUrlMatch?.groupValues?.get(1))
                        .ifEmpty { validateCoverUrl(doc.select("meta[property=og:image]").attr("content")) }

                // Tổng chương lấy từ "Chương N" text hoặc OG description
                val ogDesc = doc.select("meta[property=og:description]").attr("content")
                val chapterFromPage =
                    Regex("Chương\\s*(\\d+)", RegexOption.IGNORE_CASE)
                        .findAll(doc.text())
                        .maxByOrNull { it.groupValues[1].toIntOrNull() ?: 0 }
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0
                val totalChaptersFromOg =
                    CHAPTER_COUNT_REGEX
                        .find(ogDesc)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0
                val totalChapters = maxOf(chapterFromPage, totalChaptersFromOg)

                // Trạng thái: SST dùng <span class="hoan-thanh">Full</span> hoặc <span class="hoan-thanh-mau">Full</span>
                val hasFullBadge =
                    doc
                        .select(
                            "span.hoan-thanh, .hoan-thanh, span.hoan-thanh-mau, .hoan-thanh-mau",
                        ).isNotEmpty()
                // Chỉ check status badge, không check toàn page text để tránh false positive
                val statusText = doc.select(".chi-tiet.tt-status, .tt-status").text()
                val status = if (hasFullBadge) "Hoàn thành" else normalizeStatus(statusText)

                // Description: dùng .desc-text (mô tả thật), fallback og:description (SEO text)
                val descText =
                    doc
                        .select(".desc-text")
                        .first()
                        ?.let { el ->
                            el.select(".short-content").remove() // bỏ header "GIỚI THIỆU"
                            cleanHtml(el.html())
                        }?.trim()
                        ?.ifBlank { null }
                val description = descText ?: ogDesc.ifBlank { "" }

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
         * Chapter links: /truyen/{slug}/chuong-{N}
         * Pagination: /truyen/{slug}/page/{N}
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

                val totalPages = getTotalChapterPages(doc, storyUrl)

                StorySource.FirstPageResult(chapters = chapters, totalPages = totalPages)
            }

        /**
         * Crawl các trang chương còn lại.
         * URL: /truyen/{slug}/page/{N}
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
                                val pageUrl = "$BASE_URL/truyen/$slug/page/$page"
                                val pageDoc = fetchDocumentWithRetry(pageUrl)
                                val chapters =
                                    parseChapterLinks(pageDoc)
                                        .sortedBy { it.number }
                                        .map { it.toChapter(slug) }
                                if (chapters.isNotEmpty()) {
                                    onBatchReady(chapters)
                                }
                            } catch (e: Exception) {
                                Log.d("SsTruyen", "Chapter page $page crawl failed", e)
                            }
                        }
                    }
                }
            }
        }

        /**
         * Truyện hoàn thành.
         * URL: /truyen-hoan-thanh
         */
        override suspend fun getCompletedStories(page: Int): List<Story> =
            withContext(Dispatchers.IO) {
                val url =
                    if (page <= 1) {
                        "$BASE_URL/truyen-hoan-thanh"
                    } else {
                        "$BASE_URL/truyen-hoan-thanh/page/$page"
                    }
                parseStoryListPage(url).map { it.copy(status = "Hoàn thành") }
            }

        /**
         * Truyện theo thể loại — chỉ truyện hoàn thành.
         * SST hỗ trợ server-side filter: ?loc=hoan-thanh
         * URL: /the-loai/{slug}?loc=hoan-thanh
         * URL page N: /the-loai/{slug}/page/{N}?loc=hoan-thanh
         */
        override suspend fun getStoriesByGenre(
            genreSlug: String,
            page: Int,
            minChapters: Int,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                // SST: ?loc=hoan-thanh = chỉ hoàn thành, bỏ param = tất cả truyện
                val statusParam = if (completedOnly) "?loc=hoan-thanh" else ""
                val url =
                    if (page <= 1) {
                        "$BASE_URL/the-loai/$genreSlug$statusParam"
                    } else {
                        "$BASE_URL/the-loai/$genreSlug/page/$page$statusParam"
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
         * Danh sách thể loại.
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

                    if (name.isBlank() || name == "Thể loại" || slug == "truyen-sac") return@forEach

                    genres.add(Genre(name = name, slug = slug))
                }

                genres.distinctBy { it.slug }
            }

        // --- Private helpers ---

        private fun parseStoryList(doc: Document): List<Story> {
            val results = mutableListOf<Story>()

            // SsTruyen container: div.home-truyendecu hoặc div.each_truyen
            // Fallback: div.item, article
            val containers = doc.select("div.home-truyendecu, div.each_truyen, div.item, div.story-item, article")

            // Nếu không tìm được container chuẩn, thử tìm theo cấu trúc caption + link
            val items =
                if (containers.isNotEmpty()) {
                    containers
                } else {
                    doc.select("div:has(> .caption a[href*=/truyen/]), div:has(> h3 a[href*=/truyen/])")
                }

            items.forEach { item ->
                // Title: .caption > a, h3.story-title a, hoặc h3 > a
                val titleEl =
                    item.select(".caption a[href*=/truyen/], h3.story-title a, h3 a[href*=/truyen/]").firstOrNull()
                        ?: item.select("a[href*=/truyen/]").firstOrNull { it.text().length >= 5 }
                        ?: return@forEach

                val href = titleEl.attr("abs:href").ifEmpty { titleEl.attr("href") }
                val title = titleEl.text().trim()

                if (title.isBlank() || href.isBlank()) return@forEach
                if (href.contains("/chuong-") || href.contains("/page/") || href.contains("#")) return@forEach
                if (title.length < 3) return@forEach

                val slug = extractStorySlug(href)
                if (slug.isBlank()) return@forEach

                // Cover image: .each_truyen img hoặc img đầu tiên (SST dùng src trực tiếp)
                val coverImg = item.select(".each_truyen img, a.cover img, .cover img, img").firstOrNull()
                val coverUrl =
                    validateCoverUrl(
                        coverImg?.let { img ->
                            img
                                .attr("abs:src")
                                .ifEmpty { img.attr("src") }
                                .ifEmpty { img.attr("data-src") }
                        },
                    )

                // Author
                val author =
                    item
                        .select("a[href*=tac-gia]")
                        .firstOrNull()
                        ?.text()
                        ?.trim() ?: ""

                // Số chương: .chi-tiet.tt-status small chứa text "Chương 1547"
                // Hoặc fallback: tìm "N chương" trong text
                val statusSmall = item.select(".chi-tiet.tt-status small, .tt-status small, small").firstOrNull()
                val statusSmallText = statusSmall?.text() ?: ""
                val itemText = item.text()

                val chapterFromSmall =
                    Regex("Chương\\s*(\\d+)", RegexOption.IGNORE_CASE)
                        .find(statusSmallText)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                val totalChapters =
                    chapterFromSmall
                        ?: CHAPTER_COUNT_REGEX
                            .find(itemText)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                        ?: Regex("Số chương\\s*:\\s*(\\d+)")
                            .find(itemText)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                        ?: 0

                // Status: <span class="hoan-thanh">Full</span> trong small, hoặc text
                val hasFullBadge = item.select("span.hoan-thanh, .hoan-thanh").isNotEmpty()
                val status = if (hasFullBadge) "Hoàn thành" else normalizeStatus(statusSmallText)

                // Thể loại
                val genres =
                    item.select("a[href*=/the-loai/]").map { it.text().trim() }.filter {
                        it.isNotBlank() && it != "Thể loại"
                    }

                results.add(
                    Story(
                        id = slug,
                        slug = slug,
                        title = title,
                        author = author,
                        genres = genres,
                        url = href.ensureAbsolute(),
                        totalChapters = totalChapters,
                        coverImageUrl = coverUrl,
                        sourceId = sourceId,
                        status = status,
                    ),
                )
            }

            // Fallback: parse kiểu cũ nếu không tìm được container
            if (results.isEmpty()) {
                doc.select("a[href*=/truyen/]").forEach { el ->
                    val href = el.attr("abs:href").ifEmpty { el.attr("href") }
                    val title = el.text().trim()

                    if (title.isBlank() || href.isBlank()) return@forEach
                    if (href.contains("/chuong-") || href.contains("/page/") || href.contains("#")) return@forEach
                    if (title.length < 3) return@forEach

                    val slug = extractStorySlug(href)
                    if (slug.isBlank()) return@forEach

                    val parent = el.parents().firstOrNull { it.tagName() in listOf("li", "div", "article") }
                    val parentText = parent?.text() ?: ""

                    val totalChapters =
                        Regex("Chương\\s*(\\d+)", RegexOption.IGNORE_CASE)
                            .find(parentText)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                            ?: CHAPTER_COUNT_REGEX
                                .find(parentText)
                                ?.groupValues
                                ?.get(1)
                                ?.toIntOrNull()
                            ?: 0

                    val coverImg = parent?.select("img")?.firstOrNull()
                    val coverUrl =
                        validateCoverUrl(
                            coverImg?.let { img ->
                                img.attr("abs:src").ifEmpty { img.attr("src") }
                            },
                        )

                    val hasFullBadge = parent?.select("span.hoan-thanh")?.isNotEmpty() == true
                    val status = if (hasFullBadge) "Hoàn thành" else normalizeStatus(parentText)

                    val genres =
                        parent?.select("a[href*=/the-loai/]")?.map { it.text().trim() }?.filter {
                            it.isNotBlank() && it != "Thể loại"
                        } ?: emptyList()

                    results.add(
                        Story(
                            id = slug,
                            slug = slug,
                            title = title,
                            genres = genres,
                            url = href.ensureAbsolute(),
                            totalChapters = totalChapters,
                            coverImageUrl = coverUrl,
                            sourceId = sourceId,
                            status = status,
                        ),
                    )
                }
            }

            return results.distinctBy { it.id }
        }

        private suspend fun parseStoryListPage(url: String): List<Story> {
            val doc = fetchDocumentWithRetry(url)
            return parseStoryList(doc)
        }

        private fun parseChapterLinks(doc: Document): List<ChapterInfo> {
            val chapters = mutableListOf<ChapterInfo>()
            val seen = mutableSetOf<Int>()

            doc.select("a[href*=/chuong-]").forEach { el ->
                val href = el.attr("abs:href").ifEmpty { el.attr("href") }
                val text = el.text().trim()

                // Extract chapter number: /chuong-{N}
                val numberMatch = CHAPTER_NUMBER_REGEX.find(href)
                val number = numberMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach

                if (number in seen) return@forEach
                seen.add(number)

                // Bỏ qua navigation links
                if (text.contains("Chương trước", ignoreCase = true) ||
                    text.contains("Chương tiếp", ignoreCase = true)
                ) {
                    return@forEach
                }

                val chapterTitle = text.ifBlank { "Chương $number" }
                chapters.add(ChapterInfo(number, chapterTitle, href.ensureAbsolute()))
            }

            return chapters.sortedBy { it.number }
        }

        private fun getTotalChapterPages(
            doc: Document,
            storyUrl: String,
        ): Int {
            // SsTruyen pagination: /truyen/{slug}/page/{N}, link "Cuối" chỉ trang cuối
            val lastLink =
                doc.select("a[href*=/page/]").firstOrNull { el ->
                    val text = el.text().trim()
                    text.contains("Cuối", ignoreCase = true)
                }

            if (lastLink != null) {
                val href = lastLink.attr("href")
                val match = PAGE_NUMBER_REGEX.find(href)
                val pages = match?.groupValues?.get(1)?.toIntOrNull()
                if (pages != null && pages > 0) return pages
            }

            // Fallback: tìm số trang lớn nhất từ pagination links
            val maxPage =
                doc
                    .select("a[href*=/page/]")
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

        private fun extractStorySlug(url: String): String {
            val match = STORY_SLUG_REGEX.find(url)
            return match?.groupValues?.get(1) ?: url.trimEnd('/').substringAfterLast("/")
        }
    }
