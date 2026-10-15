package com.personal.apptruyen.data.model

/**
 * Kết quả scrape nội dung chương, bao gồm content và title thực (nếu có).
 * Dùng để trả title từ trang nội dung chương (ví dụ TTV không có title trong danh sách chương).
 */
data class ChapterContentResult(
    val content: String,
    val title: String? = null,
)
