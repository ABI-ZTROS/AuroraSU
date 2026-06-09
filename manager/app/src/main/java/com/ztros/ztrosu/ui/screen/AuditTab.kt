@file:Suppress("FunctionName")

package com.ztros.ztrosu.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.ztros.ztrosu.ui.util.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 安全审计Tab
 *
 * 功能:
 * 1. Shell 脚本执行统计 - 显示执行次数、来源、参数
 * 2. 设备分区保护 - 监控关键分区完整性
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditTab(
    securityAuditInterface: SecurityAuditInterface = SecurityAuditInterface
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 选中的子Tab
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabTitles = listOf("Shell 统计", "分区保护")

    // Shell 统计数据
    var shellStats by remember { mutableStateOf<ShellExecStats?>(null) }
    var shellRecords by remember { mutableStateOf<List<ShellExecRecord>?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // 分区保护数据
    var partitionStatus by remember { mutableStateOf<PartitionStatus?>(null) }

    // 加载数据
    fun refreshData() {
        scope.launch {
            isRefreshing = true
            shellStats = securityAuditInterface.getShellStats()
            shellRecords = securityAuditInterface.getRecentShellExecs()
            partitionStatus = securityAuditInterface.getPartitionStatus()
            isRefreshing = false
        }
    }

    // 初始加载
    LaunchedEffect(Unit) { refreshData() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                // 子Tab 切换
                TabRow(selectedTabIndex = selectedSubTab) {
                    subTabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedSubTab == index,
                            onClick = { selectedSubTab = index },
                            text = { Text(title, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { refreshData() },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }
    ) { padding ->
        when (selectedSubTab) {
            0 -> ShellStatsTab(
                stats = shellStats,
                records = shellRecords ?: emptyList(),
                onClearHistory = {
                    scope.launch {
                        securityAuditInterface.clearShellHistory()
                        refreshData()
                        snackbarHostState.showSnackbar("Shell 执行记录已清空")
                    }
                }
            )
            1 -> PartitionProtectionTab(
                status = partitionStatus,
                onSetPolicy = { enabled, autoReject, alertOnly, interval ->
                    scope.launch {
                        securityAuditInterface.setPartitionPolicy(
                            enabled, autoReject, alertOnly, interval
                        )
                        refreshData()
                        snackbarHostState.showSnackbar("分区保护策略已更新")
                    }
                },
                onResetModification = {
                    scope.launch {
                        securityAuditInterface.resetPartitionModification()
                        refreshData()
                        snackbarHostState.showSnackbar("分区修改标记已重置")
                    }
                }
            )
        }
    }
}

// ==================== Shell 统计 Tab ====================

@Composable
private fun ShellStatsTab(
    stats: ShellExecStats?,
    records: List<ShellExecRecord>,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (stats == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }

        Spacer(Modifier.height(16.dp))

        // 统计卡片
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                title = "总执行",
                value = stats.totalExecCount.toString(),
                icon = Icons.Default.Terminal,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "脚本执行",
                value = stats.scriptExecCount.toString(),
                icon = Icons.Default.Description,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "交互式",
                value = stats.interactiveCount.toString(),
                icon = Icons.Default.Terminal,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        // 最近执行信息
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("最近执行", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                InfoRow("解释器", stats.lastInterpreter.ifBlank { "无" })
                InfoRow("调用者", stats.lastCaller.ifBlank { "无" })
                InfoRow("时间", if (stats.lastTimestamp > 0) formatTimestamp(stats.lastTimestamp) else "无")
            }
        }

        Spacer(Modifier.height(12.dp))

        // 执行记录标题 + 清空按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("执行记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClearHistory) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("清空")
            }
        }

        Spacer(Modifier.height(8.dp))

        // 执行记录列表
        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("暂无 Shell 执行记录", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(records, key = { "${it.callerPid}-${it.timestamp}" }) { record ->
                    ShellExecCard(record)
                }
            }
        }
    }
}

@Composable
private fun ShellExecCard(record: ShellExecRecord) {
    val isScript = record.execType == "script"
    val bgColor = if (isScript) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isScript) Icons.Default.Description else Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isScript) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        record.callerName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    formatTimestamp(record.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(4.dp))

            // 解释器
            Text(
                record.interpreter,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            // 脚本路径（仅脚本执行）
            if (isScript && record.scriptPath.isNotBlank() && record.scriptPath != "-") {
                Text(
                    record.scriptPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "PID:${record.callerPid} UID:${record.callerUid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                AssistChip(
                    onClick = {},
                    label = { Text(if (isScript) "脚本" else "交互式", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

// ==================== 分区保护 Tab ====================

@Composable
private fun PartitionProtectionTab(
    status: PartitionStatus?,
    onSetPolicy: (Boolean, Boolean, Boolean, Int) -> Unit,
    onResetModification: () -> Unit
) {
    var showPolicyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (status == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }

        Spacer(Modifier.height(16.dp))

        // 保护状态卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (status.enabled) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (status.enabled) Icons.Default.Security else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (status.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "分区保护",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        if (status.enabled) "已启用" else "已禁用",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (status.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(8.dp))

                InfoRow("模式", when {
                    status.autoReject -> "拦截模式（自动拒绝写入）"
                    status.alertOnly -> "告警模式（仅记录）"
                    else -> "关闭"
                })
                InfoRow("检查间隔", "${status.checkInterval}秒")
            }
        }

        Spacer(Modifier.height(12.dp))

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showPolicyDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("设置策略")
            }
            OutlinedButton(
                onClick = onResetModification,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("重置标记")
            }
        }

        Spacer(Modifier.height(16.dp))

        // 分区列表
        Text("受保护分区", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(status.partitions, key = { it.mountPoint }) { partition ->
                PartitionCard(partition)
            }
        }
    }

    // 策略设置对话框
    if (showPolicyDialog && status != null) {
        PolicyDialog(
            currentStatus = status,
            onDismiss = { showPolicyDialog = false },
            onConfirm = { enabled, autoReject, alertOnly, interval ->
                onSetPolicy(enabled, autoReject, alertOnly, interval)
                showPolicyDialog = false
            }
        )
    }
}

@Composable
private fun PartitionCard(partition: PartitionInfo) {
    val hasModifications = partition.isModified || partition.modificationCount > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasModifications) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (hasModifications) Icons.Default.Warning else Icons.Default.Folder,
                contentDescription = null,
                tint = if (hasModifications) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    partition.mountPoint,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (hasModifications) {
                    Text(
                        "⚠ 检测到 ${partition.modificationCount} 次修改",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            AssistChip(
                onClick = {},
                label = {
                    Text(
                        if (hasModifications) "已修改" else "正常",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = if (hasModifications) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun PolicyDialog(
    currentStatus: PartitionStatus,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Boolean, Boolean, Int) -> Unit
) {
    var enabled by remember { mutableStateOf(currentStatus.enabled) }
    var autoReject by remember { mutableStateOf(currentStatus.autoReject) }
    var alertOnly by remember { mutableStateOf(currentStatus.alertOnly) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分区保护策略") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("启用保护")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("自动拒绝写入")
                    Switch(
                        checked = autoReject && enabled,
                        onCheckedChange = { autoReject = it },
                        enabled = enabled
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("仅告警模式")
                    Switch(
                        checked = alertOnly && enabled,
                        onCheckedChange = { alertOnly = it },
                        enabled = enabled
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(enabled, autoReject, alertOnly, currentStatus.checkInterval) }) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ==================== 通用组件 ====================

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
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
