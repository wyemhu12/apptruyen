package com.personal.apptruyen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.personal.apptruyen.ui.reader.ReaderViewModel

/**
 * Centralized reader theme colors — tránh duplicate hardcoded colors
 * giữa ReaderScreen, SettingsScreen, ChapterListPanel, TtsControlBar, etc.
 */
@Immutable
data class ReaderThemeColors(
    val backgroundColor: Color,
    val textColor: Color,
    val accentColor: Color,
    val panelBackground: Color,
    val panelContentColor: Color,
    val highlightBackground: Color,
    val highlightText: Color,
)

/**
 * Remember reader colors based on current theme.
 * Dùng @Composable vì cần MaterialTheme.colorScheme cho Light theme.
 */
@Composable
fun rememberReaderThemeColors(theme: ReaderViewModel.ReaderTheme): ReaderThemeColors {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    return remember(theme, primaryColor, surfaceColor) {
        when (theme) {
            ReaderViewModel.ReaderTheme.LIGHT ->
                ReaderThemeColors(
                    backgroundColor = Color.White,
                    textColor = Color(0xFF1C1B1F),
                    accentColor = primaryColor,
                    panelBackground = surfaceColor,
                    panelContentColor = onSurfaceColor,
                    highlightBackground = primaryContainer,
                    highlightText = primaryColor,
                )
            ReaderViewModel.ReaderTheme.DARK ->
                ReaderThemeColors(
                    backgroundColor = Color(0xFF1C1B1F),
                    textColor = Color(0xFFE6E1E5),
                    accentColor = Color(0xFF90CAF9),
                    panelBackground = Color(0xFF252525),
                    panelContentColor = Color(0xFFF0ECF0),
                    highlightBackground = Color(0xFF3A3000),
                    highlightText = Color(0xFFFFD54F),
                )
            ReaderViewModel.ReaderTheme.SEPIA ->
                ReaderThemeColors(
                    backgroundColor = SepiaBackground,
                    textColor = SepiaText,
                    accentColor = Color(0xFF8D6E3F),
                    panelBackground = SepiaBackground,
                    panelContentColor = SepiaText,
                    highlightBackground = Color(0xFFD4B896),
                    highlightText = Color(0xFF5D3A1A),
                )
        }
    }
}
