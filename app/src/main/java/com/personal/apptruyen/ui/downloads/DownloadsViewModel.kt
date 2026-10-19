package com.personal.apptruyen.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.IDownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        private val repository: IDownloadRepository,
    ) : ViewModel() {

        data class DownloadedStoryInfo(
            val story: Story,
            val downloadedCount: Int = 0,
            val estimatedSizeBytes: Long = 0,
        )

        private val _stories = MutableStateFlow<List<DownloadedStoryInfo>>(emptyList())
        val stories: StateFlow<List<DownloadedStoryInfo>> = _stories.asStateFlow()

        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        val filteredStories: StateFlow<List<DownloadedStoryInfo>> =
            combine(_stories, _searchQuery) { stories, query ->
                if (query.isBlank()) {
                    stories
                } else {
                    stories.filter { it.story.title.contains(query.trim(), ignoreCase = true) }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        init {
            viewModelScope.launch {
                // Combine stories with aggregate download stats — single query instead of N+1
                combine(
                    repository.getDownloadedStories(),
                    repository.getDownloadStats(),
                ) { storyList, statsList ->
                    val statsMap = statsList.associateBy { it.storyId }
                    storyList.map { story ->
                        val stats = statsMap[story.id]
                        DownloadedStoryInfo(
                            story = story,
                            downloadedCount = stats?.downloadedCount ?: 0,
                            estimatedSizeBytes = stats?.totalSize ?: 0L,
                        )
                    }
                }.collectLatest { infos ->
                    _stories.value = infos
                }
            }
        }

        fun updateSearchQuery(query: String) {
            _searchQuery.value = query
        }

        fun clearError() {
            _errorMessage.value = null
        }

        fun deleteDownloads(storyId: String) {
            viewModelScope.launch {
                runCatching { repository.deleteAllDownloads(storyId) }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        Timber.e(e, "Failed to delete downloads for storyId=%s", storyId)
                        _errorMessage.value = "Không xóa được các chương đã tải. Vui lòng thử lại."
                    }
            }
        }

        fun deleteEntireStory(storyId: String) {
            viewModelScope.launch {
                runCatching { repository.deleteStory(storyId) }
                    .onFailure { e ->
                        if (e is CancellationException) throw e
                        Timber.e(e, "Failed to delete story for storyId=%s", storyId)
                        _errorMessage.value = "Không xóa được truyện. Vui lòng thử lại."
                    }
            }
        }
    }
