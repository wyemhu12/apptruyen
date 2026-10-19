package com.personal.apptruyen.data.remote

import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.nodes.Document
import timber.log.Timber
import java.io.IOException
import java.net.URLEncoder
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * Web scraper cho SsTruyen (sstruyen.pics).
 * Hỗ trợ chuyển đổi từ sstruyen.net cũ, REST API cho danh sách chương và Tailwind prose cho nội dung.
 */
@Singleton
class SsTruyenScraper
    @Inject
    constructor(
        client: OkHttpClient,
    ) : BaseScraper(client) {

        override val sourceId = "sstruyen"
        override val sourceName = "SsTruyen"
        override val legacyHosts: List<String> = LEGACY_HOSTS

        internal var customBaseUrl: String? = null
        internal var customApiBaseUrl: String? = null

        override val baseUrl: String get() = customBaseUrl ?: BASE_URL
        internal val apiBaseUrl: String get() = customApiBaseUrl ?: API_BASE_URL

        override val adsSelectors: String = "script, .ads, .ad, iframe, noscript"

        override val contentSelectors =
            listOf(
                "div.prose",
                "div.wrapper.prose",
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

        internal val novelIdCache = ConcurrentHashMap<String, Long>()

        companion object {
            const val BASE_URL = "https://sstruyen.pics"
            const val API_BASE_URL = "https://api.sstruyen.pics"
            const val CDN_BASE_URL = "https://file.sstruyen.pics/files"
            val LEGACY_HOSTS = listOf("sstruyen.net")

            val LEGACY_CONTENT_SELECTORS =
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

            private const val MAX_RETRIES = 3
            private const val RETRY_DELAY_MS = 1000L

            private val CHAPTER_NUMBER_FROM_SLUG_REGEX = Regex("^chuong-(\\d+)")
            private val CHAPTER_NUMBER_FROM_NAME_REGEX = Regex("(?:Chương|Hồi|C)\\s*(\\d+)", RegexOption.IGNORE_CASE)
            private val GENRE_SLUG_REGEX = Regex("/the-loai/([a-z0-9-]+)")
            private val CHAPTER_COUNT_REGEX = Regex("(\\d+)\\s*chương", RegexOption.IGNORE_CASE)
            private val STORY_SLUG_REGEX = Regex("/truyen/([a-z0-9-]+)")
            private val SEPARATOR_LINE_REGEX = Regex("^[=\\-*_~]{3,}$")
        }

        private fun normalizeUrl(url: String): String {
            var res = url
            for (legacy in LEGACY_HOSTS) {
                res = res.replace("https://$legacy", baseUrl).replace("http://$legacy", baseUrl)
            }
            return res
        }

        internal fun extractImageSrc(img: org.jsoup.nodes.Element?): String? {
            if (img == null) return null
            val candidates =
                listOf(
                    img.attr("data-src"),
                    img.attr("data-lazy-src"),
                    img.attr("data-original"),
                    img.attr("src"),
                    img.attr("abs:src"),
                )
            return candidates.firstOrNull { raw ->
                val s = raw.trim()
                s.isNotEmpty() &&
                    !s.startsWith("data:", ignoreCase = true) &&
                    !s.contains("base64", ignoreCase = true) &&
                    !s.endsWith("/imgs/logo.webp", ignoreCase = true)
            }
        }

        internal fun normalizeCoverUrl(coverUrl: String?): String {
            if (coverUrl.isNullOrBlank()) return ""
            val trimmed = coverUrl.trim().trim('\'', '"')
            if (
                trimmed.isEmpty() ||
                trimmed.startsWith("data:", ignoreCase = true) ||
                trimmed.contains("base64", ignoreCase = true) ||
                trimmed.endsWith("/imgs/logo.webp", ignoreCase = true)
            ) {
                return ""
            }
            val fullUrl =
                when {
                    trimmed.startsWith(CDN_BASE_URL) -> {
                        val suffix = trimmed.removePrefix(CDN_BASE_URL).trimStart('/')
                        "$CDN_BASE_URL/$suffix"
                    }
                    trimmed.contains("/files/") -> {
                        val suffix = trimmed.substringAfter("/files/").trimStart('/')
                        "$CDN_BASE_URL/$suffix"
                    }
                    trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                    trimmed.startsWith("/covers/") || trimmed.startsWith("covers/") -> {
                        val suffix = trimmed.substringAfter("covers/").trimStart('/')
                        "$CDN_BASE_URL/covers/$suffix"
                    }
                    trimmed.startsWith("/") -> "$CDN_BASE_URL/${trimmed.trimStart('/')}"
                    else -> "$CDN_BASE_URL/${trimmed.trimStart('/')}"
                }
            return validateCoverUrl(fullUrl)
        }

        internal suspend fun fetchJson(url: String): JSONObject {
            var lastException: Exception? = null
            for (attempt in 1..MAX_RETRIES) {
                try {
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .header("User-Agent", userAgent)
                            .header("Accept", "application/json")
                            .build()
                    return withContext(Dispatchers.IO) {
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw ScraperException.HttpException(response.code, url)
                            }
                            val body = response.body.string()
                            if (body.isBlank()) {
                                throw ScraperException.ParseException("Phản hồi JSON rỗng", url)
                            }
                            try {
                                JSONObject(body)
                            } catch (e: JSONException) {
                                throw ScraperException.ParseException("Lỗi định dạng dữ liệu danh sách chương", url, e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    lastException = e
                    val isRetryable = e is IOException || (e is ScraperException.HttpException && (e.statusCode == 429 || e.statusCode >= 500))
                    if (!isRetryable) {
                        throw e
                    }
                    if (attempt < MAX_RETRIES) {
                        delay(RETRY_DELAY_MS * attempt)
                    }
                }
            }
            throw lastException as? ScraperException
                ?: ScraperException.NetworkException("Lỗi kết nối khi tải dữ liệu từ máy chủ", lastException)
        }

        private suspend fun fetchJsonArray(url: String): JSONArray {
            var lastException: Exception? = null
            for (attempt in 1..MAX_RETRIES) {
                try {
                    val request =
                        Request
                            .Builder()
                            .url(url)
                            .header("User-Agent", userAgent)
                            .header("Accept", "application/json")
                            .build()
                    return withContext(Dispatchers.IO) {
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                throw ScraperException.HttpException(response.code, url)
                            }
                            val body = response.body.string()
                            if (body.isBlank()) {
                                throw ScraperException.ParseException("Phản hồi JSON rỗng", url)
                            }
                            try {
                                JSONArray(body)
                            } catch (e: JSONException) {
                                throw ScraperException.ParseException("Lỗi định dạng dữ liệu danh sách thể loại", url, e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    lastException = e
                    val isRetryable = e is IOException || (e is ScraperException.HttpException && (e.statusCode == 429 || e.statusCode >= 500))
                    if (!isRetryable) {
                        throw e
                    }
                    if (attempt < MAX_RETRIES) {
                        delay(RETRY_DELAY_MS * attempt)
                    }
                }
            }
            throw lastException as? ScraperException
                ?: ScraperException.NetworkException("Lỗi kết nối khi tải dữ liệu từ máy chủ", lastException)
        }

        private suspend fun fetchNovelDetailFromApi(slug: String): JSONObject? =
            try {
                fetchJson("$apiBaseUrl/novels/$slug")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.w(e, "fetchNovelDetailFromApi failed for slug %s", slug)
                null
            }

        /**
         * Tìm kiếm truyện.
         * URL: /tim-kiem?keyword={keyword}
         */
        override suspend fun search(
            keyword: String,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                val url = "$baseUrl/tim-kiem?keyword=$encoded"
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
         */
        override suspend fun getStoryDetail(storyUrl: String): Story =
            withContext(Dispatchers.IO) {
                val normalizedUrl = normalizeUrl(storyUrl)
                val doc = fetchDocumentWithRetry(normalizedUrl)
                val slug = extractStorySlug(normalizedUrl)

                val title =
                    doc
                        .select("h1, p[itemProp=name], .title")
                        .first()
                        ?.text()
                        ?.trim()
                        ?: doc
                            .select("meta[property=og:title]")
                            .attr("content")
                            .substringBefore(" - ")
                            .trim()
                            .ifBlank { "Đang cập nhật" }

                val author =
                    doc
                        .select("div[itemProp=author] span[itemProp=name], a[href*=tac-gia], .author")
                        .first()
                        ?.text()
                        ?.trim() ?: ""

                val genres =
                    doc
                        .select("a[href*=/the-loai/]")
                        .map { it.text().trim() }
                        .filter {
                            it.isNotBlank() && it != "Thể loại" && it != "truyen-sac"
                        }.distinct()

                val wallpaperStyle = doc.select(".wallpaper[style]").first()?.attr("style") ?: ""
                val bgUrlMatch = Regex("""url\(([^)]+)\)""").find(wallpaperStyle)
                val coverRaw =
                    extractImageSrc(doc.select("img[itemProp=image], img[itemprop=image]").firstOrNull())
                        ?: bgUrlMatch?.groupValues?.get(1)
                        ?: doc.select("meta[property=og:image]").attr("content")
                var coverImageUrl = normalizeCoverUrl(coverRaw)

                val ogDesc = doc.select("meta[property=og:description]").attr("content")
                val chapterFromPage =
                    CHAPTER_COUNT_REGEX
                        .find(doc.text())
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0
                val totalChaptersFromOg =
                    CHAPTER_COUNT_REGEX
                        .find(ogDesc)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0

                var novelId = novelIdCache[slug]
                val apiDetail = if (novelId == null || coverImageUrl.isBlank()) fetchNovelDetailFromApi(slug) else null
                if (apiDetail != null) {
                    val apiNovelId = apiDetail.optLong("id")
                    if (apiNovelId > 0) {
                        novelId = apiNovelId
                        novelIdCache[slug] = apiNovelId
                    }
                    if (coverImageUrl.isBlank()) {
                        coverImageUrl = normalizeCoverUrl(apiDetail.optString("coverUrl"))
                    }
                }
                if (novelId == null) {
                    novelId = extractNovelId(doc, slug)
                    if (novelId != null) {
                        novelIdCache[slug] = novelId
                    }
                }

                val chapterCountFromApi = apiDetail?.optInt("chapterCount", 0) ?: 0
                val totalChapters = maxOf(chapterFromPage, totalChaptersFromOg, chapterCountFromApi)

                val hasFullBadge =
                    doc
                        .select(
                            "span[itemProp=bookFormat], span.hoan-thanh, .hoan-thanh, span.hoan-thanh-mau, .hoan-thanh-mau",
                        ).any { el ->
                            val text = el.text().trim()
                            text.contains("hoàn thành", ignoreCase = true) || text.contains("Full", ignoreCase = true)
                        }
                val statusText = doc.select("span[itemProp=bookFormat], .chi-tiet.tt-status, .tt-status").text()
                val status = if (hasFullBadge) "Hoàn thành" else normalizeStatus(statusText)

                val descText =
                    doc
                        .select("div[itemProp=description], .desc-text, .description")
                        .first()
                        ?.let { el ->
                            el.select(".short-content").remove()
                            cleanHtml(el.html())
                        }?.trim()
                        ?.ifBlank { null }
                val description = descText ?: ogDesc.ifBlank { "" }

                Story(
                    id = "${sourceId}_$slug",
                    slug = slug,
                    title = title,
                    author = author,
                    genres = genres,
                    description = description,
                    url = normalizedUrl.ensureAbsolute(),
                    totalChapters = totalChapters,
                    coverImageUrl = coverImageUrl,
                    sourceId = sourceId,
                    status = status,
                )
            }

        private fun extractNovelId(
            doc: Document,
            slug: String,
        ): Long? {
            val html = doc.html()
            val escapedSlug = Regex.escape(slug)

            val pattern1 = Regex("\"(?:data|novel)\"\\s*:\\s*\\{[^}]*?\"id\"\\s*:\\s*(\\d+)[^}]*?\"slug\"\\s*:\\s*\"$escapedSlug\"")
            val match1 = pattern1.find(html)
            if (match1 != null) {
                val id = match1.groupValues.getOrNull(1)?.toLongOrNull()
                if (id != null) return id
            }

            val pattern2 = Regex("\"slug\"\\s*:\\s*\"$escapedSlug\"[^}]*?\"id\"\\s*:\\s*(\\d+)")
            val match2 = pattern2.find(html)
            if (match2 != null) {
                val id = match2.groupValues.getOrNull(1)?.toLongOrNull()
                if (id != null) return id
            }

            val pattern3 = Regex("\"id\"\\s*:\\s*(\\d+)[^}]*?\"slug\"\\s*:\\s*\"$escapedSlug\"")
            val match3 = pattern3.find(html)
            if (match3 != null) {
                val id = match3.groupValues.getOrNull(1)?.toLongOrNull()
                if (id != null) return id
            }

            val dataNovelId = doc.selectFirst("[data-novel-id]")?.attr("data-novel-id")?.toLongOrNull()
            if (dataNovelId != null) return dataNovelId

            val patternScript = Regex("\"novelId\"\\s*:\\s*(\\d+)")
            val matchScript = patternScript.find(html)
            if (matchScript != null) {
                val id = matchScript.groupValues.getOrNull(1)?.toLongOrNull()
                if (id != null) return id
            }

            return null
        }

        private fun parseChapterNumber(
            chapSlug: String,
            name: String,
            page: Int,
            index: Int,
        ): Int {
            val matchSlug = CHAPTER_NUMBER_FROM_SLUG_REGEX.find(chapSlug)
            if (matchSlug != null) {
                val num = matchSlug.groupValues.getOrNull(1)?.toIntOrNull()
                if (num != null) return num
            }

            val matchName = CHAPTER_NUMBER_FROM_NAME_REGEX.find(name)
            if (matchName != null) {
                val num = matchName.groupValues.getOrNull(1)?.toIntOrNull()
                if (num != null) return num
            }

            return (page - 1) * 50 + index + 1
        }

        /**
         * Lấy trang đầu danh sách chương.
         */
        override suspend fun getFirstPageChapters(storyUrl: String): StorySource.FirstPageResult =
            withContext(Dispatchers.IO) {
                val normalizedUrl = normalizeUrl(storyUrl)
                val slug = extractStorySlug(normalizedUrl)

                var novelId = novelIdCache[slug]
                if (novelId == null) {
                    val apiDetail = fetchNovelDetailFromApi(slug)
                    if (apiDetail != null) {
                        val id = apiDetail.optLong("id")
                        if (id > 0) {
                            novelId = id
                            novelIdCache[slug] = id
                        }
                    }
                }

                val doc =
                    if (novelId == null) {
                        val d = fetchDocumentWithRetry(normalizedUrl)
                        novelId = extractNovelId(d, slug)
                        if (novelId != null) {
                            novelIdCache[slug] = novelId
                        }
                        d
                    } else {
                        null
                    }

                if (novelId != null) {
                    try {
                        val apiUrl = "$apiBaseUrl/novels/$novelId/chapters?page=1&limit=50&sort=asc"
                        val json = fetchJson(apiUrl)
                        val total = json.optInt("total", 0)
                        val totalPages =
                            json.optInt("totalPages", 0).takeIf { it > 0 }
                                ?: maxOf(1, ceil(total / 50.0).toInt())
                        val jsonChapters = json.optJSONArray("chapters")
                        val chapters = mutableListOf<Chapter>()
                        val seenNumbers = HashSet<Int>()

                        if (jsonChapters != null) {
                            for (i in 0 until jsonChapters.length()) {
                                val obj = jsonChapters.getJSONObject(i)
                                val id = obj.optLong("id")
                                val name = obj.optString("name").trim()
                                val chapSlug = obj.optString("slug").trim()
                                val chapterNumber = parseChapterNumber(chapSlug, name, 1, i)
                                if (!seenNumbers.add(chapterNumber)) {
                                    Timber.w("Duplicate chapterNumber %d detected for story %s, chapter %s", chapterNumber, slug, name)
                                }
                                val chapterUrl = "$baseUrl/doc-truyen/$slug-$chapSlug-$id"
                                chapters.add(
                                    Chapter(
                                        storyId = "${sourceId}_$slug",
                                        chapterNumber = chapterNumber,
                                        title = name.ifBlank { "Chương $chapterNumber" },
                                        url = chapterUrl,
                                    ),
                                )
                            }
                        }

                        if (chapters.isNotEmpty()) {
                            return@withContext StorySource.FirstPageResult(
                                chapters = chapters.sortedBy { it.chapterNumber },
                                totalPages = totalPages,
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Timber.w(e, "API getFirstPageChapters failed for %s, falling back to HTML", slug)
                    }
                }

                // Fallback qua HTML
                val fallbackDoc = doc ?: fetchDocumentWithRetry(normalizedUrl)
                val seen = mutableSetOf<Int>()
                val canonicalStoryId = "${sourceId}_$slug"
                val chapters =
                    parseChapterLinks(fallbackDoc, slug)
                        .filter { seen.add(it.number) }
                        .sortedBy { it.number }
                        .map { it.toChapter(canonicalStoryId) }

                val totalPages = getTotalChapterPages(fallbackDoc, normalizedUrl)
                StorySource.FirstPageResult(chapters = chapters, totalPages = totalPages)
            }

        /**
         * Crawl các trang chương còn lại qua REST API hoặc HTML fallback.
         */
        override suspend fun getRemainingChapters(
            storyUrl: String,
            totalPages: Int,
            onBatchReady: suspend (List<Chapter>) -> Unit,
        ) = withContext(Dispatchers.IO) {
            if (totalPages <= 1) return@withContext

            val normalizedUrl = normalizeUrl(storyUrl)
            val slug = extractStorySlug(normalizedUrl)

            var novelId = novelIdCache[slug]
            if (novelId == null) {
                val apiDetail = fetchNovelDetailFromApi(slug)
                if (apiDetail != null) {
                    val id = apiDetail.optLong("id")
                    if (id > 0) {
                        novelId = id
                        novelIdCache[slug] = id
                    }
                }
            }
            if (novelId == null) {
                try {
                    getStoryDetail(normalizedUrl)
                    novelId = novelIdCache[slug]
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.w(e, "Recovery getStoryDetail failed for %s", slug)
                }
            }

            if (novelId == null) {
                throw ScraperException.ParseException(
                    "Không thể xác định mã định danh truyện để tải danh sách chương",
                    storyUrl,
                )
            }

            val semaphore = Semaphore(3)
            val failedPages = Collections.synchronizedList(mutableListOf<Int>())

            coroutineScope {
                for (page in 2..totalPages) {
                    launch {
                        semaphore.withPermit {
                            try {
                                delay(crawlDelayMs)
                                val apiUrl = "$apiBaseUrl/novels/$novelId/chapters?page=$page&limit=50&sort=asc"
                                val json = fetchJson(apiUrl)
                                val jsonChapters = json.optJSONArray("chapters")
                                val batch = mutableListOf<Chapter>()
                                val seenNumbers = HashSet<Int>()

                                if (jsonChapters != null) {
                                    for (i in 0 until jsonChapters.length()) {
                                        val obj = jsonChapters.getJSONObject(i)
                                        val id = obj.optLong("id")
                                        val name = obj.optString("name").trim()
                                        val chapSlug = obj.optString("slug").trim()
                                        val chapterNumber = parseChapterNumber(chapSlug, name, page, i)
                                        if (!seenNumbers.add(chapterNumber)) {
                                            Timber.w("Duplicate chapterNumber %d detected for story %s, chapter %s", chapterNumber, slug, name)
                                        }
                                        val chapterUrl = "$baseUrl/doc-truyen/$slug-$chapSlug-$id"
                                        batch.add(
                                            Chapter(
                                                storyId = "${sourceId}_$slug",
                                                chapterNumber = chapterNumber,
                                                title = name.ifBlank { "Chương $chapterNumber" },
                                                url = chapterUrl,
                                            ),
                                        )
                                    }
                                }

                                if (batch.isNotEmpty()) {
                                    onBatchReady(batch.sortedBy { it.chapterNumber })
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Timber.e(e, "Chapter page %d crawl failed for story %s", page, slug)
                                failedPages.add(page)
                            }
                        }
                    }
                }
            }

            if (failedPages.isNotEmpty()) {
                throw ScraperException.NetworkException(
                    "Không thể tải đầy đủ danh sách chương (lỗi các trang: ${failedPages.sorted()})",
                )
            }
        }

        /**
         * Lấy nội dung chương.
         * Phân luồng thông minh theo định dạng URL:
         * - Nhánh cũ: sstruyen.net (/chuong-) trích xuất bằng 9 selector kế thừa và cleanHtml (<br>).
         * - Nhánh mới: sstruyen.pics (/doc-truyen/) trích xuất qua div.prose, div.wrapper.prose.
         */
        override suspend fun getChapterContent(chapterUrl: String): String =
            withContext(Dispatchers.IO) {
                val isLegacyRoute = chapterUrl.contains("/chuong-") && !chapterUrl.contains("/doc-truyen/")
                val targetUrl =
                    if (isLegacyRoute) {
                        chapterUrl
                    } else {
                        normalizeUrl(chapterUrl)
                    }

                val doc = fetchDocumentWithRetry(targetUrl)

                val rawContent =
                    if (isLegacyRoute) {
                        val element =
                            LEGACY_CONTENT_SELECTORS.firstNotNullOfOrNull { selector ->
                                doc.selectFirst(selector)
                            } ?: throw ScraperException.ParseException("Không tìm thấy nội dung chương", chapterUrl)
                        cleanHtml(element.html())
                    } else {
                        val proseEl =
                            doc.selectFirst("div.prose, div.wrapper.prose")
                                ?: throw ScraperException.ParseException("Không tìm thấy nội dung chương", chapterUrl)
                        proseEl.select(adsSelectors).remove()
                        proseEl.select("div.text-center").remove()
                        proseEl.select("br").append("\\n")
                        proseEl.select("p").prepend("\\n").append("\\n")
                        proseEl.text().replace("\\n", "\n")
                    }

                val cleaned = cleanContentText(rawContent)
                if (cleaned.length < 50) {
                    throw ScraperException.ParseException("Nội dung chương rỗng", chapterUrl)
                }
                cleaned
            }

        private fun cleanContentText(content: String): String =
            content
                .lines()
                .map { it.replace('\u00A0', ' ').trim() }
                .filter { line ->
                    line.isNotBlank() &&
                        !line.contains("Chương trước", ignoreCase = true) &&
                        !line.contains("Chương tiếp", ignoreCase = true) &&
                        !line.contains("sstruyen.pics", ignoreCase = true) &&
                        !line.contains("sstruyen.net", ignoreCase = true) &&
                        !line.contains("Nếu truyện không hiện chữ", ignoreCase = true) &&
                        !SEPARATOR_LINE_REGEX.matches(line)
                }.joinToString("\n\n")

        /**
         * Truyện hoàn thành.
         * URL: /danh-sach/bo-loc?status=completed&sort=updatedAt&page={page}
         */
        override suspend fun getCompletedStories(page: Int): List<Story> =
            withContext(Dispatchers.IO) {
                val url = "$baseUrl/danh-sach/bo-loc?status=completed&sort=updatedAt&page=$page"
                parseStoryListPage(url).map { it.copy(status = "Hoàn thành") }
            }

        /**
         * Truyện theo thể loại.
         * Luôn sắp xếp mới nhất: sort=updatedAt.
         * Tham số hoàn thành: status=completed.
         */
        override suspend fun getStoriesByGenre(
            genreSlug: String,
            page: Int,
            minChapters: Int,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val statusParam = if (completedOnly) "status=completed" else ""
                val typeParam =
                    when {
                        minChapters >= 1000 -> "type=3"
                        minChapters >= 500 -> "type=2"
                        minChapters >= 100 -> "type=1"
                        else -> ""
                    }
                val params =
                    listOf(
                        "genre=$genreSlug",
                        statusParam,
                        typeParam,
                        "sort=updatedAt",
                        "page=$page",
                    ).filter { it.isNotBlank() }.joinToString("&")

                val url = "$baseUrl/danh-sach/bo-loc?$params"
                val stories = parseStoryListPage(url)
                val withStatus = if (completedOnly) stories.map { it.copy(status = "Hoàn thành") } else stories
                if (minChapters > 0) {
                    withStatus.filter { it.totalChapters == 0 || it.totalChapters >= minChapters }
                } else {
                    withStatus
                }
            }

        /**
         * Danh sách thể loại.
         */
        override suspend fun getGenreList(): List<Genre> =
            withContext(Dispatchers.IO) {
                try {
                    val jsonArray = fetchJsonArray("$apiBaseUrl/genres")
                    val genres = mutableListOf<Genre>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i) ?: continue
                        val name = obj.optString("name").trim()
                        val slug = obj.optString("slug").trim()
                        if (name.isBlank() || slug.isBlank() || slug == "truyen-sac" || name == "Thể loại") continue
                        genres.add(Genre(name = name, slug = slug))
                    }
                    if (genres.isNotEmpty()) {
                        return@withContext genres.distinctBy { it.slug }.sortedBy { it.name }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.w(e, "API getGenreList failed, falling back to HTML")
                }

                val doc =
                    try {
                        fetchDocumentWithRetry("$baseUrl/danh-sach/bo-loc")
                    } catch (e: Exception) {
                        fetchDocumentWithRetry(baseUrl)
                    }
                val genres = mutableListOf<Genre>()

                doc.select("a[href*=/the-loai/]").forEach { el ->
                    val href = el.attr("href")
                    val name = el.text().trim()

                    val slugMatch = GENRE_SLUG_REGEX.find(href)
                    val slug = slugMatch?.groupValues?.get(1) ?: return@forEach

                    if (name.isBlank() || name == "Thể loại" || slug == "truyen-sac") return@forEach

                    genres.add(Genre(name = name, slug = slug))
                }

                genres.distinctBy { it.slug }.sortedBy { it.name }
            }

        // --- Private helpers ---

        private fun parseStoryList(doc: Document): List<Story> {
            val results = mutableListOf<Story>()

            val hasPartItems = doc.select("a[itemProp=hasPart]")
            if (hasPartItems.isNotEmpty()) {
                hasPartItems.forEach { item ->
                    val href = item.attr("abs:href").ifEmpty { item.attr("href") }
                    val titleEl = item.select("p[itemProp=name], h3, .title").firstOrNull()
                    val title = titleEl?.text()?.trim() ?: item.text().trim()
                    if (title.isBlank() || href.isBlank()) return@forEach
                    val slug = extractStorySlug(href)
                    if (slug.isBlank()) return@forEach

                    val authorEl = item.select("div[itemProp=author] span[itemProp=name], a[href*=tac-gia]").firstOrNull()
                    val author = authorEl?.text()?.trim() ?: ""

                    val statusBadgeEl = item.select("span[itemProp=bookFormat], span.badge, .badge").firstOrNull()
                    val statusBadge = statusBadgeEl?.text()?.trim() ?: ""
                    val status = normalizeStatus(statusBadge)

                    val coverImg = item.select("img[itemProp=image], img[itemprop=image], img").firstOrNull()
                    val coverSrc = extractImageSrc(coverImg)
                    val coverUrl = normalizeCoverUrl(coverSrc)

                    val genres =
                        item.select("a[href*=/the-loai/]").map { it.text().trim() }.filter {
                            it.isNotBlank() && it != "Thể loại"
                        }

                    val chapterSpan = item.select("span[itemProp=dateModified], span[itemprop=dateModified]").firstOrNull()
                    val chapterText = chapterSpan?.text()?.trim() ?: item.text()
                    val totalChapters = extractChapterCount(chapterText, item.text())

                    results.add(
                        Story(
                            id = "${sourceId}_$slug",
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
                return results.distinctBy { it.id }
            }

            val containers = doc.select("div.home-truyendecu, div.each_truyen, div.item, div.story-item, article")
            val items =
                if (containers.isNotEmpty()) {
                    containers
                } else {
                    doc.select("div:has(> .caption a[href*=/truyen/]), div:has(> h3 a[href*=/truyen/])")
                }

            items.forEach { item ->
                val titleEl =
                    item.select(".caption a[href*=/truyen/], h3.story-title a, h3 a[href*=/truyen/]").firstOrNull()
                        ?: item.select("a[href*=/truyen/]").firstOrNull { it.text().length >= 5 }
                        ?: return@forEach

                val href = titleEl.attr("abs:href").ifEmpty { titleEl.attr("href") }
                val title = titleEl.text().trim()

                if (title.isBlank() || href.isBlank()) return@forEach
                if (href.contains("/chuong-") || href.contains("/doc-truyen/") || href.contains("/page/") || href.contains("#")) return@forEach
                if (title.length < 3) return@forEach

                val slug = extractStorySlug(href)
                if (slug.isBlank()) return@forEach

                val coverImg = item.select(".each_truyen img, a.cover img, .cover img, img").firstOrNull()
                val coverSrc = extractImageSrc(coverImg)
                val coverUrl = normalizeCoverUrl(coverSrc)

                val author =
                    item
                        .select("a[href*=tac-gia]")
                        .firstOrNull()
                        ?.text()
                        ?.trim() ?: ""

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

                val hasFullBadge = item.select("span.hoan-thanh, .hoan-thanh").isNotEmpty()
                val status = if (hasFullBadge) "Hoàn thành" else normalizeStatus(statusSmallText)

                val genres =
                    item.select("a[href*=/the-loai/]").map { it.text().trim() }.filter {
                        it.isNotBlank() && it != "Thể loại"
                    }

                results.add(
                    Story(
                        id = "${sourceId}_$slug",
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

            if (results.isEmpty()) {
                doc.select("a[href*=/truyen/]").forEach { el ->
                    val href = el.attr("abs:href").ifEmpty { el.attr("href") }
                    val title = el.text().trim()

                    if (title.isBlank() || href.isBlank()) return@forEach
                    if (href.contains("/chuong-") || href.contains("/doc-truyen/") || href.contains("/page/") || href.contains("#")) return@forEach
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
                    val coverSrc = coverImg?.let { img -> img.attr("abs:src").ifEmpty { img.attr("src") } }
                    val coverUrl = normalizeCoverUrl(coverSrc)

                    val hasFullBadge = parent?.select("span.hoan-thanh")?.isNotEmpty() == true
                    val status = if (hasFullBadge) "Hoàn thành" else normalizeStatus(parentText)

                    val genres =
                        parent?.select("a[href*=/the-loai/]")?.map { it.text().trim() }?.filter {
                            it.isNotBlank() && it != "Thể loại"
                        } ?: emptyList()

                    results.add(
                        Story(
                            id = "${sourceId}_$slug",
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

        private fun parseChapterLinks(
            doc: Document,
            slug: String,
        ): List<ChapterInfo> {
            val chapters = mutableListOf<ChapterInfo>()
            val seen = mutableSetOf<Int>()

            doc.select("a[href*=/doc-truyen/], a[href*=/chuong-]").forEach { el ->
                val href = el.attr("abs:href").ifEmpty { el.attr("href") }
                val text = el.text().trim()

                val numberMatch =
                    Regex("(?:chuong-|-chuong-)(\\d+)").find(href)
                        ?: Regex("(?:Chương|Hồi|C)\\s*(\\d+)", RegexOption.IGNORE_CASE).find(text)
                val number = numberMatch?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach

                if (number in seen) return@forEach
                seen.add(number)

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
            val lastLink =
                doc.select("a[href*=/page/], a[href*='page=']").firstOrNull { el ->
                    val text = el.text().trim()
                    text.contains("Cuối", ignoreCase = true)
                }

            if (lastLink != null) {
                val href = lastLink.attr("href")
                val match = Regex("(?:/page/|page=)(\\d+)").find(href)
                val pages = match?.groupValues?.get(1)?.toIntOrNull()
                if (pages != null && pages > 0) return pages
            }

            val maxPage =
                doc
                    .select("a[href*=/page/], a[href*='page=']")
                    .mapNotNull { el ->
                        val href = el.attr("href")
                        Regex("(?:/page/|page=)(\\d+)")
                            .find(href)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                    }.maxOrNull()

            return maxPage ?: 1
        }

        private fun extractChapterCount(
            primaryText: String,
            fallbackText: String,
        ): Int {
            val fromPrimary =
                CHAPTER_COUNT_REGEX
                    .find(primaryText)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: CHAPTER_NUMBER_FROM_NAME_REGEX
                        .find(primaryText)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
            if (fromPrimary != null && fromPrimary > 0) return fromPrimary

            return CHAPTER_COUNT_REGEX
                .find(fallbackText)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: CHAPTER_NUMBER_FROM_NAME_REGEX
                    .find(fallbackText)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                ?: 0
        }

        private fun extractStorySlug(url: String): String {
            val cleanUrl = url.substringBefore("?").substringBefore("#").trimEnd('/')
            val match = STORY_SLUG_REGEX.find(cleanUrl)
            return match?.groupValues?.get(1) ?: cleanUrl.substringAfterLast("/")
        }
    }
