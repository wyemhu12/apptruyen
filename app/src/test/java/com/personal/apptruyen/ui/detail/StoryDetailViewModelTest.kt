package com.personal.apptruyen.ui.detail

import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.DownloadProgress
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for StoryDetailViewModel data classes and state.
 * Full VM integration tests require Android instrumented testing
 * due to Application + Service dependencies.
 */
class StoryDetailViewModelTest {

    @Test
    fun `DetailState default has correct values`() {
        val state = StoryDetailViewModel.DetailState()
        assertTrue(state.isLoading)
        assertNull(state.story)
        assertTrue(state.chapters.isEmpty())
        assertTrue(state.displayedChapters.isEmpty())
        assertEquals(0, state.totalChapterCount)
        assertFalse(state.canLoadMoreChapters)
        assertNull(state.readingProgress)
        assertNull(state.error)
        assertFalse(state.isDownloading)
        assertNull(state.downloadProgress)
        assertFalse(state.storyReplacementsEnabled)
        assertTrue(state.storyReplacements.isEmpty())
        assertFalse(state.showStorySettings)
    }

    @Test
    fun `DetailState copy updates correctly`() {
        val story =
            Story(
                id = "test.1",
                slug = "test",
                title = "Test",
                author = "Author",
                totalChapters = 10,
                url = "https://example.com",
                coverImageUrl = "",
            )
        val state =
            StoryDetailViewModel.DetailState(
                story = story,
                isLoading = false,
                totalChapterCount = 50,
            )
        assertEquals("Test", state.story?.title)
        assertFalse(state.isLoading)
        assertEquals(50, state.totalChapterCount)
    }

    @Test
    fun `DownloadProgress data class works`() {
        val progress =
            DownloadProgress(
                current = 5,
                total = 10,
                currentChapterTitle = "Chương 5",
                errors = 1,
            )
        assertEquals(5, progress.current)
        assertEquals(10, progress.total)
        assertEquals("Chương 5", progress.currentChapterTitle)
        assertEquals(1, progress.errors)
    }
}
