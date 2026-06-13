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

        /**
         * Tính streak — số ngày đọc liên tiếp tính đến hôm nay.
         */
        suspend fun getReadingStreak(): Int {
            val recent = readingStatsDao.getRecentStats(365) // last year
            if (recent.isEmpty()) return 0
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            var streak = 0
            val cal = Calendar.getInstance()
            // Start from today
            for (i in recent.indices) {
                val expectedDate = sdf.format(cal.time)
                val stat = recent.getOrNull(i)
                if (stat != null && stat.date == expectedDate && stat.totalReadingTimeMs > 0) {
                    streak++
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    // If today has no stats yet but we're checking today, skip
                    if (i == 0 && stat?.date != expectedDate) {
                        cal.add(Calendar.DAY_OF_YEAR, -1)
                        continue
                    }
                    break
                }
            }
            return streak
        }

        fun observeAllStats(): Flow<List<ReadingStatsEntity>> = readingStatsDao.getAllStats()
    }
