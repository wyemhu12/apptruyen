package com.personal.apptruyen.data.local

import androidx.room.*
import com.personal.apptruyen.data.local.entity.StoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Query("SELECT * FROM stories WHERE isInLibrary = 1 ORDER BY addedTimestamp DESC")
    fun getLibraryStories(): Flow<List<StoryEntity>>

    @Query("SELECT * FROM stories WHERE isInLibrary = 1")
    suspend fun getLibraryStoriesOnce(): List<StoryEntity>

    @Query("SELECT * FROM stories WHERE id = :storyId")
    suspend fun getStoryById(storyId: String): StoryEntity?

    @Query("SELECT * FROM stories WHERE id = :storyId")
    fun observeStory(storyId: String): Flow<StoryEntity?>

    @Upsert
    suspend fun insertStory(story: StoryEntity)

    @Update
    suspend fun updateStory(story: StoryEntity)

    @Query("DELETE FROM stories WHERE id = :storyId")
    suspend fun deleteStory(storyId: String)

    @Query("UPDATE stories SET isInLibrary = 1, addedTimestamp = :timestamp WHERE id = :storyId")
    suspend fun addToLibrary(
        storyId: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query(
        """
        SELECT s.* FROM stories s 
        INNER JOIN reading_progress rp ON s.id = rp.storyId 
        ORDER BY rp.lastReadTimestamp DESC
    """,
    )
    fun getRecentlyReadStories(): Flow<List<StoryEntity>>

    @Query(
        """
        SELECT s.* FROM stories s 
        INNER JOIN chapters c ON s.id = c.storyId 
        WHERE c.isDownloaded = 1 
        GROUP BY s.id 
        ORDER BY s.addedTimestamp DESC
    """,
    )
    fun getDownloadedStories(): Flow<List<StoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<StoryEntity>)

    @Query("SELECT * FROM stories")
    suspend fun getAllStoriesOnce(): List<StoryEntity>

    // ── Per-story text replacement ──

    @Query("UPDATE stories SET storyReplacementsEnabled = :enabled WHERE id = :storyId")
    suspend fun setStoryReplacementsEnabled(
        storyId: String,
        enabled: Boolean,
    )

    @Query("UPDATE stories SET storyCustomReplacements = :json WHERE id = :storyId")
    suspend fun setStoryCustomReplacements(
        storyId: String,
        json: String,
    )

    @Query("SELECT storyReplacementsEnabled, storyCustomReplacements FROM stories WHERE id = :storyId")
    suspend fun getStoryReplacements(storyId: String): StoryReplacementData?
}

/**
 * Partial query result cho per-story text replacement settings.
 * Room maps cột DB → field name trực tiếp.
 */
data class StoryReplacementData(
    val storyReplacementsEnabled: Boolean,
    val storyCustomReplacements: String,
)
