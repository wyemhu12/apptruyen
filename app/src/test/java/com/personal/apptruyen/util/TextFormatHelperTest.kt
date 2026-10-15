package com.personal.apptruyen.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class TextFormatHelperTest {
    @Test
    fun `formatChapter keeps hyphenated names like Spider-Man on the same line`() {
        val input = "Anh ấy là Spider-Man và X-Men cùng chiến đấu với Ho-Oh, Porygon-Z."
        val formatted = TextFormatHelper.formatChapter(input)

        assertEquals(
            "Anh ấy là Spider-Man và X-Men cùng chiến đấu với Ho-Oh, Porygon-Z.",
            formatted,
        )
        assertFalse(formatted.contains("Spider\n-Man"))
        assertFalse(formatted.contains("X\n-Men"))
        assertFalse(formatted.contains("Ho\n-Oh"))
        assertFalse(formatted.contains("Porygon\n-Z"))
    }

    @Test
    fun `formatChapter keeps inline dashes and compound words intact`() {
        val input = "Peter Parker - tức Spider-Man - đang tra cứu từ điển Hán-Việt."
        val formatted = TextFormatHelper.formatChapter(input)

        assertEquals(
            "Peter Parker - tức Spider-Man - đang tra cứu từ điển Hán-Việt.",
            formatted,
        )
    }

    @Test
    fun `formatChapter preserves existing dialogue paragraphs starting with dash`() {
        val input = "Hắn bước vào phòng.\n\n- Ngươi là Spider-Man sao?\n\n- Đúng vậy."
        val formatted = TextFormatHelper.formatChapter(input)

        assertEquals(
            "Hắn bước vào phòng.\n\n- Ngươi là Spider-Man sao?\n\n- Đúng vậy.",
            formatted,
        )
    }

    @Test
    fun `formatChapter returns blank text unchanged and preserves acronyms`() {
        assertEquals("   ", TextFormatHelper.formatChapter("   "))

        val input = "Đặc vụ S.H.I.E.L.D gặp Spider-Man... . Họ nói chuyện!!!"
        val formatted = TextFormatHelper.formatChapter(input)

        assertEquals("Đặc vụ S.H.I.E.L.D gặp Spider-Man... Họ nói chuyện!", formatted)
    }
}
