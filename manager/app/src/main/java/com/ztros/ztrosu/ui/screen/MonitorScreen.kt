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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * SOC topology information
 */
private data class SocInfo(
    val hardware: String = "",           // Hardware name from cpuinfo
    val processor: String = "",          // Processor name
    val totalCores: Int = 0,             // Total CPU cores
    val onlineCores: Int = 0,            // Currently online cores
    val bigCores: Int = 0,               // Big cluster cores (high performance)
    val littleCores: Int = 0,            // Little cluster cores (power saving)
    val maxFreqBig: Long = 0,            // Max frequency of big cluster (kHz)
    val maxFreqLittle: Long = 0,         // Max frequency of little cluster (kHz)
    val clusterInfo: List<ClusterInfo> = emptyList()  // Detailed cluster info
)

/**
 * CPU cluster information
 */
private data class ClusterInfo(
    val name: String,                    // Cluster name (e.g., "Cluster 0", "Big", "Little")
    val cores: List<Int>,                // Core indices in this cluster
    val minFreq: Long,                   // Min frequency (kHz)
    val maxFreq: Long,                   // Max frequency (kHz)
    val currentFreq: Long,               // Current frequency (kHz)
    val isOnline: Boolean,               // Whether cluster is online
)

/**
 * Per-core CPU state
 */
private data class CoreState(
    val index: Int,                      // Core index (0, 1, 2, ...)
    val frequency: Long,                 // Current frequency (kHz)
    val usage: Float,                    // Usage percentage (0-100)
    val isOnline: Boolean,               // Whether core is online
    val cluster: Int,                    // Cluster index (-1 if unknown)
)

/**
 * CPU overall state
 */
private data class CpuState(
    val usage: Float = 0f,               // Overall CPU usage
    val history: List<Float> = emptyList(), // Usage history for chart
    val socInfo: SocInfo = SocInfo(),    // SOC topology info
    val coreStates: List<CoreState> = emptyList(), // Per-core states
)

/**
 * Memory state with ZRAM and extension
 */
private data class MemState(
    val totalMb: Long = 0,               // Total physical memory (MB)
    val usedMb: Long = 0,                // Used memory (MB)
    val availableMb: Long = 0,           // Available memory (MB)
    val usagePercent: Float = 0f,        // Usage percentage
    val zramTotalMb: Long = 0,           // ZRAM total size (MB)
    val zramUsedMb: Long = 0,            // ZRAM used (MB)
    val swapTotalMb: Long = 0,           // Swap total (MB)
    val swapUsedMb: Long = 0,            // Swap used (MB)
    val hasZram: Boolean = false,        // Whether ZRAM is enabled
    val hasSwap: Boolean = false,        // Whether swap exists
    val memPlusMb: Long = 0,             // Memory Plus/Extension (MB) if supported
)

/**
 * Disk state
 */
private data class DiskState(
    val totalGb: Float = 0f,
    val usedGb: Float = 0f,
    val usagePercent: Float = 0f
)

/**
 * Network state
 */
private data class NetState(
    val rxBytes: Long = 0,
    val txBytes: Long = 0
)

/**
 * Read SOC topology information
 */
private suspend fun readSocInfo(): SocInfo = withContext(Dispatchers.IO) {
    runCatching {
        // Read /proc/cpuinfo for hardware info
        val cpuinfo = ShellUtils.fastCmd("cat /proc/cpuinfo 2>/dev/null").trim()
        var hardware = ""
        var processor = ""
        
        for (line in cpuinfo.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("Hardware", ignoreCase = true) -> {
                    hardware = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("Processor", ignoreCase = true) -> {
                    processor = trimmed.substringAfter(":").trim()
                }
            }
        }
        
        // Get total cores
        val totalCores = ShellUtils.fastCmd("cat /proc/cpuinfo | grep -c 'processor' 2>/dev/null").trim()
            .toIntOrNull() ?: Runtime.getRuntime().availableProcessors()
        
        // Get online cores
        val onlineMask = ShellUtils.fastCmd("cat /sys/devices/system/cpu/online 2>/dev/null").trim()
        val onlineCores = parseCpuMask(onlineMask, totalCores)
        
        // Detect cluster topology by checking frequencies
        val clusters = mutableListOf<ClusterInfo>()
        val clusterMap = mutableMapOf<Long, MutableList<Int>>() // freq -> cores
        
        for (i in 0 until totalCores) {
            val maxFreq = ShellUtils.fastCmd("cat /sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq 2>/dev/null").trim()
                .toLongOrNull() ?: 0
            val minFreq = ShellUtils.fastCmd("cat /sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq 2>/dev/null").trim()
                .toLongOrNull() ?: 0
            
            if (maxFreq > 0) {
                // Group cores by max frequency (same freq = same cluster)
                val key = maxFreq
                clusterMap.getOrPut(key) { mutableListOf() }.add(i)
            }
        }
        
        // Build cluster info
        val sortedFreqs = clusterMap.keys.sortedDescending()
        var clusterIdx = 0
        for (freq in sortedFreqs) {
            val cores = clusterMap[freq] ?: continue
            val minFreq = cores.map { core ->
                ShellUtils.fastCmd("cat /sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_min_freq 2>/dev/null").trim()
                    .toLongOrNull() ?: 0
            }.minOrNull() ?: 0
            
            val name = if (sortedFreqs.size == 1) {
                "统一"
            } else if (clusterIdx == 0) {
                "大核"
            } else {
                "小核"
            }
            
            clusters.add(ClusterInfo(
                name = name,
                cores = cores,
                minFreq = minFreq,
                maxFreq = freq,
                currentFreq = 0,
                isOnline = cores.any { core ->
                    ShellUtils.fastCmd("cat /sys/devices/system/cpu/cpu$core/online 2>/dev/null").trim() == "1"
                }
            ))
            clusterIdx++
        }
        
        val bigCores = clusters.firstOrNull()?.cores?.size ?: 0
        val littleCores = clusters.drop(1).sumOf { it.cores.size }
        val maxFreqBig = clusters.firstOrNull()?.maxFreq ?: 0
        val maxFreqLittle = clusters.drop(1).maxOfOrNull { it.maxFreq } ?: 0
        
        SocInfo(
            hardware = hardware.ifEmpty { getPropHardware() },
            processor = processor,
            totalCores = totalCores,
            onlineCores = onlineCores,
            bigCores = bigCores,
            littleCores = littleCores,
            maxFreqBig = maxFreqBig,
            maxFreqLittle = maxFreqLittle,
            clusterInfo = clusters
        )
    }.getOrDefault(SocInfo())
}

/**
 * Get hardware from system property
 */
private fun getPropHardware(): String {
    val board = ShellUtils.fastCmd("getprop ro.product.board 2>/dev/null").trim()
    val device = ShellUtils.fastCmd("getprop ro.board.platform 2>/dev/null").trim()
    val soc = ShellUtils.fastCmd("getprop ro.hardware 2>/dev/null").trim()
    return device.ifEmpty { board }.ifEmpty { soc }.ifEmpty { "未知" }
}

/**
 * Parse CPU online mask (e.g., "0-3,5-7" -> count)
 */
private fun parseCpuMask(mask: String, maxCores: Int): Int {
    if (mask.isBlank() || mask == "0") return 1
    return runCatching {
        var count = 0
        val parts = mask.split(",")
        for (part in parts) {
            if (part.contains("-")) {
                val range = part.split("-")
                val start = range[0].toIntOrNull() ?: 0
                val end = range[1].toIntOrNull() ?: 0
                count += (end - start + 1)
            } else {
                count += 1
            }
        }
        count.coerceIn(1, maxCores)
    }.getOrDefault(1)
}

/**
 * Read per-core CPU usage
 */
private suspend fun readCoreStates(socInfo: SocInfo): List<CoreState> = withContext(Dispatchers.IO) {
    runCatching {
        // Read /proc/stat for per-core usage
        val statLines = ShellUtils.fastCmd("cat /proc/stat 2>/dev/null | grep -E '^cpu[0-9]'").trim()
        val coreStats = mutableListOf<CoreState>()
        
        for ((idx, line) in statLines.lines().withIndex()) {
            if (line.isBlank()) continue
            val parts = line.trim().split("\\s+")
            if (parts.size >= 5) {
                val user = parts[1].toLongOrNull() ?: 0
                val nice = parts[2].toLongOrNull() ?: 0
                val system = parts[3].toLongOrNull() ?: 0
                val idle = parts[4].toLongOrNull() ?: 0
                val total = user + nice + system + idle
                val usage = if (total > 0) {
                    (total - idle).toFloat() / total.toFloat() * 100f
                } else 0f
                
                // Get current frequency
                val freq = ShellUtils.fastCmd("cat /sys/devices/system/cpu/cpu$idx/cpufreq/scaling_cur_freq 2>/dev/null").trim()
                    .toLongOrNull() ?: 0
                
                // Check online status
                val isOnline = ShellUtils.fastCmd("cat /sys/devices/system/cpu/cpu$idx/online 2>/dev/null").trim() != "0"
                
                // Determine cluster
                val cluster = socInfo.clusterInfo.indexOfFirst { it.cores.contains(idx) }
                
                coreStats.add(CoreState(
                    index = idx,
                    frequency = freq,
                    usage = usage.coerceIn(0f, 100f),
                    isOnline = isOnline,
                    cluster = cluster
                ))
            }
        }
        
        coreStats
    }.getOrDefault(emptyList())
}

/**
 * Read overall CPU usage
 */
private suspend fun readCpuUsage(): Float = withContext(Dispatchers.IO) {
    runCatching {
        val line = ShellUtils.fastCmd("cat /proc/stat | head -1").trim()
        val parts = line.split("\\s+")
        if (parts.size >= 5) {
            val idle = parts[4].toLongOrNull() ?: 0
            val total = parts.drop(1).sumOf { it.toLongOrNull() ?: 0 }
            val prevTotal = total - idle
            if (total > 0) (prevTotal.toFloat() / total.toFloat() * 100f) else 0f
        } else 0f
    }.getOrDefault(0f)
}

/**
 * Read memory info with ZRAM and swap
 */
private suspend fun readMemInfo(): MemState = withContext(Dispatchers.IO) {
    runCatching {
        val info = ShellUtils.fastCmd("cat /proc/meminfo").trim()
        val map = mutableMapOf<String, Long>()
        
        for (line in info.lines()) {
            val kv = line.split(":", limit = 2)
            if (kv.size == 2) {
                val key = kv[0].trim()
                val value = kv[1].trim().replace("[0-9]".toRegex(), "").toLongOrNull() ?: 0L
                map[key] = value
            }
        }
        
        val total = (map["MemTotal"] ?: 0L) / 1024L
        val available = (map["MemAvailable"] ?: map["MemFree"] ?: 0L) / 1024L
        val used = total - available
        val percent = if (total > 0) used.toFloat() / total.toFloat() * 100f else 0f
        
        // Read ZRAM info
        val zramInfo = ShellUtils.fastCmd("cat /proc/swaps 2>/dev/null | grep zram").trim()
        var zramTotal = 0L
        var zramUsed = 0L
        var hasZram = false
        
        if (zramInfo.isNotBlank()) {
            hasZram = true
            val zramParts = zramInfo.split("\\s+")
            if (zramParts.size >= 4) {
                zramTotal = zramParts[2].toLongOrNull() ?: 0L / 1024L
                zramUsed = zramParts[3].toLongOrNull() ?: 0L / 1024L
            }
        }
        
        // Alternative: read from zram device
        if (!hasZram) {
            val zramSize = ShellUtils.fastCmd("cat /sys/block/zram0/disksize 2>/dev/null").trim().toLongOrNull() ?: 0L
            if (zramSize > 0) {
                hasZram = true
                zramTotal = zramSize / (1024L * 1024L)
                val zramMmStat = ShellUtils.fastCmd("cat /sys/block/zram0/mm_stat 2>/dev/null").trim()
                if (zramMmStat.isNotBlank()) {
                    val mmParts = zramMmStat.split("\\s+")
                    zramUsed = (mmParts.firstOrNull()?.toLongOrNull() ?: 0L) / (1024L * 1024L)
                }
            }
        }
        
        // Read swap info
        val swapTotal = (map["SwapTotal"] ?: 0L) / 1024L
        val swapUsed = (map["SwapCached"] ?: map["SwapFree"] ?: 0L).let { swapTotal - it / 1024L }
        val hasSwap = swapTotal > 0
        
        // Memory Plus / RAM expansion (Samsung, Xiaomi, etc.)
        val memPlus = runCatching {
            // Samsung: /proc/swaps with swap file
            // Xiaomi: dynamic ram feature
            val swapFile = ShellUtils.fastCmd("cat /proc/swaps 2>/dev/null | grep -v zram | grep swap").trim()
            if (swapFile.isNotBlank()) {
                val parts = swapFile.split("\\s+")
                if (parts.size >= 4) {
                    (parts[2].toLongOrNull() ?: 0L) / 1024L
                } else 0L
            } else {
                // Check for dynamic RAM extension
                val dynRam = ShellUtils.fastCmd("getprop ro.config.dynamic_ram.enable 2>/dev/null").trim()
                if (dynRam == "true" || dynRam == "1") {
                    val extSize = ShellUtils.fastCmd("getprop ro.config.dynamic_ram.size 2>/dev/null").trim().toLongOrNull() ?: 0L
                    extSize / 1024L
                } else 0L
            }
        }.getOrDefault(0L)
        
        MemState(
            totalMb = total,
            usedMb = used,
            availableMb = available,
            usagePercent = percent,
            zramTotalMb = zramTotal,
            zramUsedMb = zramUsed,
            swapTotalMb = swapTotal,
            swapUsedMb = swapUsed.coerceAtLeast(0L),
            hasZram = hasZram,
            hasSwap = hasSwap,
            memPlusMb = memPlus
        )
    }.getOrDefault(MemState())
}

/**
 * Read disk info
 */
private suspend fun readDiskInfo(): DiskState = withContext(Dispatchers.IO) {
    runCatching {
        val line = ShellUtils.fastCmd("df /data 2>/dev/null | tail -1").trim()
        val parts = line.split("\\s+")
        if (parts.size >= 5) {
            val totalKb = parts[1].toLongOrNull() ?: 0L
            val usedKb = parts[2].toLongOrNull() ?: 0L
            val totalGb = totalKb / 1024f / 1024f
            val usedGb = usedKb / 1024f / 1024f
            val percent = if (totalKb > 0) usedKb.toFloat() / totalKb.toFloat() * 100f else 0f
            DiskState(totalGb, usedGb, percent)
        } else DiskState()
    }.getOrDefault(DiskState())
}

/**
 * Read network stats
 */
private suspend fun readNetStats(): NetState = withContext(Dispatchers.IO) {
    runCatching {
        val rx = ShellUtils.fastCmd("cat /proc/net/dev | grep -E 'wlan0|eth0|rmnet0' | awk '{print $2}'").trim()
        val tx = ShellUtils.fastCmd("cat /proc/net/dev | grep -E 'wlan0|eth0|rmnet0' | awk '{print $10}'").trim()
        val rxVal = rx.lines().sumOf { it.toLongOrNull() ?: 0L }
        val txVal = tx.lines().sumOf { it.toLongOrNull() ?: 0L }
        NetState(rxBytes = rxVal, txBytes = txVal)
    }.getOrDefault(NetState())
}

/**
 * Read SU logs
 */
private suspend fun readSuLogs(): String = withContext(Dispatchers.IO) {
    runCatching {
        ShellUtils.fastCmd("cat /data/adb/ks/sulog 2>/dev/null | tail -20").trim()
    }.getOrDefault("")
}

/**
 * Format bytes to human readable
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
        else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

/**
 * Format frequency (kHz -> MHz/GHz)
 */
private fun formatFreq(khz: Long): String {
    return when {
        khz < 1000 -> "$khz kHz"
        khz < 1_000_000 -> "${khz / 1000} MHz"
        else -> "%.2f GHz".format(khz / 1_000_000.0)
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
    var socInfoLoaded by remember { mutableStateOf(false) }

    // Load SOC info once
    LaunchedEffect(Unit) {
        val soc = readSocInfo()
        cpuState = cpuState.copy(socInfo = soc)
        socInfoLoaded = true
    }

    // Periodic updates
    LaunchedEffect(socInfoLoaded) {
        if (!socInfoLoaded) return@LaunchedEffect
        while (isActive) {
            val cpu = readCpuUsage()
            val cores = readCoreStates(cpuState.socInfo)
            val mem = readMemInfo()
            val disk = readDiskInfo()
            val net = readNetStats()
            val logs = readSuLogs()
            
            cpuState = CpuState(
                usage = cpu,
                history = (cpuState.history + cpu).takeLast(60),
                socInfo = cpuState.socInfo,
                coreStates = cores
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
            // SOC Info Card
            if (cpuState.socInfo.hardware.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeveloperBoard,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cpuState.socInfo.hardware,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "${cpuState.socInfo.totalCores}核 ${if (cpuState.socInfo.clusterInfo.size > 1) "${cpuState.socInfo.bigCores}大核+${cpuState.socInfo.littleCores}小核" else "统一架构"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${cpuState.socInfo.onlineCores}/${cpuState.socInfo.totalCores}在线",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        
                        // Cluster topology
                        if (cpuState.socInfo.clusterInfo.isNotEmpty()) {
                            HorizontalDivider()
                            for (cluster in cpuState.socInfo.clusterInfo) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${cluster.name} (${cluster.cores.joinToString(",") { "C$it" }})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${formatFreq(cluster.minFreq)} - ${formatFreq(cluster.maxFreq)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // CPU Usage Card with per-core display
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = cpuLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "%.1f%%".format(cpuState.usage),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (cpuState.usage > 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    // History chart
                    val chartColor = MaterialTheme.colorScheme.primary
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
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
                                color = chartColor,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Per-core usage grid
                    if (cpuState.coreStates.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "核心负载",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Display cores in rows of 4
                        val rows = cpuState.coreStates.chunked(4)
                        for (row in rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (core in row) {
                                    val coreColor = when {
                                        !core.isOnline -> MaterialTheme.colorScheme.surfaceVariant
                                        core.usage > 80 -> MaterialTheme.colorScheme.error
                                        core.usage > 50 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        color = coreColor.copy(alpha = 0.2f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "C${core.index}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (core.isOnline) coreColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                            Text(
                                                text = if (core.isOnline) "%.0f%%".format(core.usage) else "离线",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (core.isOnline) coreColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                            if (core.isOnline && core.frequency > 0) {
                                                Text(
                                                    text = formatFreq(core.frequency),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Memory Card with ZRAM info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = memoryLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "%.1f%%".format(memState.usagePercent),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (memState.usagePercent > 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { memState.usagePercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (memState.usagePercent > 85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "$usedLabel: ${memState.usedMb} MB / $totalLabel: ${memState.totalMb} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // ZRAM info
                    if (memState.hasZram) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Compress,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "ZRAM",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${memState.zramUsedMb}/${memState.zramTotalMb} MB",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Swap info
                    if (memState.hasSwap) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Swap",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "${memState.swapUsedMb}/${memState.swapTotalMb} MB",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Memory Plus / RAM extension
                    if (memState.memPlusMb > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.AddCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "内存扩展",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "+${memState.memPlusMb} MB",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Disk Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = diskLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "%.1f%%".format(diskState.usagePercent),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

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
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = networkLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = rxLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatBytes(netState.rxBytes),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = txLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatBytes(netState.txBytes),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }

            // SU Log Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = suLogLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    if (suLogs.isNotBlank()) {
                        Text(
                            text = suLogs,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
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