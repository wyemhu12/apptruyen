package com.personal.apptruyen.data.remote

import android.util.Log
import com.personal.apptruyen.data.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.Collections

/**
 * Abstract base class cho tất cả web scrapers.
 * Chứa shared infrastructure: HTTP fetching, caching, HTML cleanup, retry logic.
 * Subclass chỉ cần implement parsing logic riêng cho mỗi nguồn.
 */
abstract class BaseScraper(
    protected val client: OkHttpClient,
) : StorySource {

    // ══════════════════════════════════════════════════
    // Configurable properties — subclass có thể override
    // ══════════════════════════════════════════════════

    /** User-Agent header. TTV dùng Desktop UA, còn lại dùng Mobile. */
    protected open val userAgent: String = DEFAULT_MOBILE_UA

    /** Delay giữa các request khi crawl chapters (ms). */
    protected open val crawlDelayMs: Long = 300L

    /** Bật ETag/Last-Modified conditional caching (TC, TTV). */
    protected open val useEtagCaching: Boolean = false

    /** CSS selectors cho nội dung chương, theo thứ tự ưu tiên. */
    protected open val contentSelectors: List<String> = DEFAULT_CONTENT_SELECTORS

    /** CSS selectors cho ads/scripts cần xóa khỏi nội dung chương. */
    protected open val adsSelectors: String = "script, .ads, .ad, iframe, noscript, .text-center"

    // ══════════════════════════════════════════════════
    // Shared cache infrastructure
    // ══════════════════════════════════════════════════

    protected data class CacheEntry(
        val body: String,
        val etag: String? = null,
        val lastModified: String? = null,
    )

    private val maxCacheSize = 15
    protected val cache: MutableMap<String, CacheEntry> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, CacheEntry>(maxCacheSize + 1, 0.75f, false) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > maxCacheSize
            },
        )

    // ══════════════════════════════════════════════════
    // HTTP fetching
    // ══════════════════════════════════════════════════

    /**
     * Fetch URL with retry + exponential backoff.
     * Retries up to [MAX_RETRIES] times on IOException/ScraperException.
     */
    protected suspend fun fetchDocumentWithRetry(url: String): Document {
        var lastException: Exception? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return fetchDocument(url)
            } catch (e: ScraperException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    val backoff = INITIAL_BACKOFF_MS * (1 shl attempt)
                    delay(backoff)
                }
            } catch (e: IOException) {
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    val backoff = INITIAL_BACKOFF_MS * (1 shl attempt)
                    delay(backoff)
                }
            }
        }
        throw (lastException as? ScraperException)
            ?: lastException?.toScraperException(url)
            ?: ScraperException.NetworkException("Không thể kết nối sau $MAX_RETRIES lần thử")
    }

    /**
     * Fetch a single URL and parse as Jsoup Document.
     * Supports 2 modes:
     * - [useEtagCaching]=true: ETag/Last-Modified conditional requests (TC, TTV)
     * - [useEtagCaching]=false: Simple in-memory cache, check before request (SST, TF)
     */
    protected fun fetchDocument(url: String): Document {
        val absUrl = url.ensureAbsolute()

        if (!useEtagCaching) {
            // Simple mode: return cached body immediately if available
            cache[absUrl]?.let { return Jsoup.parse(it.body, absUrl) }
        }

        val requestBuilder =
            Request
                .Builder()
                .url(absUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "vi-VN,vi;q=0.9,en;q=0.8")

        // Hook for subclass to add extra headers (e.g., TTV adds Referer, Connection)
        buildExtraHeaders(requestBuilder)

        if (useEtagCaching) {
            // ETag mode: send conditional headers
            val existing = cache[absUrl]
            existing?.etag?.let { requestBuilder.header("If-None-Match", it) }
            existing?.lastModified?.let { requestBuilder.header("If-Modified-Since", it) }
        }

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (useEtagCaching) {
                val existing = cache[absUrl]
                val newEtag = response.header("ETag") ?: existing?.etag
                val newLastMod = response.header("Last-Modified") ?: existing?.lastModified

                if (response.code == 304) {
                    val cachedBody =
                        existing?.body
                            ?: throw ScraperException.CacheException("304 nhưng không có cache cho $absUrl")
                    cache[absUrl] = CacheEntry(cachedBody, newEtag, newLastMod)
                    return@use Jsoup.parse(cachedBody, absUrl)
                }

                if (!response.isSuccessful) {
                    throw ScraperException.HttpException(response.code, absUrl)
                }
                val body =
                    response.body?.string()
                        ?: throw ScraperException.ParseException("Phản hồi rỗng từ $absUrl", absUrl)
                cache[absUrl] = CacheEntry(body, newEtag, newLastMod)
                Jsoup.parse(body, absUrl)
            } else {
                // Simple mode
                if (!response.isSuccessful) {
                    throw ScraperException.HttpException(response.code, absUrl)
                }
                val body =
                    response.body?.string()
                        ?: throw ScraperException.ParseException("Phản hồi rỗng từ $absUrl", absUrl)
                cache[absUrl] = CacheEntry(body)
                Jsoup.parse(body, absUrl)
            }
        }
    }

    /**
     * Hook for subclass to add extra HTTP headers.
     * Override this to add Referer, Connection, etc.
     */
    protected open fun buildExtraHeaders(builder: Request.Builder) {
        // Default: no extra headers
    }

    // ══════════════════════════════════════════════════
    // HTML cleanup
    // ══════════════════════════════════════════════════

    /**
     * Strip HTML tags and normalize newlines.
     * Used by getStoryDetail() description and extractChapterText().
     */
    protected fun cleanHtml(html: String): String =
        html
            .replace(CLEAN_BR_REGEX, "\n")
            .replace(CLEAN_P_OPEN_REGEX, "\n")
            .replace("</p>", "\n")
            .replace(CLEAN_TAG_REGEX, "")
            .replace(CLEAN_NEWLINES_REGEX, "\n\n")
            .trim()

    /**
     * Make a relative URL absolute using [baseUrl].
     */
    protected fun String.ensureAbsolute(): String =
        if (this.startsWith("http")) {
            this
        } else {
            "$baseUrl/${this.trimStart('/')}"
        }

    // ══════════════════════════════════════════════════
    // Default implementations (overridable)
    // ══════════════════════════════════════════════════

    /**
     * Extract chapter text from a Jsoup Document.
     * Tries [contentSelectors] in order, removes [adsSelectors], falls back to nav markers.
     */
    protected open fun extractChapterText(doc: Document): String {
        for (selector in contentSelectors) {
            val el = doc.select(selector).first() ?: continue
            el.select(adsSelectors).remove()
            val text = cleanHtml(el.html())
            if (text.length > MIN_CONTENT_LENGTH) return text
        }

        // Last resort: try text between navigation markers
        val bodyText = doc.body()?.text() ?: return ""
        val chapterNav = Regex("Chương (trước|tiếp|sau)")
        val parts = bodyText.split(chapterNav)
        if (parts.size >= 3) {
            return parts[1].trim()
        }

        return ""
    }

    /**
     * Default getChapterList: fetch first page + remaining, dedup by chapter number.
     * TTV overrides this with distinctBy variant.
     */
    override suspend fun getChapterList(storyUrl: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            val result = getFirstPageChapters(storyUrl)
            val allChapters = result.chapters.toMutableList()
            val seen = allChapters.map { it.chapterNumber }.toMutableSet()

            getRemainingChapters(storyUrl, result.totalPages) { batch ->
                synchronized(allChapters) {
                    batch.forEach { ch ->
                        if (seen.add(ch.chapterNumber)) {
                            allChapters.add(ch)
                        }
                    }
                }
            }

            allChapters.sortedBy { it.chapterNumber }
        }

    /**
     * Default getChapterContent: fetch + extract + fallback error message.
     * TC overrides to add WebView fallback. SST/TF override for stricter error handling.
     */
    override suspend fun getChapterContent(chapterUrl: String): String =
        withContext(Dispatchers.IO) {
            try {
                val doc = fetchDocumentWithRetry(chapterUrl)
                val content = extractChapterText(doc)
                if (content.length > MIN_CONTENT_LENGTH) {
                    content
                } else if (content.isNotBlank()) {
                    content
                } else {
                    throw ScraperException.ParseException("Nội dung chương rỗng", chapterUrl)
                }
            } catch (e: ScraperException) {
                throw e
            } catch (e: Exception) {
                Log.d(sourceId, "Failed to load chapter: $chapterUrl", e)
                throw ScraperException.ParseException(
                    "Không thể tải nội dung chương: ${e.message}",
                    chapterUrl,
                )
            }
        }

    // ══════════════════════════════════════════════════
    // Shared data class for chapter parsing
    // ══════════════════════════════════════════════════

    /** Intermediate data class for parsing chapter links before converting to Chapter model. */
    protected data class ChapterInfo(
        val number: Int,
        val title: String,
        val url: String,
    )

    /** Convert ChapterInfo to domain Chapter model. */
    protected fun ChapterInfo.toChapter(storyId: String) =
        Chapter(
            storyId = storyId,
            chapterNumber = number,
            title = title,
            url = url,
        )

    // ══════════════════════════════════════════════════
    // Shared helpers — centralize duplicated patterns
    // ══════════════════════════════════════════════════

    /**
     * Normalize raw status text to standardized Vietnamese status.
     * Centralizes 4 different status normalization approaches across scrapers.
     */
    protected fun normalizeStatus(raw: String): String =
        when {
            raw.contains("Full", ignoreCase = true) -> "Hoàn thành"
            raw.contains("hoàn thành", ignoreCase = true) -> "Hoàn thành"
            raw.contains("Hoàn", ignoreCase = true) -> "Hoàn thành"
            raw.contains("ngừng", ignoreCase = true) -> "Ngừng viết"
            raw.isNotBlank() -> raw
            else -> "Đang ra"
        }

    /**
     * Validate and normalize cover image URL.
     * Returns empty string if URL is null, blank, or not HTTP.
     */
    protected fun validateCoverUrl(url: String?): String = url?.takeIf { it.isNotBlank() && it.startsWith("http") } ?: ""

    // ══════════════════════════════════════════════════
    // Constants
    // ══════════════════════════════════════════════════

    companion object {
        const val DEFAULT_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        const val MAX_RETRIES = 3
        const val INITIAL_BACKOFF_MS = 1000L
        const val MIN_CONTENT_LENGTH = 50

        val DEFAULT_CONTENT_SELECTORS =
            listOf(
                "#chapter-c",
                ".chapter-c",
                "#chapter-content",
                ".chapter-content",
                "#content",
                ".content",
                "div.text-justify",
                ".truyen-content",
            )

        // Cached regex patterns — avoid recompilation per call
        val CLEAN_BR_REGEX = Regex("<br\\s*/?>")
        val CLEAN_P_OPEN_REGEX = Regex("<p[^>]*>")
        val CLEAN_TAG_REGEX = Regex("<[^>]+>")
        val CLEAN_NEWLINES_REGEX = Regex("\n{3,}")
    }
}
