package com.personal.apptruyen.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.apptruyen.ui.theme.*

/**
 * Empty state premium — animated gradient background, pulsing icon, staggered text
 */
@Composable
fun AnimatedEmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { visible = true }

    // Pulsing icon animation
    val infiniteTransition = rememberInfiniteTransition(label = "emptyPulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "iconScale",
    )

    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "iconAlpha",
    )

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Animated icon with glow background
        AnimatedVisibility(
            visible = visible,
            enter =
                scaleIn(
                    initialScale = 0.3f,
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(600)),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Glow circle behind icon
                Box(
                    modifier =
                        Modifier
                            .size((80 * iconScale).dp)
                            .background(
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = iconAlpha * 0.3f),
                                            Color.Transparent,
                                        ),
                                ),
                                shape = RoundedCornerShape(50),
                            ),
                )
                Icon(
                    icon,
                    contentDescription = message,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Staggered text
        AnimatedVisibility(
            visible = visible,
            enter =
                slideInVertically(
                    initialOffsetY = { 40 },
                    animationSpec = tween(500, delayMillis = 200),
                ) + fadeIn(animationSpec = tween(500, delayMillis = 200)),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = visible,
                enter =
                    slideInVertically(
                        initialOffsetY = { 30 },
                        animationSpec = tween(500, delayMillis = 400),
                    ) + fadeIn(animationSpec = tween(500, delayMillis = 400)),
            ) {
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
                ) {
                    Text(actionText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
