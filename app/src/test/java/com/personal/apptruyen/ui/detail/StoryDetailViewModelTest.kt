package com.personal.apptruyen.ui.detail

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.personal.apptruyen.data.local.StoryReplacementData
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.ReadingProgress
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.DownloadProgress
import com.personal.apptruyen.data.repository.IReadingProgressRepository
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.ITextReplacementRepository
import com.personal.apptruyen.download.DownloadService
import com.personal.apptruyen.ui.navigation.Screen
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for StoryDetailViewModel data classes and state.
 * Full VM integration tests require Android instrumented testing
 * due to Application + Service dependencies.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryDetailViewModelTest {

    @Test
    fun `DetailState default has correct values`() {
        val state = StoryDetailViewModel.DetailState()
        assertTrue(state.isLoading)
        assertNull(state.story)
        assertTrue(state.chapters.isEmpty())
        assertTrue(state.displayedChapters.isEmpty())
        assertEquals(0, state.totalChapterCount)
        assertFalse(state.canLoadMoreChapters)
        assertNull(state.readingProgress)
        assertNull(state.error)
        assertFalse(state.isDownloading)
        assertNull(state.downloadProgress)
        assertFalse(state.storyReplacementsEnabled)
        assertTrue(state.storyReplacements.isEmpty())
        assertFalse(state.showStorySettings)
    }

    @Test
    fun `DetailState copy updates correctly`() {
        val story =
            Story(
                id = "test.1",
                slug = "test",
                title = "Test",
                author = "Author",
                totalChapters = 10,
                url = "https://example.com",
                coverImageUrl = "",
            )
        val state =
            StoryDetailViewModel.DetailState(
                story = story,
                isLoading = false,
                totalChapterCount = 50,
            )
        assertEquals("Test", state.story?.title)
        assertFalse(state.isLoading)
        assertEquals(50, state.totalChapterCount)
    }

    @Test
    fun `DownloadProgress data class works`() {
        val progress =
            DownloadProgress(
                current = 5,
                total = 10,
                currentChapterTitle = "Chương 5",
                errors = 1,
            )
        assertEquals(5, progress.current)
        assertEquals(10, progress.total)
        assertEquals("Chương 5", progress.currentChapterTitle)
        assertEquals(1, progress.errors)
    }

    @Test
    fun `lastReadChapter reflects readingProgress even when chapter is not in first page chapters`() {
        val stateWithoutProgress = StoryDetailViewModel.DetailState()
        assertNull(stateWithoutProgress.lastReadChapter)

        val stateWithZero =
            StoryDetailViewModel.DetailState(
                readingProgress = ReadingProgress(storyId = "s1", lastChapterNumber = 0),
            )
        assertNull(stateWithZero.lastReadChapter)

        val stateWithBeyondFirstPage =
            StoryDetailViewModel.DetailState(
                readingProgress = ReadingProgress(storyId = "s1", lastChapterNumber = 150, scrollPosition = 8),
            )
        assertEquals(150, stateWithBeyondFirstPage.lastReadChapter)
    }

    @Test
    fun `resolveChapterUrlForReading returns exact chapter url or constructs fallback url safely`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            mockkObject(Screen.StoryDetail)
            try {
                every { Screen.StoryDetail.decodeUrl(any()) } answers { firstArg() }
                val repo = mockk<IStoryRepository>(relaxed = true)
                val textRepo = mockk<ITextReplacementRepository>(relaxed = true)
                val progressRepo = mockk<IReadingProgressRepository>(relaxed = true)
                val notifHelper = mockk<DownloadNotificationHelper>(relaxed = true)
                val app = mockk<Application>(relaxed = true)

                val sampleStory =
                    Story(
                        id = "sstruyen_abc",
                        slug = "abc",
                        title = "Truyện Test",
                        url = "https://sstruyen.pics/truyen/abc",
                        totalChapters = 200,
                    )
                val page1Chapters =
                    listOf(
                        Chapter(storyId = "sstruyen_abc", chapterNumber = 1, title = "Chương 1", url = "https://sstruyen.pics/truyen/abc/chuong-1"),
                        Chapter(storyId = "sstruyen_abc", chapterNumber = 2, title = "Chương 2", url = "https://sstruyen.pics/truyen/abc/chuong-2"),
                    )
                coEvery { repo.getStoryDetail(any()) } returns sampleStory
                coEvery { repo.getFirstPageChapterList(any(), any()) } returns (page1Chapters to 1)
                coEvery { progressRepo.getReadingProgress(any()) } returns ReadingProgress(storyId = "sstruyen_abc", lastChapterNumber = 150)
                coEvery { repo.getChapterCount(any()) } returns 200
                coEvery { repo.getChaptersPaged(any(), any(), any()) } returns page1Chapters
                every { repo.observeChapters(any()) } returns emptyFlow()
                every { progressRepo.observeReadingProgress(any()) } returns emptyFlow()
                every { repo.observeStory(any()) } returns emptyFlow()

                val vm =
                    StoryDetailViewModel(
                        savedStateHandle = SavedStateHandle(mapOf("storyId" to "sstruyen_abc", "storyUrl" to sampleStory.url)),
                        repository = repo,
                        textReplacementRepository = textRepo,
                        readingProgressRepository = progressRepo,
                        notificationHelper = notifHelper,
                        application = app,
                    )
                advanceUntilIdle()

                assertEquals("https://sstruyen.pics/truyen/abc/chuong-2", vm.resolveChapterUrlForReading(2))
                assertEquals("https://sstruyen.pics/truyen/abc/chuong-150", vm.resolveChapterUrlForReading(150))
            } finally {
                unmockkObject(Screen.StoryDetail)
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `downloadRange filters chapters within bounds and ignores empty range when chapters list is empty`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            mockkObject(Screen.StoryDetail)
            mockkObject(DownloadService.Companion)
            try {
                every { Screen.StoryDetail.decodeUrl(any()) } answers { firstArg() }
                every { DownloadService.startDownload(any(), any(), any(), any()) } returns true

                val repo = mockk<IStoryRepository>(relaxed = true)
                val textRepo = mockk<ITextReplacementRepository>(relaxed = true)
                val progressRepo = mockk<IReadingProgressRepository>(relaxed = true)
                val notifHelper = mockk<DownloadNotificationHelper>(relaxed = true)
                val app = mockk<Application>(relaxed = true)

                val sampleStory =
                    Story(
                        id = "s1",
                        slug = "s1",
                        title = "Truyện 1",
                        url = "https://sstruyen.pics/truyen/s1",
                        totalChapters = 3,
                    )
                val chapters =
                    listOf(
                        Chapter(storyId = "s1", chapterNumber = 1, title = "Chương 1", url = "u1", isDownloaded = true),
                        Chapter(storyId = "s1", chapterNumber = 2, title = "Chương 2", url = "u2", isDownloaded = false),
                        Chapter(storyId = "s1", chapterNumber = 3, title = "Chương 3", url = "u3", isDownloaded = false),
                    )
                coEvery { repo.getStoryDetail(any()) } returns sampleStory
                coEvery { repo.getFirstPageChapterList(any(), any()) } returns (chapters to 1)
                coEvery { repo.getChapterCount(any()) } returns 3
                coEvery { repo.getChaptersPaged(any(), any(), any()) } returns chapters
                every { repo.observeChapters(any()) } returns emptyFlow()
                every { progressRepo.observeReadingProgress(any()) } returns emptyFlow()
                every { repo.observeStory(any()) } returns emptyFlow()

                val vm =
                    StoryDetailViewModel(
                        savedStateHandle = SavedStateHandle(mapOf("storyId" to "s1", "storyUrl" to sampleStory.url)),
                        repository = repo,
                        textReplacementRepository = textRepo,
                        readingProgressRepository = progressRepo,
                        notificationHelper = notifHelper,
                        application = app,
                    )
                advanceUntilIdle()

                // Range 1..1 only has already-downloaded chapter 1 -> should not trigger startDownload
                vm.downloadRange(1, 1)
                verify(exactly = 0) { DownloadService.startDownload(any(), any(), any(), any()) }

                // Range 1..3 should filter out downloaded chapter 1 and start download for [2, 3]
                vm.downloadRange(1, 3)
                advanceUntilIdle()
                coVerify { repo.addToLibrary("s1") }
                verify(exactly = 1) {
                    DownloadService.startDownload(
                        context = app,
                        storyId = "s1",
                        storyTitle = "Truyện 1",
                        chapterNumbers = match { it.contentEquals(intArrayOf(2, 3)) },
                    )
                }
            } finally {
                unmockkObject(DownloadService.Companion)
                unmockkObject(Screen.StoryDetail)
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `loadMoreChapters appends next paged batch and updates canLoadMoreChapters`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            mockkObject(Screen.StoryDetail)
            try {
                every { Screen.StoryDetail.decodeUrl(any()) } answers { firstArg() }
                val repo = mockk<IStoryRepository>(relaxed = true)
                val textRepo = mockk<ITextReplacementRepository>(relaxed = true)
                val progressRepo = mockk<IReadingProgressRepository>(relaxed = true)
                val notifHelper = mockk<DownloadNotificationHelper>(relaxed = true)
                val app = mockk<Application>(relaxed = true)

                val sampleStory = Story(id = "s1", slug = "s1", title = "Truyện 1", url = "https://sstruyen.pics/truyen/s1", totalChapters = 150)
                val page1 = (1..100).map { Chapter("s1", it, "Chương $it", "u$it") }
                val page2 = (101..150).map { Chapter("s1", it, "Chương $it", "u$it") }
                coEvery { repo.getStoryDetail(any()) } returns sampleStory
                coEvery { repo.getFirstPageChapterList(any(), any()) } returns (page1 to 1)
                coEvery { repo.getChapterCount("s1") } returns 150
                coEvery { repo.getChaptersPaged("s1", 100, 0) } returns page1
                coEvery { repo.getChaptersPaged("s1", 100, 100) } returns page2
                every { repo.observeChapters(any()) } returns emptyFlow()
                every { progressRepo.observeReadingProgress(any()) } returns emptyFlow()
                every { repo.observeStory(any()) } returns emptyFlow()

                val vm =
                    StoryDetailViewModel(
                        savedStateHandle = SavedStateHandle(mapOf("storyId" to "s1", "storyUrl" to sampleStory.url)),
                        repository = repo,
                        textReplacementRepository = textRepo,
                        readingProgressRepository = progressRepo,
                        notificationHelper = notifHelper,
                        application = app,
                    )
                advanceUntilIdle()
                assertEquals(100, vm.state.value.displayedChapters.size)
                assertTrue(vm.state.value.canLoadMoreChapters)

                vm.loadMoreChapters()
                advanceUntilIdle()
                assertEquals(150, vm.state.value.displayedChapters.size)
                assertFalse(vm.state.value.canLoadMoreChapters)
            } finally {
                unmockkObject(Screen.StoryDetail)
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `toggleStoryReplacements and addStoryReplacement call repository and update state`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            mockkObject(Screen.StoryDetail)
            try {
                every { Screen.StoryDetail.decodeUrl(any()) } answers { firstArg() }
                val repo = mockk<IStoryRepository>(relaxed = true)
                val textRepo = mockk<ITextReplacementRepository>(relaxed = true)
                val progressRepo = mockk<IReadingProgressRepository>(relaxed = true)
                val notifHelper = mockk<DownloadNotificationHelper>(relaxed = true)
                val app = mockk<Application>(relaxed = true)

                val sampleStory = Story(id = "s1", slug = "s1", title = "Truyện 1", url = "https://sstruyen.pics/truyen/s1", totalChapters = 10)
                coEvery { repo.getStoryDetail(any()) } returns sampleStory
                coEvery { repo.getFirstPageChapterList(any(), any()) } returns (emptyList<Chapter>() to 1)
                coEvery { textRepo.getStoryReplacements("s1") } returnsMany
                    listOf(
                        StoryReplacementData(false, "[]"),
                        StoryReplacementData(true, """[{"from":"lão","to":"ông"}]"""),
                    )
                every { repo.observeChapters(any()) } returns emptyFlow()
                every { progressRepo.observeReadingProgress(any()) } returns emptyFlow()
                every { repo.observeStory(any()) } returns emptyFlow()

                val vm =
                    StoryDetailViewModel(
                        savedStateHandle = SavedStateHandle(mapOf("storyId" to "s1", "storyUrl" to sampleStory.url)),
                        repository = repo,
                        textReplacementRepository = textRepo,
                        readingProgressRepository = progressRepo,
                        notificationHelper = notifHelper,
                        application = app,
                    )
                advanceUntilIdle()

                vm.toggleStoryReplacements()
                vm.addStoryReplacement("lão", "ông")
                advanceUntilIdle()

                coVerify { textRepo.setStoryReplacementsEnabled("s1", true) }
                coVerify { textRepo.addStoryReplacement("s1", "lão", "ông") }
                assertTrue(vm.state.value.storyReplacementsEnabled)
                assertEquals(1, vm.state.value.storyReplacements.size)
            } finally {
                unmockkObject(Screen.StoryDetail)
                Dispatchers.resetMain()
            }
        }
}
