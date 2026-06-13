package com.personal.apptruyen.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.apptruyen.data.model.ReadingProgress
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.ui.components.AnimatedEmptyState
import com.personal.apptruyen.ui.components.StoryCardPremium
import com.personal.apptruyen.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onStoryClick: (String, String) -> Unit, // storyId, storyUrl
    onDownloadsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val recentlyRead by viewModel.recentlyRead.collectAsState()
    val progressMap by viewModel.progressMap.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val readingStats by viewModel.readingStats.collectAsState()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val snackbarHostState = remember { SnackbarHostState() }
    var storyToDelete by remember { mutableStateOf<Story?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Đọc Truyện",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "Đọc gì hôm nay? 📖",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = onSearch,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Tìm kiếm")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Custom TabRow with pill indicator
                PremiumTabRow(
                    selectedTab = pagerState.currentPage,
                    tabs =
                        listOf(
                            TabInfo("Đang đọc", Icons.Default.MenuBook),
                            TabInfo("Thống kê", Icons.Default.BarChart),
                        ),
                    onTabSelected = { index ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                )

                // HorizontalPager for swiping between tabs
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    when (page) {
                        0 -> {
                            if (recentlyRead.isEmpty()) {
                                AnimatedEmptyState(
                                    icon = Icons.Default.MenuBook,
                                    message = "Chưa có truyện nào.\nNhấn tìm kiếm để bắt đầu!",
                                    actionText = "Tìm truyện",
                                    onActionClick = onSearch,
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column {
                                        // Sort chips
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            HomeSortOption.entries.forEach { option ->
                                                FilterChip(
                                                    selected = sortOption == option,
                                                    onClick = { viewModel.setSortOption(option) },
                                                    label = {
                                                        Text(
                                                            option.label,
                                                            style = MaterialTheme.typography.labelMedium,
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                )
                                            }
                                        }

                                        ReadingListContent(
                                            stories = recentlyRead,
                                            progressMap = progressMap,
                                            onStoryClick = onStoryClick,
                                            onDeleteClick = { story -> storyToDelete = story },
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            ReadingStatsTab(
                                stats = readingStats,
                                totalStories = recentlyRead.size,
                                progressMap = progressMap,
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    storyToDelete?.let { story ->
        AlertDialog(
            onDismissRequest = { storyToDelete = null },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text("Xóa khỏi danh sách?")
            },
            text = {
                Text(
                    "Xóa \"${story.title}\" khỏi danh sách đang đọc?\n" +
                        "Truyện đã tải sẽ không bị ảnh hưởng.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deletedStory = story
                        storyToDelete = null
                        viewModel.removeFromReadingList(deletedStory.id)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Đã xóa \"${deletedStory.title}\"",
                                actionLabel = "OK",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { storyToDelete = null }) {
                    Text("Hủy")
                }
            },
            shape = RoundedCornerShape(20.dp),
        )
    }
}

private data class TabInfo(
    val title: String,
    val icon: ImageVector,
)

@Composable
private fun PremiumTabRow(
    selectedTab: Int,
    tabs: List<TabInfo>,
    onTabSelected: (Int) -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = selectedTab == index

                Surface(
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            tab.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reading list with delete icon per story card.
 */
@Composable
private fun ReadingListContent(
    stories: List<Story>,
    progressMap: Map<String, ReadingProgress>,
    onStoryClick: (String, String) -> Unit,
    onDeleteClick: (Story) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(stories, key = { _, it -> "${it.sourceId}:${it.id}" }) { index, story ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { visible = true }

            val progress = progressMap[story.id]
            val readFraction =
                if (progress != null && story.totalChapters > 0) {
                    progress.lastChapterNumber.toFloat() / story.totalChapters
                } else {
                    0f
                }

            AnimatedVisibility(
                visible = visible,
                enter =
                    fadeIn(tween(300, delayMillis = (index % 8) * 50)) +
                        slideInVertically(
                            initialOffsetY = { 60 },
                            animationSpec = tween(400, delayMillis = (index % 8) * 50),
                        ),
            ) {
                // Card with trailing delete icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StoryCardPremium(
                            story = story,
                            onClick = { onStoryClick(story.id, story.url) },
                            showProgress = readFraction > 0f,
                            progress = readFraction,
                            subtitle =
                                if (progress != null) {
                                    "Chương ${progress.lastChapterNumber}/${story.totalChapters}"
                                } else {
                                    null
                                },
                        )
                    }
                    IconButton(
                        onClick = { onDeleteClick(story) },
                        modifier = Modifier.padding(start = 4.dp),
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

        // Bottom spacer
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Reading stats tab — shows reading summary inline on Home.
 */
@Composable
private fun ReadingStatsTab(
    stats: HomeViewModel.ReadingStatsState,
    totalStories: Int,
    progressMap: Map<String, ReadingProgress>,
) {
    val totalChaptersRead = progressMap.values.sumOf { it.lastChapterNumber }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Summary cards
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatItem(
                        icon = Icons.Default.MenuBook,
                        value = "$totalStories",
                        label = "Truyện",
                    )
                    StatItem(
                        icon = Icons.Default.Article,
                        value = "$totalChaptersRead",
                        label = "Chương đã đọc",
                    )
                    StatItem(
                        icon = Icons.Default.LocalFireDepartment,
                        value = "${stats.currentStreak}",
                        label = "Ngày liên tiếp",
                    )
                }
            }
        }

        // Today's reading
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Today,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "Hôm nay",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${stats.todayChapters}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                "chương",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                formatReadingTime(stats.todayMinutes),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                "thời gian đọc",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }

        // Weekly bar chart (last 7 days)
        if (stats.weeklyChapters.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "7 ngày gần đây",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )

                        // Simple bar chart
                        val maxChapters = stats.weeklyChapters.maxOrNull()?.coerceAtLeast(1) ?: 1
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            val dayLabels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
                            stats.weeklyChapters.forEachIndexed { idx, chapters ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    if (chapters > 0) {
                                        Text(
                                            "$chapters",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val barHeight =
                                        if (maxChapters > 0) {
                                            (chapters.toFloat() / maxChapters * 80).dp.coerceAtLeast(4.dp)
                                        } else {
                                            4.dp
                                        }
                                    Surface(
                                        modifier =
                                            Modifier
                                                .width(16.dp)
                                                .height(barHeight),
                                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                        color =
                                            if (idx == stats.weeklyChapters.lastIndex) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                            },
                                    ) {}
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        dayLabels.getOrElse(idx) { "" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom spacer
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
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

private fun formatReadingTime(minutes: Int): String =
    when {
        minutes >= 60 -> "${minutes / 60}h${if (minutes % 60 > 0) "${minutes % 60}'" else ""}"
        minutes > 0 -> "$minutes'"
        else -> "0'"
    }
