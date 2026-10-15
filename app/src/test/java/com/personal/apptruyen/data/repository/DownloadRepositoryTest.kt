package com.personal.apptruyen.data.repository

import android.util.Log
import com.personal.apptruyen.data.local.ChapterDao
import com.personal.apptruyen.data.local.DatabaseTransactionRunner
import com.personal.apptruyen.data.local.ReadingProgressDao
import com.personal.apptruyen.data.local.StoryDao
import com.personal.apptruyen.data.local.entity.ChapterEntity
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.ChapterContentResult
import com.personal.apptruyen.data.remote.ScraperException
import com.personal.apptruyen.data.remote.StorySource
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepositoryTest {

    private lateinit var storyDao: StoryDao
    private lateinit var chapterDao: ChapterDao
    private lateinit var progressDao: ReadingProgressDao
    private lateinit var transactionRunner: DatabaseTransactionRunner
    private lateinit var source: StorySource
    private lateinit var downloadRepository: DownloadRepository
    private val persistedChapters = mutableListOf<Triple<String, Int, String>>()

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        persistedChapters.clear()
        storyDao = mockk(relaxed = true)
        chapterDao = mockk(relaxed = true)
        progressDao = mockk(relaxed = true)
        transactionRunner = mockk()
        source = mockk(relaxed = true)
        every { source.baseUrl } returns "https://sstruyen.pics"

        coEvery { chapterDao.saveChapterContent(any(), any(), any(), any()) } answers {
            persistedChapters.add(Triple(firstArg(), secondArg(), thirdArg()))
        }

        coEvery { transactionRunner.withWriteTransaction<Any?>(any()) } coAnswers {
            val block = firstArg<suspend () -> Any?>()
            block.invoke()
        }

        downloadRepository =
            DownloadRepository(
                sources = setOf(source),
                storyDao = storyDao,
                chapterDao = chapterDao,
                progressDao = progressDao,
                transactionRunner = transactionRunner,
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `deleteStory executes progress chapter and story deletions atomically in order inside withWriteTransaction`() =
        runTest {
            val storyId = "tien-nghich"
            coEvery { progressDao.deleteProgress(storyId) } just Runs
            coEvery { chapterDao.deleteChaptersByStory(storyId) } just Runs
            coEvery { storyDao.deleteStory(storyId) } just Runs

            downloadRepository.deleteStory(storyId)

            coVerify(exactly = 1) { transactionRunner.withWriteTransaction<Unit>(any()) }
            coVerifyOrder {
                progressDao.deleteProgress(storyId)
                chapterDao.deleteChaptersByStory(storyId)
                storyDao.deleteStory(storyId)
            }
        }

    @Test
    fun `deleteStory rethrows DAO exception and aborts subsequent deletions in transaction`() =
        runTest {
            val storyId = "pham-nhan"
            coEvery { progressDao.deleteProgress(storyId) } just Runs
            coEvery { chapterDao.deleteChaptersByStory(storyId) } throws IllegalStateException("Simulated SQLite failure")

            val thrown =
                assertThrows(IllegalStateException::class.java) {
                    kotlinx.coroutines.runBlocking {
                        downloadRepository.deleteStory(storyId)
                    }
                }

            assertEquals("Simulated SQLite failure", thrown.message)
            coVerify(exactly = 1) { transactionRunner.withWriteTransaction<Unit>(any()) }
            coVerify(exactly = 1) { progressDao.deleteProgress(storyId) }
            coVerify(exactly = 1) { chapterDao.deleteChaptersByStory(storyId) }
            coVerify(exactly = 0) { storyDao.deleteStory(storyId) }
        }

    @Test
    fun `downloadChapters executes batch in parallel and persists in ascending chapterNumber order when network finishes out of order`() =
        runTest {
            val networkCompletionOrder = Collections.synchronizedList(mutableListOf<Int>())
            val chapters =
                listOf(
                    Chapter("s1", 3, "Chương 3", "https://sstruyen.pics/c3"),
                    Chapter("s1", 1, "Chương 1", "https://sstruyen.pics/c1"),
                    Chapter("s1", 4, "Chương 4", "https://sstruyen.pics/c4"),
                    Chapter("s1", 2, "Chương 2", "https://sstruyen.pics/c2"),
                )

            coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c1") } coAnswers {
                delay(80L)
                networkCompletionOrder.add(1)
                ChapterContentResult("Nội dung 1", "Chương 1")
            }
            coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c2") } coAnswers {
                delay(60L)
                networkCompletionOrder.add(2)
                ChapterContentResult("Nội dung 2", "Chương 2")
            }
            coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c3") } coAnswers {
                delay(40L)
                networkCompletionOrder.add(3)
                ChapterContentResult("Nội dung 3", "Chương 3")
            }
            coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c4") } coAnswers {
                delay(10L)
                networkCompletionOrder.add(4)
                ChapterContentResult("Nội dung 4", "Chương 4")
            }

            val emissions = downloadRepository.downloadChapters("s1", chapters).toList()

            assertEquals(80L, testScheduler.currentTime)
            assertEquals(listOf(4, 3, 2, 1), networkCompletionOrder)
            assertEquals(
                listOf(
                    Triple("s1", 1, "Nội dung 1"),
                    Triple("s1", 2, "Nội dung 2"),
                    Triple("s1", 3, "Nội dung 3"),
                    Triple("s1", 4, "Nội dung 4"),
                ),
                persistedChapters,
            )

            val completedTitles = emissions.drop(1).map { it.currentChapterTitle }
            assertEquals(listOf("Chương 1", "Chương 2", "Chương 3", "Chương 4"), completedTitles)
            assertEquals(4, emissions.last().current)
            assertEquals(0, emissions.last().errors)
        }

    @Test
    fun `downloadChapters bounds concurrent requests to DOWNLOAD_BATCH_SIZE and updates dummy titles across batches`() =
        runTest {
            val inFlight = AtomicInteger(0)
            val maxInFlight = AtomicInteger(0)

            val chapters =
                (1..10).map { num ->
                    val title = if (num == 2) "Chương 2: Đã có tên" else "Chương $num"
                    Chapter("s1", num, title, "https://sstruyen.pics/c$num")
                }

            for (num in 1..10) {
                coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c$num") } coAnswers {
                    val current = inFlight.incrementAndGet()
                    maxInFlight.updateAndGet { maxOf(it, current) }
                    delay(50L)
                    inFlight.decrementAndGet()
                    ChapterContentResult("Nội dung $num", "Chương $num: Tên thực")
                }
            }

            val emissions = downloadRepository.downloadChapters("s1", chapters).toList()

            assertEquals(DownloadRepository.DOWNLOAD_BATCH_SIZE, maxInFlight.get())
            assertEquals(10, emissions.last().current)
            assertEquals(0, emissions.last().errors)

            coVerify(exactly = 1) {
                chapterDao.updateChapterMetadata("s1", 1, "Chương 1: Tên thực", "https://sstruyen.pics/c1")
            }
            coVerify(exactly = 0) {
                chapterDao.updateChapterMetadata("s1", 2, any(), any())
            }
        }

    @Test
    fun `downloadChapters retries failed chapter and doubles inter-batch delay on batch error`() =
        runTest {
            val chapters =
                (1..8).map { num ->
                    Chapter("s1", num, "Chương $num", "https://sstruyen.pics/c$num")
                }

            var ch2Attempts = 0
            coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c1") } returns ChapterContentResult("ND 1", null)
            coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c2") } coAnswers {
                ch2Attempts++
                if (ch2Attempts == 1) throw ScraperException.NetworkException("Temporary timeout")
                ChapterContentResult("ND 2", null)
            }
            coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c3") } throws
                ScraperException.NetworkException("Permanent failure")
            coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c4") } returns ChapterContentResult("ND 4", null)
            for (num in 5..8) {
                coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c$num") } returns
                    ChapterContentResult("ND $num", null)
            }

            val emissions = downloadRepository.downloadChapters("s1", chapters).toList()

            assertEquals(750L, testScheduler.currentTime)
            assertEquals(8, emissions.last().current)
            assertEquals(1, emissions.last().errors)

            assertEquals(
                listOf(
                    Triple("s1", 1, "ND 1"),
                    Triple("s1", 2, "ND 2"),
                    Triple("s1", 4, "ND 4"),
                    Triple("s1", 5, "ND 5"),
                    Triple("s1", 6, "ND 6"),
                    Triple("s1", 7, "ND 7"),
                    Triple("s1", 8, "ND 8"),
                ),
                persistedChapters,
            )
            coVerify(exactly = 0) { chapterDao.saveChapterContent("s1", 3, any(), any()) }
        }

    @Test
    fun `downloadChapters rethrows CancellationException immediately without retrying`() =
        runTest {
            val chapters = listOf(Chapter("s1", 1, "Chương 1", "https://sstruyen.pics/c1"))
            coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c1") } throws CancellationException("User cancelled")

            var caught: CancellationException? = null
            try {
                downloadRepository.downloadChapters("s1", chapters).toList()
            } catch (e: CancellationException) {
                caught = e
            }

            assertTrue(caught != null)
            coVerify(exactly = 1) { source.getChapterContentWithTitle("https://sstruyen.pics/c1") }
            coVerify(exactly = 0) { chapterDao.saveChapterContent(any(), any(), any(), any()) }
        }

    @Test
    fun `downloadChapters stops before DB write when job is cancelled while scraper wraps CancellationException into ParseException`() =
        runTest {
            val chapters = listOf(Chapter("s1", 1, "Chương 1", "https://sstruyen.pics/c1"))
            coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c1") } coAnswers {
                try {
                    delay(100L)
                    ChapterContentResult("ND 1", null)
                } catch (e: Exception) {
                    throw ScraperException.ParseException("Wrapped: ${e.message}", "https://sstruyen.pics/c1")
                }
            }

            val job =
                launch {
                    downloadRepository.downloadChapters("s1", chapters).collect {}
                }

            advanceTimeBy(50L)
            job.cancel()
            advanceUntilIdle()

            coVerify(exactly = 0) { chapterDao.saveChapterContent(any(), any(), any(), any()) }
        }

    @Test
    fun `downloadChapters fails fast without retry on LoginRequiredException after saving successful chapters in current batch`() =
        runTest {
            val chapters =
                (1..8).map { num ->
                    Chapter("s1", num, "Chương $num", "https://sstruyen.pics/c$num")
                }

            coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c1") } returns ChapterContentResult("Nội dung 1", null)
            for (num in 2..4) {
                coEvery { source.getChapterContentWithTitle("https://sstruyen.pics/c$num") } throws
                    ScraperException.LoginRequiredException("wikidich")
            }

            var caughtLoginRequired: ScraperException.LoginRequiredException? = null
            try {
                downloadRepository.downloadChapters("s1", chapters).toList()
            } catch (e: ScraperException.LoginRequiredException) {
                caughtLoginRequired = e
            }

            assertTrue(caughtLoginRequired != null)
            assertEquals("wikidich", caughtLoginRequired?.sourceId)

            for (num in 2..4) {
                coVerify(exactly = 1) { source.getChapterContentWithTitle("https://sstruyen.pics/c$num") }
            }
            assertEquals(listOf(Triple("s1", 1, "Nội dung 1")), persistedChapters)
            coVerify(exactly = 1) { chapterDao.saveChapterContent(any(), any(), any(), any()) }
            for (num in 5..8) {
                coVerify(exactly = 0) { source.getChapterContentWithTitle("https://sstruyen.pics/c$num") }
            }
        }

    @Test
    fun `downloadChapters aborts early after MAX_CONSECUTIVE_FAILED_BATCHES completely failed batches`() =
        runTest {
            val chapters =
                (1..16).map { num ->
                    Chapter("s1", num, "Chương $num", "https://sstruyen.pics/c$num")
                }
            coEvery { source.getChapterContentWithTitle(any()) } throws ScraperException.HttpException(429, "https://sstruyen.pics")

            val emissions = downloadRepository.downloadChapters("s1", chapters).toList()

            assertEquals(12, emissions.last().current)
            assertEquals(12, emissions.last().errors)
            for (num in 13..16) {
                coVerify(exactly = 0) { source.getChapterContentWithTitle("https://sstruyen.pics/c$num") }
            }
        }

    @Test
    fun `getChaptersByNumbers chunks large chapter lists and returns sorted chapters`() =
        runTest {
            val inputNumbers = (1200 downTo 1).toList()
            coEvery { chapterDao.getChaptersByNumbers("s1", any()) } answers {
                val nums = secondArg<List<Int>>()
                nums.map { n ->
                    ChapterEntity(
                        storyId = "s1",
                        chapterNumber = n,
                        title = "Chương $n",
                        url = "https://sstruyen.pics/c$n",
                    )
                }
            }

            val result = downloadRepository.getChaptersByNumbers("s1", inputNumbers)

            coVerify(exactly = 2) { chapterDao.getChaptersByNumbers("s1", any()) }
            assertEquals(1200, result.size)
            assertEquals((1..1200).toList(), result.map { it.chapterNumber })
        }
}
