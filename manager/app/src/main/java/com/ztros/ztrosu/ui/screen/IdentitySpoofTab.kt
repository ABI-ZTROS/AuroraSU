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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ztros.ztrosu.ui.util.*
import kotlinx.coroutines.launch

/**
 * 身份伪装Tab
 *
 * 管理设备身份标识的伪装规则
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentitySpoofTab(
    spoofInterface: IdentitySpoofInterface = IdentitySpoofInterface
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 状态
    var rules by remember { mutableStateOf<List<SpoofRule>?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var moduleEnabled by remember { mutableStateOf(false) }

    // 加载数据
    fun refreshData() {
        scope.launch {
            isRefreshing = true
            rules = spoofInterface.getRules()
            moduleEnabled = spoofInterface.isEnabled()
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) { refreshData() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "身份伪装",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = moduleEnabled,
                        onCheckedChange = {
                            scope.launch {
                                spoofInterface.setEnabled(it)
                                moduleEnabled = it
                                snackbarHostState.showSnackbar(
                                    if (it) "身份伪装已启用" else "身份伪装已禁用"
                                )
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            spoofInterface.rotateAll()
                            refreshData()
                            snackbarHostState.showSnackbar("已轮换所有随机值")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "轮换")
                }
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加规则")
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
            if (rules.isEmpty()) {
                EmptySpoofState { showAddDialog = true }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(rules, key = { it.id }) { rule ->
                        SpoofRuleCard(
                            rule = rule,
                            onDelete = {
                                scope.launch {
                                    spoofInterface.removeRule(rule.id)
                                    refreshData()
                                    snackbarHostState.showSnackbar("规则已删除")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 添加规则对话框
    if (showAddDialog) {
        AddSpoofRuleDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { packageName, idType, strategy, value, interval ->
                scope.launch {
                    val success = spoofInterface.addRule(
                        packageName, idType, strategy, value, interval
                    )
                    if (success) {
                        refreshData()
                        snackbarHostState.showSnackbar("规则已添加")
                    } else {
                        snackbarHostState.showSnackbar("添加失败")
                    }
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
private fun SpoofRuleCard(
    rule: SpoofRule,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                        Icons.Default.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        rule.packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(rule.idType.displayName) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(rule.strategy.displayName) }
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "当前值: ${rule.currentValue}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun EmptySpoofState(onAddClick: () -> Unit) {
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
            "暂无伪装规则",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            "添加规则以伪装设备身份标识",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddClick) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("添加规则")
        }
    }
}

@Composable
private fun AddSpoofRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, SpoofIdType, SpoofStrategy, String, Int) -> Unit
) {
    var packageName by remember { mutableStateOf("") }
    var selectedIdType by remember { mutableStateOf(SpoofIdType.MAC_WIFI) }
    var selectedStrategy by remember { mutableStateOf(SpoofStrategy.RANDOM) }
    var fixedValue by remember { mutableStateOf("") }
    var rotateInterval by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加伪装规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("目标包名") },
                    placeholder = { Text("com.example.app") },
                    modifier = Modifier.fillMaxWidth()
                )

                // 标识类型选择
                Text("标识类型", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SpoofIdType.values().take(5).forEach { type ->
                        FilterChip(
                            selected = selectedIdType == type,
                            onClick = { selectedIdType = type },
                            label = { Text(type.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SpoofIdType.values().drop(5).forEach { type ->
                        FilterChip(
                            selected = selectedIdType == type,
                            onClick = { selectedIdType = type },
                            label = { Text(type.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // 策略选择
                Text("伪装策略", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SpoofStrategy.values().forEach { strategy ->
                        FilterChip(
                            selected = selectedStrategy == strategy,
                            onClick = { selectedStrategy = strategy },
                            label = { Text(strategy.displayName) }
                        )
                    }
                }

                // 固定值（仅 FIXED 策略需要）
                if (selectedStrategy == SpoofStrategy.FIXED) {
                    OutlinedTextField(
                        value = fixedValue,
                        onValueChange = { fixedValue = it },
                        label = { Text("固定值") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 轮换间隔（仅 ROTATE 策略需要）
                if (selectedStrategy == SpoofStrategy.ROTATE) {
                    OutlinedTextField(
                        value = rotateInterval,
                        onValueChange = { rotateInterval = it },
                        label = { Text("轮换间隔（秒）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        packageName,
                        selectedIdType,
                        selectedStrategy,
                        fixedValue,
                        rotateInterval.toIntOrNull() ?: 0
                    )
                },
                enabled = packageName.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
