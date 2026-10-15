package com.personal.apptruyen.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

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
