package com.personal.apptruyen.data.model

/**
 * Domain model for a chapter (chương).
 */
data class Chapter(
    val storyId: String,
    val chapterNumber: Int,
    val title: String,
    val url: String,
    val content: String? = null,
    val isDownloaded: Boolean = false,
)
