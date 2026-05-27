package com.ztros.ztrosu.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
 * Get SELinux status via shell command (using libsu ShellUtils)
 */
private suspend fun getSELinuxStatus(): String = withContext(Dispatchers.IO) {
    runCatching {
        ShellUtils.fastCmd("getenforce").trim()
    }.getOrDefault("Unknown")
}

/**
 * Get SELinux version info
 */
private suspend fun getSELinuxVersion(): String = withContext(Dispatchers.IO) {
    runCatching {
        val sestatus = ShellUtils.fastCmd("sestatus 2>/dev/null").trim()
        if (sestatus.isNotBlank()) {
            val versionLine = sestatus.lines().firstOrNull { it.contains("version", ignoreCase = true) }
            versionLine?.substringAfter(":")?.trim() ?: "N/A"
        } else {
            "N/A"
        }
    }.getOrDefault("N/A")
}

/**
 * Get SELinux policy load time
 */
private suspend fun getPolicyLoadTime(): String = withContext(Dispatchers.IO) {
    runCatching {
        val sestatus = ShellUtils.fastCmd("sestatus 2>/dev/null").trim()
        if (sestatus.isNotBlank()) {
            val timeLine = sestatus.lines().firstOrNull { it.contains("loaded", ignoreCase = true) }
            timeLine?.substringAfter(":")?.trim() ?: "N/A"
        } else {
            "N/A"
        }
    }.getOrDefault("N/A")
}

/**
 * Parse SELinux full info
 */
private suspend fun getSELinuxInfo(): SELinuxInfo = withContext(Dispatchers.IO) {
    val status = getSELinuxStatus()
    val version = getSELinuxVersion()
    val policyLoadTime = getPolicyLoadTime()
    
    SELinuxInfo(
        status = status,
        version = version,
        policyLoadTime = policyLoadTime
    )
}

/**
 * Set SELinux mode using KsuCli's proper implementation
 */
private suspend fun setSELinuxMode(enforce: Boolean): Boolean = withContext(Dispatchers.IO) {
    setSelinuxEnforce(enforce)
}

// Get process security context
private suspend fun getProcessContext(pid: Int): String = withContext(Dispatchers.IO) {
    ShellUtils.fastCmd("cat /proc/$pid/attr/current 2>/dev/null").trim()
}

// Get file security context
private suspend fun getFileContext(path: String): String = withContext(Dispatchers.IO) {
    ShellUtils.fastCmd("ls -Zd $path 2>/dev/null").trim()
}

// Set file security context
private suspend fun setFileContext(path: String, context: String): Boolean = withContext(Dispatchers.IO) {
    ShellUtils.fastCmdResult("chcon $context $path 2>/dev/null")
}

// Restore file default security context
private suspend fun restoreFileContext(path: String): Boolean = withContext(Dispatchers.IO) {
    ShellUtils.fastCmdResult("restorecon $path 2>/dev/null")
}

// Get SELinux boolean list
private suspend fun getSELinuxBooleans(): List<Pair<String, Boolean>> = withContext(Dispatchers.IO) {
    runCatching {
        val output = ShellUtils.fastCmd("getsebool -a 2>/dev/null").trim()
        output.lines()
            .filter { it.contains("-->") }
            .map { line ->
                val parts = line.split("-->")
                if (parts.size == 2) {
                    Pair(parts[0].trim(), parts[1].trim() == "on")
                } else null
            }
            .filterNotNull()
    }.getOrDefault(emptyList())
}

// Set SELinux boolean value
private suspend fun setSELinuxBoolean(name: String, value: Boolean): Boolean = withContext(Dispatchers.IO) {
    val valStr = if (value) "1" else "0"
    ShellUtils.fastCmdResult("setsebool $name $valStr 2>/dev/null")
}

// Get SELinux denial logs
private suspend fun getSELinuxDenials(): List<String> = withContext(Dispatchers.IO) {
    runCatching {
        val output = ShellUtils.fastCmd("dmesg | grep -i 'avc:.*denied' 2>/dev/null").trim()
        output.lines().filter { it.isNotBlank() }.takeLast(50)
    }.getOrDefault(emptyList())
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

    // Context viewer state
    var contextInput by rememberSaveable { mutableStateOf("") }
    var contextResult by remember { mutableStateOf("") }
    var isContextLoading by remember { mutableStateOf(false) }

    // SELinux booleans state
    var selinuxBooleans by remember { mutableStateOf(listOf<Pair<String, Boolean>>()) }
    var booleansSearchQuery by remember { mutableStateOf("") }
    var isBooleansLoading by remember { mutableStateOf(false) }

    // AVC denials state
    var avcDenials by remember { mutableStateOf(listOf<String>()) }
    var isDenialsLoading by remember { mutableStateOf(false) }
    var expandedDenialIndex by remember { mutableStateOf(-1) }

    // Context modifier state
    var modifyPath by rememberSaveable { mutableStateOf("") }
    var modifyTargetContext by rememberSaveable { mutableStateOf("") }
    var isModifying by remember { mutableStateOf(false) }

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

            // Security Context Viewer Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.selinux_context_viewer),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    OutlinedTextField(
                        value = contextInput,
                        onValueChange = { contextInput = it },
                        label = { Text(stringResource(R.string.selinux_context_path_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    val contextNotFoundStr = stringResource(R.string.selinux_context_not_found)
                    Button(
                        onClick = {
                            scope.launch {
                                isContextLoading = true
                                contextResult = ""
                                val input = contextInput.trim()
                                if (input.isNotEmpty()) {
                                    contextResult = if (input.all { it.isDigit() }) {
                                        val pid = input.toIntOrNull() ?: -1
                                        if (pid > 0) {
                                            val ctx = getProcessContext(pid)
                                            if (ctx.isNotBlank()) "PID $pid: $ctx"
                                            else contextNotFoundStr
                                        } else contextNotFoundStr
                                    } else {
                                        val ctx = getFileContext(input)
                                        if (ctx.isNotBlank()) ctx
                                        else contextNotFoundStr
                                    }
                                }
                                isContextLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = contextInput.isNotBlank() && !isContextLoading
                    ) {
                        if (isContextLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.selinux_context_view))
                    }
                    if (contextResult.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = contextResult,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // SELinux Booleans Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.selinux_booleans),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isBooleansLoading = true
                                    selinuxBooleans = getSELinuxBooleans()
                                    isBooleansLoading = false
                                }
                            },
                            enabled = !isBooleansLoading
                        ) {
                            if (isBooleansLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = booleansSearchQuery,
                        onValueChange = { booleansSearchQuery = it },
                        label = { Text(stringResource(R.string.selinux_booleans_search)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    val filteredBooleans = if (booleansSearchQuery.isBlank()) {
                        selinuxBooleans
                    } else {
                        selinuxBooleans.filter { it.first.contains(booleansSearchQuery, ignoreCase = true) }
                    }
                    if (filteredBooleans.isEmpty() && !isBooleansLoading) {
                        Text(
                            text = stringResource(R.string.selinux_booleans_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(filteredBooleans) { (name, value) ->
                                var currentValue by remember { mutableStateOf(value) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Switch(
                                        checked = currentValue,
                                        onCheckedChange = { newValue ->
                                            scope.launch {
                                                val success = loadingDialog.withLoading {
                                                    setSELinuxBoolean(name, newValue)
                                                }
                                                if (success) {
                                                    currentValue = newValue
                                                    snackBarHost.showSnackbar(
                                                        message = context.getString(R.string.selinux_boolean_set_success)
                                                    )
                                                } else {
                                                    snackBarHost.showSnackbar(
                                                        message = context.getString(R.string.selinux_boolean_set_failed)
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // AVC Denial Logs Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.selinux_avc_denials),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isDenialsLoading = true
                                    avcDenials = getSELinuxDenials()
                                    isDenialsLoading = false
                                }
                            },
                            enabled = !isDenialsLoading
                        ) {
                            if (isDenialsLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                            }
                        }
                    }
                    if (avcDenials.isEmpty() && !isDenialsLoading) {
                        Text(
                            text = stringResource(R.string.selinux_avc_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(avcDenials) { index, denial ->
                                val isExpanded = expandedDenialIndex == index
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant
                                           else MaterialTheme.colorScheme.surface
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = denial.take(80) + if (denial.length > 80) "..." else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (denial.length > 80) {
                                            TextButton(
                                                onClick = {
                                                    expandedDenialIndex = if (isExpanded) -1 else index
                                                }
                                            ) {
                                                Text(
                                                    text = if (isExpanded) "收起" else "展开",
                                                    style = MaterialTheme.typography.labelSmall
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

            // Security Context Modifier Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.selinux_context_modifier),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    OutlinedTextField(
                        value = modifyPath,
                        onValueChange = { modifyPath = it },
                        label = { Text(stringResource(R.string.selinux_context_path)) },
                        placeholder = { Text(stringResource(R.string.selinux_context_path_hint2)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = modifyTargetContext,
                        onValueChange = { modifyTargetContext = it },
                        label = { Text(stringResource(R.string.selinux_context_target)) },
                        placeholder = { Text(stringResource(R.string.selinux_context_target_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val path = modifyPath.trim()
                                    val ctx = modifyTargetContext.trim()
                                    if (path.isNotBlank() && ctx.isNotBlank()) {
                                        isModifying = true
                                        val success = loadingDialog.withLoading {
                                            setFileContext(path, ctx)
                                        }
                                        isModifying = false
                                        snackBarHost.showSnackbar(
                                            message = if (success) {
                                                context.getString(R.string.selinux_context_apply_success)
                                            } else {
                                                context.getString(R.string.selinux_context_apply_failed)
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = modifyPath.isNotBlank() && modifyTargetContext.isNotBlank() && !isModifying
                        ) {
                            Text(stringResource(R.string.selinux_context_apply))
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val path = modifyPath.trim()
                                    if (path.isNotBlank()) {
                                        isModifying = true
                                        val success = loadingDialog.withLoading {
                                            restoreFileContext(path)
                                        }
                                        isModifying = false
                                        snackBarHost.showSnackbar(
                                            message = if (success) {
                                                context.getString(R.string.selinux_context_restore_success)
                                            } else {
                                                context.getString(R.string.selinux_context_restore_failed)
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = modifyPath.isNotBlank() && !isModifying
                        ) {
                            Text(stringResource(R.string.selinux_context_restore))
                        }
                    }
                }
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