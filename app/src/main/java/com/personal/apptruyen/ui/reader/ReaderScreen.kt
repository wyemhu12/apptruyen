package com.personal.apptruyen.ui.reader

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.apptruyen.data.model.PokemonMatchRange
import com.personal.apptruyen.tts.TtsManager
import com.personal.apptruyen.tts.TtsService
import com.personal.apptruyen.ui.theme.*
import com.personal.apptruyen.ui.theme.rememberReaderThemeColors
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    // Force new LazyListState khi chương thay đổi → scroll position luôn bắt đầu từ 0 trước khi khôi phục initialScrollIndex
    val listState = remember(state.currentChapterNumber) { LazyListState() }
    val hapticTop = LocalHapticFeedback.current

    // [CRIT-10] Độ sáng màn hình đặt ở cấp gốc ReaderScreen để không bị reset khi đóng bảng Cài đặt
    val currentActivity = LocalActivity.current
    var brightness by rememberSaveable {
        val windowBrightness = currentActivity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (windowBrightness in 0.01f..1f) windowBrightness else -1f)
    }
    LaunchedEffect(currentActivity, brightness) {
        if (brightness in 0.01f..1f) {
            currentActivity?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = brightness
                window.attributes = lp
            }
        }
    }
    DisposableEffect(currentActivity) {
        onDispose {
            currentActivity?.window?.let { window ->
                val lp = window.attributes
                lp.screenBrightness = -1f
                window.attributes = lp
            }
        }
    }

    // Swipe counts for double-swipe chapter navigation (reset on chapter change)
    var nextSwipeCount by remember(state.currentChapterNumber) { mutableIntStateOf(0) }
    var prevSwipeCount by remember(state.currentChapterNumber) { mutableIntStateOf(0) }

    // Overscroll threshold per swipe (in pixels)
    val overscrollThreshold: Float = with(LocalDensity.current) { 80.dp.toPx() }

    // Split content into paragraphs (ưu tiên paragraphs đã tách trên background dispatcher từ ViewModel)
    val paragraphs =
        if (state.paragraphs.isNotEmpty()) {
            state.paragraphs
        } else {
            remember(state.content) {
                if (state.content.isBlank()) {
                    emptyList()
                } else {
                    TtsManager.splitIntoParagraphs(state.content)
                }
            }
        }

    // [CRIT-09] Khôi phục vị trí cuộn sau khi chương tải xong, sau đó mới lắng nghe snapshotFlow để lưu debounce
    LaunchedEffect(state.currentChapterNumber, state.isLoading) {
        if (state.isLoading || state.error != null || paragraphs.isEmpty()) return@LaunchedEffect
        if (state.scrollToEnd) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it > 2 }
            listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
        } else if (state.initialScrollIndex > 0) {
            val maxItemIndex = (paragraphs.size + 1).coerceAtLeast(0)
            listState.scrollToItem(state.initialScrollIndex.coerceIn(0, maxItemIndex))
        }
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .drop(1)
            .collectLatest { index ->
                delay(1000L)
                viewModel.onIntent(ReaderIntent.SaveScrollPosition(index))
            }
    }

    // [CRIT-09] & [HIGH-10] Lưu vị trí cuộn và chốt phiên đọc khi ON_PAUSE / onDispose; mở lại phiên khi ON_RESUME
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentState by rememberUpdatedState(state)
    val currentParagraphs by rememberUpdatedState(paragraphs)
    DisposableEffect(lifecycleOwner, listState) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        viewModel.onIntent(ReaderIntent.OnReaderResumed)
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        if (!currentState.isLoading && currentState.error == null && currentParagraphs.isNotEmpty()) {
                            viewModel.onIntent(ReaderIntent.SaveScrollPosition(listState.firstVisibleItemIndex))
                        }
                        viewModel.onIntent(ReaderIntent.OnReaderPaused)
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!currentState.isLoading && currentState.error == null && currentParagraphs.isNotEmpty()) {
                viewModel.onIntent(ReaderIntent.SaveScrollPosition(listState.firstVisibleItemIndex))
            }
            viewModel.onIntent(ReaderIntent.OnReaderPaused)
        }
    }

    // Auto-scroll to current paragraph when TTS is playing or paused (resume)
    LaunchedEffect(ttsState.currentParagraph, ttsState.isPlaying, ttsState.isPaused) {
        if ((ttsState.isPlaying || ttsState.isPaused) && paragraphs.isNotEmpty()) {
            // +2 because item 0 is PrevChapterSection, item 1 is chapter title
            listState.animateScrollToItem(ttsState.currentParagraph + 2)
        }
    }

    // [MED-02] Auto-scroll — tự động dừng khi chạm đáy chương
    if (state.autoScrollEnabled && !state.showSettings && !state.showChapterList && !state.isLoading) {
        LaunchedEffect(state.autoScrollSpeed, state.autoScrollEnabled, state.currentChapterNumber) {
            while (isActive) {
                if (listState.layoutInfo.totalItemsCount > 0 && !listState.canScrollForward) {
                    viewModel.onIntent(ReaderIntent.ToggleAutoScroll)
                    break
                }
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

    // [HIGH-16] Reader colors centralized in ReaderThemeColors (WCAG AA compliant)
    val readerColors = rememberReaderThemeColors(state.readerTheme)
    val bgColor = readerColors.backgroundColor
    val textColor = readerColors.textColor
    val highlightColor = readerColors.highlightBackground
    val accentColor = readerColors.accentColor
    val topBarColor = readerColors.panelBackground
    val topBarContentColor = readerColors.panelContentColor

    // [MED-01] Cố định contentPadding để ẩn/hiện thanh công cụ không làm nhảy văn bản 64dp
    val topPadding = 64.dp
    val bottomPadding = 140.dp

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
            bgColor = bgColor,
            textColor = textColor,
            accentColor = accentColor,
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
            onPokemonClick = { match -> viewModel.onIntent(ReaderIntent.SelectPokemon(match)) },
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
            ReaderSettingsPanel(
                fontSize = state.fontSize,
                theme = state.readerTheme,
                copyMode = state.copyMode,
                textReplacementEnabled = state.textReplacementEnabled,
                autoFormatEnabled = state.autoFormatEnabled,
                lineSpacing = state.lineSpacing,
                brightness = if (brightness < 0f) 0.5f else brightness,
                fontFamily = state.fontFamily,
                storyReplacementsEnabled = state.storyReplacementsEnabled,
                storyReplacements = state.storyReplacements,
                autoScrollEnabled = state.autoScrollEnabled,
                autoScrollSpeed = state.autoScrollSpeed,
                pokemonLookupEnabled = state.pokemonLookupEnabled,
                onBrightnessChange = { value ->
                    brightness = value.coerceIn(0.01f, 1f)
                },
                onFontSizeChange = { viewModel.onIntent(ReaderIntent.SetFontSize(it)) },
                onThemeChange = { viewModel.onIntent(ReaderIntent.SetReaderTheme(it)) },
                onToggleCopyMode = { viewModel.onIntent(ReaderIntent.ToggleCopyMode) },
                onToggleTextReplacement = { viewModel.onIntent(ReaderIntent.ToggleTextReplacement) },
                onToggleAutoFormat = { viewModel.onIntent(ReaderIntent.ToggleAutoFormat) },
                onTogglePokemonLookup = { viewModel.onIntent(ReaderIntent.TogglePokemonLookup) },
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

        // [HIGH-14] Global feedback snackbar (sendToGlobal)
        LaunchedEffect(state.snackbarMessage) {
            if (state.snackbarMessage != null) {
                delay(3000)
                viewModel.onIntent(ReaderIntent.ClearSnackbar)
            }
        }
        AnimatedVisibility(
            visible = state.snackbarMessage != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                action = {
                    TextButton(onClick = { viewModel.onIntent(ReaderIntent.ClearSnackbar) }) {
                        Text("Đóng", color = MaterialTheme.colorScheme.inversePrimary)
                    }
                },
            ) {
                Text(state.snackbarMessage ?: "")
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

        // Pokemon Lookup Dialog overlay
        state.selectedPokemon?.let { selection ->
            PokemonLookupDialog(
                selection = selection,
                onDismiss = { viewModel.onIntent(ReaderIntent.DismissPokemonDialog) },
            )
        }
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
    bgColor: Color,
    textColor: Color,
    accentColor: Color,
    highlightColor: Color,
    prevSwipeCount: Int,
    nextSwipeCount: Int,
    copyMode: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleBars: () -> Unit,
    onSeekAndPlay: (Int) -> Unit,
    onPokemonClick: (PokemonMatchRange) -> Unit,
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
                        color = accentColor,
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
                            border = BorderStroke(1.dp, accentColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                        ) {
                            Text("Quay lại")
                        }
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(12.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = bgColor,
                                ),
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

            val listContent: @Composable () -> Unit = {
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
                    item(key = "prev_chapter_section") {
                        PrevChapterSection(
                            hasPrevChapter = state.hasPrevChapter,
                            isLoadingPrevChapter = state.isLoadingPrevChapter,
                            readerTheme = state.readerTheme,
                            textColor = textColor,
                            swipeCount = prevSwipeCount,
                        )
                    }

                    // Chapter title
                    item(key = "chapter_title") {
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
                    itemsIndexed(
                        items = paragraphs,
                        key = { index, _ -> "paragraph_$index" },
                    ) { index, paragraph ->
                        val isActive =
                            index == ttsState.currentParagraph &&
                                (ttsState.isPlaying || ttsState.isPaused)

                        val paragraphBgColor by animateColorAsState(
                            targetValue = if (isActive) highlightColor else Color.Transparent,
                            animationSpec = tween(400),
                            label = "paragraphHighlight",
                        )

                        val baseModifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(paragraphBgColor)

                        if (copyMode) {
                            Text(
                                text = paragraph,
                                style = textStyle,
                                modifier =
                                    baseModifier
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                            )
                        } else {
                            val paragraphMatches =
                                if (state.pokemonLookupEnabled) {
                                    state.paragraphPokemonMatches[index] ?: persistentListOf()
                                } else {
                                    persistentListOf()
                                }
                            ReaderParagraphItem(
                                paragraph = paragraph,
                                matches = paragraphMatches,
                                textStyle = textStyle,
                                readerTheme = state.readerTheme,
                                modifier = baseModifier,
                                onToggleBars = onToggleBars,
                                onLongPressSeekAndPlay = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSeekAndPlay(index)
                                },
                                onPokemonClick = onPokemonClick,
                            )
                        }
                    }

                    // Next chapter section at bottom
                    item(key = "next_chapter_section") {
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

            if (copyMode) {
                SelectionContainer {
                    listContent()
                }
            } else {
                listContent()
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
