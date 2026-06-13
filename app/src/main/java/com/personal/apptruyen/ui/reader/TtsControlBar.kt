package com.personal.apptruyen.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.apptruyen.tts.TtsService
import com.personal.apptruyen.ui.theme.*

@Composable
internal fun TtsControlBar(
    ttsState: TtsService.TtsState,
    readerTheme: ReaderViewModel.ReaderTheme,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onCycleSpeed: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onShowChapterList: () -> Unit,
) {
    var showSpeedSlider by remember { mutableStateOf(false) }
    var showPitchSlider by remember { mutableStateOf(false) }

    val ttsColors = rememberReaderThemeColors(readerTheme)
    val containerColor = ttsColors.panelBackground
    val contentColor = ttsColors.panelContentColor

    Surface(
        tonalElevation = 8.dp,
        color = containerColor.copy(alpha = 0.85f), // Translucent for glass effect
        contentColor = contentColor,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Progress bar
            if (ttsState.totalParagraphs > 0) {
                val progress by animateFloatAsState(
                    targetValue = (ttsState.currentParagraph + 1f) / ttsState.totalParagraphs,
                    animationSpec = tween(300),
                    label = "ttsProgress",
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Đoạn ${ttsState.currentParagraph + 1} / ${ttsState.totalParagraphs}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = contentColor.copy(alpha = 0.7f),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Controls row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Previous chapter
                IconButton(onClick = onPrev) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Chương trước",
                        modifier = Modifier.size(26.dp),
                    )
                }

                // Play / Pause — Prominent
                FilledIconButton(
                    onClick = { if (ttsState.isPlaying) onPause() else onPlay() },
                    shape = CircleShape,
                    modifier = Modifier.size(52.dp),
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                ) {
                    Icon(
                        if (ttsState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (ttsState.isPlaying) "Tạm dừng" else "Phát",
                        modifier = Modifier.size(28.dp),
                    )
                }

                // Stop
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Dừng",
                        modifier = Modifier.size(26.dp),
                    )
                }

                // Next chapter
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Chương sau",
                        modifier = Modifier.size(26.dp),
                    )
                }

                // Speed toggle
                val speedBadgeBg =
                    when (readerTheme) {
                        ReaderViewModel.ReaderTheme.LIGHT -> MaterialTheme.colorScheme.surfaceVariant
                        ReaderViewModel.ReaderTheme.DARK -> Color(0xFF3D3530)
                        ReaderViewModel.ReaderTheme.SEPIA -> SepiaText.copy(alpha = 0.12f)
                    }
                Surface(
                    onClick = {
                        showSpeedSlider = !showSpeedSlider
                        if (showSpeedSlider) showPitchSlider = false
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = speedBadgeBg,
                    contentColor = contentColor,
                    modifier = Modifier.height(36.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = "${String.format("%.2f", ttsState.speed)}x",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Pitch badge
                Surface(
                    onClick = {
                        showPitchSlider = !showPitchSlider
                        if (showPitchSlider) showSpeedSlider = false
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = speedBadgeBg,
                    contentColor = contentColor,
                    modifier = Modifier.height(36.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = "Cao độ",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%.1f", ttsState.pitch),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Chapter list button
                IconButton(onClick = onShowChapterList) {
                    Icon(
                        Icons.Default.FormatListNumbered,
                        contentDescription = "Danh sách chương",
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // Speed slider
            AnimatedVisibility(visible = showSpeedSlider) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = contentColor.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Tốc độ",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f),
                        )
                    }
                    Slider(
                        value = ttsState.speed,
                        onValueChange = { onSpeedChange(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 29, // 0.05 increments
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "0.5x",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f),
                        )
                        Text(
                            "2.0x",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f),
                        )
                    }
                }
            }

            // Pitch slider
            AnimatedVisibility(visible = showPitchSlider) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = contentColor.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Cao độ",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f),
                        )
                    }
                    Slider(
                        value = ttsState.pitch,
                        onValueChange = { onPitchChange(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 14, // 0.1 increments
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Trầm",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f),
                        )
                        Text(
                            "Cao",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}
