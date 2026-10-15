package com.personal.apptruyen.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * State machine thuần Kotlin quản lý trạng thái phát TTS, chuyển chương tự động (auto-advance),
 * bộ đếm timeout 10 giây duy nhất và chống race condition khi tạm dừng lúc đang tải chương mới.
 *
 * Độc lập hoàn toàn với Android Looper/Context để kiểm thử 100% trên JVM JUnit 5 với TestScope.
 */
class TtsPlaybackStateMachine(
    private val scope: CoroutineScope,
    private val autoAdvanceTimeoutMs: Long = DEFAULT_AUTO_ADVANCE_TIMEOUT_MS,
    private val onTimeout: () -> Unit = {},
) {
    enum class State {
        Idle,
        Playing,
        Paused,
        BufferingAutoAdvance,
        PausedDuringAutoAdvance,
        Ended,
    }

    var state: State = State.Idle
        private set

    var suppressNextPlay: Boolean = false
        private set

    private var timeoutJob: Job? = null

    /**
     * Trả về true khi TTS đang phát hoặc đang giữ phiên chờ tải chương kế tiếp.
     * Dùng cho nút 1 chạm tai nghe và listener mất AudioFocus để chuyển đúng vào PausedDuringAutoAdvance.
     */
    val isActiveOrBuffering: Boolean
        get() = state == State.Playing || state == State.BufferingAutoAdvance

    /**
     * Giá trị playWhenReady báo cáo cho Media3 SimpleBasePlayer.
     */
    val playWhenReady: Boolean
        get() = state == State.Playing || state == State.BufferingAutoAdvance

    /**
     * Kích hoạt trạng thái chờ nạp chương mới khi đọc hết đoạn cuối cùng của chương.
     * Bắt đầu bộ đếm timeout 10s duy nhất.
     */
    fun startAutoAdvanceBuffering() {
        cancelTimeout()
        suppressNextPlay = false
        state = State.BufferingAutoAdvance
        timeoutJob =
            scope.launch {
                delay(autoAdvanceTimeoutMs)
                if (state == State.BufferingAutoAdvance) {
                    state = State.Ended
                    onTimeout()
                }
            }
    }

    /**
     * Được gọi từ TtsService.setContentForAutoAdvance() khi nội dung chương mới vừa nạp xong.
     * Tuyệt đối không tự kích hoạt phát tại đây; việc phát do lời gọi play() kế tiếp từ ReaderViewModel quyết định.
     */
    fun onAutoAdvanceContentLoaded() {
        cancelTimeout()
        when (state) {
            State.PausedDuringAutoAdvance -> {
                state = State.Paused
                suppressNextPlay = true
            }
            State.BufferingAutoAdvance -> {
                suppressNextPlay = false
            }
            State.Paused,
            State.Idle,
            State.Playing,
            -> {
                suppressNextPlay = false
            }
            State.Ended -> {
                state = State.Idle
                suppressNextPlay = false
            }
        }
    }

    /**
     * Được gọi ở ngay đầu TtsService.play().
     * @return true nếu được phép phát; false nếu lời gọi play() này bị chặn một lần do user đã pause lúc buffering.
     */
    fun onPlayRequested(): Boolean {
        cancelTimeout()
        if (suppressNextPlay) {
            suppressNextPlay = false
            state = State.Paused
            return false
        }
        state = State.Playing
        return true
    }

    /**
     * Được gọi khi user bấm Pause (UI/Notification), 1 chạm tai nghe khi isActiveOrBuffering,
     * hoặc mất AudioFocus (cuộc gọi đến).
     */
    fun onPauseRequested() {
        cancelTimeout()
        suppressNextPlay = false
        state =
            if (state == State.BufferingAutoAdvance) {
                State.PausedDuringAutoAdvance
            } else {
                State.Paused
            }
    }

    /**
     * Được gọi khi stop() hoặc khi nạp chương mới thủ công qua setContent().
     */
    fun onStopRequested() {
        cancelTimeout()
        suppressNextPlay = false
        state = State.Idle
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    companion object {
        const val DEFAULT_AUTO_ADVANCE_TIMEOUT_MS = 10_000L
    }
}
