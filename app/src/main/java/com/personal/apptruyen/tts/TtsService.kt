package com.personal.apptruyen.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.personal.apptruyen.MainActivity
import com.personal.apptruyen.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale

/**
 * Foreground Service for TTS background playback.
 * Provides notification controls and MediaSession for headphone buttons.
 */
@AndroidEntryPoint
class TtsService : Service() {

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
     * Events emitted when user presses skip at chapter boundary.
     * ViewModel listens to this to trigger chapter navigation.
     */
    enum class ChapterSkipEvent { NONE, NEXT, PREV }

    private val _skipEvent = MutableStateFlow(ChapterSkipEvent.NONE)
    val skipEvent: StateFlow<ChapterSkipEvent> = _skipEvent.asStateFlow()

    fun consumeSkipEvent() {
        _skipEvent.value = ChapterSkipEvent.NONE
    }

    companion object {
        const val CHANNEL_ID = "tts_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.personal.apptruyen.tts.PLAY"
        const val ACTION_PAUSE = "com.personal.apptruyen.tts.PAUSE"
        const val ACTION_STOP = "com.personal.apptruyen.tts.STOP"
        const val ACTION_NEXT = "com.personal.apptruyen.tts.NEXT"
        const val ACTION_PREV = "com.personal.apptruyen.tts.PREV"
        val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.3f, 1.35f, 1.4f, 1.5f, 1.75f, 2.0f)
    }

    private val binder = TtsBinder()
    private var tts: TextToSpeech? = null
    private var paragraphs: List<String> = emptyList()
    private var currentIndex = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // Silent MediaPlayer: TTS audio comes from com.google.android.tts package,
    // not our app. Android routes media buttons to the package that plays audio.
    // By playing silent audio from OUR app, we claim media button focus.
    private var silentPlayer: MediaPlayer? = null

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()
    private var pendingPlayOnInit = false
    private var pendingPitch: Float? = null
    private var pendingVoice: String? = null

    inner class TtsBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
        initTts()
        // WakeLock is now acquired only when play() is called (BUG-5 fix)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // Route Bluetooth media button events through MediaSession callbacks
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> {
                stop()
                stopSelf()
            }
            ACTION_NEXT -> skipNext()
            ACTION_PREV -> skipPrev()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaButtonHandler.removeCallbacks(mediaButtonRunnable)
        stopSilentPlayer()
        abandonAudioFocus()
        mediaSession?.release()
        tts?.stop()
        tts?.shutdown()
        releaseWakeLock()
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

                    // Enumerate Vietnamese voices
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

                    _state.value =
                        _state.value.copy(
                            isInitialized = true,
                            hasVietnameseVoice = hasVi,
                            errorMessage = if (!hasVi) "Thiết bị chưa cài giọng đọc tiếng Việt." else null,
                            availableVoices = viVoices,
                            currentVoiceName = currentVoice,
                        )

                    // Apply pending pitch/voice if set before init
                    pendingPitch?.let {
                        setPitch(it)
                        pendingPitch = null
                    }
                    pendingVoice?.let {
                        setVoice(it)
                        pendingVoice = null
                    }

                    // If play was requested before init, start now
                    if (pendingPlayOnInit && paragraphs.isNotEmpty()) {
                        pendingPlayOnInit = false
                        speakCurrent()
                    }

                    tts?.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    _state.value = _state.value.copy(isPlaying = true, isPaused = false)
                                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                                    updateNotification()
                                }
                            }

                            override fun onDone(utteranceId: String?) {
                                // TTS callback runs on engine thread — post to Main for thread safety
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    if (currentIndex < paragraphs.size - 1) {
                                        currentIndex++
                                        _state.value = _state.value.copy(currentParagraph = currentIndex)
                                        speakCurrent()
                                    } else {
                                        _state.value = _state.value.copy(isPlaying = false, isPaused = false)
                                        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                                        stopSilentPlayer()
                                        abandonAudioFocus()
                                        releaseWakeLock()
                                        updateNotification()
                                    }
                                }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    _state.value =
                                        _state.value.copy(
                                            isPlaying = false,
                                            errorMessage = "Lỗi khi đọc",
                                        )
                                    updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
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
        updateMediaMetadata(chapterTitle)
    }

    /**
     * Set content for auto-advance between chapters.
     * Does NOT call stop()/stopForeground() — keeps foreground status alive
     * to avoid Android 12+ startForeground restriction from background.
     */
    fun setContentForAutoAdvance(
        text: String,
        chapterTitle: String = "",
    ) {
        tts?.stop() // Stop speech only, not the service
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
        updateMediaMetadata(chapterTitle)
    }

    fun play() {
        if (paragraphs.isEmpty()) return
        _state.value = _state.value.copy(isPlaying = true, isPaused = false)
        acquireWakeLock()
        requestAudioFocus()
        startSilentPlayer()
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Timber.w(e, "startForeground failed, playing without foreground")
        }
        speakCurrent()
    }

    fun pause() {
        tts?.stop()
        _state.value = _state.value.copy(isPlaying = false, isPaused = true)
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        // Keep silentPlayer alive to maintain audio focus + Bluetooth media buttons
        updateNotification()
    }

    fun stop() {
        tts?.stop()
        currentIndex = 0
        _state.value =
            _state.value.copy(
                isPlaying = false,
                isPaused = false,
                currentParagraph = 0,
            )
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        stopSilentPlayer()
        abandonAudioFocus()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    fun nextParagraph() {
        if (currentIndex < paragraphs.size - 1) {
            tts?.stop()
            currentIndex++
            _state.value = _state.value.copy(currentParagraph = currentIndex)
            if (_state.value.isPlaying || _state.value.isPaused) {
                speakCurrent()
            }
        }
    }

    fun prevParagraph() {
        if (currentIndex > 0) {
            tts?.stop()
            currentIndex--
            _state.value = _state.value.copy(currentParagraph = currentIndex)
            if (_state.value.isPlaying || _state.value.isPaused) {
                speakCurrent()
            }
        }
    }

    /**
     * Skip next: always emit chapter skip event.
     * Called from notification/MediaSession — always navigates chapters.
     */
    fun skipNext() {
        _skipEvent.value = ChapterSkipEvent.NEXT
    }

    /**
     * Skip prev: always emit chapter skip event.
     * Called from notification/MediaSession — always navigates chapters.
     */
    fun skipPrev() {
        _skipEvent.value = ChapterSkipEvent.PREV
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(clamped)
        _state.value = _state.value.copy(speed = clamped)
    }

    fun cycleSpeed() {
        val current = _state.value.speed
        val nextIndex = SPEED_OPTIONS.indexOfFirst { it > current + 0.001f }
        val next = if (nextIndex >= 0) SPEED_OPTIONS[nextIndex] else SPEED_OPTIONS[0]
        setSpeed(next)
    }

    fun setPitch(pitch: Float) {
        if (!_state.value.isInitialized) {
            pendingPitch = pitch
            return
        }
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(clamped)
        _state.value = _state.value.copy(pitch = clamped)
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
            if (_state.value.isPlaying) {
                speakCurrent()
            }
        }
    }

    fun seekAndPlay(index: Int) {
        if (index in paragraphs.indices) {
            tts?.stop()
            currentIndex = index
            _state.value = _state.value.copy(currentParagraph = currentIndex, isPlaying = true, isPaused = false)
            // Bug 3 fix: acquire same resources as play() — WakeLock, AudioFocus, SilentPlayer
            acquireWakeLock()
            requestAudioFocus()
            startSilentPlayer()
            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            try {
                startForeground(NOTIFICATION_ID, buildNotification())
            } catch (e: Exception) {
                Timber.w(e, "startForeground failed")
            }
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

    // ============ Silent Player (Media Button Hack) ============
    // TTS audio is played by com.google.android.tts, NOT our app process.
    // Android routes media buttons to the package that ACTUALLY plays audio.
    // By playing silent audio via OUR MediaPlayer, we claim media button focus.

    private fun startSilentPlayer() {
        if (silentPlayer != null) return // already running
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
            // MediaPlayer in error state — just release
            try {
                silentPlayer?.release()
            } catch (_: Exception) {
            }
        }
        silentPlayer = null
    }

    // ============ Audio Focus ============
    // Required for Bluetooth media button routing.
    // Android routes media buttons to the app that holds USAGE_MEDIA audio focus.
    // TTS engine uses USAGE_ASSISTANCE_ACCESSIBILITY internally, so we must
    // request our own USAGE_MEDIA focus to be recognized as active media player.

    private fun requestAudioFocus() {
        // Skip if already holding focus — avoid abandon+re-request gap
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
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            if (_state.value.isPlaying) pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            if (_state.value.isPlaying) pause()
                        }
                    }
                }.build()
        audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    // ============ MediaSession ============

    // Multi-tap detection for headphone buttons (TWS earbuds + wired headsets)
    // TWS earbuds typically send KEYCODE_MEDIA_PLAY_PAUSE for all gestures.
    // Wired headsets send KEYCODE_HEADSETHOOK.
    // We handle both: 1 tap = play/pause, 2 taps = next chapter, 3+ taps = prev chapter.
    private var mediaButtonClickCount = 0
    private val mediaButtonHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val mediaButtonRunnable =
        Runnable {
            when (mediaButtonClickCount) {
                1 -> {
                    if (_state.value.isPlaying) pause() else play()
                }
                2 -> skipNext()
                else -> skipPrev() // 3+ taps
            }
            mediaButtonClickCount = 0
        }

    @Suppress("DEPRECATION")
    private fun getKeyEvent(intent: Intent?): android.view.KeyEvent? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
        } else {
            intent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
        }

    private fun setupMediaSession() {
        val mediaButtonReceiver = ComponentName(this, MediaButtonReceiver::class.java)
        audioManager = getSystemService(AudioManager::class.java)
        mediaSession =
            MediaSessionCompat(this, "TtsSession", mediaButtonReceiver, null).apply {
                // Explicit flags required for Bluetooth media button routing
                @Suppress("DEPRECATION")
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
                )
                setCallback(
                    object : MediaSessionCompat.Callback() {
                        override fun onPlay() {
                            play()
                        }

                        override fun onPause() {
                            pause()
                        }

                        override fun onStop() {
                            stop()
                            stopSelf()
                        }

                        override fun onSkipToNext() {
                            skipNext()
                        }

                        override fun onSkipToPrevious() {
                            skipPrev()
                        }

                        override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                            val event =
                                getKeyEvent(mediaButtonEvent) ?: return super.onMediaButtonEvent(mediaButtonEvent)
                            if (event.action != android.view.KeyEvent.ACTION_DOWN) return true // ignore ACTION_UP

                            when (event.keyCode) {
                                // Both TWS earbuds and wired headsets
                                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                android.view.KeyEvent.KEYCODE_HEADSETHOOK,
                                -> {
                                    mediaButtonClickCount++
                                    mediaButtonHandler.removeCallbacks(mediaButtonRunnable)
                                    mediaButtonHandler.postDelayed(mediaButtonRunnable, 400)
                                    return true
                                }
                                // Direct skip buttons (some Bluetooth remotes)
                                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                    skipNext()
                                    return true
                                }
                                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                    skipPrev()
                                    return true
                                }
                                // Direct play/pause buttons
                                android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                    play()
                                    return true
                                }
                                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    pause()
                                    return true
                                }
                            }
                            return super.onMediaButtonEvent(mediaButtonEvent)
                        }
                    },
                )
                isActive = true
                updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            }
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState =
            PlaybackStateCompat
                .Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE,
                ).setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, _state.value.speed)
                .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun updateMediaMetadata(title: String) {
        val metadata =
            MediaMetadataCompat
                .Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title.ifBlank { "Đọc Truyện" })
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "TTS")
                .build()
        mediaSession?.setMetadata(metadata)
    }

    // ============ Notification ============

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Nghe truyện",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Thông báo khi đang nghe truyện"
                setShowBadge(false)
            }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        // Use ACTION_MAIN + CATEGORY_LAUNCHER to bring existing task to front
        // instead of creating a new activity instance
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

        val isPlaying = _state.value.isPlaying
        val title = _state.value.chapterTitle.ifBlank { "Đọc Truyện" }
        val paragraph = _state.value.currentParagraph + 1
        val total = _state.value.totalParagraphs

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Đoạn $paragraph/$total • ${String.format("%.2f", _state.value.speed)}x")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_skip_previous,
                "Trước",
                buildActionIntent(ACTION_PREV),
            ).addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (isPlaying) "Dừng" else "Phát",
                buildActionIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY),
            ).addAction(
                R.drawable.ic_skip_next,
                "Sau",
                buildActionIntent(ACTION_NEXT),
            ).setStyle(
                androidx.media.app.NotificationCompat
                    .MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            ).build()
    }

    private fun buildActionIntent(action: String): PendingIntent {
        val intent =
            Intent(this, TtsService::class.java).apply {
                this.action = action
            }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun updateNotification() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
            // Ignore if notification update fails
        }
    }

    // ============ WakeLock ============

    private fun acquireWakeLock() {
        // Release existing WakeLock first to prevent orphan locks on repeated play()
        releaseWakeLock()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
            pm
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AppTruyen::TtsWakeLock",
                ).apply {
                    acquire(60 * 60 * 1000L) // 1 hour max
                }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
