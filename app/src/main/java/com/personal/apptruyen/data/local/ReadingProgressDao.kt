package com.personal.apptruyen.data.local

import androidx.room.*
import com.personal.apptruyen.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE storyId = :storyId")
    suspend fun getProgress(storyId: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE storyId = :storyId")
    fun observeProgress(storyId: String): Flow<ReadingProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE storyId = :storyId")
    suspend fun deleteProgress(storyId: String)

    @Query("SELECT * FROM reading_progress ORDER BY lastReadTimestamp DESC")
    fun getAllProgress(): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress")
    suspend fun getAllProgressOnce(): List<ReadingProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllProgress(list: List<ReadingProgressEntity>)
}
