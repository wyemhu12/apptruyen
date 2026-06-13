package com.personal.apptruyen.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.SearchHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChapterCountFilter(
    val label: String,
    val minChapters: Int,
) {
    NONE("Tất cả", 0),
    OVER_200("200+", 200),
    OVER_300("300+", 300),
    OVER_500("500+", 500),
    OVER_900("900+", 900),
}

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val repository: IStoryRepository,
        private val searchHistoryRepository: SearchHistoryRepository,
    ) : ViewModel() {

        data class SearchState(
            val query: String = "",
            val results: ImmutableList<Story> = persistentListOf(),
            val isLoading: Boolean = false,
            val isLoadingMore: Boolean = false,
            val error: String? = null,
            val hasSearched: Boolean = false,
            // Filter state
            val selectedGenre: Genre? = null,
            val chapterFilter: ChapterCountFilter = ChapterCountFilter.NONE,
            val completedOnly: Boolean = true, // Mặc định chỉ hiện truyện Full
            val isFilterActive: Boolean = false,
            val currentPage: Int = 1,
            val canLoadMore: Boolean = false,
        )

        private val _state = MutableStateFlow(SearchState())
        val state: StateFlow<SearchState> = _state.asStateFlow()
        private var searchJob: Job? = null

        private val _genres = MutableStateFlow<ImmutableList<Genre>>(persistentListOf())
        val genres: StateFlow<ImmutableList<Genre>> = _genres.asStateFlow()

        val recentSearches: StateFlow<List<String>> =
            searchHistoryRepository
                .getRecentSearches()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        /** Autocomplete suggestions: filter recentSearches by current query (>= 2 chars) */
        val searchSuggestions: StateFlow<List<String>> =
            combine(
                _state.map { it.query },
                recentSearches,
            ) { query, history ->
                if (query.length < 2) {
                    emptyList()
                } else {
                    history.filter { it.contains(query, ignoreCase = true) && !it.equals(query, ignoreCase = true) }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        init {
            loadGenres()
        }

        private fun loadGenres() {
            viewModelScope.launch {
                try {
                    _genres.value = repository.getGenreList().toImmutableList()
                } catch (_: Exception) {
                    // Genres load failed — filter won't have options, but search still works
                }
            }
        }

        fun onQueryChange(query: String) {
            _state.value = _state.value.copy(query = query)
        }

        fun search() {
            val query = _state.value.query.trim()
            if (query.isBlank()) return

            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    _state.value =
                        _state.value.copy(
                            isLoading = true,
                            error = null,
                            currentPage = 1,
                            canLoadMore = false,
                        )
                    try {
                        // Keyword search luôn pass completedOnly=false vì hầu hết nguồn
                        // KHÔNG CÓ status data trong search HTML. Nên trả hết để user thấy nhiều kết quả.
                        val rawResults = repository.searchStories(query, completedOnly = false)
                        val filtered = applyLocalFilters(rawResults).toImmutableList()
                        val hasFilters =
                            _state.value.selectedGenre != null ||
                                _state.value.chapterFilter != ChapterCountFilter.NONE
                        _state.value =
                            _state.value.copy(
                                results = filtered,
                                isLoading = false,
                                hasSearched = true,
                                error =
                                    if (filtered.isEmpty()) {
                                        if (hasFilters) {
                                            "Không tìm thấy truyện phù hợp bộ lọc"
                                        } else {
                                            "Không tìm thấy truyện nào với từ khóa \"$query\""
                                        }
                                    } else {
                                        null
                                    },
                            )
                        if (rawResults.isNotEmpty()) {
                            searchHistoryRepository.saveSearch(query)
                        }
                    } catch (e: Exception) {
                        _state.value =
                            _state.value.copy(
                                isLoading = false,
                                hasSearched = true,
                                error = "Lỗi tìm kiếm: ${e.localizedMessage ?: "Không thể kết nối"}",
                            )
                    }
                }
        }

        fun applyFilter(
            genre: Genre?,
            chapterFilter: ChapterCountFilter = ChapterCountFilter.NONE,
        ) {
            val hasKeyword =
                _state.value.query
                    .trim()
                    .isNotBlank()
            val hasGenre = genre != null
            val hasChapterFilter = chapterFilter != ChapterCountFilter.NONE

            if (!hasGenre && !hasChapterFilter) {
                // No filter selected — if keyword exists, re-search; otherwise clear
                if (hasKeyword) {
                    search()
                } else {
                    clearFilter()
                }
                return
            }

            _state.update {
                it.copy(
                    selectedGenre = genre,
                    chapterFilter = chapterFilter,
                    isFilterActive = hasGenre || hasChapterFilter,
                )
            }

            if (hasKeyword) {
                // Keyword + filter → search first, then filter client-side
                search()
            } else {
                // No keyword + genre → server-side genre browsing
                genre?.let { browseByGenre(it, chapterFilter) }
            }
        }

        /**
         * Apply all filters at once from the unified FilterBottomSheet.
         * completedOnly=true là default, nên chỉ tính "active filter" khi user TẮT nó (=false).
         */
        fun applyAllFilters(
            genre: Genre?,
            chapterFilter: ChapterCountFilter,
            completedOnly: Boolean,
        ) {
            val hasKeyword =
                _state.value.query
                    .trim()
                    .isNotBlank()
            val hasGenre = genre != null
            val hasChapterFilter = chapterFilter != ChapterCountFilter.NONE
            // completedOnly=true là default → không tính là active filter
            // completedOnly=false = user chủ động tắt → tính là active filter
            val completedIsNonDefault = !completedOnly
            val hasAnyFilter = hasGenre || hasChapterFilter || completedIsNonDefault

            _state.update {
                it.copy(
                    selectedGenre = genre,
                    chapterFilter = chapterFilter,
                    completedOnly = completedOnly,
                    isFilterActive = hasAnyFilter,
                )
            }

            if (hasKeyword) {
                search()
            } else if (hasGenre) {
                genre?.let { browseByGenre(it, chapterFilter) }
            } else if (!hasAnyFilter) {
                // Không filter, không keyword → clear
                if (_state.value.hasSearched) clearFilter()
            }
        }

        /**
         * Clear all filters but preserve query text.
         */
        fun clearAllFilters() {
            val currentQuery = _state.value.query
            _state.update {
                it.copy(
                    selectedGenre = null,
                    chapterFilter = ChapterCountFilter.NONE,
                    completedOnly = true, // Reset về default (Full)
                    isFilterActive = false,
                )
            }
            if (currentQuery.trim().isNotBlank() && _state.value.hasSearched) {
                search()
            }
        }

        /**
         * Browse by genre from server (no keyword).
         */
        private fun browseByGenre(
            genre: Genre,
            chapterFilter: ChapterCountFilter,
        ) {
            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    _state.update { it.copy(isLoading = true, error = null, currentPage = 1, canLoadMore = false) }
                    try {
                        val results =
                            repository.getStoriesByGenre(
                                genre.slug,
                                page = 1,
                                minChapters = chapterFilter.minChapters,
                                completedOnly = _state.value.completedOnly,
                            )
                        _state.update {
                            it.copy(
                                results = results.toImmutableList(),
                                isLoading = false,
                                hasSearched = true,
                                canLoadMore = results.isNotEmpty(),
                                error = if (results.isEmpty()) "Không tìm thấy truyện nào trong thể loại ${genre.name}" else null,
                            )
                        }
                    } catch (e: Exception) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                hasSearched = true,
                                error = "Lỗi lọc: ${e.localizedMessage ?: "Không thể kết nối"}",
                            )
                        }
                    }
                }
        }

        /**
         * Apply genre + chapter count filters on a list of stories (client-side).
         */
        private fun applyLocalFilters(stories: List<Story>): List<Story> {
            val genre = _state.value.selectedGenre
            val minChapters = _state.value.chapterFilter.minChapters
            val completedOnly = _state.value.completedOnly

            return stories.filter { story ->
                val matchesGenre =
                    genre == null ||
                        story.genres.any {
                            it.trim().equals(genre.name.trim(), ignoreCase = true)
                        }
                val matchesChapters = story.totalChapters >= minChapters
                // Lọc completedOnly: cho qua truyện Full hoặc truyện chưa biết status
                // (nhiều nguồn như TruyenCom không có status trong search HTML)
                val matchesStatus =
                    !completedOnly ||
                        story.status.isBlank() ||
                        story.status.contains("Hoàn thành", ignoreCase = true) ||
                        story.status.contains("Full", ignoreCase = true)
                matchesGenre && matchesChapters && matchesStatus
            }
        }

        fun toggleCompletedFilter() {
            val newValue = !_state.value.completedOnly
            // completedOnly=true là default → tắt nó (=false) mới là active filter
            _state.update {
                it.copy(
                    completedOnly = newValue,
                    isFilterActive =
                        !newValue || it.selectedGenre != null || it.chapterFilter != ChapterCountFilter.NONE,
                )
            }
            // Re-apply filters if we have results
            if (_state.value.hasSearched) {
                val hasKeyword =
                    _state.value.query
                        .trim()
                        .isNotBlank()
                val genre = _state.value.selectedGenre
                if (hasKeyword) {
                    search()
                } else if (genre != null) {
                    // Re-browse genre với completedOnly mới
                    browseByGenre(genre, _state.value.chapterFilter)
                }
            }
        }

        fun loadMoreFilterResults() {
            val current = _state.value
            if (current.isLoadingMore || !current.isFilterActive || !current.canLoadMore) return
            // Load more is only for server-side genre browsing (no keyword)
            if (current.query.trim().isNotBlank()) return

            val genre = current.selectedGenre ?: return
            val nextPage = current.currentPage + 1
            val minChapters = current.chapterFilter.minChapters

            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    _state.update { it.copy(isLoadingMore = true) }
                    try {
                        val moreResults =
                            repository.getStoriesByGenre(
                                genre.slug,
                                page = nextPage,
                                minChapters = minChapters,
                                completedOnly = current.completedOnly,
                            )
                        _state.update {
                            it.copy(
                                results = deduplicateStories(it.results + moreResults).toImmutableList(),
                                isLoadingMore = false,
                                currentPage = nextPage,
                                canLoadMore = moreResults.isNotEmpty(),
                            )
                        }
                    } catch (e: Exception) {
                        _state.update {
                            it.copy(
                                isLoadingMore = false,
                                error = "Lỗi tải thêm: ${e.localizedMessage ?: "Không thể kết nối"}",
                            )
                        }
                    }
                }
        }

        fun clearFilter() {
            searchJob?.cancel()
            _state.value = SearchState()
        }

        fun searchFromHistory(query: String) {
            onQueryChange(query)
            search()
        }

        fun deleteHistoryItem(query: String) {
            viewModelScope.launch {
                searchHistoryRepository.deleteSearch(query)
            }
        }

        fun clearHistory() {
            viewModelScope.launch {
                searchHistoryRepository.clearSearchHistory()
            }
        }

        /** Dedup stories by compound key (sourceId:id) to prevent LazyColumn duplicate key crash */
        private fun deduplicateStories(stories: List<Story>): List<Story> {
            val seen = mutableSetOf<String>()
            return stories.filter { story ->
                val compoundKey = "${story.sourceId}:${story.id}"
                seen.add(compoundKey)
            }
        }
    }
