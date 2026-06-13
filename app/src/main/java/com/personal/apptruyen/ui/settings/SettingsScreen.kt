package com.personal.apptruyen.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.personal.apptruyen.BuildConfig
import com.personal.apptruyen.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToStats: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val backupProgressText by viewModel.backupProgressText.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // SAF launcher for creating backup file
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip"),
        ) { uri ->
            uri?.let { viewModel.exportBackup(it, context) }
        }

    // SAF launcher for opening backup file
    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let {
                pendingImportUri = it
                showImportConfirmDialog = true
            }
        }

    // Show snackbar on backup state changes
    LaunchedEffect(backupState) {
        when (val s = backupState) {
            is BackupState.Success -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetBackupState()
            }
            is BackupState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetBackupState()
            }
            else -> {}
        }
    }

    // Import confirmation dialog
    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                pendingImportUri = null
            },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text("Xác nhận khôi phục") },
            text = {
                Text("Khôi phục sẽ ghi đè dữ liệu hiện tại. Bạn có muốn tiếp tục?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirmDialog = false
                    pendingImportUri?.let { viewModel.importBackup(it, context) }
                    pendingImportUri = null
                }) {
                    Text("Khôi phục", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirmDialog = false
                    pendingImportUri = null
                }) {
                    Text("Hủy")
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Cài Đặt",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "Tùy chỉnh trải nghiệm đọc ⚙️",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Giao diện section ──
            item {
                SettingsSection(title = "Giao diện", icon = Icons.Default.Palette) {
                    SettingsAppThemeItem(
                        currentMode = state.appThemeMode,
                        onModeChange = { viewModel.setAppThemeMode(it) },
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )

                    // Dynamic Color (Material You)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Màu động (Material You)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Dùng bảng màu từ hình nền (Android 12+)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) },
                        )
                    }
                }
            }

            // ── Đọc truyện section ──
            item {
                SettingsSection(title = "Đọc truyện", icon = Icons.Default.MenuBook) {
                    // Font size
                    SettingsSliderItem(
                        title = "Cỡ chữ mặc định",
                        value = state.fontSize,
                        valueLabel = "${state.fontSize.toInt()} sp",
                        onValueChange = { viewModel.setFontSize(it) },
                        valueRange = 12f..32f,
                        steps = 9,
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )

                    // Reader theme
                    SettingsThemeItem(
                        title = "Giao diện đọc",
                        currentTheme = state.theme,
                        onThemeChange = { viewModel.setTheme(it) },
                    )
                }
            }

            // ── Thay chữ section ──
            item {
                SettingsSection(title = "Thay chữ tự động", icon = Icons.Default.FindReplace) {
                    // Toggle on/off
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bật thay chữ", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Sửa từ bị biến dạng (vd: ch.ết → chết)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.textReplacementEnabled,
                            onCheckedChange = { viewModel.setTextReplacement(it) },
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )

                    // Custom replacements list
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "Danh sách tùy chỉnh",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (state.customReplacements.isEmpty()) {
                            Text(
                                "Chưa có từ tùy chỉnh",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.customReplacements.forEachIndexed { index, (from, to) ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "\"$from\" → \"$to\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeCustomReplacement(index) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Xóa",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Add new replacement
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
                                            placeholder = { Text("vd: ch.ết") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        OutlinedTextField(
                                            value = toText,
                                            onValueChange = { toText = it },
                                            label = { Text("Thay bằng") },
                                            placeholder = { Text("vd: chết") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            if (fromText.isNotBlank()) {
                                                viewModel.addCustomReplacement(fromText, toText)
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
                    }
                }
            }

            // ── Quản lý nguồn ──
            item {
                SettingsSection(title = "Quản lý nguồn", icon = Icons.Default.Language) {
                    SettingsViewModel.ALL_SOURCES.forEach { sourceId ->
                        val label = SettingsViewModel.SOURCE_LABELS[sourceId] ?: sourceId
                        val isEnabled = state.enabledSources.contains(sourceId)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { viewModel.toggleSource(sourceId) },
                                enabled = isEnabled || state.enabledSources.size < SettingsViewModel.ALL_SOURCES.size,
                            )
                        }
                    }
                    Text(
                        "Tắt nguồn không dùng để tăng tốc tìm kiếm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            // ── Tải trước chương ──
            item {
                SettingsSection(title = "Tải trước chương", icon = Icons.Default.CloudDownload) {
                    Text(
                        "Tự động tải trước các chương tiếp theo vào bộ nhớ tạm khi đang đọc. Đóng app sẽ mất cache.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(0, 3, 5, 10).forEach { count ->
                            val label = if (count == 0) "Tắt" else "$count chương"
                            FilterChip(
                                selected = state.prefetchChapters == count,
                                onClick = { viewModel.setPrefetchChapters(count) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── Thống kê đọc section ──
            item {
                Card(
                    onClick = onNavigateToStats,
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Column {
                                Text(
                                    "Thống kê đọc",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Xem thời gian đọc, streak, biểu đồ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Giọng đọc section ──
            item {
                SettingsSection(title = "Giọng đọc (TTS)", icon = Icons.Default.RecordVoiceOver) {
                    SettingsSliderItem(
                        title = "Tốc độ đọc mặc định",
                        value = state.ttsSpeed,
                        valueLabel = "${String.format("%.2f", state.ttsSpeed)}x",
                        onValueChange = { viewModel.setTtsSpeed(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 29,
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )

                    // Pitch slider
                    SettingsSliderItem(
                        title = "Cao độ giọng",
                        value = state.ttsPitch,
                        valueLabel = String.format("%.1fx", state.ttsPitch),
                        onValueChange = { viewModel.setTtsPitch(it) },
                        valueRange = 0.5f..2.0f,
                        steps = 14,
                    )
                }
            }

            // ── Sao lưu & Khôi phục ──
            item {
                SettingsSection(title = "Sao lưu & Khôi phục", icon = Icons.Default.CloudUpload) {
                    Text(
                        "Xuất/nhập toàn bộ dữ liệu (truyện, tiến trình, cài đặt) ra file ZIP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )

                    // Progress indicator
                    AnimatedVisibility(
                        visible = backupState is BackupState.Exporting || backupState is BackupState.Importing,
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                backupProgressText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val isProcessing = backupState is BackupState.Exporting || backupState is BackupState.Importing

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Export button
                        OutlinedButton(
                            onClick = {
                                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val date = dateFormat.format(Date())
                                exportLauncher.launch("apptruyen_backup_$date.zip")
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sao lưu")
                        }

                        // Import button
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                            },
                            enabled = !isProcessing,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Khôi phục")
                        }
                    }
                }
            }

            // ── Nhật ký lỗi ──
            item {
                val crashLogs by viewModel.crashLogs.collectAsState()
                var selectedCrashLog by remember {
                    mutableStateOf<com.personal.apptruyen.util.CrashHandler.CrashLog?>(
                        null,
                    )
                }
                var showClearConfirm by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    viewModel.loadCrashLogs(context)
                }

                SettingsSection(title = "Nhật ký lỗi", icon = Icons.Default.BugReport) {
                    if (crashLogs.isEmpty()) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                "Không có lỗi nào được ghi nhận",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text(
                                    "${crashLogs.size} lỗi",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { showClearConfirm = true }) {
                                Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Xóa tất cả", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        crashLogs.take(5).forEach { log ->
                            val dateStr =
                                SimpleDateFormat(
                                    "dd/MM HH:mm:ss",
                                    Locale.getDefault(),
                                ).format(Date(log.timestamp))
                            val firstLine =
                                log.content
                                    .lines()
                                    .firstOrNull { it.contains("Exception") || it.contains("Error") }
                                    ?: log.fileName

                            Surface(
                                onClick = { selectedCrashLog = log },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 3.dp),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        dateStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        firstLine.take(80),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        if (crashLogs.size > 5) {
                            Text(
                                "... và ${crashLogs.size - 5} lỗi khác",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                if (showClearConfirm) {
                    AlertDialog(
                        onDismissRequest = { showClearConfirm = false },
                        title = { Text("Xóa nhật ký lỗi") },
                        text = { Text("Xóa tất cả ${crashLogs.size} nhật ký lỗi?") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.clearCrashLogs(context)
                                showClearConfirm = false
                            }) { Text("Xóa", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearConfirm = false }) { Text("Hủy") }
                        },
                    )
                }

                if (selectedCrashLog != null) {
                    AlertDialog(
                        onDismissRequest = { selectedCrashLog = null },
                        title = {
                            Text(
                                "Lỗi lúc ${SimpleDateFormat(
                                    "dd/MM/yyyy HH:mm",
                                    Locale.getDefault(),
                                ).format(Date(selectedCrashLog!!.timestamp))}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        },
                        text = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.heightIn(max = 400.dp),
                            ) {
                                val scrollState = rememberScrollState()
                                Text(
                                    selectedCrashLog!!.content,
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 10.sp,
                                        ),
                                    modifier =
                                        Modifier
                                            .padding(8.dp)
                                            .verticalScroll(scrollState),
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val clipboard =
                                    context.getSystemService(
                                        android.content.Context.CLIPBOARD_SERVICE,
                                    ) as android.content.ClipboardManager
                                val clip =
                                    android.content.ClipData.newPlainText(
                                        "crash_log",
                                        selectedCrashLog!!.content,
                                    )
                                clipboard.setPrimaryClip(clip)
                                selectedCrashLog = null
                            }) { Text("Sao chép") }
                        },
                        dismissButton = {
                            TextButton(onClick = { selectedCrashLog = null }) { Text("Đóng") }
                        },
                    )
                }
            }

            // ── Thông tin ứng dụng ──
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(GradientAmberStart, GradientAmberEnd),
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                ).padding(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White,
                            )
                            Text(
                                "APP Truyện",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = "Phiên bản ${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                )
                            }

                            Text(
                                "Ứng dụng đọc truyện và nghe truyện online + offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                            Text(
                                "Đa nguồn: TruyenCom • TàngThưViện • SsTruyện • TruyenFull",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // Section header
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            content()

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsSliderItem(
    title: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SettingsThemeItem(
    title: String,
    currentTheme: ReaderThemeOption,
    onThemeChange: (ReaderThemeOption) -> Unit,
) {
    data class ThemePreview(
        val option: ReaderThemeOption,
        val bg: Color,
        val text: Color,
    )
    val themes =
        listOf(
            ThemePreview(ReaderThemeOption.LIGHT, Color.White, Color(0xFF1C1B1F)),
            ThemePreview(ReaderThemeOption.DARK, Color(0xFF1C1B1F), Color(0xFFE6E1E5)),
            ThemePreview(ReaderThemeOption.SEPIA, SepiaBackground, SepiaText),
        )

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            themes.forEach { (option, previewBg, previewText) ->
                val isSelected = currentTheme == option

                Surface(
                    onClick = { onThemeChange(option) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = previewBg,
                    border =
                        if (isSelected) {
                            ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp)
                        } else {
                            null
                        },
                    tonalElevation = if (isSelected) 4.dp else 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Aa",
                                style = MaterialTheme.typography.titleSmall,
                                color = previewText,
                            )
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = previewText.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsAppThemeItem(
    currentMode: AppThemeMode,
    onModeChange: (AppThemeMode) -> Unit,
) {
    data class ModeInfo(
        val mode: AppThemeMode,
        val icon: ImageVector,
    )
    val modes =
        listOf(
            ModeInfo(AppThemeMode.SYSTEM, Icons.Default.BrightnessAuto),
            ModeInfo(AppThemeMode.LIGHT, Icons.Default.LightMode),
            ModeInfo(AppThemeMode.DARK, Icons.Default.DarkMode),
        )

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text("Chế độ giao diện", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            modes.forEach { (mode, icon) ->
                FilterChip(
                    selected = currentMode == mode,
                    onClick = { onModeChange(mode) },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
