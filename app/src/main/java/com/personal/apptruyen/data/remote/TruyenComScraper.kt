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
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Web scraper for truyencom.com.
 * Extracts story search results, story details, chapter lists, and chapter content.
 */
@Singleton
class TruyenComScraper
    @Inject
    constructor(
        client: OkHttpClient,
        private val webViewLoader: WebViewContentLoader,
    ) : BaseScraper(client) {

        override val sourceId = "truyencom"
        override val sourceName = "TruyenCom"
        override val baseUrl = BASE_URL

        // TC uses ETag caching and shorter crawl delay
        override val useEtagCaching = true
        override val crawlDelayMs = 200L

        // TC-specific content selectors (superset of default)
        override val contentSelectors =
            listOf(
                "#chapter-c",
                ".chapter-c",
                "#js-read__content",
                "#chapter-content",
                ".chapter-content",
                "div.text-justify",
                ".truyen-content",
                "#content",
                ".content",
            )

        companion object {
            const val BASE_URL = "https://truyencom.com"

            // TC-specific regex patterns
            private val STORY_URL_REGEX = Regex("$BASE_URL/[a-z0-9-]+\\.[0-9]+/?")
            private val CHAPTER_NUMBER_REGEX = Regex("chuong-(\\d+)\\.html")
            private val SLUG_ID_REGEX = Regex("^([a-z0-9-]+)\\.(\\d+)$")
            private val GENRE_SLUG_REGEX = Regex("/truyen-([a-z0-9-]+)/")
            private val PAGINATION_LAST_REGEX = Regex("trang-(\\d+)")
            private val CHAPTER_COUNT_REGEX = Regex("(\\d+)\\s*[Cc]hương")
            private const val CHAPTERS_PER_PAGE = 50
        }

        /**
         * Search for stories by keyword.
         * URL: /tim-kiem?tukhoa={keyword}
         * Extracts title, author, and chapter count from each result row.
         */
        override suspend fun search(
            keyword: String,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                val url = "$BASE_URL/tim-kiem?tukhoa=$encoded"
                val doc = fetchDocumentWithRetry(url)

                val results = mutableListOf<Story>()

                // Try row-based parsing first (extracts author + chapter count)
                val storyRows =
                    doc
                        .select(".list-truyen .row, .row")
                        .toList()
                        .filter { it.select(".truyen-title a, h3.truyen-title a").isNotEmpty() }

                if (storyRows.isNotEmpty()) {
                    storyRows.forEach { row ->
                        val titleEl = row.select(".truyen-title a, h3.truyen-title a").first() ?: return@forEach
                        val href = titleEl.attr("abs:href").ifEmpty { titleEl.attr("href") }
                        val title = titleEl.text().trim()
                        if (title.isBlank() || href.isBlank()) return@forEach

                        val story = parseStoryLink(href, title) ?: return@forEach
                        val author =
                            row
                                .select("a[href*=tac-gia]")
                                .first()
                                ?.text()
                                ?.trim() ?: ""

                        // Extract chapter count from text like "375 Chương"
                        val chapterText = row.select(".text-info").text().ifBlank { row.text() }
                        val chapterMatch = CHAPTER_COUNT_REGEX.find(chapterText)
                        val totalChapters = chapterMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                        // TruyenCom search rows KHÔNG CÓ status badge hay text "Full"
                        // → set status="" (unknown) thay vì guess sai.
                        val status = ""

                        results.add(
                            story.copy(
                                author = author,
                                totalChapters = totalChapters,
                                sourceId = sourceId,
                                status = status,
                            ),
                        )
                    }
                } else {
                    // Fallback: simple link parsing (no chapter count / status available)
                    val storyElements =
                        doc.select(
                            ".list-truyen .truyen-title a, .col-truyen-main .truyen-title a, h3.truyen-title a",
                        )
                    if (storyElements.isEmpty()) {
                        val links =
                            doc.select("a[href]").filter { el ->
                                val elHref = el.attr("href")
                                elHref.matches(STORY_URL_REGEX) &&
                                    el.text().isNotBlank() &&
                                    !el.text().contains("Chương")
                            }
                        links.forEach { el ->
                            val story = parseStoryLink(el.attr("href"), el.text())
                            if (story != null) results.add(story)
                        }
                    } else {
                        storyElements.forEach { el ->
                            val href = el.attr("abs:href").ifEmpty { el.attr("href") }
                            val title = el.text().trim()
                            if (title.isNotBlank() && href.isNotBlank()) {
                                val story = parseStoryLink(href, title)
                                if (story != null) results.add(story)
                            }
                        }
                    }
                }

                // TruyenCom search không có status data → không filter completedOnly
                results.distinctBy { it.id }.map { it.copy(sourceId = sourceId) }
            }

        /**
         * Get full story details including chapter list.
         * URL: /{slug.id}/
         */
        override suspend fun getStoryDetail(storyUrl: String): Story =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(storyUrl)

                val title =
                    doc
                        .select("h1, .title, h3.title")
                        .first()
                        ?.text()
                        ?.trim() ?: "Đang cập nhật"
                val author =
                    doc
                        .select("a[href*=tac-gia]")
                        .first()
                        ?.text()
                        ?.trim() ?: ""
                val genres =
                    doc.select(".info-holder a[href*=truyen-]").map { it.text().trim() }.filter {
                        it.isNotBlank() && !it.contains("Full") && !it.contains("Hot")
                    }
                val descriptionEl = doc.select(".desc-text, .desc, div[itemprop=description]").first()
                val description = descriptionEl?.let { el -> cleanHtml(el.html()) } ?: ""

                // Cover image: try common selectors
                val coverImageUrl =
                    validateCoverUrl(
                        doc
                            .select("img.book, .book img, .info-holder img, div[itemprop=image] img, .books img")
                            .firstOrNull()
                            ?.let { img ->
                                img.attr("abs:src").ifEmpty { img.attr("src") }
                            },
                    )

                val chapterLinks = parseChapterLinks(doc)
                val (id, slug) = extractIdAndSlug(storyUrl)

                // Get accurate totalChapters from pagination ("Last »" link → trang-N)
                val totalPages = getTotalChapterPages(doc)
                val totalChapters =
                    if (totalPages > 1) {
                        // Fetch last page to find the highest chapter number
                        try {
                            val lastPageUrl = "${storyUrl.trimEnd('/')}/trang-$totalPages/"
                            val lastDoc = fetchDocumentWithRetry(lastPageUrl)
                            val lastPageChapters = parseChapterLinks(lastDoc)
                            val maxFromLastPage = lastPageChapters.maxOfOrNull { it.number } ?: 0
                            val maxFromFirstPage = chapterLinks.maxOfOrNull { it.number } ?: 0
                            maxOf(maxFromLastPage, maxFromFirstPage)
                        } catch (_: Exception) {
                            // Fallback: estimate from page count
                            totalPages * CHAPTERS_PER_PAGE
                        }
                    } else {
                        chapterLinks.size
                    }

                Story(
                    id = id,
                    slug = slug,
                    title = title,
                    author = author,
                    genres = genres,
                    description = description,
                    url = storyUrl.ensureAbsolute(),
                    totalChapters = totalChapters,
                    coverImageUrl = coverImageUrl,
                    sourceId = sourceId,
                )
            }

        /**
         * Get ONLY the first page of chapters + total page count.
         * Returns instantly (~0.5s) so UI can display immediately.
         */
        override suspend fun getFirstPageChapters(storyUrl: String): StorySource.FirstPageResult =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(storyUrl)
                val (storyId, _) = extractIdAndSlug(storyUrl)
                val seen = mutableSetOf<Int>()

                val firstPageChapters =
                    parseChapterLinks(doc)
                        .filter { seen.add(it.number) }
                        .sortedBy { it.number }
                        .map { it.toChapter(storyId) }

                val totalPages = getTotalChapterPages(doc)
                StorySource.FirstPageResult(chapters = firstPageChapters, totalPages = totalPages)
            }

        /**
         * Crawl remaining chapter pages (2..totalPages) in parallel with Semaphore(3).
         * Returns batches progressively via callback for real-time UI updates.
         */
        override suspend fun getRemainingChapters(
            storyUrl: String,
            totalPages: Int,
            onBatchReady: suspend (List<Chapter>) -> Unit,
        ) = withContext(Dispatchers.IO) {
            if (totalPages <= 1) return@withContext

            val (storyId, _) = extractIdAndSlug(storyUrl)
            val semaphore = Semaphore(3)

            coroutineScope {
                for (page in 2..totalPages) {
                    launch {
                        semaphore.withPermit {
                            try {
                                delay(crawlDelayMs)
                                val pageUrl = "${storyUrl.trimEnd('/')}/trang-$page/"
                                val pageDoc = fetchDocumentWithRetry(pageUrl)
                                val chapters =
                                    parseChapterLinks(pageDoc)
                                        .sortedBy { it.number }
                                        .map { it.toChapter(storyId) }
                                if (chapters.isNotEmpty()) {
                                    onBatchReady(chapters)
                                }
                            } catch (e: Exception) {
                                Log.d("TruyenComScraper", "Chapter page $page crawl failed", e)
                            }
                        }
                    }
                }
            }
        }

        // getChapterList inherited from BaseScraper (identical dedup logic)

        /**
         * Get the text content of a specific chapter.
         * URL: /{slug}/chuong-{N}.html
         * Override: TC has WebView fallback for JS-rendered content.
         */
        override suspend fun getChapterContent(chapterUrl: String): String =
            withContext(Dispatchers.IO) {
                // Step 1: Try OkHttp + Jsoup first (fast path)
                val content =
                    try {
                        val doc = fetchDocumentWithRetry(chapterUrl)
                        extractChapterText(doc)
                    } catch (e: Exception) {
                        Log.d("TruyenComScraper", "Parse chapter content failed for $chapterUrl", e)
                        ""
                    }

                // Step 2: If OkHttp content too short, fall back to WebView (JS rendering)
                if (content.length > MIN_CONTENT_LENGTH) {
                    content
                } else {
                    val webViewContent =
                        try {
                            webViewLoader.loadContent(chapterUrl.ensureAbsolute())
                        } catch (_: Exception) {
                            null
                        }

                    webViewContent?.takeIf { it.length > MIN_CONTENT_LENGTH }
                        ?: content.ifBlank { "Không thể tải nội dung chương. Vui lòng thử lại." }
                }
            }

        /**
         * Get completed stories (truyện full).
         * URL: /truyen-full/ or /truyen-full/trang-{page}/
         */
        override suspend fun getCompletedStories(page: Int): List<Story> =
            withContext(Dispatchers.IO) {
                val url =
                    if (page <= 1) {
                        "$BASE_URL/truyen-full/"
                    } else {
                        "$BASE_URL/truyen-full/trang-$page/"
                    }
                parseStoryListPage(url).map { it.copy(status = "Hoàn thành") }
            }

        /**
         * Get stories by genre — always newest completed stories.
         * Uses the JSON API (/api/list/{categoryID}/new/{page}/25) when categoryId is available.
         * Falls back to HTML parsing when categoryId is not set.
         */
        override suspend fun getStoriesByGenre(
            genreSlug: String,
            page: Int,
            minChapters: Int,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                // Check cached categoryId first
                val cachedCatId = categoryIdCache[genreSlug]
                if (cachedCatId != null && cachedCatId > 0) {
                    // Have catId cached → use fast JSON API directly
                    val results = getStoriesByGenreApi(cachedCatId, "new", page, minChapters)
                    return@withContext if (completedOnly) results.map { it.copy(status = "Hoàn thành") } else results
                }

                // Not cached — fetch HTML page
                val statusPath = if (completedOnly) "full/" else ""
                val url =
                    if (page <= 1) {
                        "$BASE_URL/truyen-$genreSlug/$statusPath"
                    } else {
                        "$BASE_URL/truyen-$genreSlug/${statusPath}trang-$page/"
                    }
                val doc =
                    try {
                        fetchDocumentWithRetry(url)
                    } catch (e: Exception) {
                        Log.d("TruyenComScraper", "Genre page fetch failed for $genreSlug", e)
                        return@withContext emptyList()
                    }

                // Try to extract categoryId from this page → cache for future calls
                val scripts = doc.select("script").map { it.html() }.joinToString("\n")
                val catIdMatch = Regex("categoryID\\s*=\\s*(\\d+)").find(scripts)
                val catId = catIdMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (catId > 0) categoryIdCache[genreSlug] = catId

                if (catId > 0 && page <= 1) {
                    val results = parseStoryListPage(doc)
                    if (completedOnly) results.map { it.copy(status = "Hoàn thành") } else results
                } else if (catId > 0) {
                    val results = getStoriesByGenreApi(catId, "new", page, minChapters)
                    if (completedOnly) results.map { it.copy(status = "Hoàn thành") } else results
                } else {
                    val results = parseStoryListPage(doc)
                    if (completedOnly) results.map { it.copy(status = "Hoàn thành") } else results
                }
            }

        /**
         * Call the JSON API to get stories sorted by the specified type.
         * API: /api/list/{categoryID}/{sortType}/{page}/25
         */
        private suspend fun getStoriesByGenreApi(
            categoryId: Int,
            sortType: String,
            page: Int,
            minChapters: Int = 0,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                if (minChapters > 0) {
                    val maxPages = 10
                    val filtered = mutableListOf<Story>()
                    for (p in page until page + maxPages) {
                        val pageResults = fetchSingleApiPage(categoryId, sortType, p)
                        if (pageResults.isEmpty()) break
                        filtered.addAll(pageResults.filter { it.totalChapters >= minChapters })
                    }
                    filtered
                } else {
                    fetchSingleApiPage(categoryId, sortType, page)
                }
            }

        /** Fetch a single page from the JSON API. */
        private fun fetchSingleApiPage(
            categoryId: Int,
            sortType: String,
            page: Int,
        ): List<Story> {
            val url = "$BASE_URL/api/list/$categoryId/$sortType/$page/25"
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .build()

            return client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use emptyList()

                try {
                    val json = JSONObject(body)
                    val items = json.getJSONArray("items")
                    val results = mutableListOf<Story>()

                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val alias = item.optString("alias", "")
                        val storyId = item.optString("storyID", "")
                        val title = item.optString("title", "")
                        val author = item.optString("author", "")
                        val chapters = item.optString("chapters", "0").toIntOrNull() ?: 0

                        if (alias.isNotBlank() && title.isNotBlank()) {
                            results.add(
                                Story(
                                    id = "$alias.$storyId",
                                    slug = alias,
                                    title = title,
                                    author = author,
                                    totalChapters = chapters,
                                    url = "$BASE_URL/$alias.$storyId/",
                                    coverImageUrl = buildCoverUrl(storyId, alias),
                                ),
                            )
                        }
                    }
                    results
                } catch (e: Exception) {
                    Log.w("TruyenComScraper", "API parse error for genre $categoryId page $page", e)
                    emptyList()
                }
            }
        }

        /**
         * Get the list of available genres from the homepage navigation.
         */
        override suspend fun getGenreList(): List<Genre> =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(BASE_URL)
                val genres = mutableListOf<Genre>()

                doc.select("a[href*=truyen-]").forEach { el ->
                    val href = el.attr("href")
                    val name =
                        el
                            .text()
                            .trim()
                            .replace(" Full", "")
                            .trim()

                    val slugMatch = GENRE_SLUG_REGEX.find(href)
                    val slug = slugMatch?.groupValues?.get(1) ?: return@forEach

                    // Skip non-genre links (hot, full, moi-cap-nhat, moi-dang)
                    if (slug in listOf("hot", "full", "moi-cap-nhat", "moi-dang")) return@forEach
                    if (name.isBlank()) return@forEach

                    genres.add(Genre(name = name, slug = slug))
                }

                genres.distinctBy { it.slug }
            }

        // --- Private helpers ---

        private val categoryIdCache = ConcurrentHashMap<String, Int>()

        internal suspend fun getCategoryId(genreSlug: String): Int =
            withContext(Dispatchers.IO) {
                categoryIdCache[genreSlug]?.let { return@withContext it }

                try {
                    val doc = fetchDocumentWithRetry("$BASE_URL/truyen-$genreSlug/full/")
                    val scripts = doc.select("script").map { it.html() }.joinToString("\n")
                    val catIdMatch = Regex("categoryID\\s*=\\s*(\\d+)").find(scripts)
                    val categoryId = catIdMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    if (categoryId > 0) categoryIdCache[genreSlug] = categoryId
                    categoryId
                } catch (_: Exception) {
                    0
                }
            }

        /**
         * Parse a story listing page (search results, genre pages, full pages).
         */
        private suspend fun parseStoryListPage(
            url: String,
            sectionId: String? = null,
        ): List<Story> {
            val doc = fetchDocumentWithRetry(url)
            return parseStoryListPage(doc, sectionId)
        }

        /**
         * Parse stories from an already-fetched Document.
         */
        private fun parseStoryListPage(
            doc: Document,
            sectionId: String? = null,
        ): List<Story> {
            val results = mutableListOf<Story>()

            val container =
                if (sectionId != null) {
                    doc.select("#$sectionId").first() ?: doc
                } else {
                    doc
                }

            val storyRows =
                container
                    .select(".list-truyen .row, .row")
                    .toList()
                    .filter { it.select(".truyen-title a, h3.truyen-title a").isNotEmpty() }

            if (storyRows.isNotEmpty()) {
                storyRows.forEach { row ->
                    val titleEl = row.select(".truyen-title a, h3.truyen-title a").first() ?: return@forEach
                    val href = titleEl.attr("abs:href").ifEmpty { titleEl.attr("href") }
                    val title = titleEl.text().trim()
                    if (title.isBlank() || href.isBlank()) return@forEach

                    val story = parseStoryLink(href, title) ?: return@forEach
                    val author =
                        row
                            .select("a[href*=tac-gia]")
                            .first()
                            ?.text()
                            ?.trim() ?: ""

                    val chapterText =
                        row
                            .select(".text-info")
                            .text()
                            .ifBlank { row.text() }
                    val chapterMatch = Regex("(\\d+)\\s*[Cc]hương").find(chapterText)
                    val totalChapters = chapterMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

                    results.add(story.copy(author = author, totalChapters = totalChapters, sourceId = sourceId))
                }
            } else {
                val storyElements =
                    container.select(
                        ".list-truyen .truyen-title a, .col-truyen-main .truyen-title a, h3.truyen-title a",
                    )
                if (storyElements.isEmpty()) {
                    val links =
                        container.select("a[href]").filter { el ->
                            val elHref = el.attr("href")
                            elHref.matches(STORY_URL_REGEX) &&
                                el.text().isNotBlank() &&
                                !el.text().contains("Chương")
                        }
                    links.forEach { el ->
                        val story = parseStoryLink(el.attr("href"), el.text())
                        if (story != null) results.add(story)
                    }
                } else {
                    storyElements.forEach { el ->
                        val elHref = el.attr("abs:href").ifEmpty { el.attr("href") }
                        val elTitle = el.text().trim()
                        if (elTitle.isNotBlank() && elHref.isNotBlank()) {
                            val story = parseStoryLink(elHref, elTitle)
                            if (story != null) results.add(story)
                        }
                    }
                }
            }

            return results.distinctBy { it.id }
        }

        /**
         * Extract total number of chapter pagination pages from "Last »" link.
         */
        private fun getTotalChapterPages(doc: Document): Int {
            val lastLink =
                doc.select("a[href*=trang-]").lastOrNull { el ->
                    val text = el.text().trim()
                    text.contains("Last") || text.contains("»") || text.contains("Cuối")
                }
            if (lastLink != null) {
                val href = lastLink.attr("href")
                val match = PAGINATION_LAST_REGEX.find(href)
                val pages = match?.groupValues?.get(1)?.toIntOrNull()
                if (pages != null && pages > 0) return pages
            }

            // Fallback: find highest page number from all pagination links
            val maxPage =
                doc
                    .select("a[href*=trang-]")
                    .mapNotNull { el ->
                        val href = el.attr("href")
                        PAGINATION_LAST_REGEX
                            .find(href)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                    }.maxOrNull()

            return maxPage ?: 1
        }

        private fun parseStoryLink(
            href: String,
            title: String,
        ): Story? {
            val (id, slug) = extractIdAndSlug(href)
            if (id.isBlank() || slug.isBlank()) return null
            val numId = id.substringAfterLast('.', "")
            return Story(
                id = id,
                slug = slug,
                title = title,
                url = href.ensureAbsolute(),
                coverImageUrl = buildCoverUrl(numId, slug),
            )
        }

        /** Build cover image URL from CDN pattern. */
        private fun buildCoverUrl(
            numId: String,
            slug: String,
        ): String {
            val n = numId.toIntOrNull() ?: return ""
            val bucket = n / 1000
            return "https://cdn.truyencom.com/medias/covers/$bucket/$n-${slug}_cover_large.jpg"
        }

        private fun parseChapterLinks(doc: Document): List<ChapterInfo> {
            val chapters = mutableListOf<ChapterInfo>()
            val seen = mutableSetOf<Int>()

            val chapterElements = doc.select("a[href*=chuong-]")

            for (el in chapterElements) {
                val href = el.attr("abs:href").ifEmpty { el.attr("href") }
                val text = el.text().trim()

                val numberMatch = CHAPTER_NUMBER_REGEX.find(href)
                val number = numberMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue

                if (number in seen) continue
                seen.add(number)

                val chapterTitle = text.ifBlank { "Chương $number" }
                chapters.add(ChapterInfo(number, chapterTitle, href.ensureAbsolute()))
            }

            return chapters.sortedBy { it.number }
        }

        private fun extractIdAndSlug(url: String): Pair<String, String> {
            val path = url.trimEnd('/').substringAfterLast("/")
            val match = SLUG_ID_REGEX.find(path)
            return if (match != null) {
                val slug = match.groupValues[1]
                val numId = match.groupValues[2]
                Pair("$slug.$numId", slug)
            } else {
                Pair(path, path)
            }
        }
    }
