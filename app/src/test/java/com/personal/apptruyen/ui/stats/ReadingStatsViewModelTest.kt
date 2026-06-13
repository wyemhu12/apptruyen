package com.personal.apptruyen.ui.stats

import com.personal.apptruyen.data.local.entity.ReadingStatsEntity
import com.personal.apptruyen.data.repository.ReadingStatsRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingStatsViewModelTest {

    private lateinit var readingStatsRepository: ReadingStatsRepository
    private lateinit var viewModel: ReadingStatsViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    private val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    private val today = sdf.format(java.util.Date())

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        readingStatsRepository = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads stats correctly`() =
        runTest {
            val todayStats =
                ReadingStatsEntity(
                    date = today,
                    totalReadingTimeMs = 30_000L,
                    chaptersRead = 5,
                )
            coEvery { readingStatsRepository.getRecentStats(7) } returns listOf(todayStats)
            coEvery { readingStatsRepository.getReadingStreak() } returns 3

            viewModel = ReadingStatsViewModel(readingStatsRepository)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals(30_000L, state.todayTimeMs)
            assertEquals(5, state.todayChapters)
            assertEquals(3, state.streak)
            assertEquals(7, state.weeklyData.size)
        }

    @Test
    fun `refresh reloads stats`() =
        runTest {
            coEvery { readingStatsRepository.getRecentStats(7) } returns emptyList()
            coEvery { readingStatsRepository.getReadingStreak() } returns 0

            viewModel = ReadingStatsViewModel(readingStatsRepository)
            advanceUntilIdle()

            val stats = ReadingStatsEntity(date = today, totalReadingTimeMs = 60_000L, chaptersRead = 10)
            coEvery { readingStatsRepository.getRecentStats(7) } returns listOf(stats)
            coEvery { readingStatsRepository.getReadingStreak() } returns 5

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(60_000L, viewModel.state.value.todayTimeMs)
            assertEquals(5, viewModel.state.value.streak)
        }

    @Test
    fun `empty stats show zeros`() =
        runTest {
            coEvery { readingStatsRepository.getRecentStats(7) } returns emptyList()
            coEvery { readingStatsRepository.getReadingStreak() } returns 0

            viewModel = ReadingStatsViewModel(readingStatsRepository)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(0L, state.todayTimeMs)
            assertEquals(0, state.todayChapters)
            assertEquals(0L, state.totalTimeMs)
            assertEquals(0, state.streak)
        }
}
