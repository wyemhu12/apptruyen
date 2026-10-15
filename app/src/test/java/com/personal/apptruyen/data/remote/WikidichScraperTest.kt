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
}
