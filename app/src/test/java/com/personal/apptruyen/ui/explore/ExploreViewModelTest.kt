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
    private lateinit var authRepository: com.personal.apptruyen.data.repository.ISourceAuthRepository
    private lateinit var activeSourceFlow: kotlinx.coroutines.flow.MutableStateFlow<String>
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
        authRepository = mockk(relaxed = true)
        activeSourceFlow =
            kotlinx.coroutines.flow.MutableStateFlow(
                com.personal.apptruyen.data.local.SourceIds.DEFAULT_SINGLE_SOURCE,
            )
        every { repository.observeActiveSource() } returns activeSourceFlow
        coEvery { repository.setActiveSource(any()) } answers { activeSourceFlow.value = firstArg() }
        every { authRepository.loginRequiredSources } returns kotlinx.coroutines.flow.MutableStateFlow(emptySet())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load fetches first page`() =
        runTest {
            coEvery { repository.getCompletedStories(1, true) } returns listOf(sampleStory)

            viewModel = ExploreViewModel(repository, authRepository)
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
            coEvery { repository.getCompletedStories(1, true) } returns listOf(sampleStory)
            coEvery { repository.getCompletedStories(2, true) } returns listOf(secondStory)

            viewModel = ExploreViewModel(repository, authRepository)
            advanceUntilIdle()

            viewModel.loadMore()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(2, state.stories.size)
        }

    @Test
    fun `refresh resets and reloads first page`() =
        runTest {
            coEvery { repository.getCompletedStories(1, true) } returns listOf(sampleStory)

            viewModel = ExploreViewModel(repository, authRepository)
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
            coEvery { repository.getCompletedStories(1, true) } returns emptyList()

            viewModel = ExploreViewModel(repository, authRepository)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.canLoadMore)
        }

    @Test
    fun `error during load is captured`() =
        runTest {
            coEvery { repository.getCompletedStories(1, true) } throws RuntimeException("Network error")

            viewModel = ExploreViewModel(repository, authRepository)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertNotNull(state.error)
            assertFalse(state.isLoading)
        }

    @Test
    fun `dismissLoginPrompt delegates to authRepository`() =
        runTest {
            viewModel = ExploreViewModel(repository, authRepository)
            viewModel.dismissLoginPrompt("wikidich")
            verify { authRepository.clearLoginRequired("wikidich") }
        }

    @Test
    fun `loadMore sets canLoadMore to false when duplicate stories returned or exception thrown`() =
        runTest {
            coEvery { repository.getCompletedStories(1, true) } returns listOf(sampleStory)
            // Page 2 returns only duplicate story
            coEvery { repository.getCompletedStories(2, true) } returns listOf(sampleStory)

            viewModel = ExploreViewModel(repository, authRepository)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.canLoadMore)

            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(1, viewModel.state.value.stories.size)
            assertFalse(viewModel.state.value.canLoadMore)
            assertEquals(1, viewModel.state.value.currentPage)

            // Now test exception on loadMore
            val secondStory = sampleStory.copy(id = "explore.2", title = "Truyện Full 2")
            coEvery { repository.getCompletedStories(2, true) } returns listOf(secondStory)
            coEvery { repository.getCompletedStories(3, true) } throws RuntimeException("Socket timeout")

            viewModel.refresh()
            advanceUntilIdle()
            viewModel.loadMore() // page 2 succeeds
            advanceUntilIdle()
            assertTrue(viewModel.state.value.canLoadMore)

            viewModel.loadMore() // page 3 throws
            advanceUntilIdle()
            assertFalse(viewModel.state.value.isLoadingMore)
            assertFalse(viewModel.state.value.canLoadMore)

            // Verify initial load error is Vietnamese
            coEvery { repository.getCompletedStories(1, true) } throws RuntimeException("Raw English Failure")
            viewModel.refresh()
            advanceUntilIdle()
            assertFalse(
                viewModel.state.value.error
                    ?.contains("Raw English Failure") == true,
            )
        }

    @Test
    fun `toggleMaleOnly updates state and reloads first page with maleOnly false`() =
        runTest {
            val femaleIncludedStory = sampleStory.copy(id = "explore.female", title = "Truyện Tổng Hợp")
            coEvery { repository.getCompletedStories(1, true) } returns listOf(sampleStory)
            coEvery { repository.getCompletedStories(1, false) } returns listOf(sampleStory, femaleIncludedStory)

            viewModel = ExploreViewModel(repository, authRepository)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.maleOnly)
            assertEquals(1, viewModel.state.value.stories.size)

            viewModel.toggleMaleOnly()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.maleOnly)
            assertEquals(2, viewModel.state.value.stories.size)
            coVerify { repository.getCompletedStories(1, false) }
        }

    @Test
    fun `pullToRefresh preserves maleOnly filter state`() =
        runTest {
            coEvery { repository.getCompletedStories(1, any()) } returns listOf(sampleStory)

            viewModel = ExploreViewModel(repository, authRepository)
            advanceUntilIdle()

            viewModel.toggleMaleOnly()
            advanceUntilIdle()
            assertFalse(viewModel.state.value.maleOnly)

            clearMocks(repository, answers = false)
            viewModel.pullToRefresh()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.maleOnly)
            assertFalse(viewModel.state.value.isRefreshing)
            coVerify(exactly = 1) { repository.getCompletedStories(1, false) }
            coVerify(exactly = 0) { repository.getCompletedStories(1, true) }
        }

    @Test
    fun `selectSource syncs with repository reloads genres and resets incompatible selectedGenre`() =
        runTest {
            activeSourceFlow.value = "wikidich"
            val wikiGenre =
                com.personal.apptruyen.data.model
                    .Genre("Làm sự nghiệp", "lam-su-nghiep")
            val sstGenre =
                com.personal.apptruyen.data.model
                    .Genre("Tiên Hiệp", "tien-hiep")
            coEvery { repository.getGenreList() } returns listOf(wikiGenre)
            coEvery { repository.getCompletedStories(1, true) } returns listOf(sampleStory)
            coEvery { repository.getStoriesByGenre("lam-su-nghiep", 1, 0, true, true) } returns listOf(sampleStory)

            viewModel = ExploreViewModel(repository, authRepository)
            advanceUntilIdle()
            assertEquals("wikidich", viewModel.state.value.activeSource)

            viewModel.selectGenre(wikiGenre)
            advanceUntilIdle()
            assertEquals(wikiGenre, viewModel.state.value.selectedGenre)

            coEvery { repository.getGenreList() } returns listOf(sstGenre)
            clearMocks(repository, answers = false)

            viewModel.selectSource("sstruyen")
            advanceUntilIdle()

            assertEquals("sstruyen", viewModel.state.value.activeSource)
            assertNull(viewModel.state.value.selectedGenre)
            assertEquals(listOf(sstGenre), viewModel.state.value.genres)
            coVerify(exactly = 1) { repository.getCompletedStories(1, true) }
        }
}
