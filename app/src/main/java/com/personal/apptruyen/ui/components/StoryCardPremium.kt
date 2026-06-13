package com.personal.apptruyen.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.personal.apptruyen.data.model.Story
import com.personal.apptruyen.ui.theme.*

/**
 * Premium story card — cover image + gradient scrim + text overlay.
 * Dùng cho HomeScreen, ExploreScreen, SearchScreen, DownloadsScreen.
 */
@Composable
fun StoryCardPremium(
    story: Story,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    progress: Float = 0f,
    subtitle: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(150),
        label = "cardScale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 2f else 6f,
        animationSpec = tween(150),
        label = "cardElevation",
    )

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.shadow(
                    elevation = elevation.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Cover image with gradient overlay
            Box(
                modifier =
                    Modifier
                        .size(72.dp, 100.dp)
                        .clip(RoundedCornerShape(12.dp)),
            ) {
                if (story.coverImageUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(story.coverImageUrl)
                                .build(),
                        contentDescription = story.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(shimmerBrush()),
                            )
                        },
                        error = {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(GradientAmberStart, GradientAmberEnd),
                                            ),
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        },
                    )
                    // Subtle gradient scrim at bottom
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.3f),
                                            ),
                                    ),
                                ),
                    )
                } else {
                    // Fallback — gradient with icon
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(GradientAmberStart, GradientAmberEnd),
                                    ),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                if (showProgress && progress > 0f) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            // Text content
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 100.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Title
                    Text(
                        text = story.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    // Author
                    if (story.author.isNotBlank()) {
                        Text(
                            text = story.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Subtitle (custom — e.g. "3 chương đã tải")
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                // Bottom row — source badge + chapter count + genres
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Source badge
                    if (story.sourceId.isNotBlank()) {
                        val label =
                            when (story.sourceId) {
                                "truyencom" -> "TC"
                                "tangthuvien" -> "TTV"
                                else -> story.sourceId.take(3).uppercase()
                            }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color =
                                if (story.sourceId == "tangthuvien") {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            contentColor =
                                if (story.sourceId == "tangthuvien") {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    if (story.totalChapters > 0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Text(
                                text = "${story.totalChapters} chương",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }

                    if (story.genres.isNotEmpty()) {
                        Text(
                            text = story.genres.take(2).joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Grid story card — compact, cover image dominant.
 * Dùng cho ExploreScreen grid layout.
 */
@Composable
fun StoryCardGrid(
    story: Story,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
    progress: Float = 0f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(150),
        label = "gridCardScale",
    )

    Card(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                ),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ),
        ) {
            // Cover image — dominant
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            ) {
                if (story.coverImageUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(story.coverImageUrl)
                                .build(),
                        contentDescription = story.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(shimmerBrush()),
                            )
                        },
                        error = {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(GradientAmberStart, GradientAmberEnd),
                                            ),
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.AutoStories,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(40.dp),
                                )
                            }
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
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                // Source badge — top start
                if (story.sourceId.isNotBlank()) {
                    val label =
                        when (story.sourceId) {
                            "truyencom" -> "TC"
                            "tangthuvien" -> "TTV"
                            else -> story.sourceId.take(3).uppercase()
                        }
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                        shape = RoundedCornerShape(6.dp),
                        color =
                            if (story.sourceId == "tangthuvien") {
                                Color(0xFF2196F3).copy(alpha = 0.85f)
                            } else {
                                Color(0xFFFF9800).copy(alpha = 0.85f)
                            },
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                // Chapter badge
                if (story.totalChapters > 0) {
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = "${story.totalChapters} ch",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // Text content
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (story.author.isNotBlank()) {
                    Text(
                        text = story.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
