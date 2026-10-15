package com.personal.apptruyen.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.SubcomposeAsyncImage
import com.personal.apptruyen.data.model.PokemonMatchRange
import com.personal.apptruyen.data.model.PokemonNameLanguage
import com.personal.apptruyen.data.model.PokemonSelection
import com.personal.apptruyen.ui.theme.PokemonTypeColors
import com.personal.apptruyen.util.PokemonLookupHelper
import kotlinx.collections.immutable.ImmutableList

/**
 * Hiển thị một đoạn văn trong chương truyện kèm hỗ trợ highlight gạch chân nét đứt
 * và chạm vào tên Pokémon để mở từ điển hình ảnh.
 *
 * - Khi chạm vào tên Pokémon: gọi [onPokemonClick].
 * - Khi chạm vào vùng văn bản thường: gọi [onToggleBars] (ẩn/hiện thanh công cụ).
 * - Khi nhấn giữ (long press): gọi [onLongPressSeekAndPlay] (phát TTS từ đoạn này).
 */
@Composable
internal fun ReaderParagraphItem(
    paragraph: String,
    matches: ImmutableList<PokemonMatchRange>,
    textStyle: TextStyle,
    readerTheme: ReaderViewModel.ReaderTheme,
    modifier: Modifier = Modifier,
    onToggleBars: () -> Unit,
    onLongPressSeekAndPlay: () -> Unit,
    onPokemonClick: (PokemonMatchRange) -> Unit,
) {
    val highlightColor = PokemonTypeColors.pokemonHighlightColor(readerTheme)

    val validMatches =
        remember(paragraph, matches) {
            matches.filter { match ->
                match.start >= 0 && match.end <= paragraph.length && match.start < match.end
            }
        }

    val annotatedText: AnnotatedString =
        remember(paragraph, validMatches, highlightColor) {
            if (validMatches.isEmpty()) {
                AnnotatedString(paragraph)
            } else {
                buildAnnotatedString {
                    append(paragraph)
                    for (match in validMatches) {
                        addStyle(
                            style =
                                SpanStyle(
                                    color = highlightColor,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            start = match.start,
                            end = match.end,
                        )
                    }
                }
            }
        }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedText,
        style = textStyle,
        onTextLayout = { textLayoutResult = it },
        modifier =
            modifier
                .pointerInput(validMatches, onToggleBars, onLongPressSeekAndPlay, onPokemonClick) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val layout = textLayoutResult
                            val tappedMatch =
                                if (layout != null && validMatches.isNotEmpty()) {
                                    findTappedPokemonMatch(layout, tapOffset, validMatches, paragraph.length)
                                } else {
                                    null
                                }
                            if (tappedMatch != null) {
                                onPokemonClick(tappedMatch)
                            } else {
                                onToggleBars()
                            }
                        },
                        onLongPress = {
                            onLongPressSeekAndPlay()
                        },
                    )
                }.padding(vertical = 8.dp, horizontal = 6.dp)
                .drawBehind {
                    val layout = textLayoutResult ?: return@drawBehind
                    if (validMatches.isEmpty()) return@drawBehind
                    val strokeWidthPx = 1.5.dp.toPx()
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f)
                    for (match in validMatches) {
                        val safeStart = match.start.coerceIn(0, (paragraph.length - 1).coerceAtLeast(0))
                        val safeEnd = (match.end - 1).coerceIn(safeStart, (paragraph.length - 1).coerceAtLeast(0))
                        val startLine = layout.getLineForOffset(safeStart)
                        val endLine = layout.getLineForOffset(safeEnd)
                        for (line in startLine..endLine) {
                            val lineStartOffset = maxOf(safeStart, layout.getLineStart(line))
                            val lineEndOffset = minOf(match.end, layout.getLineEnd(line, visibleEnd = true))
                            val startX = layout.getHorizontalPosition(lineStartOffset, usePrimaryDirection = true)
                            val endX = layout.getHorizontalPosition(lineEndOffset, usePrimaryDirection = true)
                            val leftX = minOf(startX, endX)
                            val rightX = maxOf(startX, endX)
                            val y = layout.getLineBottom(line) - 2.dp.toPx()
                            if (rightX > leftX) {
                                drawLine(
                                    color = highlightColor.copy(alpha = 0.78f),
                                    start = Offset(leftX, y),
                                    end = Offset(rightX, y),
                                    strokeWidth = strokeWidthPx,
                                    pathEffect = dashEffect,
                                )
                            }
                        }
                    }
                },
    )
}

private fun findTappedPokemonMatch(
    layout: TextLayoutResult,
    tapOffset: Offset,
    matches: List<PokemonMatchRange>,
    textLength: Int,
): PokemonMatchRange? {
    if (textLength <= 0) return null
    if (tapOffset.y < 0f || tapOffset.y > layout.size.height) return null
    val line = layout.getLineForVerticalPosition(tapOffset.y)
    val lineLeft = layout.getLineLeft(line)
    val lineRight = layout.getLineRight(line)
    if (tapOffset.x < lineLeft - 8f || tapOffset.x > lineRight + 8f) return null
    val charOffset = layout.getOffsetForPosition(tapOffset)
    return matches.firstOrNull { charOffset >= it.start && charOffset < it.end }
}

/**
 * Popup hiển thị thông tin hình ảnh, hệ và các hình thái của Pokémon khi chạm vào tên trong truyện.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun PokemonLookupDialog(
    selection: PokemonSelection,
    onDismiss: () -> Unit,
) {
    val entry = selection.entry
    val forms = entry.forms
    var currentFormIndex by remember(selection) {
        mutableIntStateOf(selection.initialFormIndex.coerceIn(0, (forms.size - 1).coerceAtLeast(0)))
    }
    var currentLang by remember(selection) {
        mutableStateOf(PokemonLookupHelper.detectInitialLanguage(entry, selection.matchedText))
    }
    var imageRetryKey by remember(currentFormIndex) { mutableIntStateOf(0) }

    val activeForm = forms.getOrNull(currentFormIndex) ?: forms.firstOrNull() ?: return
    val displayTitle = PokemonLookupHelper.formatDisplayName(entry, activeForm, currentLang)
    val subtitleNames =
        remember(entry, activeForm) {
            PokemonLookupHelper.formatSubtitleNames(entry, activeForm)
        }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header: Mã Pokédex + Tên + Nút đóng
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = String.format("#%04d", entry.id),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    Text(
                        text = "Đã chạm: \"${selection.matchedText}\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        textAlign = TextAlign.Center,
                    )

                    FilledTonalIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Đóng",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // Tên hiển thị theo ngôn ngữ đã chọn
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Text(
                    text = subtitleNames,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Thanh chuyển ngôn ngữ tên
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    PokemonNameLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = currentLang == lang,
                            onClick = { currentLang = lang },
                            label = {
                                Text(
                                    text = lang.label,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }

                // Khung ảnh Pokémon (Coil 3 SubcomposeAsyncImage)
                Box(
                    modifier =
                        Modifier
                            .size(188.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val imageUrlWithRetry =
                        if (imageRetryKey == 0) {
                            activeForm.imageUrl
                        } else {
                            "${activeForm.imageUrl}?retry=$imageRetryKey"
                        }
                    SubcomposeAsyncImage(
                        model = imageUrlWithRetry,
                        contentDescription = displayTitle,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                        contentScale = ContentScale.Fit,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 2.5.dp,
                                )
                            }
                        },
                        error = {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Không tải được ảnh",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = { imageRetryKey++ },
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = " Thử lại",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        },
                    )
                }

                // Huy hiệu Hệ (Type badges)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    activeForm.types.forEach { rawType ->
                        val badgeStyle = PokemonTypeColors.getBadgeStyle(rawType)
                        val typeLabel = PokemonLookupHelper.formatTypeLabel(rawType)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeStyle.backgroundColor,
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = badgeStyle.contentColor,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                // Bộ nút chuyển hình thái (chỉ hiển thị khi có > 1 hình thái)
                if (forms.size > 1) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                    Text(
                        text = "Hình thái (${forms.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        forms.forEachIndexed { idx, form ->
                            FilterChip(
                                selected = idx == currentFormIndex,
                                onClick = { currentFormIndex = idx },
                                label = {
                                    Text(
                                        text = PokemonLookupHelper.formatFormButtonLabel(entry, form, idx, currentLang),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
