package com.personal.apptruyen.ui.downloads

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.personal.apptruyen.ui.components.AnimatedEmptyState
import com.personal.apptruyen.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onStoryClick: (String, String) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val allDownloads by viewModel.stories.collectAsState()
    val filteredDownloads by viewModel.filteredStories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var storyToDelete by remember { mutableStateOf<String?>(null) }
    var isSearchVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    // Auto-focus search field khi mở
    LaunchedEffect(isSearchVisible) {
        if (isSearchVisible) focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Đã Tải",
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            if (allDownloads.isNotEmpty()) {
                                val subtitle =
                                    if (searchQuery.isNotBlank()) {
                                        "${filteredDownloads.size}/${allDownloads.size} kết quả 🔍"
                                    } else {
                                        "${allDownloads.size} truyện 📥"
                                    }
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        if (allDownloads.isNotEmpty()) {
                            IconButton(onClick = {
                                isSearchVisible = !isSearchVisible
                                if (!isSearchVisible) {
                                    viewModel.updateSearchQuery("")
                                    keyboardController?.hide()
                                }
                            }) {
                                Icon(
                                    if (isSearchVisible) {
                                        Icons.Default.SearchOff
                                    } else {
                                        Icons.Default.Search
                                    },
                                    contentDescription = "Tìm kiếm",
                                )
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                )

                // Animated search bar
                AnimatedVisibility(
                    visible = isSearchVisible,
                    enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .focusRequester(focusRequester),
                        placeholder = { Text("Tìm theo tên truyện...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
            }
        },
    ) { padding ->
        if (allDownloads.isEmpty()) {
            AnimatedEmptyState(
                icon = Icons.Default.CloudDownload,
                message = "Chưa tải truyện nào.\nTải truyện để đọc offline!",
                modifier = Modifier.padding(padding),
            )
        } else if (filteredDownloads.isEmpty() && searchQuery.isNotBlank()) {
            AnimatedEmptyState(
                icon = Icons.Default.SearchOff,
                message = "Không tìm thấy truyện nào\nphù hợp với \"$searchQuery\"",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                // Sử dụng filteredDownloads thay vì allDownloads
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Storage summary card
                item {
                    val totalSize = filteredDownloads.sumOf { it.estimatedSizeBytes }
                    val totalChapters = filteredDownloads.sumOf { it.downloadedCount }
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            StatItem(
                                icon = Icons.Default.Book,
                                value = "${filteredDownloads.size}",
                                label = "Truyện",
                            )
                            StatItem(
                                icon = Icons.Default.Article,
                                value = "$totalChapters",
                                label = "Chương",
                            )
                            StatItem(
                                icon = Icons.Default.Storage,
                                value = formatSize(totalSize),
                                label = "Dung lượng",
                            )
                        }
                    }
                }

                // Downloaded stories
                itemsIndexed(filteredDownloads, key = { _, it -> it.story.id }) { index, info ->
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { visible = true }

                    AnimatedVisibility(
                        visible = visible,
                        enter =
                            fadeIn(tween(300, delayMillis = minOf(index * 50, 500))) +
                                slideInVertically(
                                    initialOffsetY = { 50 },
                                    animationSpec = tween(350, delayMillis = minOf(index * 50, 500)),
                                ),
                    ) {
                        Card(
                            onClick = { onStoryClick(info.story.id, info.story.url) },
                            shape = RoundedCornerShape(16.dp),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                // Cover image
                                Box(
                                    modifier =
                                        Modifier
                                            .size(64.dp, 90.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                ) {
                                    if (info.story.coverImageUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = info.story.coverImageUrl,
                                            contentDescription = info.story.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            color = GradientAmberStart,
                                            shape = RoundedCornerShape(12.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.AutoStories,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(28.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .heightIn(min = 90.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = info.story.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = "${info.downloadedCount} chương đã tải",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Text(
                                            text = formatSize(info.estimatedSizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { storyToDelete = info.story.id },
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Xóa",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // Delete confirmation dialog
    if (storyToDelete != null) {
        AlertDialog(
            onDismissRequest = { storyToDelete = null },
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text("Xóa truyện?", style = MaterialTheme.typography.headlineSmall)
            },
            text = {
                Text(
                    "Bạn muốn xóa nội dung đã tải hay xóa hoàn toàn?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        storyToDelete?.let { viewModel.deleteEntireStory(it) }
                        storyToDelete = null
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Xóa toàn bộ")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { storyToDelete = null }) {
                        Text("Hủy")
                    }
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            storyToDelete?.let { viewModel.deleteDownloads(it) }
                            storyToDelete = null
                        },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Chỉ xóa tải")
                    }
                }
            },
        )
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}

private fun formatSize(bytes: Long): String =
    when {
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.0f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
