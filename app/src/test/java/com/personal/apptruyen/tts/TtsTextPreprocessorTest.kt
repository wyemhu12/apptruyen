package com.personal.apptruyen.tts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for TtsTextPreprocessor.
 * Pure logic — no Android framework required.
 */
class TtsTextPreprocessorTest {

    // ── Acronym dots ──

    @Test
    fun `removes dots from acronym with trailing dot`() {
        assertEquals("SHIELD", TtsTextPreprocessor.preprocess("S.H.I.E.L.D."))
    }

    @Test
    fun `removes dots from acronym without trailing dot`() {
        assertEquals("SHIELD", TtsTextPreprocessor.preprocess("S.H.I.E.L.D"))
    }

    @Test
    fun `removes dots from short acronym`() {
        assertEquals("USA", TtsTextPreprocessor.preprocess("U.S.A."))
    }

    @Test
    fun `acronym in sentence`() {
        assertEquals(
            "Anh ấy làm việc cho SHIELD từ lâu",
            TtsTextPreprocessor.preprocess("Anh ấy làm việc cho S.H.I.E.L.D. từ lâu"),
        )
    }

    // ── Abbreviations ──

    @Test
    fun `replaces Dr dot with space`() {
        assertEquals("Doctor Bach", TtsTextPreprocessor.preprocess("Dr. Bach"))
    }

    @Test
    fun `replaces Dr dot without space`() {
        assertEquals("Doctor Bach", TtsTextPreprocessor.preprocess("Dr.Bach"))
    }

    @Test
    fun `replaces Dr space dot`() {
        assertEquals("Doctor Bach", TtsTextPreprocessor.preprocess("Dr .Bach"))
    }

    @Test
    fun `replaces Mr dot`() {
        assertEquals("Mister Bean", TtsTextPreprocessor.preprocess("Mr. Bean"))
    }

    @Test
    fun `replaces Mrs dot`() {
        assertEquals("Misses Smith", TtsTextPreprocessor.preprocess("Mrs. Smith"))
    }

    @Test
    fun `replaces Prof dot`() {
        assertEquals("Professor Xuân", TtsTextPreprocessor.preprocess("Prof. Xuân"))
    }

    @Test
    fun `replaces etc dot`() {
        assertEquals("et cetera", TtsTextPreprocessor.preprocess("etc."))
    }

    // ── Mid-word dots (fallback) ──

    @Test
    fun `replaces dot between two words`() {
        assertEquals("abc xyz", TtsTextPreprocessor.preprocess("abc.xyz"))
    }

    // ── Preserved patterns (should NOT be modified) ──

    @Test
    fun `preserves sentence-ending dot`() {
        assertEquals("Hết rồi.", TtsTextPreprocessor.preprocess("Hết rồi."))
    }

    @Test
    fun `preserves decimal numbers`() {
        assertEquals("3.14", TtsTextPreprocessor.preprocess("3.14"))
    }

    @Test
    fun `preserves multiple sentences`() {
        assertEquals(
            "Câu một. Câu hai.",
            TtsTextPreprocessor.preprocess("Câu một. Câu hai."),
        )
    }

    @Test
    fun `preserves plain Vietnamese text`() {
        val text = "Đây là đoạn văn tiếng Việt bình thường."
        assertEquals(text, TtsTextPreprocessor.preprocess(text))
    }

    // ── Combined patterns ──

    @Test
    fun `handles mixed patterns in one sentence`() {
        assertEquals(
            "Doctor Bach và SHIELD đã gặp nhau.",
            TtsTextPreprocessor.preprocess("Dr.Bach và S.H.I.E.L.D. đã gặp nhau."),
        )
    }

    @Test
    fun `handles Mr Bean with SHIELD`() {
        assertEquals(
            "Mister Bean là thành viên SHIELD",
            TtsTextPreprocessor.preprocess("Mr. Bean là thành viên S.H.I.E.L.D"),
        )
    }
}
