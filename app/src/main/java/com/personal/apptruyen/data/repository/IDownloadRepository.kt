package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.local.entity.DownloadStats
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.Story
import kotlinx.coroutines.flow.Flow

/**
 * Interface cho download-related operations.
 * Tách từ IStoryRepository để giảm coupling.
 */
interface IDownloadRepository {
    suspend fun getChaptersByNumbers(
        storyId: String,
        chapterNumbers: List<Int>,
    ): List<Chapter>

    suspend fun snapshotStoryMetadata(storyId: String)

    fun downloadChapters(
        storyId: String,
        chapters: List<Chapter>,
    ): Flow<DownloadProgress>

    fun getDownloadedStories(): Flow<List<Story>>

    fun getDownloadedChapters(storyId: String): Flow<List<Chapter>>

    fun getDownloadedChapterCount(storyId: String): Flow<Int>

    fun getEstimatedSize(storyId: String): Flow<Long?>

    fun getDownloadStats(): Flow<List<DownloadStats>>

    suspend fun deleteChapterContent(
        storyId: String,
        chapterNumber: Int,
    )

    suspend fun deleteAllDownloads(storyId: String)

    suspend fun deleteStory(storyId: String)
}
