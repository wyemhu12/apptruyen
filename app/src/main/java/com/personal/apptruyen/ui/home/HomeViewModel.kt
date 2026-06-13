package com.personal.apptruyen.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.model.ReadingProgress
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.IReadingProgressRepository
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.ReadingStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

        /** Reading stats state for the stats tab */
        data class ReadingStatsState(
            val todayChapters: Int = 0,
            val todayMinutes: Int = 0,
            val currentStreak: Int = 0,
            val weeklyChapters: ImmutableList<Int> = persistentListOf(), // 7 days, Mon→Sun
        )

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
            loadReadingStats()
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
                    Log.d("HomeVM", "Refresh library failed", e)
                }
                _isRefreshing.value = false
                loadReadingStats()
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
                    Log.d("HomeVM", "Remove from reading list failed", e)
                }
            }
        }

        private fun loadReadingStats() {
            viewModelScope.launch {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

                    // Today's stats
                    val todayKey = sdf.format(java.util.Date())
                    val recentStats = readingStatsRepository.getRecentStats(30)
                    val todayStat = recentStats.firstOrNull { it.date == todayKey }

                    // Streak
                    val streak = readingStatsRepository.getReadingStreak()

                    // Weekly chapters (last 7 days, Mon→today)
                    val cal = Calendar.getInstance()
                    val weeklyChapters = mutableListOf<Int>()
                    // Go back 6 days from today
                    for (i in 6 downTo 0) {
                        val tempCal = Calendar.getInstance()
                        tempCal.add(Calendar.DAY_OF_YEAR, -i)
                        val dayKey = sdf.format(tempCal.time)
                        val dayStat = recentStats.firstOrNull { it.date == dayKey }
                        weeklyChapters.add(dayStat?.chaptersRead ?: 0)
                    }

                    _readingStats.value =
                        ReadingStatsState(
                            todayChapters = todayStat?.chaptersRead ?: 0,
                            todayMinutes = ((todayStat?.totalReadingTimeMs ?: 0) / 60_000).toInt(),
                            currentStreak = streak,
                            weeklyChapters = weeklyChapters.toImmutableList(),
                        )
                } catch (e: Exception) {
                    Log.d("HomeVM", "Load reading stats failed", e)
                }
            }
        }
    }
