package com.personal.apptruyen.data.remote

import com.personal.apptruyen.data.remote.cookie.CookieStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.InputStreamReader

class WikidichScraperTest {

    private lateinit var server: MockWebServer
    private lateinit var scraper: WikidichScraper
    private lateinit var cookieStore: CookieStore
    private val client = OkHttpClient()

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()

        val mockBaseUrl = server.url("/").toString().removeSuffix("/")
        scraper =
            WikidichScraper(client).apply {
                customBaseUrl = mockBaseUrl
            }
    }

    @AfterEach
    fun teardown() {
        server.close()
    }

    private fun loadFixture(path: String): String {
        val stream =
            javaClass.classLoader?.getResourceAsStream("fixtures/wikidich/$path")
                ?: throw IllegalArgumentException("Fixture not found: $path")
        return InputStreamReader(stream, Charsets.UTF_8).readText()
    }

    @Test
    fun `search happyPath returns stories with wikidich sourceId`() =
        runTest {
            val html = loadFixture("search_logged_in.html")
            server.enqueue(MockResponse(code = 200, body = html))

            val stories = scraper.search("tien", false)
            assertTrue(stories.isNotEmpty())
            val first = stories.first()
            assertEquals("wikidich", first.sourceId)
            assertTrue(first.title.isNotBlank())
            assertTrue(first.url.startsWith("http"))
        }

    @Test
    fun `search when not logged in throws LoginRequiredException`() =
        runTest {
            val html = loadFixture("search_not_logged_in.html")
            server.enqueue(MockResponse(code = 200, body = html))

            var thrown = false
            try {
                scraper.search("tien", false)
            } catch (e: ScraperException.LoginRequiredException) {
                thrown = true
                assertEquals("wikidich", e.sourceId)
            }
            assertTrue(thrown, "Expected LoginRequiredException")
        }

    @Test
    fun `getStoryDetail parses title author and status correctly`() =
        runTest {
            val html = loadFixture("story_detail.html")
            server.enqueue(MockResponse(code = 200, body = html))

            val story = scraper.getStoryDetail(server.url("/truyen/sample").toString())
            assertEquals("wikidich", story.sourceId)
            assertTrue(story.title.isNotBlank())
            assertNotNull(story.author)
            assertTrue(story.description.isNotBlank())
        }

    @Test
    fun `getFirstPageChapters extracts chapters from AJAX fragment`() =
        runTest {
            val storyHtml = loadFixture("story_detail.html")
            val ajaxHtml = loadFixture("chapter_index_ajax.html")

            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        if (request.url.encodedPath.contains("/book/index")) {
                            MockResponse(code = 200, body = ajaxHtml)
                        } else {
                            MockResponse(code = 200, body = storyHtml)
                        }
                }

            val result = scraper.getFirstPageChapters(server.url("/truyen/sample").toString())
            assertTrue(result.chapters.isNotEmpty())
            assertEquals(99, result.chapters.size)
            val first = result.chapters.first()
            assertEquals(1, first.chapterNumber)
            assertTrue(first.title.contains("Chương 1"))
        }

    @Test
    fun `getFirstPageChapters throws ParseException when fragment is empty`() =
        runTest {
            val storyHtml = loadFixture("story_detail.html")
            val emptyAjax = "<div class='row'><ul class='pagination'></ul></div>"

            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        if (request.url.encodedPath.contains("/book/index")) {
                            MockResponse(code = 200, body = emptyAjax)
                        } else {
                            MockResponse(code = 200, body = storyHtml)
                        }
                }

            var thrown = false
            try {
                scraper.getFirstPageChapters(server.url("/truyen/sample").toString())
            } catch (e: ScraperException.ParseException) {
                thrown = true
            }
            assertTrue(thrown, "Expected ParseException")
        }

    @Test
    fun `getRemainingChapters stops when no next page link in pagination`() =
        runTest {
            val storyHtml = loadFixture("story_detail.html")
            val p2Html = loadFixture("chapter_index_ajax_p2.html")

            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        if (request.url.encodedPath.contains("/book/index")) {
                            val lastPageHtml = p2Html.replace("data-start=\"198\"", "data-start=\"none\"")
                            MockResponse(code = 200, body = lastPageHtml)
                        } else {
                            MockResponse(code = 200, body = storyHtml)
                        }
                }

            val collected = mutableListOf<com.personal.apptruyen.data.model.Chapter>()
            scraper.getRemainingChapters(server.url("/truyen/sample").toString(), 2) { batch ->
                collected.addAll(batch)
            }
            assertTrue(collected.isNotEmpty())
            assertEquals(99, collected.size)
            assertEquals(100, collected.first().chapterNumber)
        }

    @Test
    fun `getChapterContent filters watermark`() =
        runTest {
            val html = loadFixture("chapter_content.html")
            server.enqueue(MockResponse(code = 200, body = html))

            val content = scraper.getChapterContent(server.url("/truyen/sample/chuong-1").toString())
            assertTrue(content.isNotBlank())
            assertFalse(content.contains("【❁"))
            assertFalse(content.contains("(Wikidich)"))
        }

    @Test
    fun `getCompletedStories returns completed stories`() =
        runTest {
            val html = loadFixture("search_logged_in.html")
            server.enqueue(MockResponse(code = 200, body = html))

            val stories = scraper.getCompletedStories(1, maleOnly = true)
            assertTrue(stories.isNotEmpty())
            assertEquals("wikidich", stories.first().sourceId)
        }

    @Test
    fun `getStoriesByGenre with unknown genre returns empty list without network call`() =
        runTest {
            val stories = scraper.getStoriesByGenre("genre-khong-ton-tai", 1, 0, false, maleOnly = true)
            assertTrue(stories.isEmpty())
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `fuzzySign with invalid cut throws ParseException`() {
        assertThrows(ScraperException.ParseException::class.java) {
            scraper.fuzzySign("test", 10)
        }
        assertThrows(ScraperException.ParseException::class.java) {
            scraper.fuzzySign("test", -1)
        }
    }

    @Test
    fun `getStoriesByGenre uses filter form qs=2 params with newest sort and male filter`() =
        runTest {
            val listHtml = loadFixture("search_logged_in.html")
            val detailHtml =
                """
                <html>
                <body>
                    <div class="cover-info">
                        <h2>Truyện Mẫu</h2>
                        <p>Mới nhất: <a href="/truyen/sample/chuong-245-ket-thuc">Chương 245: Kết thúc</a></p>
                    </div>
                </body>
                </html>
                """.trimIndent()

            val requestedPaths = mutableListOf<String>()
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        requestedPaths.add(request.url.toString())
                        return if (request.url.encodedPath.contains("/tim-kiem") || request.url.encodedPath.contains("/bang-xep-hang")) {
                            MockResponse(code = 200, body = listHtml)
                        } else {
                            MockResponse(code = 200, body = detailHtml)
                        }
                    }
                }

            val page1Stories = scraper.getStoriesByGenre("lam-su-nghiep", 1, 0, completedOnly = true, maleOnly = true)
            assertTrue(page1Stories.isNotEmpty())
            assertTrue(requestedPaths.first().contains("qs=2"))
            assertTrue(requestedPaths.first().contains("gender=5794f03dd7ced228f4419196"))
            assertTrue(requestedPaths.first().contains("status=5794f03dd7ced228f4419191"))
            assertTrue(requestedPaths.first().contains("genre=6785c24e23deea36d63efe8e"))
            assertTrue(requestedPaths.first().contains("so=4&vo=1"))
            assertFalse(requestedPaths.first().contains("qs=1"))
            assertFalse(requestedPaths.first().contains("tc=&tf=0"))
            assertEquals("Hoàn thành", page1Stories.first().status)

            requestedPaths.clear()
            scraper.getStoriesByGenre("quan-su", 2, 0, completedOnly = true, maleOnly = true)
            assertTrue(requestedPaths.first().contains("qs=2"))
            assertTrue(requestedPaths.first().contains("gender=5794f03dd7ced228f4419196"))
            assertTrue(requestedPaths.first().contains("genre=6785b91d23deea36d63efe7f"))
            assertTrue(requestedPaths.first().contains("so=4&vo=1"))
            assertTrue(requestedPaths.first().contains("start=20"))

            requestedPaths.clear()
            scraper.enrichInTests = true
            val searchStories = scraper.search("hogwarts", false)
            assertTrue(searchStories.isNotEmpty())
            assertTrue(requestedPaths.first().contains("/tim-kiem?q=hogwarts&qs=1"))
            assertEquals(245, searchStories.first().totalChapters)
        }

    @Test
    fun `getStoriesByGenre without male filter omits gender param`() =
        runTest {
            val html = loadFixture("search_logged_in.html")
            server.enqueue(MockResponse(code = 200, body = html))

            val stories = scraper.getStoriesByGenre("xay-dung", 1, 0, completedOnly = true, maleOnly = false)
            assertTrue(stories.isNotEmpty())

            val recorded = server.takeRequest()
            val urlStr = recorded.url.toString()
            assertTrue(urlStr.contains("/tim-kiem?qs=2"))
            assertTrue(urlStr.contains("genre=5fd3abb623deea0a1a87e9b5"))
            assertTrue(urlStr.contains("status=5794f03dd7ced228f4419191"))
            assertTrue(urlStr.contains("so=4&vo=1"))
            assertFalse(urlStr.contains("gender="))
        }

    @Test
    fun `getCompletedStories with maleOnly calls tim kiem with newest sort`() =
        runTest {
            val html = loadFixture("search_logged_in.html")
            server.enqueue(MockResponse(code = 200, body = html))

            val stories = scraper.getCompletedStories(page = 1, maleOnly = true)
            assertTrue(stories.isNotEmpty())
            assertEquals("Hoàn thành", stories.first().status)

            val recorded = server.takeRequest()
            val urlStr = recorded.url.toString()
            assertTrue(urlStr.contains("/tim-kiem?qs=2"))
            assertTrue(urlStr.contains("gender=5794f03dd7ced228f4419196"))
            assertTrue(urlStr.contains("status=5794f03dd7ced228f4419191"))
            assertTrue(urlStr.contains("so=4&vo=1"))
        }

    @Test
    fun `getCompletedStories without maleOnly calls bang xep hang fast path`() =
        runTest {
            val html = loadFixture("search_logged_in.html")
            server.enqueue(MockResponse(code = 200, body = html))

            val stories = scraper.getCompletedStories(page = 2, maleOnly = false)
            assertTrue(stories.isNotEmpty())
            assertEquals("Hoàn thành", stories.first().status)
            assertEquals(1, server.requestCount)

            val recorded = server.takeRequest()
            val urlStr = recorded.url.toString()
            assertTrue(urlStr.contains("/bang-xep-hang?so=5&start=20"))
            assertFalse(urlStr.contains("gender="))
        }

    @Test
    fun `getCompletedStories fallback to tim kiem when bang xep hang is empty`() =
        runTest {
            val emptyHtml = "<html><body><div class='book-list'></div></body></html>"
            val validHtml = loadFixture("search_logged_in.html")
            server.enqueue(MockResponse(code = 200, body = emptyHtml))
            server.enqueue(MockResponse(code = 200, body = validHtml))

            val stories = scraper.getCompletedStories(page = 1, maleOnly = false)
            assertTrue(stories.isNotEmpty())
            assertEquals(2, server.requestCount)

            val firstRequest = server.takeRequest().url.toString()
            val secondRequest = server.takeRequest().url.toString()
            assertTrue(firstRequest.contains("/bang-xep-hang?so=5"))
            assertTrue(secondRequest.contains("/tim-kiem?qs=2&status=5794f03dd7ced228f4419191&so=4&vo=1"))
            assertFalse(secondRequest.contains("gender="))
        }

    @Test
    fun `getGenreList does not contain removed gender tags`() =
        runTest {
            val genres = scraper.getGenreList()
            val removedSlugs = setOf("truyen-nam", "ngon-tinh", "dam-my", "bach-hop")
            assertEquals(19, genres.size)
            assertTrue(genres.none { it.slug in removedSlugs })
            assertTrue(WikidichScraper.GENRE_QUERY_MAP.keys.none { it in removedSlugs })
        }

    @Test
    fun `getRemainingChapters delays crawlDelayMs between AJAX pagination pages`() =
        runTest {
            val storyHtml = loadFixture("story_detail.html")
            val p2Html = loadFixture("chapter_index_ajax_p2.html")

            var ajaxCalls = 0
            val callTimestamps = mutableListOf<Long>()
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        if (request.url.encodedPath.contains("/book/index")) {
                            ajaxCalls++
                            callTimestamps.add(System.currentTimeMillis())
                            if (ajaxCalls == 1) {
                                // Page 2 has link to start=198 (Page 3)
                                MockResponse(code = 200, body = p2Html)
                            } else {
                                // Page 3 (start=198) is the last page -> remove link to start=297
                                val lastPageHtml = p2Html.replace("data-start=\"297\"", "data-start=\"none\"")
                                MockResponse(code = 200, body = lastPageHtml)
                            }
                        } else {
                            MockResponse(code = 200, body = storyHtml)
                        }
                }

            val batches = mutableListOf<Int>()
            scraper.getRemainingChapters(server.url("/truyen/sample").toString(), 3) { batch ->
                batches.add(batch.size)
            }

            assertEquals(2, ajaxCalls)
            assertEquals(listOf(99, 99), batches)
            val gapMs = callTimestamps[1] - callTimestamps[0]
            assertTrue(gapMs >= 250L, "Expected delay >= 250ms between AJAX pages, got ${gapMs}ms")
        }

    @Test
    fun `search retries on HTTP 503 and does not retry on HTTP 404`() =
        runTest {
            val html = loadFixture("search_logged_in.html")
            server.enqueue(MockResponse(code = 503, body = "Service Unavailable"))
            server.enqueue(MockResponse(code = 200, body = html))

            val stories = scraper.search("retry_test", false)
            assertTrue(stories.isNotEmpty())
            assertEquals(2, server.requestCount)

            // Now test HTTP 404 (non-retryable)
            server.enqueue(MockResponse(code = 404, body = "Not Found"))
            val ex =
                assertThrows(ScraperException.HttpException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        scraper.search("not_found_test", false)
                    }
                }
            assertEquals(404, ex.statusCode)
            assertEquals(3, server.requestCount)
        }

    @Test
    fun `search upgrades http story url and coverImageUrl to https while preserving localhost`() =
        runTest {
            val html =
                """
                <html><body>
                  <div class="book-item">
                    <div class="cover-col"><img src="http://cdn.truyenwikidich.net/covers/a.jpg"/></div>
                    <div class="info-col">
                      <a class="tooltipped" href="http://truyenwikidich.net/truyen/tien-nghich-123">Tiên Nghịch</a>
                    </div>
                  </div>
                  <div class="book-item">
                    <div class="cover-col"><img src="http://localhost:8080/covers/b.jpg"/></div>
                    <div class="info-col">
                      <a class="tooltipped" href="/truyen/pham-nhan-456">Phàm Nhân</a>
                    </div>
                  </div>
                </body></html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))
            val stories = scraper.search("tien", completedOnly = false)
            assertEquals(2, stories.size)
            assertEquals("https://truyenwikidich.net/truyen/tien-nghich-123", stories[0].url)
            assertEquals("https://cdn.truyenwikidich.net/covers/a.jpg", stories[0].coverImageUrl)
            assertEquals("http://localhost:8080/covers/b.jpg", stories[1].coverImageUrl)
        }

    @Test
    fun `extractTotalChaptersFromDetail parses varied chapter formats in cover-info and ignores links outside cover-info`() {
        val docVolume =
            org.jsoup.Jsoup.parse(
                """
                <html><body>
                  <div class="cover-info">
                    <p>Mới nhất: <a href="/truyen/test/quyen-3-chuong-245-ket">Quyển 3 - Chương 245: Đại kết cục</a></p>
                  </div>
                  <div class="related"><a href="/truyen/other/chuong-9999">Chương 9999</a></div>
                </body></html>
                """.trimIndent(),
            )
        assertEquals(245, scraper.extractTotalChaptersFromDetail(docVolume))

        val docHoi =
            org.jsoup.Jsoup.parse(
                """
                <html><body>
                  <div class="cover-info"><p>Mới nhất: <a href="/truyen/test/hoi-180-abc">Hồi 180: Phong vân</a></p></div>
                </body></html>
                """.trimIndent(),
            )
        assertEquals(180, scraper.extractTotalChaptersFromDetail(docHoi))

        val docTiet =
            org.jsoup.Jsoup.parse(
                """
                <html><body>
                  <div class="cover-info"><p>Mới nhất: <a href="/truyen/test/tiet-95-abc">Tiết 95: Ngoại truyện</a></p></div>
                </body></html>
                """.trimIndent(),
            )
        assertEquals(95, scraper.extractTotalChaptersFromDetail(docTiet))

        val docLeadingNum =
            org.jsoup.Jsoup.parse(
                """
                <html><body>
                  <div class="cover-info"><p>Mới nhất: <a href="/truyen/test/c-abc">312. Chương kết</a></p></div>
                </body></html>
                """.trimIndent(),
            )
        assertEquals(312, scraper.extractTotalChaptersFromDetail(docLeadingNum))

        val docChinese =
            org.jsoup.Jsoup.parse(
                """
                <html><body>
                  <div class="cover-info"><p>Mới nhất: <a href="/truyen/test/c-abc">第520章 结局</a></p></div>
                </body></html>
                """.trimIndent(),
            )
        assertEquals(520, scraper.extractTotalChaptersFromDetail(docChinese))

        val docHrefOnly =
            org.jsoup.Jsoup.parse(
                """
                <html><body>
                  <div class="cover-info"><p>Mới nhất: <a href="/truyen/test/quyen-2-chuong-410-abc">Hoàn bổn cảm ngôn</a></p></div>
                </body></html>
                """.trimIndent(),
            )
        assertEquals(410, scraper.extractTotalChaptersFromDetail(docHrefOnly))

        val docOutsideOnly =
            org.jsoup.Jsoup.parse(
                """
                <html><body>
                  <div class="cover-info"><p>Trạng thái: <a href="/tim-kiem?status=1">Hoàn thành</a></p></div>
                  <div class="same-genre"><a href="/truyen/other-book/chuong-9999-abc">Chương 9999</a></div>
                </body></html>
                """.trimIndent(),
            )
        assertEquals(0, scraper.extractTotalChaptersFromDetail(docOutsideOnly))
    }

    @Test
    fun `getStoryDetail falls back to fetchTotalChaptersFromBookIndex when cover-info has no chapter number`() =
        runTest {
            val detailHtml =
                loadFixture("story_detail.html").replace(
                    Regex("""<p[^>]*>\s*Mới nhất:[\s\S]*?</p>"""),
                    """<p>Mới nhất: <a href="/truyen/sample/phien-ngoai-ket-cuc-Y1">Phiên ngoại: Đại kết cục</a></p>""",
                )
            val page0Html =
                """
                <ul class="pagination">
                  <li class="active"><a href="#!" data-start="0" data-size="99" data-action="loadBookIndex">1</a></li>
                  <li class="waves-effect"><a href="#!" data-start="198" data-size="99" data-action="loadBookIndex">3</a></li>
                </ul>
                <ul>
                  ${(1..99).joinToString("\n") { """<li class="chapter-name"><a href="/truyen/sample/c-$it">Chương $it</a></li>""" }}
                </ul>
                """.trimIndent()
            val pageLastHtml =
                """
                <ul class="pagination">
                  <li class="waves-effect"><a href="#!" data-start="0" data-size="99" data-action="loadBookIndex">1</a></li>
                  <li class="active"><a href="#!" data-start="198" data-size="99" data-action="loadBookIndex">3</a></li>
                </ul>
                <ul>
                  ${(1..15).joinToString("\n") { """<li class="chapter-name"><a href="/truyen/sample/c-${198 + it}">Chương ${198 + it}</a></li>""" }}
                </ul>
                """.trimIndent()

            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.url.encodedPath
                        val query = request.url.query ?: ""
                        return when {
                            path.contains("/book/index") && query.contains("start=0") ->
                                MockResponse(code = 200, body = page0Html)
                            path.contains("/book/index") && query.contains("start=198") ->
                                MockResponse(code = 200, body = pageLastHtml)
                            else ->
                                MockResponse(code = 200, body = detailHtml)
                        }
                    }
                }

            val story = scraper.getStoryDetail(server.url("/truyen/sample").toString())
            assertEquals(213, story.totalChapters)
        }

    @Test
    fun `getStoryDetail and getFirstPageChapters share ajaxFragmentCache for start 0 without duplicate HTTP request`() =
        runTest {
            val detailHtml =
                loadFixture("story_detail.html").replace(
                    Regex("""<p[^>]*>\s*Mới nhất:[\s\S]*?</p>"""),
                    "",
                )
            val ajaxHtml =
                """
                <ul class="pagination">
                  <li class="active"><a href="#!" data-start="0" data-size="99" data-action="loadBookIndex">1</a></li>
                </ul>
                <ul>
                  ${(1..42).joinToString("\n") { """<li class="chapter-name"><a href="/truyen/sample/c-$it">Chương $it</a></li>""" }}
                </ul>
                """.trimIndent()

            var startZeroAjaxCalls = 0
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        if (request.url.encodedPath.contains("/book/index")) {
                            startZeroAjaxCalls++
                            MockResponse(code = 200, body = ajaxHtml)
                        } else {
                            MockResponse(code = 200, body = detailHtml)
                        }
                }

            val storyUrl = server.url("/truyen/sample").toString()
            val detail = scraper.getStoryDetail(storyUrl)
            val firstPage = scraper.getFirstPageChapters(storyUrl)

            assertEquals(42, detail.totalChapters)
            assertEquals(42, firstPage.chapters.size)
            assertEquals(1, startZeroAjaxCalls)
        }

    @Test
    fun `getFirstPageChapters calculates totalPages from highest data-start in pagination`() =
        runTest {
            val storyHtml = loadFixture("story_detail.html")
            val ajaxHtml =
                """
                <ul class="pagination">
                  <li class="active"><a href="#!" data-start="0" data-size="99" data-action="loadBookIndex">1</a></li>
                  <li class="waves-effect"><a href="#!" data-start="396" data-size="99" data-action="loadBookIndex">5</a></li>
                  <li class="waves-effect"><a href="#!" data-start="1980" data-size="99" data-action="loadBookIndex">&gt;&gt;</a></li>
                </ul>
                <ul>
                  <li class="chapter-name"><a href="/truyen/sample/c-1">Chương 1</a></li>
                </ul>
                """.trimIndent()

            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        if (request.url.encodedPath.contains("/book/index")) {
                            MockResponse(code = 200, body = ajaxHtml)
                        } else {
                            MockResponse(code = 200, body = storyHtml)
                        }
                }

            val result = scraper.getFirstPageChapters(server.url("/truyen/sample").toString())
            assertEquals(21, result.totalPages)
        }

    @Test
    fun `search enriches top stories and aborts early on HTTP 429 without failing search result`() =
        runTest {
            scraper.enrichInTests = true
            val searchHtml =
                """
                <html><body>
                  <div class="book-item">
                    <div class="info-col"><a class="tooltipped" href="/truyen/s1">Truyện 1</a></div>
                  </div>
                  <div class="book-item">
                    <div class="info-col"><a class="tooltipped" href="/truyen/s2">Truyện 2</a></div>
                  </div>
                  <div class="book-item">
                    <div class="info-col"><a class="tooltipped" href="/truyen/s3">Truyện 3</a></div>
                  </div>
                </body></html>
                """.trimIndent()
            val detail1Html =
                """
                <html><body>
                  <div class="cover-info">
                    <h2>Truyện 1</h2>
                    <p>Mới nhất: <a href="/truyen/s1/chuong-120">Chương 120: Mới</a></p>
                  </div>
                </body></html>
                """.trimIndent()

            var detail3Called = false
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        val path = request.url.encodedPath
                        return when {
                            path.contains("/tim-kiem") -> MockResponse(code = 200, body = searchHtml)
                            path.endsWith("/truyen/s1") -> MockResponse(code = 200, body = detail1Html)
                            path.endsWith("/truyen/s2") -> MockResponse(code = 429, body = "Too Many Requests")
                            path.endsWith("/truyen/s3") -> {
                                detail3Called = true
                                MockResponse(code = 200, body = detail1Html)
                            }
                            else -> MockResponse(code = 404, body = "Not Found")
                        }
                    }
                }

            val results = scraper.search("tien", completedOnly = false)
            assertEquals(3, results.size)
            assertEquals(120, results[0].totalChapters)
            assertEquals(0, results[1].totalChapters)
            assertEquals(0, results[2].totalChapters)
            assertFalse(detail3Called)
        }

    @Test
    fun `fetchTotalChaptersFromBookIndex returns zero gracefully on network or security param failure`() =
        runTest {
            val docWithoutParams = org.jsoup.Jsoup.parse("<html><body><div class='cover-info'><h2>Test</h2></div></body></html>")
            val count = scraper.fetchTotalChaptersFromBookIndex(docWithoutParams, server.url("/truyen/no-params").toString())
            assertEquals(0, count)
        }

    @Test
    fun `TC1 - getStoryDetail extracts cover from book-info cover-wrapper and strips o query parameter`() =
        runTest {
            val html = loadFixture("story_detail.html")
            server.enqueue(MockResponse(code = 200, body = html))

            val story = scraper.getStoryDetail(server.url("/truyen/sample").toString())
            assertTrue(story.coverImageUrl.endsWith("/photo/6a950ea654b8087c7d7c833d"))
            assertFalse(story.coverImageUrl.contains("?o="))
            assertFalse(story.coverImageUrl.contains("&o="))
        }

    @Test
    fun `TC2 and TC3 - extractCoverUrl filters default placeholders and falls back to data-src when src is svg data URI`() {
        val defaultCoverImg =
            org.jsoup.Jsoup
                .parse("""<img src="/static/img/default-cover.jpg" data-src=""/>""")
                .selectFirst("img")
        assertEquals("", scraper.extractCoverUrl(defaultCoverImg))

        val svgOnlyImg =
            org.jsoup.Jsoup
                .parse("""<img src="data:image/svg+xml;base64,PHN2Zz4=" data-src=""/>""")
                .selectFirst("img")
        assertEquals("", scraper.extractCoverUrl(svgOnlyImg))

        val lazyDataSrcImg =
            org.jsoup.Jsoup
                .parse("""<img src="data:image/svg+xml;base64,PHN2Zz4=" data-src="/photo/123?o=1"/>""")
                .selectFirst("img")
        val extracted = scraper.extractCoverUrl(lazyDataSrcImg)
        assertTrue(extracted.endsWith("/photo/123"))
        assertFalse(extracted.contains("?o="))
    }

    @Test
    fun `getGenreList returns 19 canonical Loai hinh genres and GENRE_QUERY_MAP maps every slug to 24-hex genre ObjectId matching fixture links`() =
        runTest {
            val genres = scraper.getGenreList()
            assertEquals(19, genres.size)
            assertEquals(19, WikidichScraper.GENRE_QUERY_MAP.size)
            assertEquals(genres.map { it.slug }.toSet(), WikidichScraper.GENRE_QUERY_MAP.keys)

            val objectIdRegex = Regex("^genre=[0-9a-f]{24}$")
            WikidichScraper.GENRE_QUERY_MAP.values.forEach { param ->
                assertTrue(objectIdRegex.matches(param), "Expected 24-hex genre ObjectId, got: $param")
            }

            // Cross-check against real fixtures in repo
            val detailDoc = org.jsoup.Jsoup.parse(loadFixture("story_detail.html"))
            val mangaHref = detailDoc.selectFirst("a[href*='genre=5794f03dd7ced228f44191a8']")?.attr("href").orEmpty()
            assertTrue(mangaHref.contains(WikidichScraper.GENRE_QUERY_MAP.getValue("manga-anime")))

            val searchDoc = org.jsoup.Jsoup.parse(loadFixture("search_logged_in.html"))
            val searchHtml = searchDoc.html()
            assertTrue(searchHtml.contains(WikidichScraper.GENRE_QUERY_MAP.getValue("huyen-huyen")))
            assertTrue(searchHtml.contains(WikidichScraper.GENRE_QUERY_MAP.getValue("vo-hiep")))
            assertTrue(searchHtml.contains(WikidichScraper.GENRE_QUERY_MAP.getValue("tien-hiep")))
            assertTrue(searchHtml.contains(WikidichScraper.GENRE_QUERY_MAP.getValue("trinh-tham")))
            assertTrue(searchHtml.contains(WikidichScraper.GENRE_QUERY_MAP.getValue("kinh-di")))

            // Removed legacy slugs return emptyList() without making HTTP requests
            val removedLegacySlugs = listOf("gioi-giai-tri", "hien-dai", "he-thong", "oe", "vong-du")
            for (removed in removedLegacySlugs) {
                val res = scraper.getStoriesByGenre(removed, 1, 0, completedOnly = true, maleOnly = true)
                assertTrue(res.isEmpty())
            }
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `search with 5 params throws LoginRequiredException when response contains login wall`() =
        runTest {
            val loginHtml = loadFixture("search_not_logged_in.html")
            server.enqueue(MockResponse(code = 200, body = loginHtml))

            assertThrows(ScraperException.LoginRequiredException::class.java) {
                kotlinx.coroutines.runBlocking {
                    scraper.search(
                        keyword = "tiên",
                        completedOnly = true,
                        page = 1,
                        maleOnly = true,
                        genreSlug = "tien-hiep",
                    )
                }
            }
        }

    @Test
    fun `search builds server-side URL with qs 1 genre gender status keyword and start pagination`() =
        runTest {
            val html =
                loadFixture("search_logged_in.html")
                    .replace("Còn tiếp", "Hoàn thành")
                    .replace("Ngôn tình", "Nam sinh")
            server.enqueue(MockResponse(code = 200, body = html))

            val stories =
                scraper.search(
                    keyword = "hệ thống",
                    completedOnly = true,
                    page = 2,
                    maleOnly = true,
                    genreSlug = "tien-hiep",
                )
            assertTrue(stories.isNotEmpty())

            val recorded = server.takeRequest()
            assertEquals(
                "/tim-kiem?q=h%E1%BB%87+th%E1%BB%91ng&qs=1&genre=5794f03dd7ced228f44191a4&gender=5794f03dd7ced228f4419196&status=5794f03dd7ced228f4419191&start=20",
                recorded.url.encodedPath + "?" + recorded.url.encodedQuery,
            )
        }

    @Test
    fun `search filters out all 7 non-male Wikidich gender labels when maleOnly is true`() =
        runTest {
            val genders =
                listOf(
                    "nam-sinh-1" to ("Truyện Nam Sinh" to "Nam sinh"),
                    "ngon-tinh-2" to ("Truyện Ngôn Tình" to "Ngôn tình"),
                    "dam-my-3" to ("Truyện Đam Mỹ" to "Đam mỹ"),
                    "bach-hop-4" to ("Truyện Bách Hợp" to "Bách hợp"),
                    "nu-tan-5" to ("Truyện Nữ Tần" to "Nữ tần"),
                    "nu-ton-6" to ("Truyện Nữ Tôn" to "Nữ tôn"),
                    "khong-cp-7" to ("Truyện Không CP" to "Không CP"),
                    "da-nguyen-8" to ("Truyện Đa Nguyên" to "Đa nguyên"),
                )
            val itemsHtml =
                genders.joinToString("\n") { (slug, pair) ->
                    val (title, genderLabel) = pair
                    """
                    <div class="book-item">
                        <div class="info-col">
                            <a class="tooltipped" href="/truyen/$slug">
                                <h5 class="book-title truncate">$title</h5>
                            </a>
                            <p class="book-author"><a href="/tac-gia/tg">Tác giả</a></p>
                            <p class="book-gender">$genderLabel</p>
                            <p class="book-publisher"><a href="/tim-kiem?status=5794f03dd7ced228f4419191">Hoàn thành</a></p>
                        </div>
                    </div>
                    """.trimIndent()
                }
            val html = "<html><body><div class=\"book-list\">$itemsHtml</div></body></html>"
            server.enqueue(MockResponse(code = 200, body = html))
            server.enqueue(MockResponse(code = 200, body = html))

            val maleFiltered =
                scraper.search(
                    keyword = "truyện",
                    completedOnly = true,
                    page = 1,
                    maleOnly = true,
                    genreSlug = null,
                )
            assertEquals(1, maleFiltered.size)
            assertEquals("Truyện Nam Sinh", maleFiltered.first().title)

            val allStories =
                scraper.search(
                    keyword = "truyện",
                    completedOnly = true,
                    page = 1,
                    maleOnly = false,
                    genreSlug = null,
                )
            assertEquals(8, allStories.size)
        }

    @Test
    fun `getFirstPageChapters extracts dynamic pageSize 501 from loadBookIndex script when present`() =
        runTest {
            val storyHtml =
                loadFixture("story_detail.html")
                    .replace("loadBookIndex(0, 99, false)", "loadBookIndex(0, 501, false)")
            val ajaxHtml =
                """
                <ul class="pagination">
                  <li class="active"><a href="#!" data-start="0" data-size="501" data-action="loadBookIndex">1</a></li>
                  <li class="waves-effect"><a href="#!" data-start="501" data-size="501" data-action="loadBookIndex">2</a></li>
                </ul>
                <ul>
                  <li class="chapter-name"><a href="/truyen/sample/c-1">Chương 1</a></li>
                </ul>
                """.trimIndent()

            var requestedQuery = ""
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse =
                        if (request.url.encodedPath.contains("/book/index")) {
                            requestedQuery = request.url.encodedQuery.orEmpty()
                            MockResponse(code = 200, body = ajaxHtml)
                        } else {
                            MockResponse(code = 200, body = storyHtml)
                        }
                }

            val result = scraper.getFirstPageChapters(server.url("/truyen/sample").toString())
            assertTrue(requestedQuery.contains("size=501"), "Expected size=501 in query, got: $requestedQuery")
            assertEquals(2, result.totalPages)
            assertEquals(1, result.chapters.size)
        }
}
