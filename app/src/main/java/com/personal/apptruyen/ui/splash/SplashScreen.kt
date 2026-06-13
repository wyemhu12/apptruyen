package com.personal.apptruyen.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.apptruyen.BuildConfig
import com.personal.apptruyen.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    // Animation states
    var startAnimation by remember { mutableStateOf(false) }

    val iconScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.3f,
        animationSpec =
            tween(
                durationMillis = 800,
                easing = FastOutSlowInEasing,
            ),
        label = "iconScale",
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec =
            tween(
                durationMillis = 600,
                easing = FastOutSlowInEasing,
            ),
        label = "iconAlpha",
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec =
            tween(
                durationMillis = 600,
                delayMillis = 400,
                easing = FastOutSlowInEasing,
            ),
        label = "textAlpha",
    )

    val subtitleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec =
            tween(
                durationMillis = 600,
                delayMillis = 700,
                easing = FastOutSlowInEasing,
            ),
        label = "subtitleAlpha",
    )

    // Pulsing glow effect for icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulseScale",
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2500)
        onSplashFinished()
    }

    // Elegant dark background with warm accent
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color(0xFF0B0F16), // Deep charcoal
                                Color(0xFF141B2D), // Dark blue-grey
                            ),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        // Soft warm glow behind icon — large radius for smooth fade
        Box(
            modifier =
                Modifier
                    .size(360.dp)
                    .alpha(iconAlpha * 0.18f)
                    .scale(iconScale * pulseScale)
                    .background(
                        Brush.radialGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to Color(0xFFC17900),
                                    0.25f to Color(0xFFC17900).copy(alpha = 0.6f),
                                    0.5f to Color(0xFFC17900).copy(alpha = 0.2f),
                                    0.75f to Color(0xFFC17900).copy(alpha = 0.05f),
                                    1.0f to Color.Transparent,
                                ),
                        ),
                    ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App icon with rounded corners and shadow
            Box(
                modifier =
                    Modifier
                        .size(120.dp)
                        .scale(iconScale * pulseScale)
                        .alpha(iconAlpha)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = Color(0xFFC17900).copy(alpha = 0.4f),
                            spotColor = Color(0xFFC17900).copy(alpha = 0.3f),
                        ).clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFC17900)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_splash_logo),
                    contentDescription = "App Truyện",
                    modifier = Modifier.size(96.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App name
            Text(
                text = "App Truyện",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.alpha(textAlpha),
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Text(
                text = "Đọc & Nghe truyện mọi lúc",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE0C0A0),
                modifier = Modifier.alpha(subtitleAlpha),
            )
        }

        // Version at bottom — dynamic from BuildConfig
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.45f),
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .alpha(subtitleAlpha),
        )
    }
}
