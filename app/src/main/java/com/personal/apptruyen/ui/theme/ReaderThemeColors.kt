package com.personal.apptruyen.ui.theme

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
 * Độc lập với Dark/Light Mode của hệ thống để đảm bảo độ tương phản WCAG AA (> 4.5:1) trên cả 3 nền đọc.
 */
@Composable
fun rememberReaderThemeColors(theme: ReaderViewModel.ReaderTheme): ReaderThemeColors =
    remember(theme) {
        when (theme) {
            ReaderViewModel.ReaderTheme.LIGHT ->
                ReaderThemeColors(
                    backgroundColor = BackgroundLight,
                    textColor = Color(0xFF1A1A1A),
                    accentColor = Color(0xFF8B5000),
                    panelBackground = Surface1Light,
                    panelContentColor = OnSurfaceLight,
                    highlightBackground = Color(0xFFFFF3CD),
                    highlightText = Color(0xFF5D3A00),
                )
            ReaderViewModel.ReaderTheme.DARK ->
                ReaderThemeColors(
                    backgroundColor = Color(0xFF1C1B1F),
                    textColor = Color(0xFFF0ECF0),
                    accentColor = PrimaryDark,
                    panelBackground = Color(0xFF252525),
                    panelContentColor = Color(0xFFF0ECF0),
                    highlightBackground = Color(0xFF3A3000),
                    highlightText = Color(0xFFFFD54F),
                )
            ReaderViewModel.ReaderTheme.SEPIA ->
                ReaderThemeColors(
                    backgroundColor = SepiaBackground,
                    textColor = SepiaText,
                    accentColor = Color(0xFF7A4510),
                    panelBackground = SepiaBackground,
                    panelContentColor = SepiaText,
                    highlightBackground = Color(0xFFD0B286),
                    highlightText = SepiaText,
                )
        }
    }
