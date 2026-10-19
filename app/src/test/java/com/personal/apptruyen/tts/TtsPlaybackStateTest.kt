package com.personal.apptruyen.tts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TtsPlaybackStateTest {

    @Test
    fun `basic transitions Idle to Playing to Paused to Idle`() =
        runTest {
            val sm = TtsPlaybackStateMachine(scope = backgroundScope)

            assertEquals(TtsPlaybackStateMachine.State.Idle, sm.state)
            assertFalse(sm.isActiveOrBuffering)
            assertFalse(sm.playWhenReady)

            assertTrue(sm.onPlayRequested())
            assertEquals(TtsPlaybackStateMachine.State.Playing, sm.state)
            assertTrue(sm.isActiveOrBuffering)
            assertTrue(sm.playWhenReady)

            sm.onPauseRequested()
            assertEquals(TtsPlaybackStateMachine.State.Paused, sm.state)
            assertFalse(sm.isActiveOrBuffering)
            assertFalse(sm.playWhenReady)

            sm.onStopRequested()
            assertEquals(TtsPlaybackStateMachine.State.Idle, sm.state)
            assertFalse(sm.isActiveOrBuffering)
            assertFalse(sm.playWhenReady)
        }

    @Test
    fun `auto-advance buffering keeps playWhenReady true and resumes when content loads before 10s`() =
        runTest {
            var timedOut = false
            val sm =
                TtsPlaybackStateMachine(
                    scope = backgroundScope,
                    onTimeout = { timedOut = true },
                )

            sm.onPlayRequested()
            sm.startAutoAdvanceBuffering()

            assertEquals(TtsPlaybackStateMachine.State.BufferingAutoAdvance, sm.state)
            assertTrue(sm.playWhenReady)
            assertTrue(sm.isActiveOrBuffering)

            // Sau 3 giây, chương mới tải xong
            advanceTimeBy(3_000L)
            runCurrent()
            assertFalse(timedOut)

            sm.onAutoAdvanceContentLoaded()
            assertFalse(sm.suppressNextPlay)
            assertTrue(sm.onPlayRequested())
            assertEquals(TtsPlaybackStateMachine.State.Playing, sm.state)

            // Chạy qua mốc 10 giây cũ — timer đã bị hủy nên không được gọi onTimeout
            advanceTimeBy(10_000L)
            runCurrent()
            assertFalse(timedOut)
            assertEquals(TtsPlaybackStateMachine.State.Playing, sm.state)
        }

    @Test
    fun `auto-advance buffering transitions to Ended and invokes onTimeout after 10s`() =
        runTest {
            var timedOut = false
            val sm =
                TtsPlaybackStateMachine(
                    scope = backgroundScope,
                    onTimeout = { timedOut = true },
                )

            sm.onPlayRequested()
            sm.startAutoAdvanceBuffering()

            advanceTimeBy(9_999L)
            runCurrent()
            assertFalse(timedOut)
            assertEquals(TtsPlaybackStateMachine.State.BufferingAutoAdvance, sm.state)

            advanceTimeBy(1L)
            runCurrent()
            assertTrue(timedOut)
            assertEquals(TtsPlaybackStateMachine.State.Ended, sm.state)
            assertFalse(sm.playWhenReady)
            assertFalse(sm.isActiveOrBuffering)
        }

    @Test
    fun `Ca 1 - manual next or prev chapter from Playing (pause then loadChapter) plays normally (M-SM2 TC-03)`() =
        runTest {
            val sm = TtsPlaybackStateMachine(scope = backgroundScope)

            // Đang đọc -> user bấm Next/Prev hoặc 2-chạm tai nghe -> ReaderViewModel gọi pause() trước
            assertTrue(sm.onPlayRequested())
            assertEquals(TtsPlaybackStateMachine.State.Playing, sm.state)

            sm.onPauseRequested()
            assertEquals(TtsPlaybackStateMachine.State.Paused, sm.state)

            // ReaderViewModel.loadChapter(autoPlay = true) gọi setContentForAutoAdvance rồi play()
            sm.onAutoAdvanceContentLoaded()
            assertFalse(sm.suppressNextPlay)

            val shouldPlay = sm.onPlayRequested()
            assertTrue(shouldPlay)
            assertEquals(TtsPlaybackStateMachine.State.Playing, sm.state)
        }

    @Test
    fun `Ca 2 - navigateToChapter from Idle (stop then loadChapter) plays normally (M-SM2 TC-03)`() =
        runTest {
            val sm = TtsPlaybackStateMachine(scope = backgroundScope)

            assertTrue(sm.onPlayRequested())
            // ReaderViewModel.navigateToChapter() gọi stop() trước
            sm.onStopRequested()
            assertEquals(TtsPlaybackStateMachine.State.Idle, sm.state)

            // Sau đó loadChapter(autoPlay = true) gọi setContentForAutoAdvance rồi play()
            sm.onAutoAdvanceContentLoaded()
            assertFalse(sm.suppressNextPlay)

            val shouldPlay = sm.onPlayRequested()
            assertTrue(shouldPlay)
            assertEquals(TtsPlaybackStateMachine.State.Playing, sm.state)
        }

    @Test
    fun `Ca 3 - pause during BufferingAutoAdvance suppresses ViewModel play once then allows manual play (M-SM3a TC-10)`() =
        runTest {
            var timedOut = false
            val sm =
                TtsPlaybackStateMachine(
                    scope = backgroundScope,
                    onTimeout = { timedOut = true },
                )

            sm.onPlayRequested()
            sm.startAutoAdvanceBuffering()
            assertEquals(TtsPlaybackStateMachine.State.BufferingAutoAdvance, sm.state)

            // User bấm Pause ở giây thứ 1 khi đang tải chương mới
            advanceTimeBy(1_000L)
            runCurrent()
            sm.onPauseRequested()
            assertEquals(TtsPlaybackStateMachine.State.PausedDuringAutoAdvance, sm.state)
            assertFalse(sm.playWhenReady)

            // Chương mới tải xong ở giây thứ 2: ReaderViewModel:448 gọi setContentForAutoAdvance
            advanceTimeBy(1_000L)
            runCurrent()
            sm.onAutoAdvanceContentLoaded()
            assertEquals(TtsPlaybackStateMachine.State.Paused, sm.state)
            assertTrue(sm.suppressNextPlay)

            // Ngay lập tức ReaderViewModel:455 gọi ttsManager.play() -> bị chặn một lần
            val autoPlayAllowed = sm.onPlayRequested()
            assertFalse(autoPlayAllowed)
            assertEquals(TtsPlaybackStateMachine.State.Paused, sm.state)
            assertFalse(sm.suppressNextPlay)

            // Sau 10s nữa timer cũ không được nổ
            advanceTimeBy(10_000L)
            runCurrent()
            assertFalse(timedOut)

            // Khi user chủ động bấm Play lại -> được phép phát bình thường
            val manualPlayAllowed = sm.onPlayRequested()
            assertTrue(manualPlayAllowed)
            assertEquals(TtsPlaybackStateMachine.State.Playing, sm.state)
        }

    @Test
    fun `Ca 4 - headset 1-tap and AudioFocus loss during BufferingAutoAdvance enter PausedDuringAutoAdvance (M-SM3b)`() =
        runTest {
            val sm = TtsPlaybackStateMachine(scope = backgroundScope)

            // Kịch bản 1: 1 chạm tai nghe lúc đang BufferingAutoAdvance
            sm.onPlayRequested()
            sm.startAutoAdvanceBuffering()
            assertTrue(sm.isActiveOrBuffering)

            // Mô phỏng nhánh 1 chạm trong TtsService: if (stateMachine.isActiveOrBuffering) pause() else play()
            if (sm.isActiveOrBuffering) {
                sm.onPauseRequested()
            } else {
                sm.onPlayRequested()
            }
            assertEquals(TtsPlaybackStateMachine.State.PausedDuringAutoAdvance, sm.state)

            // Kịch bản 2: Mất AudioFocus (AUDIOFOCUS_LOSS_TRANSIENT do cuộc gọi) lúc đang BufferingAutoAdvance
            sm.onPlayRequested()
            sm.startAutoAdvanceBuffering()
            assertTrue(sm.isActiveOrBuffering)

            if (sm.isActiveOrBuffering) {
                sm.onPauseRequested()
            }
            assertEquals(TtsPlaybackStateMachine.State.PausedDuringAutoAdvance, sm.state)
            sm.onAutoAdvanceContentLoaded()
            assertFalse(sm.onPlayRequested())
            assertEquals(TtsPlaybackStateMachine.State.Paused, sm.state)
        }

    @Test
    fun `late content arriving after timeout Ended resets cleanly and allows play (m-TMR)`() =
        runTest {
            var timedOut = false
            val sm =
                TtsPlaybackStateMachine(
                    scope = backgroundScope,
                    onTimeout = { timedOut = true },
                )

            sm.onPlayRequested()
            sm.startAutoAdvanceBuffering()
            advanceTimeBy(10_000L)
            runCurrent()
            assertTrue(timedOut)
            assertEquals(TtsPlaybackStateMachine.State.Ended, sm.state)

            // Chương mới về muộn sau khi đã timeout
            sm.onAutoAdvanceContentLoaded()
            assertEquals(TtsPlaybackStateMachine.State.Idle, sm.state)
            assertFalse(sm.suppressNextPlay)
            assertTrue(sm.onPlayRequested())
            assertEquals(TtsPlaybackStateMachine.State.Playing, sm.state)
        }

    @Test
    fun `shouldProcessUtteranceDone rejects stale utteranceId or paused state`() =
        runTest {
            val sm = TtsPlaybackStateMachine(scope = backgroundScope)
            assertTrue(sm.onPlayRequested())

            assertTrue(
                TtsPlaybackStateMachine.shouldProcessUtteranceDone(
                    utteranceId = "paragraph_3",
                    currentParagraphIndex = 3,
                    isPlaying = sm.state == TtsPlaybackStateMachine.State.Playing,
                ),
            )
            assertFalse(
                TtsPlaybackStateMachine.shouldProcessUtteranceDone(
                    utteranceId = "paragraph_2",
                    currentParagraphIndex = 3,
                    isPlaying = sm.state == TtsPlaybackStateMachine.State.Playing,
                ),
            )
            assertFalse(
                TtsPlaybackStateMachine.shouldProcessUtteranceDone(
                    utteranceId = "paragraph_3",
                    currentParagraphIndex = 3,
                    isPlaying = false,
                ),
            )

            sm.onPauseRequested()
            assertFalse(
                TtsPlaybackStateMachine.shouldProcessUtteranceDone(
                    utteranceId = "paragraph_3",
                    currentParagraphIndex = 3,
                    isPlaying = sm.state == TtsPlaybackStateMachine.State.Playing,
                ),
            )
        }

    @Test
    fun `onStopRequested followed by onPlayRequested clears suppressNextPlay after onAutoAdvanceContentLoaded in PausedDuringAutoAdvance`() =
        runTest {
            val sm = TtsPlaybackStateMachine(scope = backgroundScope)
            sm.onPlayRequested()
            sm.startAutoAdvanceBuffering()
            sm.onPauseRequested()
            assertEquals(TtsPlaybackStateMachine.State.PausedDuringAutoAdvance, sm.state)

            sm.onAutoAdvanceContentLoaded()
            assertTrue(sm.suppressNextPlay)

            // User stops TTS (e.g. switches chapter manually), then plays new chapter
            sm.onStopRequested()
            assertFalse(sm.suppressNextPlay)
            assertEquals(TtsPlaybackStateMachine.State.Idle, sm.state)

            assertTrue(sm.onPlayRequested())
            assertEquals(TtsPlaybackStateMachine.State.Playing, sm.state)
        }
}
