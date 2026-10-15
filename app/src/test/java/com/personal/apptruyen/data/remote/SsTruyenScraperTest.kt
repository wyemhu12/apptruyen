package com.personal.apptruyen.data.remote

import com.personal.apptruyen.data.model.Chapter
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SsTruyenScraperTest {

    private lateinit var server: MockWebServer
    private lateinit var scraper: SsTruyenScraper
    private val client = OkHttpClient()

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()

        val mockBaseUrl = server.url("/").toString().removeSuffix("/")
        scraper =
            SsTruyenScraper(client).apply {
                customBaseUrl = mockBaseUrl
                customApiBaseUrl = mockBaseUrl
            }
    }

    @AfterEach
    fun teardown() {
        server.close()
    }

    @Test
    fun `search parses stories correctly from HTML`() =
        runTest {
            val html =
                """
                <html>
                <body>
                    <a itemprop="hasPart" href="/truyen/tien-nghich">
                        <img itemprop="image" src="https://file.sstruyen.pics/files/covers/tien-nghich.jpg" alt="Tiên Nghịch" />
                        <p itemprop="name">Tiên Nghịch</p>
                        <div itemprop="author"><span itemprop="name">Nhĩ Căn</span></div>
                        <span itemprop="bookFormat">Đã hoàn thành</span>
                    </a>
                </body>
                </html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))

            val results = scraper.search("tien", completedOnly = false)
            assertEquals(1, results.size)
            val story = results.first()
            assertEquals("tien-nghich", story.id)
            assertEquals("tien-nghich", story.slug)
            assertEquals("Tiên Nghịch", story.title)
            assertEquals("Nhĩ Căn", story.author)
            assertEquals("Hoàn thành", story.status)
            assertEquals("https://file.sstruyen.pics/files/covers/tien-nghich.jpg", story.coverImageUrl)
        }

    @Test
    fun `search with completedOnly filters completed stories`() =
        runTest {
            val html =
                """
                <html>
                <body>
                    <a itemprop="hasPart" href="/truyen/tien-nghich">
                        <p itemprop="name">Tiên Nghịch</p>
                        <span itemprop="bookFormat">Đã hoàn thành</span>
                    </a>
                    <a itemprop="hasPart" href="/truyen/dang-phat-hanh">
                        <p itemprop="name">Truyện Đang Ra</p>
                        <span itemprop="bookFormat">Đang phát hành</span>
                    </a>
                </body>
                </html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))

            val results = scraper.search("truyen", completedOnly = true)
            assertEquals(1, results.size)
            assertEquals("tien-nghich", results.first().id)
        }

    @Test
    fun `getStoryDetail parses metadata and novelId safely`() =
        runTest {
            val html =
                """
                <html>
                <head>
                    <meta property="og:title" content="Tiên Nghịch - Nhĩ Căn" />
                    <meta property="og:description" content="Truyện Tiên Nghịch 2088 chương hay nhất" />
                </head>
                <body>
                    <h1>Tiên Nghịch</h1>
                    <div itemprop="author"><span itemprop="name">Nhĩ Căn</span></div>
                    <a href="/the-loai/tien-hiep">Tiên Hiệp</a>
                    <img itemprop="image" src="/files/covers/tien-nghich.jpg" />
                    <div itemprop="description">Một thiếu niên bình thường bước vào con đường tu tiên...</div>
                    <span itemprop="bookFormat">Đã hoàn thành</span>
                    <script>
                        window.__INITIAL_DATA__ = {"data":{"id":104251,"name":"Tiên Nghịch","slug":"tien-nghich"}};
                    </script>
                </body>
                </html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))

            val story = scraper.getStoryDetail("${scraper.baseUrl}/truyen/tien-nghich")
            assertEquals("Tiên Nghịch", story.title)
            assertEquals("Nhĩ Căn", story.author)
            assertEquals(listOf("Tiên Hiệp"), story.genres)
            assertEquals("Hoàn thành", story.status)
            assertEquals("https://file.sstruyen.pics/files/covers/tien-nghich.jpg", story.coverImageUrl)
            assertEquals(104251L, scraper.novelIdCache["tien-nghich"])
        }

    @Test
    fun `getFirstPageChapters extracts chapters and totalPages from REST API`() =
        runTest {
            scraper.novelIdCache["tien-nghich"] = 104251L
            val json =
                """
                {
                    "chapters": [
                        {"id": 104251, "name": "Chương 1: Đi xa", "slug": "chuong-1-di-xa"},
                        {"id": 104252, "name": "Chương 2: Ly hương", "slug": "chuong-2-ly-huong"}
                    ],
                    "total": 2088,
                    "page": 1,
                    "totalPages": 42
                }
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = json))

            val result = scraper.getFirstPageChapters("${scraper.baseUrl}/truyen/tien-nghich")
            assertEquals(42, result.totalPages)
            assertEquals(2, result.chapters.size)

            val ch1 = result.chapters[0]
            assertEquals(1, ch1.chapterNumber)
            assertEquals("Chương 1: Đi xa", ch1.title)
            assertTrue(ch1.url.contains("/doc-truyen/tien-nghich-chuong-1-di-xa-104251"))

            val ch2 = result.chapters[1]
            assertEquals(2, ch2.chapterNumber)
            assertEquals("Chương 2: Ly hương", ch2.title)
            assertTrue(ch2.url.contains("/doc-truyen/tien-nghich-chuong-2-ly-huong-104252"))
        }

    @Test
    fun `getRemainingChapters calls REST API and batches chapters`() =
        runTest {
            scraper.novelIdCache["tien-nghich"] = 104251L
            val json =
                """
                {
                    "chapters": [
                        {"id": 104301, "name": "Chương 51: Trúc Cơ", "slug": "chuong-51-truc-co"}
                    ],
                    "total": 2088,
                    "page": 2,
                    "totalPages": 2
                }
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = json))

            val collected = mutableListOf<Chapter>()
            scraper.getRemainingChapters("${scraper.baseUrl}/truyen/tien-nghich", 2) { batch ->
                collected.addAll(batch)
            }

            assertEquals(1, collected.size)
            assertEquals(51, collected.first().chapterNumber)
            assertEquals("Chương 51: Trúc Cơ", collected.first().title)
        }

    @Test
    fun `getRemainingChapters recovering when novelIdCache missing`() =
        runTest {
            scraper.novelIdCache.clear()
            val apiNovelJson =
                """
                {
                    "id": 104251,
                    "name": "Tiên Nghịch",
                    "slug": "tien-nghich"
                }
                """.trimIndent()
            val chaptersJson =
                """
                {
                    "chapters": [
                        {"id": 104301, "name": "Chương 51: Trúc Cơ", "slug": "chuong-51-truc-co"}
                    ],
                    "totalPages": 2
                }
                """.trimIndent()

            server.enqueue(MockResponse(code = 200, body = apiNovelJson))
            server.enqueue(MockResponse(code = 200, body = chaptersJson))

            val collected = mutableListOf<Chapter>()
            scraper.getRemainingChapters("${scraper.baseUrl}/truyen/tien-nghich", 2) { batch ->
                collected.addAll(batch)
            }

            assertEquals(1, collected.size)
            assertEquals(51, collected.first().chapterNumber)
            assertEquals(104251L, scraper.novelIdCache["tien-nghich"])
        }

    @Test
    fun `getRemainingChapters throws ParseException when recovery fails`() =
        runTest {
            scraper.novelIdCache.clear()
            val htmlWithoutId = "<html><body><h1>Tiên Nghịch</h1></body></html>"
            server.enqueue(MockResponse(code = 404, body = "Not Found"))
            server.enqueue(MockResponse(code = 200, body = htmlWithoutId))

            val exception =
                assertThrows(ScraperException.ParseException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        scraper.getRemainingChapters("${scraper.baseUrl}/truyen/tien-nghich", 2) {}
                    }
                }
            assertTrue(exception.message?.contains("Không thể xác định mã định danh truyện") == true)
        }

    @Test
    fun `getRemainingChapters throws NetworkException on failed pages`() =
        runTest {
            scraper.novelIdCache["tien-nghich"] = 104251L
            server.enqueue(MockResponse(code = 500, body = "Internal Server Error 1"))
            server.enqueue(MockResponse(code = 500, body = "Internal Server Error 2"))
            server.enqueue(MockResponse(code = 500, body = "Internal Server Error 3"))

            val exception =
                assertThrows(ScraperException.NetworkException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        scraper.getRemainingChapters("${scraper.baseUrl}/truyen/tien-nghich", 2) {}
                    }
                }
            assertTrue(exception.message?.contains("lỗi các trang: [2]") == true)
        }

    @Test
    fun `getChapterContent with new url extracts from div prose`() =
        runTest {
            val html =
                """
                <html>
                <body>
                    <div class="prose">
                        <p>Đoạn văn thứ nhất của chương truyện mới với đầy đủ nội dung chi tiết và câu từ phong phú.</p>
                        <p>Đoạn văn thứ hai tiếp tục mạch truyện hấp dẫn của tác phẩm để người đọc theo dõi.</p>
                    </div>
                </body>
                </html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))

            val url = "${scraper.baseUrl}/doc-truyen/tien-nghich-chuong-1-di-xa-104251"
            val content = scraper.getChapterContent(url)

            assertTrue(content.contains("Đoạn văn thứ nhất của chương truyện mới"))
            assertTrue(content.contains("Đoạn văn thứ hai tiếp tục mạch truyện"))
            assertTrue(content.contains("\n\n"))
            assertTrue(content.length >= 50)
        }

    @Test
    fun `getChapterContent with legacy url on MockWebServer`() =
        runTest {
            val html =
                """
                <html>
                <body>
                    <div id="chapter-c" class="content box-chap">
                        Đoạn văn thứ nhất của truyện Tiên Nghịch trên trang sstruyen cũ.<br><br>
                        Đoạn văn thứ hai mô tả cảnh giới tu tiên đầy khắc nghiệt và gian nan.
                    </div>
                </body>
                </html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))

            val legacyUrl = server.url("/truyen/tien-nghich/chuong-1").toString()
            val content = scraper.getChapterContent(legacyUrl)

            val recorded = server.takeRequest()
            assertEquals("/truyen/tien-nghich/chuong-1", recorded.url.encodedPath)

            assertTrue(content.contains("Đoạn văn thứ nhất của truyện Tiên Nghịch"))
            assertTrue(content.contains("Đoạn văn thứ hai mô tả cảnh giới tu tiên"))
            assertTrue(content.contains("\n\n"))
            assertTrue(content.length >= 50)
        }

    @Test
    fun `getCompletedStories requests correct filter path`() =
        runTest {
            val html =
                """
                <html>
                <body>
                    <a itemprop="hasPart" href="/truyen/tien-nghich">
                        <p itemprop="name">Tiên Nghịch</p>
                    </a>
                </body>
                </html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))

            val stories = scraper.getCompletedStories(1)
            assertEquals(1, stories.size)
            assertEquals("Hoàn thành", stories.first().status)

            val recorded = server.takeRequest()
            assertEquals("/danh-sach/bo-loc", recorded.url.encodedPath)
            val query = recorded.url.query ?: ""
            assertTrue(query.contains("status=completed"))
            assertTrue(query.contains("sort=updatedAt"))
            assertTrue(query.contains("page=1"))
        }

    @Test
    fun `getStoriesByGenre requests correct query params`() =
        runTest {
            val html =
                """
                <html>
                <body>
                    <a itemprop="hasPart" href="/truyen/tien-nghich">
                        <p itemprop="name">Tiên Nghịch</p>
                    </a>
                </body>
                </html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))

            val stories = scraper.getStoriesByGenre("tien-hiep", 1, minChapters = 500, completedOnly = true)
            assertEquals(1, stories.size)

            val recorded = server.takeRequest()
            val urlStr = recorded.url.toString()
            assertTrue(urlStr.contains("genre=tien-hiep"))
            assertTrue(urlStr.contains("status=completed"))
            assertTrue(urlStr.contains("type=2"))
            assertTrue(urlStr.contains("sort=updatedAt"))
            assertTrue(urlStr.contains("page=1"))
        }

    @Test
    fun `getGenreList fetches all genres from REST API JSON Array`() =
        runTest {
            val json =
                """
                [
                    {"name":"Bách Hợp","slug":"bach-hop"},
                    {"name":"Khoa Huyễn","slug":"khoa-huyen"},
                    {"name":"Truyện Sắc","slug":"truyen-sac"},
                    {"name":"Thể loại","slug":"the-loai"}
                ]
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = json))

            val genres = scraper.getGenreList()
            assertEquals(2, genres.size)
            assertEquals("bach-hop", genres[0].slug)
            assertEquals("Bách Hợp", genres[0].name)
            assertEquals("khoa-huyen", genres[1].slug)
            assertEquals("Khoa Huyễn", genres[1].name)
        }

    @Test
    fun `getGenreList falls back to HTML when API fails`() =
        runTest {
            server.enqueue(MockResponse(code = 404, body = "Not Found"))
            val html =
                """
                <html>
                <body>
                    <a href="/the-loai/tien-hiep">Tiên Hiệp</a>
                    <a href="/the-loai/kiem-hiep">Kiếm Hiệp</a>
                    <a href="/the-loai/truyen-sac">Truyện Sắc</a>
                </body>
                </html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))

            val genres = scraper.getGenreList()
            assertEquals(2, genres.size)
            assertEquals("kiem-hiep", genres[0].slug)
            assertEquals("tien-hiep", genres[1].slug)
        }

    @Test
    fun `emptyContentThrowsParseException when content is too short`() =
        runTest {
            val html = "<html><body><div class=\"prose\"><p>Ngắn</p></div></body></html>"
            server.enqueue(MockResponse(code = 200, body = html))

            val url = "${scraper.baseUrl}/doc-truyen/tien-nghich-chuong-1-104251"
            val exception =
                assertThrows(ScraperException.ParseException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        scraper.getChapterContent(url)
                    }
                }
            assertTrue(exception.message?.contains("Nội dung chương rỗng") == true)
        }

    @Test
    fun `fetchJson does not retry non-retryable 404 or JSONException`() =
        runTest {
            server.enqueue(MockResponse(code = 404, body = "Not Found"))
            assertThrows(ScraperException.HttpException::class.java) {
                kotlinx.coroutines.runBlocking {
                    scraper.fetchJson(server.url("/api/not-found").toString())
                }
            }
            assertEquals(1, server.requestCount)

            server.enqueue(MockResponse(code = 200, body = "{malformed-json"))
            val parseEx =
                assertThrows(ScraperException.ParseException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        scraper.fetchJson(server.url("/api/malformed").toString())
                    }
                }
            assertEquals("Lỗi định dạng dữ liệu danh sách chương", parseEx.message)
            assertEquals(2, server.requestCount)
        }

    @Test
    fun `search parses totalChapters from dateModified span and chapter name pattern`() =
        runTest {
            val html =
                """
                <html>
                <body>
                    <a itemprop="hasPart" href="/truyen/hogwarts-ta-that-su-la-dien-hinh-vu-su">
                        <p itemprop="name">Hogwarts: Ta Thật Sự Là Điển Hình Vu sư</p>
                        <div itemprop="author"><span itemprop="name">Đoản Vĩ Ly Miêu</span></div>
                        <span itemprop="bookFormat">Đang phát hành</span>
                        <span itemprop="dateModified" class="text-xs line-clamp-1">Chương 483</span>
                    </a>
                </body>
                </html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))

            val results = scraper.search("hogwarts", completedOnly = false)
            assertEquals(1, results.size)
            val story = results.first()
            assertEquals("hogwarts-ta-that-su-la-dien-hinh-vu-su", story.id)
            assertEquals(483, story.totalChapters)
        }

    @Test
    fun `getFirstPageChapters fetches novelId from API and computes totalPages correctly`() =
        runTest {
            scraper.novelIdCache.clear()
            val novelApiJson =
                """
                {
                    "id": 38717,
                    "name": "Hogwarts: Ta Thật Sự Là Điển Hình Vu sư",
                    "chapterCount": 999
                }
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = novelApiJson))

            val chaptersJson =
                """
                {
                    "chapters": [
                        {"id": 4313022, "name": "Chương 1: Khởi đầu", "slug": "chuong-1-khoi-dau"}
                    ],
                    "total": 999,
                    "page": 1,
                    "totalPages": 20
                }
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = chaptersJson))

            val result = scraper.getFirstPageChapters("${scraper.baseUrl}/truyen/hogwarts-ta-that-su-la-dien-hinh-vu-su")
            assertEquals(20, result.totalPages)
            assertEquals(1, result.chapters.size)
            assertEquals(1, result.chapters.first().chapterNumber)
            assertEquals(38717L, scraper.novelIdCache["hogwarts-ta-that-su-la-dien-hinh-vu-su"])
        }

    @Test
    fun `getChapterContent extracts br separated text and strips footer boilerplate`() =
        runTest {
            val html =
                """
                <html>
                <body>
                    <div class="wrapper prose dark:prose-invert max-w-none">
                        <br><br>
                        "Người yếu mới nói công bằng, cường giả chỉ nói chinh phục, Turville!"
                        <br><br>
                        "Nhưng là lão sư, cường giả thương hại cũng là bình đẳng a."
                        <br><br>
                        Tháng 7, Durmstrang. Đây là một khu nhà không tính là lớn trường học.
                        <div class="text-center mt-8">
                            <p>===================================<br><p>Nếu truyện không hiện chữ thiệu nội dung hoặc bắt nhập mật khẩu hay đăng nhập hãy báo lỗi để bên mình sửa lỗi</p>
                        </div>
                    </div>
                </body>
                </html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))

            val url = "${scraper.baseUrl}/doc-truyen/an-giau-o-hogwarts-lam-giao-su-chuong-1-9744041"
            val content = scraper.getChapterContent(url)

            assertTrue(content.contains("Người yếu mới nói công bằng, cường giả chỉ nói chinh phục, Turville!"))
            assertTrue(content.contains("Nhưng là lão sư, cường giả thương hại cũng là bình đẳng a."))
            assertTrue(content.contains("Tháng 7, Durmstrang."))
            assertTrue(!content.contains("Nếu truyện không hiện chữ"))
            assertTrue(!content.contains("==================================="))
        }

    @Test
    fun `search extracts data-src cover when src is base64 placeholder`() =
        runTest {
            val html =
                """
                <html>
                <body>
                    <a itemProp="hasPart" href="/truyen/an-giau-o-hogwarts-lam-giao-su">
                        <img class="lazy" src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGMAAQAABQABDQottAAAAABJRU5ErkJggg==" data-src="https://file.sstruyen.pics/files/covers/an-giau-o-hogwarts-lam-giao-su.webp" itemProp="image" />
                        <p itemProp="name">Ẩn Giấu Ở Hogwarts Làm Giáo Sư</p>
                        <div itemProp="author"><span itemProp="name">Tác Giả A</span></div>
                        <span itemProp="bookFormat">Đã hoàn thành</span>
                        <span itemProp="dateModified">Chương 520</span>
                    </a>
                </body>
                </html>
                """.trimIndent()
            server.enqueue(MockResponse(code = 200, body = html))

            val results = scraper.search("hogwarts", false)
            assertEquals(1, results.size)
            assertEquals(
                "https://file.sstruyen.pics/files/covers/an-giau-o-hogwarts-lam-giao-su.webp",
                results.first().coverImageUrl,
            )
            assertEquals(520, results.first().totalChapters)
        }

    @Test
    fun `normalizeCoverUrl handles leading slash covers and rejects base64`() {
        assertEquals(
            "https://file.sstruyen.pics/files/covers/sample.webp",
            scraper.normalizeCoverUrl("/covers/sample.webp"),
        )
        assertEquals(
            "https://file.sstruyen.pics/files/covers/sample.webp",
            scraper.normalizeCoverUrl("https://file.sstruyen.pics/files//covers/sample.webp"),
        )
        assertEquals(
            "",
            scraper.normalizeCoverUrl("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwC"),
        )
    }
}
