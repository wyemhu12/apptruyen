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
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Web scraper for truyen.tangthuvien.vn.
 * Chỉ trả về truyện đã hoàn thành, sắp xếp theo mới nhất.
 */
@Singleton
class TangThuVienScraper
    @Inject
    constructor(
        client: OkHttpClient,
    ) : BaseScraper(client) {

        override val sourceId = "tangthuvien"
        override val sourceName = "Tàng Thư Viện"
        override val baseUrl = BASE_URL

        // TTV uses Desktop UA and ETag caching
        override val userAgent = DESKTOP_UA
        override val useEtagCaching = true

        // TTV has custom headers
        override fun buildExtraHeaders(builder: Request.Builder) {
            builder
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "$BASE_URL/")
                .header("Connection", "keep-alive")
        }

        companion object {
            const val BASE_URL = "https://truyen.tangthuvien.vn"
            private const val DESKTOP_UA =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            private const val CHAPTERS_PER_PAGE = 75

            // Cached regex patterns (TTV-specific)
            private val CHAPTER_NUMBER_REGEX = Regex("chuong-(\\d+)")
            private val CHAPTER_COUNT_REGEX = Regex("(\\d+)\\s*chương", RegexOption.IGNORE_CASE)
            private val STORY_ID_REGEX = Regex("""id="story_id_hidden"\s+value="(\d+)"""")
        }

        // Cache numeric story ID per story slug — thread-safe cho concurrent loads
        private val numericStoryIdCache = ConcurrentHashMap<String, String>()

        // ---- Search ----

        override suspend fun search(
            keyword: String,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                // Always use search endpoint — /tong-hop ignores term param
                val url = "$BASE_URL/ket-qua-tim-kiem?term=$encoded"
                val doc = fetchDocumentWithRetry(url)
                val stories = parseStoryList(doc)
                if (completedOnly) {
                    stories.filter { it.status.contains("Hoàn thành", ignoreCase = true) }
                } else {
                    stories
                }
            }

        // ---- Story Detail ----

        override suspend fun getStoryDetail(storyUrl: String): Story =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(storyUrl)

                val title =
                    doc
                        .select(".book-info h1")
                        .first()
                        ?.text()
                        ?.trim() ?: "Đang cập nhật"
                val author =
                    doc
                        .select(".book-info .tag a.blue")
                        .first()
                        ?.text()
                        ?.trim() ?: ""
                val genres =
                    doc
                        .select(".book-info .tag a.red")
                        .map { it.text().trim() }
                        .filter { it.isNotBlank() }
                val status = doc.select(".book-info .tag span.blue").text().trim()

                val descriptionEl = doc.select(".book-intro").first()
                val description = descriptionEl?.let { cleanHtml(it.html()) } ?: ""

                val coverImageUrl =
                    validateCoverUrl(
                        doc.select("#bookImg img").first()?.let { img ->
                            img.attr("abs:src").ifEmpty { img.attr("src") }
                        },
                    )

                // Extract total chapters from tab text like "Danh sách chương (2039 chương)"
                val chapterTabText = doc.select("#j-bookCatalogPage").text()
                val totalChapters =
                    CHAPTER_COUNT_REGEX
                        .find(chapterTabText)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0

                val slug = extractSlug(storyUrl)

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
                    status = normalizeStatus(status),
                )
            }

        // ---- Chapter List ----

        override suspend fun getFirstPageChapters(storyUrl: String): StorySource.FirstPageResult =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(storyUrl)
                val storyId = extractSlug(storyUrl)
                val chapters = parseChapterLinks(doc, storyId)

                // Extract total chapters from tab text like "Danh sách chương (2039 chương)"
                val chapterTabText = doc.select("#j-bookCatalogPage").text()
                val totalChapters =
                    CHAPTER_COUNT_REGEX
                        .find(chapterTabText)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull()
                        ?: chapters.size

                // TTV paginates chapters via AJAX — 75 chapters per page
                val totalPages =
                    if (totalChapters > CHAPTERS_PER_PAGE) {
                        kotlin.math.ceil(totalChapters.toDouble() / CHAPTERS_PER_PAGE).toInt()
                    } else {
                        1
                    }

                // Store numeric story ID for AJAX calls in getRemainingChapters
                val numericId = extractNumericStoryId(doc)
                val slug = extractSlug(storyUrl)
                if (numericId != null) {
                    numericStoryIdCache[slug] = numericId
                }

                StorySource.FirstPageResult(chapters = chapters, totalPages = totalPages)
            }

        override suspend fun getRemainingChapters(
            storyUrl: String,
            totalPages: Int,
            onBatchReady: suspend (List<Chapter>) -> Unit,
        ) = withContext(Dispatchers.IO) {
            if (totalPages <= 1) return@withContext

            // Get numeric story ID — try cached value first, then re-fetch page
            val slug = extractSlug(storyUrl)
            val numericId =
                numericStoryIdCache[slug] ?: run {
                    val doc = fetchDocumentWithRetry(storyUrl)
                    extractNumericStoryId(doc)?.also { numericStoryIdCache[slug] = it }
                } ?: return@withContext

            val storyId = extractSlug(storyUrl)
            val semaphore = Semaphore(3)

            // Page 0 was already fetched in getFirstPageChapters → start from page 1
            coroutineScope {
                for (page in 1 until totalPages) {
                    launch {
                        semaphore.withPermit {
                            try {
                                delay(crawlDelayMs)
                                val chapters = fetchChapterPageAjax(numericId, page, storyId)
                                if (chapters.isNotEmpty()) {
                                    onBatchReady(chapters)
                                }
                            } catch (e: Exception) {
                                Log.d("TTVScraper", "Chapter page $page crawl failed", e)
                            }
                        }
                    }
                }
            }
        }

        // TTV uses distinctBy instead of seen.add() dedup pattern
        override suspend fun getChapterList(storyUrl: String): List<Chapter> =
            withContext(Dispatchers.IO) {
                val result = getFirstPageChapters(storyUrl)
                val allChapters = result.chapters.toMutableList()

                if (result.totalPages > 1) {
                    getRemainingChapters(storyUrl, result.totalPages) { batch ->
                        synchronized(allChapters) {
                            allChapters.addAll(batch)
                        }
                    }
                }

                allChapters.distinctBy { it.chapterNumber }.sortedBy { it.chapterNumber }
            }

        // ---- Chapter Content ----

        // TTV has unique selectors — override getChapterContent instead of using extractChapterText
        override suspend fun getChapterContent(chapterUrl: String): String =
            withContext(Dispatchers.IO) {
                try {
                    val doc = fetchDocumentWithRetry(chapterUrl)

                    // Primary selector
                    val contentEl =
                        doc.select(".chapter-c-content .box-chap").first()
                            ?: doc.select(".chapter-c-content").first()

                    if (contentEl != null) {
                        contentEl.select("script, .ads, .ad, iframe, noscript").remove()
                        val text = cleanHtml(contentEl.html())
                        if (text.length > MIN_CONTENT_LENGTH) return@withContext text
                    }

                    throw ScraperException.ParseException("Nội dung chương quá ngắn hoặc rỗng", chapterUrl)
                } catch (e: ScraperException) {
                    throw e // Re-throw ScraperException as-is
                } catch (e: Exception) {
                    Log.d("TTVScraper", "Failed to load chapter: $chapterUrl", e)
                    throw ScraperException.ParseException("Không thể tải nội dung chương: ${e.message}", chapterUrl)
                }
            }

        // ---- Browse: Completed Stories ----

        override suspend fun getCompletedStories(page: Int): List<Story> =
            withContext(Dispatchers.IO) {
                val url = "$BASE_URL/tong-hop?fns=ht&page=$page"
                val doc = fetchDocumentWithRetry(url)
                parseStoryList(doc)
            }

        // ---- Browse: By Genre ----

        override suspend fun getStoriesByGenre(
            genreSlug: String,
            page: Int,
            minChapters: Int,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val ctgId = genreSlugToId(genreSlug)
                if (ctgId == 0) {
                    // TTV không có thể loại này — trả empty thay vì gửi request sai
                    Log.d("TTVScraper", "Genre slug '$genreSlug' not mapped, skipping")
                    return@withContext emptyList()
                }
                // TTV genre URL: /tong-hop?ctg={id}&fns=ht&page=N — fns=ht = chỉ hoàn thành
                // Khi completedOnly=false, bỏ fns=ht để load tất cả truyện
                val statusParam = if (completedOnly) "&fns=ht" else ""
                val url = "$BASE_URL/tong-hop?ctg=$ctgId$statusParam&page=$page"
                val doc = fetchDocumentWithRetry(url)
                val stories = parseStoryList(doc)

                if (minChapters > 0) {
                    stories.filter { it.totalChapters >= minChapters }
                } else {
                    stories
                }
            }

        // ---- Genre List ----

        override suspend fun getGenreList(): List<Genre> =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(BASE_URL)
                val genres = mutableListOf<Genre>()

                // Parse genre links from dropdown: /the-loai/{slug}
                doc.select("a[href*=/the-loai/]").forEach { el ->
                    val href = el.attr("href")
                    val name =
                        el
                            .text()
                            .trim()
                            .replace(Regex("\\d+$"), "")
                            .trim()

                    val slugMatch = Regex("/the-loai/([a-z0-9-]+)").find(href)
                    val slug = slugMatch?.groupValues?.get(1) ?: return@forEach
                    if (name.isBlank()) return@forEach

                    genres.add(Genre(name = name, slug = slug))
                }

                genres.distinctBy { it.slug }
            }

        // ---- Private helpers ----

        /**
         * Parse story items from list pages (search, completed, genre).
         * Items use selector: .book-img-text ul li
         */
        private fun parseStoryList(doc: org.jsoup.nodes.Document): List<Story> {
            val results = mutableListOf<Story>()

            doc.select(".book-img-text ul li, .rank-table-list li").forEach { item ->
                val titleEl = item.select("h4 a, h3 a, .book-mid-info h4 a").first() ?: return@forEach
                val title = titleEl.text().trim()
                val href = titleEl.attr("abs:href").ifEmpty { titleEl.attr("href") }
                if (title.isBlank() || href.isBlank()) return@forEach

                val slug = extractSlug(href)
                val author =
                    item
                        .select(".author a.name, .author a:first-of-type")
                        .first()
                        ?.text()
                        ?.trim() ?: ""

                // Parse chapter count from text like "2039 chương" or "Đã hoàn thành|2039 chương"
                val infoText = item.select(".author").text()
                val totalChapters =
                    CHAPTER_COUNT_REGEX
                        .find(infoText)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0

                // Parse status
                val status = normalizeStatus(infoText)

                // Cover image
                val coverUrl =
                    validateCoverUrl(
                        item.select(".book-img-box img, img").first()?.let { img ->
                            img.attr("abs:src").ifEmpty { img.attr("src") }
                        },
                    )

                // Description
                val description = item.select(".intro").text().trim()

                // Genre
                val genres =
                    item.select(".author a").mapNotNull { a ->
                        val aHref = a.attr("href")
                        if (aHref.contains("/the-loai/")) a.text().trim() else null
                    }

                results.add(
                    Story(
                        id = slug,
                        slug = slug,
                        title = title,
                        author = author,
                        genres = genres,
                        description = description,
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

        /**
         * Parse chapter links from a story detail page.
         * Chapters are in .volume containers, each li a.
         */
        private fun parseChapterLinks(
            doc: org.jsoup.nodes.Document,
            storyId: String,
        ): List<Chapter> {
            val chapters = mutableListOf<Chapter>()
            val seen = mutableSetOf<Int>()

            // TTV uses volume-based chapter layout on main page, ul.cf in AJAX responses
            doc.select(".volume li a, #max-volume li a, .list-chapter li a, ul.cf li a").forEach { el ->
                val href = el.attr("abs:href").ifEmpty { el.attr("href") }
                val text = el.attr("title").ifBlank { el.text().trim() }

                val numberMatch = CHAPTER_NUMBER_REGEX.find(href)
                val number = numberMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach

                if (number in seen) return@forEach
                seen.add(number)

                chapters.add(
                    Chapter(
                        storyId = storyId,
                        chapterNumber = number,
                        title = text.ifBlank { "Chương $number" },
                        url = href.ensureAbsolute(),
                    ),
                )
            }

            return chapters.sortedBy { it.chapterNumber }
        }

        private fun extractSlug(url: String): String {
            // URL: https://truyen.tangthuvien.vn/doc-truyen/{slug}
            // or: https://truyen.tangthuvien.vn/doc-truyen/{slug}/chuong-N
            val path = url.trimEnd('/').substringAfter("/doc-truyen/", "")
            return path.substringBefore("/").ifBlank { path }
        }

        // Map genre slug to TTV category ID for /tong-hop?ctg= queries
        private val genreSlugToIdMap =
            mapOf(
                "tien-hiep" to 1,
                "huyen-huyen" to 2,
                "do-thi" to 3,
                "khoa-huyen" to 4,
                "ky-huyen" to 5,
                "vo-hiep" to 6,
                "lich-su" to 7,
                "dong-nhan" to 8,
                "quan-su" to 9,
                "du-hi" to 10,
                "canh-ky" to 11,
                "linh-di" to 12,
            )

        private fun genreSlugToId(slug: String): Int = genreSlugToIdMap[slug] ?: 0

        // ---- TTV Chapter AJAX helpers ----

        /**
         * Extract numeric story ID from hidden input field.
         * TTV uses this ID for AJAX chapter pagination.
         * HTML: <input type="hidden" name="story_id" id="story_id_hidden" value="38952">
         */
        private fun extractNumericStoryId(doc: org.jsoup.nodes.Document): String? {
            // Try input element first
            val inputVal = doc.select("#story_id_hidden").attr("value")
            if (inputVal.isNotBlank()) return inputVal

            // Fallback: regex on page source
            val html = doc.html()
            return STORY_ID_REGEX.find(html)?.groupValues?.get(1)
        }

        /**
         * Fetch a single page of chapters from TTV AJAX endpoint.
         * URL: /doc-truyen/page/{storyNumericId}?page={pageIndex}&limit=75&web=1
         * pageIndex is 0-based.
         */
        private suspend fun fetchChapterPageAjax(
            storyNumericId: String,
            pageIndex: Int,
            storySlug: String,
        ): List<Chapter> {
            val url = "$BASE_URL/doc-truyen/page/$storyNumericId?page=$pageIndex&limit=$CHAPTERS_PER_PAGE&web=1"
            val doc = fetchDocumentWithRetry(url)
            return parseChapterLinks(doc, storySlug)
        }
    }
