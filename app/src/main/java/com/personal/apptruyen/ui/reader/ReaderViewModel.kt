package com.personal.apptruyen.ui.reader

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.local.SettingsKeys
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.ReadingProgress
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
        private val ttsManager: TtsManager,
        private val dataStore: DataStore<Preferences>,
        @ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
    ) : ViewModel() {

        private val storyId: String = savedStateHandle["storyId"] ?: ""
        private val chapterNumber: Int = savedStateHandle["chapterNumber"] ?: 1
        private val chapterUrl: String = NavigationUtils.decodeUrl(savedStateHandle["chapterUrl"] ?: "")

        // ── TTS public API (decoupled from TtsManager) ──
        val ttsState: StateFlow<TtsService.TtsState> = ttsManager.state
        val ttsSkipEvent: StateFlow<TtsService.ChapterSkipEvent> = ttsManager.skipEvent

        fun ttsPlay() = ttsManager.play()

        fun ttsPause() = ttsManager.pause()

        fun ttsStop() = ttsManager.stop()

        fun ttsCycleSpeed() = ttsManager.cycleSpeed()

        fun ttsSetSpeed(speed: Float) = ttsManager.setSpeed(speed)

        fun ttsSetPitch(pitch: Float) = ttsManager.setPitch(pitch)

        fun ttsSeekAndPlay(index: Int) = ttsManager.seekAndPlay(index)

        data class ReaderState(
            val chapterTitle: String = "",
            val content: String = "",
            val isLoading: Boolean = true,
            val error: String? = null,
            val currentChapterNumber: Int = 1,
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
        private val contentCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
        private var prefetchCount = 0
        private var sessionStartTime: Long = System.currentTimeMillis()

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
                dataStore.data.first().let { prefs ->
                    val defaultSpeed = prefs[SettingsKeys.TTS_SPEED] ?: 1.0f
                    val defaultPitch = prefs[SettingsKeys.TTS_PITCH] ?: 1.0f
                    val defaultVoice = prefs[SettingsKeys.TTS_VOICE] ?: ""
                    val defaultFontSize = prefs[SettingsKeys.FONT_SIZE] ?: 18f
                    val defaultTheme = prefs[SettingsKeys.THEME] ?: 0
                    val replacementEnabled = prefs[SettingsKeys.TEXT_REPLACEMENT_ENABLED] ?: true
                    val autoFormatEnabled = prefs[SettingsKeys.AUTO_FORMAT_ENABLED] ?: true
                    val customJson = prefs[SettingsKeys.CUSTOM_REPLACEMENTS]
                    val defaultLineSpacing = prefs[SettingsKeys.LINE_SPACING] ?: 1.6f
                    val fontFamilyOrdinal = prefs[SettingsKeys.FONT_FAMILY] ?: 0
                    prefetchCount = prefs[SettingsKeys.PREFETCH_CHAPTERS] ?: 0
                    val savedAutoScrollSpeed = prefs[SettingsKeys.AUTO_SCROLL_SPEED] ?: 3

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
                    _state.value =
                        _state.value.copy(
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
                        )
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
                loadChapterAndPlay(nextNum, nextChapter.url)
            } else {
                val constructedUrl = tryConstructChapterUrl(chapterUrl, nextNum)
                if (constructedUrl != null) {
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

        private fun loadChapter(
            number: Int,
            url: String,
            autoPlay: Boolean = false,
            scrollToEnd: Boolean = false,
        ) {
            currentChapterUrl = url
            viewModelScope.launch {
                _state.value =
                    _state.value.copy(isLoading = true, error = null, isLoadingNextChapter = false, scrollToEnd = false)
                try {
                    // Check in-memory prefetch cache first
                    val cachedContent = contentCache.remove(number)
                    val rawContent = cachedContent ?: repository.getChapterContent(storyId, number, url)
                    var content = rawContent
                    if (_state.value.autoFormatEnabled) {
                        content =
                            com.personal.apptruyen.util.TextFormatHelper
                                .formatChapter(content)
                    }

                    content =
                        TextReplacementHelper.applyAllReplacements(
                            content,
                            _state.value.textReplacementEnabled,
                            customReplacements,
                            storyReplacementsEnabled,
                            storyCustomReplacements,
                        )

                    val chapter = chapters.find { it.chapterNumber == number }
                    val title = chapter?.title ?: "Chương $number"
                    val maxChapter = if (chapters.isNotEmpty()) chapters.maxOf { it.chapterNumber } else 0
                    _state.value =
                        _state.value.copy(
                            chapterTitle = title,
                            content = content,
                            isLoading = false,
                            isLoadingPrevChapter = false,
                            scrollToEnd = scrollToEnd,
                            currentChapterNumber = number,
                            hasNextChapter = maxChapter == 0 || number < maxChapter,
                            hasPrevChapter = number > 1,
                        )

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

                    // Save reading progress
                    readingProgressRepository.saveReadingProgress(
                        ReadingProgress(
                            storyId = storyId,
                            lastChapterNumber = number,
                            scrollPosition = 0,
                        ),
                    )

                    // Prefetch next N chapters in background
                    prefetchAhead(number)
                } catch (e: Exception) {
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            isLoadingNextChapter = false,
                            isLoadingPrevChapter = false,
                            error = e.localizedMessage ?: "Không thể tải nội dung. Kiểm tra kết nối mạng.",
                        )
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
                            val content = repository.getChapterContent(storyId, nextNum, nextChapter.url)
                            contentCache[nextNum] = content
                            // Evict old entries if cache exceeds limit
                            while (contentCache.size > prefetchCount + 2) {
                                val oldest = contentCache.keys().toList().minOrNull() ?: break
                                if (oldest != _state.value.currentChapterNumber) {
                                    contentCache.remove(oldest)
                                } else {
                                    break
                                }
                            }
                            Log.d("ReaderVM", "Prefetched chapter $nextNum into memory cache")
                        } catch (e: Exception) {
                            Log.d("ReaderVM", "Prefetch chapter $nextNum failed: ${e.message}")
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

        fun nextChapter() {
            val currentNum = _state.value.currentChapterNumber
            val nextNum = currentNum + 1
            val nextChapter = chapters.find { it.chapterNumber == nextNum }
            // Only auto-play if TTS was already active (playing or paused)
            val wasActive = ttsManager.state.value.isPlaying || ttsManager.state.value.isPaused
            // Use pause() when active to keep foreground service alive (Android 12+)
            if (wasActive) ttsManager.pause() else ttsManager.stop()
            // Record chapter read for stats
            viewModelScope.launch { readingStatsRepository.recordChapterRead() }
            if (nextChapter != null) {
                loadChapter(nextNum, nextChapter.url, autoPlay = wasActive)
            } else {
                val constructedUrl = tryConstructChapterUrl(chapterUrl, nextNum)
                if (constructedUrl != null) {
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
                loadChapter(nextNum, nextChapter.url, autoPlay = false)
            } else {
                val constructedUrl = tryConstructChapterUrl(chapterUrl, nextNum)
                if (constructedUrl != null) {
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
                loadChapter(prevNum, prevChapter.url, autoPlay = false, scrollToEnd = true)
            } else {
                val constructedUrl = tryConstructChapterUrl(chapterUrl, prevNum)
                if (constructedUrl != null) {
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
                loadChapter(prevNum, prevChapter.url, autoPlay = wasActive)
            } else {
                val constructedUrl = tryConstructChapterUrl(chapterUrl, prevNum)
                if (constructedUrl != null) {
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

        fun toggleTextReplacement() {
            val newValue = !_state.value.textReplacementEnabled
            _state.value = _state.value.copy(textReplacementEnabled = newValue)
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.TEXT_REPLACEMENT_ENABLED] = newValue }
            }
            // Reload current chapter to apply/remove replacements
            loadChapter(_state.value.currentChapterNumber, currentChapterUrl)
        }

        fun toggleAutoFormat() {
            val newValue = !_state.value.autoFormatEnabled
            _state.value = _state.value.copy(autoFormatEnabled = newValue)
            viewModelScope.launch {
                dataStore.edit { it[SettingsKeys.AUTO_FORMAT_ENABLED] = newValue }
            }
            // Tải lại chương để áp dụng thay đổi
            loadChapter(_state.value.currentChapterNumber, currentChapterUrl)
        }

        // ── Per-story text replacement ──

        fun toggleStoryReplacements() {
            val newValue = !_state.value.storyReplacementsEnabled
            storyReplacementsEnabled = newValue
            _state.value = _state.value.copy(storyReplacementsEnabled = newValue)
            viewModelScope.launch {
                textReplacementRepository.setStoryReplacementsEnabled(storyId, newValue)
            }
            loadChapter(_state.value.currentChapterNumber, currentChapterUrl)
        }

        fun addStoryReplacement(
            from: String,
            to: String,
        ) {
            viewModelScope.launch {
                textReplacementRepository.addStoryReplacement(storyId, from, to)
                reloadStoryReplacements()
                loadChapter(_state.value.currentChapterNumber, currentChapterUrl)
            }
        }

        fun removeStoryReplacement(index: Int) {
            viewModelScope.launch {
                textReplacementRepository.removeStoryReplacement(storyId, index)
                reloadStoryReplacements()
                loadChapter(_state.value.currentChapterNumber, currentChapterUrl)
            }
        }

        suspend fun sendReplacementToGlobal(
            from: String,
            to: String,
        ): Boolean = textReplacementRepository.sendReplacementToGlobal(from, to)

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
            val wasActive = ttsManager.state.value.isPlaying || ttsManager.state.value.isPaused
            ttsManager.stop()
            _state.value = _state.value.copy(showChapterList = false)
            loadChapter(chapter.chapterNumber, chapter.url, autoPlay = wasActive)
        }

        fun saveScrollPosition(position: Int) {
            // Dùng appScope thay vì viewModelScope vì khi user bấm Back,
            // viewModelScope bị cancel trong onCleared() TRƯỚC KHI DisposableEffect
            // onDispose() kịp gọi saveScrollPosition() → progress mất.
            appScope.launch {
                readingProgressRepository.saveReadingProgress(
                    ReadingProgress(
                        storyId = storyId,
                        lastChapterNumber = _state.value.currentChapterNumber,
                        scrollPosition = position,
                    ),
                )
            }
        }

        companion object {
            // M2: Cached regex — used by tryConstructChapterUrl() on every chapter navigation
            // Matches both simple pattern /chuong-N and hash pattern /chuong-N-hashId
            private val CHAPTER_URL_REGEX = Regex("/chuong-\\d+(-[A-Za-z0-9_!-]+)?")
        }

        override fun onCleared() {
            super.onCleared()
            ttsManager.stop() // stop() not shutdown() — TtsManager is @Singleton

            // Final save reading progress — backup vì DisposableEffect onDispose()
            // có thể không kịp chạy trước onCleared()
            appScope.launch {
                try {
                    readingProgressRepository.saveReadingProgress(
                        ReadingProgress(
                            storyId = storyId,
                            lastChapterNumber = _state.value.currentChapterNumber,
                            scrollPosition = 0, // không có scroll position chính xác ở đây
                        ),
                    )
                } catch (_: Exception) {
                    // non-fatal
                }
            }

            // Record reading session duration — use appScope because viewModelScope is already cancelled
            val durationMs = System.currentTimeMillis() - sessionStartTime
            if (durationMs > 5000) { // Only record if > 5 seconds
                appScope.launch {
                    try {
                        readingStatsRepository.recordReadingSession(durationMs, storyId)
                    } catch (_: Exception) {
                        // non-fatal
                    }
                }
            }
        }
    }
