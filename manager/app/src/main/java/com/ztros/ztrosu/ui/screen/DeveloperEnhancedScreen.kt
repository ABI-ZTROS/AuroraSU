package com.ztros.ztrosu.ui.screen

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private suspend fun getLoadedModules(): List<String> = withContext(Dispatchers.IO) {
    runCatching {
        val result = ShellUtils.fastCmd("ls /data/adb/modules 2>/dev/null").trim()
        if (result.isNotBlank()) result.lines().filter { it.isNotBlank() } else emptyList()
    }.getOrDefault(emptyList())
}

private suspend fun getDmesgLogs(): String = withContext(Dispatchers.IO) {
    runCatching { ShellUtils.fastCmd("dmesg 2>/dev/null | tail -30").trim() }.getOrDefault("")
}

private suspend fun getLogcatLogs(): String = withContext(Dispatchers.IO) {
    runCatching { ShellUtils.fastCmd("logcat -d -t 30 2>/dev/null").trim() }.getOrDefault("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun DeveloperEnhancedScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val devEnabled = prefs.getBoolean("enable_developer_options", false)

    // Pre-resolve strings
    val terminalLabel = stringResource(R.string.dev_terminal)
    val terminalDesc = stringResource(R.string.dev_terminal_desc)
    val selinuxLabel = stringResource(R.string.dev_selinux_editor)
    val selinuxDesc = stringResource(R.string.dev_selinux_editor_desc)
    val moduleDebugLabel = stringResource(R.string.dev_module_debug)
    val moduleDebugDesc = stringResource(R.string.dev_module_debug_desc)
    val modulesLoadedLabel = stringResource(R.string.dev_modules_loaded)
    val modulesEmptyLabel = stringResource(R.string.dev_modules_empty)
    val logViewerLabel = stringResource(R.string.dev_log_viewer)
    val logViewerDesc = stringResource(R.string.dev_log_viewer_desc)
    val dmesgLabel = stringResource(R.string.dev_log_dmesg)
    val logcatLabel = stringResource(R.string.dev_log_logcat)
    val logEmptyLabel = stringResource(R.string.dev_log_empty)
    val terminalHint = stringResource(R.string.terminal_hint)
    val cancelLabel = stringResource(R.string.cancel)
    val devOptLabel = stringResource(R.string.enable_developer_options)

    // Terminal state
    var commandInput by remember { mutableStateOf("") }
    var terminalOutput by remember { mutableStateOf("ZTR_OS SU Terminal v1.0\n$ ") }
    val terminalScrollState = rememberLazyListState()
    var commandHistory by remember { mutableStateOf(listOf<String>()) }

    // Module debug state
    var loadedModules by remember { mutableStateOf(listOf<String>()) }

    // Log viewer state
    var logDialogContent by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(devEnabled) {
        if (devEnabled) loadedModules = getLoadedModules()
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
            if (!devEnabled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeveloperMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = devOptLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            // Terminal emulator card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = {
                            Text(terminalLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = { Text(terminalDesc) },
                        leadingContent = { Icon(Icons.Filled.Terminal, contentDescription = null) }
                    )

                    // Terminal output
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        LazyColumn(
                            state = terminalScrollState,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        ) {
                            items(terminalOutput.lines()) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Command input
                    OutlinedTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(terminalHint) },
                        singleLine = true,
                        trailingIcon = {
                            if (commandInput.isNotBlank()) {
                                IconButton(onClick = {
                                    val cmd = commandInput.trim()
                                    if (cmd.isNotEmpty()) {
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) { ShellUtils.fastCmd(cmd) }
                                            terminalOutput = "$terminalOutput$cmd\n$result\n$ "
                                            commandHistory = commandHistory + cmd
                                            commandInput = ""
                                        }
                                    }
                                }) {
                                    Icon(Icons.Filled.Send, contentDescription = null)
                                }
                            }
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false)
                    )
                }
            }

            // SELinux Policy Editor entry
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    leadingContent = { Icon(Icons.Filled.Policy, contentDescription = null) },
                    headlineContent = {
                        Text(selinuxLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = { Text(selinuxDesc) },
                    trailingContent = { Icon(Icons.Filled.ArrowForward, contentDescription = null) }
                )
            }

            // Module Debug section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = {
                            Text(moduleDebugLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = { Text(moduleDebugDesc) },
                        leadingContent = { Icon(Icons.Filled.Extension, contentDescription = null) }
                    )
                    HorizontalDivider()
                    Text(modulesLoadedLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (loadedModules.isEmpty()) {
                        Text(modulesEmptyLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        loadedModules.forEach { module ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ViewModule,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(text = module, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            }

            // Log Viewer section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = {
                            Text(logViewerLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = { Text(logViewerDesc) },
                        leadingContent = { Icon(Icons.Filled.Article, contentDescription = null) }
                    )
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    logDialogContent = dmesgLabel to getDmesgLogs()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(dmesgLabel, style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    logDialogContent = logcatLabel to getLogcatLogs()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(logcatLabel, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    // Shared log dialog
    logDialogContent?.let { (logTitle, logContent) ->
        LogViewDialog(
            title = logTitle,
            content = logContent,
            emptyLabel = logEmptyLabel,
            cancelLabel = cancelLabel,
            onDismiss = { logDialogContent = null }
        )
    }
}

@Composable
private fun LogViewDialog(
    title: String,
    content: String,
    emptyLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Surface(
                modifier = Modifier.fillMaxWidth().height(300.dp).horizontalScroll(rememberScrollState()),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                val logScrollState = rememberLazyListState()
                LazyColumn(state = logScrollState, modifier = Modifier.padding(8.dp)) {
                    if (content.isNotBlank()) {
                        items(content.lines()) { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        item { Text(emptyLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        }
    )
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
                text = stringResource(R.string.dev_enhanced_title),
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
private fun DeveloperEnhancedPreview() {
    DeveloperEnhancedScreen(EmptyDestinationsNavigator)
}
