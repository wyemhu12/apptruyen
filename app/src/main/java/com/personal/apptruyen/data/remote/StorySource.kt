package com.personal.apptruyen.data.remote

import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.data.model.Story

/**
 * Interface chung cho mọi nguồn scrape truyện.
 * Tất cả implementations chỉ trả về truyện đã hoàn thành, sắp xếp theo mới nhất.
 */
interface StorySource {
    val sourceId: String // e.g. "truyencom", "tangthuvien"
    val sourceName: String // e.g. "TruyenCom", "Tàng Thư Viện"
    val baseUrl: String // e.g. "https://truyencom.com"

    /** Tìm truyện theo keyword, có thể lọc chỉ truyện đã hoàn thành */
    suspend fun search(
        keyword: String,
        completedOnly: Boolean = false,
    ): List<Story>

    /** Lấy chi tiết truyện */
    suspend fun getStoryDetail(storyUrl: String): Story

    /** Lấy danh sách chương (trang đầu + totalPages) */
    suspend fun getFirstPageChapters(storyUrl: String): FirstPageResult

    /** Crawl các trang chương còn lại */
    suspend fun getRemainingChapters(
        storyUrl: String,
        totalPages: Int,
        onBatchReady: suspend (List<Chapter>) -> Unit,
    )

    /** Lấy toàn bộ danh sách chương */
    suspend fun getChapterList(storyUrl: String): List<Chapter>

    /** Lấy nội dung chương */
    suspend fun getChapterContent(chapterUrl: String): String

    /** Truyện hoàn thành mới nhất (phân trang) */
    suspend fun getCompletedStories(page: Int = 1): List<Story>

    /** Truyện theo thể loại (phân trang). completedOnly=true chỉ lấy truyện hoàn thành. */
    suspend fun getStoriesByGenre(
        genreSlug: String,
        page: Int = 1,
        minChapters: Int = 0,
        completedOnly: Boolean = true,
    ): List<Story>

    /** Danh sách thể loại */
    suspend fun getGenreList(): List<Genre>

    /** Kết quả trang đầu chapter với tổng số trang */
    data class FirstPageResult(
        val chapters: List<Chapter>,
        val totalPages: Int,
    )
}
