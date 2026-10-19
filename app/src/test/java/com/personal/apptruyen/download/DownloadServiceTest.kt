package com.personal.apptruyen.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadServiceTest {

    @Test
    fun `buildCompletionText returns Vietnamese re-login message with downloaded count when loginRequired is true`() {
        val text =
            DownloadService.buildCompletionText(
                downloaded = 44,
                errors = 3,
                loginRequired = true,
            )

        assertEquals(
            "Đã tải 44 chương. Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại để tải tiếp.",
            text,
        )
    }

    @Test
    fun `buildCompletionText returns error count summary when loginRequired is false and errors greater than zero`() {
        val text =
            DownloadService.buildCompletionText(
                downloaded = 12,
                errors = 3,
                loginRequired = false,
            )

        assertEquals("Đã tải 12 chương (3 lỗi)", text)
    }

    @Test
    fun `buildCompletionText returns success message when loginRequired is false and errors is zero`() {
        val text =
            DownloadService.buildCompletionText(
                downloaded = 20,
                errors = 0,
                loginRequired = false,
            )

        assertEquals("Đã tải 20 chương thành công", text)
    }

    @Test
    fun `buildCompletionText returns Vietnamese Cloudflare rate limit message when rateLimited is true`() {
        val text =
            DownloadService.buildCompletionText(
                downloaded = 5,
                errors = 1,
                loginRequired = false,
                rateLimited = true,
            )

        assertEquals(
            "Đã tải 5 chương. Nguồn truyện đang giới hạn tốc độ truy cập (Cloudflare), vui lòng thử lại sau ít phút.",
            text,
        )
    }

    @Test
    fun `shouldStopOnJobCompletion and resolveStopStartIdOnCompletion return lastStartId only when token matches`() {
        assertTrue(DownloadService.shouldStopOnJobCompletion(activeToken = 2L, jobToken = 2L))
        assertFalse(DownloadService.shouldStopOnJobCompletion(activeToken = 2L, jobToken = 1L))
        assertTrue(DownloadService.shouldStopOnJobCompletion(activeToken = 0L, jobToken = 0L))

        assertEquals(
            42,
            DownloadService.resolveStopStartIdOnCompletion(
                activeToken = 2L,
                jobToken = 2L,
                lastStartId = 42,
            ),
        )
        assertNull(
            DownloadService.resolveStopStartIdOnCompletion(
                activeToken = 2L,
                jobToken = 1L,
                lastStartId = 42,
            ),
        )
    }

    @Test
    fun `buildCompletionText formats timedOut message in Vietnamese with priority over generic errors`() {
        val text =
            DownloadService.buildCompletionText(
                downloaded = 12,
                errors = 3,
                loginRequired = false,
                rateLimited = false,
                timedOut = true,
            )

        assertEquals(
            "Đã tải 12 chương. Đã tạm dừng do hệ thống giới hạn thời gian chạy nền, vui lòng mở ứng dụng để tải tiếp.",
            text,
        )
    }
}
