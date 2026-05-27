package com.ztros.ztrosu.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun HotUpdateScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val bottomBarScrollState = LocalScrollState.current
    val bottomBarScrollConnection = if (bottomBarScrollState != null) {
        rememberScrollConnection(
            isScrollingDown = bottomBarScrollState.isScrollingDown,
            scrollOffset = bottomBarScrollState.scrollOffset,
            previousScrollOffset = bottomBarScrollState.previousScrollOffset,
            threshold = 30f
        )
    } else null
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    // Pre-resolve all string resources
    val currentVersionTitle = stringResource(R.string.hotupdate_current_version)
    val checkUpdateTitle = stringResource(R.string.hotupdate_check)
    val changelogTitle = stringResource(R.string.hotupdate_changelog)
    val rollbackTitle = stringResource(R.string.hotupdate_rollback)
    val rollbackConfirmMsg = stringResource(R.string.hotupdate_reboot_required)
    val updateStatusUpToDate = stringResource(R.string.hotupdate_up_to_date)
    val updateStatusUpdating = stringResource(R.string.hotupdate_installing)
    val updateStatusSuccess = stringResource(R.string.hotupdate_success)
    val updateStatusFailed = stringResource(R.string.hotupdate_failed)
    val confirmText = stringResource(R.string.confirm)
    val cancelText = stringResource(R.string.cancel)
    val updateText = stringResource(R.string.hotupdate_update)
    val downloadingText = stringResource(R.string.hotupdate_downloading)

    val currentVersion = remember {
        runCatching { Natives.version.toString() }.getOrNull() ?: "Unknown"
    }

    var updateStatus by remember { mutableStateOf("") }
    var isUpdating by remember { mutableStateOf(false) }
    var showRollbackConfirm by remember { mutableStateOf(false) }
    var changelogExpanded by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf("") }
    var changelog by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var canInstall by remember { mutableStateOf(false) }

    val changelogEntries = listOf(
        "v1.5.0 - Improved kernel module compatibility",
        "v1.4.2 - Fixed SELinux policy loading issue",
        "v1.4.1 - Fixed module mount on Android 15",
        "v1.4.0 - Added hot update support for kernel modules",
        "v1.3.0 - Performance optimizations for module loading"
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
            // Current Version Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentVersionTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = currentVersion,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            // Check for Updates Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isUpdating = true
                                loadingDialog.show()
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        // 检查本地 ksud 版本
                                        val localVersion = ShellUtils.fastCmd("ksud --version 2>/dev/null").trim()
                                        // 检查 GitHub 最新 release
                                        val apiResult = ShellUtils.fastCmd("curl -s --connect-timeout 10 https://api.github.com/repos/ABI-ZTROS/AuroraSU/releases/latest 2>/dev/null")
                                        if (apiResult.contains("\"tag_name\"")) {
                                            val tagName = apiResult.lines().firstOrNull { it.contains("\"tag_name\"") }
                                                ?.split("\"tag_name\"")?.get(1)?.split("\"")?.get(1) ?: ""
                                            val body = apiResult.lines().firstOrNull { it.contains("\"body\"") }
                                                ?.substringAfter("\"body\"")?.split("\"")?.get(1) ?: ""
                                            Triple(true, tagName, body)
                                        } else {
                                            Triple(false, "", "")
                                        }
                                    }.getOrDefault(Triple(false, "", ""))
                                }
                                loadingDialog.hide()
                                isUpdating = false
                                if (result.first) {
                                    latestVersion = result.second
                                    changelog = result.third.ifBlank { "无更新说明" }
                                    updateStatus = "发现新版本: ${result.second}"
                                } else {
                                    updateStatus = "检查失败，请检查网络连接"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isUpdating
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(downloadingText)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(checkUpdateTitle)
                        }
                    }

                    if (isUpdating) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (updateStatus.isNotEmpty()) {
                        Text(
                            text = updateStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (updateStatus) {
                                updateStatusUpToDate -> MaterialTheme.colorScheme.primary
                                updateStatusSuccess -> MaterialTheme.colorScheme.primary
                                updateStatusFailed -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // Changelog Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.Filled.History, null) },
                        headlineContent = {
                            Text(
                                text = changelogTitle,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = if (changelogExpanded) {
                                    Icons.Filled.ExpandLess
                                } else {
                                    Icons.Filled.ExpandMore
                                },
                                contentDescription = null
                            )
                        }
                    )

                    if (changelogExpanded) {
                        changelogEntries.forEach { entry ->
                            Text(
                                text = entry,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

                    TextButton(
                        onClick = { changelogExpanded = !changelogExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (changelogExpanded) {
                                stringResource(R.string.settings_uninstall)
                            } else {
                                changelogTitle
                            }
                        )
                    }
                }
            }

            // Download Update Card
            if (latestVersion.isNotEmpty() && !isDownloading) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "下载更新",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Text(
                            text = "最新版本: $latestVersion",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (changelog.isNotEmpty()) {
                            Text(
                                text = changelog,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isDownloading = true
                                    downloadProgress = 0f
                                    val success = withContext(Dispatchers.IO) {
                                        runCatching {
                                            // 获取下载链接
                                            val assets = ShellUtils.fastCmd("curl -s https://api.github.com/repos/ABI-ZTROS/AuroraSU/releases/latest | grep \"browser_download_url.*arm64.*apk\" | head -1 | cut -d'\"' -f4")
                                            if (assets.isNotBlank()) {
                                                ShellUtils.fastCmd("curl -L -o /data/local/tmp/aurorasu_update.apk --progress-bar $assets 2>&1")
                                                true
                                            } else {
                                                false
                                            }
                                        }.getOrDefault(false)
                                    }
                                    isDownloading = false
                                    if (success) {
                                        canInstall = true
                                        updateStatus = "下载完成，可以安装"
                                    } else {
                                        updateStatus = "下载失败"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("开始下载")
                        }
                    }
                }
            }

            // Downloading progress card
            if (isDownloading) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "正在下载更新...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Install Update Button
            if (canInstall) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(
                                Uri.parse("file:///data/local/tmp/aurorasu_update.apk"),
                                "application/vnd.android.package-archive"
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Filled.InstallMobile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("安装更新")
                }
            }

            // Rollback Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = rollbackTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    Text(
                        text = rollbackConfirmMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    OutlinedButton(
                        onClick = { showRollbackConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(rollbackTitle)
                    }
                }
            }

            Spacer(Modifier)
        }
    }

    // Rollback confirmation dialog
    if (showRollbackConfirm) {
        AlertDialog(
            onDismissRequest = { showRollbackConfirm = false },
            title = {
                Text(
                    text = rollbackTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Text(text = rollbackConfirmMsg)
            },
            confirmButton = {
                TextButton(onClick = {
                    showRollbackConfirm = false
                    scope.launch {
                        val success = loadingDialog.withLoading {
                            withContext(Dispatchers.IO) {
                                delay(1500L)
                                runCatching {
                                    ShellUtils.fastCmdResult("ksud module update --rollback 2>/dev/null")
                                }.getOrDefault(false)
                            }
                        }
                        if (success) {
                            updateStatus = updateStatusSuccess
                            snackBarHost.showSnackbar(updateStatusSuccess)
                        } else {
                            updateStatus = updateStatusFailed
                            snackBarHost.showSnackbar(updateStatusFailed)
                        }
                    }
                }) {
                    Text(text = confirmText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRollbackConfirm = false }) {
                    Text(text = cancelText)
                }
            }
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
                text = stringResource(R.string.hotupdate_title),
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
private fun HotUpdatePreview() {
    HotUpdateScreen(EmptyDestinationsNavigator)
}
