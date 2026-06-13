package com.personal.apptruyen.data.repository

import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import kotlinx.coroutines.flow.Flow

/**
 * Interface cho StoryRepository — dùng để mock trong unit tests.
 * Chỉ chứa story/chapter core operations.
 * Download → IDownloadRepository, Text Replacement → ITextReplacementRepository,
 * Reading Progress → IReadingProgressRepository.
 */
interface IStoryRepository {

    // ── Search & Discovery ──
    suspend fun getCompletedStories(page: Int = 1): List<Story>

    suspend fun getStoriesByGenre(
        genreSlug: String,
        page: Int = 1,
        minChapters: Int = 0,
        completedOnly: Boolean = true,
    ): List<Story>

    suspend fun getGenreList(): List<Genre>

    suspend fun searchStories(
        keyword: String,
        completedOnly: Boolean = false,
    ): List<Story>

    // ── Story Detail ──
    suspend fun getStoryDetail(storyUrl: String): Story

    fun observeStory(storyId: String): Flow<Story?>

    // ── Chapters ──
    suspend fun getFirstPageChapterList(
        storyUrl: String,
        storyId: String,
    ): Pair<List<Chapter>, Int>

    suspend fun loadRemainingChapters(
        storyUrl: String,
        storyId: String,
        totalPages: Int,
    )

    suspend fun getChapterList(
        storyUrl: String,
        storyId: String,
    ): List<Chapter>

    fun observeChapters(storyId: String): Flow<List<Chapter>>

    suspend fun getChaptersPaged(
        storyId: String,
        limit: Int,
        offset: Int,
    ): List<Chapter>

    suspend fun getChapterCount(storyId: String): Int

    suspend fun getChapterContent(
        storyId: String,
        chapterNumber: Int,
        chapterUrl: String,
    ): String

    // ── Library ──
    suspend fun addToLibrary(storyId: String)

    // ── Refresh ──
    suspend fun refreshLibraryStories()
}
