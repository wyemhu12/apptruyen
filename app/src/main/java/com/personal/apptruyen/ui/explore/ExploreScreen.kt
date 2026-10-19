package com.personal.apptruyen.ui.explore

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.apptruyen.ui.components.AnimatedEmptyState
import com.personal.apptruyen.ui.components.ShimmerStoryGrid
import com.personal.apptruyen.ui.components.StoryCardGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onStoryClick: (String, String) -> Unit,
    onSearchClick: () -> Unit = {},
    onNavigateToSourceLogin: (String) -> Unit = {},
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loginRequiredSources by viewModel.loginRequiredSources.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // Infinity scroll: trigger loadMore when near bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem =
                gridState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 4 && !state.isLoadingMore && state.canLoadMore && !state.isLoading
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.stories.isNotEmpty()) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Khám Phá",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        val subtitle =
                            if (state.selectedGenre != null) {
                                "${state.selectedGenre?.name} ✨"
                            } else {
                                "Truyện hoàn thành hay nhất ✨"
                            }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = onSearchClick,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Tìm kiếm")
                    }
                    FilledTonalIconButton(
                        onClick = { viewModel.refresh() },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Làm mới")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.pullToRefresh() },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when {
                state.isLoading -> {
                    ShimmerStoryGrid(
                        count = 6,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                state.error != null && state.stories.isEmpty() -> {
                    AnimatedEmptyState(
                        icon = Icons.Default.Explore,
                        message = state.error ?: "Đã xảy ra lỗi",
                        actionText = "Thử lại",
                        onActionClick = { viewModel.refresh() },
                    )
                }
                else -> {
                    Column {
                        // Genre chips row
                        if (state.genres.isNotEmpty()) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item {
                                    FilterChip(
                                        selected = state.selectedGenre == null,
                                        onClick = { viewModel.selectGenre(null) },
                                        label = { Text("Tất cả") },
                                        shape = RoundedCornerShape(20.dp),
                                    )
                                }
                                items(state.genres, key = { it.slug }) { genre ->
                                    FilterChip(
                                        selected = state.selectedGenre == genre,
                                        onClick = { viewModel.selectGenre(genre) },
                                        label = { Text(genre.name) },
                                        shape = RoundedCornerShape(20.dp),
                                    )
                                }
                            }
                        }

                        // Wikidich login required banner
                        AnimatedVisibility(
                            visible = loginRequiredSources.contains("wikidich"),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.tertiary,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Wikidich yêu cầu đăng nhập",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "Đăng nhập tài khoản Wikidich để đọc và duyệt danh sách đầy đủ.",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Button(
                                        onClick = { onNavigateToSourceLogin("wikidich") },
                                        colors =
                                            ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.tertiary,
                                                contentColor = MaterialTheme.colorScheme.onTertiary,
                                            ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Text("Đăng nhập", style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissLoginPrompt("wikidich") },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Đóng",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (state.stories.isEmpty()) {
                            val isWikidichLoginNeeded = loginRequiredSources.contains("wikidich")
                            AnimatedEmptyState(
                                icon = Icons.Default.Explore,
                                message =
                                    if (isWikidichLoginNeeded) {
                                        "Vui lòng đăng nhập tài khoản Wikidich để duyệt và tìm kiếm danh sách truyện"
                                    } else {
                                        "Không tìm thấy truyện nào"
                                    },
                                actionText = if (isWikidichLoginNeeded) "Đăng nhập Wikidich" else "Thử lại",
                                onActionClick = {
                                    if (isWikidichLoginNeeded) {
                                        onNavigateToSourceLogin("wikidich")
                                    } else {
                                        viewModel.refresh()
                                    }
                                },
                            )
                        } else {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = 150.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                itemsIndexed(
                                    items = state.stories,
                                    key = { _, story -> "${story.sourceId}:${story.id}" },
                                ) { index, story ->
                                    var visible by remember { mutableStateOf(false) }
                                    LaunchedEffect(Unit) { visible = true }

                                    AnimatedVisibility(
                                        visible = visible,
                                        enter =
                                            fadeIn(tween(300, delayMillis = (index % 6) * 60)) +
                                                scaleIn(
                                                    initialScale = 0.85f,
                                                    animationSpec = tween(350, delayMillis = (index % 6) * 60),
                                                ),
                                    ) {
                                        StoryCardGrid(
                                            story = story,
                                            onClick = { onStoryClick(story.id, story.url) },
                                        )
                                    }
                                }

                                // Loading more indicator
                                if (state.isLoadingMore) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(24.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(28.dp),
                                                strokeWidth = 3.dp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } // end Column
                }
            }
        }
    }
}
