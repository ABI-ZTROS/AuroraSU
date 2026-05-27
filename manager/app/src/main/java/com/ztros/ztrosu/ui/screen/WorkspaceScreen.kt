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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.topjohnwu.superuser.ShellUtils
import com.ztros.ztrosu.R
import com.ztros.ztrosu.ui.LocalScrollState
import com.ztros.ztrosu.ui.component.SwitchItem
import com.ztros.ztrosu.ui.rememberScrollConnection
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UserInfo(
    val id: String,
    val name: String,
    val isCurrent: Boolean,
    val isManagedProfile: Boolean
)

private suspend fun getUserList(): List<UserInfo> = withContext(Dispatchers.IO) {
    runCatching {
        val output = ShellUtils.fastCmd("pm list users 2>/dev/null").trim()
        output.lines()
            .filter { it.contains("UserInfo") }
            .map { line ->
                val parts = line.split(":")
                val id = parts.getOrNull(1)?.trim()?.replace("UserInfo", "")?.trim() ?: "0"
                val name = parts.getOrNull(2)?.trim() ?: ""
                val flags = parts.getOrNull(3)?.trim() ?: ""
                UserInfo(
                    id = id,
                    name = name.ifBlank { if (id == "0") "主用户" else "用户 $id" },
                    isCurrent = flags.contains("Current"),
                    isManagedProfile = flags.contains("ManagedProfile")
                )
            }
    }.getOrDefault(emptyList())
}

private suspend fun createUser(name: String): Boolean = withContext(Dispatchers.IO) {
    ShellUtils.fastCmdResult("pm create-user --profileOf 0 '$name' 2>/dev/null")
}

private suspend fun switchUser(userId: String): Boolean = withContext(Dispatchers.IO) {
    ShellUtils.fastCmdResult("am switch-user $userId 2>/dev/null")
}

private suspend fun deleteUser(userId: String): Boolean = withContext(Dispatchers.IO) {
    ShellUtils.fastCmdResult("pm remove-user $userId 2>/dev/null")
}

private suspend fun getWorkspaceSuPolicy(userId: String): Boolean = withContext(Dispatchers.IO) {
    val content = ShellUtils.fastCmd("cat /data/adb/ksu/workspace_su_policy/$userId.json 2>/dev/null").trim()
    content.contains("\"independent\":true")
}

private suspend fun setWorkspaceSuPolicy(userId: String, independent: Boolean): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        ShellUtils.fastCmd("mkdir -p /data/adb/ksu/workspace_su_policy 2>/dev/null")
        val json = """{"independent":$independent}"""
        ShellUtils.fastCmd("echo '$json' > /data/adb/ksu/workspace_su_policy/$userId.json 2>/dev/null")
        true
    }.getOrDefault(false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun WorkspaceScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val scope = rememberCoroutineScope()

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    var userList by remember { mutableStateOf(listOf<UserInfo>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<UserInfo?>(null) }
    var showSwitchConfirm by remember { mutableStateOf<UserInfo?>(null) }
    var workspaceSuPolicies by remember { mutableStateOf(mapOf<String, Boolean>()) }

    // Load user list on first composition
    LaunchedEffect(Unit) {
        isLoading = true
        userList = getUserList()
        // Load SU policies for each user
        val policies = mutableMapOf<String, Boolean>()
        userList.forEach { user ->
            policies[user.id] = getWorkspaceSuPolicy(user.id)
        }
        workspaceSuPolicies = policies
        isLoading = false
    }

    val currentLabel = stringResource(R.string.multiuser_current)
    val selectProfile = stringResource(R.string.multiuser_select_profile)
    val suPolicyTitle = stringResource(R.string.multiuser_independent_su)
    val suPolicyDesc = stringResource(R.string.multiuser_independent_su_desc)
    val switchLabel = stringResource(R.string.multiuser_switch)
    val confirmTitle = stringResource(R.string.multiuser_confirm_title)
    val cancelBtn = stringResource(R.string.cancel)
    val confirmBtn = stringResource(R.string.confirm)
    val switchSuccess = stringResource(R.string.multiuser_switch_success)

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
            // Current user card
            val currentUser = userList.firstOrNull { it.isCurrent }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        headlineContent = {
                            Text(
                                text = currentLabel,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Filled.Workspaces, contentDescription = null)
                        }
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (currentUser?.isManagedProfile == true) Icons.Filled.Work else Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = currentUser?.name ?: "加载中...",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = "ID: ${currentUser?.id ?: "-"}${if (currentUser?.isManagedProfile == true) " (工作空间)" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // User list card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = selectProfile,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    if (isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    } else if (userList.isEmpty()) {
                        Text(
                            text = "未找到用户",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        userList.forEach { user ->
                            WorkspaceUserItem(
                                user = user,
                                independentSu = workspaceSuPolicies[user.id] ?: false,
                                onSwitch = { showSwitchConfirm = user },
                                onDelete = { showDeleteConfirm = user },
                                onSuPolicyToggle = { enabled ->
                                    scope.launch {
                                        val success = setWorkspaceSuPolicy(user.id, enabled)
                                        if (success) {
                                            workspaceSuPolicies = workspaceSuPolicies.toMutableMap().apply {
                                                put(user.id, enabled)
                                            }
                                        } else {
                                            snackBarHost.showSnackbar("SU 策略设置失败")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Create workspace button
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("创建工作空间")
            }
        }
    }

    // Create workspace dialog
    if (showCreateDialog) {
        var newWorkspaceName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "创建工作空间",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                OutlinedTextField(
                    value = newWorkspaceName,
                    onValueChange = { newWorkspaceName = it },
                    label = { Text("工作空间名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newWorkspaceName.trim()
                    if (name.isNotBlank()) {
                        showCreateDialog = false
                        scope.launch {
                            isLoading = true
                            val success = createUser(name)
                            if (success) {
                                userList = getUserList()
                                snackBarHost.showSnackbar("工作空间创建成功")
                            } else {
                                snackBarHost.showSnackbar("工作空间创建失败")
                            }
                            isLoading = false
                        }
                    }
                }) { Text(text = confirmBtn) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(text = cancelBtn) }
            }
        )
    }

    // Switch user confirm dialog
    showSwitchConfirm?.let { user ->
        AlertDialog(
            onDismissRequest = { showSwitchConfirm = null },
            title = {
                Text(
                    text = confirmTitle,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = { Text(text = "确定要切换到用户 \"${user.name}\" 吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showSwitchConfirm = null
                    scope.launch {
                        val success = switchUser(user.id)
                        if (success) {
                            snackBarHost.showSnackbar(switchSuccess)
                        } else {
                            snackBarHost.showSnackbar("切换用户失败")
                        }
                    }
                }) { Text(text = confirmBtn) }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchConfirm = null }) { Text(text = cancelBtn) }
            }
        )
    }

    // Delete user confirm dialog
    showDeleteConfirm?.let { user ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = {
                Text(
                    text = "删除用户",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = { Text(text = "确定要删除用户 \"${user.name}\" 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = null
                    scope.launch {
                        isLoading = true
                        val success = deleteUser(user.id)
                        if (success) {
                            userList = getUserList()
                            snackBarHost.showSnackbar("用户已删除")
                        } else {
                            snackBarHost.showSnackbar("删除用户失败")
                        }
                        isLoading = false
                    }
                }) { Text(text = confirmBtn) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text(text = cancelBtn) }
            }
        )
    }
}

@Composable
private fun WorkspaceUserItem(
    user: UserInfo,
    independentSu: Boolean,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
    onSuPolicyToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (user.isCurrent) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (user.isManagedProfile) Icons.Filled.Work else Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (user.isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "ID: ${user.id}${if (user.isCurrent) " (当前)" else ""}${if (user.isManagedProfile) " (工作空间)" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (user.isCurrent) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // SU Policy switch for this workspace
            if (user.isManagedProfile) {
                SwitchItem(
                    icon = Icons.Filled.AdminPanelSettings,
                    title = suPolicyTitle,
                    summary = suPolicyDesc,
                    checked = independentSu,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    onSuPolicyToggle(it)
                }
            }

            // Action buttons (only for non-current users)
            if (!user.isCurrent && user.id != "0") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSwitch,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("切换")
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("删除")
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
                text = stringResource(R.string.multiuser_title),
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
private fun WorkspacePreview() {
    WorkspaceScreen(EmptyDestinationsNavigator)
}
