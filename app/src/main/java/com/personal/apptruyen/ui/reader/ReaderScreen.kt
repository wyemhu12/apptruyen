package com.personal.apptruyen.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.apptruyen.tts.TtsManager
import com.personal.apptruyen.tts.TtsService
import com.personal.apptruyen.ui.theme.*
import com.personal.apptruyen.ui.theme.rememberReaderThemeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val ttsState by viewModel.ttsState.collectAsState()
    // Force new LazyListState khi chương thay đổi → scroll position luôn bắt đầu từ 0
    val listState = remember(state.currentChapterNumber) { LazyListState() }
    val hapticTop = LocalHapticFeedback.current
    // Snackbar for per-story sendToGlobal feedback
    var globalFeedback by remember { mutableStateOf<String?>(null) }

    // Bug 5 fix: Auto-save scroll position with debounce (2s stability)
    // Key theo chương để avoid saving position cũ cho chương mới
    LaunchedEffect(state.currentChapterNumber) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                delay(2000) // debounce: wait 2s of stability before saving
                viewModel.onIntent(ReaderIntent.SaveScrollPosition(index))
            }
    }

    // Bug 5 fix: Save scroll position on exit
    DisposableEffect(Unit) {
        onDispose {
            viewModel.onIntent(ReaderIntent.SaveScrollPosition(listState.firstVisibleItemIndex))
        }
    }

    // Swipe counts for double-swipe chapter navigation (reset on chapter change)
    var nextSwipeCount by remember(state.currentChapterNumber) { mutableIntStateOf(0) }
    var prevSwipeCount by remember(state.currentChapterNumber) { mutableIntStateOf(0) }

    // Overscroll threshold per swipe (in pixels)
    val overscrollThreshold: Float = with(LocalDensity.current) { 80.dp.toPx() }

    // Split content into paragraphs for individual rendering
    val paragraphs =
        remember(state.content) {
            if (state.content.isBlank()) {
                emptyList()
            } else {
                TtsManager.splitIntoParagraphs(state.content)
            }
        }

    // Auto-scroll to current paragraph when TTS is playing or paused (resume)
    LaunchedEffect(ttsState.currentParagraph, ttsState.isPlaying, ttsState.isPaused) {
        if ((ttsState.isPlaying || ttsState.isPaused) && paragraphs.isNotEmpty()) {
            // +2 because item 0 is PrevChapterSection, item 1 is chapter title
            listState.animateScrollToItem(ttsState.currentParagraph + 2)
        }
    }

    // Scroll to end chỉ khi quay về chương trước — scroll to top tự động nhờ listState reset
    if (state.scrollToEnd) {
        LaunchedEffect(state.currentChapterNumber) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it > 2 }
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    // Auto-scroll
    if (state.autoScrollEnabled && !state.showSettings && !state.showChapterList) {
        LaunchedEffect(state.autoScrollSpeed, state.autoScrollEnabled) {
            while (isActive) {
                val pixelsPerTick = state.autoScrollSpeed * 1.2f
                listState.animateScrollBy(
                    pixelsPerTick,
                    tween(durationMillis = 50, easing = LinearEasing),
                )
                delay(50)
            }
        }
    }

    // NestedScrollConnection to detect overscroll at list boundaries
    val nestedScrollConnection =
        remember(state.currentChapterNumber) {
            object : NestedScrollConnection {
                private var bottomAccum = 0f
                private var topAccum = 0f

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    when {
                        available.y < 0f -> {
                            // Overscroll at bottom (user swiping up past end)
                            bottomAccum += abs(available.y)
                            if (bottomAccum >= overscrollThreshold) {
                                nextSwipeCount++
                                bottomAccum = 0f
                            }
                        }
                        available.y > 0f -> {
                            // Overscroll at top (user swiping down past start)
                            topAccum += available.y
                            if (topAccum >= overscrollThreshold) {
                                prevSwipeCount++
                                topAccum = 0f
                            }
                        }
                    }
                    // Reset accumulator when scrolling away from boundary
                    if (consumed.y > 0f) bottomAccum = 0f
                    if (consumed.y < 0f) topAccum = 0f
                    return Offset.Zero
                }
            }
        }

    // React to swipe counts — trigger navigation
    LaunchedEffect(nextSwipeCount) {
        if (nextSwipeCount >= 2) {
            viewModel.onIntent(ReaderIntent.LoadNextOnScroll)
        }
    }
    LaunchedEffect(prevSwipeCount) {
        if (prevSwipeCount >= 2) {
            viewModel.onIntent(ReaderIntent.LoadPrevOnScroll)
        }
    }

    // Reader background & text colors based on theme
    val (bgColor, textColor) =
        when (state.readerTheme) {
            ReaderViewModel.ReaderTheme.LIGHT -> Color(0xFFFFFBF5) to Color(0xFF1A1A1A)
            ReaderViewModel.ReaderTheme.DARK -> Color(0xFF1C1B1F) to Color(0xFFF0ECF0)
            ReaderViewModel.ReaderTheme.SEPIA -> SepiaBackground to Color(0xFF3E2C1A)
        }

    // Highlight color for active paragraph
    val highlightColor =
        when (state.readerTheme) {
            ReaderViewModel.ReaderTheme.LIGHT -> Color(0xFFFFF3CD) // warm yellow
            ReaderViewModel.ReaderTheme.DARK -> Color(0xFF3A3000) // dark gold
            ReaderViewModel.ReaderTheme.SEPIA -> Color(0xFFD0B286) // deeper sepia contrast
        }

    val readerColors = rememberReaderThemeColors(state.readerTheme)
    val topBarColor = readerColors.panelBackground
    val topBarContentColor = readerColors.panelContentColor

    // Animated padding for bars visibility
    val topPadding by animateDpAsState(
        targetValue = if (state.areBarsVisible) 64.dp else 0.dp,
        animationSpec = tween(300),
        label = "topPadding",
    )
    val bottomPadding by animateDpAsState(
        targetValue = if (state.areBarsVisible) 140.dp else 0.dp,
        animationSpec = tween(300),
        label = "bottomPadding",
    )

    // Show bars when settings panel is opened
    LaunchedEffect(state.showSettings) {
        if (state.showSettings && !state.areBarsVisible) {
            viewModel.onIntent(ReaderIntent.ToggleBars)
        }
    }

    // Intercept system back: đóng panel thay vì quay về trang trước
    BackHandler(enabled = state.showSettings || state.showChapterList) {
        when {
            state.showSettings -> viewModel.onIntent(ReaderIntent.ToggleSettings)
            state.showChapterList -> viewModel.onIntent(ReaderIntent.ToggleChapterList)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(bgColor),
    ) {
        // Main content area (loading / error / chapter text)
        ReaderContentArea(
            state = state,
            ttsState = ttsState,
            paragraphs = paragraphs,
            listState = listState,
            nestedScrollConnection = nestedScrollConnection,
            topPadding = topPadding,
            bottomPadding = bottomPadding,
            textColor = textColor,
            highlightColor = highlightColor,
            prevSwipeCount = prevSwipeCount,
            nextSwipeCount = nextSwipeCount,
            copyMode = state.copyMode,
            onBack = onBack,
            onRetry = { viewModel.onIntent(ReaderIntent.RetryLoad) },
            onToggleBars = {
                if (!state.showChapterList) {
                    // Pause auto-scroll on touch
                    if (state.autoScrollEnabled) {
                        viewModel.onIntent(ReaderIntent.ToggleAutoScroll)
                    }
                    viewModel.onIntent(ReaderIntent.ToggleBars)
                }
            },
            onSeekAndPlay = { viewModel.ttsSeekAndPlay(it) },
            onPrevChapter = {
                hapticTop.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.onIntent(ReaderIntent.PrevChapter)
            },
            onNextChapter = {
                hapticTop.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                viewModel.onIntent(ReaderIntent.NextChapter)
            },
        )

        // TopAppBar overlay — slide from top
        ReaderTopBar(
            visible = state.areBarsVisible,
            chapterTitle = state.chapterTitle,
            topBarColor = topBarColor,
            topBarContentColor = topBarContentColor,
            onBack = onBack,
            onToggleSettings = { viewModel.onIntent(ReaderIntent.ToggleSettings) },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // Auto-scroll indicator
        AnimatedVisibility(
            visible = state.autoScrollEnabled,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = if (state.areBarsVisible) 72.dp else 8.dp, end = 8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "Tự cuộn ×${state.autoScrollSpeed}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // Bottom bar overlay — slide from bottom
        AnimatedVisibility(
            visible = state.areBarsVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TtsControlBar(
                ttsState = ttsState,
                readerTheme = state.readerTheme,
                onPlay = { viewModel.ttsPlay() },
                onPause = { viewModel.ttsPause() },
                onStop = { viewModel.ttsStop() },
                onNext = { viewModel.onIntent(ReaderIntent.NextChapter) },
                onPrev = { viewModel.onIntent(ReaderIntent.PrevChapter) },
                onCycleSpeed = { viewModel.ttsCycleSpeed() },
                onSpeedChange = { viewModel.ttsSetSpeed(it) },
                onPitchChange = { viewModel.ttsSetPitch(it) },
                onShowChapterList = { viewModel.onIntent(ReaderIntent.ToggleChapterList) },
            )
        }

        // Settings bottom sheet overlay
        AnimatedVisibility(
            visible = state.showSettings,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val currentActivity = LocalContext.current as? Activity
            val initialBrightness =
                remember {
                    currentActivity?.window?.attributes?.screenBrightness ?: -1f
                }
            var brightness by remember {
                mutableFloatStateOf(
                    if (initialBrightness < 0f) 0.5f else initialBrightness,
                )
            }

            // Bug 1 fix: Restore system brightness when leaving settings / screen
            DisposableEffect(Unit) {
                onDispose {
                    currentActivity?.let { activity ->
                        val lp = activity.window.attributes
                        lp.screenBrightness = -1f // -1f = system default
                        activity.window.attributes = lp
                    }
                }
            }
            ReaderSettingsPanel(
                fontSize = state.fontSize,
                theme = state.readerTheme,
                copyMode = state.copyMode,
                textReplacementEnabled = state.textReplacementEnabled,
                autoFormatEnabled = state.autoFormatEnabled,
                lineSpacing = state.lineSpacing,
                brightness = brightness,
                fontFamily = state.fontFamily,
                storyReplacementsEnabled = state.storyReplacementsEnabled,
                storyReplacements = state.storyReplacements,
                autoScrollEnabled = state.autoScrollEnabled,
                autoScrollSpeed = state.autoScrollSpeed,
                onBrightnessChange = { value ->
                    brightness = value
                    currentActivity?.let { activity ->
                        val lp = activity.window.attributes
                        lp.screenBrightness = value
                        activity.window.attributes = lp
                    }
                },
                onFontSizeChange = { viewModel.onIntent(ReaderIntent.SetFontSize(it)) },
                onThemeChange = { viewModel.onIntent(ReaderIntent.SetReaderTheme(it)) },
                onToggleCopyMode = { viewModel.onIntent(ReaderIntent.ToggleCopyMode) },
                onToggleTextReplacement = { viewModel.onIntent(ReaderIntent.ToggleTextReplacement) },
                onToggleAutoFormat = { viewModel.onIntent(ReaderIntent.ToggleAutoFormat) },
                onLineSpacingChange = { viewModel.onIntent(ReaderIntent.SetLineSpacing(it)) },
                onFontFamilyChange = { viewModel.onIntent(ReaderIntent.SetFontFamily(it)) },
                onToggleStoryReplacements = { viewModel.onIntent(ReaderIntent.ToggleStoryReplacements) },
                onAddStoryReplacement = { from, to -> viewModel.onIntent(ReaderIntent.AddStoryReplacement(from, to)) },
                onRemoveStoryReplacement = { index -> viewModel.onIntent(ReaderIntent.RemoveStoryReplacement(index)) },
                onSendToGlobal = { from, to ->
                    viewModel.onIntent(ReaderIntent.SendReplacementToGlobal(from, to))
                },
                onToggleAutoScroll = { viewModel.onIntent(ReaderIntent.ToggleAutoScroll) },
                onSetAutoScrollSpeed = { viewModel.onIntent(ReaderIntent.SetAutoScrollSpeed(it)) },
                onDismiss = { viewModel.onIntent(ReaderIntent.ToggleSettings) },
            )
        }

        // TTS error snackbar — auto-dismiss after 5s
        var showTtsError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(ttsState.errorMessage) {
            if (ttsState.errorMessage != null) {
                showTtsError = ttsState.errorMessage
                delay(5000)
                showTtsError = null
            } else {
                showTtsError = null
            }
        }
        AnimatedVisibility(
            visible = showTtsError != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                action = {
                    TextButton(onClick = { showTtsError = null }) {
                        Text("Đóng", color = MaterialTheme.colorScheme.inversePrimary)
                    }
                },
            ) {
                Text(showTtsError ?: "")
            }
        }

        // Global feedback snackbar (sendToGlobal)
        LaunchedEffect(globalFeedback) {
            if (globalFeedback != null) {
                delay(3000)
                globalFeedback = null
            }
        }
        AnimatedVisibility(
            visible = globalFeedback != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                action = {
                    TextButton(onClick = { globalFeedback = null }) {
                        Text("Đóng", color = MaterialTheme.colorScheme.inversePrimary)
                    }
                },
            ) {
                Text(globalFeedback ?: "")
            }
        }

        // Chapter list panel overlay
        ChapterListPanel(
            visible = state.showChapterList,
            chapters = state.chapterList,
            currentChapterNumber = state.currentChapterNumber,
            readerTheme = state.readerTheme,
            onChapterClick = { chapter -> viewModel.onIntent(ReaderIntent.NavigateToChapter(chapter)) },
            onDismiss = { viewModel.onIntent(ReaderIntent.ToggleChapterList) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReaderContentArea(
    state: ReaderViewModel.ReaderState,
    ttsState: TtsService.TtsState,
    paragraphs: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    textColor: Color,
    highlightColor: Color,
    prevSwipeCount: Int,
    nextSwipeCount: Int,
    copyMode: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleBars: () -> Unit,
    onSeekAndPlay: (Int) -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    when {
        state.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Đang tải nội dung...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f),
                    )
                }
            }
        }
        state.error != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Quay lại")
                        }
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Thử lại")
                        }
                    }
                }
            }
        }
        else -> {
            val resolvedFont =
                when (state.fontFamily) {
                    ReaderViewModel.ReaderFontFamily.NOTO_SERIF -> NotoSerif
                    ReaderViewModel.ReaderFontFamily.ROBOTO -> FontFamily.SansSerif
                    ReaderViewModel.ReaderFontFamily.BE_VIETNAM_PRO -> BeVietnamPro
                    ReaderViewModel.ReaderFontFamily.SYSTEM -> FontFamily.Default
                }
            val textStyle =
                TextStyle(
                    fontSize = state.fontSize.sp,
                    lineHeight = (state.fontSize * state.lineSpacing).sp,
                    color = textColor,
                    fontFamily = resolvedFont,
                    fontWeight = FontWeight.Medium,
                )

            // Continuous scroll: LazyColumn
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                contentPadding =
                    PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = topPadding + 16.dp,
                        bottom = bottomPadding + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Previous chapter section at top
                item {
                    PrevChapterSection(
                        hasPrevChapter = state.hasPrevChapter,
                        isLoadingPrevChapter = state.isLoadingPrevChapter,
                        readerTheme = state.readerTheme,
                        textColor = textColor,
                        swipeCount = prevSwipeCount,
                    )
                }

                // Chapter title
                item {
                    Text(
                        text = state.chapterTitle,
                        style =
                            MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = resolvedFont,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = textColor,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }

                // Paragraphs with highlight and long-press
                itemsIndexed(paragraphs) { index, paragraph ->
                    val isActive =
                        index == ttsState.currentParagraph &&
                            (ttsState.isPlaying || ttsState.isPaused)

                    val bgColor =
                        if (isActive) {
                            animateColorAsState(
                                targetValue = highlightColor,
                                animationSpec = tween(400),
                                label = "paragraphHighlight",
                            ).value
                        } else {
                            Color.Transparent
                        }

                    val baseModifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)

                    if (copyMode) {
                        SelectionContainer {
                            Text(
                                text = paragraph,
                                style = textStyle,
                                modifier =
                                    baseModifier
                                        .clickable { onToggleBars() }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                            )
                        }
                    } else {
                        Text(
                            text = paragraph,
                            style = textStyle,
                            modifier =
                                baseModifier
                                    .combinedClickable(
                                        onClick = onToggleBars,
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSeekAndPlay(index)
                                        },
                                    ).padding(vertical = 8.dp, horizontal = 6.dp),
                        )
                    }
                }

                // Next chapter section at bottom
                item {
                    NextChapterSection(
                        hasNextChapter = state.hasNextChapter,
                        isLoadingNextChapter = state.isLoadingNextChapter,
                        readerTheme = state.readerTheme,
                        textColor = textColor,
                        swipeCount = nextSwipeCount,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    visible: Boolean,
    chapterTitle: String,
    topBarColor: Color,
    topBarContentColor: Color,
    onBack: () -> Unit,
    onToggleSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier,
    ) {
        TopAppBar(
            title = {
                Text(
                    text = chapterTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                }
            },
            actions = {
                IconButton(onClick = onToggleSettings) {
                    Icon(Icons.Default.Tune, contentDescription = "Cài đặt đọc")
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    titleContentColor = topBarContentColor,
                    navigationIconContentColor = topBarContentColor,
                    actionIconContentColor = topBarContentColor,
                ),
        )
    }
}
