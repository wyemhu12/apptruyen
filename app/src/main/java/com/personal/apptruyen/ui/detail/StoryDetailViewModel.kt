package com.personal.apptruyen.ui.detail

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.ReadingProgress
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.remote.ScraperException
import com.personal.apptruyen.data.repository.DownloadProgress
import com.personal.apptruyen.data.repository.IReadingProgressRepository
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.ITextReplacementRepository
import com.personal.apptruyen.download.DownloadService
import com.personal.apptruyen.ui.navigation.NavigationUtils
import com.personal.apptruyen.util.TextReplacementHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class StoryDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: IStoryRepository,
        private val textReplacementRepository: ITextReplacementRepository,
        private val readingProgressRepository: IReadingProgressRepository,
        private val notificationHelper: DownloadNotificationHelper,
        private val application: Application,
    ) : ViewModel() {

        private val storyId: String = NavigationUtils.decodeRouteParam(savedStateHandle["storyId"] ?: "")
        private val storyUrl: String = savedStateHandle["storyUrl"] ?: ""

        data class DetailState(
            val story: Story? = null,
            val chapters: ImmutableList<Chapter> = persistentListOf(), // toàn bộ (dùng cho download)
            val displayedChapters: ImmutableList<Chapter> = persistentListOf(), // hiển thị (lazy paged)
            val totalChapterCount: Int = 0,
            val canLoadMoreChapters: Boolean = false,
            val readingProgress: ReadingProgress? = null,
            val isLoading: Boolean = true,
            val error: String? = null,
            val isDownloading: Boolean = false,
            val downloadProgress: DownloadProgress? = null,
            // Per-story text replacement
            val storyReplacementsEnabled: Boolean = false,
            val storyReplacements: ImmutableList<Pair<String, String>> = persistentListOf(),
            val showStorySettings: Boolean = false,
        ) {
            val lastReadChapter: Int?
                get() = readingProgress?.lastChapterNumber?.takeIf { it > 0 }
        }

        private val _state = MutableStateFlow(DetailState())
        val state: StateFlow<DetailState> = _state.asStateFlow()

        private var observerJob: Job? = null
        private val chapterPageSize = 100

        init {
            loadStoryDetail()
            observeDownloadService()
            loadStoryReplacements()
        }

        private var backgroundCrawlJob: Job? = null
        private var progressObserverJob: Job? = null

        // ── Per-story text replacement ──

        private fun effectiveStoryId(): String =
            _state.value.story
                ?.id
                ?.ifBlank { storyId }
                ?: storyId

        private fun loadStoryReplacements(targetId: String = effectiveStoryId()) {
            if (targetId.isBlank()) return
            viewModelScope.launch {
                val data = textReplacementRepository.getStoryReplacements(targetId)
                if (data != null) {
                    _state.update { s ->
                        s.copy(
                            storyReplacementsEnabled = data.storyReplacementsEnabled,
                            storyReplacements =
                                TextReplacementHelper
                                    .parseCustomReplacements(
                                        data.storyCustomReplacements,
                                    ).toImmutableList(),
                        )
                    }
                }
            }
        }

        fun toggleStoryReplacements() {
            val newValue = !_state.value.storyReplacementsEnabled
            _state.update { it.copy(storyReplacementsEnabled = newValue) }
            viewModelScope.launch {
                textReplacementRepository.setStoryReplacementsEnabled(effectiveStoryId(), newValue)
            }
        }

        fun addStoryReplacement(
            from: String,
            to: String,
        ) {
            viewModelScope.launch {
                val targetId = effectiveStoryId()
                textReplacementRepository.addStoryReplacement(targetId, from, to)
                loadStoryReplacements(targetId) // Reload to sync state
            }
        }

        fun removeStoryReplacement(index: Int) {
            viewModelScope.launch {
                val targetId = effectiveStoryId()
                textReplacementRepository.removeStoryReplacement(targetId, index)
                loadStoryReplacements(targetId)
            }
        }

        /**
         * Gửi 1 cặp thay thế từ per-story sang cài đặt tổng (global DataStore).
         * @return true nếu thêm thành công, false nếu đã tồn tại
         */
        suspend fun sendReplacementToGlobal(
            from: String,
            to: String,
        ): Boolean = textReplacementRepository.sendReplacementToGlobal(from, to)

        fun toggleStorySettings() {
            _state.update { it.copy(showStorySettings = !it.showStorySettings) }
        }

        // ── Story detail loading ──

        private fun loadStoryDetail() {
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true, error = null)
                try {
                    val decodedUrl =
                        com.personal.apptruyen.ui.navigation.Screen.StoryDetail
                            .decodeUrl(storyUrl)
                    val story = repository.getStoryDetail(decodedUrl)
                    if (story.id.isNotBlank() && story.id != storyId) {
                        loadStoryReplacements(story.id)
                    }

                    // Phase 1: First page chapters + reading progress in parallel → display INSTANTLY
                    val chaptersDeferred = async { repository.getFirstPageChapterList(decodedUrl, story.id) }
                    val progressDeferred = async { readingProgressRepository.getReadingProgress(story.id) }

                    val (firstPageChapters, totalPages) = chaptersDeferred.await()
                    val progress = progressDeferred.await()

                    // Display immediately with first page data
                    val totalCount = repository.getChapterCount(story.id)
                    val displayPage = repository.getChaptersPaged(story.id, chapterPageSize, 0)

                    _state.value =
                        _state.value.copy(
                            story = story,
                            chapters = firstPageChapters.toImmutableList(),
                            displayedChapters = displayPage.toImmutableList(),
                            totalChapterCount = totalCount,
                            canLoadMoreChapters = displayPage.size < totalCount,
                            readingProgress = progress,
                            isLoading = false,
                        )

                    // Observe chapter updates — auto-updates UI when background crawl inserts new batches
                    observerJob?.cancel()
                    observerJob =
                        repository
                            .observeChapters(story.id)
                            .onEach { updatedChapters ->
                                _state.update { s ->
                                    s.copy(
                                        chapters = updatedChapters.toImmutableList(),
                                        totalChapterCount = updatedChapters.size,
                                        displayedChapters =
                                            updatedChapters
                                                .take(
                                                    s.displayedChapters.size.coerceAtLeast(chapterPageSize),
                                                ).toImmutableList(),
                                        canLoadMoreChapters = s.displayedChapters.size < updatedChapters.size,
                                    )
                                }
                            }.launchIn(viewModelScope)

                    // Observe reading progress — tự cập nhật khi user quay lại từ ReaderScreen
                    // (ReaderVM save progress bằng appScope → Room emit update → Flow ở đây nhận)
                    progressObserverJob?.cancel()
                    progressObserverJob =
                        readingProgressRepository
                            .observeReadingProgress(story.id)
                            .onEach { updatedProgress ->
                                _state.update { s -> s.copy(readingProgress = updatedProgress) }
                            }.launchIn(viewModelScope)

                    repository
                        .observeStory(story.id)
                        .onEach { updatedStory ->
                            if (updatedStory != null) {
                                _state.update { s -> s.copy(story = updatedStory) }
                            }
                        }.launchIn(viewModelScope)

                    // Phase 2: Background crawl remaining pages (2..totalPages)
                    if (totalPages > 1) {
                        backgroundCrawlJob?.cancel()
                        backgroundCrawlJob =
                            viewModelScope.launch {
                                try {
                                    repository.loadRemainingChapters(decodedUrl, story.id, totalPages)
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    // Non-fatal: first page already displayed
                                    Timber.tag("StoryDetail").d(e, "Background chapter crawl failed")
                                }
                            }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.value =
                        _state.value.copy(
                            isLoading = false,
                            error = (e as? ScraperException)?.message ?: "Không thể tải thông tin truyện. Vui lòng kiểm tra mạng và thử lại.",
                        )
                }
            }
        }

        fun loadMoreChapters() {
            val currentState = _state.value
            if (!currentState.canLoadMoreChapters) return

            viewModelScope.launch {
                val storyId = currentState.story?.id ?: return@launch
                val offset = currentState.displayedChapters.size
                val nextPage = repository.getChaptersPaged(storyId, chapterPageSize, offset)
                _state.update { s ->
                    val newDisplayed = (s.displayedChapters + nextPage).toImmutableList()
                    s.copy(
                        displayedChapters = newDisplayed,
                        canLoadMoreChapters = newDisplayed.size < s.totalChapterCount,
                    )
                }
            }
        }

        fun downloadChapters(count: Int) {
            val chapters =
                _state.value.chapters
                    .filter { !it.isDownloaded }
                    .take(count)
            if (chapters.isEmpty()) return
            performDownload(chapters)
        }

        fun downloadAll() {
            val chapters = _state.value.chapters.filter { !it.isDownloaded }
            if (chapters.isEmpty()) return
            performDownload(chapters)
        }

        fun downloadRange(
            from: Int,
            to: Int,
        ) {
            val chapters =
                _state.value.chapters
                    .filter { it.chapterNumber in from..to && !it.isDownloaded }
                    .sortedBy { it.chapterNumber }
            if (chapters.isEmpty()) return
            performDownload(chapters)
        }

        /**
         * Khởi chạy DownloadService foreground thay vì download trong viewModelScope.
         * Service chạy với WakeLock → tiếp tục tải khi tắt màn hình hoặc chuyển app.
         * Không xung đột với TtsService (khác notification ID, channel, foregroundServiceType).
         */
        private fun performDownload(chapters: List<Chapter>) {
            val story = _state.value.story ?: return
            val chapterNumbers = chapters.map { it.chapterNumber }.toIntArray()

            viewModelScope.launch {
                repository.addToLibrary(story.id)
            }

            val started =
                DownloadService.startDownload(
                    context = application,
                    storyId = story.id,
                    storyTitle = story.title,
                    chapterNumbers = chapterNumbers,
                )
            if (!started) {
                _state.update {
                    it.copy(
                        error = "Không thể khởi chạy dịch vụ tải nền. Vui lòng giữ ứng dụng trên màn hình và thử lại.",
                    )
                }
            }
        }

        fun cancelDownload() {
            DownloadService.cancelDownload(application)
        }

        /**
         * Observe DownloadService static StateFlow — cập nhật UI khi service báo progress.
         * Service chạy độc lập, ViewModel chỉ observe.
         */
        private fun observeDownloadService() {
            DownloadService.downloadState
                .onEach { downloadState ->
                    val matchesCurrentStory =
                        { targetId: String ->
                            targetId == storyId || targetId == _state.value.story?.id
                        }
                    when (downloadState) {
                        is DownloadService.DownloadState.Idle -> {
                            _state.update { it.copy(isDownloading = false, downloadProgress = null) }
                        }
                        is DownloadService.DownloadState.Downloading -> {
                            if (matchesCurrentStory(downloadState.storyId)) {
                                _state.update {
                                    it.copy(
                                        isDownloading = true,
                                        downloadProgress = downloadState.progress,
                                    )
                                }
                            }
                        }
                        is DownloadService.DownloadState.Completed -> {
                            if (matchesCurrentStory(downloadState.storyId)) {
                                _state.update { it.copy(isDownloading = false, downloadProgress = null) }
                            }
                        }
                        is DownloadService.DownloadState.Cancelled -> {
                            if (matchesCurrentStory(downloadState.storyId)) {
                                _state.update { it.copy(isDownloading = false, downloadProgress = null) }
                            }
                        }
                    }
                }.launchIn(viewModelScope)
        }

        fun resolveChapterUrlForReading(chapterNumber: Int): String {
            val s = _state.value
            val exact = s.chapters.find { it.chapterNumber == chapterNumber }?.url
            if (!exact.isNullOrBlank()) return exact

            val sampleUrl =
                s.chapters
                    .firstOrNull()
                    ?.url
                    .orEmpty()
            if (sampleUrl.isNotBlank() && CHAPTER_URL_REGEX.containsMatchIn(sampleUrl)) {
                return sampleUrl.replace(CHAPTER_URL_REGEX, "/chuong-$chapterNumber")
            }
            return s.story?.url?.takeIf { it.isNotBlank() }
                ?: com.personal.apptruyen.ui.navigation.Screen.StoryDetail
                    .decodeUrl(storyUrl)
        }

        fun retry() {
            loadStoryDetail()
        }

        companion object {
            private val CHAPTER_URL_REGEX = Regex("/chuong-\\d+(-[A-Za-z0-9_!-]+)?")
        }
    }
