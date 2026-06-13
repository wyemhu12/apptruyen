package com.personal.apptruyen.ui.downloads

import com.personal.apptruyen.data.local.entity.DownloadStats
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.IDownloadRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private lateinit var downloadRepository: IDownloadRepository
    private lateinit var viewModel: DownloadsViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    private val sampleStory =
        Story(
            id = "test.1",
            slug = "test",
            title = "Truyen Da Tai",
            author = "Author",
            totalChapters = 30,
            url = "https://example.com/test.1/",
            coverImageUrl = "https://example.com/cover.jpg",
        )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        downloadRepository = mockk(relaxed = true)

        every { downloadRepository.getDownloadedStories() } returns flowOf(listOf(sampleStory))
        every { downloadRepository.getDownloadStats() } returns
            flowOf(
                listOf(DownloadStats(storyId = "test.1", downloadedCount = 10, totalSize = 50000L)),
            )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `displays downloaded stories with counts and sizes`() =
        runTest {
            viewModel = DownloadsViewModel(downloadRepository)
            advanceUntilIdle()

            val stories = viewModel.stories.value
            assertEquals(1, stories.size)
            assertEquals("Truyen Da Tai", stories[0].story.title)
            assertEquals(10, stories[0].downloadedCount)
            assertEquals(50000L, stories[0].estimatedSizeBytes)
        }

    @Test
    fun `search query filters stories`() =
        runTest {
            viewModel = DownloadsViewModel(downloadRepository)
            advanceUntilIdle()

            viewModel.updateSearchQuery("xyz")
            advanceUntilIdle()

            val filtered = viewModel.filteredStories.first()
            assertTrue(filtered.isEmpty())
        }

    @Test
    fun `deleteDownloads calls repository deleteAllDownloads`() =
        runTest {
            coEvery { downloadRepository.deleteAllDownloads(any()) } just Runs

            viewModel = DownloadsViewModel(downloadRepository)
            advanceUntilIdle()

            viewModel.deleteDownloads("test.1")
            advanceUntilIdle()

            coVerify { downloadRepository.deleteAllDownloads("test.1") }
        }

    @Test
    fun `updateSearchQuery updates searchQuery state`() =
        runTest {
            viewModel = DownloadsViewModel(downloadRepository)
            advanceUntilIdle()

            viewModel.updateSearchQuery("test query")
            assertEquals("test query", viewModel.searchQuery.value)

            viewModel.updateSearchQuery("")
            assertEquals("", viewModel.searchQuery.value)
        }
}
