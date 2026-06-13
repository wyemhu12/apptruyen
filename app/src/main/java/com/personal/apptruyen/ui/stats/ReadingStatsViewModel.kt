package com.personal.apptruyen.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.repository.ReadingStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadingStatsViewModel
    @Inject
    constructor(
        private val readingStatsRepository: ReadingStatsRepository,
    ) : ViewModel() {

        data class StatsState(
            val todayTimeMs: Long = 0,
            val todayChapters: Int = 0,
            val totalTimeMs: Long = 0,
            val totalChapters: Int = 0,
            val streak: Int = 0,
            val weeklyData: ImmutableList<DayStat> = persistentListOf(),
            val isLoading: Boolean = true,
        )

        data class DayStat(
            val label: String, // "T2", "T3", etc.
            val timeMs: Long,
            val chapters: Int,
        )

        private val _state = MutableStateFlow(StatsState())
        val state: StateFlow<StatsState> = _state.asStateFlow()

        init {
            loadStats()
        }

        private fun loadStats() {
            viewModelScope.launch {
                _state.value = _state.value.copy(isLoading = true)

                val recentStats = readingStatsRepository.getRecentStats(7)
                val streak = readingStatsRepository.getReadingStreak()

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                val today = sdf.format(java.util.Date())
                val todayStats = recentStats.find { it.date == today }

                // Build weekly data (last 7 days)
                val dayLabels = listOf("CN", "T2", "T3", "T4", "T5", "T6", "T7")
                val cal = java.util.Calendar.getInstance()
                val weeklyData =
                    (6 downTo 0).map { daysAgo ->
                        val tempCal = java.util.Calendar.getInstance()
                        tempCal.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
                        val dateKey = sdf.format(tempCal.time)
                        val dayOfWeek = tempCal.get(java.util.Calendar.DAY_OF_WEEK)
                        val label = dayLabels[dayOfWeek - 1]
                        val stat = recentStats.find { it.date == dateKey }
                        DayStat(
                            label = label,
                            timeMs = stat?.totalReadingTimeMs ?: 0,
                            chapters = stat?.chaptersRead ?: 0,
                        )
                    }

                // Total stats (from all recent data we have)
                val totalTime = recentStats.sumOf { it.totalReadingTimeMs }
                val totalChapters = recentStats.sumOf { it.chaptersRead }

                _state.value =
                    StatsState(
                        todayTimeMs = todayStats?.totalReadingTimeMs ?: 0,
                        todayChapters = todayStats?.chaptersRead ?: 0,
                        totalTimeMs = totalTime,
                        totalChapters = totalChapters,
                        streak = streak,
                        weeklyData = weeklyData.toImmutableList(),
                        isLoading = false,
                    )
            }
        }

        fun refresh() {
            loadStats()
        }
    }
