package com.personal.apptruyen.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.apptruyen.ui.theme.*

@Composable
fun shimmerBrush(
    targetValue: Float = 1000f,
    colors: List<Color> =
        if (isSystemInDarkTheme()) {
            listOf(
                ShimmerBaseDark,
                ShimmerHighlightDark,
                ShimmerBaseDark,
            )
        } else {
            listOf(
                ShimmerBaseLight,
                ShimmerHighlightLight,
                ShimmerBaseLight,
            )
        },
): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = targetValue,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmerTranslate",
    )

    return Brush.linearGradient(
        colors = colors,
        start = Offset(translateAnim - 300f, translateAnim - 300f),
        end = Offset(translateAnim, translateAnim),
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    height: Dp = 20.dp,
    cornerRadius: Dp = 8.dp,
) {
    Box(
        modifier =
            modifier
                .height(height)
                .clip(RoundedCornerShape(cornerRadius))
                .background(shimmerBrush()),
    )
}

/**
 * Shimmer skeleton cho một story card — dùng khi loading.
 */
@Composable
fun ShimmerStoryCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Cover image placeholder
        Box(
            modifier =
                Modifier
                    .size(72.dp, 100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Title
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.8f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush),
            )
            // Author
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush),
            )
            // Genre tags
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier =
                        Modifier
                            .width(60.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(brush),
                )
                Box(
                    modifier =
                        Modifier
                            .width(50.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(brush),
                )
            }
            // Chapter count
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.3f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush),
            )
        }
    }
}

/**
 * Grid shimmer — 2 columns layout
 */
@Composable
fun ShimmerStoryGrid(
    count: Int = 6,
    modifier: Modifier = Modifier,
) {
    val brush = shimmerBrush()

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count / 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(2) {
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(brush),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(brush),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(brush),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shimmer skeleton cho StoryDetailScreen — hiện khi đang load.
 */
@Composable
fun ShimmerStoryDetail(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
    ) {
        // Hero image placeholder
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(brush),
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Title lines
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush),
            )

            // Author
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.35f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush),
            )

            // Genre chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) {
                    Box(
                        modifier =
                            Modifier
                                .width(70.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(brush),
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(brush),
                )
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(brush),
                )
            }

            // Description
            repeat(3) { idx ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(if (idx == 2) 0.6f else 1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(brush),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Chapter list header
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(brush),
            )

            // Chapter list items
            repeat(6) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(40.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush),
                    )
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(brush),
                    )
                }
            }
        }
    }
}
