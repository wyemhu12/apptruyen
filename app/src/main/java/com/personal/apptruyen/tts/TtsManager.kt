package com.personal.apptruyen.tts

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTS Manager - Proxy that delegates to TtsService for background playback.
 * Maintains backward-compatible API for UI consumers.
 * Implements Closeable so resources are released if shutdown() is forgotten.
 */
@Singleton
class TtsManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @com.personal.apptruyen.di.ApplicationScope private val scope: CoroutineScope,
    ) : Closeable {

        private val _state = MutableStateFlow(TtsService.TtsState())
        val state: StateFlow<TtsService.TtsState> = _state.asStateFlow()

        private val _skipEvent = MutableStateFlow(TtsService.ChapterSkipEvent.NONE)
        val skipEvent: StateFlow<TtsService.ChapterSkipEvent> = _skipEvent.asStateFlow()

        fun consumeSkipEvent() {
            service?.consumeSkipEvent()
            _skipEvent.value = TtsService.ChapterSkipEvent.NONE
        }

        private var service: TtsService? = null
        private var isBound = false
        private var isBinding = false
        private var pendingContent: Pair<String, String>? = null // text, chapterTitle
        private var pendingAutoAdvanceContent: Pair<String, String>? = null
        private var pendingSpeed: Float? = null
        private var pendingPitch: Float? = null
        private var pendingVoice: String? = null
        private var pendingSeekParagraphIndex: Int? = null
        private var pendingPlay = false
        private var pendingSeekAndPlayIndex: Int? = null
        private var stateCollectJob: Job? = null
        private var skipEventCollectJob: Job? = null

        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    binder: IBinder?,
                ) {
                    val ttsBinder =
                        binder as? TtsService.TtsBinder ?: run {
                            Timber.w("Received non-TtsBinder onServiceConnected: %s", binder)
                            return
                        }
                    val svc = ttsBinder.getService()
                    service = svc
                    isBound = true
                    isBinding = false

                    // P2: Cancel previous collectors to avoid duplicate emissions on reconnect
                    stateCollectJob?.cancel()
                    skipEventCollectJob?.cancel()

                    // Forward service state directly (unified TtsState)
                    stateCollectJob =
                        scope.launch {
                            svc.state.collect { serviceState ->
                                _state.value = serviceState
                            }
                        }

                    // Collect chapter skip events from service
                    skipEventCollectJob =
                        scope.launch {
                            svc.skipEvent.collect { event ->
                                _skipEvent.value = event
                            }
                        }

                    // Apply pending settings before content & playback
                    pendingSpeed?.let { speed ->
                        svc.setSpeed(speed)
                        pendingSpeed = null
                    }
                    pendingPitch?.let { pitch ->
                        svc.setPitch(pitch)
                        pendingPitch = null
                    }
                    pendingVoice?.let { voice ->
                        svc.setVoice(voice)
                        pendingVoice = null
                    }

                    // Apply pending content
                    pendingContent?.let { (text, title) ->
                        svc.setContent(text, title)
                        pendingContent = null
                    }
                    pendingAutoAdvanceContent?.let { (text, title) ->
                        svc.setContentForAutoAdvance(text, title)
                        pendingAutoAdvanceContent = null
                    }
                    pendingSeekParagraphIndex?.let { idx ->
                        svc.seekToParagraph(idx)
                        pendingSeekParagraphIndex = null
                    }

                    // Apply pending playback commands
                    val seekPlayIdx = pendingSeekAndPlayIndex
                    if (seekPlayIdx != null) {
                        pendingSeekAndPlayIndex = null
                        pendingPlay = false
                        svc.seekAndPlay(seekPlayIdx)
                    } else if (pendingPlay) {
                        pendingPlay = false
                        svc.play()
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    stateCollectJob?.cancel()
                    skipEventCollectJob?.cancel()
                    service = null
                    isBound = false
                    isBinding = false
                }
            }

        private fun ensureServiceStartedAndBound() {
            if (isBound && service != null) return
            if (isBinding) return
            isBinding = true
            val intent = Intent(context, TtsService::class.java)
            context.startService(intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        fun setContent(
            text: String,
            chapterTitle: String = "",
        ) {
            if (isBound && service != null) {
                service?.setContent(text, chapterTitle)
            } else {
                pendingAutoAdvanceContent = null
                pendingContent = text to chapterTitle
            }
        }

        fun setContentForAutoAdvance(
            text: String,
            chapterTitle: String = "",
        ) {
            if (isBound && service != null) {
                service?.setContentForAutoAdvance(text, chapterTitle)
            } else {
                pendingContent = null
                pendingAutoAdvanceContent = text to chapterTitle
                ensureServiceStartedAndBound()
            }
        }

        fun play() {
            val svc = service
            if (isBound && svc != null) {
                svc.play()
            } else {
                pendingPlay = true
                ensureServiceStartedAndBound()
            }
        }

        fun pause() {
            pendingPlay = false
            pendingSeekAndPlayIndex = null
            service?.pause()
        }

        fun stop() {
            pendingPlay = false
            pendingSeekAndPlayIndex = null
            service?.stop()
        }

        fun nextParagraph() {
            service?.nextParagraph()
        }

        fun prevParagraph() {
            service?.prevParagraph()
        }

        fun setSpeed(speed: Float) {
            val clamped = speed.coerceIn(0.5f, 2.0f)
            _state.value = _state.value.copy(speed = clamped)
            val svc = service
            if (isBound && svc != null) {
                svc.setSpeed(clamped)
            } else {
                pendingSpeed = clamped
            }
        }

        fun cycleSpeed() {
            val svc = service
            if (isBound && svc != null) {
                svc.cycleSpeed()
            } else {
                val current = _state.value.speed
                val nextIndex = SPEED_OPTIONS.indexOfFirst { it > current + 0.001f }
                val next = if (nextIndex >= 0) SPEED_OPTIONS[nextIndex] else SPEED_OPTIONS[0]
                setSpeed(next)
            }
        }

        fun seekToParagraph(index: Int) {
            val svc = service
            if (isBound && svc != null) {
                svc.seekToParagraph(index)
            } else {
                pendingSeekParagraphIndex = index
            }
        }

        fun seekAndPlay(index: Int) {
            val svc = service
            if (isBound && svc != null) {
                svc.seekAndPlay(index)
            } else {
                pendingSeekAndPlayIndex = index
                ensureServiceStartedAndBound()
            }
        }

        fun skipNext() {
            service?.skipNext()
        }

        fun skipPrev() {
            service?.skipPrev()
        }

        fun setPitch(pitch: Float) {
            val clamped = pitch.coerceIn(0.5f, 2.0f)
            _state.value = _state.value.copy(pitch = clamped)
            val svc = service
            if (isBound && svc != null) {
                svc.setPitch(clamped)
            } else {
                pendingPitch = clamped
            }
        }

        fun setVoice(voiceName: String) {
            val svc = service
            if (isBound && svc != null) {
                svc.setVoice(voiceName)
            } else {
                pendingVoice = voiceName
            }
        }

        fun shutdown() {
            stateCollectJob?.cancel()
            skipEventCollectJob?.cancel()
            pendingPlay = false
            pendingSeekAndPlayIndex = null
            service?.stop()
            if (isBound || isBinding) {
                try {
                    context.unbindService(connection)
                } catch (_: Exception) {
                }
                isBound = false
                isBinding = false
            }
            service = null
        }

        override fun close() = shutdown()

        companion object {
            val SPEED_OPTIONS = TtsService.SPEED_OPTIONS

            // Cached regex — splitIntoParagraphs called frequently
            private val PARAGRAPH_SPLIT_REGEX = Regex("\n\\s*\n")
            private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?。！？])\\s+")

            fun splitIntoParagraphs(
                text: String,
                maxLength: Int = 500,
            ): List<String> {
                if (text.isBlank()) return emptyList()
                val rawParagraphs =
                    text
                        .split(PARAGRAPH_SPLIT_REGEX)
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                val result = mutableListOf<String>()
                for (p in rawParagraphs) {
                    if (p.length <= maxLength) {
                        result.add(p)
                    } else {
                        val sentences = p.split(SENTENCE_SPLIT_REGEX)
                        var current = StringBuilder()
                        for (s in sentences) {
                            if (current.length + s.length > maxLength && current.isNotEmpty()) {
                                result.add(current.toString().trim())
                                current = StringBuilder()
                            }
                            current.append(s).append(" ")
                        }
                        if (current.isNotBlank()) {
                            result.add(current.toString().trim())
                        }
                    }
                }
                return result.ifEmpty { listOf(text) }
            }
        }
    }
