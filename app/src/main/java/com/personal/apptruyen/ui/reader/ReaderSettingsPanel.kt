package com.personal.apptruyen.ui.reader

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.apptruyen.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsPanel(
    fontSize: Float,
    theme: ReaderViewModel.ReaderTheme,
    copyMode: Boolean,
    textReplacementEnabled: Boolean,
    autoFormatEnabled: Boolean,
    lineSpacing: Float,
    brightness: Float,
    fontFamily: ReaderViewModel.ReaderFontFamily,
    // Per-story text replacement
    storyReplacementsEnabled: Boolean,
    storyReplacements: List<Pair<String, String>>,
    // Auto-scroll
    autoScrollEnabled: Boolean,
    autoScrollSpeed: Int,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderViewModel.ReaderTheme) -> Unit,
    onToggleCopyMode: () -> Unit,
    onToggleTextReplacement: () -> Unit,
    onToggleAutoFormat: () -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onFontFamilyChange: (ReaderViewModel.ReaderFontFamily) -> Unit,
    onToggleStoryReplacements: () -> Unit,
    onAddStoryReplacement: (String, String) -> Unit,
    onRemoveStoryReplacement: (Int) -> Unit,
    onSendToGlobal: (String, String) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onSetAutoScrollSpeed: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Cài đặt đọc",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                FilledTonalIconButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Đóng",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // ═══════ Section 1: Hiển thị ═══════
            Text(
                "HIỂN THỊ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Font family picker
            Column {
                Text("Phông chữ", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReaderViewModel.ReaderFontFamily.entries.forEach { f ->
                        val isSelected = fontFamily == f
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFontFamilyChange(f) },
                            label = {
                                Text(
                                    f.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Font size
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Cỡ chữ", style = MaterialTheme.typography.labelLarge)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${fontSize.toInt()} sp",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = fontSize,
                    onValueChange = onFontSizeChange,
                    valueRange = 12f..32f,
                    steps = 9,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Aa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Aa",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Line Spacing
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Khoảng cách dòng", style = MaterialTheme.typography.labelLarge)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = String.format("%.1fx", lineSpacing),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = lineSpacing,
                    onValueChange = onLineSpacingChange,
                    valueRange = 1.2f..2.2f,
                    steps = 9,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(
                        Icons.Default.FormatLineSpacing,
                        contentDescription = "Chật",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.Default.FormatLineSpacing,
                        contentDescription = "Rộng",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Brightness
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Độ sáng", style = MaterialTheme.typography.labelLarge)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "${(brightness * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value = brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = 0.01f..1f,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(
                        Icons.Default.BrightnessLow,
                        contentDescription = "Tối",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.Default.BrightnessHigh,
                        contentDescription = "Sáng",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Theme selection
            Column {
                Text("Giao diện", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ReaderViewModel.ReaderTheme.entries.forEach { t ->
                        val name =
                            when (t) {
                                ReaderViewModel.ReaderTheme.LIGHT -> "Sáng"
                                ReaderViewModel.ReaderTheme.DARK -> "Tối"
                                ReaderViewModel.ReaderTheme.SEPIA -> "Sepia"
                            }
                        val previewBg =
                            when (t) {
                                ReaderViewModel.ReaderTheme.LIGHT -> Color.White
                                ReaderViewModel.ReaderTheme.DARK -> Color(0xFF1C1B1F)
                                ReaderViewModel.ReaderTheme.SEPIA -> SepiaBackground
                            }
                        val previewText =
                            when (t) {
                                ReaderViewModel.ReaderTheme.LIGHT -> Color(0xFF1C1B1F)
                                ReaderViewModel.ReaderTheme.DARK -> Color(0xFFE6E1E5)
                                ReaderViewModel.ReaderTheme.SEPIA -> SepiaText
                            }
                        val isSelected = theme == t

                        Surface(
                            onClick = { onThemeChange(t) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(64.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = previewBg,
                            border =
                                if (isSelected) {
                                    ButtonDefaults.outlinedButtonBorder.copy(
                                        width = 2.dp,
                                    )
                                } else {
                                    null
                                },
                            tonalElevation = if (isSelected) 4.dp else 0.dp,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Aa",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = previewText,
                                    )
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = previewText.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ═══════ Section 2: Công cụ ═══════
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                "CÔNG CỤ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            // Text replacement toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Thay chữ tự động", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Sửa từ bị biến dạng (vd: ch.ết → chết)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = textReplacementEnabled,
                    onCheckedChange = { onToggleTextReplacement() },
                )
            }

            // Auto format toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tự động format văn bản", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Sửa khoảng trắng dấu câu, tách dòng thoại",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoFormatEnabled,
                    onCheckedChange = { onToggleAutoFormat() },
                )
            }

            // Copy mode toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chế độ Copy", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Bật để chọn và sao chép văn bản",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = copyMode,
                    onCheckedChange = { onToggleCopyMode() },
                )
            }

            // ═══════ Tự cuộn ═══════
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                "TỰ CUỘN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bật tự cuộn", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Tự động cuộn trang khi đọc",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = autoScrollEnabled,
                    onCheckedChange = { onToggleAutoScroll() },
                )
            }

            if (autoScrollEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Tốc độ", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = autoScrollSpeed.toFloat(),
                        onValueChange = { onSetAutoScrollSpeed(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${autoScrollSpeed}x",
                        modifier = Modifier.width(36.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // ═══════ Section 3: Thay chữ riêng cho truyện ═══════
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                "THAY CHỮ RIÊNG CHO TRUYỆN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )

            // Per-story toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bật thay chữ riêng", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Chỉ áp dụng cho truyện này",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = storyReplacementsEnabled,
                    onCheckedChange = { onToggleStoryReplacements() },
                )
            }

            // Per-story replacements list
            if (storyReplacements.isNotEmpty()) {
                storyReplacements.forEachIndexed { index, (from, to) ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "\"$from\" \u2192 \"$to\"",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        // Gửi sang cài đặt tổng
                        IconButton(
                            onClick = { onSendToGlobal(from, to) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = "Gửi sang cài đặt tổng",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        // Xóa
                        IconButton(
                            onClick = { onRemoveStoryReplacement(index) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Xóa",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            } else {
                Text(
                    "Chưa có từ tùy chỉnh nào",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Add button
            var showAddDialog by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Thêm từ thay thế")
            }

            if (showAddDialog) {
                var fromText by remember { mutableStateOf("") }
                var toText by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showAddDialog = false },
                    title = { Text("Thêm từ thay thế") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = fromText,
                                onValueChange = { fromText = it },
                                label = { Text("Từ gốc") },
                                placeholder = { Text("vd: nàn") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = toText,
                                onValueChange = { toText = it },
                                label = { Text("Thay bằng") },
                                placeholder = { Text("vd: nàng") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (fromText.isNotBlank()) {
                                    onAddStoryReplacement(fromText, toText)
                                    showAddDialog = false
                                }
                            },
                        ) {
                            Text("Thêm")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Hủy")
                        }
                    },
                )
            }

            // Info note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Thay chữ tổng (cài đặt app) sẽ được áp dụng trước, sau đó mới đến danh sách riêng của truyện này.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
