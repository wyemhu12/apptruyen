package com.personal.apptruyen.data.model

/**
 * Reading progress for a story.
 */
data class ReadingProgress(
    val storyId: String,
    val lastChapterNumber: Int,
    val scrollPosition: Int = 0,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
)
