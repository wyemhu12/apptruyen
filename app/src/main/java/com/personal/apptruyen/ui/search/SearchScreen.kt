package com.personal.apptruyen.ui.search

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.apptruyen.data.model.Genre
import com.personal.apptruyen.ui.components.ShimmerStoryCard
import com.personal.apptruyen.ui.components.StoryCardPremium

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onStoryClick: (String, String) -> Unit,
    initialQuery: String = "",
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val genres by viewModel.genres.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // TextFieldValue giữ IME composition state cho tiếng Việt (telex/vni)
    var textFieldValue by remember { mutableStateOf(TextFieldValue(initialQuery)) }
    // Sync từ ViewModel → local (khi clear, history click)
    LaunchedEffect(state.query) {
        if (state.query != textFieldValue.text) {
            textFieldValue = TextFieldValue(state.query)
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            viewModel.onQueryChange(initialQuery)
            viewModel.search()
        }
    }

    // Infinity scroll for filter results
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 3 && !state.isLoadingMore && state.canLoadMore && state.isFilterActive
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.results.isNotEmpty()) {
            viewModel.loadMoreFilterResults()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tìm kiếm", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            // ─── Search bar with inline filter button ───
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    TextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Nhập tên truyện...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingIcon = {
                            if (textFieldValue.text.isNotBlank()) {
                                IconButton(onClick = {
                                    textFieldValue = TextFieldValue("")
                                    viewModel.onQueryChange("")
                                }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Xóa",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions =
                            KeyboardActions(onSearch = {
                                viewModel.onQueryChange(textFieldValue.text)
                                viewModel.search()
                            }),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        shape = RoundedCornerShape(16.dp),
                    )
                }

                // Filter button — badge indicates active filter count
                // Badge: completedOnly=true là default, chỉ tính khi tắt (=false)
                val activeFilterCount =
                    listOfNotNull(
                        state.selectedGenre,
                        if (state.chapterFilter != ChapterCountFilter.NONE) true else null,
                        if (!state.completedOnly) true else null, // Tắt Full = active filter
                    ).size

                BadgedBox(
                    badge = {
                        if (activeFilterCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ) {
                                Text("$activeFilterCount")
                            }
                        }
                    },
                ) {
                    FilledTonalIconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors =
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor =
                                    if (activeFilterCount > 0) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                            ),
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Bộ lọc",
                            tint =
                                if (activeFilterCount > 0) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }

            // ─── Active filter chips (compact, dismissible) ───
            val hasActiveFilters =
                state.selectedGenre != null ||
                    state.chapterFilter != ChapterCountFilter.NONE ||
                    !state.completedOnly // Tắt Full = non-default = active filter

            AnimatedVisibility(
                visible = hasActiveFilters,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.selectedGenre?.let { genre ->
                        InputChip(
                            selected = true,
                            onClick = {
                                viewModel.applyAllFilters(
                                    genre = null,
                                    chapterFilter = state.chapterFilter,
                                    completedOnly = state.completedOnly,
                                )
                            },
                            label = { Text(genre.name, style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Bỏ thể loại",
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(28.dp),
                        )
                    }
                    if (state.chapterFilter != ChapterCountFilter.NONE) {
                        InputChip(
                            selected = true,
                            onClick = {
                                viewModel.applyAllFilters(
                                    genre = state.selectedGenre,
                                    chapterFilter = ChapterCountFilter.NONE,
                                    completedOnly = state.completedOnly,
                                )
                            },
                            label = { Text(state.chapterFilter.label, style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Bỏ lọc số chương",
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(28.dp),
                        )
                    }
                    if (!state.completedOnly) {
                        // User đã tắt Full → hiện chip "Cả đang ra" để dismiss
                        InputChip(
                            selected = true,
                            onClick = {
                                viewModel.applyAllFilters(
                                    genre = state.selectedGenre,
                                    chapterFilter = state.chapterFilter,
                                    completedOnly = true, // Reset về default
                                )
                            },
                            label = { Text("Cả đang ra", style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Bật lại lọc Full",
                                    modifier = Modifier.size(14.dp),
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(28.dp),
                        )
                    }
                }
            }

            // ─── Autocomplete suggestions dropdown ───
            val showSuggestions =
                searchSuggestions.isNotEmpty() && textFieldValue.text.isNotBlank() && !state.hasSearched
            AnimatedVisibility(
                visible = showSuggestions,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        searchSuggestions.forEach { suggestion ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Transparent,
                                onClick = {
                                    textFieldValue = TextFieldValue(suggestion)
                                    viewModel.onQueryChange(suggestion)
                                    viewModel.search()
                                },
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Results summary ───
            if (state.results.isNotEmpty()) {
                val summary =
                    buildString {
                        append("Tìm thấy ${state.results.size} truyện")
                        if (!state.completedOnly) {
                            append(" • Cả đang ra")
                        } else {
                            append(" • Full")
                        }
                        state.selectedGenre?.let { append(" • ${it.name}") }
                        if (state.chapterFilter != ChapterCountFilter.NONE) {
                            append(" • ${state.chapterFilter.label} chương")
                        }
                    }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ─── Content area ───
            when {
                state.isLoading -> {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        repeat(4) {
                            ShimmerStoryCard()
                        }
                    }
                }
                state.error != null && state.results.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(32.dp),
                            )
                            OutlinedButton(
                                onClick = { viewModel.search() },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Thử lại")
                            }
                        }
                    }
                }
                state.results.isNotEmpty() -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(state.results, key = { _, it -> "${it.sourceId}:${it.id}" }) { index, story ->
                            var visible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { visible = true }

                            AnimatedVisibility(
                                visible = visible,
                                enter =
                                    fadeIn(tween(250, delayMillis = (index % 10) * 40)) +
                                        slideInVertically(
                                            initialOffsetY = { 50 },
                                            animationSpec = tween(300, delayMillis = (index % 10) * 40),
                                        ),
                            ) {
                                StoryCardPremium(
                                    story = story,
                                    onClick = { onStoryClick(story.id, story.url) },
                                )
                            }
                        }

                        // Loading more indicator
                        if (state.isLoadingMore) {
                            item {
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
                // Search history
                !state.hasSearched && recentSearches.isNotEmpty() -> {
                    FilterPromptSection(
                        onFilterClick = { showFilterSheet = true },
                    )
                    SearchHistorySection(
                        searches = recentSearches,
                        onSearchClick = { viewModel.searchFromHistory(it) },
                        onDeleteItem = { viewModel.deleteHistoryItem(it) },
                        onClearAll = { viewModel.clearHistory() },
                    )
                }
                // Empty initial state
                !state.hasSearched -> {
                    FilterPromptSection(
                        onFilterClick = { showFilterSheet = true },
                    )
                }
                state.hasSearched -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Không tìm thấy kết quả",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // ─── Unified Filter Bottom Sheet ───
    if (showFilterSheet) {
        FilterBottomSheet(
            genres = genres,
            selectedGenre = state.selectedGenre,
            chapterFilter = state.chapterFilter,
            completedOnly = state.completedOnly,
            onDismiss = { showFilterSheet = false },
            onApply = { genre, chapterFilter, completedOnly ->
                showFilterSheet = false
                viewModel.applyAllFilters(genre, chapterFilter, completedOnly)
            },
            onClearAll = {
                showFilterSheet = false
                viewModel.clearAllFilters()
            },
        )
    }
}

/**
 * Unified filter bottom sheet — contains chapter count, status, and genre filters.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterBottomSheet(
    genres: List<Genre>,
    selectedGenre: Genre?,
    chapterFilter: ChapterCountFilter,
    completedOnly: Boolean,
    onDismiss: () -> Unit,
    onApply: (Genre?, ChapterCountFilter, Boolean) -> Unit,
    onClearAll: () -> Unit,
) {
    // Local state so user can adjust before applying
    var localGenre by remember { mutableStateOf(selectedGenre) }
    var localChapterFilter by remember { mutableStateOf(chapterFilter) }
    var localCompleted by remember { mutableStateOf(completedOnly) }

    // completedOnly=true là default → chỉ tính active filter khi user TẮT nó (=false)
    val hasAnyFilter =
        localGenre != null ||
            localChapterFilter != ChapterCountFilter.NONE ||
            !localCompleted

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
        ) {
            // Title
            Text(
                text = "Bộ lọc",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            // ─── Section 1: Chapter count ───
            Text(
                text = "Số chương",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ChapterCountFilter.entries.forEach { option ->
                    FilterChip(
                        selected = localChapterFilter == option,
                        onClick = {
                            localChapterFilter =
                                if (localChapterFilter == option && option != ChapterCountFilter.NONE) {
                                    ChapterCountFilter.NONE
                                } else {
                                    option
                                }
                        },
                        label = {
                            Text(option.label, style = MaterialTheme.typography.labelMedium)
                        },
                        shape = RoundedCornerShape(20.dp),
                        border = null,
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // ─── Section 2: Status ───
            Text(
                text = "Trạng thái",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            FilterChip(
                selected = localCompleted,
                onClick = { localCompleted = !localCompleted },
                label = {
                    Text("Chỉ truyện Full", style = MaterialTheme.typography.labelMedium)
                },
                shape = RoundedCornerShape(20.dp),
                border = null,
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // ─── Section 3: Genre ───
            Text(
                text = "Thể loại",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                genres.forEach { genre ->
                    FilterChip(
                        selected = localGenre?.slug == genre.slug,
                        onClick = {
                            localGenre = if (localGenre?.slug == genre.slug) null else genre
                        },
                        label = {
                            Text(genre.name, style = MaterialTheme.typography.labelMedium)
                        },
                        shape = RoundedCornerShape(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Apply button ───
            Button(
                onClick = { onApply(localGenre, localChapterFilter, localCompleted) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Áp dụng bộ lọc", style = MaterialTheme.typography.labelLarge)
            }

            // ─── Clear all button ───
            if (hasAnyFilter) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) {
                    Text("Xóa tất cả bộ lọc", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun FilterPromptSection(onFilterClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = onFilterClick,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Lọc Truyện",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "Tìm kiếm theo tên truyện hoặc chọn thể loại.\nKết hợp từ khóa + bộ lọc để tìm chi tiết.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SearchHistorySection(
    searches: List<String>,
    onSearchClick: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Tìm kiếm gần đây",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClearAll) {
            Text("Xóa tất cả", style = MaterialTheme.typography.labelSmall)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(searches, key = { it }) { query ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                onClick = { onSearchClick(query) },
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = { onDeleteItem(query) },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Xóa",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
