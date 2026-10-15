package com.personal.apptruyen.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.personal.apptruyen.data.local.ChapterDao
import com.personal.apptruyen.data.local.DatabaseTransactionRunner
import com.personal.apptruyen.data.local.ReadingProgressDao
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.local.entity.ChapterEntity
import com.personal.apptruyen.data.local.entity.StoryEntity
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.ChapterContentResult
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.remote.ScraperCircuitBreaker
import com.personal.apptruyen.data.remote.ScraperException
import com.personal.apptruyen.data.remote.StorySource
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for StoryRepository (story/chapter core) and split repositories.
 */
class StoryRepositoryTest {

    private lateinit var storyRepository: StoryRepository
    private lateinit var downloadRepository: DownloadRepository
    private lateinit var readingProgressRepository: ReadingProgressRepository
    private lateinit var source: StorySource
    private lateinit var storyDao: StoryDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var progressDao: ReadingProgressDao
    private lateinit var transactionRunner: DatabaseTransactionRunner
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var authRepository: ISourceAuthRepository
    private val testScope = TestScope()

    @BeforeEach
    fun setUp() {
        source = mockk()
        every { source.sourceId } returns "truyencom"
        every { source.sourceName } returns "TruyenCom"
        every { source.baseUrl } returns "https://truyencom.com"
        every { source.legacyHosts } returns emptyList()

        storyDao = mockk(relaxed = true)
        chapterDao = mockk(relaxed = true)
        progressDao = mockk(relaxed = true)
        transactionRunner = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        every { dataStore.data } returns flowOf(emptyPreferences())
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.d(any(), any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>(), any()) } returns 0

        storyRepository =
            StoryRepository(
                setOf(source),
                storyDao,
                chapterDao,
                testScope,
                dataStore,
                ScraperCircuitBreaker(),
                authRepository,
            )
        downloadRepository =
            DownloadRepository(
                setOf(source),
                storyDao,
                chapterDao,
                progressDao,
                transactionRunner,
            )
        readingProgressRepository =
            ReadingProgressRepository(
                progressDao,
                storyDao,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    // ----- getStoryDetail -----

    @Test
    fun `getStoryDetail returns cached story when available`() =
        runTest {
            val entity =
                StoryEntity(
                    id = "test.1",
                    slug = "test",
                    title = "Test Story",
                    author = "Author",
                    url = "https://truyencom.com/test.1/",
                )
            coEvery { storyDao.getStoryById("test.1") } returns entity

            val result = storyRepository.getStoryDetail("https://truyencom.com/test.1/")
            assertEquals("Test Story", result.title)
            assertEquals("test.1", result.id)

            // Source should NOT be called since cache hit
            coVerify(exactly = 0) { source.getStoryDetail(any()) }
        }

    @Test
    fun `getStoryDetail fetches from web when not cached`() =
        runTest {
            coEvery { storyDao.getStoryById(any()) } returns null
            coEvery { source.getStoryDetail(any()) } returns
                Story(
                    id = "novel.5",
                    slug = "novel",
                    title = "Web Story",
                    url = "https://truyencom.com/novel.5/",
                )
            coEvery { storyDao.insertStory(any()) } just Runs

            val result = storyRepository.getStoryDetail("https://truyencom.com/novel.5/")
            assertEquals("Web Story", result.title)
            coVerify(exactly = 1) { source.getStoryDetail(any()) }
            coVerify(exactly = 1) { storyDao.insertStory(any()) }
        }

    // ----- searchStories -----

    @Test
    fun `searchStories aggregates from sources`() =
        runTest {
            val stories =
                listOf(
                    Story(id = "s.1", slug = "s", title = "Story 1", url = "https://truyencom.com/s.1/"),
                    Story(id = "s.2", slug = "s2", title = "Story 2", url = "https://truyencom.com/s2.2/"),
                )
            coEvery { source.search(any(), any()) } returns stories

            val results = storyRepository.searchStories("test")
            assertEquals(2, results.size)
            assertEquals("Story 1", results[0].title)
        }

    // ----- getChapterList -----

    @Test
    fun `getChapterList returns cached chapters on network failure`() =
        runTest {
            coEvery { source.getChapterList(any()) } throws Exception("Network error")
            coEvery { chapterDao.getChaptersByStoryOnce("test.1") } returns
                listOf(
                    ChapterEntity(
                        storyId = "test.1",
                        chapterNumber = 1,
                        title = "Chương 1",
                        url = "https://truyencom.com/truyen/test/chuong-1.html",
                        content = "cached content",
                        isDownloaded = true,
                    ),
                )

            val result = storyRepository.getChapterList("https://truyencom.com/test.1/", "test.1")
            assertEquals(1, result.size)
            assertEquals("Chương 1", result[0].title)
        }

    @Test
    fun `getChapterList throws when no cache and no network`() =
        runTest {
            coEvery { source.getChapterList(any()) } throws Exception("Network error")
            coEvery { chapterDao.getChaptersByStoryOnce("test.1") } returns emptyList()

            try {
                storyRepository.getChapterList("https://truyencom.com/test.1/", "test.1")
                fail("Expected exception")
            } catch (e: Exception) {
                assertTrue(e.message!!.contains("Không có kết nối mạng"))
            }
        }

    // ----- downloadChapters (DownloadRepository) -----

    @Test
    fun `downloadChapters emits progress updates`() =
        runTest {
            val chapters =
                listOf(
                    Chapter(storyId = "s.1", chapterNumber = 1, title = "Ch 1", url = "https://truyencom.com/url1"),
                    Chapter(storyId = "s.1", chapterNumber = 2, title = "Ch 2", url = "https://truyencom.com/url2"),
                )
            coEvery { source.getChapterContentWithTitle(any()) } returns ChapterContentResult("content")
            coEvery { chapterDao.saveChapterContent(any(), any(), any()) } just Runs

            val progress = downloadRepository.downloadChapters("s.1", chapters).toList()

            // Should emit: start(0), done(1), start(1), done(2) = 4 emissions
            assertTrue(progress.size >= 3)
            assertEquals(0, progress.first().current)
            assertEquals(2, progress.last().current)
        }

    @Test
    fun `downloadChapters continues on single chapter error`() =
        runTest {
            val chapters =
                listOf(
                    Chapter(storyId = "s.1", chapterNumber = 1, title = "Ch 1", url = "https://truyencom.com/url1"),
                    Chapter(storyId = "s.1", chapterNumber = 2, title = "Ch 2", url = "https://truyencom.com/url2"),
                )
            coEvery { source.getChapterContentWithTitle("https://truyencom.com/url1") } throws Exception("Error")
            coEvery { source.getChapterContentWithTitle("https://truyencom.com/url2") } returns ChapterContentResult("content")
            coEvery { chapterDao.saveChapterContent(any(), any(), any()) } just Runs

            val progress = downloadRepository.downloadChapters("s.1", chapters).toList()

            // Should still complete both chapters despite error on first
            assertEquals(2, progress.last().current)
        }

    // ----- Reading Progress (ReadingProgressRepository) -----

    @Test
    fun `getReadingProgress returns null for new story`() =
        runTest {
            coEvery { progressDao.getProgress("new-story") } returns null

            val result = readingProgressRepository.getReadingProgress("new-story")
            assertNull(result)
        }

    @Test
    fun `saveReadingProgress persists to DAO`() =
        runTest {
            val progress =
                com.personal.apptruyen.data.model.ReadingProgress(
                    storyId = "s.1",
                    lastChapterNumber = 5,
                    scrollPosition = 100,
                )
            coEvery { progressDao.saveProgress(any()) } just Runs

            readingProgressRepository.saveReadingProgress(progress)
            coVerify(exactly = 1) { progressDao.saveProgress(any()) }
        }

    // ----- Downloaded stories (DownloadRepository) -----

    @Test
    fun `getDownloadedStories returns flow from DAO`() =
        runTest {
            val entities =
                listOf(
                    StoryEntity(
                        id = "s.1",
                        slug = "s",
                        title = "Downloaded Story",
                        url = "https://truyencom.com/s.1/",
                    ),
                )
            every { storyDao.getDownloadedStories() } returns flowOf(entities)

            val stories = downloadRepository.getDownloadedStories().first()
            assertEquals(1, stories.size)
            assertEquals("Downloaded Story", stories[0].title)
        }

    @Test
    fun `deleteAllDownloads clears chapter content`() =
        runTest {
            coEvery { chapterDao.deleteAllChapterContent("s.1") } just Runs

            downloadRepository.deleteAllDownloads("s.1")
            coVerify(exactly = 1) { chapterDao.deleteAllChapterContent("s.1") }
        }

    @Test
    fun `searchStories marks login required when LoginRequiredException thrown`() =
        runTest {
            coEvery { source.search(any(), any()) } throws
                ScraperException.LoginRequiredException("truyencom", "Cần đăng nhập")

            val results = storyRepository.searchStories("test", false)
            assertTrue(results.isEmpty())
            verify { authRepository.markLoginRequired("truyencom") }
        }

    @Test
    fun `getChapterContent returns cached content for removed source`() =
        runTest {
            val cachedChapter =
                ChapterEntity(
                    storyId = "old.1",
                    chapterNumber = 1,
                    title = "Chương 1",
                    url = "https://unsupported.com/chap-1",
                    content = "Nội dung đã tải trước đó",
                    isDownloaded = true,
                )
            coEvery { chapterDao.getChapter("old.1", 1) } returns cachedChapter

            val result = storyRepository.getChapterContent("old.1", 1, "https://unsupported.com/chap-1")
            assertEquals("Nội dung đã tải trước đó", result.content)
            assertEquals("Chương 1", result.title)
        }

    @Test
    fun `getChapterContent throws ParseException for removed source when not cached`() =
        runTest {
            coEvery { chapterDao.getChapter("old.1", 1) } returns null

            val exception =
                assertThrows(ScraperException.ParseException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        storyRepository.getChapterContent("old.1", 1, "https://unsupported.com/chap-1")
                    }
                }
            assertTrue(exception.message?.contains("không còn hoạt động") == true)
        }

    @Test
    fun `sourceFor routes legacy sstruyen net url via public getStoryDetail`() =
        runTest {
            val ssSource = mockk<StorySource>()
            every { ssSource.sourceId } returns "sstruyen"
            every { ssSource.sourceName } returns "SsTruyen"
            every { ssSource.baseUrl } returns "https://sstruyen.pics"
            every { ssSource.legacyHosts } returns listOf("sstruyen.net")
            val expectedStory =
                Story(
                    id = "tien-nghich",
                    slug = "tien-nghich",
                    title = "Tiên Nghịch",
                    url = "https://sstruyen.net/truyen/tien-nghich",
                )
            coEvery { storyDao.getStoryById(any()) } returns null
            coEvery { ssSource.getStoryDetail("https://sstruyen.net/truyen/tien-nghich") } returns expectedStory

            val customRepo =
                StoryRepository(
                    setOf(ssSource),
                    storyDao,
                    chapterDao,
                    testScope,
                    dataStore,
                    ScraperCircuitBreaker(),
                    authRepository,
                )

            val result = customRepo.getStoryDetail("https://sstruyen.net/truyen/tien-nghich")
            assertEquals("Tiên Nghịch", result.title)
            assertEquals("tien-nghich", result.id)
            coVerify(exactly = 1) { ssSource.getStoryDetail("https://sstruyen.net/truyen/tien-nghich") }
        }

    @Test
    fun `mergeAndInsertChapters preserves title for downloaded chapters`() =
        runTest {
            val storyId = "tien-nghich"
            val storyUrl = "https://sstruyen.pics/truyen/tien-nghich"
            val ssSource = mockk<StorySource>()
            every { ssSource.sourceId } returns "sstruyen"
            every { ssSource.sourceName } returns "SsTruyen"
            every { ssSource.baseUrl } returns "https://sstruyen.pics"
            every { ssSource.legacyHosts } returns listOf("sstruyen.net")

            val downloadedChapterEntity =
                ChapterEntity(
                    storyId = storyId,
                    chapterNumber = 1,
                    title = "Chương 1: Ly hương",
                    url = "https://sstruyen.net/truyen/tien-nghich/chuong-1",
                    content = "Nội dung chương 1 cũ đã tải",
                    isDownloaded = true,
                )
            val notDownloadedChapterEntity =
                ChapterEntity(
                    storyId = storyId,
                    chapterNumber = 2,
                    title = "Chương 2: Cũ",
                    url = "https://sstruyen.net/truyen/tien-nghich/chuong-2",
                    content = null,
                    isDownloaded = false,
                )
            coEvery { chapterDao.getChaptersByStoryOnce(storyId) } returns
                listOf(downloadedChapterEntity, notDownloadedChapterEntity)

            val freshChapters =
                listOf(
                    Chapter(storyId, 1, "Chương 1: Đi xa", "https://sstruyen.pics/doc-truyen/tien-nghich-chuong-1-di-xa-104251"),
                    Chapter(storyId, 2, "Chương 2: Mới", "https://sstruyen.pics/doc-truyen/tien-nghich-chuong-2-moi-104252"),
                )
            coEvery { ssSource.getFirstPageChapters(storyUrl) } returns
                StorySource.FirstPageResult(chapters = freshChapters, totalPages = 1)

            val customRepo =
                StoryRepository(
                    setOf(ssSource),
                    storyDao,
                    chapterDao,
                    testScope,
                    dataStore,
                    ScraperCircuitBreaker(),
                    authRepository,
                )

            val result = customRepo.getFirstPageChapterList(storyUrl, storyId)
            assertEquals(2, result.first.size)

            coVerify {
                chapterDao.updateChapterMetadata(
                    storyId,
                    1,
                    "Chương 1: Ly hương",
                    "https://sstruyen.pics/doc-truyen/tien-nghich-chuong-1-di-xa-104251",
                )
            }
            coVerify {
                chapterDao.updateChapterMetadata(
                    storyId,
                    2,
                    "Chương 2: Mới",
                    "https://sstruyen.pics/doc-truyen/tien-nghich-chuong-2-moi-104252",
                )
            }
        }

    @Test
    fun `downloadRepository routes legacy sstruyen net url via snapshotStoryMetadata`() =
        runTest {
            val storyId = "pham-nhan"
            val legacyUrl = "https://sstruyen.net/truyen/pham-nhan-tu-tien"
            val ssSource = mockk<StorySource>()
            every { ssSource.sourceId } returns "sstruyen"
            every { ssSource.sourceName } returns "SsTruyen"
            every { ssSource.baseUrl } returns "https://sstruyen.pics"
            every { ssSource.legacyHosts } returns listOf("sstruyen.net")

            val storyEntity =
                StoryEntity(
                    id = storyId,
                    slug = "pham-nhan-tu-tien",
                    title = "Phàm Nhân Tu Tiên",
                    author = "Vong Ngữ",
                    genres = "Tiên Hiệp",
                    description = "Mô tả",
                    url = legacyUrl,
                    totalChapters = 2000,
                    coverImageUrl = "",
                    sourceId = "sstruyen",
                    status = "Hoàn thành",
                )
            coEvery { storyDao.getStoryById(storyId) } returns storyEntity
            val freshStory =
                Story(
                    id = storyId,
                    slug = "pham-nhan-tu-tien",
                    title = "Phàm Nhân Tu Tiên (Mới)",
                    author = "Vong Ngữ",
                    genres = listOf("Tiên Hiệp"),
                    description = "Mô tả mới",
                    url = "https://sstruyen.pics/truyen/pham-nhan-tu-tien",
                    totalChapters = 2446,
                    coverImageUrl = "",
                    sourceId = "sstruyen",
                    status = "Hoàn thành",
                )
            coEvery { ssSource.getStoryDetail(legacyUrl) } returns freshStory

            val dlRepo = DownloadRepository(setOf(ssSource), storyDao, chapterDao, progressDao, transactionRunner)
            dlRepo.snapshotStoryMetadata(storyId)

            coVerify(exactly = 1) { ssSource.getStoryDetail(legacyUrl) }
            coVerify { storyDao.insertStory(any()) }
        }

    @Test
    fun `downloadRepository downloadChapters throws ParseException for unsupported source`() =
        runTest {
            val storyId = "unsupported-story"
            val chapters = listOf(Chapter(storyId, 1, "Chương 1", "https://unsupported.com/chap-1"))

            val exception =
                assertThrows(ScraperException.ParseException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        downloadRepository.downloadChapters(storyId, chapters).toList()
                    }
                }
            assertTrue(exception.message?.contains("không được hỗ trợ") == true)
        }
}
