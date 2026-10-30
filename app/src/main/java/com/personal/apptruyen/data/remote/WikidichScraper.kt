package com.personal.apptruyen.data.remote

import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import timber.log.Timber
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WikidichScraper
    @Inject
    constructor(
        client: OkHttpClient,
    ) : BaseScraper(client) {

        override val sourceId: String = "wikidich"
        override val sourceName: String = "Wikidich"
        var customBaseUrl: String? = null
        internal var enrichInTests: Boolean = false
        override val baseUrl: String
            get() = customBaseUrl ?: "https://wikicv.org"

        override val userAgent: String = DESKTOP_UA

        override fun buildExtraHeaders(builder: Request.Builder) {
            builder.header("Referer", "$baseUrl/")
        }

        private val ajaxFragmentCache: MutableMap<String, CacheEntry> =
            java.util.Collections.synchronizedMap(
                object : LinkedHashMap<String, CacheEntry>(MAX_AJAX_CACHE_SIZE + 1, 0.75f, false) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > MAX_AJAX_CACHE_SIZE
                },
            )

        companion object {
            private const val DEFAULT_PAGE_SIZE = 99
            private const val MAX_PAGINATION_PAGES = 200
            private const val SEARCH_PAGE_SIZE = 20
            private const val MALE_GENDER_PARAM = "gender=5794f03dd7ced228f4419196"
            private const val COMPLETED_STATUS_PARAM = "status=5794f03dd7ced228f4419191"
            private const val NEWEST_SORT_PARAM = "so=4&vo=1"
            private const val ENRICH_MAX_ITEMS = 5
            private const val ENRICH_BATCH_SIZE = 2
            private const val ENRICH_TIMEOUT_MS = 3_500L
            private const val MAX_PAGINATION_JUMPS = 2
            private const val MAX_AJAX_CACHE_SIZE = 30
            private const val AJAX_CACHE_TTL_MS = 10 * 60 * 1000L
            internal const val DESKTOP_UA =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

            private val WATERMARK_REGEX = Regex("【❁.*\\(Wikidich\\).*❁】", RegexOption.IGNORE_CASE)
            private val LATEST_CHAPTER_TEXT_REGEX =
                Regex(
                    """(?:(?:Quyển|Phần)\s*\d+\s*[-–—:.,]?\s*)?(?:Chương|Hồi|Tiết|Phần|Đệ)\s*(\d+)|第\s*(\d+)\s*[章回節节]|^\s*(\d{1,5})(?:\s*[.:,\-–—]\s*|\s+)""",
                    RegexOption.IGNORE_CASE,
                )
            private val LATEST_CHAPTER_HREF_REGEX =
                Regex(
                    """/(?:quyen-\d+-)?(?:chuong|hoi|tiet|phan)-(\d+)(?:[-~]|$)""",
                    RegexOption.IGNORE_CASE,
                )

            internal val NON_MALE_WIKIDICH_GENDERS =
                setOf(
                    "ngôn tình",
                    "đam mỹ",
                    "bách hợp",
                    "nữ tần",
                    "nữ tôn",
                    "không cp",
                    "đa nguyên",
                )

            internal val GENRE_QUERY_MAP =
                mapOf(
                    "tinh-cam" to "genre=5794f03dd7ced228f44191a2",
                    "lam-su-nghiep" to "genre=6785c24e23deea36d63efe8e",
                    "tien-hiep" to "genre=5794f03dd7ced228f44191a4",
                    "huyen-huyen" to "genre=57d17c94d7ced218fcd2734b",
                    "khoa-hoc-vien-tuong" to "genre=5794f03dd7ced228f44191a9",
                    "mat-the" to "genre=5794f03dd7ced228f44191ac",
                    "sinh-ton" to "genre=5fd54e6523deea0a1a87e9b9",
                    "tranh-ba" to "genre=5a1ce8f512fb556782b06f7e",
                    "vo-hiep" to "genre=5794f03dd7ced228f44191a3",
                    "trinh-tham" to "genre=5794f03dd7ced228f44191a7",
                    "kinh-di" to "genre=5794f03dd7ced228f44191a6",
                    "quan-truong" to "genre=5b10edd012fb550d90428316",
                    "kinh-thuong" to "genre=5794f03dd7ced228f44191d4",
                    "quan-su" to "genre=6785b91d23deea36d63efe7f",
                    "xay-dung" to "genre=5fd3abb623deea0a1a87e9b5",
                    "lam-ruong" to "genre=5794f03dd7ced228f44191c0",
                    "huyen-hoc" to "genre=58458dc2d7ced23af03679c5",
                    "phim-anh" to "genre=596bd83012fb557cb030fc5a",
                    "manga-anime" to "genre=5794f03dd7ced228f44191a8",
                )
        }

        override suspend fun search(
            keyword: String,
            completedOnly: Boolean,
        ): List<Story> =
            search(
                keyword = keyword,
                completedOnly = completedOnly,
                page = 1,
                maleOnly = false,
                genreSlug = null,
            )

        override suspend fun search(
            keyword: String,
            completedOnly: Boolean,
            page: Int,
            maleOnly: Boolean,
            genreSlug: String?,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val trimmedKeyword = keyword.trim()
                if (trimmedKeyword.isBlank()) return@withContext emptyList()
                val filterParam =
                    if (!genreSlug.isNullOrBlank()) {
                        GENRE_QUERY_MAP[genreSlug] ?: return@withContext emptyList()
                    } else {
                        null
                    }
                val encoded = URLEncoder.encode(trimmedKeyword, "UTF-8")
                val genreParam = if (filterParam != null) "&$filterParam" else ""
                val genderParam = if (maleOnly) "&$MALE_GENDER_PARAM" else ""
                val statusParam = if (completedOnly) "&$COMPLETED_STATUS_PARAM" else ""
                val start = (page.coerceAtLeast(1) - 1) * SEARCH_PAGE_SIZE
                val startParam = if (start > 0) "&start=$start" else ""
                val url = "$baseUrl/tim-kiem?q=$encoded&qs=1$genreParam$genderParam$statusParam$startParam"
                val doc = fetchDocumentWithRetry(url)
                checkLoginRequired(doc)
                val stories = parseStoryList(doc)
                val filtered = filterStories(stories, completedOnly = completedOnly, maleOnly = maleOnly)
                enrichWithChapterCounts(filtered, maxItems = ENRICH_MAX_ITEMS)
            }

        internal fun filterStories(
            stories: List<Story>,
            completedOnly: Boolean,
            maleOnly: Boolean,
        ): List<Story> {
            var result = stories
            if (completedOnly) {
                result =
                    result.filter { story ->
                        story.status.contains("Hoàn thành", ignoreCase = true) ||
                            story.status.contains("Full", ignoreCase = true)
                    }
            }
            if (maleOnly) {
                result =
                    result.filter { story ->
                        story.genres.none { rawGender ->
                            val normalized =
                                Normalizer
                                    .normalize(rawGender.trim().lowercase(Locale.ROOT), Normalizer.Form.NFC)
                            normalized in NON_MALE_WIKIDICH_GENDERS
                        }
                    }
            }
            return result
        }

        override suspend fun getStoryDetail(storyUrl: String): Story =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(storyUrl)
                checkLoginRequired(doc)

                val title =
                    doc.selectFirst(".cover-info h2, .cover-info h1, .book-info h2")?.text()?.trim()
                        ?: throw ScraperException.ParseException("Không tìm thấy tiêu đề truyện", storyUrl)

                val author = doc.selectFirst(".cover-info a[href*='/tac-gia/']")?.text()?.trim() ?: "Chưa rõ"

                val coverImg = doc.selectFirst(".book-info .cover-wrapper img, .cover-info img, .book-info img")
                val coverUrl = extractCoverUrl(coverImg)

                val descElement = doc.selectFirst(".book-desc-detail, .book-desc")
                val description =
                    descElement?.let { el ->
                        el.select("br").append("\\n")
                        el.text().replace("\\n", "\n").trim()
                    } ?: ""

                val genres =
                    doc
                        .select(".cover-info a[href*='/tim-kiem?'], .book-desc a[href*='/tim-kiem?']")
                        .map { it.text().trim() }
                        .filter { it.isNotBlank() && !it.contains("Còn tiếp") && !it.contains("Hoàn thành") }
                        .distinct()

                val statusText =
                    doc
                        .selectFirst(".cover-info a[href*='status=']")
                        ?.text()
                        ?.trim()
                        .orEmpty()
                val status =
                    if (statusText.contains("Hoàn thành", ignoreCase = true)) {
                        "Hoàn thành"
                    } else {
                        "Đang ra"
                    }

                val extractedTotal = extractTotalChaptersFromDetail(doc)
                val totalChapters =
                    if (extractedTotal > 0) {
                        extractedTotal
                    } else {
                        fetchTotalChaptersFromBookIndex(doc, storyUrl)
                    }
                val slug = extractSlug(storyUrl)

                Story(
                    id = "${sourceId}_$slug",
                    slug = slug,
                    title = title,
                    author = author,
                    coverImageUrl = coverUrl,
                    description = description,
                    genres = genres,
                    status = status,
                    totalChapters = totalChapters,
                    sourceId = sourceId,
                    url = storyUrl,
                )
            }

        override suspend fun getFirstPageChapters(storyUrl: String): StorySource.FirstPageResult =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(storyUrl)
                checkLoginRequired(doc)

                val html = doc.html()
                val (bookId, cut, signKey) = extractSecurityParams(html, storyUrl)
                val pageSize = extractPageSize(html)
                val sign = makeBookIndexSign(signKey, 0, pageSize, cut)
                val indexUrl = "$baseUrl/book/index?bookId=$bookId&start=0&size=$pageSize&signKey=$signKey&sign=$sign"

                val fragmentHtml = fetchAjaxFragment(indexUrl, storyUrl)
                val fragmentDoc = Jsoup.parseBodyFragment(fragmentHtml)
                val storyId = "${sourceId}_${extractSlug(storyUrl)}"
                val chapters = parseChapterItems(fragmentDoc, storyId, 0)

                if (chapters.isEmpty()) {
                    throw ScraperException.ParseException("Không trích xuất được danh sách chương từ máy chủ Wikidich", indexUrl)
                }

                val paginationLinks = fragmentDoc.select("ul.pagination li a[data-action='loadBookIndex']")
                val maxStart =
                    paginationLinks
                        .mapNotNull { it.attr("data-start").toIntOrNull() }
                        .maxOrNull() ?: 0
                val maxPageFromText =
                    paginationLinks
                        .mapNotNull { it.text().trim().toIntOrNull() }
                        .maxOrNull() ?: 1
                val totalPages = maxOf((maxStart / pageSize) + 1, maxPageFromText)

                StorySource.FirstPageResult(
                    chapters = chapters,
                    totalPages = totalPages,
                )
            }

        override suspend fun getRemainingChapters(
            storyUrl: String,
            totalPages: Int,
            onBatchReady: suspend (List<Chapter>) -> Unit,
        ) = withContext(Dispatchers.IO) {
            val doc = fetchDocumentWithRetry(storyUrl)
            checkLoginRequired(doc)

            val html = doc.html()
            val (bookId, cut, signKey) = extractSecurityParams(html, storyUrl)
            val pageSize = extractPageSize(html)
            val storyId = "${sourceId}_${extractSlug(storyUrl)}"
            var currentStart = pageSize
            var pageCount = 0

            while (pageCount < MAX_PAGINATION_PAGES) {
                val sign = makeBookIndexSign(signKey, currentStart, pageSize, cut)
                val indexUrl = "$baseUrl/book/index?bookId=$bookId&start=$currentStart&size=$pageSize&signKey=$signKey&sign=$sign"

                val fragmentHtml = fetchAjaxFragment(indexUrl, storyUrl)
                val fragmentDoc = Jsoup.parseBodyFragment(fragmentHtml)
                val pageChapters = parseChapterItems(fragmentDoc, storyId, currentStart)

                if (pageChapters.isEmpty()) {
                    break
                }
                onBatchReady(pageChapters)

                val nextStart = currentStart + pageSize
                val hasNextPage = fragmentDoc.selectFirst("ul.pagination li a[data-action='loadBookIndex'][data-start='$nextStart']") != null
                if (!hasNextPage) {
                    break
                }

                delay(crawlDelayMs)
                currentStart = nextStart
                pageCount++
            }
        }

        override suspend fun getChapterContent(chapterUrl: String): String =
            withContext(Dispatchers.IO) {
                val doc = fetchDocumentWithRetry(chapterUrl)
                checkLoginRequired(doc)

                val bodyElement =
                    doc.selectFirst("#bookContentBody, .book-content, #chapter-content")
                        ?: throw ScraperException.ParseException("Không tìm thấy nội dung chương", chapterUrl)

                val paragraphs = bodyElement.select("p").map { it.text().trim() }
                val content =
                    if (paragraphs.isNotEmpty()) {
                        paragraphs
                            .filter { it.isNotBlank() }
                            .map { cleanWatermark(it) }
                            .joinToString("\n\n")
                    } else {
                        bodyElement.select("br").append("\\n")
                        val raw = bodyElement.text().replace("\\n", "\n")
                        raw
                            .lines()
                            .map { cleanWatermark(it.trim()) }
                            .filter { it.isNotBlank() }
                            .joinToString("\n\n")
                    }

                if (content.isBlank()) {
                    throw ScraperException.ParseException("Nội dung chương trống", chapterUrl)
                }
                content
            }

        override suspend fun getCompletedStories(
            page: Int,
            maleOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val startParam = if (page > 1) "&start=${(page - 1) * SEARCH_PAGE_SIZE}" else ""
                if (!maleOnly) {
                    val url = "$baseUrl/bang-xep-hang?so=5$startParam"
                    val doc = fetchDocumentWithRetry(url)
                    checkLoginRequired(doc)
                    val stories = parseStoryList(doc)
                    if (stories.isNotEmpty()) {
                        stories.map { it.copy(status = "Hoàn thành") }
                    } else {
                        val fallbackDoc =
                            fetchDocumentWithRetry(
                                "$baseUrl/tim-kiem?qs=2&$COMPLETED_STATUS_PARAM&$NEWEST_SORT_PARAM$startParam",
                            )
                        checkLoginRequired(fallbackDoc)
                        parseStoryList(fallbackDoc).map { it.copy(status = "Hoàn thành") }
                    }
                } else {
                    val url = "$baseUrl/tim-kiem?qs=2&$MALE_GENDER_PARAM&$COMPLETED_STATUS_PARAM&$NEWEST_SORT_PARAM$startParam"
                    val doc = fetchDocumentWithRetry(url)
                    checkLoginRequired(doc)
                    parseStoryList(doc).map { it.copy(status = "Hoàn thành") }
                }
            }

        override suspend fun getStoriesByGenre(
            genreSlug: String,
            page: Int,
            minChapters: Int,
            completedOnly: Boolean,
            maleOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val filterParam = GENRE_QUERY_MAP[genreSlug] ?: return@withContext emptyList()
                val genderParam = if (maleOnly) "&$MALE_GENDER_PARAM" else ""
                val statusParam = if (completedOnly) "&$COMPLETED_STATUS_PARAM" else ""
                val startParam = if (page > 1) "&start=${(page - 1) * SEARCH_PAGE_SIZE}" else ""
                val url = "$baseUrl/tim-kiem?qs=2&$filterParam$genderParam$statusParam&$NEWEST_SORT_PARAM$startParam"
                val doc = fetchDocumentWithRetry(url)
                checkLoginRequired(doc)
                val stories = parseStoryList(doc)
                if (completedOnly) {
                    stories.map { it.copy(status = "Hoàn thành") }
                } else {
                    stories
                }
            }

        override suspend fun getGenreList(): List<Genre> =
            listOf(
                Genre("Tình cảm", "tinh-cam"),
                Genre("Làm sự nghiệp", "lam-su-nghiep"),
                Genre("Tiên hiệp", "tien-hiep"),
                Genre("Huyền huyễn", "huyen-huyen"),
                Genre("Khoa học viễn tưởng", "khoa-hoc-vien-tuong"),
                Genre("Mạt thế", "mat-the"),
                Genre("Sinh tồn", "sinh-ton"),
                Genre("Tranh bá", "tranh-ba"),
                Genre("Võ hiệp", "vo-hiep"),
                Genre("Trinh thám", "trinh-tham"),
                Genre("Kinh dị", "kinh-di"),
                Genre("Quan trường", "quan-truong"),
                Genre("Kinh thương", "kinh-thuong"),
                Genre("Quân sự", "quan-su"),
                Genre("Xây dựng", "xay-dung"),
                Genre("Làm ruộng", "lam-ruong"),
                Genre("Huyền học", "huyen-hoc"),
                Genre("Phim ảnh", "phim-anh"),
                Genre("Manga anime", "manga-anime"),
            )

        internal fun extractTotalChaptersFromDetail(doc: Document): Int {
            val coverLinks = doc.select(".cover-info a[href*='/truyen/']")
            val latestLink =
                coverLinks.lastOrNull { a ->
                    val href = a.attr("href")
                    LATEST_CHAPTER_HREF_REGEX.containsMatchIn(href) ||
                        a.parent()?.text()?.contains("Mới nhất", ignoreCase = true) == true
                } ?: coverLinks.lastOrNull { a ->
                    a.attr("href").trimEnd('/').count { it == '/' } >= 3
                }

            if (latestLink != null) {
                val linkText = latestLink.text().trim()
                val textMatches = LATEST_CHAPTER_TEXT_REGEX.findAll(linkText).toList()
                if (textMatches.isNotEmpty()) {
                    val lastMatch = textMatches.last()
                    val num =
                        lastMatch.groupValues
                            .drop(1)
                            .firstOrNull { it.isNotEmpty() }
                            ?.toIntOrNull()
                    if (num != null && num > 0) return num
                }

                val href = latestLink.attr("href")
                val hrefMatch = LATEST_CHAPTER_HREF_REGEX.find(href)
                val hrefNum = hrefMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (hrefNum != null && hrefNum in 1..20_000) return hrefNum
            }
            return 0
        }

        /**
         * Fallback khi khối .cover-info trên trang chi tiết không có số chương (ví dụ chương mới nhất là Phiên ngoại
         * hoặc truyện chỉ hiện nhãn Hoàn thành). Gọi AJAX /book/index trang đầu (start=0), kiểm tra thanh phân trang
         * data-start lớn nhất và đếm số chương ở trang cuối.
         */
        internal suspend fun fetchTotalChaptersFromBookIndex(
            doc: Document,
            storyUrl: String,
        ): Int =
            runCatching {
                val html = doc.html()
                val (bookId, cut, signKey) = extractSecurityParams(html, storyUrl)
                val pageSize = extractPageSize(html)
                val firstSign = makeBookIndexSign(signKey, 0, pageSize, cut)
                val firstUrl =
                    "$baseUrl/book/index?bookId=$bookId&start=0&size=$pageSize&signKey=$signKey&sign=$firstSign"
                val firstHtml = fetchAjaxFragment(firstUrl, storyUrl)
                val firstDoc = Jsoup.parseBodyFragment(firstHtml)
                val firstPageCount = firstDoc.select("li.chapter-name a[href^='/truyen/']").size
                if (firstPageCount == 0) return@runCatching 0

                var currentStart =
                    firstDoc
                        .select("ul.pagination li a[data-action='loadBookIndex']")
                        .mapNotNull { it.attr("data-start").toIntOrNull() }
                        .maxOrNull() ?: 0

                if (currentStart <= 0) {
                    return@runCatching firstPageCount
                }

                var jumps = 0
                var lastDoc = firstDoc
                var visitedStart = 0
                while (currentStart > visitedStart && jumps < MAX_PAGINATION_JUMPS) {
                    visitedStart = currentStart
                    val pageSign = makeBookIndexSign(signKey, currentStart, pageSize, cut)
                    val pageUrl =
                        "$baseUrl/book/index?bookId=$bookId&start=$currentStart&size=$pageSize&signKey=$signKey&sign=$pageSign"
                    val pageHtml = fetchAjaxFragment(pageUrl, storyUrl)
                    lastDoc = Jsoup.parseBodyFragment(pageHtml)
                    val nextMaxStart =
                        lastDoc
                            .select("ul.pagination li a[data-action='loadBookIndex']")
                            .mapNotNull { it.attr("data-start").toIntOrNull() }
                            .maxOrNull() ?: currentStart
                    if (nextMaxStart > currentStart) {
                        currentStart = nextMaxStart
                    }
                    jumps++
                }

                val lastPageCount = lastDoc.select("li.chapter-name a[href^='/truyen/']").size
                if (lastPageCount > 0) {
                    visitedStart + lastPageCount
                } else {
                    visitedStart
                }
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                Timber.w(e, "WikidichScraper: Failed to fetch total chapters from /book/index for %s", storyUrl)
                0
            }

        private suspend fun enrichWithChapterCounts(
            stories: List<Story>,
            maxItems: Int = ENRICH_MAX_ITEMS,
        ): List<Story> {
            if (stories.isEmpty() || maxItems <= 0) return stories
            if (customBaseUrl != null && !enrichInTests) return stories

            val toEnrich = stories.take(maxItems)
            val remaining = stories.drop(maxItems)
            val abortEnrich =
                java.util.concurrent.atomic
                    .AtomicBoolean(false)

            val enriched =
                coroutineScope {
                    val batches = toEnrich.chunked(ENRICH_BATCH_SIZE)
                    val results = mutableListOf<Story>()
                    for ((batchIndex, batch) in batches.withIndex()) {
                        if (abortEnrich.get()) {
                            results.addAll(batch)
                            continue
                        }
                        if (batchIndex > 0) {
                            delay(crawlDelayMs)
                        }
                        val batchResult =
                            batch
                                .map { story ->
                                    async {
                                        if (story.totalChapters > 0 || abortEnrich.get()) {
                                            story
                                        } else {
                                            try {
                                                withTimeoutOrNull(ENRICH_TIMEOUT_MS) {
                                                    val detailDoc = fetchDocument(story.url)
                                                    val total = extractTotalChaptersFromDetail(detailDoc)
                                                    if (total > 0) story.copy(totalChapters = total) else story
                                                } ?: story
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: ScraperException.HttpException) {
                                                if (e.statusCode in setOf(403, 429, 503)) {
                                                    abortEnrich.set(true)
                                                }
                                                story
                                            } catch (_: ScraperException.LoginRequiredException) {
                                                abortEnrich.set(true)
                                                story
                                            } catch (_: Exception) {
                                                story
                                            }
                                        }
                                    }
                                }.awaitAll()
                        results.addAll(batchResult)
                    }
                    results
                }
            return enriched + remaining
        }

        private fun checkLoginRequired(doc: Document) {
            if (doc.html().contains("Đăng nhập để xem nội dung")) {
                cache.clear()
                ajaxFragmentCache.clear()
                throw ScraperException.LoginRequiredException(
                    sourceId = sourceId,
                    message = "Vui lòng đăng nhập để xem nội dung từ Wikidich",
                )
            }
        }

        private fun parseStoryList(doc: Document): List<Story> {
            val items = doc.select(".book-item")
            val stories = mutableListOf<Story>()

            for (item in items) {
                val titleElement = item.selectFirst(".info-col a.tooltipped, .info-col a[href^='/truyen/'], h5.book-title")
                val link = titleElement?.attr("href") ?: continue
                val title = item.selectFirst("h5.book-title")?.text()?.trim() ?: titleElement.text().trim()
                if (title.isBlank()) continue

                val storyUrl = fixUrl(link)
                val author = item.selectFirst(".book-author a")?.text()?.trim() ?: "Chưa rõ"
                val coverImg = item.selectFirst(".cover-col img")
                val coverUrl = extractCoverUrl(coverImg)

                val genreText =
                    item
                        .selectFirst(".book-gender")
                        ?.text()
                        ?.trim()
                        .orEmpty()
                val genres = if (genreText.isNotBlank()) listOf(genreText) else emptyList()

                val statusElement =
                    item.selectFirst(".book-publisher a[href*='status=']")
                        ?: item.select(".book-publisher a").lastOrNull()
                val statusText = statusElement?.text()?.trim().orEmpty()
                val status =
                    if (statusText.contains("Hoàn thành", ignoreCase = true)) {
                        "Hoàn thành"
                    } else {
                        "Đang ra"
                    }

                val slug = extractSlug(storyUrl)

                stories.add(
                    Story(
                        id = "${sourceId}_$slug",
                        slug = slug,
                        title = title,
                        author = author,
                        coverImageUrl = coverUrl,
                        genres = genres,
                        status = status,
                        sourceId = sourceId,
                        url = storyUrl,
                    ),
                )
            }
            return stories
        }

        /**
         * Trích xuất URL ảnh bìa hợp lệ từ thẻ img, ưu tiên ứng viên đầu tiên trong [src, data-src].
         * Loại bỏ tham số query 'o' trên mọi đường dẫn '/photo/' để chuẩn hóa cache key theo ảnh danh sách.
         * Lưu ý: Áp dụng cho mọi host có '/photo/' vì scraper chỉ sinh URL thuộc baseUrl của chính nó
         * (bao gồm cả trường hợp chạy trên localhost trong MockWebServer unit test).
         */
        internal fun extractCoverUrl(img: Element?): String {
            if (img == null) return ""
            val candidates = listOf(img.attr("src").trim(), img.attr("data-src").trim())
            val raw =
                candidates
                    .firstOrNull { candidate ->
                        candidate.isNotBlank() &&
                            !candidate.startsWith("data:", ignoreCase = true) &&
                            !candidate.contains("base64", ignoreCase = true) &&
                            !candidate.contains("default-cover", ignoreCase = true) &&
                            !candidate.contains("default-avatar", ignoreCase = true)
                    }.orEmpty()
            if (raw.isBlank()) return ""

            val fixed = fixUrl(raw)
            val httpUrl = fixed.toHttpUrlOrNull() ?: return fixed
            return if (httpUrl.encodedPath.startsWith("/photo/") && httpUrl.queryParameter("o") != null) {
                httpUrl
                    .newBuilder()
                    .removeAllQueryParameters("o")
                    .build()
                    .toString()
            } else {
                fixed
            }
        }

        private fun parseChapterItems(
            doc: Document,
            storyId: String,
            startIndex: Int,
        ): List<Chapter> {
            val links = doc.select("li.chapter-name a[href^='/truyen/']")
            val chapters = mutableListOf<Chapter>()

            links.forEachIndexed { index, a ->
                val chapTitle = a.text().trim()
                val chapHref = a.attr("href").trim()
                if (chapTitle.isNotBlank() && chapHref.isNotBlank()) {
                    chapters.add(
                        Chapter(
                            storyId = storyId,
                            chapterNumber = startIndex + index + 1,
                            title = chapTitle,
                            url = fixUrl(chapHref),
                        ),
                    )
                }
            }
            return chapters
        }

        private fun extractSecurityParams(
            html: String,
            storyUrl: String,
        ): Triple<String, Int, String> {
            val bookIdMatch = Regex("""var\s+bookId\s*=\s*["']([^"']+)["']""").find(html)
            val cutMatch = Regex("""text\.substring\((\d+)\)""").find(html)
            val signKeyMatch = Regex("""var\s+signKey\s*=\s*["']([^"']+)["']""").find(html)

            val bookId = bookIdMatch?.groupValues?.get(1)
            val cut = cutMatch?.groupValues?.get(1)?.toIntOrNull()
            val signKey = signKeyMatch?.groupValues?.get(1)

            if (bookId.isNullOrBlank() || cut == null || signKey.isNullOrBlank()) {
                throw ScraperException.ParseException("Không tìm thấy thông số bảo mật danh sách chương từ trang truyện", storyUrl)
            }
            return Triple(bookId, cut, signKey)
        }

        private fun extractPageSize(html: String): Int =
            Regex("""loadBookIndex\(\s*\d+\s*,\s*(\d+)""")
                .find(html)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: DEFAULT_PAGE_SIZE

        private fun extractSlug(url: String): String {
            val path = url.substringAfter("/truyen/").substringBefore("?").substringBefore("#")
            return path.trimEnd('/')
        }

        internal fun fuzzySign(
            text: String,
            cut: Int,
        ): String {
            if (cut < 0 || cut > text.length) {
                throw ScraperException.ParseException("Vị trí cắt $cut vượt quá độ dài chuỗi ${text.length}")
            }
            return text.substring(cut) + text.substring(0, cut)
        }

        internal fun makeBookIndexSign(
            signKey: String,
            start: Int,
            size: Int,
            cut: Int,
        ): String {
            val raw = signKey + start + size
            val fuzzy = fuzzySign(raw, cut)
            return sha256Hex(fuzzy)
        }

        private fun sha256Hex(text: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        private suspend fun fetchAjaxFragment(
            url: String,
            referer: String,
        ): String {
            ajaxFragmentCache[url]?.let { entry ->
                if (nowMs() - entry.timestamp < AJAX_CACHE_TTL_MS) {
                    return entry.body
                } else {
                    ajaxFragmentCache.remove(url)
                }
            }

            val request =
                Request
                    .Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    ).header("Accept", "*/*")
                    .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", referer)
                    .build()

            var lastException: Exception? = null
            repeat(MAX_RETRIES) { attempt ->
                try {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw ScraperException.HttpException(response.code, url)
                        }
                        val body = response.body.string()
                        ajaxFragmentCache[url] = CacheEntry(body = body, timestamp = nowMs())
                        return body
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val scraperEx = e.toScraperException(url)
                    if (!isRetryableScraperException(scraperEx)) throw scraperEx
                    lastException = scraperEx
                    if (attempt < MAX_RETRIES - 1) {
                        delay(INITIAL_BACKOFF_MS * (1L shl attempt))
                    }
                }
            }
            throw (lastException as? ScraperException)
                ?: ScraperException.NetworkException("Không thể kết nối mạng. Vui lòng kiểm tra kết nối Internet.")
        }

        private fun cleanWatermark(text: String): String = WATERMARK_REGEX.replace(text, "").trim()

        private fun fixUrl(url: String): String =
            when {
                url.startsWith("https://") -> url
                url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1") -> url
                url.startsWith("http://") -> "https://${url.removePrefix("http://")}"
                url.startsWith("//") -> "https:$url"
                url.startsWith("/") -> "$baseUrl$url"
                else -> "$baseUrl/$url"
            }
    }
