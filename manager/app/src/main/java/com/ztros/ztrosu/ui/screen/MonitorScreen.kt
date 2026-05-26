package com.ztros.ztrosu.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ztros.ztrosu.R
import com.ztros.ztrosu.ui.LocalScrollState
import com.ztros.ztrosu.ui.rememberScrollConnection
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class CpuState(
    val usage: Float = 0f,
    val history: List<Float> = emptyList()
)

private data class MemState(
    val totalMb: Long = 0,
    val usedMb: Long = 0,
    val usagePercent: Float = 0f
)

private data class DiskState(
    val totalGb: Float = 0f,
    val usedGb: Float = 0f,
    val usagePercent: Float = 0f
)

private data class NetState(
    val rxBytes: Long = 0,
    val txBytes: Long = 0
)

private suspend fun readCpuUsage(): Float = withContext(Dispatchers.IO) {
    runCatching {
        val line = ShellUtils.fastCmd("cat /proc/stat | head -1").trim()
        val parts = line.split("\\s+".toRegex()).drop(1).map { it.toLongOrNull() ?: 0L }
        if (parts.size >= 4) {
            val idle = parts[3]
            val total = parts.sum()
            val prevTotal = total - idle
            if (prevTotal > 0) ((total - idle).toFloat() / total.toFloat() * 100f) else 0f
        } else 0f
    }.getOrDefault(0f)
}

private suspend fun readMemInfo(): MemState = withContext(Dispatchers.IO) {
    runCatching {
        val info = ShellUtils.fastCmd("cat /proc/meminfo").trim()
        val map = mutableMapOf<String, Long>()
        info.lines().forEach { line ->
            val kv = line.split(":", limit = 2)
            if (kv.size == 2) {
                val key = kv[0].trim()
                val value = kv[1].trim().replace("[^0-9]".toRegex(), "").toLongOrNull() ?: 0L
                map[key] = value
            }
        }
        val total = (map["MemTotal"] ?: 0L) / 1024
        val available = (map["MemAvailable"] ?: (map["MemFree"] ?: 0L)) / 1024
        val used = total - available
        val percent = if (total > 0) used.toFloat() / total.toFloat() * 100f else 0f
        MemState(totalMb = total, usedMb = used, usagePercent = percent)
    }.getOrDefault(MemState())
}

private suspend fun readDiskInfo(): DiskState = withContext(Dispatchers.IO) {
    runCatching {
        val line = ShellUtils.fastCmd("df /data 2>/dev/null | tail -1").trim()
        val parts = line.split("\\s+".toRegex())
        if (parts.size >= 5) {
            val totalKb = parts[1].toLongOrNull() ?: 0L
            val usedKb = parts[2].toLongOrNull() ?: 0L
            val totalGb = totalKb / 1024f / 1024f
            val usedGb = usedKb / 1024f / 1024f
            val percent = if (totalKb > 0) usedKb.toFloat() / totalKb.toFloat() * 100f else 0f
            DiskState(totalGb = totalGb, usedGb = usedGb, usagePercent = percent)
        } else DiskState()
    }.getOrDefault(DiskState())
}

private suspend fun readNetStats(): NetState = withContext(Dispatchers.IO) {
    runCatching {
        val rx = ShellUtils.fastCmd("cat /proc/net/dev | grep -E 'wlan0|eth0' | awk '{print \$2}'").trim()
        val tx = ShellUtils.fastCmd("cat /proc/net/dev | grep -E 'wlan0|eth0' | awk '{print \$10}'").trim()
        val rxVal = rx.lines().map { it.toLongOrNull() ?: 0L }.sum()
        val txVal = tx.lines().map { it.toLongOrNull() ?: 0L }.sum()
        NetState(rxBytes = rxVal, txBytes = txVal)
    }.getOrDefault(NetState())
}

private suspend fun readSuLogs(): String = withContext(Dispatchers.IO) {
    runCatching {
        ShellUtils.fastCmd("cat /data/adb/ksu/sulog 2>/dev/null | tail -20").trim()
    }.getOrDefault("")
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun MonitorScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    val cpuLabel = stringResource(R.string.monitor_cpu)
    val memoryLabel = stringResource(R.string.monitor_memory)
    val diskLabel = stringResource(R.string.monitor_disk)
    val networkLabel = stringResource(R.string.monitor_network)
    val suLogLabel = stringResource(R.string.monitor_su_log)
    val rxLabel = stringResource(R.string.monitor_rx)
    val txLabel = stringResource(R.string.monitor_tx)
    val usedLabel = stringResource(R.string.monitor_used)
    val totalLabel = stringResource(R.string.monitor_total)

    var cpuState by remember { mutableStateOf(CpuState()) }
    var memState by remember { mutableStateOf(MemState()) }
    var diskState by remember { mutableStateOf(DiskState()) }
    var netState by remember { mutableStateOf(NetState()) }
    var suLogs by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val cpu = readCpuUsage()
            val mem = readMemInfo()
            val disk = readDiskInfo()
            val net = readNetStats()
            val logs = readSuLogs()
            cpuState = CpuState(
                usage = cpu,
                history = (cpuState.history + cpu).takeLast(60)
            )
            memState = mem
            diskState = disk
            netState = net
            suLogs = logs
            delay(2000)
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                onBack = dropUnlessResumed { navigator.popBackStack() },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackBarHost, modifier = Modifier.padding(bottom = navBarPadding)) },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .let { modifier ->
                    val bottomBarScrollState = LocalScrollState.current
                    val bottomBarScrollConnection = bottomBarScrollState?.let {
                        rememberScrollConnection(
                            isScrollingDown = it.isScrollingDown,
                            scrollOffset = it.scrollOffset,
                            previousScrollOffset = it.previousScrollOffset,
                            threshold = 30f
                        )
                    }
                    if (bottomBarScrollConnection != null) {
                        modifier
                            .nestedScroll(bottomBarScrollConnection)
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                    } else {
                        modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CPU Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(
                                text = cpuLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Memory, contentDescription = null)
                        },
                        trailingContent = {
                            Text(
                                text = "%.1f%%".format(cpuState.usage),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (cpuState.usage > 80f) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    ) {
                        val data = cpuState.history
                        if (data.size >= 2) {
                            val stepX = size.width / (data.size - 1)
                            val path = Path()
                            path.moveTo(0f, size.height - (data[0] / 100f) * size.height)
                            for (i in 1 until data.size) {
                                path.lineTo(i * stepX, size.height - (data[i] / 100f) * size.height)
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF4CAF50),
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            // Memory Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(
                                text = memoryLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Storage, contentDescription = null)
                        },
                        trailingContent = {
                            Text(
                                text = "%.1f%%".format(memState.usagePercent),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                    LinearProgressIndicator(
                        progress = { memState.usagePercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (memState.usagePercent > 85f) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "$usedLabel: ${memState.usedMb} MB / $totalLabel: ${memState.totalMb} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Disk Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(
                                text = diskLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Folder, contentDescription = null)
                        },
                        trailingContent = {
                            Text(
                                text = "%.1f%%".format(diskState.usagePercent),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )
                    LinearProgressIndicator(
                        progress = { diskState.usagePercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "$usedLabel: %.1f GB / $totalLabel: %.1f GB".format(diskState.usedGb, diskState.totalGb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Network Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(
                                text = networkLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Language, contentDescription = null)
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$rxLabel: ${formatBytes(netState.rxBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$txLabel: ${formatBytes(netState.txBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // SU Log Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(
                                text = suLogLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Description, contentDescription = null)
                        }
                    )
                    if (suLogs.isNotBlank()) {
                        Text(
                            text = suLogs,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 10
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.monitor_no_logs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.monitor_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Preview
@Composable
private fun MonitorPreview() {
    MonitorScreen(EmptyDestinationsNavigator)
}
