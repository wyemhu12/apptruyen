package com.personal.apptruyen.ui.reader

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.local.getIntSafe
import com.personal.apptruyen.data.local.healCorruptedIntPreferences
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.ChapterContentResult
import com.personal.apptruyen.data.model.PokemonMatchRange
import com.personal.apptruyen.data.model.PokemonSelection
import com.personal.apptruyen.data.model.ReadingProgress
import com.personal.apptruyen.data.remote.ScraperException
import com.personal.apptruyen.data.repository.IPokemonRepository
import com.personal.apptruyen.data.repository.IReadingProgressRepository
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.ITextReplacementRepository
import com.personal.apptruyen.data.repository.ReadingStatsRepository
import com.personal.apptruyen.di.ApplicationScope
import com.personal.apptruyen.tts.TtsManager
import com.personal.apptruyen.tts.TtsService
import com.personal.apptruyen.ui.navigation.NavigationUtils
import com.personal.apptruyen.util.TextReplacementHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * MVI Intent — tất cả user actions đi qua đây.
 */
sealed interface ReaderIntent {
    // Navigation
    object RetryLoad : ReaderIntent

    object NextChapter : ReaderIntent

    object PrevChapter : ReaderIntent

    object LoadNextOnScroll : ReaderIntent

    object LoadPrevOnScroll : ReaderIntent

    data class NavigateToChapter(
        val chapter: com.personal.apptruyen.data.model.Chapter,
    ) : ReaderIntent

    // Settings
    data class SetFontSize(
        val size: Float,
    ) : ReaderIntent

    data class SetLineSpacing(
        val spacing: Float,
    ) : ReaderIntent

    data class SetReaderTheme(
        val theme: com.personal.apptruyen.ui.reader.ReaderViewModel.ReaderTheme,
    ) : ReaderIntent

    data class SetFontFamily(
        val family: com.personal.apptruyen.ui.reader.ReaderViewModel.ReaderFontFamily,
    ) : ReaderIntent

    // UI toggles
    object ToggleSettings : ReaderIntent

    object ToggleBars : ReaderIntent

    object ToggleChapterList : ReaderIntent

    object ToggleCopyMode : ReaderIntent

    // Text replacement & format
    object ToggleTextReplacement : ReaderIntent

    object ToggleAutoFormat : ReaderIntent

    object ToggleStoryReplacements : ReaderIntent

    data class AddStoryReplacement(
        val from: String,
        val to: String,
    ) : ReaderIntent

    data class RemoveStoryReplacement(
        val index: Int,
    ) : ReaderIntent

    data class SendReplacementToGlobal(
        val from: String,
        val to: String,
    ) : ReaderIntent

    // Scroll
    data class SaveScrollPosition(
        val position: Int,
    ) : ReaderIntent

    // Auto-scroll
    object ToggleAutoScroll : ReaderIntent

    data class SetAutoScrollSpeed(
        val speed: Int,
    ) : ReaderIntent

    // Pokemon lookup
    object TogglePokemonLookup : ReaderIntent

    data class SelectPokemon(
        val match: PokemonMatchRange,
    ) : ReaderIntent

    object DismissPokemonDialog : ReaderIntent

    // Lifecycle & UI feedback
    object OnReaderResumed : ReaderIntent

    object OnReaderPaused : ReaderIntent

    object ClearSnackbar : ReaderIntent
}

@HiltViewModel
class ReaderViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: IStoryRepository,
        private val textReplacementRepository: ITextReplacementRepository,
        private val readingProgressRepository: IReadingProgressRepository,
        private val readingStatsRepository: ReadingStatsRepository,
        private val pokemonRepository: IPokemonRepository,
        private val ttsManager: TtsManager,
        private val dataStore: DataStore<Preferences>,
        @ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
    ) : ViewModel() {

        private val storyId: String = NavigationUtils.decodeRouteParam(savedStateHandle["storyId"] ?: "")
        private val chapterNumber: Int = savedStateHandle["chapterNumber"] ?: 1
        private val chapterUrl: String = NavigationUtils.decodeUrl(savedStateHandle["chapterUrl"] ?: "")

        // ── TTS public API (decoupled from TtsManager) ──
        val ttsState: StateFlow<TtsService.TtsState> = ttsManager.state
        val ttsSkipEvent: StateFlow<TtsService.ChapterSkipEvent> = ttsManager.skipEvent

        fun ttsPlay() = ttsManager.play()

        fun ttsPause() = ttsManager.pause()

        fun ttsStop() = ttsManager.stop()

        fun ttsCycleSpeed() = ttsManager.cycleSpeed()

        fun ttsSetSpeed(speed: Float) {
            ttsManager.setSpeed(speed)
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.TTS_SPEED] = speed }
            }
        }

        fun ttsSetPitch(pitch: Float) {
            ttsManager.setPitch(pitch)
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.TTS_PITCH] = pitch }
            }
        }

        fun ttsSeekAndPlay(index: Int) = ttsManager.seekAndPlay(index)

        data class ReaderState(
            val chapterTitle: String = "",
            val content: String = "",
            val paragraphs: ImmutableList<String> = persistentListOf(),
            val isLoading: Boolean = true,
            val error: String? = null,
            val currentChapterNumber: Int = 1,
            val initialScrollIndex: Int = 0,
            val fontSize: Float = 18f,
            val readerTheme: ReaderTheme = ReaderTheme.LIGHT,
            val showSettings: Boolean = false,
            val isLoadingNextChapter: Boolean = false,
            val isLoadingPrevChapter: Boolean = false,
            val hasNextChapter: Boolean = true,
            val hasPrevChapter: Boolean = false,
            val totalChapters: Int = 0,
            val scrollToEnd: Boolean = false,
            val areBarsVisible: Boolean = true,
            val showChapterList: Boolean = false,
            val chapterList: ImmutableList<Chapter> = persistentListOf(),
            val copyMode: Boolean = false,
            val textReplacementEnabled: Boolean = true,
            val autoFormatEnabled: Boolean = true,
            val lineSpacing: Float = 1.6f,
            val fontFamily: ReaderFontFamily = ReaderFontFamily.NOTO_SERIF,
            // Per-story text replacement
            val storyReplacementsEnabled: Boolean = false,
            val storyReplacements: ImmutableList<Pair<String, String>> = persistentListOf(),
            // Auto-scroll
            val autoScrollEnabled: Boolean = false,
            val autoScrollSpeed: Int = 3, // 1-10
            // Pokemon lookup
            val pokemonLookupEnabled: Boolean = true,
            val paragraphPokemonMatches: ImmutableMap<Int, ImmutableList<PokemonMatchRange>> = persistentMapOf(),
            val selectedPokemon: PokemonSelection? = null,
            val snackbarMessage: String? = null,
        )

        enum class ReaderTheme { LIGHT, DARK, SEPIA }

        enum class ReaderFontFamily(
            val label: String,
        ) {
            NOTO_SERIF("Noto Serif"),
            ROBOTO("Roboto"),
            BE_VIETNAM_PRO("Be Vietnam Pro"),
            SYSTEM("Mặc định"),
        }

        private val _state = MutableStateFlow(ReaderState(currentChapterNumber = chapterNumber))
        val state: StateFlow<ReaderState> = _state.asStateFlow()

        private var chapters: List<Chapter> = emptyList()
        private var currentChapterUrl: String = chapterUrl // Track for retry
        private var customReplacements: List<Pair<String, String>> = emptyList()
        private var storyReplacementsEnabled: Boolean = false
        private var storyCustomReplacements: List<Pair<String, String>> = emptyList()

        /** In-memory cache cho prefetch. Key = chapterNumber, Value = raw content. Đóng app mất. */
        private val contentCache = java.util.concurrent.ConcurrentHashMap<Int, ChapterContentResult>()
        private var prefetchCount = 0
        private val settingsLoadedDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()
        private var loadChapterJob: Job? = null
        private var reprocessJob: Job? = null

        @Volatile
        private var currentScrollPosition: Int = 0

        internal var workDispatcher: kotlinx.coroutines.CoroutineDispatcher = defaultWorkDispatcher
        internal var timeProvider: () -> Long = { System.currentTimeMillis() }
        private var sessionStartTime: Long = timeProvider()

        /** MVI entry point — route all user intents. */
        fun onIntent(intent: ReaderIntent) {
            when (intent) {
                is ReaderIntent.RetryLoad -> retryLoadChapter()
                is ReaderIntent.NextChapter -> nextChapter()
                is ReaderIntent.PrevChapter -> prevChapter()
                is ReaderIntent.LoadNextOnScroll -> loadNextChapterOnScroll()
                is ReaderIntent.LoadPrevOnScroll -> loadPrevChapterOnScroll()
                is ReaderIntent.NavigateToChapter -> navigateToChapter(intent.chapter)
                is ReaderIntent.SetFontSize -> setFontSize(intent.size)
                is ReaderIntent.SetLineSpacing -> setLineSpacing(intent.spacing)
                is ReaderIntent.SetReaderTheme -> setReaderTheme(intent.theme)
                is ReaderIntent.SetFontFamily -> setFontFamily(intent.family)
                is ReaderIntent.ToggleSettings -> toggleSettings()
                is ReaderIntent.ToggleBars -> toggleBarsVisibility()
                is ReaderIntent.ToggleChapterList -> toggleChapterList()
                is ReaderIntent.ToggleCopyMode -> toggleCopyMode()
                is ReaderIntent.ToggleTextReplacement -> toggleTextReplacement()
                is ReaderIntent.ToggleAutoFormat -> toggleAutoFormat()
                is ReaderIntent.ToggleStoryReplacements -> toggleStoryReplacements()
                is ReaderIntent.AddStoryReplacement -> addStoryReplacement(intent.from, intent.to)
                is ReaderIntent.RemoveStoryReplacement -> removeStoryReplacement(intent.index)
                is ReaderIntent.SendReplacementToGlobal ->
                    viewModelScope.launch {
                        sendReplacementToGlobal(
                            intent.from,
                            intent.to,
                        )
                    }
                is ReaderIntent.SaveScrollPosition -> saveScrollPosition(intent.position)
                is ReaderIntent.ToggleAutoScroll -> toggleAutoScroll()
                is ReaderIntent.SetAutoScrollSpeed -> setAutoScrollSpeed(intent.speed)
                is ReaderIntent.TogglePokemonLookup -> togglePokemonLookup()
                is ReaderIntent.SelectPokemon -> selectPokemon(intent.match)
                is ReaderIntent.DismissPokemonDialog -> dismissPokemonDialog()
                is ReaderIntent.OnReaderResumed -> onReaderResumed()
                is ReaderIntent.OnReaderPaused -> onReaderPaused()
                is ReaderIntent.ClearSnackbar -> clearSnackbar()
            }
        }

        init {
            loadDefaultSettings()
            loadChapter(chapterNumber, chapterUrl)
            loadChapterList()
            observeTtsForAutoAdvance()
            observeSkipEvents()
        }

        /**
         * Read default settings from DataStore and apply.
         */
        private fun loadDefaultSettings() {
            viewModelScope.launch {
                try {
                    try {
                        dataStore.healCorruptedIntPreferences()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Timber.tag("ReaderVM").w(e, "Failed to heal corrupted Int preferences")
                    }
                    dataStore.data.first().let { prefs ->
                        val defaultSpeed = prefs[SettingsKeys.TTS_SPEED] ?: 1.0f
                        val defaultPitch = prefs[SettingsKeys.TTS_PITCH] ?: 1.0f
                        val defaultVoice = prefs[SettingsKeys.TTS_VOICE] ?: ""
                        val defaultFontSize = prefs[SettingsKeys.FONT_SIZE] ?: 18f
                        val defaultTheme = prefs.getIntSafe(SettingsKeys.THEME, 0)
                        val replacementEnabled = prefs[SettingsKeys.TEXT_REPLACEMENT_ENABLED] ?: true
                        val autoFormatEnabled = prefs[SettingsKeys.AUTO_FORMAT_ENABLED] ?: true
                        val customJson = prefs[SettingsKeys.CUSTOM_REPLACEMENTS]
                        val defaultLineSpacing = prefs[SettingsKeys.LINE_SPACING] ?: 1.6f
                        val fontFamilyOrdinal = prefs.getIntSafe(SettingsKeys.FONT_FAMILY, 0)
                        prefetchCount = prefs.getIntSafe(SettingsKeys.PREFETCH_CHAPTERS, 0)
                        val savedAutoScrollSpeed = prefs.getIntSafe(SettingsKeys.AUTO_SCROLL_SPEED, 3)
                        val pokemonLookupEnabled = prefs[SettingsKeys.POKEMON_LOOKUP_ENABLED] ?: true

                        ttsManager.setSpeed(defaultSpeed)
                        ttsManager.setPitch(defaultPitch)
                        if (defaultVoice.isNotBlank()) {
                            ttsManager.setVoice(defaultVoice)
                        }
                        customReplacements = TextReplacementHelper.parseCustomReplacements(customJson)

                        // Load per-story replacements from Room
                        val storyData = textReplacementRepository.getStoryReplacements(storyId)
                        if (storyData != null) {
                            storyReplacementsEnabled = storyData.storyReplacementsEnabled
                            storyCustomReplacements =
                                TextReplacementHelper.parseCustomReplacements(
                                    storyData.storyCustomReplacements,
                                )
                        }

                        val theme =
                            when (defaultTheme) {
                                1 -> ReaderTheme.DARK
                                2 -> ReaderTheme.SEPIA
                                else -> ReaderTheme.LIGHT
                            }
                        _state.update {
                            it.copy(
                                fontSize = defaultFontSize,
                                readerTheme = theme,
                                textReplacementEnabled = replacementEnabled,
                                autoFormatEnabled = autoFormatEnabled,
                                lineSpacing = defaultLineSpacing,
                                fontFamily =
                                    ReaderFontFamily.entries.getOrElse(
                                        fontFamilyOrdinal,
                                    ) { ReaderFontFamily.NOTO_SERIF },
                                storyReplacementsEnabled = storyReplacementsEnabled,
                                storyReplacements = storyCustomReplacements.toImmutableList(),
                                autoScrollSpeed = savedAutoScrollSpeed,
                                pokemonLookupEnabled = pokemonLookupEnabled,
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.tag("ReaderVM").w(e, "Failed to load default settings")
                } finally {
                    settingsLoadedDeferred.complete(Unit)
                }
            }
        }

        /**
         * Watch TTS state — when finished reading all paragraphs,
         * auto-advance to next chapter and continue playing.
         */
        private fun observeTtsForAutoAdvance() {
            viewModelScope.launch {
                var prevPlaying = false

                ttsManager.state.collect { ttsState ->
                    // Detect: was playing -> stopped -> at last paragraph = chapter finished
                    val atLastParagraph =
                        ttsState.totalParagraphs > 0 &&
                            ttsState.currentParagraph >= ttsState.totalParagraphs - 1
                    val justStopped = prevPlaying && !ttsState.isPlaying && !ttsState.isPaused

                    if (justStopped && atLastParagraph) {
                        // Auto-advance to next chapter
                        autoNextChapter()
                    }

                    prevPlaying = ttsState.isPlaying
                }
            }
        }

        private fun autoNextChapter() {
            val currentNum = _state.value.currentChapterNumber
            val nextNum = currentNum + 1
            val nextChapter = chapters.find { it.chapterNumber == nextNum }
            if (nextChapter != null) {
                recordChapterReadOnTransition(nextNum)
                loadChapterAndPlay(nextNum, nextChapter.url)
            } else {
                val constructedUrl = tryConstructChapterUrl(chapterUrl, nextNum)
                if (constructedUrl != null) {
                    recordChapterReadOnTransition(nextNum)
                    loadChapterAndPlay(nextNum, constructedUrl)
                }
            }
        }

        private fun loadChapterList() {
            viewModelScope.launch {
                repository.observeChapters(storyId).collect { list ->
                    if (list.isNotEmpty()) {
                        chapters = list
                        val maxChapter = list.maxOf { it.chapterNumber }
                        _state.value =
                            _state.value.copy(
                                totalChapters = maxChapter,
                                hasNextChapter = _state.value.currentChapterNumber < maxChapter,
                                hasPrevChapter = _state.value.currentChapterNumber > 1,
                                chapterList = list.toImmutableList(),
                            )
                    }
                }
            }
        }

        /**
         * Listen for chapter skip events from notification/MediaSession controls.
         * When user taps next at last paragraph or prev at first paragraph,
         * navigate to next/prev chapter.
         */
        private fun observeSkipEvents() {
            viewModelScope.launch {
                ttsManager.skipEvent.collect { event ->
                    when (event) {
                        TtsService.ChapterSkipEvent.NEXT -> {
                            ttsManager.consumeSkipEvent()
                            nextChapter()
                        }
                        TtsService.ChapterSkipEvent.PREV -> {
                            ttsManager.consumeSkipEvent()
                            prevChapter()
                        }
                        TtsService.ChapterSkipEvent.NONE -> { /* no-op */ }
                    }
                }
            }
        }

        private suspend fun processRawChapterContent(
            rawContent: String,
            autoFormat: Boolean,
            textReplace: Boolean,
            globalCustom: List<Pair<String, String>>,
            storyReplace: Boolean,
            storyCustom: List<Pair<String, String>>,
        ): Pair<String, ImmutableList<String>> =
            kotlinx.coroutines.withContext(workDispatcher) {
                var processed = rawContent
                if (autoFormat) {
                    processed =
                        com.personal.apptruyen.util.TextFormatHelper
                            .formatChapter(processed)
                }
                processed =
                    TextReplacementHelper.applyAllReplacements(
                        processed,
                        textReplace,
                        globalCustom,
                        storyReplace,
                        storyCustom,
                    )
                val splitParagraphs =
                    if (processed.isBlank()) {
                        persistentListOf()
                    } else {
                        TtsManager.splitIntoParagraphs(processed).toImmutableList()
                    }
                processed to splitParagraphs
            }

        private fun reprocessCurrentChapter() {
            val currentNum = _state.value.currentChapterNumber
            val cachedRaw = contentCache[currentNum]
            if (cachedRaw == null) {
                if (!_state.value.isLoading) {
                    loadChapter(currentNum, currentChapterUrl)
                }
                return
            }
            reprocessJob?.cancel()
            reprocessJob =
                viewModelScope.launch {
                    try {
                        settingsLoadedDeferred.await()
                        val s = _state.value
                        val (processed, splitParagraphs) =
                            processRawChapterContent(
                                rawContent = cachedRaw.content,
                                autoFormat = s.autoFormatEnabled,
                                textReplace = s.textReplacementEnabled,
                                globalCustom = customReplacements,
                                storyReplace = storyReplacementsEnabled,
                                storyCustom = storyCustomReplacements,
                            )
                        _state.update {
                            it.copy(
                                content = processed,
                                paragraphs = splitParagraphs,
                                initialScrollIndex = currentScrollPosition,
                            )
                        }
                        refreshPokemonMatches(splitParagraphs, _state.value.pokemonLookupEnabled)
                        if (!ttsManager.state.value.isPlaying && !ttsManager.state.value.isPaused) {
                            ttsManager.setContent(processed, _state.value.chapterTitle)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }
        }

        private fun trimContentCache(protectedChapterNumber: Int) {
            val maxCacheEntries = maxOf(prefetchCount + 3, 5)
            while (contentCache.size > maxCacheEntries) {
                val oldest =
                    contentCache
                        .keys()
                        .toList()
                        .filter { it != protectedChapterNumber }
                        .minOrNull() ?: break
                contentCache.remove(oldest)
            }
        }

        private fun loadChapter(
            number: Int,
            url: String,
            autoPlay: Boolean = false,
            scrollToEnd: Boolean = false,
        ) {
            loadChapterJob?.cancel()
            reprocessJob?.cancel()
            pokemonMatchJob?.cancel()

            if (number != _state.value.currentChapterNumber || scrollToEnd) {
                currentScrollPosition = 0
            }
            val resolvedUrl = chapters.find { it.chapterNumber == number }?.url?.takeIf { it.isNotBlank() } ?: url
            currentChapterUrl = resolvedUrl

            _state.update {
                it.copy(
                    currentChapterNumber = number,
                    isLoading = true,
                    error = null,
                    isLoadingNextChapter = false,
                    scrollToEnd = false,
                    initialScrollIndex = 0,
                    selectedPokemon = null,
                    paragraphPokemonMatches = persistentMapOf(),
                )
            }

            loadChapterJob =
                viewModelScope.launch {
                    try {
                        // Check in-memory raw cache first (keep entry so reprocessCurrentChapter works offline)
                        val cachedResult = contentCache[number]
                        val result =
                            cachedResult ?: repository.getChapterContent(storyId, number, resolvedUrl).also { fetched ->
                                contentCache[number] = fetched
                                trimContentCache(number)
                            }

                        settingsLoadedDeferred.await()

                        val savedProgress =
                            runCatching {
                                readingProgressRepository.getReadingProgress(storyId)
                            }.getOrNull()
                        if (!scrollToEnd &&
                            savedProgress != null &&
                            savedProgress.lastChapterNumber == number &&
                            savedProgress.scrollPosition > 0
                        ) {
                            currentScrollPosition = savedProgress.scrollPosition
                        } else if (scrollToEnd) {
                            currentScrollPosition = 0
                        }

                        val (content, splitParagraphs) =
                            processRawChapterContent(
                                rawContent = result.content,
                                autoFormat = _state.value.autoFormatEnabled,
                                textReplace = _state.value.textReplacementEnabled,
                                globalCustom = customReplacements,
                                storyReplace = storyReplacementsEnabled,
                                storyCustom = storyCustomReplacements,
                            )

                        val chapter = chapters.find { it.chapterNumber == number }
                        // Use extracted title if available, otherwise fall back to chapter.title
                        val title =
                            result.title?.takeIf { it.isNotBlank() }
                                ?: chapter?.title ?: "Chương $number"
                        val maxChapter = if (chapters.isNotEmpty()) chapters.maxOf { it.chapterNumber } else 0
                        _state.update {
                            it.copy(
                                chapterTitle = title,
                                content = content,
                                paragraphs = splitParagraphs,
                                isLoading = false,
                                isLoadingPrevChapter = false,
                                scrollToEnd = scrollToEnd,
                                initialScrollIndex = currentScrollPosition,
                                currentChapterNumber = number,
                                hasNextChapter = maxChapter == 0 || number < maxChapter,
                                hasPrevChapter = number > 1,
                            )
                        }
                        refreshPokemonMatches(splitParagraphs, _state.value.pokemonLookupEnabled)

                        // Set content for TTS
                        if (autoPlay) {
                            // Auto-advance: don't stop foreground service to avoid
                            // Android 12+ startForeground restriction from background
                            ttsManager.setContentForAutoAdvance(content, title)
                        } else {
                            ttsManager.setContent(content, title)
                        }

                        // Auto-play if requested
                        if (autoPlay) {
                            ttsManager.play()
                        }

                        // Save reading progress (preserve restored/current scroll position)
                        readingProgressRepository.saveReadingProgress(
                            ReadingProgress(
                                storyId = storyId,
                                lastChapterNumber = number,
                                scrollPosition = currentScrollPosition,
                            ),
                        )

                        // Prefetch next N chapters in background
                        prefetchAhead(number)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isLoadingNextChapter = false,
                                isLoadingPrevChapter = false,
                                error = (e as? ScraperException)?.message ?: "Không thể tải nội dung chương. Vui lòng kiểm tra kết nối mạng.",
                            )
                        }
                    }
                }
        }

        /**
         * Prefetch tiếp N chương vào bộ nhớ tạm (in-memory).
         * Không lưu vào Room — đóng app mất cache.
         * Không xung đột với download offline vì lưu ở ConcurrentHashMap riêng.
         * Cancel prefetch cũ khi user chuyển chương mới.
         */
        private var prefetchJob: kotlinx.coroutines.Job? = null

        private fun prefetchAhead(currentNumber: Int) {
            if (prefetchCount <= 0) return
            // Cancel prefetch trước đó nếu user chuyển chương nhanh
            prefetchJob?.cancel()
            prefetchJob =
                viewModelScope.launch {
                    for (offset in 1..prefetchCount) {
                        val nextNum = currentNumber + offset
                        if (contentCache.containsKey(nextNum)) continue // đã cache rồi
                        val nextChapter = chapters.find { it.chapterNumber == nextNum } ?: continue
                        try {
                            val result = repository.getChapterContent(storyId, nextNum, nextChapter.url)
                            contentCache[nextNum] = result
                            trimContentCache(_state.value.currentChapterNumber)
                            Timber.tag("ReaderVM").d("Prefetched chapter %d into memory cache", nextNum)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Timber.tag("ReaderVM").d("Prefetch chapter %d failed: %s", nextNum, e.message)
                            break // Dừng prefetch nếu lỗi mạng
                        }
                    }
                }
        }

        /** Retry loading the current chapter (called from error state UI) */
        fun retryLoadChapter() {
            loadChapter(_state.value.currentChapterNumber, currentChapterUrl)
        }

        /**
         * Load chapter and always start TTS playback.
         */
        private fun loadChapterAndPlay(
            number: Int,
            url: String,
        ) {
            loadChapter(number, url, autoPlay = true)
        }

        private fun recordChapterReadOnTransition(targetChapterNumber: Int) {
            if (targetChapterNumber == _state.value.currentChapterNumber) return
            viewModelScope.launch {
                try {
                    readingStatsRepository.recordChapterRead()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }

        fun nextChapter() {
            val currentNum = _state.value.currentChapterNumber
            val nextNum = currentNum + 1
            val nextChapter = chapters.find { it.chapterNumber == nextNum }
            // Only auto-play if TTS was already active (playing or paused)
            val wasActive = ttsManager.state.value.isPlaying || ttsManager.state.value.isPaused
            // Use pause() when active to keep foreground service alive (Android 12+)
            if (wasActive) ttsManager.pause() else ttsManager.stop()
            if (nextChapter != null) {
                recordChapterReadOnTransition(nextNum)
                loadChapter(nextNum, nextChapter.url, autoPlay = wasActive)
            } else {
                val constructedUrl = tryConstructChapterUrl(chapterUrl, nextNum)
                if (constructedUrl != null) {
                    recordChapterReadOnTransition(nextNum)
                    loadChapter(nextNum, constructedUrl, autoPlay = wasActive)
                }
            }
        }

        /**
         * Load next chapter triggered by scrolling to the end.
         * Does NOT auto-play TTS. Sets isLoadingNextChapter for UI indicator.
         */
        fun loadNextChapterOnScroll() {
            if (_state.value.isLoadingNextChapter || _state.value.isLoading) return
            val currentNum = _state.value.currentChapterNumber
            val nextNum = currentNum + 1
            val nextChapter = chapters.find { it.chapterNumber == nextNum }
            _state.value = _state.value.copy(isLoadingNextChapter = true, scrollToEnd = false)
            ttsManager.stop()
            if (nextChapter != null) {
                recordChapterReadOnTransition(nextNum)
                loadChapter(nextNum, nextChapter.url, autoPlay = false)
            } else {
                val constructedUrl = tryConstructChapterUrl(chapterUrl, nextNum)
                if (constructedUrl != null) {
                    recordChapterReadOnTransition(nextNum)
                    loadChapter(nextNum, constructedUrl, autoPlay = false)
                } else {
                    _state.value = _state.value.copy(isLoadingNextChapter = false, hasNextChapter = false)
                }
            }
        }

        /**
         * Load previous chapter triggered by scrolling to the top.
         * Does NOT auto-play TTS.
         */
        fun loadPrevChapterOnScroll() {
            if (_state.value.isLoadingPrevChapter || _state.value.isLoading || _state.value.isLoadingNextChapter) return
            val currentNum = _state.value.currentChapterNumber
            if (currentNum <= 1) return
            val prevNum = currentNum - 1
            val prevChapter = chapters.find { it.chapterNumber == prevNum }
            _state.value = _state.value.copy(isLoadingPrevChapter = true, scrollToEnd = true)
            ttsManager.stop()
            if (prevChapter != null) {
                recordChapterReadOnTransition(prevNum)
                loadChapter(prevNum, prevChapter.url, autoPlay = false, scrollToEnd = true)
            } else {
                val constructedUrl = tryConstructChapterUrl(chapterUrl, prevNum)
                if (constructedUrl != null) {
                    recordChapterReadOnTransition(prevNum)
                    loadChapter(prevNum, constructedUrl, autoPlay = false, scrollToEnd = true)
                } else {
                    _state.value = _state.value.copy(isLoadingPrevChapter = false)
                }
            }
        }

        fun prevChapter() {
            val currentNum = _state.value.currentChapterNumber
            val prevNum = currentNum - 1
            if (prevNum < 1) return
            val prevChapter = chapters.find { it.chapterNumber == prevNum }
            // Only auto-play if TTS was already active (playing or paused)
            val wasActive = ttsManager.state.value.isPlaying || ttsManager.state.value.isPaused
            // Use pause() when active to keep foreground service alive (Android 12+)
            if (wasActive) ttsManager.pause() else ttsManager.stop()
            if (prevChapter != null) {
                recordChapterReadOnTransition(prevNum)
                loadChapter(prevNum, prevChapter.url, autoPlay = wasActive)
            } else {
                val constructedUrl = tryConstructChapterUrl(chapterUrl, prevNum)
                if (constructedUrl != null) {
                    recordChapterReadOnTransition(prevNum)
                    loadChapter(prevNum, constructedUrl, autoPlay = wasActive)
                }
            }
        }

        /**
         * Try to construct a chapter URL by replacing the chapter number in the URL pattern.
         * URL pattern: https://truyencom.com/truyen/slug/chuong-N
         */
        private fun tryConstructChapterUrl(
            currentUrl: String,
            targetNumber: Int,
        ): String? =
            try {
                val regex = CHAPTER_URL_REGEX
                if (regex.containsMatchIn(currentUrl)) {
                    currentUrl.replace(regex, "/chuong-$targetNumber")
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }

        fun setFontSize(size: Float) {
            val clamped = size.coerceIn(12f, 32f)
            _state.value = _state.value.copy(fontSize = clamped)
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.FONT_SIZE] = clamped }
            }
        }

        fun setLineSpacing(spacing: Float) {
            val clamped = spacing.coerceIn(1.2f, 2.2f)
            _state.value = _state.value.copy(lineSpacing = clamped)
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.LINE_SPACING] = clamped }
            }
        }

        fun setReaderTheme(theme: ReaderTheme) {
            _state.value = _state.value.copy(readerTheme = theme)
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.THEME] = theme.ordinal }
            }
        }

        fun toggleSettings() {
            _state.value = _state.value.copy(showSettings = !_state.value.showSettings)
        }

        fun setFontFamily(fontFamily: ReaderFontFamily) {
            _state.value = _state.value.copy(fontFamily = fontFamily)
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.FONT_FAMILY] = fontFamily.ordinal }
            }
        }

        fun toggleBarsVisibility() {
            _state.value = _state.value.copy(areBarsVisible = !_state.value.areBarsVisible)
        }

        fun toggleChapterList() {
            _state.value = _state.value.copy(showChapterList = !_state.value.showChapterList)
        }

        fun toggleCopyMode() {
            val enabling = !_state.value.copyMode
            // Auto-pause TTS khi bật copy mode — SelectionContainer bị conflict với
            // animateColorAsState recomposition liên tục từ TTS highlight
            if (enabling && ttsManager.state.value.isPlaying) {
                ttsManager.pause()
            }
            _state.value = _state.value.copy(copyMode = enabling)
        }

        private fun toggleAutoScroll() {
            _state.update { it.copy(autoScrollEnabled = !it.autoScrollEnabled) }
        }

        private fun setAutoScrollSpeed(speed: Int) {
            val clamped = speed.coerceIn(1, 10)
            _state.update { it.copy(autoScrollSpeed = clamped) }
            viewModelScope.launch {
                dataStore.edit { prefs ->
                    prefs[SettingsKeys.AUTO_SCROLL_SPEED] = clamped
                }
            }
        }

        // ── Pokemon lookup ──

        private var pokemonMatchJob: Job? = null

        fun togglePokemonLookup() {
            val newValue = !_state.value.pokemonLookupEnabled
            _state.update {
                it.copy(
                    pokemonLookupEnabled = newValue,
                    selectedPokemon = if (newValue) it.selectedPokemon else null,
                )
            }
            viewModelScope.launch {
                dataStore.edit { prefs ->
                    prefs[SettingsKeys.POKEMON_LOOKUP_ENABLED] = newValue
                }
            }
            val currentParagraphs =
                _state.value.paragraphs.ifEmpty {
                    if (_state.value.content.isBlank()) {
                        emptyList()
                    } else {
                        TtsManager.splitIntoParagraphs(_state.value.content)
                    }
                }
            refreshPokemonMatches(currentParagraphs, newValue)
        }

        private fun selectPokemon(match: PokemonMatchRange) {
            _state.update {
                it.copy(
                    selectedPokemon =
                        PokemonSelection(
                            entry = match.entry,
                            matchedText = match.matchedText,
                            initialFormIndex = match.initialFormIndex,
                        ),
                )
            }
        }

        private fun dismissPokemonDialog() {
            _state.update { it.copy(selectedPokemon = null) }
        }

        private fun refreshPokemonMatches(
            paragraphs: List<String>,
            enabled: Boolean,
        ) {
            pokemonMatchJob?.cancel()
            if (!enabled || paragraphs.isEmpty()) {
                _state.update { it.copy(paragraphPokemonMatches = persistentMapOf()) }
                return
            }
            pokemonMatchJob =
                viewModelScope.launch {
                    runCatching {
                        val matchesMap =
                            kotlinx.coroutines.withContext(workDispatcher) {
                                pokemonRepository
                                    .findMatchesInParagraphs(paragraphs)
                                    .mapValues { (_, list) -> list.toImmutableList() }
                                    .toImmutableMap()
                            }
                        _state.update { it.copy(paragraphPokemonMatches = matchesMap) }
                    }.onFailure { e ->
                        if (e is CancellationException) throw e
                    }
                }
        }

        fun toggleTextReplacement() {
            val newValue = !_state.value.textReplacementEnabled
            _state.value = _state.value.copy(textReplacementEnabled = newValue)
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.TEXT_REPLACEMENT_ENABLED] = newValue }
            }
            reprocessCurrentChapter()
        }

        fun toggleAutoFormat() {
            val newValue = !_state.value.autoFormatEnabled
            _state.value = _state.value.copy(autoFormatEnabled = newValue)
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.AUTO_FORMAT_ENABLED] = newValue }
            }
            reprocessCurrentChapter()
        }

        // ── Per-story text replacement ──

        fun toggleStoryReplacements() {
            val newValue = !_state.value.storyReplacementsEnabled
            storyReplacementsEnabled = newValue
            _state.value = _state.value.copy(storyReplacementsEnabled = newValue)
            viewModelScope.launch {
                textReplacementRepository.setStoryReplacementsEnabled(storyId, newValue)
            }
            reprocessCurrentChapter()
        }

        fun addStoryReplacement(
            from: String,
            to: String,
        ) {
            viewModelScope.launch {
                textReplacementRepository.addStoryReplacement(storyId, from, to)
                reloadStoryReplacements()
                reprocessCurrentChapter()
            }
        }

        fun removeStoryReplacement(index: Int) {
            viewModelScope.launch {
                textReplacementRepository.removeStoryReplacement(storyId, index)
                reloadStoryReplacements()
                reprocessCurrentChapter()
            }
        }

        suspend fun sendReplacementToGlobal(
            from: String,
            to: String,
        ): Boolean {
            val added = textReplacementRepository.sendReplacementToGlobal(from, to)
            if (added) {
                if (customReplacements.none { it.first == from && it.second == to }) {
                    customReplacements = customReplacements + (from to to)
                }
                _state.update {
                    it.copy(snackbarMessage = "Đã thêm \"$from\" → \"$to\" vào cài đặt thay chữ chung")
                }
                reprocessCurrentChapter()
            } else {
                _state.update {
                    it.copy(snackbarMessage = "Từ \"$from\" → \"$to\" đã tồn tại trong cài đặt chung")
                }
            }
            return added
        }

        private fun clearSnackbar() {
            _state.update { it.copy(snackbarMessage = null) }
        }

        private suspend fun reloadStoryReplacements() {
            val data = textReplacementRepository.getStoryReplacements(storyId)
            if (data != null) {
                storyReplacementsEnabled = data.storyReplacementsEnabled
                storyCustomReplacements =
                    TextReplacementHelper.parseCustomReplacements(
                        data.storyCustomReplacements,
                    )
                _state.value =
                    _state.value.copy(
                        storyReplacementsEnabled = data.storyReplacementsEnabled,
                        storyReplacements = storyCustomReplacements.toImmutableList(),
                    )
            }
        }

        fun navigateToChapter(chapter: Chapter) {
            if (chapter.chapterNumber == _state.value.currentChapterNumber &&
                !_state.value.isLoading &&
                _state.value.error == null
            ) {
                _state.update { it.copy(showChapterList = false) }
                return
            }
            val wasActive = ttsManager.state.value.isPlaying || ttsManager.state.value.isPaused
            ttsManager.stop()
            _state.value = _state.value.copy(showChapterList = false)
            recordChapterReadOnTransition(chapter.chapterNumber)
            loadChapter(chapter.chapterNumber, chapter.url, autoPlay = wasActive)
        }

        fun saveScrollPosition(position: Int) {
            val safePos = position.coerceAtLeast(0)
            currentScrollPosition = safePos
            _state.update { it.copy(initialScrollIndex = safePos) }
            val chapterNum = _state.value.currentChapterNumber
            // Dùng appScope thay vì viewModelScope vì khi user bấm Back,
            // viewModelScope bị cancel trong onCleared() TRƯỚC KHI DisposableEffect
            // onDispose() kịp gọi saveScrollPosition() → progress mất.
            appScope.launch {
                try {
                    readingProgressRepository.saveReadingProgress(
                        ReadingProgress(
                            storyId = storyId,
                            lastChapterNumber = chapterNum,
                            scrollPosition = safePos,
                        ),
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }

        private fun flushReadingSession() {
            val start = sessionStartTime
            if (start <= 0L) return
            val now = timeProvider()
            sessionStartTime = 0L
            val durationMs = now - start
            if (durationMs >= MIN_READING_SESSION_MS) {
                appScope.launch {
                    try {
                        readingStatsRepository.recordReadingSession(durationMs, storyId)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                    }
                }
            }
        }

        private fun onReaderResumed() {
            if (sessionStartTime <= 0L) {
                sessionStartTime = timeProvider()
            }
        }

        private fun onReaderPaused() {
            flushReadingSession()
        }

        companion object {
            // M2: Cached regex — used by tryConstructChapterUrl() on every chapter navigation
            // Matches both simple pattern /chuong-N and hash pattern /chuong-N-hashId
            private val CHAPTER_URL_REGEX = Regex("/chuong-\\d+(-[A-Za-z0-9_!-]+)?")
            const val MIN_READING_SESSION_MS = 5_000L
            internal var defaultWorkDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
        }

        override fun onCleared() {
            super.onCleared()
            ttsManager.stop() // stop() not shutdown() — TtsManager is @Singleton

            val chapterNum = _state.value.currentChapterNumber
            val scrollPos = currentScrollPosition
            // Final save reading progress — backup vì DisposableEffect onDispose()
            // có thể không kịp chạy trước onCleared()
            appScope.launch {
                try {
                    readingProgressRepository.saveReadingProgress(
                        ReadingProgress(
                            storyId = storyId,
                            lastChapterNumber = chapterNum,
                            scrollPosition = scrollPos,
                        ),
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // non-fatal
                }
            }

            // Record reading session duration — use appScope because viewModelScope is already cancelled
            flushReadingSession()
        }
    }
