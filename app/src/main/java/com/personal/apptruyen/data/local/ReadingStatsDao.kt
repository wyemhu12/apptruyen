package com.personal.apptruyen.data.local

import androidx.room.*
import com.personal.apptruyen.data.local.entity.ReadingStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingStatsDao {

    @Query("SELECT * FROM reading_stats WHERE date = :date")
    suspend fun getStatsForDate(date: String): ReadingStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStats(stats: ReadingStatsEntity)

    @Query("INSERT OR IGNORE INTO reading_stats (date, totalReadingTimeMs, chaptersRead) VALUES (:date, 0, 0)")
    suspend fun ensureDateExists(date: String)

    @Query("UPDATE reading_stats SET totalReadingTimeMs = totalReadingTimeMs + :durationMs WHERE date = :date")
    suspend fun incrementReadingTime(
        date: String,
        durationMs: Long,
    )

    @Query("UPDATE reading_stats SET chaptersRead = chaptersRead + :count WHERE date = :date")
    suspend fun incrementChaptersRead(
        date: String,
        count: Int = 1,
    )

    @Query("SELECT * FROM reading_stats ORDER BY date DESC")
    fun getAllStats(): Flow<List<ReadingStatsEntity>>

    @Query("SELECT * FROM reading_stats WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getStatsBetween(
        from: String,
        to: String,
    ): List<ReadingStatsEntity>

    @Query("SELECT * FROM reading_stats ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentStats(limit: Int): List<ReadingStatsEntity>

    @Query("SELECT * FROM reading_stats")
    suspend fun getAllStatsOnce(): List<ReadingStatsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStats(list: List<ReadingStatsEntity>)
}
