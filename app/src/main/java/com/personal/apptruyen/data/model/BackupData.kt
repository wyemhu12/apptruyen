package com.personal.apptruyen.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersion: String = "",
    val stories: List<StoryBackup> = emptyList(),
    val chapters: List<ChapterBackup> = emptyList(),
    val readingProgress: List<ProgressBackup> = emptyList(),
    val readingStats: List<StatsBackup> = emptyList(),
    val searchHistory: List<SearchBackup> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

@Serializable
data class StoryBackup(
    val id: String,
    val slug: String,
    val title: String,
    val author: String = "",
    val genres: String = "",
    val description: String = "",
    val url: String,
    val totalChapters: Int = 0,
    val coverImageUrl: String = "",
    val isInLibrary: Boolean = false,
    val addedTimestamp: Long = 0,
    val sourceId: String = "truyencom",
    val status: String = "",
    val storyReplacementsEnabled: Boolean = false,
    val storyCustomReplacements: String = "",
)

@Serializable
data class ChapterBackup(
    val storyId: String,
    val chapterNumber: Int,
    val title: String,
    val url: String,
    val isDownloaded: Boolean = false,
    val downloadedTimestamp: Long? = null,
    val hasContent: Boolean = false, // true = file exists in chapters/{storyId}/{number}.txt
)

@Serializable
data class ProgressBackup(
    val storyId: String,
    val lastChapterNumber: Int,
    val scrollPosition: Int = 0,
    val lastReadTimestamp: Long = 0,
)

@Serializable
data class StatsBackup(
    val date: String,
    val totalReadingTimeMs: Long = 0,
    val chaptersRead: Int = 0,
    val storiesRead: Int = 0,
)

@Serializable
data class SearchBackup(
    val query: String,
    val timestamp: Long = 0,
)
