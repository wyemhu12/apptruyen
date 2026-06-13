package com.personal.apptruyen.ui.home

import com.personal.apptruyen.data.model.ReadingProgress
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.IReadingProgressRepository
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.ReadingStatsRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var storyRepository: IStoryRepository
    private lateinit var readingProgressRepository: IReadingProgressRepository
    private lateinit var readingStatsRepository: ReadingStatsRepository
    private lateinit var viewModel: HomeViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    private val sampleStory =
        Story(
            id = "test.1",
            slug = "test",
            title = "Truyen Test",
            author = "Author",
            totalChapters = 10,
            url = "https://example.com/test.1/",
            coverImageUrl = "https://example.com/cover.jpg",
        )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        storyRepository = mockk(relaxed = true)
        readingProgressRepository = mockk(relaxed = true)
        readingStatsRepository = mockk(relaxed = true)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0

        every { readingProgressRepository.getRecentlyReadStories() } returns flowOf(listOf(sampleStory))
        every { readingProgressRepository.getAllReadingProgress() } returns
            flowOf(
                listOf(
                    ReadingProgress(
                        storyId = "test.1",
                        lastChapterNumber = 5,
                        scrollPosition = 0,
                        lastReadTimestamp = System.currentTimeMillis(),
                    ),
                ),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `recentlyRead emits from repository flow`() =
        runTest {
            viewModel = HomeViewModel(storyRepository, readingProgressRepository, readingStatsRepository)

            val stories = viewModel.recentlyRead.first()
            assertEquals(1, stories.size)
            assertEquals("Truyen Test", stories[0].title)
        }

    @Test
    fun `refresh calls repository refreshLibraryStories`() =
        runTest {
            coEvery { storyRepository.refreshLibraryStories() } just Runs

            viewModel = HomeViewModel(storyRepository, readingProgressRepository, readingStatsRepository)
            viewModel.refresh()
            advanceUntilIdle()

            assertFalse(viewModel.isRefreshing.value)
            coVerify { storyRepository.refreshLibraryStories() }
        }

    @Test
    fun `sort options change sort state`() =
        runTest {
            viewModel = HomeViewModel(storyRepository, readingProgressRepository, readingStatsRepository)

            viewModel.setSortOption(HomeSortOption.ALPHA_ASC)
            assertEquals(HomeSortOption.ALPHA_ASC, viewModel.sortOption.value)

            viewModel.setSortOption(HomeSortOption.UNFINISHED)
            assertEquals(HomeSortOption.UNFINISHED, viewModel.sortOption.value)
        }

    @Test
    fun `refresh handles exception gracefully`() =
        runTest {
            coEvery { storyRepository.refreshLibraryStories() } throws RuntimeException("Network error")

            viewModel = HomeViewModel(storyRepository, readingProgressRepository, readingStatsRepository)
            viewModel.refresh()
            advanceUntilIdle()

            assertFalse(viewModel.isRefreshing.value)
        }
}
