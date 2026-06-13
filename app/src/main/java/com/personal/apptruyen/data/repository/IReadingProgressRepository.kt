package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.model.ReadingProgress
import com.personal.apptruyen.data.model.Story
import kotlinx.coroutines.flow.Flow

/**
 * Interface cho reading progress operations.
 */
interface IReadingProgressRepository {
    suspend fun getReadingProgress(storyId: String): ReadingProgress?

    fun observeReadingProgress(storyId: String): Flow<ReadingProgress?>

    suspend fun saveReadingProgress(progress: ReadingProgress)

    suspend fun deleteReadingProgress(storyId: String)

    fun getRecentlyReadStories(): Flow<List<Story>>

    fun getAllReadingProgress(): Flow<List<ReadingProgress>>
}
