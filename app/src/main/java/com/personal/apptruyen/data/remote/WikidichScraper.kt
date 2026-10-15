package com.personal.apptruyen.data.remote

import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.security.MessageDigest
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

        companion object {
            private const val DEFAULT_PAGE_SIZE = 99
            private const val MAX_PAGINATION_PAGES = 200
            private const val SEARCH_PAGE_SIZE = 20
            private const val ENRICH_BATCH_SIZE = 5
            private const val ENRICH_TIMEOUT_MS = 3_500L
            private const val DESKTOP_UA =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

            private val WATERMARK_REGEX = Regex("【❁.*\\(Wikidich\\).*❁】", RegexOption.IGNORE_CASE)
            private val LATEST_CHAPTER_TEXT_REGEX =
                Regex(
                    "(?:Chương|Hồi|Phần|Quyển\\s*\\d+\\s*[-–:]?\\s*Chương)\\s*(\\d+)",
                    RegexOption.IGNORE_CASE,
                )
            private val LATEST_CHAPTER_HREF_REGEX = Regex("/chuong-(\\d+)", RegexOption.IGNORE_CASE)

            val GENRE_QUERY_MAP =
                mapOf(
                    "tien-hiep" to "tag=5794f03dd7ced228f44191ad&genre=5794f03dd7ced228f44191a4",
                    "huyen-huyen" to "genre=57d17c94d7ced218fcd2734b",
                    "vo-hiep" to "genre=5794f03dd7ced228f44191a3",
                    "xuyen-khong" to "tag=5794f03dd7ced228f44191af",
                    "xuyen-viet" to "tag=5794f03dd7ced228f44191af",
                    "trong-sinh" to "tag=5794f03dd7ced228f44191b4",
                    "ngon-tinh" to "gender=5794f03dd7ced228f4419195",
                    "dam-my" to "gender=5794f03dd7ced228f4419197",
                    "bach-hop" to "gender=5794f03dd7ced228f4419198",
                    "truyen-nam" to "gender=5794f03dd7ced228f4419196",
                    "do-thi" to "age=5794f03dd7ced228f441919d",
                    "hien-dai" to "age=5794f03dd7ced228f441919d",
                    "co-dai" to "age=5794f03dd7ced228f441919b",
                    "tuong-lai" to "age=5794f03dd7ced228f441919e&tag=5794f03dd7ced228f44191c6",
                    "vong-du" to "genre=5794f03dd7ced228f44191a5",
                    "mat-the" to "tag=5794f03dd7ced228f44191ac",
                    "khoa-huyen" to "age=5794f03dd7ced228f441919e&tag=5794f03dd7ced228f44191c6",
                    "ma-phap" to "tag=5794f03dd7ced228f44191b0",
                    "dien-van" to "tag=5794f03dd7ced228f44191c0",
                    "lam-ruong" to "tag=5794f03dd7ced228f44191c0",
                    "my-thuc" to "tag=5794f03dd7ced228f44191c5",
                    "khong-gian" to "tag=5794f03dd7ced228f44191bb",
                    "trach-dau" to "tag=5794f03ed7ced228f44191dc",
                    "cung-dau" to "tag=5794f03dd7ced228f44191d7",
                    "trinh-tham" to "tag=5794f03dd7ced228f44191c1&genre=5794f03dd7ced228f44191a7",
                    "kinh-di" to "genre=5794f03dd7ced228f44191a6",
                    "linh-di" to "genre=5794f03dd7ced228f44191a6",
                    "he-thong" to "tag=5794f03dd7ced228f44191b6",
                    "gioi-giai-tri" to "tag=5794f03ed7ced228f44191de",
                    "giai-tri" to "tag=5794f03ed7ced228f44191de",
                    "sinh-con" to "tag=5794f03dd7ced228f44191c2",
                    "chu-cong" to "tag=5794f03dd7ced228f44191cc",
                    "dong-nhan" to "official=5794f03dd7ced228f4419194",
                    "dien-sinh" to "official=5794f03dd7ced228f4419194",
                    "hai-huoc" to "tag=58458dc3d7ced23af03679d9",
                    "nhe-nhang" to "tag=58458dc3d7ced23af03679cf",
                    "chua-lanh" to "tag=6032781123deea60925a9f2e",
                    "doan-sung" to "tag=603277ea23deea60925a9f2d",
                    "sung" to "tag=5794f03dd7ced228f44191b5",
                    "ngot-sung" to "tag=5794f03dd7ced228f44191b5",
                    "van-nhan-me" to "tag=6046549323deea4c3dfee6b4",
                    "he" to "ending=5794f03dd7ced228f441919f",
                    "be" to "ending=5794f03dd7ced228f44191a1",
                    "oe" to "ending=5794f03dd7ced228f44191a2",
                    "di-nang" to "tag=5794f03dd7ced228f44191b2",
                    "di-gioi" to "tag=5794f03dd7ced228f44191b1",
                )
        }

        override suspend fun search(
            keyword: String,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                // Chế độ 1 trên wikicv.org: Tìm kiếm theo từ khóa (?q=<keyword>&qs=1)
                val url = "$baseUrl/tim-kiem?q=$encoded&qs=1"
                val doc = fetchDocument(url)
                checkLoginRequired(doc)
                val stories = parseStoryList(doc)
                val filtered =
                    if (completedOnly) {
                        stories.filter { it.status.contains("Hoàn thành", ignoreCase = true) }.ifEmpty { stories }
                    } else {
                        stories
                    }
                if (customBaseUrl != null && enrichInTests) {
                    enrichWithChapterCounts(filtered, maxItems = 5)
                } else {
                    filtered
                }
            }

        override suspend fun getStoryDetail(storyUrl: String): Story =
            withContext(Dispatchers.IO) {
                val doc = fetchDocument(storyUrl)
                checkLoginRequired(doc)

                val title =
                    doc.selectFirst(".cover-info h2, .cover-info h1, .book-info h2")?.text()?.trim()
                        ?: throw ScraperException.ParseException("Không tìm thấy tiêu đề truyện", storyUrl)

                val author = doc.selectFirst(".cover-info a[href*='/tac-gia/']")?.text()?.trim() ?: "Chưa rõ"

                val coverImg = doc.selectFirst(".cover-info img, .book-info img")
                val coverUrl = coverImg?.attr("src")?.let { fixUrl(it) } ?: ""

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

                val totalChapters = extractTotalChaptersFromDetail(doc)
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
                val doc = fetchDocument(storyUrl)
                checkLoginRequired(doc)

                val (bookId, cut, signKey) = extractSecurityParams(doc.html(), storyUrl)
                val sign = makeBookIndexSign(signKey, 0, DEFAULT_PAGE_SIZE, cut)
                val indexUrl = "$baseUrl/book/index?bookId=$bookId&start=0&size=$DEFAULT_PAGE_SIZE&signKey=$signKey&sign=$sign"

                val fragmentHtml = fetchAjaxFragment(indexUrl, storyUrl)
                val fragmentDoc = Jsoup.parseBodyFragment(fragmentHtml)
                val storyId = "${sourceId}_${extractSlug(storyUrl)}"
                val chapters = parseChapterItems(fragmentDoc, storyId, 0)

                if (chapters.isEmpty()) {
                    throw ScraperException.ParseException("Không trích xuất được danh sách chương từ máy chủ Wikidich", indexUrl)
                }

                val paginationLinks = fragmentDoc.select("ul.pagination li a[data-action='loadBookIndex']")
                val totalPages =
                    if (paginationLinks.isNotEmpty()) {
                        paginationLinks.mapNotNull { it.text().toIntOrNull() }.maxOrNull() ?: 1
                    } else {
                        1
                    }

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
            val doc = fetchDocument(storyUrl)
            checkLoginRequired(doc)

            val (bookId, cut, signKey) = extractSecurityParams(doc.html(), storyUrl)
            val storyId = "${sourceId}_${extractSlug(storyUrl)}"
            var currentStart = DEFAULT_PAGE_SIZE
            var pageCount = 0

            while (pageCount < MAX_PAGINATION_PAGES) {
                val sign = makeBookIndexSign(signKey, currentStart, DEFAULT_PAGE_SIZE, cut)
                val indexUrl = "$baseUrl/book/index?bookId=$bookId&start=$currentStart&size=$DEFAULT_PAGE_SIZE&signKey=$signKey&sign=$sign"

                val fragmentHtml = fetchAjaxFragment(indexUrl, storyUrl)
                val fragmentDoc = Jsoup.parseBodyFragment(fragmentHtml)
                val pageChapters = parseChapterItems(fragmentDoc, storyId, currentStart)

                if (pageChapters.isEmpty()) {
                    break
                }
                onBatchReady(pageChapters)

                val nextStart = currentStart + DEFAULT_PAGE_SIZE
                val hasNextPage = fragmentDoc.selectFirst("ul.pagination li a[data-action='loadBookIndex'][data-start='$nextStart']") != null
                if (!hasNextPage) {
                    break
                }

                currentStart = nextStart
                pageCount++
            }
        }

        override suspend fun getChapterContent(chapterUrl: String): String =
            withContext(Dispatchers.IO) {
                val doc = fetchDocument(chapterUrl)
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

        override suspend fun getCompletedStories(page: Int): List<Story> =
            withContext(Dispatchers.IO) {
                val startParam = if (page > 1) "&start=${(page - 1) * SEARCH_PAGE_SIZE}" else ""
                val url = "$baseUrl/bang-xep-hang?so=5$startParam"
                val doc = fetchDocument(url)
                checkLoginRequired(doc)
                val stories = parseStoryList(doc)
                if (stories.isNotEmpty()) {
                    stories.map { it.copy(status = "Hoàn thành") }
                } else {
                    val fallbackDoc = fetchDocument("$baseUrl/tim-kiem?status=5794f03dd7ced228f4419191&qs=2$startParam")
                    checkLoginRequired(fallbackDoc)
                    parseStoryList(fallbackDoc).map { it.copy(status = "Hoàn thành") }
                }
            }

        override suspend fun getStoriesByGenre(
            genreSlug: String,
            page: Int,
            minChapters: Int,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val filterParam = GENRE_QUERY_MAP[genreSlug] ?: return@withContext emptyList()
                val statusParam = if (completedOnly) "&status=5794f03dd7ced228f4419191" else ""
                val cal = java.util.Calendar.getInstance()
                val month = cal.get(java.util.Calendar.MONTH) + 1
                val year = cal.get(java.util.Calendar.YEAR)
                val startParam = if (page > 1) "&start=${(page - 1) * SEARCH_PAGE_SIZE}" else ""
                // Chế độ 2 trên wikicv.org: Tìm kiếm theo bộ lọc (status, tag/genre/age/gender, tc, tf=0, m, y, q=)
                val url = "$baseUrl/tim-kiem?qs=1$statusParam&$filterParam&tc=&tf=0&m=$month&y=$year&q=$startParam"
                val doc = fetchDocument(url)
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
                Genre("Tiên Hiệp", "tien-hiep"),
                Genre("Huyền Huyễn", "huyen-huyen"),
                Genre("Võ Hiệp", "vo-hiep"),
                Genre("Xuyên Không", "xuyen-khong"),
                Genre("Xuyên Việt", "xuyen-viet"),
                Genre("Trọng Sinh", "trong-sinh"),
                Genre("Ngôn Tình", "ngon-tinh"),
                Genre("Đam Mỹ", "dam-my"),
                Genre("Bách Hợp", "bach-hop"),
                Genre("Truyện Nam", "truyen-nam"),
                Genre("Đô Thị", "do-thi"),
                Genre("Hiện Đại", "hien-dai"),
                Genre("Cổ Đại", "co-dai"),
                Genre("Tương Lai", "tuong-lai"),
                Genre("Võng Du", "vong-du"),
                Genre("Mạt Thế", "mat-the"),
                Genre("Khoa Huyễn", "khoa-huyen"),
                Genre("Ma Pháp", "ma-phap"),
                Genre("Điền Văn", "dien-van"),
                Genre("Mỹ Thực", "my-thuc"),
                Genre("Không Gian", "khong-gian"),
                Genre("Trạch Đấu", "trach-dau"),
                Genre("Cung Đấu", "cung-dau"),
                Genre("Trinh Thám", "trinh-tham"),
                Genre("Linh Dị", "linh-di"),
                Genre("Hệ Thống", "he-thong"),
                Genre("Giới Giải Trí", "gioi-giai-tri"),
                Genre("Sinh Con", "sinh-con"),
                Genre("Chủ Công", "chu-cong"),
                Genre("Đồng Nhân", "dong-nhan"),
                Genre("Diễn Sinh", "dien-sinh"),
                Genre("Hài Hước", "hai-huoc"),
                Genre("Nhẹ Nhàng", "nhe-nhang"),
                Genre("Chữa Lành", "chua-lanh"),
                Genre("Đoàn Sủng", "doan-sung"),
                Genre("Ngọt Sủng", "ngot-sung"),
                Genre("Vạn Nhân Mê", "van-nhan-me"),
                Genre("Dị Năng", "di-nang"),
                Genre("Dị Giới", "di-gioi"),
            )

        internal fun extractTotalChaptersFromDetail(doc: Document): Int {
            val latestLink =
                doc.select(".cover-info a[href*='/truyen/']").lastOrNull { a ->
                    val href = a.attr("href")
                    href.contains("/chuong-") || a.parent()?.text()?.contains("Mới nhất", ignoreCase = true) == true
                } ?: doc.select(".cover-info a[href*='/truyen/']").lastOrNull()

            if (latestLink != null) {
                val linkText = latestLink.text().trim()
                val textMatches = LATEST_CHAPTER_TEXT_REGEX.findAll(linkText).toList()
                if (textMatches.isNotEmpty()) {
                    val num =
                        textMatches
                            .last()
                            .groupValues
                            .getOrNull(1)
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

        private suspend fun enrichWithChapterCounts(
            stories: List<Story>,
            maxItems: Int = 10,
        ): List<Story> {
            if (stories.isEmpty() || maxItems <= 0) return stories
            if (customBaseUrl != null && !enrichInTests) return stories

            val toEnrich = stories.take(maxItems)
            val remaining = stories.drop(maxItems)

            val enriched =
                coroutineScope {
                    toEnrich.chunked(ENRICH_BATCH_SIZE).flatMap { batch ->
                        batch
                            .map { story ->
                                async {
                                    if (story.totalChapters > 0) {
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
                                        } catch (_: Exception) {
                                            story
                                        }
                                    }
                                }
                            }.awaitAll()
                    }
                }
            return enriched + remaining
        }

        private fun checkLoginRequired(doc: Document) {
            if (doc.html().contains("Đăng nhập để xem nội dung")) {
                cache.clear()
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
                val coverUrl = coverImg?.let { it.attr("src").ifBlank { it.attr("data-src") } }?.let { fixUrl(it) } ?: ""

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
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                    ).header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", referer)
                    .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw ScraperException.HttpException(response.code, url)
                    }
                    return response.body.string()
                }
            } catch (e: Exception) {
                throw e.toScraperException(url)
            }
        }

        private fun cleanWatermark(text: String): String = WATERMARK_REGEX.replace(text, "").trim()

        private fun fixUrl(url: String): String =
            when {
                url.startsWith("http://") || url.startsWith("https://") -> url
                url.startsWith("//") -> "https:$url"
                url.startsWith("/") -> "$baseUrl$url"
                else -> "$baseUrl/$url"
            }
    }
