package com.ztros.ztrosu.ui.screen

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
import androidx.compose.ui.graphics.StrokeCap
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
import com.ztros.ztrosu.ui.component.rememberConfirmDialog
import com.ztros.ztrosu.ui.rememberScrollConnection
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AuditStats(
    val grantedCount: Int = 0,
    val deniedCount: Int = 0,
    val avcDenials: Int = 0,
    val allowedApps: Int = 0,
    val riskScore: Int = 0,
    val isAnomalous: Boolean = false,
    val recentLogs: List<String> = emptyList(),
    val anomalies: List<String> = emptyList()
)

private data class LogEntry(
    val timestamp: String,
    val app: String,
    val action: String,
    val granted: Boolean
)

private suspend fun fetchAuditStats(): AuditStats? = withContext(Dispatchers.IO) {
    runCatching {
        // Read KernelSU log
        val ksuLog = ShellUtils.fastCmd("cat /data/adb/ksu/sulog 2>/dev/null").trim()
        val ksuLines = ksuLog.lines().filter { it.isNotBlank() }.takeLast(100)

        // Read system audit log
        val auditLog = ShellUtils.fastCmd("cat /proc/last_kmsg 2>/dev/null || dmesg 2>/dev/null").trim()
        val auditLines = auditLog.lines()
            .filter { it.contains("avc:", ignoreCase = true) || it.contains("selinux", ignoreCase = true) }
            .takeLast(50)

        // Read KernelSU allowlist
        val allowlist = ShellUtils.fastCmd("cat /data/adb/ksu/.allowlist 2>/dev/null").trim()
        val allowedApps = allowlist.lines().filter { it.isNotBlank() }.size

        // Parse KSU log entries
        val logEntries = mutableListOf<LogEntry>()
        var granted = 0
        var denied = 0
        val anomalies = mutableListOf<String>()

        ksuLines.forEach { line ->
            val isGranted = !line.contains("deny", ignoreCase = true) &&
                !line.contains("reject", ignoreCase = true)
            if (isGranted) granted++ else denied++

            val parts = line.split(Regex("\\s+"), limit = 4)
            val timestamp = if (parts.size >= 1) parts[0] else ""
            val app = if (parts.size >= 2) parts[1] else "unknown"
            val action = if (parts.size >= 3) parts[2] else ""
            logEntries.add(LogEntry(timestamp, app, action, granted = isGranted))
        }

        // Anomaly detection: rapid repeated denials
        val recentDenied = ksuLines.takeLast(20).count {
            it.contains("deny", ignoreCase = true) || it.contains("reject", ignoreCase = true)
        }
        val isAnomalous = recentDenied > 5

        if (recentDenied > 10) {
            anomalies.add("High denial rate detected: $recentDenied denials in recent requests")
        }

        // Check for unknown packages
        val unknownApps = ksuLines.takeLast(50).count {
            it.contains("unknown", ignoreCase = true) || it.contains("null", ignoreCase = true)
        }
        if (unknownApps > 5) {
            anomalies.add("Multiple requests from unidentified sources: $unknownApps")
        }

        val avcDenials = auditLines.size

        // Risk score
        val riskScore = when {
            avcDenials > 100 -> 90
            avcDenials > 50 -> 70
            avcDenials > 20 -> 50
            isAnomalous -> 60
            recentDenied > 10 -> 85
            recentDenied > 5 -> 60
            unknownApps > 5 -> 70
            anomalies.isNotEmpty() -> 50
            else -> 20
        }

        AuditStats(
            grantedCount = granted,
            deniedCount = denied,
            avcDenials = avcDenials,
            allowedApps = allowedApps,
            riskScore = riskScore,
            isAnomalous = isAnomalous,
            recentLogs = (ksuLines.takeLast(15).map { line ->
                val parts = line.split(Regex("\\s+"), limit = 4)
                val timestamp = if (parts.size >= 1) parts[0] else ""
                val app = if (parts.size >= 2) parts[1] else "unknown"
                val action = if (parts.size >= 3) parts[2] else ""
                "[$timestamp] $app: $action"
            } + auditLines).takeLast(30),
            anomalies = anomalies
        )
    }.getOrNull()
}

private suspend fun revokeAllSu(): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ShellUtils.fastCmd("rm -f /data/adb/ksu/.allowlist 2>/dev/null")
        true
    }.getOrDefault(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AuditScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    val riskScoreLabel = stringResource(R.string.audit_risk_score)
    val suStatsLabel = stringResource(R.string.audit_su_stats)
    val grantedLabel = stringResource(R.string.audit_granted)
    val deniedLabel = stringResource(R.string.audit_denied)
    val avcDenialsLabel = stringResource(R.string.audit_avc_denials)
    val allowedAppsLabel = stringResource(R.string.audit_allowed_apps)
    val recentLogsLabel = stringResource(R.string.audit_recent_logs)
    val anomalyLabel = stringResource(R.string.audit_anomaly)
    val revokeAllLabel = stringResource(R.string.audit_revoke_all)
    val revokeConfirmTitle = stringResource(R.string.audit_revoke_confirm_title)
    val revokeConfirmMsg = stringResource(R.string.audit_revoke_confirm_msg)
    val revokeSuccess = stringResource(R.string.audit_revoke_success)
    val revokeFailed = stringResource(R.string.audit_revoke_failed)
    val noAnomalies = stringResource(R.string.audit_no_anomalies)
    val noLogs = stringResource(R.string.audit_no_logs)

    var auditStats by remember { mutableStateOf<AuditStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        auditStats = fetchAuditStats()
        isLoading = false
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            scope.launch {
                val ok = revokeAllSu()
                auditStats = fetchAuditStats()
                snackBarHost.showSnackbar(
                    message = if (ok) revokeSuccess else revokeFailed
                )
            }
        }
    )

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
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (auditStats == null) {
                // Disconnected state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.audit_disconnected),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val stats = auditStats!!
                // Risk Score Card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = riskScoreLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Box(
                            modifier = Modifier.size(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { stats.riskScore / 100f },
                                modifier = Modifier.size(120.dp),
                                strokeWidth = 8.dp,
                                strokeCap = StrokeCap.Round,
                                color = when {
                                    stats.riskScore >= 80 -> MaterialTheme.colorScheme.error
                                    stats.riskScore >= 50 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                            Text(
                                text = "${stats.riskScore}",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    stats.riskScore >= 80 -> MaterialTheme.colorScheme.error
                                    stats.riskScore >= 50 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }

                // SU Stats Card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = {
                                Text(
                                    text = suStatsLabel,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Security, contentDescription = null)
                            }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${stats.grantedCount}",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = grantedLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${stats.deniedCount}",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = deniedLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${stats.avcDenials}",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = avcDenialsLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${stats.allowedApps}",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = allowedAppsLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Anomaly Detection Card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = {
                                Text(
                                    text = anomalyLabel,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = if (stats.anomalies.isNotEmpty())
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        if (stats.anomalies.isNotEmpty()) {
                            stats.anomalies.forEach { anomaly ->
                                Text(
                                    text = anomaly,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Text(
                                text = noAnomalies,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Recent Logs Card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = {
                                Text(
                                    text = recentLogsLabel,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Filled.History, contentDescription = null)
                            }
                        )
                        if (stats.recentLogs.isNotEmpty()) {
                            stats.recentLogs.forEach { log ->
                                Text(
                                    text = log,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        } else {
                            Text(
                                text = noLogs,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Revoke All Button
                Button(
                    onClick = {
                        confirmDialog.showConfirm(
                            title = revokeConfirmTitle,
                            content = revokeConfirmMsg
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(revokeAllLabel)
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
                text = stringResource(R.string.audit_title),
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
private fun AuditPreview() {
    AuditScreen(EmptyDestinationsNavigator)
}
