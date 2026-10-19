package com.personal.apptruyen.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.local.entity.ReadingStatsEntity
import com.personal.apptruyen.data.model.ReadingProgress
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.IReadingProgressRepository
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.ReadingStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

enum class HomeSortOption(
    val label: String,
) {
    RECENT("Gần đây"),
    ALPHA_ASC("A → Z"),
    UNFINISHED("Chưa xong"),
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val storyRepository: IStoryRepository,
        private val readingProgressRepository: IReadingProgressRepository,
        private val readingStatsRepository: ReadingStatsRepository,
    ) : ViewModel() {

        data class DayChapterStat(
            val label: String,
            val chapters: Int,
            val isToday: Boolean = false,
        )

        /** Reading stats state for the stats tab */
        data class ReadingStatsState(
            val todayChapters: Int = 0,
            val todayMinutes: Int = 0,
            val currentStreak: Int = 0,
            val weeklyChapters: ImmutableList<Int> = persistentListOf(),
            val weeklyStats: ImmutableList<DayChapterStat> = persistentListOf(),
        )

        internal var calendarProvider: () -> Calendar = defaultCalendarProvider
        private var statsObserverJob: kotlinx.coroutines.Job? = null

        internal constructor(
            storyRepository: IStoryRepository,
            readingProgressRepository: IReadingProgressRepository,
            readingStatsRepository: ReadingStatsRepository,
            calendarProvider: () -> Calendar,
        ) : this(storyRepository, readingProgressRepository, readingStatsRepository) {
            this.calendarProvider = calendarProvider
            observeReadingStats()
        }

        /** Reading progress map: storyId → ReadingProgress */
        val progressMap: StateFlow<Map<String, ReadingProgress>> =
            readingProgressRepository
                .getAllReadingProgress()
                .map { list -> list.associateBy { it.storyId } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        private val _sortOption = MutableStateFlow(HomeSortOption.RECENT)
        val sortOption: StateFlow<HomeSortOption> = _sortOption.asStateFlow()

        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

        private val _readingStats = MutableStateFlow(ReadingStatsState())
        val readingStats: StateFlow<ReadingStatsState> = _readingStats.asStateFlow()

        private val rawRecentlyRead: StateFlow<List<Story>> =
            readingProgressRepository
                .getRecentlyReadStories()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val hasReadingHistory: StateFlow<Boolean> =
            rawRecentlyRead
                .map { it.isNotEmpty() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

        /** Sorted recently read stories based on selected sort option */
        val recentlyRead: StateFlow<List<Story>> =
            combine(
                rawRecentlyRead,
                _sortOption,
                progressMap,
            ) { stories, sort, progress ->
                when (sort) {
                    HomeSortOption.RECENT -> stories
                    HomeSortOption.ALPHA_ASC -> stories.sortedBy { it.title.lowercase() }
                    HomeSortOption.UNFINISHED ->
                        stories.filter { story ->
                            val p = progress[story.id]
                            p == null ||
                                story.totalChapters <= 0 ||
                                p.lastChapterNumber < story.totalChapters
                        }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        init {
            observeReadingStats()
        }

        fun setSortOption(option: HomeSortOption) {
            _sortOption.value = option
        }

        fun refresh() {
            viewModelScope.launch {
                _isRefreshing.value = true
                try {
                    storyRepository.refreshLibraryStories()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.d(e, "Refresh library failed")
                }
                _isRefreshing.value = false
                refreshReadingStats()
            }
        }

        /**
         * Xóa truyện khỏi danh sách "Đang đọc".
         * Chỉ xóa reading progress — không xóa story data hay chapters đã tải.
         */
        fun removeFromReadingList(storyId: String) {
            viewModelScope.launch {
                try {
                    readingProgressRepository.deleteReadingProgress(storyId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.d(e, "Remove from reading list failed")
                }
            }
        }

        private fun observeReadingStats() {
            statsObserverJob?.cancel()
            statsObserverJob =
                viewModelScope.launch {
                    readingStatsRepository
                        .observeAllStats()
                        .catch { e -> Timber.w(e, "Failed to observe reading stats") }
                        .collect { allStats ->
                            recalculateReadingStats(allStats)
                        }
                }
        }

        fun refreshReadingStats() {
            viewModelScope.launch {
                try {
                    val recentStats = readingStatsRepository.getRecentStats(30)
                    recalculateReadingStats(recentStats)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Refresh reading stats failed")
                }
            }
        }

        private suspend fun recalculateReadingStats(allStats: List<ReadingStatsEntity>) {
            try {
                val streak = readingStatsRepository.getReadingStreak()
                val statsByDate = allStats.associateBy { it.date }
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val baseCal = calendarProvider()
                val todayStr = sdf.format(baseCal.time)
                val todayStat = statsByDate[todayStr]
                val todayMinutes = ((todayStat?.totalReadingTimeMs ?: 0L) / 60_000).toInt()
                val todayChapters = todayStat?.chaptersRead ?: 0

                val weeklyChaptersList = mutableListOf<Int>()
                val weeklyStatsList = mutableListOf<DayChapterStat>()
                for (i in 6 downTo 0) {
                    val tempCal = (baseCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }
                    val dateStr = sdf.format(tempCal.time)
                    val chapters = statsByDate[dateStr]?.chaptersRead ?: 0
                    val dayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK) // 1 (CN) .. 7 (T7)
                    val label = DAY_LABELS.getOrElse(dayOfWeek - 1) { "" }
                    weeklyChaptersList.add(chapters)
                    weeklyStatsList.add(
                        DayChapterStat(
                            label = label,
                            chapters = chapters,
                            isToday = (i == 0),
                        ),
                    )
                }

                _readingStats.value =
                    ReadingStatsState(
                        todayChapters = todayChapters,
                        todayMinutes = todayMinutes,
                        currentStreak = streak,
                        weeklyChapters = weeklyChaptersList.toImmutableList(),
                        weeklyStats = weeklyStatsList.toImmutableList(),
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to recalculate reading stats")
            }
        }

        companion object {
            private val DAY_LABELS = listOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")
            internal var defaultCalendarProvider: () -> Calendar = { Calendar.getInstance() }
        }
    }
