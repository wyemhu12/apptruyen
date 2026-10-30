package com.personal.apptruyen.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.local.SourceIds
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.remote.ScraperException
import com.personal.apptruyen.data.repository.ISourceAuthRepository
import com.personal.apptruyen.data.repository.IStoryRepository
import com.personal.apptruyen.data.repository.SearchHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
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
        private val authRepository: ISourceAuthRepository,
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
            val maleOnly: Boolean = true, // Mặc định lọc Truyện nam trên Wikidich
            val isFilterActive: Boolean = false,
            val currentPage: Int = 1,
            val canLoadMore: Boolean = false,
            val activeSource: String = SourceIds.DEFAULT_SINGLE_SOURCE,
        )

        companion object {
            private val NON_MALE_WIKIDICH_GENDERS =
                setOf(
                    "ngôn tình",
                    "đam mỹ",
                    "bách hợp",
                    "nữ tần",
                    "nữ tôn",
                    "không cp",
                    "đa nguyên",
                )
        }

        private fun hasAnyNonDefaultFilter(
            genre: Genre?,
            chapterFilter: ChapterCountFilter,
            completedOnly: Boolean,
            maleOnly: Boolean,
            activeSource: String = _state.value.activeSource,
        ): Boolean =
            genre != null ||
                chapterFilter != ChapterCountFilter.NONE ||
                !completedOnly ||
                (activeSource == SourceIds.WIKIDICH && !maleOnly)

        private val _state = MutableStateFlow(SearchState())
        val state: StateFlow<SearchState> = _state.asStateFlow()
        private var searchJob: Job? = null

        private val _genres = MutableStateFlow<ImmutableList<Genre>>(persistentListOf())
        val genres: StateFlow<ImmutableList<Genre>> = _genres.asStateFlow()

        val recentSearches: StateFlow<List<String>> =
            searchHistoryRepository
                .getRecentSearches()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val loginRequiredSources: StateFlow<Set<String>> =
            authRepository.loginRequiredSources
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

        fun dismissLoginPrompt(sourceId: String) {
            authRepository.clearLoginRequired(sourceId)
        }

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

        private var lastObservedSource: String? = null
        private var lastSubmittedQuery: String = ""

        init {
            loadGenres()
            observeActiveSourceChanges()
        }

        private fun observeActiveSourceChanges() {
            viewModelScope.launch {
                repository.observeActiveSource().collectLatest { sourceId ->
                    val previous = lastObservedSource
                    lastObservedSource = sourceId
                    _state.update { it.copy(activeSource = sourceId) }
                    if (previous != null && previous != sourceId) {
                        handleActiveSourceSwitched()
                    }
                }
            }
        }

        private suspend fun handleActiveSourceSwitched() {
            val newGenres =
                try {
                    repository.getGenreList().toImmutableList()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to reload genres after source switch")
                    persistentListOf()
                }
            _genres.value = newGenres
            val current = _state.value
            val validGenre = current.selectedGenre?.takeIf { g -> newGenres.any { it.slug == g.slug } }
            val filterActive =
                hasAnyNonDefaultFilter(
                    genre = validGenre,
                    chapterFilter = current.chapterFilter,
                    completedOnly = current.completedOnly,
                    maleOnly = current.maleOnly,
                    activeSource = current.activeSource,
                )
            _state.update {
                it.copy(
                    selectedGenre = validGenre,
                    isFilterActive = filterActive,
                    error = null,
                )
            }
            val s = _state.value
            if (s.query.trim().isNotBlank() && s.hasSearched) {
                search()
            } else if (s.selectedGenre != null) {
                browseByGenre(s.selectedGenre, s.chapterFilter)
            } else if (s.isFilterActive) {
                browseByFilterOnly(s.chapterFilter)
            } else if (s.hasSearched || (current.selectedGenre != null && validGenre == null)) {
                clearFilter()
            }
        }

        fun selectSource(sourceId: String) {
            if (sourceId !in SourceIds.ALL_ACTIVE_SOURCES || sourceId == _state.value.activeSource) return
            viewModelScope.launch {
                repository.setActiveSource(sourceId)
            }
        }

        private fun loadGenres() {
            viewModelScope.launch {
                try {
                    _genres.value = repository.getGenreList().toImmutableList()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Genres load failed — filter won't have options, but search still works
                }
            }
        }

        fun onQueryChange(query: String) {
            _state.value = _state.value.copy(query = query)
        }

        fun search() {
            val currentState = _state.value
            val query = currentState.query.trim()
            if (query.isBlank()) return

            lastSubmittedQuery = query
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
                        val isWikidich = _state.value.activeSource == SourceIds.WIKIDICH
                        val rawResults =
                            if (isWikidich) {
                                repository.searchStories(
                                    keyword = query,
                                    completedOnly = _state.value.completedOnly,
                                    page = 1,
                                    maleOnly = _state.value.maleOnly,
                                    genreSlug = _state.value.selectedGenre?.slug,
                                )
                            } else {
                                // Keyword search trên TTV / SsTruyện luôn pass completedOnly=false
                                // vì HTML kết quả không có status đầy đủ, lọc client-side qua applyLocalFilters.
                                repository.searchStories(query, completedOnly = false)
                            }
                        val filtered = applyLocalFilters(rawResults).toImmutableList()
                        val canLoadMore =
                            isWikidich &&
                                rawResults.isNotEmpty() &&
                                _state.value.chapterFilter == ChapterCountFilter.NONE
                        _state.value =
                            _state.value.copy(
                                results = filtered,
                                isLoading = false,
                                hasSearched = true,
                                currentPage = 1,
                                canLoadMore = canLoadMore,
                                error = null,
                            )
                        if (rawResults.isNotEmpty()) {
                            searchHistoryRepository.saveSearch(query)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _state.value =
                            _state.value.copy(
                                isLoading = false,
                                hasSearched = true,
                                error = (e as? ScraperException)?.message ?: "Không thể kết nối. Vui lòng kiểm tra mạng và thử lại.",
                            )
                    }
                }
        }

        fun applyFilter(
            genre: Genre?,
            chapterFilter: ChapterCountFilter = ChapterCountFilter.NONE,
        ) {
            applyAllFilters(
                genre = genre,
                chapterFilter = chapterFilter,
                completedOnly = _state.value.completedOnly,
                maleOnly = _state.value.maleOnly,
            )
        }

        /**
         * Apply all filters at once from the unified FilterBottomSheet.
         * completedOnly=true và maleOnly=true là default, nên chỉ tính "active filter" khi user TẮT (=false).
         */
        fun applyAllFilters(
            genre: Genre?,
            chapterFilter: ChapterCountFilter,
            completedOnly: Boolean,
            maleOnly: Boolean,
        ) {
            val hasKeyword =
                _state.value.query
                    .trim()
                    .isNotBlank()
            val hasAnyFilter = hasAnyNonDefaultFilter(genre, chapterFilter, completedOnly, maleOnly)

            _state.update {
                it.copy(
                    selectedGenre = genre,
                    chapterFilter = chapterFilter,
                    completedOnly = completedOnly,
                    maleOnly = maleOnly,
                    isFilterActive = hasAnyFilter,
                )
            }

            if (hasKeyword) {
                search()
            } else if (genre != null) {
                browseByGenre(genre, chapterFilter)
            } else if (hasAnyFilter) {
                browseByFilterOnly(chapterFilter)
            } else if (_state.value.hasSearched) {
                clearFilter()
            }
        }

        /**
         * Clear all filters but preserve query text.
         */
        fun clearAllFilters() {
            val currentQuery = _state.value.query
            val hadSearched = _state.value.hasSearched
            _state.update {
                it.copy(
                    selectedGenre = null,
                    chapterFilter = ChapterCountFilter.NONE,
                    completedOnly = true, // Reset về default (Full)
                    maleOnly = true, // Reset về default (Truyện nam)
                    isFilterActive = false,
                )
            }
            if (currentQuery.trim().isNotBlank() && hadSearched) {
                search()
            } else if (hadSearched) {
                clearFilter()
            }
        }

        /**
         * Browse by genre from server (no keyword).
         */
        private fun browseByGenre(
            genre: Genre,
            chapterFilter: ChapterCountFilter,
        ) {
            lastSubmittedQuery = ""
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
                                maleOnly = _state.value.maleOnly,
                            )
                        _state.update {
                            it.copy(
                                results = results.toImmutableList(),
                                isLoading = false,
                                hasSearched = true,
                                canLoadMore = results.isNotEmpty(),
                                error = null,
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _state.update {
                            it.copy(
                                isLoading = false,
                                hasSearched = true,
                                error = (e as? ScraperException)?.message ?: "Không thể tải danh sách truyện. Vui lòng thử lại.",
                            )
                        }
                    }
                }
        }

        /**
         * Browse stories when only chapter count, completion status, or male filter is selected (no keyword, no genre).
         */
        private fun browseByFilterOnly(chapterFilter: ChapterCountFilter) {
            lastSubmittedQuery = ""
            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    _state.update {
                        it.copy(
                            isLoading = true,
                            error = null,
                            hasSearched = true,
                            currentPage = 1,
                            canLoadMore = false,
                        )
                    }
                    try {
                        val rawStories =
                            repository.getCompletedStories(
                                page = 1,
                                maleOnly = _state.value.maleOnly,
                            )
                        val filtered = applyLocalFilters(rawStories)
                        _state.update {
                            it.copy(
                                results = filtered.toImmutableList(),
                                isLoading = false,
                                currentPage = 1,
                                canLoadMore = rawStories.isNotEmpty() && chapterFilter == ChapterCountFilter.NONE,
                                error = null,
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                hasSearched = true,
                                error = (e as? ScraperException)?.message ?: "Không thể tải danh sách truyện. Vui lòng thử lại.",
                            )
                        }
                    }
                }
        }

        /**
         * Apply genre + chapter count + Wikidich maleOnly filters on a list of stories (client-side).
         */
        private fun applyLocalFilters(stories: List<Story>): List<Story> {
            val genre = _state.value.selectedGenre
            val minChapters = _state.value.chapterFilter.minChapters
            val completedOnly = _state.value.completedOnly
            val maleOnly = _state.value.maleOnly

            return stories.filter { story ->
                val matchesGenre =
                    genre == null ||
                        story.genres.isEmpty() ||
                        story.sourceId == "wikidich" ||
                        story.genres.any {
                            it.trim().equals(genre.name.trim(), ignoreCase = true)
                        }
                val matchesChapters = minChapters == 0 || story.totalChapters == 0 || story.totalChapters >= minChapters
                // Lọc completedOnly: cho qua truyện Full hoặc truyện chưa biết status
                val matchesStatus =
                    !completedOnly ||
                        story.status.isBlank() ||
                        story.status.contains("Hoàn thành", ignoreCase = true) ||
                        story.status.contains("Full", ignoreCase = true)
                val matchesMale =
                    !maleOnly ||
                        story.sourceId != "wikidich" ||
                        story.genres.none { g ->
                            java.text.Normalizer
                                .normalize(g.trim(), java.text.Normalizer.Form.NFC)
                                .lowercase() in NON_MALE_WIKIDICH_GENDERS
                        }
                matchesGenre && matchesChapters && matchesStatus && matchesMale
            }
        }

        fun toggleCompletedFilter() {
            val newValue = !_state.value.completedOnly
            // completedOnly=true là default → tắt nó (=false) mới là active filter
            _state.update {
                it.copy(
                    completedOnly = newValue,
                    isFilterActive =
                        hasAnyNonDefaultFilter(
                            genre = it.selectedGenre,
                            chapterFilter = it.chapterFilter,
                            completedOnly = newValue,
                            maleOnly = it.maleOnly,
                        ),
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
                } else if (_state.value.isFilterActive) {
                    browseByFilterOnly(_state.value.chapterFilter)
                } else {
                    clearFilter()
                }
            }
        }

        fun toggleMaleFilter() {
            val newValue = !_state.value.maleOnly
            _state.update {
                it.copy(
                    maleOnly = newValue,
                    isFilterActive =
                        hasAnyNonDefaultFilter(
                            genre = it.selectedGenre,
                            chapterFilter = it.chapterFilter,
                            completedOnly = it.completedOnly,
                            maleOnly = newValue,
                        ),
                )
            }
            if (_state.value.hasSearched) {
                val hasKeyword =
                    _state.value.query
                        .trim()
                        .isNotBlank()
                val genre = _state.value.selectedGenre
                if (hasKeyword) {
                    search()
                } else if (genre != null) {
                    browseByGenre(genre, _state.value.chapterFilter)
                } else if (_state.value.isFilterActive) {
                    browseByFilterOnly(_state.value.chapterFilter)
                } else {
                    clearFilter()
                }
            }
        }

        fun loadMore() {
            loadMoreFilterResults()
        }

        fun loadMoreFilterResults() {
            val current = _state.value
            if (current.isLoading ||
                current.isLoadingMore ||
                !current.canLoadMore ||
                (!current.isFilterActive && !current.hasSearched)
            ) {
                return
            }

            val nextPage = current.currentPage + 1
            val minChapters = current.chapterFilter.minChapters

            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    _state.update { it.copy(isLoadingMore = true) }
                    try {
                        val moreResults =
                            when {
                                lastSubmittedQuery.isNotBlank() -> {
                                    val rawMore =
                                        repository.searchStories(
                                            keyword = lastSubmittedQuery,
                                            completedOnly = current.completedOnly,
                                            page = nextPage,
                                            maleOnly = current.maleOnly,
                                            genreSlug = current.selectedGenre?.slug,
                                        )
                                    applyLocalFilters(rawMore)
                                }
                                current.selectedGenre != null -> {
                                    repository.getStoriesByGenre(
                                        current.selectedGenre.slug,
                                        page = nextPage,
                                        minChapters = minChapters,
                                        completedOnly = current.completedOnly,
                                        maleOnly = current.maleOnly,
                                    )
                                }
                                current.isFilterActive -> {
                                    val rawMore =
                                        repository.getCompletedStories(
                                            page = nextPage,
                                            maleOnly = current.maleOnly,
                                        )
                                    applyLocalFilters(rawMore)
                                }
                                else -> emptyList()
                            }
                        _state.update {
                            val merged = deduplicateStories(it.results + moreResults)
                            val hasNewItems = merged.size > it.results.size
                            it.copy(
                                results = merged.toImmutableList(),
                                isLoadingMore = false,
                                currentPage = if (hasNewItems) nextPage else it.currentPage,
                                canLoadMore = moreResults.isNotEmpty() && hasNewItems,
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _state.update {
                            it.copy(
                                isLoadingMore = false,
                                canLoadMore = false,
                                error = (e as? ScraperException)?.message ?: "Không thể tải thêm truyện. Vui lòng thử lại.",
                            )
                        }
                    }
                }
        }

        fun clearFilter() {
            searchJob?.cancel()
            lastSubmittedQuery = ""
            _state.value = SearchState(activeSource = _state.value.activeSource)
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
