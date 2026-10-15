package com.personal.apptruyen.download

import org.junit.jupiter.api.Assertions.assertEquals
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
}
