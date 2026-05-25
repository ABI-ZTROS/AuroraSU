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
    val isRunning: Boolean          // Whether an update is in progress
)

/**
 * Parse update engine status from dumpsys output
 */
private suspend fun getUpdateEngineInfo(): UpdateEngineInfo = withContext(Dispatchers.IO) {
    runCatching {
        val output = ShellUtils.fastCmd("dumpsys update_engine_client").trim()
        if (output.isBlank()) {
            return@withContext UpdateEngineInfo(
                currentOperation = "UNKNOWN",
                lastCheckedTime = "N/A",
                progress = 0f,
                newPartitionSize = 0L,
                newVersion = "N/A",
                isRunning = false
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
                trimmed.contains("CURRENT_OP", ignoreCase = true) -> {
                    val op = trimmed.substringAfter(":").trim()
                    currentOperation = parseOperation(op)
                }
                trimmed.contains("LAST_CHECKED_TIME", ignoreCase = true) ||
                trimmed.contains("last_checked_time", ignoreCase = true) -> {
                    val time = trimmed.substringAfter(":").trim().toLongOrNull()
                    lastCheckedTime = if (time != null && time > 0) {
                        formatTimestamp(time)
                    } else {
                        "N/A"
                    }
                }
                trimmed.contains("PROGRESS", ignoreCase = true) -> {
                    val p = trimmed.substringAfter(":").trim().toFloatOrNull()
                    if (p != null) progress = p
                }
                trimmed.contains("NEW_PARTITION_SIZE", ignoreCase = true) ||
                trimmed.contains("new_partition_size", ignoreCase = true) -> {
                    val size = trimmed.substringAfter(":").trim().toLongOrNull()
                    if (size != null) newPartitionSize = size
                }
                trimmed.contains("NEW_VERSION", ignoreCase = true) ||
                trimmed.contains("new_version", ignoreCase = true) -> {
                    val ver = trimmed.substringAfter(":").trim()
                    if (ver.isNotBlank()) newVersion = ver
                }
            }
        }

        val isRunning = currentOperation != "IDLE" && currentOperation != "ERROR" && currentOperation != "UPDATED_NEED_REBOOT"

        UpdateEngineInfo(
            currentOperation = currentOperation,
            lastCheckedTime = lastCheckedTime,
            progress = progress,
            newPartitionSize = newPartitionSize,
            newVersion = newVersion,
            isRunning = isRunning
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
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
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

            // Reset Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.update_engine_actions),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
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
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
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
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
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
