package com.personal.apptruyen.ui.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.lifecycle.SavedStateHandle
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.ChapterContentResult
import com.personal.apptruyen.data.model.PokemonEntry
import com.personal.apptruyen.data.model.PokemonForm
import com.personal.apptruyen.data.model.PokemonMatchRange
import com.personal.apptruyen.data.model.PokemonSelection
import com.personal.apptruyen.data.model.ReadingProgress
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
        ReaderViewModel.defaultWorkDispatcher = testDispatcher
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
        coEvery { readingProgressRepository.getReadingProgress(any()) } returns null
        coEvery { pokemonRepository.findMatchesInParagraphs(any()) } returns persistentMapOf()
        every { ttsManager.state } returns MutableStateFlow(TtsService.TtsState())
        every { ttsManager.skipEvent } returns MutableStateFlow(TtsService.ChapterSkipEvent.NONE)
    }

    @AfterEach
    fun tearDown() {
        ReaderViewModel.defaultWorkDispatcher = Dispatchers.Default
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

    @Test
    fun `loadChapter restores initialScrollIndex when savedProgress matches current chapter and resets when chapter differs`() =
        runTest {
            val ch1 = Chapter(storyId = "test-story", chapterNumber = 1, title = "Chương 1", url = "https://example.com/chap-1")
            val ch2 = Chapter(storyId = "test-story", chapterNumber = 2, title = "Chương 2", url = "https://example.com/chap-2")
            every { repository.observeChapters("test-story") } returns flowOf(listOf(ch1, ch2))
            coEvery {
                readingProgressRepository.getReadingProgress("test-story")
            } returns ReadingProgress(storyId = "test-story", lastChapterNumber = 1, scrollPosition = 14)
            coEvery {
                repository.getChapterContent("test-story", 1, any())
            } returns ChapterContentResult(content = "Đoạn 1\n\nĐoạn 2", title = "Chương 1")
            coEvery {
                repository.getChapterContent("test-story", 2, any())
            } returns ChapterContentResult(content = "Chương 2 đoạn 1\n\nChương 2 đoạn 2", title = "Chương 2")

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(14, viewModel.state.value.initialScrollIndex)
            coVerify {
                readingProgressRepository.saveReadingProgress(
                    match { it.storyId == "test-story" && it.lastChapterNumber == 1 && it.scrollPosition == 14 },
                )
            }

            // Navigate to chapter 2 -> scrollPosition should reset to 0
            viewModel.onIntent(ReaderIntent.NextChapter)
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.currentChapterNumber)
            assertEquals(0, viewModel.state.value.initialScrollIndex)
            coVerify {
                readingProgressRepository.saveReadingProgress(
                    match { it.storyId == "test-story" && it.lastChapterNumber == 2 && it.scrollPosition == 0 },
                )
            }
        }

    @Test
    fun `SaveScrollPosition updates currentScrollPosition and onCleared preserves it`() =
        runTest {
            coEvery {
                repository.getChapterContent(any(), any(), any())
            } returns ChapterContentResult(content = "Đoạn 1\n\nĐoạn 2", title = "Chương 1")

            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(ReaderIntent.SaveScrollPosition(22))
            assertEquals(22, viewModel.state.value.initialScrollIndex)

            val onClearedMethod = ReaderViewModel::class.java.getDeclaredMethod("onCleared")
            onClearedMethod.isAccessible = true
            onClearedMethod.invoke(viewModel)

            coVerify(atLeast = 2) {
                readingProgressRepository.saveReadingProgress(
                    match { it.storyId == "test-story" && it.lastChapterNumber == 1 && it.scrollPosition == 22 },
                )
            }
        }

    @Test
    fun `init awaits loadDefaultSettings before processing first chapter content`() =
        runTest {
            val prefsGate = CompletableDeferred<Preferences>()
            every { dataStore.data } returns flow { emit(prefsGate.await()) }
            coEvery {
                repository.getChapterContent(any(), any(), any())
            } returns ChapterContentResult(content = "Bì Khả Khâu phóng điện.", title = "Chương 1")

            val viewModel = createViewModel()
            // Before settings finish loading, chapter processing should wait at settingsLoadedDeferred
            assertTrue(viewModel.state.value.isLoading)

            prefsGate.complete(
                preferencesOf(
                    SettingsKeys.TEXT_REPLACEMENT_ENABLED to true,
                    SettingsKeys.CUSTOM_REPLACEMENTS to "[{\"from\":\"Bì Khả Khâu\",\"to\":\"Pikachu\"}]",
                ),
            )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertTrue(
                viewModel.state.value.content
                    .contains("Pikachu"),
            )
        }

    @Test
    fun `rapid chapter navigation cancels previous loadChapterJob and settles on latest chapter`() =
        runTest {
            val ch1 = Chapter(storyId = "test-story", chapterNumber = 1, title = "Chương 1", url = "https://example.com/chuong-1")
            val ch2 = Chapter(storyId = "test-story", chapterNumber = 2, title = "Chương 2", url = "https://example.com/chuong-2")
            val ch3 = Chapter(storyId = "test-story", chapterNumber = 3, title = "Chương 3", url = "https://example.com/chuong-3")
            every { repository.observeChapters("test-story") } returns flowOf(listOf(ch1, ch2, ch3))

            val ch2Gate = CompletableDeferred<ChapterContentResult>()
            coEvery {
                repository.getChapterContent("test-story", 1, any())
            } returns ChapterContentResult(content = "Nội dung 1", title = "Chương 1")
            coEvery {
                repository.getChapterContent("test-story", 2, any())
            } coAnswers { ch2Gate.await() }
            coEvery {
                repository.getChapterContent("test-story", 3, any())
            } returns ChapterContentResult(content = "Nội dung 3", title = "Chương 3")

            val viewModel = createViewModel()
            advanceUntilIdle()

            // First NextChapter starts loading chapter 2 (which suspends on ch2Gate)
            viewModel.onIntent(ReaderIntent.NextChapter)
            assertEquals(2, viewModel.state.value.currentChapterNumber)
            assertTrue(viewModel.state.value.isLoading)

            // Immediately press NextChapter again -> moves to chapter 3 and cancels chapter 2 job
            viewModel.onIntent(ReaderIntent.NextChapter)
            advanceUntilIdle()

            assertEquals(3, viewModel.state.value.currentChapterNumber)
            assertFalse(viewModel.state.value.isLoading)
            assertEquals("Chương 3", viewModel.state.value.chapterTitle)
            assertTrue(
                viewModel.state.value.content
                    .contains("Nội dung 3"),
            )
        }

    @Test
    fun `ToggleAutoFormat and ToggleTextReplacement reprocess from raw cache without refetching network`() =
        runTest {
            coEvery {
                repository.getChapterContent("test-story", 1, any())
            } returns ChapterContentResult(content = "Câu 1.Câu 2 của Bổn tọa.", title = "Chương 1")

            val viewModel = createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.getChapterContent("test-story", 1, any()) }

            viewModel.onIntent(ReaderIntent.ToggleAutoFormat)
            advanceUntilIdle()
            viewModel.onIntent(ReaderIntent.ToggleTextReplacement)
            advanceUntilIdle()

            // Still only 1 network/repo call because reprocessCurrentChapter uses raw contentCache
            coVerify(exactly = 1) { repository.getChapterContent("test-story", 1, any()) }
            assertFalse(viewModel.state.value.autoFormatEnabled)
            assertFalse(viewModel.state.value.textReplacementEnabled)
        }

    @Test
    fun `OnReaderPaused flushes reading session and excludes background duration before OnReaderResumed`() =
        runTest {
            var fakeNow = 1_000_000L
            coEvery {
                repository.getChapterContent(any(), any(), any())
            } returns ChapterContentResult(content = "Nội dung chương 1.", title = "Chương 1")

            val viewModel = createViewModel()
            viewModel.timeProvider = { fakeNow }
            // Reset session start with fakeNow via Pause -> Resume
            viewModel.onIntent(ReaderIntent.OnReaderPaused)
            viewModel.onIntent(ReaderIntent.OnReaderResumed)

            // Read for 10 seconds -> Pause
            fakeNow += 10_000L
            viewModel.onIntent(ReaderIntent.OnReaderPaused)

            // App stays in background for 1 hour -> Resume -> Read 6 seconds -> Pause
            fakeNow += 3_600_000L
            viewModel.onIntent(ReaderIntent.OnReaderResumed)
            fakeNow += 6_000L
            viewModel.onIntent(ReaderIntent.OnReaderPaused)

            coVerify(exactly = 1) { readingStatsRepository.recordReadingSession(10_000L, "test-story") }
            coVerify(exactly = 1) { readingStatsRepository.recordReadingSession(6_000L, "test-story") }
        }

    @Test
    fun `recordChapterRead is called across NextChapter LoadNextOnScroll NavigateToChapter and TTS autoNextChapter`() =
        runTest {
            val ttsStateFlow = MutableStateFlow(TtsService.TtsState())
            every { ttsManager.state } returns ttsStateFlow
            val chapters =
                (1..5).map { num ->
                    Chapter(storyId = "test-story", chapterNumber = num, title = "Chương $num", url = "https://example.com/chuong-$num")
                }
            every { repository.observeChapters("test-story") } returns flowOf(chapters)
            coEvery {
                repository.getChapterContent(any(), any(), any())
            } returns ChapterContentResult(content = "Nội dung", title = "Chương")

            val viewModel = createViewModel()
            advanceUntilIdle()

            // 1. NextChapter (1 -> 2)
            viewModel.onIntent(ReaderIntent.NextChapter)
            advanceUntilIdle()

            // 2. LoadNextOnScroll (2 -> 3)
            viewModel.onIntent(ReaderIntent.LoadNextOnScroll)
            advanceUntilIdle()

            // 3. NavigateToChapter (3 -> 4)
            viewModel.onIntent(ReaderIntent.NavigateToChapter(chapters[3]))
            advanceUntilIdle()

            // Navigating to the SAME chapter (4 -> 4) should NOT increment recordChapterRead
            viewModel.onIntent(ReaderIntent.NavigateToChapter(chapters[3]))
            advanceUntilIdle()

            // 4. TTS autoNextChapter (4 -> 5): playing at last paragraph -> stopped
            ttsStateFlow.value =
                TtsService.TtsState(
                    isPlaying = true,
                    isPaused = false,
                    currentParagraph = 2,
                    totalParagraphs = 3,
                )
            ttsStateFlow.value =
                TtsService.TtsState(
                    isPlaying = false,
                    isPaused = false,
                    currentParagraph = 2,
                    totalParagraphs = 3,
                )
            advanceUntilIdle()

            coVerify(exactly = 4) { readingStatsRepository.recordChapterRead() }
        }

    @Test
    fun `SendReplacementToGlobal updates in-memory replacements reprocesses chapter and emits Vietnamese snackbarMessage`() =
        runTest {
            coEvery {
                repository.getChapterContent("test-story", 1, any())
            } returns ChapterContentResult(content = "Hắn nhìn nàn mỉm cười.", title = "Chương 1")
            coEvery { textReplacementRepository.sendReplacementToGlobal("nàn", "nàng") } returnsMany listOf(true, false)

            val viewModel = createViewModel()
            advanceUntilIdle()

            // First time: added = true
            viewModel.onIntent(ReaderIntent.SendReplacementToGlobal("nàn", "nàng"))
            advanceUntilIdle()

            assertEquals(
                "Đã thêm \"nàn\" → \"nàng\" vào cài đặt thay chữ chung",
                viewModel.state.value.snackbarMessage,
            )
            assertTrue(
                viewModel.state.value.content
                    .contains("Hắn nhìn nàng mỉm cười."),
            )

            // Second time: added = false (already exists)
            viewModel.onIntent(ReaderIntent.SendReplacementToGlobal("nàn", "nàng"))
            advanceUntilIdle()

            assertEquals(
                "Từ \"nàn\" → \"nàng\" đã tồn tại trong cài đặt chung",
                viewModel.state.value.snackbarMessage,
            )

            viewModel.onIntent(ReaderIntent.ClearSnackbar)
            assertNull(viewModel.state.value.snackbarMessage)
        }
}
