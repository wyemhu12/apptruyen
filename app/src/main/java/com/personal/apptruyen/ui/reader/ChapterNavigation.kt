package com.personal.apptruyen.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.apptruyen.ui.theme.rememberReaderThemeColors

@Composable
internal fun NextChapterSection(
    hasNextChapter: Boolean,
    isLoadingNextChapter: Boolean,
    readerTheme: ReaderViewModel.ReaderTheme,
    textColor: Color,
    swipeCount: Int = 0,
) {
    val themeColors = rememberReaderThemeColors(readerTheme)
    val accentColor = themeColors.accentColor

    val dividerColor = textColor.copy(alpha = 0.15f)
    val remaining = (2 - swipeCount).coerceAtLeast(0)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalDivider(
            color = dividerColor,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        when {
            isLoadingNextChapter -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = accentColor,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Đang tải chương tiếp theo...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.6f),
                )
            }
            hasNextChapter -> {
                Icon(
                    Icons.Default.KeyboardDoubleArrowUp,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (remaining <= 1) {
                        "Kéo lên 1 lần nữa để sang chương tiếp"
                    } else {
                        "Kéo lên $remaining lần để sang chương tiếp"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium,
                )
            }
            else -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = accentColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "Đã hết truyện",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
internal fun PrevChapterSection(
    hasPrevChapter: Boolean,
    isLoadingPrevChapter: Boolean,
    readerTheme: ReaderViewModel.ReaderTheme,
    textColor: Color,
    swipeCount: Int = 0,
) {
    if (!hasPrevChapter && !isLoadingPrevChapter) return

    val themeColors = rememberReaderThemeColors(readerTheme)
    val accentColor = themeColors.accentColor

    val remaining = (2 - swipeCount).coerceAtLeast(0)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            isLoadingPrevChapter -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = accentColor,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Đang tải chương trước...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.6f),
                )
            }
            hasPrevChapter -> {
                Icon(
                    Icons.Default.KeyboardDoubleArrowDown,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (remaining <= 1) {
                        "Kéo xuống 1 lần nữa để về chương trước"
                    } else {
                        "Kéo xuống $remaining lần để về chương trước"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(
            color = textColor.copy(alpha = 0.15f),
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}
