package com.personal.apptruyen.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.personal.apptruyen.MainActivity
import com.personal.apptruyen.R
import com.personal.apptruyen.data.repository.DownloadProgress
import com.personal.apptruyen.data.repository.IDownloadRepository
import com.personal.apptruyen.data.repository.IStoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground Service cho tải truyện.
 * Chạy song song với TtsService (khác NOTIFICATION_ID, khác channel, khác foregroundServiceType).
 *
 * TtsService dùng:
 *   - NOTIFICATION_ID = 1001
 *   - CHANNEL_ID = "tts_playback_channel"
 *   - foregroundServiceType = mediaPlayback
 *
 * DownloadService dùng:
 *   - NOTIFICATION_ID = 3001
 *   - CHANNEL_ID = "download_progress_channel"
 *   - foregroundServiceType = dataSync
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadRepository: IDownloadRepository

    @Inject
    lateinit var storyRepository: IStoryRepository

    companion object {
        const val CHANNEL_ID = "download_progress_channel"
        const val NOTIFICATION_ID = 3001
        const val COMPLETE_CHANNEL_ID = "download_complete"

        const val ACTION_START = "com.personal.apptruyen.download.START"
        const val ACTION_CANCEL = "com.personal.apptruyen.download.CANCEL"

        const val EXTRA_STORY_ID = "story_id"
        const val EXTRA_STORY_TITLE = "story_title"
        const val EXTRA_CHAPTER_NUMBERS = "chapter_numbers"

        // Progress được shared qua static StateFlow — ViewModel observe từ bên ngoài
        private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
        val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

        fun startDownload(
            context: Context,
            storyId: String,
            storyTitle: String,
            chapterNumbers: IntArray,
        ) {
            val intent =
                Intent(context, DownloadService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_STORY_ID, storyId)
                    putExtra(EXTRA_STORY_TITLE, storyTitle)
                    putExtra(EXTRA_CHAPTER_NUMBERS, chapterNumbers)
                }
            context.startForegroundService(intent)
        }

        fun cancelDownload(context: Context) {
            val intent =
                Intent(context, DownloadService::class.java).apply {
                    action = ACTION_CANCEL
                }
            context.startService(intent)
        }
    }

    sealed class DownloadState {
        data object Idle : DownloadState()

        data class Downloading(
            val storyId: String,
            val storyTitle: String,
            val progress: DownloadProgress,
        ) : DownloadState()

        data class Completed(
            val storyId: String,
            val storyTitle: String,
            val downloaded: Int,
            val errors: Int,
        ) : DownloadState()

        data class Cancelled(
            val storyId: String,
            val storyTitle: String,
            val downloaded: Int,
        ) : DownloadState()
    }

    private var downloadJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                val storyId = intent.getStringExtra(EXTRA_STORY_ID) ?: return START_NOT_STICKY
                val storyTitle = intent.getStringExtra(EXTRA_STORY_TITLE) ?: "Truyện"
                val chapterNumbers = intent.getIntArrayExtra(EXTRA_CHAPTER_NUMBERS) ?: return START_NOT_STICKY

                startDownload(storyId, storyTitle, chapterNumbers)
            }
            ACTION_CANCEL -> {
                cancelCurrentDownload()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
    }

    private fun startDownload(
        storyId: String,
        storyTitle: String,
        chapterNumbers: IntArray,
    ) {
        // Nếu đang tải, cancel cái cũ trước
        downloadJob?.cancel()

        // Start foreground NGAY để tránh crash trên Android 12+
        acquireWakeLock()
        try {
            startForeground(NOTIFICATION_ID, buildProgressNotification(storyTitle, 0, chapterNumbers.size, 0))
        } catch (e: Exception) {
            Timber.w(e, "startForeground failed")
        }

        downloadJob =
            serviceScope.launch {
                try {
                    storyRepository.addToLibrary(storyId)

                    // Snapshot đầy đủ metadata (title, author, genres, description, cover, status)
                    // vào Room DB — đảm bảo info truyện vẫn đầy đủ khi nguồn sập
                    downloadRepository.snapshotStoryMetadata(storyId)

                    // Query chapters từ Room bằng chapter numbers
                    val allChapters = downloadRepository.getChaptersByNumbers(storyId, chapterNumbers.toList())
                    if (allChapters.isEmpty()) {
                        _downloadState.value = DownloadState.Completed(storyId, storyTitle, 0, 0)
                        stopSelfCleanly()
                        return@launch
                    }

                    var lastProgress: DownloadProgress? = null
                    downloadRepository.downloadChapters(storyId, allChapters).collect { progress ->
                        lastProgress = progress
                        _downloadState.value = DownloadState.Downloading(storyId, storyTitle, progress)

                        // Update notification
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(
                            NOTIFICATION_ID,
                            buildProgressNotification(
                                storyTitle,
                                progress.current,
                                progress.total,
                                progress.errors,
                            ),
                        )
                    }

                    // Hoàn thành
                    val downloaded = lastProgress?.current ?: 0
                    val errors = lastProgress?.errors ?: 0
                    _downloadState.value = DownloadState.Completed(storyId, storyTitle, downloaded, errors)

                    // Notification hoàn thành
                    sendCompletionNotification(storyTitle, downloaded, errors)
                } catch (e: CancellationException) {
                    // User hủy — đã xử lý trong cancelCurrentDownload
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Download failed")
                    _downloadState.value = DownloadState.Completed(storyId, storyTitle, 0, 1)
                    sendCompletionNotification(storyTitle, 0, 1)
                } finally {
                    stopSelfCleanly()
                }
            }
    }

    private fun cancelCurrentDownload() {
        val currentState = _downloadState.value
        downloadJob?.cancel()
        downloadJob = null

        if (currentState is DownloadState.Downloading) {
            val downloaded = currentState.progress.current
            _downloadState.value =
                DownloadState.Cancelled(
                    currentState.storyId,
                    currentState.storyTitle,
                    downloaded,
                )
            sendCancellationNotification(currentState.storyTitle, downloaded)
        }

        stopSelfCleanly()
    }

    private fun stopSelfCleanly() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        // Reset state after short delay cho ViewModel đọc kịp result
        serviceScope.launch {
            delay(3000)
            if (_downloadState.value !is DownloadState.Downloading) {
                _downloadState.value = DownloadState.Idle
            }
        }
    }

    // ============ Notification ============

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        // Progress channel (ongoing, low priority)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Đang tải truyện", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Hiện tiến độ tải truyện"
                setShowBadge(false)
            },
        )

        // Completion channel (auto-dismiss, default priority)
        nm.createNotificationChannel(
            NotificationChannel(
                COMPLETE_CHANNEL_ID,
                "Tải truyện hoàn tất",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Thông báo khi tải truyện xong"
            },
        )
    }

    private fun buildProgressNotification(
        title: String,
        current: Int,
        total: Int,
        errors: Int,
    ): Notification {
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

        val cancelIntent =
            PendingIntent.getService(
                this,
                ACTION_CANCEL.hashCode(),
                Intent(this, DownloadService::class.java).apply { action = ACTION_CANCEL },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val text = if (errors > 0) "Đang tải $current/$total ($errors lỗi)" else "Đang tải $current/$total"

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, current, false)
            .addAction(R.drawable.ic_notification, "Hủy", cancelIntent)
            .build()
    }

    private fun sendCompletionNotification(
        storyTitle: String,
        downloaded: Int,
        errors: Int,
    ) {
        val text = if (errors > 0) "Đã tải $downloaded chương ($errors lỗi)" else "Đã tải $downloaded chương thành công"
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            storyTitle.hashCode(),
            NotificationCompat
                .Builder(this, COMPLETE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(storyTitle)
                .setContentText(text)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun sendCancellationNotification(
        storyTitle: String,
        downloaded: Int,
    ) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(
            storyTitle.hashCode(),
            NotificationCompat
                .Builder(this, COMPLETE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(storyTitle)
                .setContentText("Đã hủy tải ($downloaded chương đã tải)")
                .setAutoCancel(true)
                .build(),
        )
    }

    // ============ WakeLock ============

    private fun acquireWakeLock() {
        releaseWakeLock()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
            pm
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AppTruyen::DownloadWakeLock",
                ).apply {
                    acquire(2 * 60 * 60 * 1000L) // 2 hours max
                }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}
