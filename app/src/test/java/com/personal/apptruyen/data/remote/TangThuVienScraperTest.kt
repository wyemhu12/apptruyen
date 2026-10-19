package com.personal.apptruyen.data.remote

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TangThuVienScraperTest {
    private lateinit var server: MockWebServer
    private lateinit var scraper: TangThuVienScraper

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        scraper =
            TangThuVienScraper(OkHttpClient()).apply {
                customBaseUrl = server.url("/").toString().removeSuffix("/")
            }
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `search parses story cards and enriches chapter counts from detail page`() =
        runTest {
            val searchHtml =
                """
                <html><body>
                <div class="flex items-start gap-2">
                    <a href="/tien-nghich"><img src="/_next/image?url=http%3A%2F%2Fcdn.tangthuvien.org%2Fcovers%2Ftien-nghich.jpg&amp;w=256"/></a>
                    <div class="flex-1">
                        <h3><a href="/tien-nghich">Tiên Nghịch</a></h3>
                        <p class="text-muted-foreground truncate">Nhĩ Căn</p>
                        <span>Hoàn thành</span>
                    </div>
                </div>
                </body></html>
                """.trimIndent()
            val detailHtml = "<html><body><span>2088<!-- --> Chương</span></body></html>"

            server.enqueue(MockResponse(code = 200, body = searchHtml))
            server.enqueue(MockResponse(code = 200, body = detailHtml))

            val results = scraper.search("Tiên Nghịch", completedOnly = false)

            assertEquals(1, results.size)
            val story = results[0]
            assertEquals("tangthuvien_tien-nghich", story.id)
            assertEquals("tien-nghich", story.slug)
            assertEquals("Tiên Nghịch", story.title)
            assertEquals("Nhĩ Căn", story.author)
            assertEquals("Hoàn thành", story.status)
            assertEquals(2088, story.totalChapters)
            assertEquals("https://cdn.tangthuvien.org/covers/tien-nghich.jpg", story.coverImageUrl)
        }

    @Test
    fun `getStoryDetail parses metadata genres description and totalChapters`() =
        runTest {
            val detailHtml =
                """
                <html><body>
                <h1 class="text-xl font-bold">Tiên Nghịch</h1>
                <a href="/tac-gia/nhi-can">Nhĩ Căn</a>
                <a href="/truyen?genre=tien-hiep">Tiên Hiệp</a>
                <img src="http://cdn.tangthuvien.org/covers/tien-nghich.jpg"/>
                <span>Hoàn thành</span>
                <span>241<!-- --> Chương</span>
                <div class="leading-relaxed"><p>Mô tả truyện Tiên Nghịch</p></div>
                </body></html>
                """.trimIndent()

            server.enqueue(MockResponse(code = 200, body = detailHtml))

            val story = scraper.getStoryDetail("${scraper.baseUrl}/tien-nghich")

            assertEquals("tangthuvien_tien-nghich", story.id)
            assertEquals("Tiên Nghịch", story.title)
            assertEquals("Nhĩ Căn", story.author)
            assertEquals(listOf("Tiên Hiệp"), story.genres)
            assertEquals("Mô tả truyện Tiên Nghịch", story.description)
            assertEquals("Hoàn thành", story.status)
            assertEquals(241, story.totalChapters)
            assertEquals("https://cdn.tangthuvien.org/covers/tien-nghich.jpg", story.coverImageUrl)
        }

    @Test
    fun `getChapterList generates sequential chapters from detail page totalChapters`() =
        runTest {
            val detailHtml = "<html><body><h1 class=\"text-xl font-bold\">Tiên Nghịch</h1><span>3<!-- --> Chương</span></body></html>"
            server.enqueue(MockResponse(code = 200, body = detailHtml))

            val chapters = scraper.getChapterList("${scraper.baseUrl}/tien-nghich")

            assertEquals(3, chapters.size)
            assertEquals(1, chapters[0].chapterNumber)
            assertEquals("Chương 1", chapters[0].title)
            assertEquals("${scraper.baseUrl}/tien-nghich/1", chapters[0].url)
            assertEquals(3, chapters[2].chapterNumber)
            assertEquals("${scraper.baseUrl}/tien-nghich/3", chapters[2].url)
        }

    @Test
    fun `getChapterContentWithTitle strips hash prefix and merges dialogue lines`() =
        runTest {
            val chapterHtml =
                """
                <html><body>
                <h1>#8 Chương 8: Nhập Môn</h1>
                <div class="chapter-content">
                    <p>Nội dung đoạn 1 đủ dài trên năm mươi ký tự để vượt qua kiểm tra độ dài tối thiểu của scraper.</p>
                    <p>- Câu thoại bắt đầu bằng gạch ngang được gộp vào đoạn trước.</p>
                    <p>Nội dung đoạn 2 tiếp theo.</p>
                </div>
                </body></html>
                """.trimIndent()

            server.enqueue(MockResponse(code = 200, body = chapterHtml))

            val result = scraper.getChapterContentWithTitle("${scraper.baseUrl}/tien-nghich/8")

            assertEquals("Chương 8: Nhập Môn", result.title)
            assertFalse(result.content.contains("#8"))
            assertTrue(result.content.contains("của scraper. - Câu thoại bắt đầu bằng gạch ngang được gộp vào đoạn trước."))
            assertTrue(result.content.contains("Nội dung đoạn 2 tiếp theo."))
        }

    @Test
    fun `getStoryDetail throws HttpException on 404 and after retries on 500`() =
        runTest {
            server.enqueue(MockResponse(code = 404, body = "Not Found"))

            val ex404 =
                assertThrows<ScraperException.HttpException> {
                    scraper.getStoryDetail("${scraper.baseUrl}/khong-ton-tai")
                }
            assertEquals(404, ex404.statusCode)
            assertEquals(1, server.requestCount)

            repeat(3) {
                server.enqueue(MockResponse(code = 500, body = "Server Error"))
            }

            val ex500 =
                assertThrows<ScraperException.HttpException> {
                    scraper.getStoryDetail("${scraper.baseUrl}/loi-may-chu")
                }
            assertEquals(500, ex500.statusCode)
            assertEquals(4, server.requestCount)
        }
}
