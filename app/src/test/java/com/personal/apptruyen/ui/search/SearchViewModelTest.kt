package com.personal.apptruyen.ui.search

import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.SearchHistoryRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private lateinit var repository: IStoryRepository
    private lateinit var searchHistoryRepository: SearchHistoryRepository
    private lateinit var viewModel: SearchViewModel
    private val dispatcher = UnconfinedTestDispatcher()

    private val sampleStory =
        Story(
            id = "search-test.1",
            slug = "search-test",
            title = "Kết Quả Tìm Kiếm",
            author = "Author",
            totalChapters = 10,
            url = "https://example.com/search-test.1/",
            status = "Hoàn thành",
        )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        searchHistoryRepository = mockk(relaxed = true)

        coEvery { repository.getGenreList() } returns
            listOf(
                Genre(name = "Tiên Hiệp", slug = "tien-hiep", categoryId = 1),
            )
        every { searchHistoryRepository.getRecentSearches(any()) } returns flowOf(listOf("old query"))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `search calls repository and saves history on success`() =
        runTest {
            coEvery { repository.searchStories(any(), any()) } returns listOf(sampleStory)
            coEvery { searchHistoryRepository.saveSearch(any()) } just Runs

            viewModel = SearchViewModel(repository, searchHistoryRepository)
            advanceUntilIdle()

            viewModel.onQueryChange("test")
            viewModel.search()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(1, state.results.size)
            assertEquals("Kết Quả Tìm Kiếm", state.results[0].title)
            assertFalse(state.isLoading)
            assertTrue(state.hasSearched)
            coVerify { searchHistoryRepository.saveSearch("test") }
        }

    @Test
    fun `search with no results shows error message`() =
        runTest {
            coEvery { repository.searchStories("xyz", any()) } returns emptyList()

            viewModel = SearchViewModel(repository, searchHistoryRepository)
            advanceUntilIdle()

            viewModel.onQueryChange("xyz")
            viewModel.search()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.results.isEmpty())
            assertTrue(state.error?.contains("xyz") == true)
        }

    @Test
    fun `blank query does not trigger search`() =
        runTest {
            viewModel = SearchViewModel(repository, searchHistoryRepository)
            advanceUntilIdle()

            viewModel.onQueryChange("   ")
            viewModel.search()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.hasSearched)
            coVerify(exactly = 0) { repository.searchStories(any()) }
        }

    @Test
    fun `deleteHistoryItem delegates to repository`() =
        runTest {
            coEvery { searchHistoryRepository.deleteSearch(any()) } just Runs

            viewModel = SearchViewModel(repository, searchHistoryRepository)
            advanceUntilIdle()

            viewModel.deleteHistoryItem("old query")
            advanceUntilIdle()

            coVerify { searchHistoryRepository.deleteSearch("old query") }
        }

    @Test
    fun `clearHistory delegates to repository`() =
        runTest {
            coEvery { searchHistoryRepository.clearSearchHistory() } just Runs

            viewModel = SearchViewModel(repository, searchHistoryRepository)
            advanceUntilIdle()

            viewModel.clearHistory()
            advanceUntilIdle()

            coVerify { searchHistoryRepository.clearSearchHistory() }
        }

    @Test
    fun `genres are loaded on init`() =
        runTest {
            viewModel = SearchViewModel(repository, searchHistoryRepository)
            advanceUntilIdle()

            val genres = viewModel.genres.value
            assertEquals(1, genres.size)
            assertEquals("Tiên Hiệp", genres[0].name)
        }

    @Test
    fun `clearFilter resets state`() =
        runTest {
            viewModel = SearchViewModel(repository, searchHistoryRepository)
            advanceUntilIdle()

            viewModel.onQueryChange("test")
            viewModel.clearFilter()

            assertEquals("", viewModel.state.value.query)
            assertFalse(viewModel.state.value.isFilterActive)
        }
}
