@file:Suppress("FunctionName")

package com.ztros.ztrosu.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ztros.ztrosu.data.audit.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 安全审计Tab
 *
 * 显示审计日志、异常检测和统计信息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditTab(
    auditManager: AuditManager
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 收集实时数据
    val recentLogs by auditManager.getRecentLogs(50).collectAsStateWithLifecycle(initialValue = emptyList())
    val anomalyLogs by auditManager.getAnomalyLogs().collectAsStateWithLifecycle(initialValue = emptyList())
    val stats by auditManager.statsFlow.collectAsStateWithLifecycle()

    // 状态
    var showExportDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf<AuditFilter>(AuditFilter.All) }
    var isDetecting by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 异常检测按钮
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            isDetecting = true
                            auditManager.runAnomalyDetection(24)
                            isDetecting = false
                            snackbarHostState.showSnackbar("异常检测完成")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    enabled = !isDetecting
                ) {
                    if (isDetecting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Security, contentDescription = "检测异常")
                    }
                }

                // 导出按钮
                FloatingActionButton(
                    onClick = { showExportDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Download, contentDescription = "导出")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 统计卡片
            StatsCards(stats)

            Spacer(modifier = Modifier.height(16.dp))

            // 过滤栏
            FilterBar(
                selectedFilter = selectedFilter,
                onFilterChange = { selectedFilter = it },
                onShowFilterDialog = { showFilterDialog = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 日志列表
            val displayLogs = when (selectedFilter) {
                is AuditFilter.All -> recentLogs
                is AuditFilter.Anomaly -> anomalyLogs
                is AuditFilter.HighRisk -> recentLogs.filter { it.riskScore >= 50 }
                is AuditFilter.Denied -> recentLogs.filter { it.result == 1 }
            }

            if (displayLogs.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(displayLogs, key = { it.id }) { entry ->
                        AuditLogCard(entry)
                    }
                }
            }
        }
    }

    // 导出对话框
    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { format ->
                scope.launch {
                    val now = System.currentTimeMillis()
                    val yesterday = now - 86400_000L
                    val data = auditManager.exportLogs(format, yesterday, now)
                    // TODO: 保存到文件或分享
                    snackbarHostState.showSnackbar("导出完成: ${data.length} 字符")
                    showExportDialog = false
                }
            }
        )
    }
}

/**
 * 统计卡片区域
 */
@Composable
private fun StatsCards(stats: AuditStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            title = "总事件",
            value = stats.totalCount.toString(),
            icon = Icons.Default.Storage,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "异常",
            value = stats.anomalyCount.toString(),
            icon = Icons.Default.Warning,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            title = "拒绝",
            value = stats.deniedCount.toString(),
            icon = Icons.Default.Block,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.8f))
        }
    }
}

/**
 * 过滤栏
 */
@Composable
private fun FilterBar(
    selectedFilter: AuditFilter,
    onFilterChange: (AuditFilter) -> Unit,
    onShowFilterDialog: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedFilter is AuditFilter.All,
            onClick = { onFilterChange(AuditFilter.All) },
            label = { Text("全部") },
            leadingIcon = if (selectedFilter is AuditFilter.All) {
                { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
            } else null
        )
        FilterChip(
            selected = selectedFilter is AuditFilter.Anomaly,
            onClick = { onFilterChange(AuditFilter.Anomaly) },
            label = { Text("异常") },
            leadingIcon = if (selectedFilter is AuditFilter.Anomaly) {
                { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
            } else null
        )
        FilterChip(
            selected = selectedFilter is AuditFilter.HighRisk,
            onClick = { onFilterChange(AuditFilter.HighRisk) },
            label = { Text("高危") },
            leadingIcon = if (selectedFilter is AuditFilter.HighRisk) {
                { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
            } else null
        )
        FilterChip(
            selected = selectedFilter is AuditFilter.Denied,
            onClick = { onFilterChange(AuditFilter.Denied) },
            label = { Text("拒绝") },
            leadingIcon = if (selectedFilter is AuditFilter.Denied) {
                { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }
            } else null
        )
    }
}

/**
 * 审计日志卡片
 */
@Composable
private fun AuditLogCard(entry: AuditLogEntry) {
    val riskColor = when {
        entry.riskScore >= 80 -> MaterialTheme.colorScheme.error
        entry.riskScore >= 50 -> MaterialTheme.colorScheme.tertiary
        entry.riskScore >= 20 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isAnomaly) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 事件类型标签
                AssistChip(
                    onClick = {},
                    label = { Text(entry.eventTypeName) },
                    colors = AssistChipDefaults.assistChipColors(
                        leadingContainerColor = riskColor.copy(alpha = 0.2f),
                        labelColor = riskColor
                    )
                )

                // 时间
                Text(
                    formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(4.dp))

            // 路径
            Text(
                entry.path,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PID/UID
                Text(
                    "PID:${entry.pid} UID:${entry.uid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                // 结果
                val resultColor = if (entry.result == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
                Text(
                    entry.resultName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = resultColor
                )
            }

            // 异常信息
            if (entry.isAnomaly) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${entry.anomalyType} (风险:${entry.riskScore})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "暂无审计日志",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            "启动事件监听后将自动收集",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        )
    }
}

/**
 * 导出对话框
 */
@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (ExportFormat) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导出审计日志") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择导出格式:")
                Button(
                    onClick = { onExport(ExportFormat.CSV) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("CSV 格式")
                }
                Button(
                    onClick = { onExport(ExportFormat.JSON) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("JSON 格式")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 审计过滤条件
 */
sealed class AuditFilter {
    object All : AuditFilter()
    object Anomaly : AuditFilter()
    object HighRisk : AuditFilter()
    object Denied : AuditFilter()
}

/**
 * 格式化时间戳
 */
private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
