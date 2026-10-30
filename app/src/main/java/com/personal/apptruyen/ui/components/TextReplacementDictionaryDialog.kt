package com.personal.apptruyen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.apptruyen.util.TextReplacementItem
import kotlinx.collections.immutable.ImmutableList

private const val MAX_PREVIEW_ITEMS = 3

/**
 * Khối tóm tắt danh sách thay chữ gọn gàng cho SettingsScreen và ReaderSettingsPanel.
 * Cho phép xem nhanh tối đa 3 quy tắc đầu tiên, mở hộp thoại "Từ điển" toàn màn hình
 * hoặc mở nhanh hộp thoại "Thông tin" để thêm từ mới.
 */
@Composable
fun TextReplacementSummarySection(
    items: ImmutableList<TextReplacementItem>,
    isGlobal: Boolean,
    onAdd: (from: String, to: String, caseSensitive: Boolean) -> Unit,
    onUpdate: (index: Int, expected: TextReplacementItem, from: String, to: String, caseSensitive: Boolean) -> Unit,
    onRemove: (index: Int, expected: TextReplacementItem) -> Unit,
    modifier: Modifier = Modifier,
    onSendToGlobal: ((from: String, to: String, caseSensitive: Boolean) -> Unit)? = null,
) {
    var showDictionaryDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf<IndexedValue<TextReplacementItem>?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (items.isEmpty()) {
            Text(
                text = "Chưa có từ tùy chỉnh nào",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ReplacementPreviewCard(
                items = items,
                onClickItem = { indexedItem -> editingTarget = indexedItem },
                onOpenDictionary = { showDictionaryDialog = true },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { showDictionaryDialog = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.FindReplace,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Từ điển (${items.size})",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Thêm từ",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (showDictionaryDialog) {
        TextReplacementDictionaryDialog(
            items = items,
            isGlobal = isGlobal,
            onAdd = onAdd,
            onUpdate = onUpdate,
            onRemove = onRemove,
            onSendToGlobal = onSendToGlobal,
            onDismiss = { showDictionaryDialog = false },
        )
    }

    if (showAddDialog) {
        TextReplacementEditDialog(
            initialItem = null,
            isGlobal = isGlobal,
            onSave = { from, to, caseSensitive ->
                onAdd(from, to, caseSensitive)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    editingTarget?.let { target ->
        TextReplacementEditDialog(
            initialItem = target.value,
            isGlobal = isGlobal,
            onSave = { from, to, caseSensitive ->
                onUpdate(target.index, target.value, from, to, caseSensitive)
                editingTarget = null
            },
            onDismiss = { editingTarget = null },
        )
    }
}

@Composable
private fun ReplacementPreviewCard(
    items: ImmutableList<TextReplacementItem>,
    onClickItem: (IndexedValue<TextReplacementItem>) -> Unit,
    onOpenDictionary: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.take(MAX_PREVIEW_ITEMS).forEachIndexed { index, item ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onClickItem(IndexedValue(index, item)) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "\"${item.from}\" → \"${item.to}\"",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.caseSensitive) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                text = "Hoa/thường",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            if (items.size > MAX_PREVIEW_ITEMS) {
                Text(
                    text = "Xem tất cả ${items.size} từ trong Từ điển…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenDictionary)
                            .padding(top = 2.dp, bottom = 2.dp, start = 4.dp),
                )
            }
        }
    }
}

/**
 * Hộp thoại toàn màn hình "Từ điển" quản lý danh sách từ thay thế:
 * có thanh tìm kiếm từ khóa và bảng 4 cột (Tìm | Thay thế | Hoa thường | Xóa).
 */
@Composable
fun TextReplacementDictionaryDialog(
    items: ImmutableList<TextReplacementItem>,
    isGlobal: Boolean,
    onAdd: (from: String, to: String, caseSensitive: Boolean) -> Unit,
    onUpdate: (index: Int, expected: TextReplacementItem, from: String, to: String, caseSensitive: Boolean) -> Unit,
    onRemove: (index: Int, expected: TextReplacementItem) -> Unit,
    onDismiss: () -> Unit,
    onSendToGlobal: ((from: String, to: String, caseSensitive: Boolean) -> Unit)? = null,
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf<IndexedValue<TextReplacementItem>?>(null) }

    val filteredItems =
        remember(items, searchQuery) {
            val trimmed = searchQuery.trim()
            if (trimmed.isEmpty()) {
                items.withIndex().toList()
            } else {
                items.withIndex().filter { (_, item) ->
                    item.from.contains(trimmed, ignoreCase = true) ||
                        item.to.contains(trimmed, ignoreCase = true)
                }
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DictionaryTopBar(
                    itemCount = items.size,
                    isGlobal = isGlobal,
                    onBack = onDismiss,
                    onAddClick = { showAddDialog = true },
                )

                DictionarySearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )

                DictionaryTable(
                    filteredItems = filteredItems,
                    totalCount = items.size,
                    showSendToGlobalColumn = onSendToGlobal != null,
                    onRowClick = { indexedItem -> editingTarget = indexedItem },
                    onToggleCaseSensitive = { indexedItem, checked ->
                        val item = indexedItem.value
                        onUpdate(indexedItem.index, item, item.from, item.to, checked)
                    },
                    onSendToGlobal = { item ->
                        onSendToGlobal?.invoke(item.from, item.to, item.caseSensitive)
                    },
                    onRemove = { indexedItem ->
                        onRemove(indexedItem.index, indexedItem.value)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showAddDialog) {
        TextReplacementEditDialog(
            initialItem = null,
            isGlobal = isGlobal,
            onSave = { from, to, caseSensitive ->
                onAdd(from, to, caseSensitive)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    editingTarget?.let { target ->
        TextReplacementEditDialog(
            initialItem = target.value,
            isGlobal = isGlobal,
            onSave = { from, to, caseSensitive ->
                onUpdate(target.index, target.value, from, to, caseSensitive)
                editingTarget = null
            },
            onDismiss = { editingTarget = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DictionaryTopBar(
    itemCount: Int,
    isGlobal: Boolean,
    onBack: () -> Unit,
    onAddClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Từ điển",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text =
                        if (isGlobal) {
                            "Áp dụng cho tất cả truyện ($itemCount từ)"
                        } else {
                            "Áp dụng riêng cho truyện này ($itemCount từ)"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                )
            }
        },
        actions = {
            IconButton(onClick = onAddClick) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Thêm từ thay thế",
                )
            }
        },
    )
}

@Composable
private fun DictionarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Nhập từ khóa") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Tìm kiếm",
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Xóa từ khóa",
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DictionaryTable(
    filteredItems: List<IndexedValue<TextReplacementItem>>,
    totalCount: Int,
    showSendToGlobalColumn: Boolean,
    onRowClick: (IndexedValue<TextReplacementItem>) -> Unit,
    onToggleCaseSensitive: (IndexedValue<TextReplacementItem>, Boolean) -> Unit,
    onSendToGlobal: (TextReplacementItem) -> Unit,
    onRemove: (IndexedValue<TextReplacementItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        DictionaryTableHeader(
            showSendToGlobalColumn = showSendToGlobalColumn,
            borderColor = borderColor,
        )

        if (filteredItems.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        if (totalCount == 0) {
                            "Chưa có từ thay thế nào. Bấm dấu + ở góc trên để thêm."
                        } else {
                            "Không tìm thấy từ phù hợp với từ khóa."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    items = filteredItems,
                    key = { (originalIndex, item) ->
                        "$originalIndex:${item.from}:${item.to}:${item.caseSensitive}"
                    },
                ) { indexedItem ->
                    DictionaryTableRow(
                        indexedItem = indexedItem,
                        showSendToGlobalColumn = showSendToGlobalColumn,
                        borderColor = borderColor,
                        onRowClick = { onRowClick(indexedItem) },
                        onToggleCaseSensitive = { checked ->
                            onToggleCaseSensitive(indexedItem, checked)
                        },
                        onSendToGlobal = { onSendToGlobal(indexedItem.value) },
                        onRemove = { onRemove(indexedItem) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionaryTableHeader(
    showSendToGlobalColumn: Boolean,
    borderColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, borderColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Tìm",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp, horizontal = 8.dp),
        )
        VerticalDivider(color = borderColor)
        Text(
            text = "Thay thế",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp, horizontal = 8.dp),
        )
        VerticalDivider(color = borderColor)
        Text(
            text = "Hoa\nthường",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .width(64.dp)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
        )
        if (showSendToGlobalColumn) {
            VerticalDivider(color = borderColor)
            Text(
                text = "Chung",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .width(52.dp)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
            )
        }
        VerticalDivider(color = borderColor)
        Text(
            text = "Xóa",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .width(52.dp)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
        )
    }
}

@Composable
private fun DictionaryTableRow(
    indexedItem: IndexedValue<TextReplacementItem>,
    showSendToGlobalColumn: Boolean,
    borderColor: androidx.compose.ui.graphics.Color,
    onRowClick: () -> Unit,
    onToggleCaseSensitive: (Boolean) -> Unit,
    onSendToGlobal: () -> Unit,
    onRemove: () -> Unit,
) {
    val item = indexedItem.value
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .border(width = 0.5.dp, color = borderColor)
                .clickable(onClick = onRowClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.from,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        VerticalDivider(color = borderColor)
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.to,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        VerticalDivider(color = borderColor)
        Box(
            modifier =
                Modifier
                    .width(64.dp)
                    .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Checkbox(
                checked = item.caseSensitive,
                onCheckedChange = onToggleCaseSensitive,
            )
        }
        if (showSendToGlobalColumn) {
            VerticalDivider(color = borderColor)
            Box(
                modifier =
                    Modifier
                        .width(52.dp)
                        .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onSendToGlobal) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Gửi sang cài đặt tổng",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        VerticalDivider(color = borderColor)
        Box(
            modifier =
                Modifier
                    .width(52.dp)
                    .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Xóa",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Hộp thoại "Thông tin" để thêm mới hoặc chỉnh sửa một mục thay chữ,
 * có câu xem trước trực quan và tùy chọn "Phân biệt hoa thường".
 */
@Composable
fun TextReplacementEditDialog(
    initialItem: TextReplacementItem?,
    isGlobal: Boolean,
    onSave: (from: String, to: String, caseSensitive: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var fromText by remember(initialItem) { mutableStateOf(initialItem?.from ?: "") }
    var toText by remember(initialItem) { mutableStateOf(initialItem?.to ?: "") }
    var caseSensitive by remember(initialItem) {
        mutableStateOf(initialItem?.caseSensitive ?: false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Thông tin",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            EditDialogBody(
                fromText = fromText,
                toText = toText,
                caseSensitive = caseSensitive,
                isGlobal = isGlobal,
                onFromChange = { fromText = it },
                onToChange = { toText = it },
                onCaseSensitiveChange = { caseSensitive = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fromText.isNotBlank()) {
                        onSave(fromText, toText, caseSensitive)
                    }
                },
                enabled = fromText.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Lưu lại")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Đóng lại")
            }
        },
    )
}

@Composable
private fun EditDialogBody(
    fromText: String,
    toText: String,
    caseSensitive: Boolean,
    isGlobal: Boolean,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onCaseSensitiveChange: (Boolean) -> Unit,
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val previewFrom = fromText.ifBlank { "..." }
    val previewTo = toText.ifEmpty { "(xóa bỏ)" }
    val scopeLabel = if (isGlobal) "trong tất cả các bộ truyện" else "trong bộ truyện này"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text =
                buildAnnotatedString {
                    append("Tất cả những từ: ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
                        append(previewFrom)
                    }
                    append(" sẽ được thay bằng ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
                        append(previewTo)
                    }
                    append(" ")
                    append(scopeLabel)
                },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = fromText,
            onValueChange = onFromChange,
            label = { Text("Từ cần thay thế") },
            placeholder = { Text("vd: Mây tía") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = toText,
            onValueChange = onToChange,
            label = { Text("Từ thay thế") },
            placeholder = { Text("vd: Tử Vân") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCaseSensitiveChange(!caseSensitive) }
                    .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Phân biệt hoa thường",
                style = MaterialTheme.typography.bodyMedium,
            )
            Checkbox(
                checked = caseSensitive,
                onCheckedChange = onCaseSensitiveChange,
            )
        }
    }
}
