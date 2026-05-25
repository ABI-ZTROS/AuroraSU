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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ztros.ztrosu.Natives
import com.ztros.ztrosu.R
import com.ztros.ztrosu.ui.LocalScrollState
import com.ztros.ztrosu.ui.component.rememberLoadingDialog
import com.ztros.ztrosu.ui.rememberScrollConnection
import com.ztros.ztrosu.ui.util.*
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SELinux status information
 */
private data class SELinuxInfo(
    val status: String,        // Enforcing, Permissive, Disabled
    val version: String,       // SELinux version string
    val policyLoadTime: String // Policy load timestamp
)

/**
 * Get SELinux status via shell command
 */
private suspend fun getSELinuxStatus(): String = withContext(Dispatchers.IO) {
    runCatching {
        ShellUtils.fastCmd("getenforce").trim()
    }.getOrDefault("Unknown")
}

/**
 * Set SELinux enforce mode via shell command
 */
private suspend fun setSELinuxMode(enforce: Boolean): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val valStr = if (enforce) "1" else "0"
        ShellUtils.fastCmdResult("setenforce $valStr")
    }.getOrDefault(false)
}

/**
 * Get SELinux version info
 */
private suspend fun getSELinuxVersion(): String = withContext(Dispatchers.IO) {
    runCatching {
        ShellUtils.fastCmd("sestatus").trim()
    }.getOrDefault("")
}

/**
 * Get SELinux policy load time from /sys/fs/selinux/policy_capabilities or /proc
 */
private suspend fun getPolicyLoadTime(): String = withContext(Dispatchers.IO) {
    runCatching {
        val output = ShellUtils.fastCmd("cat /sys/fs/selinux/policy_capabilities 2>/dev/null || echo ''").trim()
        if (output.isBlank()) {
            // Try reading checkreqprot as a proxy for policy info
            ShellUtils.fastCmd("stat -c '%y' /sys/fs/selinux/policy 2>/dev/null || echo 'N/A'").trim()
        } else {
            "Available"
        }
    }.getOrDefault("N/A")
}

/**
 * Parse SELinux full info
 */
private suspend fun getSELinuxInfo(): SELinuxInfo = withContext(Dispatchers.IO) {
    val status = getSELinuxStatus()

    val version = runCatching {
        val sestatus = ShellUtils.fastCmd("sestatus 2>/dev/null").trim()
        if (sestatus.isNotBlank()) {
            val versionLine = sestatus.lines().firstOrNull { it.contains("version", ignoreCase = true) }
            versionLine?.substringAfter(":")?.trim() ?: "N/A"
        } else {
            "N/A"
        }
    }.getOrDefault("N/A")

    val policyLoadTime = runCatching {
        val sestatus = ShellUtils.fastCmd("sestatus 2>/dev/null").trim()
        if (sestatus.isNotBlank()) {
            val timeLine = sestatus.lines().firstOrNull { it.contains("loaded", ignoreCase = true) }
            timeLine?.substringAfter(":")?.trim() ?: "N/A"
        } else {
            "N/A"
        }
    }.getOrDefault("N/A")

    SELinuxInfo(
        status = status,
        version = version,
        policyLoadTime = policyLoadTime
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SELinuxScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    var selinuxInfo by remember { mutableStateOf(SELinuxInfo("Loading...", "N/A", "N/A")) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Load SELinux info on first composition
    LaunchedEffect(Unit) {
        selinuxInfo = getSELinuxInfo()
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
                    // Current status display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = when (selinuxInfo.status.lowercase()) {
                                "enforcing" -> Icons.Filled.Shield
                                "permissive" -> Icons.Filled.Shield
                                else -> Icons.Filled.Warning
                            },
                            contentDescription = null,
                            tint = when (selinuxInfo.status.lowercase()) {
                                "enforcing" -> MaterialTheme.colorScheme.primary
                                "permissive" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.selinux_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.selinux_status, selinuxInfo.status),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    HorizontalDivider()

                    // Version info
                    InfoRow(
                        label = stringResource(R.string.selinux_version),
                        value = selinuxInfo.version
                    )

                    // Policy load time
                    InfoRow(
                        label = stringResource(R.string.selinux_policy_loaded),
                        value = selinuxInfo.policyLoadTime
                    )
                }
            }

            // Switch Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.selinux_switch_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    val isEnforcing = selinuxInfo.status.equals("Enforcing", ignoreCase = true)
                    val isDisabled = selinuxInfo.status.equals("Disabled", ignoreCase = true)

                    if (isDisabled) {
                        Text(
                            text = stringResource(R.string.selinux_disabled_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        // Current mode indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isEnforcing) {
                                    stringResource(R.string.selinux_enforcing)
                                } else {
                                    stringResource(R.string.selinux_permissive)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isEnforcing) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                }
                            ) {
                                Text(
                                    text = if (isEnforcing) {
                                        stringResource(R.string.selinux_enforcing)
                                    } else {
                                        stringResource(R.string.selinux_permissive)
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isEnforcing) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Switch button
                        Button(
                            onClick = {
                                scope.launch {
                                    val success = loadingDialog.withLoading {
                                        setSELinuxMode(!isEnforcing)
                                    }
                                    if (success) {
                                        selinuxInfo = getSELinuxInfo()
                                        snackBarHost.showSnackbar(
                                            message = context.getString(R.string.selinux_switch_success)
                                        )
                                    } else {
                                        snackBarHost.showSnackbar(
                                            message = context.getString(R.string.selinux_switch_failed)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (isEnforcing) Icons.Filled.LockOpen else Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = if (isEnforcing) {
                                    stringResource(R.string.selinux_switch_to_permissive)
                                } else {
                                    stringResource(R.string.selinux_switch_to_enforcing)
                                }
                            )
                        }
                    }
                }
            }

            // Refresh button
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isRefreshing = true
                        selinuxInfo = getSELinuxInfo()
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
            fontWeight = FontWeight.Medium
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
                text = stringResource(R.string.selinux_title),
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
private fun SELinuxPreview() {
    SELinuxScreen(EmptyDestinationsNavigator)
}
