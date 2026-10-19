package com.personal.apptruyen.ui.home

import com.personal.apptruyen.data.local.entity.ReadingStatsEntity
import com.personal.apptruyen.data.model.ReadingProgress
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.IReadingProgressRepository
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.ReadingStatsRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

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

    @Test
    fun `weeklyStats computes dynamic day labels from calendarProvider and updates when observeAllStats emits`() =
        runTest {
            // 2026-10-14 is Wednesday (T4)
            val fixedCalendarProvider: () -> Calendar = {
                Calendar.getInstance(TimeZone.getDefault(), Locale.US).apply {
                    set(2026, Calendar.OCTOBER, 14, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
            val statsFlow =
                MutableStateFlow(
                    listOf(
                        ReadingStatsEntity(date = "2026-10-14", totalReadingTimeMs = 600_000L, chaptersRead = 4),
                        ReadingStatsEntity(date = "2026-10-12", totalReadingTimeMs = 300_000L, chaptersRead = 2),
                    ),
                )
            every { readingStatsRepository.observeAllStats() } returns statsFlow
            coEvery { readingStatsRepository.getReadingStreak() } returns 3

            viewModel =
                HomeViewModel(
                    storyRepository = storyRepository,
                    readingProgressRepository = readingProgressRepository,
                    readingStatsRepository = readingStatsRepository,
                    calendarProvider = fixedCalendarProvider,
                )
            advanceUntilIdle()

            val stats = viewModel.readingStats.value
            assertEquals(7, stats.weeklyStats.size)
            assertEquals(
                listOf("T5", "T6", "T7", "CN", "T2", "T3", "T4"),
                stats.weeklyStats.map { it.label },
            )
            assertTrue(stats.weeklyStats.last().isToday)
            assertFalse(stats.weeklyStats.first().isToday)
            assertEquals(4, stats.todayChapters)
            assertEquals(10, stats.todayMinutes)
            assertEquals(3, stats.currentStreak)
            assertEquals(listOf(0, 0, 0, 0, 2, 0, 4), stats.weeklyChapters)

            // Reactive emission
            statsFlow.value =
                listOf(
                    ReadingStatsEntity(date = "2026-10-14", totalReadingTimeMs = 1_200_000L, chaptersRead = 9),
                )
            advanceUntilIdle()

            assertEquals(9, viewModel.readingStats.value.todayChapters)
            assertEquals(20, viewModel.readingStats.value.todayMinutes)
            assertEquals(
                9,
                viewModel.readingStats.value.weeklyStats
                    .last()
                    .chapters,
            )
        }

    @Test
    fun `hasReadingHistory remains true when UNFINISHED filter yields empty recentlyRead list`() =
        runTest {
            every { readingProgressRepository.getAllReadingProgress() } returns
                flowOf(
                    listOf(
                        ReadingProgress(
                            storyId = "test.1",
                            lastChapterNumber = 10, // Equals sampleStory.totalChapters == 10 (finished)
                            scrollPosition = 0,
                            lastReadTimestamp = System.currentTimeMillis(),
                        ),
                    ),
                )

            viewModel = HomeViewModel(storyRepository, readingProgressRepository, readingStatsRepository)
            // Collect both StateFlows so WhileSubscribed activates
            assertEquals(1, viewModel.recentlyRead.first().size)
            assertTrue(viewModel.hasReadingHistory.first())

            viewModel.setSortOption(HomeSortOption.UNFINISHED)
            advanceUntilIdle()

            assertTrue(viewModel.recentlyRead.first().isEmpty())
            assertTrue(viewModel.hasReadingHistory.first())
        }
}
