package com.personal.apptruyen.data.local.entity

/**
 * Room projection for aggregate download stats per story.
 * Returned by ChapterDao.getDownloadStats() — single query replaces N+1 pattern.
 */
data class DownloadStats(
    val storyId: String,
    val downloadedCount: Int,
    val totalSize: Long,
)
