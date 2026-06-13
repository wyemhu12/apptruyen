package com.personal.apptruyen.ui.detail

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.personal.apptruyen.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P1: Tách notification logic ra khỏi ViewModel.
 * ViewModel không nên giữ Context trực tiếp — class này nhận @ApplicationContext thay thế.
 */
@Singleton
class DownloadNotificationHelper
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val notificationManager: NotificationManager,
    ) {
        companion object {
            private const val CHANNEL_ID = "download_complete"
        }

        init {
            createNotificationChannel()
        }

        private fun createNotificationChannel() {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Tải chương hoàn tất",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Thông báo khi tải chương truyện hoàn thành"
                }
            notificationManager.createNotificationChannel(channel)
        }

        fun sendDownloadCompleteNotification(
            storyId: String,
            storyTitle: String,
            downloaded: Int,
            errors: Int,
            cancelled: Boolean = false,
        ) {
            val text =
                when {
                    cancelled -> "Đã hủy tải ($downloaded chương đã tải)"
                    errors > 0 -> "Đã tải $downloaded chương ($errors lỗi)"
                    else -> "Đã tải $downloaded chương thành công"
                }

            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(storyTitle)
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

            notificationManager.notify(storyId.hashCode(), notification)
        }
    }
