package com.personal.apptruyen.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_progress",
    indices = [Index(value = ["lastReadTimestamp"])],
)
data class ReadingProgressEntity(
    @PrimaryKey
    val storyId: String,
    val lastChapterNumber: Int,
    val scrollPosition: Int = 0,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
)
