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
        private var pendingContent: Pair<String, String>? = null // text, chapterTitle
        private var pendingSpeed: Float? = null
        private var pendingPitch: Float? = null
        private var pendingVoice: String? = null
        private var stateCollectJob: Job? = null
        private var skipEventCollectJob: Job? = null

        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    binder: IBinder?,
                ) {
                    val ttsBinder = binder as TtsService.TtsBinder
                    service = ttsBinder.getService()
                    isBound = true

                    // P2: Cancel previous collectors to avoid duplicate emissions on reconnect
                    stateCollectJob?.cancel()
                    skipEventCollectJob?.cancel()

                    // Forward service state directly (unified TtsState)
                    stateCollectJob =
                        scope.launch {
                            val svc = service ?: return@launch
                            svc.state.collect { serviceState ->
                                _state.value = serviceState
                            }
                        }

                    // Collect chapter skip events from service
                    skipEventCollectJob =
                        scope.launch {
                            val svc = service ?: return@launch
                            svc.skipEvent.collect { event ->
                                _skipEvent.value = event
                            }
                        }

                    // Apply pending settings
                    pendingSpeed?.let { speed ->
                        service?.setSpeed(speed)
                        pendingSpeed = null
                    }
                    pendingPitch?.let { pitch ->
                        service?.setPitch(pitch)
                        pendingPitch = null
                    }
                    pendingVoice?.let { voice ->
                        service?.setVoice(voice)
                        pendingVoice = null
                    }

                    // If there was pending content, set it now
                    pendingContent?.let { (text, title) ->
                        service?.setContent(text, title)
                        pendingContent = null
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    stateCollectJob?.cancel()
                    skipEventCollectJob?.cancel()
                    service = null
                    isBound = false
                }
            }

        init {
            bindService()
        }

        private fun bindService() {
            if (isBound) return // Prevent double-bind
            val intent = Intent(context, TtsService::class.java)
            context.startService(intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        fun setContent(
            text: String,
            chapterTitle: String = "",
        ) {
            if (isBound) {
                service?.setContent(text, chapterTitle)
            } else {
                pendingContent = text to chapterTitle
            }
        }

        fun setContentForAutoAdvance(
            text: String,
            chapterTitle: String = "",
        ) {
            if (isBound) {
                service?.setContentForAutoAdvance(text, chapterTitle)
            } else {
                pendingContent = text to chapterTitle
            }
        }

        fun play() {
            service?.play()
        }

        fun pause() {
            service?.pause()
        }

        fun stop() {
            service?.stop()
        }

        fun nextParagraph() {
            service?.nextParagraph()
        }

        fun prevParagraph() {
            service?.prevParagraph()
        }

        fun setSpeed(speed: Float) {
            if (isBound) {
                service?.setSpeed(speed)
            } else {
                pendingSpeed = speed
            }
        }

        fun cycleSpeed() {
            service?.cycleSpeed()
        }

        fun seekToParagraph(index: Int) {
            service?.seekToParagraph(index)
        }

        fun seekAndPlay(index: Int) {
            service?.seekAndPlay(index)
        }

        fun skipNext() {
            service?.skipNext()
        }

        fun skipPrev() {
            service?.skipPrev()
        }

        fun setPitch(pitch: Float) {
            if (isBound) {
                service?.setPitch(pitch)
            } else {
                pendingPitch = pitch
            }
        }

        fun setVoice(voiceName: String) {
            if (isBound) {
                service?.setVoice(voiceName)
            } else {
                pendingVoice = voiceName
            }
        }

        fun shutdown() {
            stateCollectJob?.cancel()
            skipEventCollectJob?.cancel()
            service?.stop()
            if (isBound) {
                try {
                    context.unbindService(connection)
                } catch (_: Exception) {
                }
                isBound = false
            }
            service = null
        }

        override fun close() = shutdown()

        companion object {
            val SPEED_OPTIONS = TtsService.SPEED_OPTIONS

            // Cached regex — splitIntoParagraphs called frequently
            private val PARAGRAPH_SPLIT_REGEX = Regex("\n\\s*\n|\n")
            private val SENTENCE_SPLIT_REGEX = Regex("(?<=[.!?。！？])\\s+")

            fun splitIntoParagraphs(
                text: String,
                maxLength: Int = 500,
            ): List<String> {
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
