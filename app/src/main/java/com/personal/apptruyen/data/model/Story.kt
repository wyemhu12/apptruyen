package com.personal.apptruyen.data.model

/**
 * Domain model for a story (truyện).
 */
data class Story(
    val id: String, // slug.numericId e.g. "pham-nhan-tu-tien.14"
    val slug: String, // e.g. "pham-nhan-tu-tien"
    val title: String,
    val author: String = "",
    val genres: List<String> = emptyList(),
    val description: String = "",
    val url: String,
    val totalChapters: Int = 0,
    val coverImageUrl: String = "",
    val sourceId: String = "truyencom", // Source identifier: "truyencom", "tangthuvien"
    val status: String = "", // "Hoàn thành", "Đang ra", etc.
)
