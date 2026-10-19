package com.personal.apptruyen.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.remote.ScraperException
import com.personal.apptruyen.data.repository.ISourceAuthRepository
import com.personal.apptruyen.data.repository.IStoryRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel
    @Inject
    constructor(
        private val repository: IStoryRepository,
        private val authRepository: ISourceAuthRepository,
    ) : ViewModel() {

        data class ExploreState(
            val stories: ImmutableList<Story> = persistentListOf(),
            val genres: ImmutableList<Genre> = persistentListOf(),
            val selectedGenre: Genre? = null,
            val isLoading: Boolean = false,
            val isLoadingMore: Boolean = false,
            val isRefreshing: Boolean = false,
            val error: String? = null,
            val currentPage: Int = 1,
            val canLoadMore: Boolean = true,
        )

        private val _state = MutableStateFlow(ExploreState())
        val state: StateFlow<ExploreState> = _state.asStateFlow()
        private var loadJob: Job? = null
        private var loadMoreJob: Job? = null

        val loginRequiredSources: StateFlow<Set<String>> =
            authRepository.loginRequiredSources
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

        fun dismissLoginPrompt(sourceId: String) {
            authRepository.clearLoginRequired(sourceId)
        }

        init {
            loadGenres()
            loadFirstPage()
        }

        private fun loadGenres() {
            viewModelScope.launch {
                try {
                    val genres = repository.getGenreList()
                    _state.update { it.copy(genres = genres.toImmutableList()) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // silent - genres are optional
                }
            }
        }

        fun selectGenre(genre: Genre?) {
            if (_state.value.selectedGenre == genre) return
            _state.update { it.copy(selectedGenre = genre) }
            loadFirstPage()
        }

        fun loadFirstPage() {
            loadJob?.cancel()
            loadMoreJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    val genre = _state.value.selectedGenre
                    _state.update { it.copy(isLoading = true, stories = persistentListOf(), error = null) }
                    try {
                        val stories =
                            if (genre != null) {
                                repository.getStoriesByGenre(genre.slug, page = 1, completedOnly = true)
                            } else {
                                repository.getCompletedStories(page = 1)
                            }
                        _state.update {
                            it.copy(
                                stories = stories.toImmutableList(),
                                isLoading = false,
                                currentPage = 1,
                                canLoadMore = stories.isNotEmpty(),
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = (e as? ScraperException)?.message ?: "Không thể kết nối. Vui lòng kiểm tra mạng và thử lại.",
                            )
                        }
                    }
                }
        }

        fun loadMore() {
            if (_state.value.isLoadingMore || !_state.value.canLoadMore) return

            loadMoreJob?.cancel()
            loadMoreJob =
                viewModelScope.launch {
                    _state.update { it.copy(isLoadingMore = true) }
                    try {
                        val genre = _state.value.selectedGenre
                        val nextPage = _state.value.currentPage + 1
                        val moreStories =
                            if (genre != null) {
                                repository.getStoriesByGenre(genre.slug, page = nextPage, completedOnly = true)
                            } else {
                                repository.getCompletedStories(page = nextPage)
                            }
                        _state.update {
                            val merged = deduplicateStories(it.stories + moreStories)
                            val hasNewItems = merged.size > it.stories.size
                            it.copy(
                                stories = merged.toImmutableList(),
                                isLoadingMore = false,
                                currentPage = if (hasNewItems) nextPage else it.currentPage,
                                canLoadMore = moreStories.isNotEmpty() && hasNewItems,
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

        fun refresh() {
            loadFirstPage()
        }

        fun pullToRefresh() {
            loadJob?.cancel()
            loadMoreJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _state.update { it.copy(isRefreshing = true, error = null) }
                    try {
                        val genre = _state.value.selectedGenre
                        val stories =
                            if (genre != null) {
                                repository.getStoriesByGenre(genre.slug, page = 1, completedOnly = true)
                            } else {
                                repository.getCompletedStories(page = 1)
                            }
                        _state.update {
                            it.copy(
                                stories = stories.toImmutableList(),
                                isRefreshing = false,
                                currentPage = 1,
                                canLoadMore = stories.isNotEmpty(),
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        _state.update {
                            it.copy(
                                isRefreshing = false,
                                error = (e as? ScraperException)?.message ?: "Không thể làm mới dữ liệu. Vui lòng kiểm tra mạng và thử lại.",
                            )
                        }
                    }
                }
        }

        /** Dedup stories by compound key (sourceId:id) to prevent LazyGrid duplicate key crash */
        private fun deduplicateStories(stories: List<Story>): List<Story> {
            val seen = mutableSetOf<String>()
            return stories.filter { story ->
                val compoundKey = "${story.sourceId}:${story.id}"
                seen.add(compoundKey)
            }
        }
    }
