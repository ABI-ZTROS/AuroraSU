package com.ztros.ztrosu.ui.screen

import android.annotation.SuppressLint
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dergoogler.mmrl.ui.component.LabelItem
import com.dergoogler.mmrl.ui.component.LabelItemDefaults
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ztros.ztrosu.R
import com.ztros.ztrosu.ui.LocalScrollState
import com.ztros.ztrosu.ui.component.SwitchItem
import com.ztros.ztrosu.ui.component.rememberConfirmDialog
import com.ztros.ztrosu.ui.component.rememberLoadingDialog
import com.ztros.ztrosu.ui.rememberScrollConnection
import com.ztros.ztrosu.ui.theme.GREEN
import com.ztros.ztrosu.ui.theme.ORANGE
import com.ztros.ztrosu.ui.theme.RED
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import com.ztros.ztrosu.ui.util.VFSDebugUtil
import com.ztros.ztrosu.ui.util.VFSPolicy
import com.ztros.ztrosu.ui.util.VFSStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun VFSDebugScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    var stats by remember { mutableStateOf(VFSStats()) }
    var policy by remember { mutableStateOf(VFSPolicy()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var autoRefresh by remember { mutableStateOf(true) }

    // Local UI state for policy form
    var localEnabled by remember { mutableStateOf(policy.enabled) }
    var localLogLevel by remember { mutableStateOf(policy.logLevel) }
    var localDefaultAction by remember { mutableStateOf(policy.defaultAction) }
    var localRulesText by remember { mutableStateOf(policy.rules.joinToString("\n")) }

    suspend fun refreshData(silent: Boolean = false) = withContext(Dispatchers.IO) {
        if (!silent) isRefreshing = true
        try {
            stats = VFSDebugUtil.getVFSStats()
            policy = VFSDebugUtil.getVFSPolicy()
            
            if (!silent) {
                localEnabled = policy.enabled
                localLogLevel = policy.logLevel
                localDefaultAction = policy.defaultAction
                localRulesText = policy.rules.joinToString("\n")
            }
        } catch (e: Exception) {
            if (!silent) {
                scope.launch {
                    snackBarHost.showSnackbar(
                        message = "Failed to load: ${e.message}",
                        duration = androidx.compose.material3.SnackbarDuration.Short
                    )
                }
            }
        } finally {
            if (!silent) isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    LaunchedEffect(autoRefresh) {
        if (autoRefresh) {
            while (isActive) {
                refreshData(silent = true)
                delay(1000L)
            }
        }
    }

    suspend fun savePolicy() = withContext(Dispatchers.IO) {
        loadingDialog.show()
        try {
            val newPolicy = VFSPolicy(
                enabled = localEnabled,
                logLevel = localLogLevel,
                defaultAction = localDefaultAction,
                rules = localRulesText.lines().filter { it.isNotBlank() }
            )

            val validation = VFSDebugUtil.validatePolicy(newPolicy)
            if (!validation.first) {
                scope.launch {
                    snackBarHost.showSnackbar(
                        message = validation.second,
                        duration = androidx.compose.material3.SnackbarDuration.Long
                    )
                }
                return@withContext
            }

            val success = VFSDebugUtil.setVFSPolicy(newPolicy)
            if (success) {
                policy = newPolicy
                scope.launch {
                    snackBarHost.showSnackbar(
                        message = context.getString(R.string.vfs_debug_settings_saved),
                        duration = androidx.compose.material3.SnackbarDuration.Short
                    )
                }
            } else {
                scope.launch {
                    snackBarHost.showSnackbar(
                        message = "Failed to save settings",
                        duration = androidx.compose.material3.SnackbarDuration.Long
                    )
                }
            }
        } catch (e: Exception) {
            scope.launch {
                snackBarHost.showSnackbar(
                    message = "Error: ${e.message}",
                    duration = androidx.compose.material3.SnackbarDuration.Long
                )
            }
        } finally {
            loadingDialog.hide()
        }
    }

    suspend fun resetStats() = withContext(Dispatchers.IO) {
        val confirmed = confirmDialog.awaitConfirm(
            title = context.getString(R.string.vfs_debug_clear_stats),
            content = "Are you sure you want to clear all statistics?"
        )
        
        if (confirmed == com.ztros.ztrosu.ui.component.ConfirmResult.Confirmed) {
            loadingDialog.show()
            try {
                val success = VFSDebugUtil.resetStats()
                if (success) {
                    refreshData()
                    scope.launch {
                        snackBarHost.showSnackbar(
                            message = context.getString(R.string.vfs_debug_stats_cleared),
                            duration = androidx.compose.material3.SnackbarDuration.Short
                        )
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    snackBarHost.showSnackbar(
                        message = "Error: ${e.message}",
                        duration = androidx.compose.material3.SnackbarDuration.Long
                    )
                }
            } finally {
                loadingDialog.hide()
            }
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                onBack = dropUnlessResumed { navigator.popBackStack() },
                onRefresh = { scope.launch { refreshData() } },
                isRefreshing = isRefreshing,
                autoRefresh = autoRefresh,
                onToggleAutoRefresh = { autoRefresh = it },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackBarHost, modifier = Modifier.padding(bottom = navBarPadding)) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { scope.launch { savePolicy() } },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.vfs_debug_save))
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        val bottomBarScrollState = LocalScrollState.current
        val bottomBarScrollConnection = bottomBarScrollState?.let {
            rememberScrollConnection(
                isScrollingDown = it.isScrollingDown,
                scrollOffset = it.scrollOffset,
                previousScrollOffset = it.previousScrollOffset,
                threshold = 30f
            )
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .let<Modifier, Modifier> { modifier ->
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
            StatsCard(stats = stats)
            
            Spacer(Modifier.height(8.dp))
            
            ConfigCard(
                policy = policy,
                localEnabled = localEnabled,
                onEnabledChange = { localEnabled = it },
                localLogLevel = localLogLevel,
                onLogLevelChange = { localLogLevel = it },
                localDefaultAction = localDefaultAction,
                onDefaultActionChange = { localDefaultAction = it },
                localRulesText = localRulesText,
                onRulesTextChange = { localRulesText = it },
                onResetStats = { scope.launch { resetStats() } }
            )
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatsCard(stats: VFSStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.vfs_debug_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            
            HorizontalDivider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = stringResource(R.string.vfs_debug_open_count),
                    value = stats.openCount.toString(),
                    color = GREEN
                )
                StatItem(
                    label = "Read",
                    value = stats.readCount.toString(),
                    color = Color(0xFF4CAF50)
                )
                StatItem(
                    label = "Write",
                    value = stats.writeCount.toString(),
                    color = Color(0xFFFF9800)
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Close",
                    value = stats.closeCount.toString(),
                    color = Color(0xFF9C27B0)
                )
                StatItem(
                    label = "Denied",
                    value = stats.deniedCount.toString(),
                    color = RED
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConfigCard(
    policy: VFSPolicy,
    localEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    localLogLevel: Int,
    onLogLevelChange: (Int) -> Unit,
    localDefaultAction: String,
    onDefaultActionChange: (String) -> Unit,
    localRulesText: String,
    onRulesTextChange: (String) -> Unit,
    onResetStats: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Configuration",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            
            HorizontalDivider()
            
            SwitchItem(
                icon = Icons.Filled.ToggleOn,
                title = "Enable VFS Debug",
                summary = "Enable kernel-level VFS monitoring",
                checked = localEnabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Log Level",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "0 = Error, 5 = Verbose",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LogLevelSelector(
                    currentLevel = localLogLevel,
                    onLevelChange = onLogLevelChange
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Default Action",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Action when no rule matches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ActionSelector(
                    currentAction = localDefaultAction,
                    onActionChange = onDefaultActionChange
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                text = "Access Rules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Format: action:path:mode\nExample: deny:/data/data/:rw",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            OutlinedTextField(
                value = localRulesText,
                onValueChange = onRulesTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10,
                shape = RoundedCornerShape(8.dp)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Button(
                onClick = onResetStats,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.vfs_debug_clear_stats))
            }
        }
    }
}

@Composable
private fun LogLevelSelector(currentLevel: Int, onLevelChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val levels = listOf(0, 1, 2, 3, 4, 5)
    
    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = when (currentLevel) {
                    0, 1 -> MaterialTheme.colorScheme.errorContainer
                    2, 3 -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color(0xFFE8F5E9)
                }
            )
        ) {
            Text(text = currentLevel.toString())
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            levels.forEach { level ->
                DropdownMenuItem(
                    text = { Text("Level $level") },
                    onClick = {
                        onLevelChange(level)
                        expanded = false
                    },
                    trailingIcon = {
                        if (level == currentLevel) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionSelector(currentAction: String, onActionChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentAction == "allow") GREEN else RED
            )
        ) {
            Text(text = currentAction.uppercase())
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            listOf("allow", "deny").forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.uppercase()) },
                    onClick = {
                        onActionChange(action)
                        expanded = false
                    },
                    trailingIcon = {
                        if (action == currentAction) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    autoRefresh: Boolean = false,
    onToggleAutoRefresh: (Boolean) -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.vfs_debug_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        },
        actions = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Switch(
                    checked = autoRefresh,
                    onCheckedChange = onToggleAutoRefresh,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}

@Preview
@Composable
private fun VFSDebugPreview() {
    VFSDebugScreen(EmptyDestinationsNavigator)
}
