package com.personal.apptruyen.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.ui.theme.*

@Composable
internal fun ChapterListPanel(
    visible: Boolean,
    chapters: List<Chapter>,
    currentChapterNumber: Int,
    readerTheme: ReaderViewModel.ReaderTheme,
    onChapterClick: (Chapter) -> Unit,
    onDismiss: () -> Unit,
) {
    val chapterListState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(visible) {
        if (!visible) {
            searchQuery = ""
        }
    }

    val filteredChapters =
        remember(chapters, searchQuery) {
            val q = searchQuery.trim()
            if (q.isEmpty()) {
                chapters
            } else {
                chapters.filter { ch ->
                    ch.chapterNumber.toString().contains(q) ||
                        ch.title.contains(q, ignoreCase = true)
                }
            }
        }

    // Auto-scroll to center the current chapter when panel opens or chapter changes
    LaunchedEffect(visible, currentChapterNumber) {
        if (visible && searchQuery.isBlank() && chapters.isNotEmpty()) {
            val currentIndex = chapters.indexOfFirst { it.chapterNumber == currentChapterNumber }
            if (currentIndex >= 0) {
                // Estimate visible items and scroll so current is centered
                val offset = (currentIndex - 4).coerceAtLeast(0)
                chapterListState.scrollToItem(offset)
            }
        }
    }

    // Panel colors per theme — centralized in ReaderThemeColors
    val themeColors = rememberReaderThemeColors(readerTheme)
    val panelBg = themeColors.panelBackground
    val panelContentColor = themeColors.panelContentColor
    val highlightBg = themeColors.highlightBackground
    val highlightText = themeColors.highlightText
    val accentColor = themeColors.accentColor

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim — tap to dismiss
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
            )

            // Panel sliding from right
            AnimatedVisibility(
                visible = visible,
                enter =
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(300),
                    ),
                exit =
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(300),
                    ),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.72f),
                    color = panelBg,
                    contentColor = panelContentColor,
                    shadowElevation = 16.dp,
                ) {
                    Column(modifier = Modifier.safeDrawingPadding()) {
                        // Header
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.FormatListNumbered,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = panelContentColor,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Danh sách chương",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = panelContentColor,
                            )
                        }

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Tìm số hoặc tên chương...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = panelContentColor.copy(alpha = 0.6f),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Tìm chương",
                                    tint = panelContentColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Xóa tìm kiếm",
                                            tint = panelContentColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = panelContentColor),
                            shape = RoundedCornerShape(12.dp),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    unfocusedBorderColor = panelContentColor.copy(alpha = 0.25f),
                                    cursorColor = accentColor,
                                ),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                        )

                        HorizontalDivider(
                            color = panelContentColor.copy(alpha = 0.12f),
                            thickness = 1.dp,
                        )

                        if (filteredChapters.isEmpty()) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Không tìm thấy chương phù hợp",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = panelContentColor.copy(alpha = 0.7f),
                                )
                            }
                        } else {
                            // Chapter list
                            LazyColumn(
                                state = chapterListState,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                itemsIndexed(
                                    items = filteredChapters,
                                    key = { index, ch -> "panel_ch_${ch.chapterNumber}_$index" },
                                ) { _, chapter ->
                                    val isCurrent = chapter.chapterNumber == currentChapterNumber

                                    Surface(
                                        onClick = { onChapterClick(chapter) },
                                        color = if (isCurrent) highlightBg else Color.Transparent,
                                        contentColor = if (isCurrent) highlightText else panelContentColor,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier =
                                                Modifier
                                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (isCurrent) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.MenuBook,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                    tint = highlightText,
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                            }

                                            Text(
                                                text = chapter.title.ifBlank { "Chương ${chapter.chapterNumber}" },
                                                style =
                                                    MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    ),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
