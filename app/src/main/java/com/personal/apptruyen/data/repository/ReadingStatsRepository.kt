package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.local.ReadingStatsDao
import com.personal.apptruyen.data.local.entity.ReadingStatsEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository cho reading statistics.
 * Tách từ StoryRepository để giảm coupling.
 *
 * Quản lý: thời gian đọc, số chương đã đọc, streak.
 */
@Singleton
class ReadingStatsRepository
    @Inject
    constructor(
        private val readingStatsDao: ReadingStatsDao,
    ) {

        private fun todayKey(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date())
        }

        /**
         * Ghi lại thời gian đọc (gọi từ ReaderViewModel.onCleared).
         */
        suspend fun recordReadingSession(
            durationMs: Long,
            storyId: String,
        ) {
            if (durationMs <= 0) return
            val date = todayKey()
            readingStatsDao.ensureDateExists(date)
            readingStatsDao.incrementReadingTime(date, durationMs)
        }

        /**
         * Ghi lại khi đọc xong 1 chương (gọi từ ReaderViewModel khi chuyển chương).
         */
        suspend fun recordChapterRead() {
            val date = todayKey()
            readingStatsDao.ensureDateExists(date)
            readingStatsDao.incrementChaptersRead(date)
        }

        suspend fun getRecentStats(days: Int = 7): List<ReadingStatsEntity> = readingStatsDao.getRecentStats(days)

        private fun ReadingStatsEntity.isActiveDay(): Boolean = totalReadingTimeMs > 0L || chaptersRead > 0

        /**
         * Tính streak — số ngày đọc liên tiếp tính đến hôm nay.
         */
        suspend fun getReadingStreak(): Int = getReadingStreak(System.currentTimeMillis())

        internal suspend fun getReadingStreak(nowMillis: Long): Int {
            val recent = readingStatsDao.getRecentStats(365) // last year
            if (recent.isEmpty()) return 0

            val statsByDate = recent.associateBy { it.date }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }

            val todayDate = sdf.format(calendar.time)
            if (statsByDate[todayDate]?.isActiveDay() != true) {
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                val yesterdayDate = sdf.format(calendar.time)
                if (statsByDate[yesterdayDate]?.isActiveDay() != true) {
                    return 0
                }
            }

            var streak = 0
            while (streak < 365) {
                val dateKey = sdf.format(calendar.time)
                if (statsByDate[dateKey]?.isActiveDay() == true) {
                    streak++
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }
            return streak
        }

        fun observeAllStats(): Flow<List<ReadingStatsEntity>> = readingStatsDao.getAllStats()
    }
