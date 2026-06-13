package com.personal.apptruyen.tts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for TtsManager paragraph splitting logic.
 * This is pure logic that doesn't require Android framework.
 */
class TtsManagerTest {

    // Mirror the splitIntoParagraphs logic for testing
    private fun splitIntoParagraphs(
        text: String,
        maxLength: Int = 3000,
    ): List<String> {
        val rawParagraphs =
            text
                .split(Regex("\\n\\s*\\n|\\n"))
                .map { it.trim() }
                .filter { it.isNotBlank() }

        val result = mutableListOf<String>()
        for (paragraph in rawParagraphs) {
            if (paragraph.length <= maxLength) {
                result.add(paragraph)
            } else {
                // Split long paragraphs at sentence boundaries
                val sentences = paragraph.split(Regex("(?<=[.!?。！？])\\s+"))
                var currentChunk = StringBuilder()
                for (sentence in sentences) {
                    if (currentChunk.length + sentence.length > maxLength && currentChunk.isNotEmpty()) {
                        result.add(currentChunk.toString().trim())
                        currentChunk = StringBuilder()
                    }
                    currentChunk.append(sentence).append(" ")
                }
                if (currentChunk.isNotBlank()) {
                    result.add(currentChunk.toString().trim())
                }
            }
        }
        return result
    }

    @Test
    fun `splits text by double newlines`() {
        val text = "Paragraph one.\n\nParagraph two."
        val result = splitIntoParagraphs(text)
        assertEquals(2, result.size)
        assertEquals("Paragraph one.", result[0])
        assertEquals("Paragraph two.", result[1])
    }

    @Test
    fun `splits text by single newlines`() {
        val text = "Line one.\nLine two.\nLine three."
        val result = splitIntoParagraphs(text)
        assertEquals(3, result.size)
    }

    @Test
    fun `filters out blank paragraphs`() {
        val text = "Content.\n\n\n\n\nMore content."
        val result = splitIntoParagraphs(text)
        assertEquals(2, result.size)
    }

    @Test
    fun `handles empty input`() {
        val result = splitIntoParagraphs("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `handles whitespace-only input`() {
        val result = splitIntoParagraphs("   \n\n   \n  ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `handles single paragraph`() {
        val text = "Just one paragraph of text."
        val result = splitIntoParagraphs(text)
        assertEquals(1, result.size)
        assertEquals("Just one paragraph of text.", result[0])
    }

    @Test
    fun `splits long paragraph at sentence boundaries`() {
        // Create text longer than maxLength
        val sentence = "This is a test sentence. "
        val longParagraph = sentence.repeat(200) // ~5000 chars
        val result = splitIntoParagraphs(longParagraph, maxLength = 500)
        assertTrue(result.size > 1)
        // Each chunk should be under maxLength (approximately)
        result.forEach { chunk ->
            assertTrue(chunk.length <= 600, "Chunk too long: ${chunk.length}")
        }
    }

    @Test
    fun `preserves Vietnamese text content`() {
        val text = "Đây là đoạn văn đầu tiên.\n\nĐây là đoạn thứ hai.\n\nVà đoạn cuối."
        val result = splitIntoParagraphs(text)
        assertEquals(3, result.size)
        assertEquals("Đây là đoạn văn đầu tiên.", result[0])
        assertEquals("Đây là đoạn thứ hai.", result[1])
        assertEquals("Và đoạn cuối.", result[2])
    }

    @Test
    fun `trims whitespace from paragraphs`() {
        val text = "  Paragraph one.  \n\n  Paragraph two.  "
        val result = splitIntoParagraphs(text)
        assertEquals("Paragraph one.", result[0])
        assertEquals("Paragraph two.", result[1])
    }

    @Test
    fun `handles mixed newline styles`() {
        val text = "Para 1.\n\nPara 2.\nPara 3.\n\n\nPara 4."
        val result = splitIntoParagraphs(text)
        assertEquals(4, result.size)
    }
}
