package com.personal.apptruyen.ui.stats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatsScreen(
    onBack: () -> Unit,
    viewModel: ReadingStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thống kê đọc") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Today stats
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Timer,
                            title = "Hôm nay",
                            value = formatDuration(state.todayTimeMs),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Book,
                            title = "Chương đọc",
                            value = "${state.todayChapters}",
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                // Streak and total
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.LocalFireDepartment,
                            title = "Streak",
                            value = "${state.streak} ngày",
                            color = Color(0xFFFF6B35),
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.BarChart,
                            title = "7 ngày qua",
                            value = formatDuration(state.totalTimeMs),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                // Weekly bar chart
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                        ) {
                            Text(
                                "Biểu đồ 7 ngày",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            WeeklyBarChart(
                                data = state.weeklyData,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(160.dp),
                            )
                        }
                    }
                }

                // Total chapters
                item {
                    Card(
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
                            Column {
                                Text(
                                    "Tổng chương đọc (7 ngày)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "${state.totalChapters} chương",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Icon(
                                Icons.Default.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    color: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = color.copy(alpha = 0.1f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
private fun WeeklyBarChart(
    data: List<ReadingStatsViewModel.DayStat>,
    modifier: Modifier = Modifier,
) {
    val maxTime = data.maxOfOrNull { it.timeMs }?.coerceAtLeast(1L) ?: 1L
    val barColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        // Bars
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEach { dayStat ->
                val targetFraction = (dayStat.timeMs.toFloat() / maxTime).coerceIn(0.02f, 1f)
                val animatedFraction by animateFloatAsState(
                    targetValue = if (dayStat.timeMs > 0) targetFraction else 0.02f,
                    animationSpec = tween(600),
                    label = "barAnim",
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f),
                ) {
                    // Time label above bar
                    if (dayStat.timeMs > 0) {
                        Text(
                            text = formatShortDuration(dayStat.timeMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = barColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Bar
                    Box(
                        modifier =
                            Modifier
                                .width(28.dp)
                                .fillMaxHeight(animatedFraction)
                                .background(
                                    color = if (dayStat.timeMs > 0) barColor else barColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                                ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            data.forEach { dayStat ->
                Text(
                    text = dayStat.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}p"
        minutes > 0 -> "$minutes phút"
        totalSeconds > 0 -> "${totalSeconds}s"
        else -> "0 phút"
    }
}

private fun formatShortDuration(ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    return when {
        hours > 0 -> "${hours}h"
        totalMinutes > 0 -> "${totalMinutes}p"
        else -> "<1p"
    }
}
