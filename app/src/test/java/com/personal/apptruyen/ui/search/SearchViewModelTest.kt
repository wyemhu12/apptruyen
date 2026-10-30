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
    private lateinit var authRepository: com.personal.apptruyen.data.repository.ISourceAuthRepository
    private lateinit var activeSourceFlow: kotlinx.coroutines.flow.MutableStateFlow<String>
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
        authRepository = mockk(relaxed = true)
        activeSourceFlow =
            kotlinx.coroutines.flow.MutableStateFlow(
                com.personal.apptruyen.data.local.SourceIds.DEFAULT_SINGLE_SOURCE,
            )
        every { repository.observeActiveSource() } returns activeSourceFlow
        coEvery { repository.setActiveSource(any()) } answers { activeSourceFlow.value = firstArg() }
        every { authRepository.loginRequiredSources } returns kotlinx.coroutines.flow.MutableStateFlow(emptySet())

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

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
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
    fun `search with no results sets error to null and hasSearched to true while network exception sets Vietnamese error`() =
        runTest {
            coEvery { repository.searchStories("xyz", any()) } returns emptyList()

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.onQueryChange("xyz")
            viewModel.search()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.results.isEmpty())
            assertTrue(state.hasSearched)
            assertNull(state.error)

            coEvery { repository.searchStories("xyz", any()) } throws RuntimeException("Read timed out")
            viewModel.search()
            advanceUntilIdle()

            val errState = viewModel.state.value
            assertNotNull(errState.error)
            assertFalse(errState.error?.contains("Read timed out") == true)
        }

    @Test
    fun `blank query does not trigger search`() =
        runTest {
            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
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

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.deleteHistoryItem("old query")
            advanceUntilIdle()

            coVerify { searchHistoryRepository.deleteSearch("old query") }
        }

    @Test
    fun `clearHistory delegates to repository`() =
        runTest {
            coEvery { searchHistoryRepository.clearSearchHistory() } just Runs

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.clearHistory()
            advanceUntilIdle()

            coVerify { searchHistoryRepository.clearSearchHistory() }
        }

    @Test
    fun `genres are loaded on init`() =
        runTest {
            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            val genres = viewModel.genres.value
            assertEquals(1, genres.size)
            assertEquals("Tiên Hiệp", genres[0].name)
        }

    @Test
    fun `clearFilter resets state`() =
        runTest {
            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.onQueryChange("test")
            viewModel.clearFilter()

            assertEquals("", viewModel.state.value.query)
            assertFalse(viewModel.state.value.isFilterActive)
        }

    @Test
    fun `dismissLoginPrompt delegates to authRepository`() =
        runTest {
            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            viewModel.dismissLoginPrompt("wikidich")
            verify { authRepository.clearLoginRequired("wikidich") }
        }

    @Test
    fun `applyFilter and applyAllFilters with ChapterCountFilter only trigger browseByFilterOnly and clearAllFilters resets state`() =
        runTest {
            val shortStory = sampleStory.copy(id = "short.1", title = "Truyện Ngắn", totalChapters = 50)
            val longStory = sampleStory.copy(id = "long.1", title = "Truyện Dài", totalChapters = 350)
            coEvery { repository.getCompletedStories(1, true) } returns listOf(shortStory, longStory)

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.applyFilter(null, ChapterCountFilter.OVER_200)
            advanceUntilIdle()

            val stateAfterFilter = viewModel.state.value
            assertTrue(stateAfterFilter.hasSearched)
            assertTrue(stateAfterFilter.isFilterActive)
            assertEquals(1, stateAfterFilter.results.size)
            assertEquals("Truyện Dài", stateAfterFilter.results[0].title)
            coVerify { repository.getCompletedStories(1, true) }

            viewModel.clearAllFilters()
            advanceUntilIdle()

            val stateAfterClear = viewModel.state.value
            assertFalse(stateAfterClear.hasSearched)
            assertFalse(stateAfterClear.isFilterActive)
            assertTrue(stateAfterClear.results.isEmpty())
        }

    @Test
    fun `loadMoreFilterResults stops canLoadMore when duplicate results returned or exception thrown`() =
        runTest {
            val genre = Genre(name = "Tiên Hiệp", slug = "tien-hiep", categoryId = 1)
            coEvery { repository.getStoriesByGenre(genre.slug, 1, 0, true, true) } returns listOf(sampleStory)
            coEvery { repository.getStoriesByGenre(genre.slug, 2, 0, true, true) } returns listOf(sampleStory)

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.applyFilter(genre, ChapterCountFilter.NONE)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.canLoadMore)

            viewModel.loadMoreFilterResults()
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.results.size)
            assertFalse(viewModel.state.value.canLoadMore)
        }

    @Test
    fun `toggleMaleFilter updates state and triggers search or browse`() =
        runTest {
            coEvery { repository.getCompletedStories(1, any()) } returns listOf(sampleStory)

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            // First trigger a filter search so hasSearched = true
            viewModel.applyAllFilters(null, ChapterCountFilter.OVER_200, completedOnly = true, maleOnly = true)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.hasSearched)
            assertTrue(viewModel.state.value.maleOnly)

            viewModel.toggleMaleFilter()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.maleOnly)
            assertTrue(viewModel.state.value.isFilterActive)
            coVerify { repository.getCompletedStories(1, false) }
        }

    @Test
    fun `toggleCompletedFilter does not reset maleOnly when maleOnly is false`() =
        runTest {
            activeSourceFlow.value = com.personal.apptruyen.data.local.SourceIds.WIKIDICH
            coEvery { repository.getCompletedStories(1, any()) } returns listOf(sampleStory)

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            // Turn off both maleOnly and completedOnly via applyAllFilters (sets hasSearched = true)
            viewModel.applyAllFilters(
                genre = null,
                chapterFilter = ChapterCountFilter.NONE,
                completedOnly = false,
                maleOnly = false,
            )
            advanceUntilIdle()
            assertFalse(viewModel.state.value.maleOnly)
            assertFalse(viewModel.state.value.completedOnly)
            assertTrue(viewModel.state.value.isFilterActive)

            clearMocks(repository, answers = false)
            // Re-enable completedOnly = true; maleOnly must remain false and isFilterActive must remain true
            viewModel.toggleCompletedFilter()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.completedOnly)
            assertFalse(viewModel.state.value.maleOnly)
            assertTrue(viewModel.state.value.isFilterActive)
            assertTrue(viewModel.state.value.hasSearched)
            coVerify { repository.getCompletedStories(page = 1, maleOnly = false) }
        }

    @Test
    fun `browseByGenre forwards maleOnly to repository`() =
        runTest {
            val genre = Genre(name = "Hệ Thống", slug = "he-thong")
            coEvery { repository.getStoriesByGenre("he-thong", 1, 0, true, false) } returns listOf(sampleStory)

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.applyAllFilters(
                genre = genre,
                chapterFilter = ChapterCountFilter.NONE,
                completedOnly = true,
                maleOnly = false,
            )
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.results.size)
            coVerify { repository.getStoriesByGenre("he-thong", 1, 0, true, false) }
        }

    @Test
    fun `browseByFilterOnly forwards maleOnly to repository`() =
        runTest {
            activeSourceFlow.value = com.personal.apptruyen.data.local.SourceIds.WIKIDICH
            coEvery { repository.getCompletedStories(1, false) } returns listOf(sampleStory)

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.applyAllFilters(
                genre = null,
                chapterFilter = ChapterCountFilter.NONE,
                completedOnly = true,
                maleOnly = false,
            )
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.results.size)
            coVerify { repository.getCompletedStories(1, false) }
        }

    @Test
    fun `loadMoreFilterResults forwards maleOnly to repository`() =
        runTest {
            val genre = Genre(name = "Hệ Thống", slug = "he-thong")
            val page2Story = sampleStory.copy(id = "search-test.2", title = "Trang 2")
            coEvery { repository.getStoriesByGenre("he-thong", 1, 0, true, false) } returns listOf(sampleStory)
            coEvery { repository.getStoriesByGenre("he-thong", 2, 0, true, false) } returns listOf(page2Story)

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.applyAllFilters(
                genre = genre,
                chapterFilter = ChapterCountFilter.NONE,
                completedOnly = true,
                maleOnly = false,
            )
            advanceUntilIdle()

            viewModel.loadMoreFilterResults()
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.results.size)
            coVerify { repository.getStoriesByGenre("he-thong", 2, 0, true, false) }
        }

    @Test
    fun `applyLocalFilters filters wikidich non-male stories and preserves other sources`() =
        runTest {
            val wikidichMale =
                sampleStory.copy(
                    id = "wd.male",
                    title = "Wikidich Nam Sinh",
                    sourceId = "wikidich",
                    genres = listOf("Nam sinh", "Hệ thống"),
                )
            val wikidichNgonTinhNfd =
                sampleStory.copy(
                    id = "wd.ngontinh",
                    title = "Wikidich Ngôn Tình",
                    sourceId = "wikidich",
                    genres = listOf(java.text.Normalizer.normalize("Ngôn tình", java.text.Normalizer.Form.NFD)),
                )
            val wikidichDamMy =
                sampleStory.copy(
                    id = "wd.dammy",
                    title = "Wikidich Đam Mỹ",
                    sourceId = "wikidich",
                    genres = listOf("Đam mỹ"),
                )
            val ttvNgonTinh =
                sampleStory.copy(
                    id = "ttv.ngontinh",
                    title = "TTV Ngôn Tình",
                    sourceId = "tangthuvien",
                    genres = listOf("Ngôn tình"),
                )
            val sstDamMy =
                sampleStory.copy(
                    id = "sst.dammy",
                    title = "SST Đam Mỹ",
                    sourceId = "sstruyen",
                    genres = listOf("Đam mỹ"),
                )

            coEvery { repository.searchStories("test", false) } returns
                listOf(
                    wikidichMale,
                    wikidichNgonTinhNfd,
                    wikidichDamMy,
                    ttvNgonTinh,
                    sstDamMy,
                )

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.onQueryChange("test")
            viewModel.search()
            advanceUntilIdle()

            val resultIds =
                viewModel.state.value.results
                    .map { it.id }
            assertEquals(listOf("wd.male", "ttv.ngontinh", "sst.dammy"), resultIds)
        }

    @Test
    fun `selectSource updates activeSource reloads genres and resets incompatible selectedGenre`() =
        runTest {
            activeSourceFlow.value = "wikidich"
            val wikiGenre = Genre("Làm sự nghiệp", "lam-su-nghiep")
            val sstGenre = Genre("Tiên Hiệp", "tien-hiep")
            coEvery { repository.getGenreList() } returns listOf(wikiGenre)
            coEvery { repository.getStoriesByGenre("lam-su-nghiep", 1, 0, true, true) } returns listOf(sampleStory)

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.applyAllFilters(
                genre = wikiGenre,
                chapterFilter = ChapterCountFilter.NONE,
                completedOnly = true,
                maleOnly = true,
            )
            advanceUntilIdle()
            assertEquals("wikidich", viewModel.state.value.activeSource)
            assertEquals(wikiGenre, viewModel.state.value.selectedGenre)

            coEvery { repository.getGenreList() } returns listOf(sstGenre)
            viewModel.selectSource("sstruyen")
            advanceUntilIdle()

            assertEquals("sstruyen", viewModel.state.value.activeSource)
            assertEquals(listOf(sstGenre), viewModel.genres.value)
            assertNull(viewModel.state.value.selectedGenre)
            assertFalse(viewModel.state.value.isFilterActive)
        }

    @Test
    fun `search with keyword and genre on wikidich forwards parameters to repository and loadMoreFilterResults fetches next page`() =
        runTest {
            activeSourceFlow.value = "wikidich"
            val wikiGenre = Genre("Tiên hiệp", "tien-hiep")
            val page1Story = sampleStory.copy(id = "wd.1", sourceId = "wikidich", genres = listOf("Nam sinh", "Tiên hiệp"))
            val page2Story = sampleStory.copy(id = "wd.2", sourceId = "wikidich", genres = listOf("Nam sinh", "Tiên hiệp"))

            coEvery {
                repository.searchStories(
                    keyword = "tiên",
                    completedOnly = true,
                    page = 1,
                    maleOnly = true,
                    genreSlug = "tien-hiep",
                )
            } returns listOf(page1Story)

            coEvery {
                repository.searchStories(
                    keyword = "tiên",
                    completedOnly = true,
                    page = 2,
                    maleOnly = true,
                    genreSlug = "tien-hiep",
                )
            } returns listOf(page2Story)

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.onQueryChange("tiên")
            viewModel.applyAllFilters(
                genre = wikiGenre,
                chapterFilter = ChapterCountFilter.NONE,
                completedOnly = true,
                maleOnly = true,
            )
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.results.size)
            assertTrue(viewModel.state.value.canLoadMore)
            coVerify(exactly = 1) {
                repository.searchStories(
                    keyword = "tiên",
                    completedOnly = true,
                    page = 1,
                    maleOnly = true,
                    genreSlug = "tien-hiep",
                )
            }

            viewModel.loadMoreFilterResults()
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.results.size)
            assertEquals(2, viewModel.state.value.currentPage)
            coVerify(exactly = 1) {
                repository.searchStories(
                    keyword = "tiên",
                    completedOnly = true,
                    page = 2,
                    maleOnly = true,
                    genreSlug = "tien-hiep",
                )
            }
        }

    @Test
    fun `loadMoreFilterResults in browseByFilterOnly mode fetches next page of completedStories`() =
        runTest {
            activeSourceFlow.value = "wikidich"
            val page1Story = sampleStory.copy(id = "wd.1", sourceId = "wikidich")
            val page2Story = sampleStory.copy(id = "wd.2", sourceId = "wikidich")
            coEvery { repository.getCompletedStories(page = 1, maleOnly = false) } returns listOf(page1Story)
            coEvery { repository.getCompletedStories(page = 2, maleOnly = false) } returns listOf(page2Story)

            viewModel = SearchViewModel(repository, searchHistoryRepository, authRepository)
            advanceUntilIdle()

            viewModel.applyAllFilters(
                genre = null,
                chapterFilter = ChapterCountFilter.NONE,
                completedOnly = true,
                maleOnly = false,
            )
            advanceUntilIdle()
            assertEquals(1, viewModel.state.value.results.size)
            assertTrue(viewModel.state.value.canLoadMore)

            viewModel.loadMoreFilterResults()
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.results.size)
            assertEquals(2, viewModel.state.value.currentPage)
            coVerify(exactly = 1) { repository.getCompletedStories(page = 2, maleOnly = false) }
        }
}
