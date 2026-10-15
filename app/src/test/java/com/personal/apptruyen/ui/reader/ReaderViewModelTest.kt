package com.personal.apptruyen.ui.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import com.personal.apptruyen.data.model.ChapterContentResult
import com.personal.apptruyen.data.model.PokemonEntry
import com.personal.apptruyen.data.model.PokemonForm
import com.personal.apptruyen.data.model.PokemonMatchRange
import com.personal.apptruyen.data.model.PokemonSelection
import com.personal.apptruyen.data.remote.ScraperException
import com.personal.apptruyen.data.repository.IPokemonRepository
import com.personal.apptruyen.data.repository.IReadingProgressRepository
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.ITextReplacementRepository
import com.personal.apptruyen.data.repository.ReadingStatsRepository
import com.personal.apptruyen.tts.TtsManager
import com.personal.apptruyen.tts.TtsService
import com.personal.apptruyen.ui.navigation.NavigationUtils
import io.mockk.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    private lateinit var repository: IStoryRepository
    private lateinit var textReplacementRepository: ITextReplacementRepository
    private lateinit var readingProgressRepository: IReadingProgressRepository
    private lateinit var readingStatsRepository: ReadingStatsRepository
    private lateinit var pokemonRepository: IPokemonRepository
    private lateinit var ttsManager: TtsManager
    private lateinit var dataStore: DataStore<Preferences>
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val samplePikachuEntry =
        PokemonEntry(
            id = 25,
            nameCn = "皮卡丘",
            nameVn = "Pikachu",
            nameHv = "Bì Khả Khâu",
            nameEn = "Pikachu",
            nameJp = "ピカチュウ",
            aliases = listOf("Tỷ Ca Khâu"),
            forms =
                listOf(
                    PokemonForm(
                        formCn = "皮卡丘",
                        formVn = "Pikachu",
                        formHv = "Bì Khả Khâu",
                        imageUrl = "https://vn.portal-pokemon.com/play/resources/pokedex/img/pm/2b3f6ff00db7a1efae21d85cfb8995eaff2da8d8.png",
                        types = listOf("Điện"),
                    ),
                ),
        )

    private val savedStateHandle =
        SavedStateHandle(
            mapOf(
                "storyId" to "test-story",
                "chapterNumber" to 1,
                "chapterUrl" to "https://example.com/chap-1",
            ),
        )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(NavigationUtils)
        every { NavigationUtils.decodeUrl(any()) } answers { firstArg() }

        repository = mockk(relaxed = true)
        textReplacementRepository = mockk(relaxed = true)
        readingProgressRepository = mockk(relaxed = true)
        readingStatsRepository = mockk(relaxed = true)
        pokemonRepository = mockk(relaxed = true)
        ttsManager = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)

        every { dataStore.data } returns flowOf(emptyPreferences())
        every { repository.observeChapters(any()) } returns flowOf(emptyList())
        coEvery { textReplacementRepository.getStoryReplacements(any()) } returns null
        coEvery { pokemonRepository.findMatchesInParagraphs(any()) } returns persistentMapOf()
        every { ttsManager.state } returns MutableStateFlow(TtsService.TtsState())
        every { ttsManager.skipEvent } returns MutableStateFlow(TtsService.ChapterSkipEvent.NONE)
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(NavigationUtils)
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ReaderViewModel =
        ReaderViewModel(
            savedStateHandle = savedStateHandle,
            repository = repository,
            textReplacementRepository = textReplacementRepository,
            readingProgressRepository = readingProgressRepository,
            readingStatsRepository = readingStatsRepository,
            pokemonRepository = pokemonRepository,
            ttsManager = ttsManager,
            dataStore = dataStore,
            appScope = testScope,
        )

    @Test
    fun `loadChapter displays error when repository throws exception for unsupported source`() =
        runTest {
            coEvery {
                repository.getChapterContent(any(), any(), any())
            } throws ScraperException.ParseException("Nguồn truyện này không còn hoạt động hoặc không được hỗ trợ", "https://example.com/chap-1")

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertNotNull(state.error)
            assertTrue(state.error?.contains("không còn hoạt động") == true)
        }

    @Test
    fun `loadChapter loads content successfully`() =
        runTest {
            coEvery {
                repository.getChapterContent(any(), any(), any())
            } returns ChapterContentResult(content = "Nội dung chương 1.", title = "Chương 1: Mở Đầu")

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertEquals("Chương 1: Mở Đầu", state.chapterTitle)
            assertTrue(state.content.contains("Nội dung chương 1."))
        }

    @Test
    fun `onIntent RetryLoad re-attempts loading chapter`() =
        runTest {
            coEvery {
                repository.getChapterContent(any(), any(), any())
            } throws RuntimeException("Network fail") andThen
                ChapterContentResult(content = "Nội dung thử lại thành công.", title = "Chương 1")

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertTrue(viewModel.state.value.error != null)

            viewModel.onIntent(ReaderIntent.RetryLoad)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertTrue(state.content.contains("thành công"))
        }

    @Test
    fun `TC-07 pokemonLookupEnabled defaults to true and loadChapter populates matches without altering TTS content`() =
        runTest {
            val rawParagraph = "Tiểu Trí gọi Bì Khả Khâu sử dụng Thập Vạn Phục Đặc."
            val expectedMatch =
                PokemonMatchRange(
                    start = 13,
                    end = 24,
                    matchedText = "Bì Khả Khâu",
                    entry = samplePikachuEntry,
                )
            coEvery {
                repository.getChapterContent(any(), any(), any())
            } returns ChapterContentResult(content = rawParagraph, title = "Chương 1")
            coEvery {
                pokemonRepository.findMatchesInParagraphs(any())
            } returns persistentMapOf(0 to persistentListOf(expectedMatch))

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.pokemonLookupEnabled)
            assertEquals(1, state.paragraphPokemonMatches.size)
            assertEquals("Bì Khả Khâu", state.paragraphPokemonMatches[0]?.first()?.matchedText)
            assertTrue(state.content.contains("Bì Khả Khâu"))
            verify {
                ttsManager.setContent(state.content, "Chương 1")
            }
        }

    @Test
    fun `TC-08 TogglePokemonLookup updates state and clears or recomputes matches`() =
        runTest {
            val rawParagraph = "Bì Khả Khâu xuất chiến."
            val expectedMatch =
                PokemonMatchRange(
                    start = 0,
                    end = 11,
                    matchedText = "Bì Khả Khâu",
                    entry = samplePikachuEntry,
                )
            coEvery {
                repository.getChapterContent(any(), any(), any())
            } returns ChapterContentResult(content = rawParagraph, title = "Chương 1")
            coEvery {
                pokemonRepository.findMatchesInParagraphs(any())
            } returns persistentMapOf(0 to persistentListOf(expectedMatch))

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(ReaderIntent.SelectPokemon(expectedMatch))
            assertNotNull(viewModel.state.value.selectedPokemon)

            viewModel.onIntent(ReaderIntent.TogglePokemonLookup)
            advanceUntilIdle()

            assertFalse(viewModel.state.value.pokemonLookupEnabled)
            assertTrue(
                viewModel.state.value.paragraphPokemonMatches
                    .isEmpty(),
            )
            assertNull(viewModel.state.value.selectedPokemon)

            viewModel.onIntent(ReaderIntent.TogglePokemonLookup)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.pokemonLookupEnabled)
            assertEquals(1, viewModel.state.value.paragraphPokemonMatches.size)
        }

    @Test
    fun `TC-09 SelectPokemon and DismissPokemonDialog update selectedPokemon state`() =
        runTest {
            coEvery {
                repository.getChapterContent(any(), any(), any())
            } returns ChapterContentResult(content = "Nội dung chương 1.", title = "Chương 1")

            val viewModel = createViewModel()
            advanceUntilIdle()

            val match =
                PokemonMatchRange(
                    start = 0,
                    end = 11,
                    matchedText = "Bì Khả Khâu",
                    entry = samplePikachuEntry,
                    initialFormIndex = 0,
                )
            viewModel.onIntent(ReaderIntent.SelectPokemon(match))
            assertEquals(
                PokemonSelection(
                    entry = samplePikachuEntry,
                    matchedText = "Bì Khả Khâu",
                    initialFormIndex = 0,
                ),
                viewModel.state.value.selectedPokemon,
            )

            viewModel.onIntent(ReaderIntent.DismissPokemonDialog)
            assertNull(viewModel.state.value.selectedPokemon)
        }
}
