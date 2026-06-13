package com.personal.apptruyen.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.IDownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

        fun deleteDownloads(storyId: String) {
            viewModelScope.launch {
                repository.deleteAllDownloads(storyId)
            }
        }

        fun deleteEntireStory(storyId: String) {
            viewModelScope.launch {
                repository.deleteStory(storyId)
            }
        }
    }
