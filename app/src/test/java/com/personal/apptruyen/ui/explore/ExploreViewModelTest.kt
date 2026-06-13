package com.personal.apptruyen.ui.explore

import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.IStoryRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {

    private lateinit var repository: IStoryRepository
    private lateinit var viewModel: ExploreViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    private val sampleStory =
        Story(
            id = "explore.1",
            slug = "explore",
            title = "Truyện Full",
            author = "Author",
            totalChapters = 100,
            url = "https://example.com/explore.1/",
        )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load fetches first page`() =
        runTest {
            coEvery { repository.getCompletedStories(1) } returns listOf(sampleStory)

            viewModel = ExploreViewModel(repository)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(1, state.stories.size)
            assertEquals("Truyện Full", state.stories[0].title)
            assertFalse(state.isLoading)
        }

    @Test
    fun `loadMore appends to existing list`() =
        runTest {
            val secondStory = sampleStory.copy(id = "explore.2", title = "Truyện Full 2")
            coEvery { repository.getCompletedStories(1) } returns listOf(sampleStory)
            coEvery { repository.getCompletedStories(2) } returns listOf(secondStory)

            viewModel = ExploreViewModel(repository)
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(2, state.stories.size)
        }

    @Test
    fun `refresh resets and reloads first page`() =
        runTest {
            coEvery { repository.getCompletedStories(1) } returns listOf(sampleStory)

            viewModel = ExploreViewModel(repository)
            advanceUntilIdle()

            viewModel.refresh()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isRefreshing)
            assertEquals(1, state.stories.size)
        }

    @Test
    fun `empty result disables loadMore`() =
        runTest {
            coEvery { repository.getCompletedStories(1) } returns emptyList()

            viewModel = ExploreViewModel(repository)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.canLoadMore)
        }

    @Test
    fun `error during load is captured`() =
        runTest {
            coEvery { repository.getCompletedStories(1) } throws RuntimeException("Network error")

            viewModel = ExploreViewModel(repository)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertNotNull(state.error)
            assertFalse(state.isLoading)
        }
}
