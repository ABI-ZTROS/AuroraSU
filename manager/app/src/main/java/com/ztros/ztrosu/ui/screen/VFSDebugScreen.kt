@file:Suppress("FunctionName")

package com.ztros.ztrosu.ui.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ztros.ztrosu.R
import com.ztros.ztrosu.ui.LocalScrollState
import com.ztros.ztrosu.ui.component.ConfirmResult
import com.ztros.ztrosu.ui.component.GlassCard
import com.ztros.ztrosu.ui.component.SwitchItem
import com.ztros.ztrosu.ui.component.rememberConfirmDialog
import com.ztros.ztrosu.ui.component.rememberLoadingDialog
import com.ztros.ztrosu.ui.rememberScrollConnection
import com.ztros.ztrosu.ui.theme.GREEN
import com.ztros.ztrosu.ui.theme.ORANGE
import com.ztros.ztrosu.ui.theme.RED
import com.ztros.ztrosu.ui.util.VFSKernelInterface.CommChannel
import com.ztros.ztrosu.ui.util.HookMode
import com.ztros.ztrosu.ui.util.HookType
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import com.ztros.ztrosu.ui.util.RuleAction
import com.ztros.ztrosu.ui.util.VFSBackend
import com.ztros.ztrosu.ui.util.VFSDebugUtil
import com.ztros.ztrosu.ui.util.VFSHookManager
import com.ztros.ztrosu.ui.util.VFSHookTarget
import com.ztros.ztrosu.ui.util.VFSKernelInterface
import com.ztros.ztrosu.ui.util.VFSNetlinkListener
import com.ztros.ztrosu.ui.util.VFSOp
import com.ztros.ztrosu.ui.util.VFSRule
import com.ztros.ztrosu.ui.util.VFSRuleEngine
import com.ztros.ztrosu.ui.util.VFSStats
import com.ztros.ztrosu.ui.util.VFSPolicy
import com.ztros.ztrosu.ui.util.VFSTemplate
import com.ztros.ztrosu.ui.util.VFSTemplateManager
import com.ztros.ztrosu.ui.util.VFSProtocolTranslator
import com.ztros.ztrosu.ui.util.VFSEvent
import com.ztros.ztrosu.ui.util.EVENT_VFS_OPEN
import com.ztros.ztrosu.ui.util.EVENT_VFS_READ
import com.ztros.ztrosu.ui.util.EVENT_VFS_WRITE
import com.ztros.ztrosu.ui.util.EVENT_VFS_CLOSE
import com.ztros.ztrosu.ui.util.EVENT_VFS_DENY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==================== Main Screen ====================

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun VFSDebugScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        if (isNavBarHidden) 0.dp else 112.dp

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // ==================== Shared State ====================

    var stats by remember { mutableStateOf<VFSStats?>(null) }
    var policy by remember { mutableStateOf<VFSPolicy?>(null) }
    var backend by remember { mutableStateOf<VFSBackend?>(null) }
    var channel by remember { mutableStateOf<CommChannel?>(null) }
    var moduleVersion by remember { mutableStateOf<Int?>(null) }

    // Local policy form state
    var localEnabled by remember { mutableStateOf(false) }
    var localLogLevel by remember { mutableIntStateOf(0) }
    var localDefaultAction by remember { mutableStateOf("allow") }

    // Hook targets
    var hookTargets by remember { mutableStateOf<List<VFSHookTarget>>(emptyList()) }

    // Rules
    var rules by remember { mutableStateOf<List<VFSRule>>(emptyList()) }

    // Templates
    var templates by remember { mutableStateOf<List<VFSTemplate>>(emptyList()) }
    var activeTemplateId by remember { mutableStateOf<String?>(null) }

    // Events
    var events by remember { mutableStateOf<List<VFSEvent>>(emptyList()) }
    var isListening by remember { mutableStateOf(false) }

    // Loading states
    var isRefreshing by remember { mutableStateOf(false) }

    // ==================== Data Loading ====================

    suspend fun refreshData(silent: Boolean = false) = withContext(Dispatchers.IO) {
        if (!silent) isRefreshing = true
        try {
            // Reset cached detections to ensure fresh results
            VFSDebugUtil.resetBackend()
            VFSKernelInterface.resetChannelCache()

            Log.d("VFSDebug", "refreshData: starting detection...")
            backend = VFSDebugUtil.detectBackend()
            Log.d("VFSDebug", "refreshData: backend=$backend")
            stats = VFSDebugUtil.getVFSStats()
            policy = VFSDebugUtil.getVFSPolicy()
            moduleVersion = try { VFSKernelInterface.getVersion() } catch (e: Exception) { Log.e("VFSDebug", "getVersion failed", e); null }
            Log.d("VFSDebug", "refreshData: moduleVersion=$moduleVersion")
            channel = try { VFSKernelInterface.detectBestChannel() } catch (_: Exception) { null }
            Log.d("VFSDebug", "refreshData: channel=$channel")

            localEnabled = policy?.enabled ?: false
            localLogLevel = policy?.logLevel ?: 0
            localDefaultAction = policy?.defaultAction ?: "allow"

            hookTargets = VFSHookManager.getHookTargets()
            rules = VFSRuleEngine.getRules()
            templates = VFSTemplateManager.getAllTemplates()
            activeTemplateId = try { VFSTemplateManager.getActiveTemplateId() } catch (_: Exception) { null }

            if (isListening) {
                events = VFSNetlinkListener.getBufferedEvents()
            }
        } catch (_: Exception) {
            // silent
        } finally {
            if (!silent) isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        VFSRuleEngine.initialize()
        VFSTemplateManager.initialize()
        refreshData()
    }

    // Auto-refresh
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(2000L)
            refreshData(silent = true)
        }
    }

    // ==================== Actions ====================

    suspend fun savePolicy() = withContext(Dispatchers.IO) {
        loadingDialog.show()
        try {
            val newPolicy = VFSPolicy(
                enabled = localEnabled,
                logLevel = localLogLevel,
                defaultAction = localDefaultAction,
                rules = policy?.rules ?: emptyList()
            )
            val validation = VFSDebugUtil.validatePolicy(newPolicy)
            if (!validation.first) {
                scope.launch { snackBarHost.showSnackbar(validation.second, duration = SnackbarDuration.Long) }
                return@withContext
            }
            val success = VFSDebugUtil.setVFSPolicy(newPolicy)
            if (success) {
                policy = newPolicy
                scope.launch { snackBarHost.showSnackbar(context.getString(R.string.vfs_debug_settings_saved), duration = SnackbarDuration.Short) }
            }
        } catch (_: Exception) {
        } finally {
            loadingDialog.hide()
        }
    }

    suspend fun resetStats() = withContext(Dispatchers.IO) {
        val confirmed = confirmDialog.awaitConfirm(
            title = context.getString(R.string.vfs_debug_clear_stats),
            content = context.getString(R.string.vfs_clear_all_confirm).format("statistics")
        )
        if (confirmed == ConfirmResult.Confirmed) {
            loadingDialog.show()
            try {
                VFSDebugUtil.resetStats()
                refreshData()
                scope.launch { snackBarHost.showSnackbar(context.getString(R.string.vfs_debug_stats_cleared), duration = SnackbarDuration.Short) }
            } catch (_: Exception) {
            } finally {
                loadingDialog.hide()
            }
        }
    }

    suspend fun addHook(type: HookType, identifier: String, mode: HookMode) = withContext(Dispatchers.IO) {
        loadingDialog.show()
        try {
            val target = when (type) {
                HookType.PID -> {
                    val pid = identifier.toIntOrNull() ?: return@withContext
                    VFSHookManager.addPidHook(pid, mode)
                }
                HookType.PACKAGE -> {
                    VFSHookManager.addPackageHook(identifier, mode)
                }
            }
            if (target != null) {
                hookTargets = VFSHookManager.getHookTargets()
                scope.launch { snackBarHost.showSnackbar(context.getString(R.string.vfs_hook_added), duration = SnackbarDuration.Short) }
            } else {
                scope.launch { snackBarHost.showSnackbar(context.getString(R.string.vfs_hook_add_failed), duration = SnackbarDuration.Short) }
            }
        } catch (_: Exception) {
        } finally {
            loadingDialog.hide()
        }
    }

    suspend fun removeHook(id: String) = withContext(Dispatchers.IO) {
        VFSHookManager.removePidHook(id)
        hookTargets = VFSHookManager.getHookTargets()
        scope.launch { snackBarHost.showSnackbar(context.getString(R.string.vfs_hook_removed), duration = SnackbarDuration.Short) }
    }

    suspend fun toggleHookEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        VFSHookManager.toggleHook(id, enabled)
        hookTargets = VFSHookManager.getHookTargets()
    }

    suspend fun clearAllHooks() = withContext(Dispatchers.IO) {
        val confirmed = confirmDialog.awaitConfirm(
            title = context.getString(R.string.vfs_clear_all_hooks),
            content = context.getString(R.string.vfs_clear_all_confirm).format("hooks")
        )
        if (confirmed == ConfirmResult.Confirmed) {
            loadingDialog.show()
            try {
                VFSHookManager.clearAll()
                hookTargets = VFSHookManager.getHookTargets()
            } catch (_: Exception) {
            } finally {
                loadingDialog.hide()
            }
        }
    }

    suspend fun addRule(rule: VFSRule) = withContext(Dispatchers.IO) {
        loadingDialog.show()
        try {
            val validation = VFSRuleEngine.validateRule(rule)
            if (!validation.first) {
                scope.launch { snackBarHost.showSnackbar(validation.second, duration = SnackbarDuration.Long) }
                return@withContext
            }
            VFSRuleEngine.addRule(rule)
            rules = VFSRuleEngine.getRules()
            scope.launch { snackBarHost.showSnackbar(context.getString(R.string.vfs_rule_added), duration = SnackbarDuration.Short) }
        } catch (_: Exception) {
        } finally {
            loadingDialog.hide()
        }
    }

    suspend fun deleteRule(ruleId: String) = withContext(Dispatchers.IO) {
        VFSRuleEngine.deleteRule(ruleId)
        rules = VFSRuleEngine.getRules()
        scope.launch { snackBarHost.showSnackbar(context.getString(R.string.vfs_rule_deleted), duration = SnackbarDuration.Short) }
    }

    suspend fun clearAllRules() = withContext(Dispatchers.IO) {
        val confirmed = confirmDialog.awaitConfirm(
            title = context.getString(R.string.vfs_clear_all_rules),
            content = context.getString(R.string.vfs_clear_all_confirm).format("rules")
        )
        if (confirmed == ConfirmResult.Confirmed) {
            loadingDialog.show()
            try {
                VFSRuleEngine.clearRules()
                rules = VFSRuleEngine.getRules()
            } catch (_: Exception) {
            } finally {
                loadingDialog.hide()
            }
        }
    }

    suspend fun applyTemplate(templateId: String) = withContext(Dispatchers.IO) {
        loadingDialog.show()
        try {
            val success = VFSTemplateManager.applyTemplate(templateId)
            if (success) {
                activeTemplateId = VFSTemplateManager.getActiveTemplateId()
                refreshData()
                scope.launch { snackBarHost.showSnackbar(context.getString(R.string.vfs_template_applied), duration = SnackbarDuration.Short) }
            } else {
                scope.launch { snackBarHost.showSnackbar(context.getString(R.string.vfs_template_apply_failed), duration = SnackbarDuration.Short) }
            }
        } catch (_: Exception) {
        } finally {
            loadingDialog.hide()
        }
    }

    suspend fun deleteTemplate(templateId: String) = withContext(Dispatchers.IO) {
        VFSTemplateManager.deleteTemplate(templateId)
        templates = VFSTemplateManager.getAllTemplates()
    }

    fun startEventListening() {
        VFSDebugUtil.startEventStream { event ->
            events = VFSNetlinkListener.getBufferedEvents()
        }
        isListening = true
    }

    fun stopEventListening() {
        VFSDebugUtil.stopEventStream()
        isListening = false
    }

    // ==================== Scaffold ====================

    val tabTitles = listOf(
        R.string.vfs_tab_dashboard,
        R.string.vfs_tab_hooks,
        R.string.vfs_tab_rules,
        R.string.vfs_tab_templates,
        R.string.vfs_tab_events,
        R.string.vfs_tab_protocol,
        R.string.vfs_tab_audit,
        R.string.vfs_tab_spoof
    )

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.vfs_debug_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = dropUnlessResumed { navigator.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { scope.launch { refreshData() } },
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Analytics, contentDescription = null)
                            }
                        }
                    },
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    scrollBehavior = scrollBehavior
                )
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) {
                    tabTitles.forEachIndexed { index, titleRes ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(stringResource(titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackBarHost, modifier = Modifier.padding(bottom = navBarPadding)) },
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

        Box(
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
                .fillMaxSize()
        ) {
            when (selectedTabIndex) {
                0 -> DashboardTab(
                    stats = stats ?: VFSStats(),
                    policy = policy ?: VFSPolicy(),
                    backend = backend ?: VFSBackend.MOCK,
                    channel = channel,
                    moduleVersion = moduleVersion,
                    hookTargets = hookTargets,
                    rules = rules,
                    events = events,
                    localEnabled = localEnabled,
                    localLogLevel = localLogLevel,
                    localDefaultAction = localDefaultAction,
                    onEnabledChange = { localEnabled = it; scope.launch { savePolicy() } },
                    onLogLevelChange = { localLogLevel = it; scope.launch { savePolicy() } },
                    onDefaultActionChange = { localDefaultAction = it; scope.launch { savePolicy() } },
                    onResetStats = { scope.launch { resetStats() } },
                    onExportLogs = {
                        scope.launch {
                            snackBarHost.showSnackbar("Logs exported", duration = SnackbarDuration.Short)
                        }
                    },
                    onNavigateToProtocol = { selectedTabIndex = 5 }
                )
                1 -> HooksTab(
                    hookTargets = hookTargets,
                    onAddHook = { type, identifier, mode -> scope.launch { addHook(type, identifier, mode) } },
                    onRemoveHook = { scope.launch { removeHook(it) } },
                    onToggleHook = { id, enabled -> scope.launch { toggleHookEnabled(id, enabled) } },
                    onClearAll = { scope.launch { clearAllHooks() } }
                )
                2 -> RulesTab(
                    rules = rules,
                    onAddRule = { scope.launch { addRule(it) } },
                    onDeleteRule = { scope.launch { deleteRule(it) } },
                    onClearAll = { scope.launch { clearAllRules() } }
                )
                3 -> TemplatesTab(
                    templates = templates,
                    activeTemplateId = activeTemplateId,
                    onApplyTemplate = { scope.launch { applyTemplate(it) } },
                    onDeleteTemplate = { scope.launch { deleteTemplate(it) } }
                )
                4 -> EventsTab(
                    events = events,
                    isListening = isListening,
                    onStartListening = { startEventListening() },
                    onStopListening = { stopEventListening() },
                    onClearEvents = { VFSNetlinkListener.clearBuffer(); events = emptyList() }
                )
                5 -> ProtocolTab(
                    events = events,
                    channel = channel,
                    moduleVersion = moduleVersion,
                    onNavigateToProtocol = { selectedTabIndex = 5 }
                )
                6 -> AuditTab()
                7 -> IdentitySpoofTab()
            }
        }
    }
}

// ==================== Tab 1: Dashboard ====================

@Composable
private fun DashboardTab(
    stats: VFSStats,
    policy: VFSPolicy,
    backend: VFSBackend,
    channel: CommChannel?,
    moduleVersion: Int?,
    hookTargets: List<VFSHookTarget>,
    rules: List<VFSRule>,
    events: List<VFSEvent>,
    localEnabled: Boolean,
    localLogLevel: Int,
    localDefaultAction: String,
    onEnabledChange: (Boolean) -> Unit,
    onLogLevelChange: (Int) -> Unit,
    onDefaultActionChange: (String) -> Unit,
    onResetStats: () -> Unit,
    onExportLogs: () -> Unit,
    onNavigateToProtocol: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { BackendStatusCard(backend, channel, moduleVersion) }
        item { StatsOverviewCard(stats) }
        item { QuickSummaryRow(hookTargets, rules, events) }
        item { QuickConfigCard(localEnabled, localLogLevel, localDefaultAction, onEnabledChange, onLogLevelChange, onDefaultActionChange) }
        item { ActionButtonsRow(onResetStats, onExportLogs) }
        item { ProtocolDebugCard(channel, moduleVersion, onNavigateToProtocol) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun BackendStatusCard(backend: VFSBackend, channel: CommChannel?, moduleVersion: Int?) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.vfs_backend_status),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getBackendName(backend),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(getBackendColor(backend).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = getBackendColor(backend)
                )
                if (channel != null) {
                    Text(
                        text = getChannelName(channel),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(getChannelColor(channel).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = getChannelColor(channel)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.vfs_disconnected),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(RED.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = RED
                    )
                }
                if (moduleVersion != null) {
                    Text(
                        text = "v${moduleVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.vfs_module_not_loaded),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(RED.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = RED
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsOverviewCard(stats: VFSStats) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.vfs_debug_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(stringResource(R.string.vfs_debug_open_count), stats.openCount.toString(), GREEN)
                    StatItem(stringResource(R.string.vfs_read_count), stats.readCount.toString(), Color(0xFF4CAF50))
                    StatItem(stringResource(R.string.vfs_write_count), stats.writeCount.toString(), ORANGE)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(stringResource(R.string.vfs_close_count), stats.closeCount.toString(), Color(0xFF9C27B0))
                    StatItem(stringResource(R.string.vfs_denied_count), stats.deniedCount.toString(), RED)
                    StatItem(
                        stringResource(R.string.vfs_total_ops),
                        (stats.openCount + stats.readCount + stats.writeCount + stats.closeCount).toString(),
                        MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSummaryRow(hookTargets: List<VFSHookTarget>, rules: List<VFSRule>, events: List<VFSEvent>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickSummaryCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.vfs_hook_targets_count),
            value = hookTargets.size.toString(),
            color = MaterialTheme.colorScheme.primary
        )
        QuickSummaryCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.vfs_active_rules),
            value = rules.count { it.enabled }.toString(),
            color = GREEN
        )
        QuickSummaryCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.vfs_events_received),
            value = events.size.toString(),
            color = ORANGE
        )
    }
}

@Composable
private fun QuickSummaryCard(modifier: Modifier, label: String, value: String, color: Color) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickConfigCard(
    localEnabled: Boolean,
    localLogLevel: Int,
    localDefaultAction: String,
    onEnabledChange: (Boolean) -> Unit,
    onLogLevelChange: (Int) -> Unit,
    onDefaultActionChange: (String) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.vfs_config_enable),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            HorizontalDivider()
            SwitchItem(
                icon = Icons.Filled.ToggleOn,
                title = stringResource(R.string.vfs_config_enable),
                summary = stringResource(R.string.vfs_config_enable_summary),
                checked = localEnabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.vfs_config_log_level), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = stringResource(R.string.vfs_config_log_level_summary), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LogLevelSelector(currentLevel = localLogLevel, onLevelChange = onLogLevelChange)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.vfs_config_default_action), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = stringResource(R.string.vfs_config_default_action_summary), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ActionSelector(currentAction = localDefaultAction, onActionChange = onDefaultActionChange)
            }
        }
    }
}

@Composable
private fun ActionButtonsRow(onResetStats: () -> Unit, onExportLogs: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onResetStats,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.vfs_debug_clear_stats))
        }
        OutlinedButton(
            onClick = onExportLogs,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.vfs_debug_export_logs))
        }
    }
}

// ==================== Tab 2: Hook Targets ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HooksTab(
    hookTargets: List<VFSHookTarget>,
    onAddHook: (HookType, String, HookMode) -> Unit,
    onRemoveHook: (String) -> Unit,
    onToggleHook: (String, Boolean) -> Unit,
    onClearAll: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableIntStateOf(0) } // 0=All, 1=Enabled, 2=Disabled

    val filteredTargets = remember(hookTargets, searchQuery, filterMode) {
        hookTargets.filter { target ->
            val matchesSearch = searchQuery.isBlank() ||
                target.identifier.contains(searchQuery, ignoreCase = true) ||
                target.processName.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (filterMode) {
                0 -> true
                1 -> target.enabled
                2 -> !target.enabled
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.vfs_search_targets)) },
            leadingIcon = { Icon(Icons.Filled.FilterAlt, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterMode == 0,
                onClick = { filterMode = 0 },
                label = { Text(stringResource(R.string.vfs_all)) }
            )
            FilterChip(
                selected = filterMode == 1,
                onClick = { filterMode = 1 },
                label = { Text(stringResource(R.string.vfs_enabled_only)) }
            )
            FilterChip(
                selected = filterMode == 2,
                onClick = { filterMode = 2 },
                label = { Text(stringResource(R.string.vfs_disabled_only)) }
            )
            Spacer(Modifier.width(8.dp))
            if (hookTargets.isNotEmpty()) {
                FilterChip(
                    selected = false,
                    onClick = onClearAll,
                    label = { Text(stringResource(R.string.vfs_clear_all_hooks)) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (filteredTargets.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = stringResource(R.string.vfs_no_hooks), style = MaterialTheme.typography.titleMedium)
                    Text(text = stringResource(R.string.vfs_no_hooks_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTargets, key = { it.id }) { target ->
                    HookTargetCard(
                        target = target,
                        onToggle = { onToggleHook(target.id, !target.enabled) },
                        onRemove = { onRemoveHook(target.id) }
                    )
                }
            }
        }
    }

    // FAB
    FloatingActionButton(
        onClick = { showAddDialog = true },
        modifier = Modifier
            .padding(16.dp)
            .align(Alignment.BottomEnd),
        containerColor = MaterialTheme.colorScheme.primary
    ) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.vfs_add_hook))
    }
    }

    // Add Hook Dialog
    if (showAddDialog) {
        AddHookDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { type, identifier, mode ->
                showAddDialog = false
                onAddHook(type, identifier, mode)
            }
        )
    }
}

@Composable
private fun HookTargetCard(target: VFSHookTarget, onToggle: () -> Unit, onRemove: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Type badge
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                when (target.type) {
                                    HookType.PID -> stringResource(R.string.vfs_hook_type_pid)
                                    HookType.PACKAGE -> stringResource(R.string.vfs_hook_type_package)
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when (target.type) {
                                HookType.PID -> MaterialTheme.colorScheme.primaryContainer
                                HookType.PACKAGE -> MaterialTheme.colorScheme.tertiaryContainer
                            }
                        )
                    )
                    // Identifier
                    Text(
                        text = if (target.processName.isNotEmpty()) target.processName else target.identifier,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mode chip
                    Text(
                        text = getHookModeName(target.mode),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    // Enable switch
                    Switch(checked = target.enabled, onCheckedChange = { onToggle() })
                    // Delete button
                    IconButton(onClick = onRemove, colors = IconButtonDefaults.iconButtonColors(contentColor = RED)) {
                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
            // Subtitle: UID info
            Text(
                text = "UID: ${target.uid} | ${if (target.enabled) stringResource(R.string.vfs_hook_enabled) else stringResource(R.string.vfs_hook_disabled)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddHookDialog(onDismiss: () -> Unit, onConfirm: (HookType, String, HookMode) -> Unit) {
    var selectedType by remember { mutableStateOf(HookType.PID) }
    var identifier by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(HookMode.MONITOR_ONLY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vfs_add_hook), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Type selection
                Text(text = stringResource(R.string.vfs_add_hook), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedType == HookType.PID,
                        onClick = { selectedType = HookType.PID },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(stringResource(R.string.vfs_add_hook_pid)) }
                    SegmentedButton(
                        selected = selectedType == HookType.PACKAGE,
                        onClick = { selectedType = HookType.PACKAGE },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(stringResource(R.string.vfs_add_hook_package)) }
                }

                // Identifier input
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (selectedType == HookType.PID) stringResource(R.string.vfs_hook_type_pid) else stringResource(R.string.vfs_hook_type_package)) },
                    placeholder = {
                        Text(if (selectedType == HookType.PID) stringResource(R.string.vfs_pid_hint) else stringResource(R.string.vfs_package_hint))
                    },
                    singleLine = true,
                    keyboardOptions = if (selectedType == HookType.PID) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default
                )

                // Mode selection
                Text(text = stringResource(R.string.vfs_select_hook_mode), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                HookModeSelector(selectedMode = selectedMode, onModeChange = { selectedMode = it })
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedType, identifier.trim(), selectedMode) },
                enabled = identifier.trim().isNotEmpty()
            ) { Text(stringResource(R.string.vfs_debug_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(android.R.string.cancel.toString()) }
        }
    )
}

@Composable
private fun HookModeSelector(selectedMode: HookMode, onModeChange: (HookMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val modes = HookMode.entries

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(getHookModeName(selectedMode))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            modes.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(getHookModeName(mode)) },
                    onClick = { onModeChange(mode); expanded = false },
                    trailingIcon = { if (mode == selectedMode) Icon(Icons.Filled.Check, contentDescription = null) else null }
                )
            }
        }
    }
}

// ==================== Tab 3: Rules Engine ====================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesTab(
    rules: List<VFSRule>,
    onAddRule: (VFSRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onClearAll: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<VFSRule?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { editingRule = null; showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.vfs_add_rule))
            }
            Spacer(Modifier.weight(1f))
            if (rules.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(stringResource(R.string.vfs_clear_all_rules), color = RED)
                }
            }
        }

        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = stringResource(R.string.vfs_no_rules), style = MaterialTheme.typography.titleMedium)
                    Text(text = stringResource(R.string.vfs_no_rules_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onEdit = { editingRule = rule; showAddDialog = true },
                        onDelete = { onDeleteRule(rule.id) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        RuleDialog(
            rule = editingRule,
            onDismiss = { showAddDialog = false; editingRule = null },
            onConfirm = { rule ->
                showAddDialog = false
                editingRule = null
                onAddRule(rule)
            }
        )
    }
}

@Composable
private fun RuleCard(rule: VFSRule, onEdit: () -> Unit, onDelete: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Action badge
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                getRuleActionName(rule.action),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = when (rule.action) {
                                RuleAction.ALLOW -> GREEN.copy(alpha = 0.15f)
                                RuleAction.DENY -> RED.copy(alpha = 0.15f)
                                RuleAction.LOG_ONLY -> MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                    )
                    // Path pattern
                    Text(
                        text = rule.pathPattern,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                IconButton(onClick = onDelete, colors = IconButtonDefaults.iconButtonColors(contentColor = RED)) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            // Details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // UID filter
                if (rule.uidFilter != null) {
                    Text(
                        text = "UID: ${rule.uidFilter}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Operation type chips
                rule.opTypes.forEach { op ->
                    Text(
                        text = getOpShortName(op),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Priority
                Text(
                    text = "P${rule.priority}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Enabled indicator
                if (!rule.enabled) {
                    Text(
                        text = stringResource(R.string.vfs_hook_disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = RED
                    )
                }
            }

            // Description
            rule.description?.let { desc ->
                if (desc.isNotEmpty()) {
                    Text(text = desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun RuleDialog(rule: VFSRule?, onDismiss: () -> Unit, onConfirm: (VFSRule) -> Unit) {
    var action by remember { mutableStateOf(rule?.action ?: RuleAction.DENY) }
    var pathPattern by remember { mutableStateOf(rule?.pathPattern ?: "") }
    var uidFilterText by remember { mutableStateOf(rule?.uidFilter?.toString() ?: "") }
    var selectedOps by remember { mutableStateOf(rule?.opTypes?.toMutableSet() ?: mutableSetOf(VFSOp.READ, VFSOp.WRITE)) }
    var priority by remember { mutableStateOf(rule?.priority ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (rule != null) stringResource(R.string.vfs_edit_rule) else stringResource(R.string.vfs_add_rule),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Action selector
                Text(text = stringResource(R.string.vfs_rule_action_allow) + "/" + stringResource(R.string.vfs_rule_action_deny) + "/" + stringResource(R.string.vfs_rule_action_log), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(RuleAction.ALLOW, RuleAction.DENY, RuleAction.LOG_ONLY).forEachIndexed { index, act ->
                        SegmentedButton(
                            selected = action == act,
                            onClick = { action = act },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                        ) { Text(getRuleActionName(act)) }
                    }
                }

                // Path pattern
                OutlinedTextField(
                    value = pathPattern,
                    onValueChange = { pathPattern = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.vfs_rule_path)) },
                    placeholder = { Text(stringResource(R.string.vfs_rule_path_hint)) },
                    singleLine = true
                )

                // UID filter
                OutlinedTextField(
                    value = uidFilterText,
                    onValueChange = { uidFilterText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.vfs_rule_uid_filter)) },
                    placeholder = { Text("Optional") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Operation type checkboxes
                Text(text = stringResource(R.string.vfs_rule_ops), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VFSOp.entries.forEach { op ->
                        FilterChip(
                            selected = op in selectedOps,
                            onClick = {
                                if (op in selectedOps) {
                                    selectedOps = selectedOps.toMutableSet().also { it.remove(op) }
                                } else {
                                    selectedOps = selectedOps.toMutableSet().also { it.add(op) }
                                }
                            },
                            label = { Text(getOpName(op)) }
                        )
                    }
                }

                // Priority
                OutlinedTextField(
                    value = priority.toString(),
                    onValueChange = { priority = it.toIntOrNull() ?: 0 },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.vfs_rule_priority)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newRule = VFSRule(
                        id = rule?.id ?: java.util.UUID.randomUUID().toString(),
                        action = action,
                        pathPattern = pathPattern,
                        uidFilter = uidFilterText.toIntOrNull(),
                        opTypes = selectedOps,
                        priority = priority,
                        enabled = rule?.enabled ?: true,
                        createdAt = rule?.createdAt ?: System.currentTimeMillis()
                    )
                    onConfirm(newRule)
                },
                enabled = pathPattern.isNotBlank() && selectedOps.isNotEmpty()
            ) { Text(stringResource(R.string.vfs_debug_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(android.R.string.cancel.toString()) }
        }
    )
}

// ==================== Tab 4: Templates ====================

@Composable
private fun TemplatesTab(
    templates: List<VFSTemplate>,
    activeTemplateId: String?,
    onApplyTemplate: (String) -> Unit,
    onDeleteTemplate: (String) -> Unit
) {
    var expandedTemplateId by remember { mutableStateOf<String?>(null) }

    if (templates.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No templates", style = MaterialTheme.typography.titleMedium)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(templates, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    isActive = template.id == activeTemplateId,
                    isExpanded = expandedTemplateId == template.id,
                    onApply = { onApplyTemplate(template.id) },
                    onDelete = { onDeleteTemplate(template.id) },
                    onToggleExpand = {
                        expandedTemplateId = if (expandedTemplateId == template.id) null else template.id
                    }
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: VFSTemplate,
    isActive: Boolean,
    isExpanded: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    onToggleExpand: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = template.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    // Built-in / Custom badge
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (template.isBuiltIn) stringResource(R.string.vfs_template_builtin) else stringResource(R.string.vfs_template_custom),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (template.isBuiltIn) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                    if (isActive) {
                        AssistChip(
                            onClick = {},
                            label = { Text(stringResource(R.string.vfs_template_active), style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = GREEN.copy(alpha = 0.15f))
                        )
                    }
                }
            }

            // Description
            if (template.description.isNotEmpty()) {
                Text(text = template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Stats row
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.vfs_template_hooks_count, template.hookTargets.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.vfs_template_rules_count, template.rules.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expand toggle
            if (template.hookTargets.isNotEmpty() || template.rules.isNotEmpty()) {
                TextButton(onClick = onToggleExpand) {
                    Text(if (isExpanded) "Collapse" else "Details", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Expanded details
            if (isExpanded) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    template.hookTargets.forEach { hook ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Hook: ${hook.path}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    template.rules.forEach { rule ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${rule.action}: ${rule.pathPattern} [${rule.mode}]",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.vfs_template_apply))
                }
                if (!template.isBuiltIn) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RED)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ==================== Tab 5: Events ====================

@Composable
private fun EventsTab(
    events: List<VFSEvent>,
    isListening: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onClearEvents: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new events arrive
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            listState.animateScrollToItem(events.size)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Control bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { if (isListening) onStopListening() else onStartListening() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) RED else GREEN
                )
            ) {
                Icon(
                    if (isListening) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isListening) stringResource(R.string.vfs_event_stop) else stringResource(R.string.vfs_event_start))
            }

            Spacer(Modifier.weight(1f))

            // Status badge
            BadgedBox(
                badge = {
                    Badge {
                        Text(events.size.toString())
                    }
                }
            ) {
                Text(
                    text = if (isListening) stringResource(R.string.vfs_event_listening) else stringResource(R.string.vfs_event_stopped),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isListening) GREEN else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))

            OutlinedButton(onClick = onClearEvents, enabled = events.isNotEmpty()) {
                Text(stringResource(R.string.vfs_event_clear))
            }
        }

        if (isListening) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }

        Spacer(Modifier.height(4.dp))

        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = stringResource(R.string.vfs_no_events), style = MaterialTheme.typography.titleMedium)
                    Text(text = stringResource(R.string.vfs_no_events_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(events.reversed(), key = { "${it.timestamp}_${it.pid}_${it.path}" }) { event ->
                    EventItem(event = event)
                }
            }
        }
    }
}

@Composable
private fun EventItem(event: VFSEvent) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Event type badge
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        getEventTypeName(event.eventType),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = getEventTypeColor(event.eventType).copy(alpha = 0.15f)
                ),
                modifier = Modifier.size(width = 56.dp, height = 28.dp)
            )

            // PID & UID
            Text(
                text = "PID:${event.pid} UID:${event.uid}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(120.dp)
            )

            // File path
            Text(
                text = event.path,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Result badge
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        if (event.result == 0) stringResource(R.string.vfs_event_result_allow) else stringResource(R.string.vfs_event_result_deny),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (event.result == 0) GREEN.copy(alpha = 0.15f) else RED.copy(alpha = 0.15f)
                )
            )

            // Timestamp
            Text(
                text = formatTimestamp(event.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== Tab 6: Protocol Translator ====================

@Composable
private fun ProtocolTab(
    events: List<VFSEvent>,
    channel: CommChannel?,
    moduleVersion: Int?,
    onNavigateToProtocol: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.vfs_protocol_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        item { RuleTranslatorSection() }
        item { PolicyTranslatorSection() }
        item { HookCommandTranslatorSection() }
        item { LiveEventInspectorSection(events) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ==================== Dashboard: Protocol Debug Card ====================

@Composable
private fun ProtocolDebugCard(
    channel: CommChannel?,
    moduleVersion: Int?,
    onNavigateToProtocol: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.vfs_protocol_channel_info),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (channel != null) {
                    Text(
                        text = getChannelName(channel),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(getChannelColor(channel).copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = getChannelColor(channel)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.vfs_disconnected),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(RED.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = RED
                    )
                }
                Text(
                    text = "${stringResource(R.string.vfs_protocol_title)} v${moduleVersion ?: "?"}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Button(
                onClick = onNavigateToProtocol,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vfs_protocol_goto))
            }
        }
    }
}

// ==================== Protocol: Rule Translator Section ====================

@Composable
private fun RuleTranslatorSection() {
    var ruleInput by remember { mutableStateOf("") }
    var hexInput by remember { mutableStateOf("") }
    var forwardResult by remember { mutableStateOf<String?>(null) }
    var forwardParsed by remember { mutableStateOf<String?>(null) }
    var reverseResult by remember { mutableStateOf<String?>(null) }
    var validationStatus by remember { mutableStateOf<Pair<Boolean, String?>>(true to null) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.vfs_protocol_rule_translator),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            HorizontalDivider()

            // Forward translation: rule string -> binary
            Text(
                text = stringResource(R.string.vfs_protocol_binary_preview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = ruleInput,
                onValueChange = {
                    ruleInput = it
                    val result = VFSProtocolTranslator.validateRuleString(it)
                    validationStatus = result.valid to result.error
                },
                label = { Text(stringResource(R.string.vfs_protocol_input_hint)) },
                placeholder = { Text("deny:/system/**:rw") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (ruleInput.isNotEmpty()) {
                        Text(
                            text = if (validationStatus.first) stringResource(R.string.vfs_protocol_validation_ok)
                            else "${stringResource(R.string.vfs_protocol_validation_error)}: ${validationStatus.second}",
                            color = if (validationStatus.first) GREEN else RED
                        )
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val binary = VFSProtocolTranslator.ruleToBinary(ruleInput)
                        if (binary != null) {
                            forwardResult = VFSProtocolTranslator.hexDump(binary)
                            val validation = VFSProtocolTranslator.validateRuleString(ruleInput)
                            forwardParsed = validation.parsedFields?.entries?.joinToString("\n") { "  ${it.key} = ${it.value}" }
                        } else {
                            forwardResult = null
                            forwardParsed = null
                        }
                    },
                    enabled = ruleInput.isNotEmpty() && validationStatus.first,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.vfs_protocol_translate))
                }
            }

            if (forwardResult != null) {
                Text(
                    text = stringResource(R.string.vfs_protocol_hex_dump),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = forwardResult!!,
                        modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (forwardParsed != null) {
                Text(
                    text = stringResource(R.string.vfs_protocol_parsed_fields),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = forwardParsed!!,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider()

            // Reverse translation: hex -> rule string
            Text(
                text = stringResource(R.string.vfs_protocol_string_preview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = hexInput,
                onValueChange = { hexInput = it },
                label = { Text(stringResource(R.string.vfs_protocol_hex_input_hint)) },
                placeholder = { Text("01 0a 2f 73 79 73 74 65 6d") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    try {
                        val bytes = hexInput.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
                        reverseResult = VFSProtocolTranslator.ruleToString(bytes)
                    } catch (_: Exception) {
                        reverseResult = "解析错误: 无效的十六进制格式"
                    }
                },
                enabled = hexInput.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.vfs_protocol_reverse))
            }

            if (reverseResult != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = reverseResult!!,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ==================== Protocol: Policy Translator Section ====================

@Composable
private fun PolicyTranslatorSection() {
    var enabled by remember { mutableStateOf(true) }
    var logLevel by remember { mutableStateOf(0) }
    var defaultAction by remember { mutableStateOf("allow") }
    var hexDump by remember { mutableStateOf<String?>(null) }
    var fieldBreakdown by remember { mutableStateOf<String?>(null) }

    // Auto-translate on state change
    LaunchedEffect(enabled, logLevel, defaultAction) {
        val binary = VFSProtocolTranslator.policyToBinary(enabled, logLevel, defaultAction)
        hexDump = VFSProtocolTranslator.hexDump(binary)
        val map = VFSProtocolTranslator.policyToMap(binary)
        fieldBreakdown = map.entries.joinToString("\n") { "  ${it.key} = ${it.value}" }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.vfs_protocol_policy_translator),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            HorizontalDivider()

            // Policy controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.vfs_protocol_field_enabled) + ":", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.vfs_protocol_field_log_level) + ":", style = MaterialTheme.typography.bodyMedium)
                LogLevelSelector(currentLevel = logLevel, onLevelChange = { logLevel = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.vfs_protocol_field_default_action) + ":", style = MaterialTheme.typography.bodyMedium)
                ActionSelector(currentAction = defaultAction, onActionChange = { defaultAction = it })
            }

            // Hex dump display
            if (hexDump != null) {
                Text(
                    text = stringResource(R.string.vfs_protocol_hex_dump) + " (4 ${stringResource(R.string.vfs_protocol_bytes)})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = hexDump!!,
                        modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Field breakdown
            if (fieldBreakdown != null) {
                Text(
                    text = stringResource(R.string.vfs_protocol_parsed_fields),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = fieldBreakdown!!,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ==================== Protocol: Hook Command Translator Section ====================

@Composable
private fun HookCommandTranslatorSection() {
    var commandInput by remember { mutableStateOf("") }
    var hexInput by remember { mutableStateOf("") }
    var forwardHex by remember { mutableStateOf<String?>(null) }
    var forwardParsed by remember { mutableStateOf<String?>(null) }
    var reverseResult by remember { mutableStateOf<String?>(null) }
    var validationStatus by remember { mutableStateOf<Pair<Boolean, String?>>(true to null) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.ToggleOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.vfs_protocol_hook_translator),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            HorizontalDivider()

            // Forward translation
            Text(
                text = stringResource(R.string.vfs_protocol_binary_preview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = commandInput,
                onValueChange = {
                    commandInput = it
                    val result = VFSProtocolTranslator.validateHookCommand(it)
                    validationStatus = result.valid to result.error
                },
                label = { Text(stringResource(R.string.vfs_protocol_hook_input_hint)) },
                placeholder = { Text("add:PID:12345:10086:INTERCEPT_ALL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (commandInput.isNotEmpty()) {
                        Text(
                            text = if (validationStatus.first) stringResource(R.string.vfs_protocol_validation_ok)
                            else "${stringResource(R.string.vfs_protocol_validation_error)}: ${validationStatus.second}",
                            color = if (validationStatus.first) GREEN else RED
                        )
                    }
                }
            )
            Button(
                onClick = {
                    val binary = VFSProtocolTranslator.hookCommandToBinary(commandInput)
                    if (binary != null) {
                        forwardHex = VFSProtocolTranslator.hexDump(binary)
                        val validation = VFSProtocolTranslator.validateHookCommand(commandInput)
                        forwardParsed = validation.parsedFields?.entries?.joinToString("\n") { "  ${it.key} = ${it.value}" }
                    } else {
                        forwardHex = null
                        forwardParsed = null
                    }
                },
                enabled = commandInput.isNotEmpty() && validationStatus.first,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.vfs_protocol_translate))
            }

            if (forwardHex != null) {
                Text(
                    text = stringResource(R.string.vfs_protocol_hex_dump),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = forwardHex!!,
                        modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (forwardParsed != null) {
                Text(
                    text = stringResource(R.string.vfs_protocol_parsed_fields),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = forwardParsed!!,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            HorizontalDivider()

            // Reverse translation
            Text(
                text = stringResource(R.string.vfs_protocol_string_preview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = hexInput,
                onValueChange = { hexInput = it },
                label = { Text(stringResource(R.string.vfs_protocol_hex_input_hint)) },
                placeholder = { Text("01 04 50 49 44 ...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = {
                    try {
                        val bytes = hexInput.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()
                        reverseResult = VFSProtocolTranslator.hookBinaryToCommand(bytes)
                    } catch (_: Exception) {
                        reverseResult = "解析错误: 无效的十六进制格式"
                    }
                },
                enabled = hexInput.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.vfs_protocol_reverse))
            }

            if (reverseResult != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = reverseResult!!,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ==================== Protocol: Live Event Inspector Section ====================

@Composable
private fun LiveEventInspectorSection(events: List<VFSEvent>) {
    val lastEvent = events.lastOrNull()

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.vfs_protocol_event_inspector),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (lastEvent != null) {
                    Badge(
                        containerColor = GREEN
                    ) {
                        Text(stringResource(R.string.vfs_protocol_live_event), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider()

            if (lastEvent != null) {
                // Show parsed fields from real event data (no simulated binary)
                Text(
                    text = stringResource(R.string.vfs_protocol_parsed_fields),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        EventFieldRow(stringResource(R.string.vfs_protocol_field_event_type), getEventTypeName(lastEvent.eventType))
                        EventFieldRow(stringResource(R.string.vfs_protocol_field_pid), lastEvent.pid.toString())
                        EventFieldRow(stringResource(R.string.vfs_protocol_field_uid), lastEvent.uid.toString())
                        EventFieldRow(stringResource(R.string.vfs_protocol_field_path), lastEvent.path)
                        EventFieldRow(stringResource(R.string.vfs_protocol_field_timestamp), formatTimestamp(lastEvent.timestamp))
                        EventFieldRow(stringResource(R.string.vfs_protocol_field_result), if (lastEvent.result == 0) stringResource(R.string.vfs_event_result_allow) else stringResource(R.string.vfs_event_result_deny))
                    }
                }

                // Human-readable string
                Text(
                    text = stringResource(R.string.vfs_protocol_string_preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = "[${getEventTypeName(lastEvent.eventType)}] pid=${lastEvent.pid} uid=${lastEvent.uid} path=\"${lastEvent.path}\" -> ${if (lastEvent.result == 0) stringResource(R.string.vfs_event_result_allow) else stringResource(R.string.vfs_event_result_deny)}",
                        modifier = Modifier.padding(12.dp).horizontalScroll(rememberScrollState()),
                        fontFamily = FontFamily.Monospace,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.vfs_protocol_no_event),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun EventFieldRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = MaterialTheme.typography.bodySmall.fontSize,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

// ==================== Shared Utility Composables ====================

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LogLevelSelector(currentLevel: Int, onLevelChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val levels = listOf(0, 1, 2, 3, 4, 5)

    Box {
        Button(onClick = { expanded = true }) {
            Text(text = currentLevel.toString())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            levels.forEach { level ->
                DropdownMenuItem(
                    text = { Text("Level $level") },
                    onClick = { onLevelChange(level); expanded = false },
                    trailingIcon = { if (level == currentLevel) Icon(Icons.Filled.Check, contentDescription = null) else null }
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("allow", "deny").forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.uppercase()) },
                    onClick = { onActionChange(action); expanded = false },
                    trailingIcon = { if (action == currentAction) Icon(Icons.Filled.Check, contentDescription = null) else null }
                )
            }
        }
    }
}

// ==================== Helper Functions ====================

private fun getBackendName(backend: VFSBackend): String = when (backend) {
    VFSBackend.KERNEL_SYSFS -> "SysFS"
    VFSBackend.KERNEL_DEBUGFS -> "DebugFS"
    VFSBackend.MOCK -> "Mock"
}

private fun getBackendColor(backend: VFSBackend): Color = when (backend) {
    VFSBackend.KERNEL_SYSFS -> GREEN
    VFSBackend.KERNEL_DEBUGFS -> Color(0xFF4CAF50)
    VFSBackend.MOCK -> RED
}

private fun getChannelName(channel: CommChannel): String = when (channel) {
    CommChannel.PIPE -> "PIPE"
    CommChannel.SYSFS -> "SysFS"
    CommChannel.USERSPACE -> "Shell"
}

private fun getChannelColor(channel: CommChannel): Color = when (channel) {
    CommChannel.PIPE -> GREEN
    CommChannel.SYSFS -> Color(0xFF4CAF50)
    CommChannel.USERSPACE -> ORANGE
}

@Composable
private fun getHookModeName(mode: HookMode): String = when (mode) {
    HookMode.MONITOR_ONLY -> stringResource(R.string.vfs_hook_mode_monitor)
    HookMode.INTERCEPT_READ -> stringResource(R.string.vfs_hook_mode_read)
    HookMode.INTERCEPT_WRITE -> stringResource(R.string.vfs_hook_mode_write)
    HookMode.INTERCEPT_ALL -> stringResource(R.string.vfs_hook_mode_all)
}

@Composable
private fun getRuleActionName(action: RuleAction): String = when (action) {
    RuleAction.ALLOW -> stringResource(R.string.vfs_rule_action_allow)
    RuleAction.DENY -> stringResource(R.string.vfs_rule_action_deny)
    RuleAction.LOG_ONLY -> stringResource(R.string.vfs_rule_action_log)
}

@Composable
private fun getOpName(op: VFSOp): String = when (op) {
    VFSOp.OPEN -> stringResource(R.string.vfs_rule_op_open)
    VFSOp.READ -> stringResource(R.string.vfs_rule_op_read)
    VFSOp.WRITE -> stringResource(R.string.vfs_rule_op_write)
    VFSOp.CLOSE -> stringResource(R.string.vfs_rule_op_close)
}

private fun getOpShortName(op: VFSOp): String = when (op) {
    VFSOp.OPEN -> "O"
    VFSOp.READ -> "R"
    VFSOp.WRITE -> "W"
    VFSOp.CLOSE -> "C"
}

@Composable
private fun getEventTypeName(eventType: Int): String = when (eventType) {
    EVENT_VFS_OPEN -> stringResource(R.string.vfs_event_type_open)
    EVENT_VFS_READ -> stringResource(R.string.vfs_event_type_read)
    EVENT_VFS_WRITE -> stringResource(R.string.vfs_event_type_write)
    EVENT_VFS_CLOSE -> stringResource(R.string.vfs_event_type_close)
    EVENT_VFS_DENY -> stringResource(R.string.vfs_event_type_deny)
    else -> "UNK"
}

private fun getEventTypeColor(eventType: Int): Color = when (eventType) {
    EVENT_VFS_OPEN -> Color(0xFF42A5F5)
    EVENT_VFS_READ -> GREEN
    EVENT_VFS_WRITE -> ORANGE
    EVENT_VFS_CLOSE -> Color(0xFF9C27B0)
    EVENT_VFS_DENY -> RED
    else -> Color(0xFF757575)
}

private fun formatTimestamp(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        timestamp.toString()
    }
}
