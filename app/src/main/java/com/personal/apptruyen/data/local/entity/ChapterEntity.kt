package com.personal.apptruyen.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

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
