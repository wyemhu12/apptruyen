package com.personal.apptruyen.tts

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.personal.apptruyen.MainActivity
import com.personal.apptruyen.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale

/**
 * Media3 [MediaSessionService] cho phát âm thanh TTS chạy nền.
 * Tích hợp [DefaultMediaNotificationProvider], [TtsPlayer] (SimpleBasePlayer) và [TtsPlaybackStateMachine].
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class TtsService : MediaSessionService() {

    data class TtsState(
        val isPlaying: Boolean = false,
        val isPaused: Boolean = false,
        val speed: Float = 1.0f,
        val pitch: Float = 1.0f,
        val currentParagraph: Int = 0,
        val totalParagraphs: Int = 0,
        val isInitialized: Boolean = false,
        val hasVietnameseVoice: Boolean = false,
        val errorMessage: String? = null,
        val chapterTitle: String = "",
        val availableVoices: List<String> = emptyList(),
        val currentVoiceName: String = "",
    )

    /**
     * Sự kiện phát ra khi người dùng nhấn chuyển chương từ notification hoặc tai nghe.
     */
    enum class ChapterSkipEvent { NONE, NEXT, PREV }

    private val _skipEvent = MutableStateFlow(ChapterSkipEvent.NONE)
    val skipEvent: StateFlow<ChapterSkipEvent> = _skipEvent.asStateFlow()

    fun consumeSkipEvent() {
        _skipEvent.value = ChapterSkipEvent.NONE
    }

    companion object {
        val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.3f, 1.35f, 1.4f, 1.5f, 1.75f, 2.0f)
        private const val MEDIA_BUTTON_DEBOUNCE_MS = 400L
        private const val WAKE_LOCK_TIMEOUT_MS = 60 * 60 * 1000L
    }

    private val binder = TtsBinder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    internal val stateMachine =
        TtsPlaybackStateMachine(
            scope = serviceScope,
            onTimeout = { handleAutoAdvanceTimeout() },
        )

    private var tts: TextToSpeech? = null
    private var paragraphs: List<String> = emptyList()
    private var currentIndex = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSession? = null
    private lateinit var ttsPlayer: TtsPlayer
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // Silent MediaPlayer giữ định tuyến nút bấm tai nghe Bluetooth về process của ứng dụng
    private var silentPlayer: MediaPlayer? = null

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()
    private var pendingPlayOnInit = false
    private var pendingSpeed: Float? = null
    private var pendingPitch: Float? = null
    private var pendingVoice: String? = null

    inner class TtsBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent) ?: binder

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(this).apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )
        setupMediaSession()
        initTts()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaButtonHandler.removeCallbacks(mediaButtonRunnable)
        stopSilentPlayer()
        abandonAudioFocus()
        releaseWakeLock()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    // ============ TTS Engine ============

    private fun initTts() {
        tts =
            TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val viLocale =
                        Locale
                            .Builder()
                            .setLanguage("vi")
                            .setRegion("VN")
                            .build()
                    val result = tts?.setLanguage(viLocale)
                    val hasVi =
                        result != TextToSpeech.LANG_MISSING_DATA &&
                            result != TextToSpeech.LANG_NOT_SUPPORTED

                    val viVoices =
                        try {
                            tts
                                ?.voices
                                ?.filter { voice ->
                                    voice.locale.language == "vi" && !voice.isNetworkConnectionRequired
                                }?.map { it.name }
                                ?.sorted() ?: emptyList()
                        } catch (_: Exception) {
                            emptyList()
                        }

                    val currentVoice = tts?.voice?.name ?: ""
                    val initialSpeed = (pendingSpeed ?: _state.value.speed).coerceIn(0.5f, 2.0f)
                    pendingSpeed = null
                    tts?.setSpeechRate(initialSpeed)
                    val initialPitch = (pendingPitch ?: _state.value.pitch).coerceIn(0.5f, 2.0f)
                    pendingPitch = null
                    tts?.setPitch(initialPitch)

                    _state.value =
                        _state.value.copy(
                            isInitialized = true,
                            hasVietnameseVoice = hasVi,
                            errorMessage = if (!hasVi) "Thiết bị chưa cài giọng đọc tiếng Việt." else null,
                            availableVoices = viVoices,
                            currentVoiceName = currentVoice,
                            speed = initialSpeed,
                            pitch = initialPitch,
                        )

                    pendingVoice?.let {
                        setVoice(it)
                        pendingVoice = null
                    }

                    if (pendingPlayOnInit && paragraphs.isNotEmpty()) {
                        pendingPlayOnInit = false
                        speakCurrent()
                    }

                    tts?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                mainHandler.post {
                                    _state.value = _state.value.copy(isPlaying = true, isPaused = false)
                                    syncPlayerState()
                                }
                            }

                            override fun onDone(utteranceId: String?) {
                                mainHandler.post {
                                    if (!TtsPlaybackStateMachine.shouldProcessUtteranceDone(
                                            utteranceId = utteranceId,
                                            currentParagraphIndex = currentIndex,
                                            isPlaying = _state.value.isPlaying,
                                        )
                                    ) {
                                        Timber.d(
                                            "Ignoring stale TTS onDone(utteranceId=%s, currentIndex=%d, isPlaying=%b)",
                                            utteranceId,
                                            currentIndex,
                                            _state.value.isPlaying,
                                        )
                                        return@post
                                    }
                                    if (currentIndex < paragraphs.size - 1) {
                                        currentIndex++
                                        _state.value = _state.value.copy(currentParagraph = currentIndex)
                                        syncPlayerState()
                                        speakCurrent()
                                    } else {
                                        // Đọc xong đoạn cuối:
                                        // 1. Tầng App đặt isPlaying = false để ReaderViewModel kích hoạt autoNextChapter()
                                        // 2. Tầng StateMachine + TtsPlayer giữ BufferingAutoAdvance (STATE_BUFFERING, playWhenReady = true)
                                        //    và duy trì WakeLock + AudioFocus trong tối đa 10s
                                        _state.value = _state.value.copy(isPlaying = false, isPaused = false)
                                        stateMachine.startAutoAdvanceBuffering()
                                        syncPlayerState()
                                    }
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                mainHandler.post {
                                    if (!stateMachine.isActiveOrBuffering && !_state.value.isPlaying) {
                                        Timber.d("Ignoring TTS onError(%s) while not active/playing", utteranceId)
                                        return@post
                                    }
                                    Timber.e("TTS error at paragraph $currentIndex (utterance=%s)", utteranceId)
                                    stateMachine.onStopRequested()
                                    stopSilentPlayer()
                                    abandonAudioFocus()
                                    releaseWakeLock()
                                    _state.value =
                                        _state.value.copy(
                                            isPlaying = false,
                                            isPaused = false,
                                            errorMessage = "Lỗi khi đọc",
                                        )
                                    syncPlayerState()
                                }
                            }
                        },
                    )
                } else {
                    _state.value =
                        _state.value.copy(
                            isInitialized = false,
                            errorMessage = "Không thể khởi tạo TTS.",
                        )
                }
            }
    }

    private fun handleAutoAdvanceTimeout() {
        stopSilentPlayer()
        abandonAudioFocus()
        releaseWakeLock()
        syncPlayerState()
    }

    // ============ Public API ============

    fun setContent(
        text: String,
        chapterTitle: String = "",
    ) {
        stop()
        paragraphs = TtsManager.splitIntoParagraphs(text)
        currentIndex = 0
        _state.value =
            _state.value.copy(
                currentParagraph = 0,
                totalParagraphs = paragraphs.size,
                isPlaying = false,
                isPaused = false,
                chapterTitle = chapterTitle,
            )
        syncPlayerState()
    }

    /**
     * Nạp nội dung chương mới cho luồng chuyển chương tự động (auto-advance).
     * Không gọi stop() để duy trì phiên Media3 Foreground Service liền mạch.
     * Tuyệt đối không tự gọi play() tại đây; lệnh play() đến từ ReaderViewModel ngay sau đó.
     */
    fun setContentForAutoAdvance(
        text: String,
        chapterTitle: String = "",
    ) {
        tts?.stop()
        paragraphs = TtsManager.splitIntoParagraphs(text)
        currentIndex = 0
        _state.value =
            _state.value.copy(
                currentParagraph = 0,
                totalParagraphs = paragraphs.size,
                isPlaying = false,
                isPaused = false,
                chapterTitle = chapterTitle,
            )
        stateMachine.onAutoAdvanceContentLoaded()
        syncPlayerState()
    }

    fun play() {
        if (paragraphs.isEmpty()) return
        if (!stateMachine.onPlayRequested()) {
            syncPlayerState()
            return
        }
        _state.value = _state.value.copy(isPlaying = true, isPaused = false)
        acquireWakeLock()
        requestAudioFocus()
        startSilentPlayer()
        syncPlayerState()
        speakCurrent()
    }

    fun pause() {
        stateMachine.onPauseRequested()
        tts?.stop()
        _state.value = _state.value.copy(isPlaying = false, isPaused = true)
        syncPlayerState()
    }

    fun stop() {
        stateMachine.onStopRequested()
        tts?.stop()
        currentIndex = 0
        _state.value =
            _state.value.copy(
                isPlaying = false,
                isPaused = false,
                currentParagraph = 0,
            )
        stopSilentPlayer()
        abandonAudioFocus()
        releaseWakeLock()
        syncPlayerState()
    }

    fun nextParagraph() {
        if (currentIndex < paragraphs.size - 1) {
            val targetIndex = currentIndex + 1
            if (_state.value.isPlaying || _state.value.isPaused) {
                seekAndPlay(targetIndex)
            } else {
                tts?.stop()
                currentIndex = targetIndex
                _state.value = _state.value.copy(currentParagraph = currentIndex)
                syncPlayerState()
            }
        }
    }

    fun prevParagraph() {
        if (currentIndex > 0) {
            val targetIndex = currentIndex - 1
            if (_state.value.isPlaying || _state.value.isPaused) {
                seekAndPlay(targetIndex)
            } else {
                tts?.stop()
                currentIndex = targetIndex
                _state.value = _state.value.copy(currentParagraph = currentIndex)
                syncPlayerState()
            }
        }
    }

    fun skipNext() {
        _skipEvent.value = ChapterSkipEvent.NEXT
    }

    fun skipPrev() {
        _skipEvent.value = ChapterSkipEvent.PREV
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _state.value = _state.value.copy(speed = clamped)
        if (!_state.value.isInitialized) {
            pendingSpeed = clamped
            return
        }
        tts?.setSpeechRate(clamped)
        syncPlayerState()
    }

    fun cycleSpeed() {
        val current = _state.value.speed
        val nextIndex = SPEED_OPTIONS.indexOfFirst { it > current + 0.001f }
        val next = if (nextIndex >= 0) SPEED_OPTIONS[nextIndex] else SPEED_OPTIONS[0]
        setSpeed(next)
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _state.value = _state.value.copy(pitch = clamped)
        if (!_state.value.isInitialized) {
            pendingPitch = clamped
            return
        }
        tts?.setPitch(clamped)
        syncPlayerState()
    }

    fun setVoice(voiceName: String) {
        if (!_state.value.isInitialized) {
            pendingVoice = voiceName
            return
        }
        if (voiceName.isBlank()) return
        try {
            val voice = tts?.voices?.find { it.name == voiceName }
            if (voice != null) {
                tts?.voice = voice
                _state.value = _state.value.copy(currentVoiceName = voiceName)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to set voice: $voiceName")
        }
    }

    fun seekToParagraph(index: Int) {
        if (index in paragraphs.indices) {
            tts?.stop()
            currentIndex = index
            _state.value = _state.value.copy(currentParagraph = currentIndex)
            syncPlayerState()
            if (_state.value.isPlaying) {
                speakCurrent()
            }
        }
    }

    fun seekAndPlay(index: Int) {
        if (index in paragraphs.indices) {
            stateMachine.onStopRequested()
            stateMachine.onPlayRequested()
            tts?.stop()
            currentIndex = index
            _state.value = _state.value.copy(currentParagraph = currentIndex, isPlaying = true, isPaused = false)
            acquireWakeLock()
            requestAudioFocus()
            startSilentPlayer()
            syncPlayerState()
            speakCurrent()
        }
    }

    private fun speakCurrent() {
        if (!_state.value.isInitialized) {
            pendingPlayOnInit = true
            return
        }
        if (currentIndex in paragraphs.indices) {
            val processedText = TtsTextPreprocessor.preprocess(paragraphs[currentIndex])
            tts?.speak(
                processedText,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "paragraph_$currentIndex",
            )
        }
    }

    // ============ Silent Player ============

    private fun startSilentPlayer() {
        if (silentPlayer != null) return
        try {
            silentPlayer =
                MediaPlayer.create(this, R.raw.silence)?.apply {
                    isLooping = true
                    setVolume(0f, 0f)
                    start()
                }
        } catch (e: Exception) {
            Timber.w(e, "Silent player failed")
        }
    }

    private fun stopSilentPlayer() {
        try {
            silentPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {
            try {
                silentPlayer?.release()
            } catch (_: Exception) {
            }
        }
        silentPlayer = null
    }

    // ============ Audio Focus ============

    private fun requestAudioFocus() {
        if (audioFocusRequest != null) return
        audioFocusRequest =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                ).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        -> {
                            if (stateMachine.isActiveOrBuffering) pause()
                        }
                    }
                }.build()
        audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    // ============ Media3 MediaSession ============

    private var mediaButtonClickCount = 0
    private val mediaButtonHandler = Handler(Looper.getMainLooper())
    private val mediaButtonRunnable =
        Runnable {
            when (mediaButtonClickCount) {
                1 -> {
                    if (stateMachine.isActiveOrBuffering) pause() else play()
                }
                2 -> skipNext()
                else -> skipPrev()
            }
            mediaButtonClickCount = 0
        }

    @Suppress("DEPRECATION")
    private fun getKeyEvent(intent: Intent?): KeyEvent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            intent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

    private fun setupMediaSession() {
        ttsPlayer =
            TtsPlayer(
                onPlayCommand = { play() },
                onPauseCommand = { pause() },
                onStopCommand = {
                    stop()
                    stopSelf()
                },
                onSkipNextCommand = { skipNext() },
                onSkipPrevCommand = { skipPrev() },
                onSpeedPitchCommand = { speed, pitch ->
                    setSpeed(speed)
                    setPitch(pitch)
                },
            )

        val openIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        mediaSession =
            MediaSession
                .Builder(this, ttsPlayer)
                .setSessionActivity(openIntent)
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo,
                        ): MediaSession.ConnectionResult =
                            if (session.isMediaNotificationController(controller) ||
                                controller.isTrusted ||
                                controller.packageName == packageName
                            ) {
                                MediaSession.ConnectionResult
                                    .AcceptedResultBuilder(session)
                                    .setAvailablePlayerCommands(TtsPlayer.AVAILABLE_COMMANDS)
                                    .build()
                            } else {
                                MediaSession.ConnectionResult.reject()
                            }

                        override fun onMediaButtonEvent(
                            session: MediaSession,
                            controllerInfo: MediaSession.ControllerInfo,
                            intent: Intent,
                        ): Boolean = handleMediaButtonKeyEvent(intent)
                    },
                ).build()
    }

    private fun handleMediaButtonKeyEvent(mediaButtonEvent: Intent): Boolean {
        val event = getKeyEvent(mediaButtonEvent) ?: return false
        if (event.action != KeyEvent.ACTION_DOWN) return true

        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            -> {
                mediaButtonClickCount++
                mediaButtonHandler.removeCallbacks(mediaButtonRunnable)
                mediaButtonHandler.postDelayed(mediaButtonRunnable, MEDIA_BUTTON_DEBOUNCE_MS)
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                skipNext()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                skipPrev()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                play()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                pause()
                true
            }
            else -> false
        }
    }

    private fun syncPlayerState() {
        if (!::ttsPlayer.isInitialized) return
        val currentState = _state.value
        val paragraph = (currentState.currentParagraph + 1).coerceAtMost(currentState.totalParagraphs.coerceAtLeast(1))
        val total = currentState.totalParagraphs
        val subtitle =
            if (total > 0) {
                "Đoạn $paragraph/$total • ${String.format(Locale.US, "%.2f", currentState.speed)}x"
            } else {
                "TTS"
            }

        val playerState =
            when (stateMachine.state) {
                TtsPlaybackStateMachine.State.Playing -> Player.STATE_READY
                TtsPlaybackStateMachine.State.Paused,
                TtsPlaybackStateMachine.State.PausedDuringAutoAdvance,
                -> Player.STATE_READY
                TtsPlaybackStateMachine.State.BufferingAutoAdvance -> Player.STATE_BUFFERING
                TtsPlaybackStateMachine.State.Ended -> Player.STATE_ENDED
                TtsPlaybackStateMachine.State.Idle -> Player.STATE_IDLE
            }

        try {
            ttsPlayer.updatePlaybackState(
                playbackState = playerState,
                playWhenReady = stateMachine.playWhenReady,
                speed = currentState.speed,
                pitch = currentState.pitch,
                title = currentState.chapterTitle.ifBlank { "Đọc Truyện" },
                subtitle = subtitle,
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to update Media3 player state")
        }
    }

    // ============ WakeLock ============

    private fun acquireWakeLock() {
        releaseWakeLock()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
            pm
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AppTruyen::TtsWakeLock",
                ).apply {
                    acquire(WAKE_LOCK_TIMEOUT_MS)
                }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
