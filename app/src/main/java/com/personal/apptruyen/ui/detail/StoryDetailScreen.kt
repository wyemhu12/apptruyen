package com.personal.apptruyen.ui.detail

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.personal.apptruyen.data.model.Chapter
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.data.repository.DownloadProgress
import com.personal.apptruyen.ui.components.ShimmerStoryDetail
import com.personal.apptruyen.ui.components.shimmerBrush
import com.personal.apptruyen.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StoryDetailScreen(
    onBack: () -> Unit,
    onReadChapter: (String, Int, String) -> Unit, // storyId, chapterNumber, chapterUrl
    viewModel: StoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var isFabVisible by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val nestedScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // Return no consumed offset, just track direction
                    if (available.y < -10) {
                        isFabVisible = false
                    } else if (available.y > 10) {
                        isFabVisible = true
                    }
                    return Offset.Zero
                }
            }
        }

    Scaffold(
        modifier = Modifier.nestedScroll(nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = { },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                ShimmerStoryDetail(
                    modifier = Modifier.padding(padding),
                )
            }
            state.error != null -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.error ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(32.dp),
                        )
                        OutlinedButton(
                            onClick = { viewModel.retry() },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Thử lại")
                        }
                    }
                }
            }
            state.story != null -> {
                val story = state.story ?: return@Scaffold
                val progress = state.readingProgress
                val lastChapterNum = progress?.lastChapterNumber
                val lastReadChapter =
                    if (lastChapterNum != null) {
                        state.chapters.find { it.chapterNumber == lastChapterNum }
                    } else {
                        null
                    }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // ── Hero Section ──
                    item {
                        HeroSection(story = story)
                    }

                    // ── Genre Chips ──
                    if (story.genres.isNotEmpty()) {
                        item {
                            FlowRow(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                story.genres.forEach { genre ->
                                    SuggestionChip(
                                        onClick = { },
                                        label = {
                                            Text(genre, style = MaterialTheme.typography.labelSmall)
                                        },
                                        shape = RoundedCornerShape(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    // ── Action Buttons ──
                    item {
                        ActionButtonsSection(
                            story = story,
                            firstChapter = state.chapters.firstOrNull(),
                            lastReadChapter = lastReadChapter,
                            onReadChapter = onReadChapter,
                            onDownloadRange = { showDownloadDialog = true },
                            onDownloadAll = { viewModel.downloadAll() },
                        )
                    }

                    // ── Download Progress ──
                    if (state.isDownloading && state.downloadProgress != null) {
                        item {
                            DownloadProgressCard(
                                progress = state.downloadProgress ?: return@item,
                                onCancel = { viewModel.cancelDownload() },
                            )
                        }
                    }

                    // ── Description ──
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "Giới thiệu",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            if (story.description.isNotBlank()) {
                                Text(
                                    text = story.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                TextButton(
                                    onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(
                                        if (isDescriptionExpanded) "Thu gọn" else "Xem thêm",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            } else {
                                Text(
                                    text = "Chưa có mô tả cho truyện này.",
                                    style =
                                        MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }

                    // ── Unread Badge (B8) ──
                    if (progress != null && story.totalChapters > 0) {
                        val unread = story.totalChapters - progress.lastChapterNumber
                        if (unread > 0) {
                            item {
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text("Còn $unread chương chưa đọc") },
                                    icon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }
                    }

                    // ── Chapter list header ──
                    item {
                        Text(
                            text = "Danh sách chương (${state.totalChapterCount})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }

                    // ── Chapters (lazy paged — C2 + B5) ──
                    itemsIndexed(state.displayedChapters, key = { _, ch -> ch.chapterNumber }) { index, chapter ->
                        ChapterListItem(
                            chapter = chapter,
                            lastChapterNum = lastChapterNum,
                            storyId = story.id,
                            onReadChapter = onReadChapter,
                        )

                        // Infinity scroll trigger (C2)
                        if (index == state.displayedChapters.size - 10 && state.canLoadMoreChapters) {
                            LaunchedEffect(state.displayedChapters.size) {
                                viewModel.loadMoreChapters()
                            }
                        }
                    }

                    // Loading indicator for more chapters
                    if (state.canLoadMoreChapters) {
                        item {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }

                    // Bottom spacer for nav bar
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }

    // ── Bottom Sheet Chapter Jump (B9) ──
    ChapterJumpSheet(
        story = state.story,
        displayedChapters = state.chapters,
        isVisible = isFabVisible,
        onReadChapter = onReadChapter,
    )

    // Download range dialog
    if (showDownloadDialog) {
        DownloadRangeDialog(
            totalChapters = state.chapters.size,
            onDismiss = { showDownloadDialog = false },
            onDownload = { from, to ->
                showDownloadDialog = false
                viewModel.downloadRange(from, to)
            },
        )
    }
}

// ── Extracted Composables ──────────────────────────────────────────

@Composable
private fun HeroSection(story: Story) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(300.dp),
    ) {
        // Cover image background
        if (story.coverImageUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(story.coverImageUrl)
                        .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(25.dp),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(shimmerBrush()),
                    )
                },
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(GradientAmberStart, GradientAmberEnd),
                            ),
                        ),
            )
        }

        // Gradient scrim overlay
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.7f),
                                ),
                        ),
                    ),
        )

        // Content on top of hero
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Cover image card
            if (story.coverImageUrl.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    SubcomposeAsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(story.coverImageUrl)
                                .build(),
                        contentDescription = story.title,
                        modifier = Modifier.size(100.dp, 140.dp),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(shimmerBrush()),
                            )
                        },
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                if (story.author.isNotBlank()) {
                    Text(
                        text = story.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                // Chapter count badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = "${story.totalChapters} chương",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtonsSection(
    story: Story,
    firstChapter: Chapter?,
    lastReadChapter: Chapter?,
    onReadChapter: (String, Int, String) -> Unit,
    onDownloadRange: () -> Unit,
    onDownloadAll: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Read from start
        if (firstChapter != null) {
            Button(
                onClick = {
                    onReadChapter(story.id, firstChapter.chapterNumber, firstChapter.url)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Đọc từ đầu", style = MaterialTheme.typography.labelLarge)
            }
        }

        // Continue reading
        if (lastReadChapter != null) {
            FilledTonalButton(
                onClick = {
                    onReadChapter(story.id, lastReadChapter.chapterNumber, lastReadChapter.url)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tiếp tục đọc", style = MaterialTheme.typography.labelLarge)
            }
        }

        // Download actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onDownloadRange,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tải khoảng", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = onDownloadAll,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tải tất cả", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DownloadProgressCard(
    progress: DownloadProgress,
    onCancel: () -> Unit = {},
) {
    Card(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (progress.currentChapterTitle.isNotBlank()) progress.currentChapterTitle else "Đang tải...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${progress.current} / ${progress.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                // Cancel button
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Hủy tải",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    if (progress.total > 0) progress.current.toFloat() / progress.total else 0f
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.tertiaryContainer,
            )
            if (progress.errors > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${progress.errors} lỗi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ChapterListItem(
    chapter: Chapter,
    lastChapterNum: Int?,
    storyId: String,
    onReadChapter: (String, Int, String) -> Unit,
) {
    val isLastRead = chapter.chapterNumber == lastChapterNum
    val isRead = lastChapterNum != null && chapter.chapterNumber < lastChapterNum
    val contentAlpha = if (isRead) 0.5f else 1f

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        color =
            if (isLastRead) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        onClick = {
            onReadChapter(storyId, chapter.chapterNumber, chapter.url)
        },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Read indicator (B5)
            if (isRead) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Đã đọc",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }

            // Number badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color =
                    if (isLastRead) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${chapter.chapterNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (isLastRead) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
                            },
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )

            if (chapter.isDownloaded) {
                Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = "Đã tải",
                    tint = StatusDownloaded,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterJumpSheet(
    story: Story?,
    displayedChapters: List<Chapter>,
    isVisible: Boolean = true,
    onReadChapter: (String, Int, String) -> Unit,
) {
    var showChapterSheet by remember { mutableStateOf(false) }
    var chapterSearchQuery by remember { mutableStateOf("") }

    if (story != null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomEnd,
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                FloatingActionButton(
                    onClick = { showChapterSheet = true },
                    modifier = Modifier.padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        Icons.Default.FormatListNumbered,
                        contentDescription = "Nhảy đến chương",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }

    if (showChapterSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showChapterSheet = false
                chapterSearchQuery = ""
            },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
            ) {
                Text(
                    "Nhảy đến chương",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                OutlinedTextField(
                    value = chapterSearchQuery,
                    onValueChange = { chapterSearchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tìm chương (số hoặc tên)...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))

                val filteredChapters =
                    remember(chapterSearchQuery, displayedChapters) {
                        if (chapterSearchQuery.isBlank()) {
                            displayedChapters
                        } else {
                            displayedChapters.filter { ch ->
                                ch.title.contains(chapterSearchQuery, ignoreCase = true) ||
                                    ch.chapterNumber.toString().contains(chapterSearchQuery)
                            }
                        }
                    }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(filteredChapters) { _, chapter ->
                        Surface(
                            onClick = {
                                showChapterSheet = false
                                chapterSearchQuery = ""
                                story?.let { s ->
                                    onReadChapter(s.id, chapter.chapterNumber, chapter.url)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    "${chapter.chapterNumber}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    chapter.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DownloadRangeDialog(
    totalChapters: Int,
    onDismiss: () -> Unit,
    onDownload: (Int, Int) -> Unit,
) {
    var fromText by remember { mutableStateOf("1") }
    var toText by remember { mutableStateOf(totalChapters.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text("Tải chương", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Chọn khoảng chương muốn tải (1 - $totalChapters)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = fromText,
                        onValueChange = { fromText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Từ") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    Text("→", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = toText,
                        onValueChange = { toText = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Đến") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var from = (fromText.toIntOrNull() ?: 1).coerceIn(1, totalChapters)
                    var to = (toText.toIntOrNull() ?: totalChapters).coerceIn(1, totalChapters)
                    if (from > to) {
                        val tmp = from
                        from = to
                        to = tmp
                    }
                    onDownload(from, to)
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Tải xuống")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
    )
}
