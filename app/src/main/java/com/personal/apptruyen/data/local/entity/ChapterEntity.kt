package com.personal.apptruyen.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(
    tableName = "chapters",
    primaryKeys = ["storyId", "chapterNumber"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["storyId"])],
)
data class ChapterEntity(
    val storyId: String,
    val chapterNumber: Int,
    val title: String,
    val url: String,
    val content: String? = null,
    val isDownloaded: Boolean = false,
    val downloadedTimestamp: Long? = null,
)
