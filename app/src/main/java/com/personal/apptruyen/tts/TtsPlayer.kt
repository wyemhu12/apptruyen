package com.personal.apptruyen.tts

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Triển khai Media3 [SimpleBasePlayer] đại diện cho TextToSpeech engine.
 * Cung cấp đầy đủ 8 commands và 1 MediaItem trong timeline để DefaultMediaNotificationProvider
 * hiển thị notification media chuẩn và điều phối sự kiện nút bấm.
 */
@UnstableApi
class TtsPlayer(
    private val onPlayCommand: () -> Unit,
    private val onPauseCommand: () -> Unit,
    private val onStopCommand: () -> Unit,
    private val onSkipNextCommand: () -> Unit,
    private val onSkipPrevCommand: () -> Unit,
    private val onSpeedPitchCommand: (Float, Float) -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentPlaybackState: Int = Player.STATE_IDLE
    private var currentPlayWhenReady: Boolean = false
    private var currentSpeed: Float = 1.0f
    private var currentPitch: Float = 1.0f
    private var currentTitle: String = "Đọc Truyện"
    private var currentSubtitle: String = "TTS"

    override fun getState(): State {
        val mediaMetadata =
            MediaMetadata
                .Builder()
                .setTitle(currentTitle.ifBlank { "Đọc Truyện" })
                .setArtist(currentSubtitle.ifBlank { "TTS" })
                .setAlbumTitle("App Truyện")
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                .build()

        val mediaItem =
            MediaItem
                .Builder()
                .setMediaId(MEDIA_ITEM_ID)
                .setMediaMetadata(mediaMetadata)
                .build()

        val itemData =
            MediaItemData
                .Builder(MEDIA_ITEM_ID)
                .setMediaItem(mediaItem)
                .setMediaMetadata(mediaMetadata)
                .build()

        return State
            .Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlayWhenReady(currentPlayWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(currentPlaybackState)
            .setPlaybackParameters(PlaybackParameters(currentSpeed, currentPitch))
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
            ).setPlaylist(listOf(itemData))
            .setCurrentMediaItemIndex(0)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) {
            onPlayCommand()
        } else {
            onPauseCommand()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        onStopCommand()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            -> onSkipNextCommand()
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            -> onSkipPrevCommand()
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        onSpeedPitchCommand(playbackParameters.speed, playbackParameters.pitch)
        return Futures.immediateVoidFuture()
    }

    fun updatePlaybackState(
        playbackState: Int,
        playWhenReady: Boolean,
        speed: Float = currentSpeed,
        pitch: Float = currentPitch,
        title: String = currentTitle,
        subtitle: String = currentSubtitle,
    ) {
        val block = {
            currentPlaybackState = playbackState
            currentPlayWhenReady = playWhenReady
            currentSpeed = speed.coerceIn(0.5f, 2.0f)
            currentPitch = pitch.coerceIn(0.5f, 2.0f)
            currentTitle = title
            currentSubtitle = subtitle
            invalidateState()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    companion object {
        private const val MEDIA_ITEM_ID = "tts_active_chapter"

        val AVAILABLE_COMMANDS: Player.Commands =
            Player.Commands
                .Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SET_SPEED_AND_PITCH,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_GET_METADATA,
                ).build()
    }
}
