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

            val stories = scraper.getCompletedStories(1)
            assertTrue(stories.isNotEmpty())
            assertEquals("wikidich", stories.first().sourceId)
        }

    @Test
    fun `getStoriesByGenre with unknown genre returns empty list without network call`() =
        runTest {
            val stories = scraper.getStoriesByGenre("genre-khong-ton-tai", 1, 0, false)
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
    fun `getStoriesByGenre uses filter form qs=1 params while search uses q and qs=1`() =
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

            val page1Stories = scraper.getStoriesByGenre("gioi-giai-tri", 1, 0, true)
            assertTrue(page1Stories.isNotEmpty())
            assertTrue(requestedPaths.first().contains("qs=1"))
            assertTrue(requestedPaths.first().contains("status=5794f03dd7ced228f4419191"))
            assertTrue(requestedPaths.first().contains("tag=5794f03ed7ced228f44191de"))
            assertTrue(requestedPaths.first().contains("tc=&tf=0"))
            assertEquals("Hoàn thành", page1Stories.first().status)

            requestedPaths.clear()
            scraper.getStoriesByGenre("hien-dai", 2, 0, true)
            assertTrue(requestedPaths.first().contains("qs=1"))
            assertTrue(requestedPaths.first().contains("age=5794f03dd7ced228f441919d"))
            assertTrue(requestedPaths.first().contains("start=20"))

            requestedPaths.clear()
            scraper.enrichInTests = true
            val searchStories = scraper.search("hogwarts", false)
            assertTrue(searchStories.isNotEmpty())
            assertTrue(requestedPaths.first().contains("/tim-kiem?q=hogwarts&qs=1"))
            assertEquals(245, searchStories.first().totalChapters)
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
}
