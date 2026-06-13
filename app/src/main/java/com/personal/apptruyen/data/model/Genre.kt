package com.personal.apptruyen.data.model

/**
 * Domain model for a story genre/category (thể loại).
 * Data comes from truyencom.com navigation menu.
 */
data class Genre(
    val name: String, // e.g. "Tiên Hiệp"
    val slug: String, // e.g. "tien-hiep"
    val categoryId: Int = 0, // numeric ID for API calls, e.g. 39 for "Khoa Huyễn"
)
