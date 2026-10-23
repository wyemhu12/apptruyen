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
import kotlinx.coroutines.CancellationException
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
        coEvery { transactionRunner.withWriteTransaction<Any?>(any()) } coAnswers {
            val block = firstArg<suspend () -> Any?>()
            block.invoke()
        }
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
                transactionRunner,
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

    @Test
    fun `getStoryDetail returns cached Wikidich story by prefixed ID when offline`() =
        runTest {
            val wikiSource = mockk<StorySource>()
            every { wikiSource.sourceId } returns "wikidich"
            every { wikiSource.sourceName } returns "Wikidich"
            every { wikiSource.baseUrl } returns "https://truyenwikidich.net"
            every { wikiSource.legacyHosts } returns listOf("wikidich.com")

            val repo =
                StoryRepository(
                    setOf(wikiSource),
                    storyDao,
                    chapterDao,
                    testScope,
                    dataStore,
                    ScraperCircuitBreaker(),
                    authRepository,
                    transactionRunner,
                )

            val cachedEntity =
                StoryEntity(
                    id = "wikidich_ta-co-mot-toa-kinh-hoang-nha-6789",
                    slug = "ta-co-mot-toa-kinh-hoang-nha-6789",
                    title = "Ta Có Một Tòa Kinh Hoàng Nhà",
                    author = "Ngoạn Hỏa",
                    url = "https://truyenwikidich.net/truyen/ta-co-mot-toa-kinh-hoang-nha-6789",
                    sourceId = "wikidich",
                    isInLibrary = true,
                )
            coEvery { storyDao.getStoryByUrl(any()) } returns null
            coEvery { storyDao.getStoryById("wikidich_ta-co-mot-toa-kinh-hoang-nha-6789") } returns cachedEntity

            val result = repo.getStoryDetail("https://truyenwikidich.net/truyen/ta-co-mot-toa-kinh-hoang-nha-6789")
            assertEquals("wikidich_ta-co-mot-toa-kinh-hoang-nha-6789", result.id)
            assertEquals("Ta Có Một Tòa Kinh Hoàng Nhà", result.title)
            coVerify(exactly = 0) { wikiSource.getStoryDetail(any()) }
        }

    @Test
    fun `getStoryDetail does not collide across sources sharing the same raw slug`() =
        runTest {
            val ttvSource = mockk<StorySource>()
            every { ttvSource.sourceId } returns "tangthuvien"
            every { ttvSource.sourceName } returns "Tàng Thư Viện"
            every { ttvSource.baseUrl } returns "https://truyen.tangthuvien.vn"
            every { ttvSource.legacyHosts } returns emptyList()

            val ssSource = mockk<StorySource>()
            every { ssSource.sourceId } returns "sstruyen"
            every { ssSource.sourceName } returns "SsTruyện"
            every { ssSource.baseUrl } returns "https://sstruyen.pics"
            every { ssSource.legacyHosts } returns emptyList()

            val repo =
                StoryRepository(
                    setOf(ttvSource, ssSource),
                    storyDao,
                    chapterDao,
                    testScope,
                    dataStore,
                    ScraperCircuitBreaker(),
                    authRepository,
                    transactionRunner,
                )

            // Legacy TangThuVien story cached with raw slug "pham-nhan-tu-tien"
            val legacyTtvEntity =
                StoryEntity(
                    id = "pham-nhan-tu-tien",
                    slug = "pham-nhan-tu-tien",
                    title = "Phàm Nhân Tu Tiên (TTV)",
                    url = "https://truyen.tangthuvien.vn/doc-truyen/pham-nhan-tu-tien",
                    sourceId = "tangthuvien",
                )
            coEvery { storyDao.getStoryByUrl(any()) } returns null
            coEvery { storyDao.getStoryById("sstruyen_pham-nhan-tu-tien") } returns null
            coEvery { storyDao.getStoryById("pham-nhan-tu-tien") } returns legacyTtvEntity

            val ssStory =
                Story(
                    id = "sstruyen_pham-nhan-tu-tien",
                    slug = "pham-nhan-tu-tien",
                    title = "Phàm Nhân Tu Tiên (SsTruyện)",
                    url = "https://sstruyen.pics/truyen/pham-nhan-tu-tien",
                    sourceId = "sstruyen",
                )
            coEvery { ssSource.getStoryDetail("https://sstruyen.pics/truyen/pham-nhan-tu-tien") } returns ssStory
            val insertedSlot = slot<StoryEntity>()
            coEvery { storyDao.insertStory(capture(insertedSlot)) } just Runs

            // Opening SsTruyen URL with same slug must NOT return TTV's cached entity or overwrite it
            val result = repo.getStoryDetail("https://sstruyen.pics/truyen/pham-nhan-tu-tien")
            assertEquals("sstruyen_pham-nhan-tu-tien", result.id)
            assertEquals("sstruyen", result.sourceId)
            assertEquals("sstruyen_pham-nhan-tu-tien", insertedSlot.captured.id)
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
            coEvery { chapterDao.saveChapterContent(any(), any(), any(), any(), any(), any()) } just Runs

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
            coEvery { chapterDao.saveChapterContent(any(), any(), any(), any(), any(), any()) } just Runs

            val progress = downloadRepository.downloadChapters("s.1", chapters).toList()

            // Should complete both chapters with 1 downloaded, 1 error, 2 processed
            assertEquals(1, progress.last().current)
            assertEquals(1, progress.last().errors)
            assertEquals(2, progress.last().processed)
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
                    transactionRunner,
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
                    transactionRunner,
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

    @Test
    fun `mergeAndInsertChapters runs in writeTransaction and skips updateChapterMetadata for unchanged chapters or blank new url`() =
        runTest {
            val storyId = "test.1"
            val storyUrl = "https://truyencom.com/test.1/"
            val existingChapters =
                listOf(
                    ChapterEntity(
                        storyId = storyId,
                        chapterNumber = 1,
                        title = "Chương 1",
                        url = "https://truyencom.com/test.1/c1",
                        isDownloaded = false,
                    ),
                    ChapterEntity(
                        storyId = storyId,
                        chapterNumber = 2,
                        title = "Chương 2",
                        url = "https://truyencom.com/test.1/c2",
                        isDownloaded = true,
                    ),
                )
            coEvery { chapterDao.getChaptersByStoryOnce(storyId) } returns existingChapters
            coEvery { source.getFirstPageChapters(storyUrl) } returns
                StorySource.FirstPageResult(
                    chapters =
                        listOf(
                            // Unchanged title and url -> must skip updateChapterMetadata
                            Chapter(storyId, 1, "Chương 1", "https://truyencom.com/test.1/c1"),
                            // Blank new url -> must retain existing url and skip updateChapterMetadata
                            Chapter(storyId, 2, "Chương 2 Mới", ""),
                            // New chapter -> must be inserted via insertChaptersIgnore
                            Chapter(storyId, 3, "Chương 3", "https://truyencom.com/test.1/c3"),
                        ),
                    totalPages = 1,
                )

            storyRepository.getFirstPageChapterList(storyUrl, storyId)

            coVerify(atLeast = 1) { transactionRunner.withWriteTransaction<Any?>(any()) }
            coVerify(exactly = 1) { chapterDao.insertChaptersIgnore(any()) }
            coVerify(exactly = 0) { chapterDao.updateChapterMetadata(any(), any(), any(), any()) }
        }

    @Test
    fun `resolveSafeCoverUrl and resolveSafeTotalChapters filter base64 data urls and preserve positive chapter counts`() {
        assertEquals(
            "https://example.com/old.jpg",
            resolveSafeCoverUrl(
                freshCoverUrl = "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
                existingCoverUrl = "https://example.com/old.jpg",
            ),
        )
        assertEquals(
            "",
            resolveSafeCoverUrl(
                freshCoverUrl = "data:image/png;base64,abc",
                existingCoverUrl = "data:image/png;base64,old",
            ),
        )
        assertEquals(
            "https://example.com/new.jpg",
            resolveSafeCoverUrl(
                freshCoverUrl = "https://example.com/new.jpg",
                existingCoverUrl = "https://example.com/old.jpg",
            ),
        )

        assertEquals(120, resolveSafeTotalChapters(freshTotal = 0, existingTotal = 120))
        assertEquals(150, resolveSafeTotalChapters(freshTotal = 150, existingTotal = 120))
        assertEquals(0, resolveSafeTotalChapters(freshTotal = -1, existingTotal = 0))
    }

    @Test
    fun `refreshLibraryStories guards zero totalChapters and base64 coverImageUrl`() =
        runTest {
            val libStory =
                StoryEntity(
                    id = "test.1",
                    slug = "test",
                    title = "Truyện Thư Viện",
                    author = "Tác giả",
                    genres = "Tiên Hiệp",
                    description = "Mô tả",
                    url = "https://truyencom.com/test.1/",
                    totalChapters = 300,
                    coverImageUrl = "https://truyencom.com/cover.jpg",
                    isInLibrary = true,
                    sourceId = "truyencom",
                    status = "Đang ra",
                    storyReplacementsEnabled = true,
                    storyCustomReplacements = "x=y",
                )
            coEvery { storyDao.getLibraryStoriesOnce() } returns listOf(libStory)
            coEvery { source.getStoryDetail("https://truyencom.com/test.1/") } returns
                Story(
                    id = "test.1",
                    slug = "test",
                    title = "Truyện Thư Viện",
                    author = "Tác giả",
                    genres = listOf("Tiên Hiệp"),
                    description = "Mô tả mới",
                    url = "https://truyencom.com/test.1/",
                    totalChapters = 0,
                    coverImageUrl = "data:image/png;base64,AAA",
                    sourceId = "truyencom",
                    status = "Đang ra",
                )
            val savedSlot = slot<StoryEntity>()
            coEvery { storyDao.insertStory(capture(savedSlot)) } just Runs

            storyRepository.refreshLibraryStories()

            assertEquals(300, savedSlot.captured.totalChapters)
            assertEquals("https://truyencom.com/cover.jpg", savedSlot.captured.coverImageUrl)
            assertTrue(savedSlot.captured.storyReplacementsEnabled)
            assertEquals("x=y", savedSlot.captured.storyCustomReplacements)
        }

    @Test
    fun `getChapterList and getFirstPageChapterList rethrow CancellationException instead of falling back to cache`() =
        runTest {
            coEvery { source.getChapterList(any()) } throws CancellationException("cancelled")
            coEvery { source.getFirstPageChapters(any()) } throws CancellationException("cancelled")

            assertThrows(CancellationException::class.java) {
                kotlinx.coroutines.runBlocking {
                    storyRepository.getChapterList("https://truyencom.com/test.1/", "test.1")
                }
            }
            assertThrows(CancellationException::class.java) {
                kotlinx.coroutines.runBlocking {
                    storyRepository.getFirstPageChapterList("https://truyencom.com/test.1/", "test.1")
                }
            }
        }

    @Test
    fun `upgradeToHttps and toDomain upgrade http covers across StoryRepository DownloadRepository and ReadingProgressRepository`() =
        runTest {
            assertEquals("https://example.com/a.jpg", upgradeToHttps("http://example.com/a.jpg"))
            assertEquals("https://example.com/new.jpg", resolveSafeCoverUrl("http://example.com/new.jpg", null))
            assertEquals("https://example.com/old.jpg", resolveSafeCoverUrl("data:image/png;base64,AAA", "http://example.com/old.jpg"))
            assertEquals("http://localhost:8080/local.jpg", resolveSafeCoverUrl("http://localhost:8080/local.jpg", null))

            val cachedEntity =
                StoryEntity(
                    id = "test.http",
                    slug = "test.http",
                    title = "Test HTTP Cover",
                    author = "Author",
                    url = "https://truyencom.com/test.http/",
                    coverImageUrl = "http://example.com/cached.jpg",
                    sourceId = "truyencom",
                )
            coEvery { storyDao.getStoryById("test.http") } returns cachedEntity
            every { storyDao.getDownloadedStories() } returns
                flowOf(listOf(cachedEntity.copy(coverImageUrl = "http://example.com/dl.jpg")))
            every { storyDao.getRecentlyReadStories() } returns
                flowOf(listOf(cachedEntity.copy(coverImageUrl = "http://example.com/recent.jpg")))

            val detail = storyRepository.getStoryDetail("https://truyencom.com/test.http/")
            val downloaded = downloadRepository.getDownloadedStories().first()
            val recent = readingProgressRepository.getRecentlyReadStories().first()

            assertEquals("https://example.com/cached.jpg", detail.coverImageUrl)
            assertEquals("https://example.com/dl.jpg", downloaded.single().coverImageUrl)
            assertEquals("https://example.com/recent.jpg", recent.single().coverImageUrl)
        }

    @Test
    fun `getStoryDetail heals zero totalChapters from chapterDao and preserves higher totalChapters when fresh is lower`() =
        runTest {
            val cachedZeroEntity =
                StoryEntity(
                    id = "truyencom_story-1",
                    slug = "story-1",
                    title = "Truyện 1",
                    author = "Tác giả",
                    url = "https://truyencom.com/story-1/",
                    totalChapters = 0,
                    sourceId = "truyencom",
                )
            coEvery { storyDao.getStoryByUrl("https://truyencom.com/story-1/") } returns cachedZeroEntity
            coEvery { chapterDao.getChapterCount("truyencom_story-1") } returns 150

            val healedStory = storyRepository.getStoryDetail("https://truyencom.com/story-1/")
            assertEquals(150, healedStory.totalChapters)
            coVerify { storyDao.updateTotalChaptersIfGreater("truyencom_story-1", 150) }

            // Nhánh không có cache URL nhưng có existingEntity = 485, trong khi remote trả về 148 (đánh số lại theo quyển)
            val existingHighEntity =
                StoryEntity(
                    id = "truyencom_story-2",
                    slug = "story-2",
                    title = "Truyện 2",
                    author = "Tác giả",
                    url = "https://truyencom.com/story-2/",
                    totalChapters = 485,
                    sourceId = "truyencom",
                )
            coEvery { storyDao.getStoryByUrl("https://truyencom.com/story-2/") } returns null
            coEvery { storyDao.getStoryById("truyencom_story-2") } returnsMany listOf(null, existingHighEntity)
            coEvery { chapterDao.getChapterCount("truyencom_story-2") } returns 485
            coEvery { source.getStoryDetail("https://truyencom.com/story-2/") } returns
                Story(
                    id = "truyencom_story-2",
                    slug = "story-2",
                    title = "Truyện 2",
                    author = "Tác giả",
                    url = "https://truyencom.com/story-2/",
                    totalChapters = 148,
                    sourceId = "truyencom",
                )

            val preservedStory = storyRepository.getStoryDetail("https://truyencom.com/story-2/")
            assertEquals(485, preservedStory.totalChapters)
            coVerify { storyDao.updateTotalChaptersIfGreater("truyencom_story-2", 485) }
        }

    @Test
    fun `getFirstPageChapterList updates story totalChapters in DB via updateTotalChaptersIfGreater`() =
        runTest {
            val storyId = "wikidich_sample"
            val storyUrl = "https://truyencom.com/sample/"
            val chapters =
                (1..99).map { num ->
                    Chapter(storyId = storyId, chapterNumber = num, title = "Chương $num", url = "https://truyencom.com/sample/c-$num")
                }
            coEvery { chapterDao.getChaptersByStoryOnce(storyId) } returns emptyList()
            coEvery { chapterDao.getChapterCount(storyId) } returns 99
            coEvery { source.getFirstPageChapters(storyUrl) } returns
                StorySource.FirstPageResult(chapters = chapters, totalPages = 5)

            val result = storyRepository.getFirstPageChapterList(storyUrl, storyId)
            assertEquals(99, result.first.size)
            assertEquals(5, result.second)
            coVerify { storyDao.updateTotalChaptersIfGreater(storyId, 99) }
        }

    @Test
    fun `getChapterList updates story totalChapters to highest chapter count when full index is loaded`() =
        runTest {
            val storyId = "wikidich_full"
            val storyUrl = "https://truyencom.com/full/"
            val chapters =
                (1..350).map { num ->
                    Chapter(storyId = storyId, chapterNumber = num, title = "Chương $num", url = "https://truyencom.com/full/c-$num")
                }
            coEvery { chapterDao.getChaptersByStoryOnce(storyId) } returns emptyList()
            coEvery { chapterDao.getChapterCount(storyId) } returns 350
            coEvery { source.getChapterList(storyUrl) } returns chapters

            val result = storyRepository.getChapterList(storyUrl, storyId)
            assertEquals(350, result.size)
            coVerify { storyDao.updateTotalChaptersIfGreater(storyId, 350) }
        }

    @Test
    fun `refreshLibraryStories preserves monotonic totalChapters using existing and chapterDao count`() =
        runTest {
            val libStory =
                StoryEntity(
                    id = "wikidich_vol",
                    slug = "vol",
                    title = "Truyện Nhiều Quyển",
                    author = "Tác giả",
                    url = "https://truyencom.com/vol/",
                    totalChapters = 485,
                    isInLibrary = true,
                    sourceId = "truyencom",
                )
            coEvery { storyDao.getLibraryStoriesOnce() } returns listOf(libStory)
            coEvery { storyDao.getStoryById("wikidich_vol") } returns libStory
            coEvery { chapterDao.getChapterCount("wikidich_vol") } returns 485
            coEvery { source.getStoryDetail("https://truyencom.com/vol/") } returns
                Story(
                    id = "wikidich_vol",
                    slug = "vol",
                    title = "Truyện Nhiều Quyển",
                    author = "Tác giả",
                    url = "https://truyencom.com/vol/",
                    totalChapters = 148,
                    sourceId = "truyencom",
                )
            val savedSlot = slot<StoryEntity>()
            coEvery { storyDao.insertStory(capture(savedSlot)) } just Runs

            storyRepository.refreshLibraryStories()

            assertEquals(485, savedSlot.captured.totalChapters)
            coVerify { storyDao.updateTotalChaptersIfGreater("wikidich_vol", 485) }
        }
}
