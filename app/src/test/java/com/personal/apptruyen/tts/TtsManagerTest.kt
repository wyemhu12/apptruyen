package com.personal.apptruyen.tts

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for TtsManager paragraph splitting logic.
 * This is pure logic that doesn't require Android framework.
 */
class TtsManagerTest {
    @Test
    fun `splits text by double newlines`() {
        val text = "Paragraph one.\n\nParagraph two."
        val result = TtsManager.splitIntoParagraphs(text)
        assertEquals(2, result.size)
        assertEquals("Paragraph one.", result[0])
        assertEquals("Paragraph two.", result[1])
    }

    @Test
    fun `splits text by single newlines`() {
        val text = "Line one.\nLine two.\nLine three."
        val result = TtsManager.splitIntoParagraphs(text)
        assertEquals(1, result.size)
        assertEquals("Line one.\nLine two.\nLine three.", result[0])
    }

    @Test
    fun `filters out blank paragraphs`() {
        val text = "Content.\n\n\n\n\nMore content."
        val result = TtsManager.splitIntoParagraphs(text)
        assertEquals(2, result.size)
    }

    @Test
    fun `handles empty input`() {
        val result = TtsManager.splitIntoParagraphs("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `handles whitespace-only input`() {
        val result = TtsManager.splitIntoParagraphs("   \n\n   \n  ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `handles single paragraph`() {
        val text = "Just one paragraph of text."
        val result = TtsManager.splitIntoParagraphs(text)
        assertEquals(1, result.size)
        assertEquals("Just one paragraph of text.", result[0])
    }

    @Test
    fun `splits long paragraph at sentence boundaries`() {
        // Create text longer than maxLength
        val sentence = "This is a test sentence. "
        val longParagraph = sentence.repeat(200) // ~5000 chars
        val result = TtsManager.splitIntoParagraphs(longParagraph, maxLength = 500)
        assertTrue(result.size > 1)
        // Each chunk should be under maxLength (approximately)
        result.forEach { chunk ->
            assertTrue(chunk.length <= 600, "Chunk too long: ${chunk.length}")
        }
    }

    @Test
    fun `preserves Vietnamese text content`() {
        val text = "Đây là đoạn văn đầu tiên.\n\nĐây là đoạn thứ hai.\n\nVà đoạn cuối."
        val result = TtsManager.splitIntoParagraphs(text)
        assertEquals(3, result.size)
        assertEquals("Đây là đoạn văn đầu tiên.", result[0])
        assertEquals("Đây là đoạn thứ hai.", result[1])
        assertEquals("Và đoạn cuối.", result[2])
    }

    @Test
    fun `trims whitespace from paragraphs`() {
        val text = "  Paragraph one.  \n\n  Paragraph two.  "
        val result = TtsManager.splitIntoParagraphs(text)
        assertEquals("Paragraph one.", result[0])
        assertEquals("Paragraph two.", result[1])
    }

    @Test
    fun `handles mixed newline styles`() {
        val text = "Para 1.\n\nPara 2.\nPara 3.\n\n\nPara 4."
        val result = TtsManager.splitIntoParagraphs(text)
        assertEquals(3, result.size)
        assertEquals("Para 1.", result[0])
        assertEquals("Para 2.\nPara 3.", result[1])
        assertEquals("Para 4.", result[2])
    }

    @Test
    fun `TtsManager does not call startService on init and queues play and speed until service connects`() =
        kotlinx.coroutines.test.runTest {
            val context = io.mockk.mockk<android.content.Context>(relaxed = true)
            val connectionSlot = io.mockk.slot<android.content.ServiceConnection>()
            io.mockk.every {
                context.bindService(any(), capture(connectionSlot), any<Int>())
            } returns true

            val manager = TtsManager(context, this)

            // On init, startService and bindService must NOT be called
            io.mockk.verify(exactly = 0) { context.startService(any()) }
            io.mockk.verify(exactly = 0) { context.bindService(any(), any(), any<Int>()) }

            // Setting speed before binding updates state immediately
            manager.setSpeed(1.5f)
            assertEquals(1.5f, manager.state.value.speed, 0.01f)

            // Calling play() triggers startService + bindService
            manager.play()
            io.mockk.verify(exactly = 1) { context.startService(any()) }
            io.mockk.verify(exactly = 1) { context.bindService(any(), any(), any<Int>()) }

            // Simulate service connected
            val mockService = io.mockk.mockk<TtsService>(relaxed = true)
            val serviceStateFlow = kotlinx.coroutines.flow.MutableStateFlow(TtsService.TtsState(speed = 1.5f))
            val skipFlow = kotlinx.coroutines.flow.MutableStateFlow(TtsService.ChapterSkipEvent.NONE)
            io.mockk.every { mockService.state } returns serviceStateFlow
            io.mockk.every { mockService.skipEvent } returns skipFlow
            val mockBinder = io.mockk.mockk<TtsService.TtsBinder>()
            io.mockk.every { mockBinder.getService() } returns mockService

            connectionSlot.captured.onServiceConnected(null, mockBinder)

            io.mockk.verify(exactly = 1) { mockService.setSpeed(1.5f) }
            io.mockk.verify(exactly = 1) { mockService.play() }

            manager.shutdown()
        }
}
