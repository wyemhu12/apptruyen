package com.personal.apptruyen.data.remote

import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.ChapterContentResult
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Web scraper for tangthuvien.org (New React/Next.js site).
 */
@Singleton
class TangThuVienScraper
    @Inject
    constructor(
        client: OkHttpClient,
    ) : BaseScraper(client) {

        override val sourceId = "tangthuvien"
        override val sourceName = "Tàng Thư Viện"

        @androidx.annotation.VisibleForTesting
        internal var customBaseUrl: String? = null

        override val baseUrl: String
            get() = customBaseUrl ?: BASE_URL

        // New TTV can use mobile or desktop UA
        override val userAgent = DEFAULT_MOBILE_UA
        override val useEtagCaching = false

        override fun buildExtraHeaders(builder: Request.Builder) {
            builder
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", "$baseUrl/")
        }

        companion object {
            const val BASE_URL = "https://tangthuvien.org"

            // Extracts digit from string like "241 Chương" or "<span>241<!-- --> Chương</span>"
            private val CHAPTER_COUNT_REGEX = Regex("(\\d+)\\s*(?:<!-- -->\\s*)?Chương", RegexOption.IGNORE_CASE)

            // Strips "#8 " or "#123 " prefix from title/paragraph text like "#8 Chương 8: Tên chương"
            private val TITLE_HASH_PREFIX = Regex("^#\\d+\\s+")

            // Danh sách đầy đủ thể loại từ tangthuvien.org/truyen (fallback khi scrape thất bại)
            private val FALLBACK_GENRES =
                listOf(
                    Genre("Cường cường", "cuong-cuong"),
                    Genre("Adult", "adult"),
                    Genre("Bách hợp", "bach-hop"),
                    Genre("Bình Đạm", "binh-dam"),
                    Genre("Cung đấu", "cung-dau"),
                    Genre("Cơ Trí", "co-tri"),
                    Genre("Cạnh Kỹ", "canh-ky"),
                    Genre("Cận Đại", "can-dai"),
                    Genre("Cổ Tiên Hiệp", "co-tien-hiep"),
                    Genre("Cổ đại", "co-dai"),
                    Genre("Dã sử", "da-su"),
                    Genre("Dị giới", "di-gioi"),
                    Genre("Dị năng", "di-nang"),
                    Genre("Gia đấu", "gia-dau"),
                    Genre("Góc Nhìn Nam", "goc-nhin-nam"),
                    Genre("H", "h"),
                    Genre("HE", "he"),
                    Genre("Hiện Đại", "hien-dai"),
                    Genre("Huyền Bí", "huyen-bi"),
                    Genre("Huyền Nghi", "huyen-nghi"),
                    Genre("Huyền huyễn", "huyen-huyen"),
                    Genre("Huyền ảo", "huyen-ao"),
                    Genre("Hài hước", "hai-huoc"),
                    Genre("Hào môn", "hao-mon"),
                    Genre("Hậu Cung - Harem", "hau-cung-harem"),
                    Genre("Hắc Ám Lưu", "hac-am-luu"),
                    Genre("Hệ thống", "he-thong"),
                    Genre("Hồng Hoang", "hong-hoang"),
                    Genre("Hỗ công", "ho-cong"),
                    Genre("Khoa Huyễn Không Gian", "khoa-huyen-khong-gian"),
                    Genre("Khoa huyễn", "khoa-huyen"),
                    Genre("Khác", "khac"),
                    Genre("Kinh dị", "kinh-di"),
                    Genre("Kiếm hiệp", "kiem-hiep"),
                    Genre("Kỳ Huyễn", "ky-huyen"),
                    Genre("Kỳ Ảo", "ky-ao"),
                    Genre("Light Novel", "light-novel"),
                    Genre("Linh dị", "linh-di"),
                    Genre("Lịch Sử", "lich-su"),
                    Genre("Ma Pháp", "ma-phap"),
                    Genre("Mạt Thế", "mat-the"),
                    Genre("NP", "np"),
                    Genre("Ngôn tình", "ngon-tinh"),
                    Genre("Ngược", "nguoc"),
                    Genre("Nhiệt Huyết", "nhiet-huyet"),
                    Genre("Nhân thú", "nhan-thu"),
                    Genre("Nhẹ Nhàng", "nhe-nhang"),
                    Genre("Non-CP", "non-cp"),
                    Genre("Nữ Phụ", "nu-phu"),
                    Genre("Nữ tôn", "nu-ton"),
                    Genre("OE", "oe"),
                    Genre("Phong Thủy - Tâm Linh", "phong-thuy-tam-linh"),
                    Genre("Phàm Nhân Lưu", "pham-nhan-luu"),
                    Genre("Phúc hắc", "phuc-hac"),
                    Genre("Phương Tây", "phuong-tay"),
                    Genre("Phản Phái", "phan-phai"),
                    Genre("Quan Trường", "quan-truong"),
                    Genre("Quân sự", "quan-su"),
                    Genre("SE", "se"),
                    Genre("Sắc", "sac"),
                    Genre("Sắc Hiệp", "sac-hiep"),
                    Genre("Sủng", "sung"),
                    Genre("Thanh thủy văn", "thanh-thuy-van"),
                    Genre("Thiết Huyết", "thiet-huyet"),
                    Genre("Thám Hiểm", "tham-hiem"),
                    Genre("Tiên hiệp", "tien-hiep"),
                    Genre("Tiểu thuyết", "tieu-thuyet"),
                    Genre("Trinh thám", "trinh-tham"),
                    Genre("Truy thê", "truy-the"),
                    Genre("Truyện Teen", "truyen-teen"),
                    Genre("Trùng Sinh", "trung-sinh"),
                    Genre("Trọng sinh", "trong-sinh"),
                    Genre("Tu Chân", "tu-chan"),
                    Genre("Tu tiên", "tu-tien"),
                    Genre("Tâm Lý", "tam-ly"),
                    Genre("Tây Phương Kỳ Huyễn", "tay-phuong-ky-huyen"),
                    Genre("Vô Sỉ", "vo-si"),
                    Genre("Vô Địch", "vo-dich"),
                    Genre("Võ Hiệp", "vo-hiep"),
                    Genre("Võng du", "vong-du"),
                    Genre("Vườn trường", "vuon-truong"),
                    Genre("Xuyên Nhanh", "xuyen-nhanh"),
                    Genre("Xuyên Thư", "xuyen-thu"),
                    Genre("Xuyên không", "xuyen-khong"),
                    Genre("Xây Dựng Thế Lực", "xay-dung-the-luc"),
                    Genre("Đam mỹ", "dam-my"),
                    Genre("Điền văn", "dien-van"),
                    Genre("Đoản văn", "doan-van"),
                    Genre("Đô thị", "do-thi"),
                    Genre("Đông Phương", "dong-phuong"),
                    Genre("Đông Phương Huyền Huyễn", "dong-phuong-huyen-huyen"),
                    Genre("Đồng nhân", "dong-nhan"),
                    Genre("nữ cường", "nu-cuong"),
                )
        }

        // ---- Search ----

        override suspend fun search(
            keyword: String,
            completedOnly: Boolean,
        ): List<Story> =
            withContext(Dispatchers.IO) {
                val encoded = URLEncoder.encode(keyword, "UTF-8")
                val statusParam = if (completedOnly) "&status=COMPLETED" else ""
                val url = "$baseUrl/truyen?keyword=$encoded$statusParam"
                val doc = fetchDocumentWithRetry(url)
                enrichWithChapterCounts(parseStoryList(doc))
            }

        // ---- Story Detail ----

        override suspend fun getStoryDetail(storyUrl: String): Story =
            withContext(Dispatchers.IO) {
                // Migrate old URLs to new URLs automatically
                val urlToFetch = migrateUrl(storyUrl)
                val doc = fetchDocumentWithRetry(urlToFetch)

                val title =
                    doc
                        .select("h1, h2")
                        .firstOrNull { it.text().isNotBlank() }
                        ?.text()
                        ?.trim() ?: "Đang cập nhật"

                // Author is usually a link like /tac-gia/name
                val author =
                    doc
                        .select("a[href^=/tac-gia/]")
                        .firstOrNull()
                        ?.text()
                        ?.trim() ?: ""

                // Genres are links like /truyen?genre=...
                val genres = doc.select("a[href^=/truyen?genre=]").map { it.text().trim() }.filter { it.isNotBlank() }

                // Description (Tóm tắt)
                // Look for text in <p> tags that are children of a div with leading-relaxed
                val descriptionEls = doc.select("div.leading-relaxed p")
                val description =
                    if (descriptionEls.isNotEmpty()) {
                        descriptionEls.joinToString("\n") { it.text().trim() }
                    } else {
                        doc.select(".text-muted-foreground").text().trim()
                    }

                // Info cover
                val coverImageUrl =
                    doc
                        .select("img")
                        .firstOrNull {
                            val src = it.attr("src")
                            src.contains("cover") || src.contains("/_next/image")
                        }?.let {
                            val src = it.attr("abs:src").ifEmpty { it.attr("src") }
                            // NextJS image optimization URL parsing
                            val decoded =
                                if (src.contains("/_next/image?url=")) {
                                    java.net.URLDecoder.decode(
                                        src.substringAfter("/_next/image?url=").substringBefore("&"),
                                        "UTF-8",
                                    )
                                } else {
                                    src
                                }
                            val absolute = if (decoded.startsWith("/")) "$baseUrl$decoded" else decoded
                            validateCoverUrl(absolute)
                        } ?: ""

                // Extract total chapters from text like "241 Chương"
                val htmlSource = doc.html()
                val totalChapters =
                    CHAPTER_COUNT_REGEX
                        .find(htmlSource)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0

                // Status
                val status =
                    if (htmlSource.contains("Hoàn thành", ignoreCase = true) || htmlSource.contains("COMPLETED", ignoreCase = true)) {
                        "Hoàn thành"
                    } else {
                        "Đang cập nhật"
                    }

                val slug = extractSlug(urlToFetch)

                Story(
                    id = "${sourceId}_$slug",
                    slug = slug,
                    title = title,
                    author = author,
                    genres = genres,
                    description = description,
                    url = urlToFetch,
                    totalChapters = totalChapters,
                    coverImageUrl = coverImageUrl,
                    sourceId = sourceId,
                    status = status,
                )
            }

        // ---- Chapter List ----

        override suspend fun getFirstPageChapters(storyUrl: String): StorySource.FirstPageResult =
            withContext(Dispatchers.IO) {
                val story = getStoryDetail(storyUrl)
                val total = if (story.totalChapters > 0) story.totalChapters else 1
                val chapters = generateChapters(story.slug, total)
                StorySource.FirstPageResult(chapters = chapters, totalPages = 1)
            }

        override suspend fun getRemainingChapters(
            storyUrl: String,
            totalPages: Int,
            onBatchReady: suspend (List<Chapter>) -> Unit,
        ) {
            // All chapters are generated in first page
        }

        override suspend fun getChapterList(storyUrl: String): List<Chapter> =
            withContext(Dispatchers.IO) {
                getFirstPageChapters(storyUrl).chapters
            }

        private fun generateChapters(
            slug: String,
            total: Int,
        ): List<Chapter> {
            val chapters = mutableListOf<Chapter>()
            val canonicalStoryId = "${sourceId}_$slug"
            for (i in 1..total) {
                chapters.add(
                    Chapter(
                        storyId = canonicalStoryId,
                        chapterNumber = i,
                        title = "Chương $i", // Dummy title until fetched
                        url = "$baseUrl/$slug/$i",
                    ),
                )
            }
            return chapters
        }

        // ---- Chapter Content ----

        override suspend fun getChapterContent(chapterUrl: String): String = getChapterContentWithTitle(chapterUrl).content

        override suspend fun getChapterContentWithTitle(chapterUrl: String): ChapterContentResult =
            withContext(Dispatchers.IO) {
                try {
                    val urlToFetch = migrateUrl(chapterUrl)
                    val doc = fetchDocumentWithRetry(urlToFetch)

                    // Find actual chapter title — usually "#X Chương Y: Zzz"
                    val titleEl =
                        doc
                            .select("h1, h2, div.font-semibold, div.font-bold")
                            .firstOrNull { it.text().contains("Chương", ignoreCase = true) }

                    // Extract and clean title: strip "#8 " prefix
                    val extractedTitle =
                        titleEl
                            ?.text()
                            ?.trim()
                            ?.let { raw ->
                                TITLE_HASH_PREFIX.replace(raw, "").trim()
                            }?.ifBlank { null }

                    // The text content is inside multiple <p> tags.
                    val paragraphs = doc.select("p")
                    if (paragraphs.isEmpty()) {
                        throw ScraperException.ParseException("Không tìm thấy nội dung chương", urlToFetch)
                    }

                    // Build content from paragraphs — do NOT prepend title
                    val sb = StringBuilder()
                    for (p in paragraphs) {
                        var txt = p.text().trim()
                        if (txt.isNotBlank() && txt.length > 5) {
                            // Strip "#8 " prefix from paragraphs like "#8 Chương 8: Tên chương"
                            txt = TITLE_HASH_PREFIX.replace(txt, "")

                            // Merge lines starting with dash or lowercase letter into the previous paragraph
                            if (sb.isNotEmpty() && (txt.startsWith("-") || txt.startsWith("–") || txt.first().isLowerCase())) {
                                if (sb.endsWith("\n\n")) {
                                    sb.setLength(sb.length - 2)
                                    sb.append(" ")
                                }
                            }
                            // Basic heuristic to avoid UI buttons or nav text
                            sb.append(txt).append("\n\n")
                        }
                    }

                    val text = sb.toString().trim()
                    if (text.length > MIN_CONTENT_LENGTH) {
                        return@withContext ChapterContentResult(
                            content = text,
                            title = extractedTitle,
                        )
                    }

                    throw ScraperException.ParseException("Nội dung chương quá ngắn hoặc rỗng", urlToFetch)
                } catch (e: ScraperException) {
                    throw e
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.tag("TTVScraper").d(e, "Failed to load chapter: %s", chapterUrl)
                    throw ScraperException.ParseException("Không thể tải nội dung chương", chapterUrl, e)
                }
            }

        // ---- Browse: Completed Stories ----

        override suspend fun getCompletedStories(page: Int): List<Story> =
            withContext(Dispatchers.IO) {
                val url = "$baseUrl/truyen?status=COMPLETED&sort=createdAt&order=desc&page=$page"
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
                val statusParam = if (completedOnly) "&status=COMPLETED" else ""
                val url = "$baseUrl/truyen?genre=$genreSlug&page=$page$statusParam"
                val doc = fetchDocumentWithRetry(url)
                val stories = enrichWithChapterCounts(parseStoryList(doc))

                if (minChapters > 0) {
                    stories.filter { it.totalChapters >= minChapters }
                } else {
                    stories
                }
            }

        // ---- Genre List ----

        override suspend fun getGenreList(): List<Genre> =
            withContext(Dispatchers.IO) {
                // Fetch từ trang /truyen vì trang chủ render client-side (Next.js),
                // Jsoup không thấy genre links. Trang /truyen có SSR sẵn danh sách thể loại.
                val doc = fetchDocumentWithRetry("$baseUrl/truyen")
                val genres = mutableListOf<Genre>()

                // Link có dạng ?genre=khoa-huyen hoặc href chứa genre=
                doc.select("a[href*=genre=]").forEach { el ->
                    val href = el.attr("href")
                    val name = el.text().trim()
                    val slug = href.substringAfter("genre=").substringBefore("&")
                    if (name.isNotBlank() && slug.isNotBlank()) {
                        genres.add(Genre(name = name, slug = slug))
                    }
                }

                // Nếu scrape thất bại, fallback danh sách đầy đủ từ TTV
                if (genres.isEmpty()) {
                    return@withContext FALLBACK_GENRES
                }

                genres.distinctBy { it.slug }
            }

        // ---- Private helpers ----

        private fun parseStoryList(doc: org.jsoup.nodes.Document): List<Story> {
            val results = mutableListOf<Story>()

            // On TangThuVien.org, links to stories are wrapped in a flex container
            doc.select("div.flex.items-start.gap-2").forEach { container ->
                val a = container.select("a").firstOrNull() ?: return@forEach
                val href = a.attr("href")
                if (href.contains("/tac-gia/")) return@forEach

                // Because story links might be wrapped around images or text separately, we use a heuristic
                val img = container.select("img").firstOrNull()
                val titleEl =
                    container.select("h1, h2, h3, .font-semibold, .font-bold").firstOrNull {
                        it.text().isNotBlank() && !it.text().equals("Truyện", ignoreCase = true)
                    }

                if (img != null && titleEl != null) {
                    val title = titleEl.text().trim()
                    if (title.isBlank()) return@forEach

                    val slug = extractSlug(href)
                    val rawCover = img.attr("abs:src").ifEmpty { img.attr("src") }
                    val decodedCover =
                        if (rawCover.contains("/_next/image?url=")) {
                            java.net.URLDecoder.decode(
                                rawCover.substringAfter("/_next/image?url=").substringBefore("&"),
                                "UTF-8",
                            )
                        } else {
                            rawCover
                        }
                    val coverUrl = validateCoverUrl(if (decodedCover.startsWith("/")) "$baseUrl$decodedCover" else decodedCover)

                    // Parse chapter count if available in container
                    val textContent = container.text()
                    val totalChapters =
                        CHAPTER_COUNT_REGEX
                            .find(textContent)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull() ?: 0
                    val status = if (textContent.contains("Hoàn thành", ignoreCase = true)) "Hoàn thành" else "Đang cập nhật"

                    // Attempt to extract author
                    val authorEl = container.select(".text-muted-foreground.truncate, .truncate").firstOrNull()
                    val author = authorEl?.text()?.trim() ?: ""

                    results.add(
                        Story(
                            id = "${sourceId}_$slug",
                            slug = slug,
                            title = title,
                            author = author, // Extracted from container
                            genres = emptyList(),
                            description = "",
                            url = "$baseUrl$href",
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

        /**
         * Enrich stories thiếu totalChapters bằng cách fetch detail page song song.
         * TTV list page không cung cấp số chương → phải fetch từ detail page.
         * Giới hạn: tối đa 10 stories, timeout 7s tổng, batch 5 concurrent.
         */
        private suspend fun enrichWithChapterCounts(stories: List<Story>): List<Story> {
            val needEnrich = stories.filter { it.totalChapters <= 0 }
            if (needEnrich.isEmpty()) return stories

            // Wrap in timeout to avoid exceeding StoryRepository's 10s per-source timeout
            val chapterCounts = mutableMapOf<String, Int>()
            try {
                kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    needEnrich.chunked(5).forEach { batch ->
                        coroutineScope {
                            batch
                                .map { story ->
                                    async(Dispatchers.IO) {
                                        try {
                                            val detailUrl = migrateUrl(story.url)
                                            val doc = fetchDocumentWithRetry(detailUrl)
                                            val count =
                                                CHAPTER_COUNT_REGEX
                                                    .find(doc.html())
                                                    ?.groupValues
                                                    ?.get(1)
                                                    ?.toIntOrNull() ?: 0
                                            story.id to count
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            Timber.tag("TTVScraper").d("Enrich failed for %s: %s", story.id, e.message)
                                            story.id to 0
                                        }
                                    }
                                }.awaitAll()
                                .forEach { (id, count) ->
                                    if (count > 0) chapterCounts[id] = count
                                }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.tag("TTVScraper").d("Enrich timeout/error: %s", e.message)
            }

            return stories.map { story ->
                val enrichedCount = chapterCounts[story.id]
                if (enrichedCount != null && enrichedCount > 0) {
                    story.copy(totalChapters = enrichedCount)
                } else {
                    story
                }
            }
        }

        private fun extractSlug(url: String): String {
            // URL có thể là https://tangthuvien.org/slug hoặc /slug
            // Hoặc url cũ: https://truyen.tangthuvien.vn/doc-truyen/slug
            val normalizedBase = baseUrl.trimEnd('/')
            val path =
                url
                    .trimEnd('/')
                    .removePrefix("$normalizedBase/")
                    .substringAfter("/doc-truyen/")
                    .substringAfter("tangthuvien.org/")
                    .removePrefix("/")
            return path.substringBefore("/").substringBefore("?").ifBlank { path.replace("/", "") }
        }

        private fun migrateUrl(oldUrl: String): String {
            if (oldUrl.contains("truyen.tangthuvien.vn/doc-truyen/")) {
                // Example old: https://truyen.tangthuvien.vn/doc-truyen/tien-gia
                // Example old chapter: https://truyen.tangthuvien.vn/doc-truyen/tien-gia/chuong-1
                val parts = oldUrl.substringAfter("/doc-truyen/").split("/")
                val slug = parts.firstOrNull() ?: return oldUrl
                val chapterPart = parts.getOrNull(1)

                return if (chapterPart != null && chapterPart.startsWith("chuong-")) {
                    val chapterNum = chapterPart.replace("chuong-", "")
                    "$baseUrl/$slug/$chapterNum"
                } else {
                    "$baseUrl/$slug"
                }
            }
            if (oldUrl.startsWith("/")) {
                return "$baseUrl$oldUrl"
            }
            return oldUrl
        }
    }
