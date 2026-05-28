package com.ztros.ztrosu.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ztros.ztrosu.R
import com.ztros.ztrosu.ui.LocalScrollState
import com.ztros.ztrosu.ui.component.rememberLoadingDialog
import com.ztros.ztrosu.ui.rememberScrollConnection
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Update Engine status information
 */
private data class UpdateEngineInfo(
    val currentOperation: String,   // IDLE, VERIFYING, UPDATING, FINALIZING, etc.
    val lastCheckedTime: String,    // Last update check time
    val progress: Float,            // 0.0 - 1.0
    val newPartitionSize: Long,     // Update package size in bytes
    val newVersion: String,         // New version string
    val isRunning: Boolean,         // Whether an update is in progress
    val bootPartitionOk: Boolean = true,
    val systemPartitionOk: Boolean = true,
    val vendorPartitionOk: Boolean = true,
    val currentSlot: String = "_a",
    val isDynamicPartition: Boolean = false,
)

/**
 * Get partition path using multiple possible locations
 */
private fun getPartitionPath(name: String, slot: String): String? {
    val possiblePaths = listOf(
        "/dev/block/by-name/$name$slot",
        "/dev/block/bootdevice/by-name/$name$slot",
        "/dev/block/platform/*/by-name/$name$slot",
        "/dev/block/mapper/$name$slot",
        "/dev/block/$name$slot"
    )
    
    for (path in possiblePaths) {
        // Try glob expansion for paths with *
        if (path.contains("*")) {
            val expanded = ShellUtils.fastCmd("ls $path 2>/dev/null").trim()
            if (expanded.isNotEmpty() && !expanded.contains("No such")) {
                return expanded.split("\n").firstOrNull()?.trim()
            }
        } else {
            val check = ShellUtils.fastCmd("ls -la $path 2>/dev/null").trim()
            if (check.isNotEmpty() && !check.contains("No such")) {
                return path
            }
        }
    }
    return null
}

/**
 * Check if partition is healthy by reading its first block
 */
private fun isPartitionHealthy(path: String?): Boolean {
    if (path.isNullOrEmpty()) return false
    return runCatching {
        // Try to read first 4KB of partition
        val result = ShellUtils.fastCmd("dd if=$path of=/dev/null bs=4096 count=1 2>&1 && echo SUCCESS").trim()
        result.contains("SUCCESS") || result.contains("1+0 records")
    }.getOrDefault(false)
}

/**
 * Parse update engine status from dumpsys output
 */
private suspend fun getUpdateEngineInfo(): UpdateEngineInfo = withContext(Dispatchers.IO) {
    runCatching {
        // Get current slot
        val currentSlot = ShellUtils.fastCmd("getprop ro.boot.slot_suffix 2>/dev/null").trim()
            .ifEmpty { ShellUtils.fastCmd("getprop ro.boot.slot 2>/dev/null").trim() }
            .ifEmpty { "_a" }
        
        // Check if A/B device
        val isAbDevice = ShellUtils.fastCmd("getprop ro.build.ab_update 2>/dev/null").trim()
            .toBoolean() || currentSlot.isNotEmpty()
        
        // Detect dynamic partitions
        val hasSuper = ShellUtils.fastCmd("ls -la /dev/block/mapper/super 2>/dev/null || ls -la /dev/block/by-name/super 2>/dev/null").trim().isNotEmpty()
        
        // Check partition health using multiple methods
        val bootPath = getPartitionPath("boot", currentSlot)
        val systemPath = getPartitionPath("system", currentSlot)
        val vendorPath = getPartitionPath("vendor", currentSlot)
        
        // Also check init_boot for Android 13+
        val initBootPath = getPartitionPath("init_boot", currentSlot)
        
        val bootOk = isPartitionHealthy(bootPath) || isPartitionHealthy(initBootPath)
        val systemOk = isPartitionHealthy(systemPath)
        val vendorOk = isPartitionHealthy(vendorPath) || !hasSuper // vendor may be in super
        
        // Try to get update engine status
        val output = ShellUtils.fastCmd("dumpsys update_engine_client 2>/dev/null || dumpsys update_engine 2>/dev/null").trim()
        
        if (output.isBlank() || output.contains("Can't find service")) {
            // Update engine not available, return partition info only
            return@withContext UpdateEngineInfo(
                currentOperation = if (isAbDevice) "AB_DEVICE" else "NOT_SUPPORTED",
                lastCheckedTime = "N/A",
                progress = 0f,
                newPartitionSize = 0L,
                newVersion = "N/A",
                isRunning = false,
                bootPartitionOk = bootOk,
                systemPartitionOk = systemOk,
                vendorPartitionOk = vendorOk,
                currentSlot = currentSlot,
                isDynamicPartition = hasSuper,
            )
        }

        var currentOperation = "IDLE"
        var lastCheckedTime = "N/A"
        var progress = 0f
        var newPartitionSize = 0L
        var newVersion = "N/A"

        for (line in output.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.contains("CURRENT_OP", ignoreCase = true) || 
                trimmed.contains("current_operation", ignoreCase = true) -> {
                    val op = trimmed.substringAfter(":").trim()
                        .substringAfter("=").trim()
                    currentOperation = parseOperation(op)
                }
                trimmed.contains("LAST_CHECKED_TIME", ignoreCase = true) ||
                trimmed.contains("last_checked_time", ignoreCase = true) ||
                trimmed.contains("last_check_time", ignoreCase = true) -> {
                    val timeStr = trimmed.substringAfter(":").trim()
                        .substringAfter("=").trim()
                    val time = timeStr.toLongOrNull()
                    lastCheckedTime = if (time != null && time > 0) {
                        formatTimestamp(time)
                    } else {
                        timeStr.takeIf { it.isNotBlank() } ?: "N/A"
                    }
                }
                trimmed.contains("PROGRESS", ignoreCase = true) -> {
                    val p = trimmed.substringAfter(":").trim()
                        .substringAfter("=").trim()
                        .toFloatOrNull()
                    if (p != null) progress = p.coerceIn(0f, 1f)
                }
                trimmed.contains("NEW_PARTITION_SIZE", ignoreCase = true) ||
                trimmed.contains("new_partition_size", ignoreCase = true) ||
                trimmed.contains("payload_size", ignoreCase = true) -> {
                    val size = trimmed.substringAfter(":").trim()
                        .substringAfter("=").trim()
                        .toLongOrNull()
                    if (size != null && size > 0) newPartitionSize = size
                }
                trimmed.contains("NEW_VERSION", ignoreCase = true) ||
                trimmed.contains("new_version", ignoreCase = true) ||
                trimmed.contains("target_version", ignoreCase = true) -> {
                    val ver = trimmed.substringAfter(":").trim()
                        .substringAfter("=").trim()
                    if (ver.isNotBlank() && ver != "0") newVersion = ver
                }
            }
        }

        val isRunning = currentOperation != "IDLE" && 
                       currentOperation != "ERROR" && 
                       currentOperation != "UPDATED_NEED_REBOOT" &&
                       currentOperation != "NOT_SUPPORTED"

        UpdateEngineInfo(
            currentOperation = currentOperation,
            lastCheckedTime = lastCheckedTime,
            progress = progress,
            newPartitionSize = newPartitionSize,
            newVersion = newVersion,
            isRunning = isRunning,
            bootPartitionOk = bootOk,
            systemPartitionOk = systemOk,
            vendorPartitionOk = vendorOk,
            currentSlot = currentSlot,
            isDynamicPartition = hasSuper,
        )
    }.getOrDefault(
        UpdateEngineInfo(
            currentOperation = "UNKNOWN",
            lastCheckedTime = "N/A",
            progress = 0f,
            newPartitionSize = 0L,
            newVersion = "N/A",
            isRunning = false
        )
    )
}

/**
 * Parse the numeric operation code to human-readable string
 */
private fun parseOperation(op: String): String {
    return when (op.trim()) {
        "0" -> "IDLE"
        "1" -> "CHECKING_FOR_UPDATE"
        "2" -> "UPDATE_AVAILABLE"
        "3" -> "DOWNLOADING"
        "4" -> "VERIFYING"
        "5" -> "FINALIZING"
        "6" -> "UPDATED_NEED_REBOOT"
        "7" -> "REPORTING_ERROR_EVENT"
        "8" -> "ATTEMPTING_ROLLBACK"
        "9" -> "DISABLED"
        else -> op.ifBlank { "IDLE" }
    }
}

/**
 * Format unix timestamp to readable date
 */
private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0) return "N/A"
    return try {
        val seconds = if (timestamp > 1_000_000_000_000L) timestamp / 1000 else timestamp
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        sdf.format(java.util.Date(seconds * 1000))
    } catch (e: Exception) {
        "N/A"
    }
}

/**
 * Format bytes to human-readable size
 */
private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "N/A"
    return when {
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.2f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}

/**
 * Reset update engine by clearing update cache
 */
private suspend fun resetUpdateEngine(): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        // Stop update engine service
        ShellUtils.fastCmdResult("stop update_engine") &&
        // Clear the update payload and state
        ShellUtils.fastCmdResult("rm -rf /data/update_engine 2>/dev/null; rm -rf /cache/update_engine 2>/dev/null; rm -rf /data/ota 2>/dev/null; rm -rf /cache/ota 2>/dev/null") &&
        // Reset the update engine state
        ShellUtils.fastCmdResult("update_engine_client --cancel 2>/dev/null; true") &&
        // Restart update engine
        ShellUtils.fastCmdResult("start update_engine")
    }.getOrDefault(false)
}

/**
 * Repair partitions by checking slot, cleaning residual data,
 * verifying partition table integrity, and restarting update_engine service.
 */
private suspend fun repairPartitions(): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        // 1. Check current slot
        val currentSlot = ShellUtils.fastCmd("getprop ro.boot.slot_suffix 2>/dev/null").trim().ifEmpty { "_a" }
        val otherSlot = if (currentSlot == "_a") "_b" else "_a"

        // 2. Cancel any pending update_engine operations
        ShellUtils.fastCmd("update_engine_client --cancel 2>/dev/null")

        // 3. Clean residual update data
        ShellUtils.fastCmd("rm -rf /data/update_engine/payload 2>/dev/null")
        ShellUtils.fastCmd("rm -rf /cache/update_engine 2>/dev/null")

        // 4. Verify partition table integrity
        val gptResult = ShellUtils.fastCmd("sgdisk --verify /dev/block/sda 2>/dev/null")
        val gptOk = !gptResult.contains("Problem") && !gptResult.contains("Error")

        // 5. Check super partition (dynamic partition device)
        val superInfo = ShellUtils.fastCmd("ls -la /dev/block/mapper/super 2>/dev/null").trim()
        val hasSuper = superInfo.isNotEmpty()

        if (hasSuper) {
            // Dynamic partitions: check sub-partitions in super
            ShellUtils.fastCmd("dmctl list devices 2>/dev/null")
        }

        // 6. Restart update_engine service
        ShellUtils.fastCmd("stop update_engine 2>/dev/null")
        Thread.sleep(500)
        ShellUtils.fastCmd("start update_engine 2>/dev/null")

        true
    }.getOrDefault(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun UpdateEngineScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    var engineInfo by remember { mutableStateOf(UpdateEngineInfo("Loading...", "N/A", 0f, 0L, "N/A", false)) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showRepairConfirm by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        engineInfo = getUpdateEngineInfo()
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
            // Status Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Status header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (engineInfo.isRunning) {
                                Icons.Filled.Sync
                            } else {
                                Icons.Filled.SystemUpdate
                            },
                            contentDescription = null,
                            tint = when {
                                engineInfo.isRunning -> MaterialTheme.colorScheme.primary
                                engineInfo.currentOperation == "UPDATED_NEED_REBOOT" -> MaterialTheme.colorScheme.tertiary
                                engineInfo.currentOperation == "ERROR" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.update_engine_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = stringResource(R.string.update_engine_status, engineInfo.currentOperation),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // Status badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = when {
                                engineInfo.isRunning -> MaterialTheme.colorScheme.primaryContainer
                                engineInfo.currentOperation == "UPDATED_NEED_REBOOT" -> MaterialTheme.colorScheme.tertiaryContainer
                                engineInfo.currentOperation == "ERROR" -> MaterialTheme.colorScheme.errorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = getLocalizedStatus(engineInfo.currentOperation),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Progress bar when updating
                    if (engineInfo.isRunning && engineInfo.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { engineInfo.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "%.1f%%".format(engineInfo.progress * 100),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    HorizontalDivider()

                    // Detail info rows
                    InfoRow(
                        label = stringResource(R.string.update_engine_last_checked),
                        value = engineInfo.lastCheckedTime
                    )

                    InfoRow(
                        label = stringResource(R.string.update_engine_new_version),
                        value = engineInfo.newVersion
                    )

                    InfoRow(
                        label = stringResource(R.string.update_engine_package_size),
                        value = formatFileSize(engineInfo.newPartitionSize)
                    )
                }
            }

            // Partition Health Card
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
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.update_engine_partition_health),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = stringResource(R.string.update_engine_current_slot, engineInfo.currentSlot),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (engineInfo.isDynamicPartition) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Dynamic",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Boot partition status
                    PartitionStatusRow(
                        name = "boot",
                        isOk = engineInfo.bootPartitionOk,
                        slot = engineInfo.currentSlot
                    )

                    // System partition status
                    PartitionStatusRow(
                        name = "system",
                        isOk = engineInfo.systemPartitionOk,
                        slot = engineInfo.currentSlot
                    )

                    // Vendor partition status
                    PartitionStatusRow(
                        name = "vendor",
                        isOk = engineInfo.vendorPartitionOk,
                        slot = engineInfo.currentSlot
                    )

                    HorizontalDivider()

                    // Repair button
                    OutlinedButton(
                        onClick = { showRepairConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.update_engine_repair_partitions))
                    }
                }
            }

            // Reset Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_engine_actions),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )

                    Text(
                        text = stringResource(R.string.update_engine_reset_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.update_engine_reset))
                    }
                }
            }

            // Refresh button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isRefreshing = true
                        engineInfo = getUpdateEngineInfo()
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.refresh))
            }
        }
    }

    // Reset confirmation dialog
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.update_engine_reset),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Text(text = stringResource(R.string.update_engine_reset_confirm))
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    scope.launch {
                        val success = loadingDialog.withLoading {
                            resetUpdateEngine()
                        }
                        if (success) {
                            engineInfo = getUpdateEngineInfo()
                            snackBarHost.showSnackbar(
                                message = context.getString(R.string.update_engine_reset_success)
                            )
                        } else {
                            snackBarHost.showSnackbar(
                                message = context.getString(R.string.update_engine_reset_failed)
                            )
                        }
                    }
                }) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    // Repair confirmation dialog
    if (showRepairConfirm) {
        AlertDialog(
            onDismissRequest = { showRepairConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.update_engine_repair_partitions),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Text(text = stringResource(R.string.update_engine_repair_confirm))
            },
            confirmButton = {
                TextButton(onClick = {
                    showRepairConfirm = false
                    scope.launch {
                        val success = loadingDialog.withLoading {
                            repairPartitions()
                        }
                        if (success) {
                            engineInfo = getUpdateEngineInfo()
                            snackBarHost.showSnackbar(
                                message = context.getString(R.string.update_engine_repair_success)
                            )
                        } else {
                            snackBarHost.showSnackbar(
                                message = context.getString(R.string.update_engine_repair_failed)
                            )
                        }
                    }
                }) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRepairConfirm = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun getLocalizedStatus(status: String): String {
    val context = LocalContext.current
    return when (status) {
        "IDLE" -> context.getString(R.string.update_engine_idle)
        "VERIFYING" -> context.getString(R.string.update_engine_verifying)
        "UPDATING", "DOWNLOADING" -> context.getString(R.string.update_engine_updating)
        "FINALIZING" -> context.getString(R.string.update_engine_finalizing)
        "UPDATED_NEED_REBOOT" -> context.getString(R.string.update_engine_need_reboot)
        "ERROR" -> context.getString(R.string.update_engine_error)
        "AB_DEVICE" -> context.getString(R.string.update_engine_ab_device)
        "NOT_SUPPORTED" -> context.getString(R.string.update_engine_not_supported)
        "UNKNOWN" -> context.getString(R.string.update_engine_unknown)
        else -> status
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun PartitionStatusRow(name: String, isOk: Boolean, slot: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isOk) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "$name$slot",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isOk) "OK" else "Missing",
            style = MaterialTheme.typography.labelMedium,
            color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
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
                text = stringResource(R.string.update_engine_title),
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
private fun UpdateEnginePreview() {
    UpdateEngineScreen(EmptyDestinationsNavigator)
}
