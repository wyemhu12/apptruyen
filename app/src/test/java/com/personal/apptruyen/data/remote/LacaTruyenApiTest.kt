package com.personal.apptruyen.data.remote

import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BaseScraperCacheTest {
    private lateinit var server: MockWebServer
    private lateinit var scraper: FakeCacheScraper

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        scraper =
            FakeCacheScraper(
                client = OkHttpClient(),
                baseUrl = server.url("/").toString().removeSuffix("/"),
            )
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun `fetchDocument returns cached document within CACHE_TTL_MS without second network request`() {
        server.enqueue(MockResponse(code = 200, body = "<html><body>Lần 1</body></html>"))
        server.enqueue(MockResponse(code = 200, body = "<html><body>Lần 2</body></html>"))

        scraper.fakeNowMs = 0L
        val doc1 = scraper.fetch("/page")
        assertEquals("Lần 1", doc1.body().text())

        scraper.fakeNowMs = BaseScraper.CACHE_TTL_MS - 1L
        val doc2 = scraper.fetch("/page")
        assertEquals("Lần 1", doc2.body().text())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `fetchDocument evicts expired cache entry after CACHE_TTL_MS and fetches fresh document from server`() {
        server.enqueue(MockResponse(code = 200, body = "<html><body>Lần 1</body></html>"))
        server.enqueue(MockResponse(code = 200, body = "<html><body>Lần 2</body></html>"))

        scraper.fakeNowMs = 0L
        val doc1 = scraper.fetch("/page")
        assertEquals("Lần 1", doc1.body().text())
        assertEquals(1, server.requestCount)

        scraper.fakeNowMs = BaseScraper.CACHE_TTL_MS + 1L
        val doc2 = scraper.fetch("/page")
        assertEquals("Lần 2", doc2.body().text())
        assertEquals(2, server.requestCount)
    }
}

internal class FakeCacheScraper(
    client: OkHttpClient,
    override val baseUrl: String,
    override val useEtagCaching: Boolean = false,
) : BaseScraper(client) {
    var fakeNowMs: Long = 1_000_000L
    override val sourceId: String = "fake"
    override val sourceName: String = "Fake"

    override fun nowMs(): Long = fakeNowMs

    fun fetch(url: String): Document = fetchDocument(url)

    override suspend fun search(
        keyword: String,
        completedOnly: Boolean,
    ) = emptyList<Story>()

    override suspend fun getStoryDetail(storyUrl: String): Story = throw UnsupportedOperationException()

    override suspend fun getFirstPageChapters(storyUrl: String) = StorySource.FirstPageResult(emptyList(), 1)

    override suspend fun getRemainingChapters(
        storyUrl: String,
        totalPages: Int,
        onBatchReady: suspend (List<Chapter>) -> Unit,
    ) {}

    override suspend fun getCompletedStories(page: Int): List<Story> = emptyList()

    override suspend fun getStoriesByGenre(
        genreSlug: String,
        page: Int,
        minChapters: Int,
        completedOnly: Boolean,
    ): List<Story> = emptyList()

    override suspend fun getGenreList(): List<Genre> = emptyList()
}
