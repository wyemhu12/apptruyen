package com.personal.apptruyen.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Thống kê đọc theo ngày.
 * PrimaryKey = date string "yyyy-MM-dd" để dễ query theo khoảng thời gian.
 */
@Entity(tableName = "reading_stats")
data class ReadingStatsEntity(
    @PrimaryKey
    val date: String, // "2026-02-23"
    val totalReadingTimeMs: Long = 0, // tổng ms đọc trong ngày
    val chaptersRead: Int = 0, // số chương đọc trong ngày
    val storiesRead: Int = 0, // số truyện khác nhau đọc trong ngày
)
