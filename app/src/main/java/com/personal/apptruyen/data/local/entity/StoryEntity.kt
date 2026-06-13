package com.personal.apptruyen.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stories",
    indices = [Index(value = ["isInLibrary"])],
)
data class StoryEntity(
    @PrimaryKey
    val id: String, // slug.numericId (truyencom) or slug (tangthuvien)
    val slug: String,
    val title: String,
    val author: String = "",
    val genres: String = "", // comma-separated
    val description: String = "",
    val url: String,
    val totalChapters: Int = 0,
    val coverImageUrl: String = "",
    val isInLibrary: Boolean = false,
    val addedTimestamp: Long = 0, // Set by addToLibrary(), 0 = not in library
    val sourceId: String = "truyencom", // Source identifier
    val status: String = "", // "Hoàn thành", "Đang ra", etc.
    // Bật/tắt thay chữ riêng cho truyện này (mặc định: tắt)
    val storyReplacementsEnabled: Boolean = false,
    // Danh sách thay thế riêng cho truyện, lưu dạng JSON giống global
    val storyCustomReplacements: String = "",
)
