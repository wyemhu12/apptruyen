package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.local.ReadingStatsDao
import com.personal.apptruyen.data.local.entity.ReadingStatsEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReadingStatsRepositoryTest {

    private lateinit var statsDao: ReadingStatsDao
    private lateinit var repository: ReadingStatsRepository
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @BeforeEach
    fun setUp() {
        statsDao = mockk(relaxed = true)
        repository = ReadingStatsRepository(statsDao)
    }

    private fun fixedTimeMillis(
        year: Int,
        monthOneBased: Int,
        day: Int,
    ): Long =
        Calendar
            .getInstance()
            .apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, monthOneBased - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

    private fun dateStringDaysAgo(
        baseMillis: Long,
        daysAgo: Int,
    ): String {
        val cal = Calendar.getInstance().apply { timeInMillis = baseMillis }
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return dateFormat.format(cal.time)
    }

    @Test
    fun `getReadingStreak preserves consecutive streak from yesterday when today has no reading record`() =
        runTest {
            val now = fixedTimeMillis(2026, 10, 16)
            val yesterday = dateStringDaysAgo(now, 1)
            val twoDaysAgo = dateStringDaysAgo(now, 2)
            val threeDaysAgo = dateStringDaysAgo(now, 3)

            coEvery { statsDao.getRecentStats(365) } returns
                listOf(
                    ReadingStatsEntity(date = yesterday, totalReadingTimeMs = 120_000L, chaptersRead = 2),
                    ReadingStatsEntity(date = twoDaysAgo, totalReadingTimeMs = 60_000L, chaptersRead = 1),
                    ReadingStatsEntity(date = threeDaysAgo, totalReadingTimeMs = 90_000L, chaptersRead = 3),
                )

            val streak = repository.getReadingStreak(nowMillis = now)
            assertEquals(3, streak)
        }

    @Test
    fun `getReadingStreak counts today when chaptersRead is positive even if totalReadingTimeMs is zero`() =
        runTest {
            val now = fixedTimeMillis(2026, 10, 16)
            val today = dateStringDaysAgo(now, 0)
            val yesterday = dateStringDaysAgo(now, 1)

            coEvery { statsDao.getRecentStats(365) } returns
                listOf(
                    ReadingStatsEntity(date = today, totalReadingTimeMs = 0L, chaptersRead = 1),
                    ReadingStatsEntity(date = yesterday, totalReadingTimeMs = 300_000L, chaptersRead = 4),
                )

            val streak = repository.getReadingStreak(nowMillis = now)
            assertEquals(2, streak)
        }

    @Test
    fun `getReadingStreak returns 0 when neither today nor yesterday has reading activity`() =
        runTest {
            val now = fixedTimeMillis(2026, 10, 16)
            val today = dateStringDaysAgo(now, 0)
            val twoDaysAgo = dateStringDaysAgo(now, 2)

            coEvery { statsDao.getRecentStats(365) } returns
                listOf(
                    ReadingStatsEntity(date = today, totalReadingTimeMs = 0L, chaptersRead = 0),
                    ReadingStatsEntity(date = twoDaysAgo, totalReadingTimeMs = 500_000L, chaptersRead = 5),
                )

            val streak = repository.getReadingStreak(nowMillis = now)
            assertEquals(0, streak)
        }
}
