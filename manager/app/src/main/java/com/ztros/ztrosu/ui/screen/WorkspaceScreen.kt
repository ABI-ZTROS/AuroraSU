package com.ztros.ztrosu.ui.screen

import android.content.Context
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
import androidx.core.content.edit
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ztros.ztrosu.R
import com.ztros.ztrosu.ui.LocalScrollState
import com.ztros.ztrosu.ui.component.SwitchItem
import com.ztros.ztrosu.ui.rememberScrollConnection
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

private enum class WorkspaceProfile(val key: String) {
    Personal("personal"),
    Work("work"),
    Isolated("isolated")
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun WorkspaceScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    val prefs = context.getSharedPreferences("workspace", Context.MODE_PRIVATE)
    val currentProfileKey = prefs.getString("current_workspace", WorkspaceProfile.Personal.key)
        ?: WorkspaceProfile.Personal.key
    var selectedProfile by rememberSaveable { mutableStateOf(currentProfileKey) }
    var independentSuPolicy by rememberSaveable {
        mutableStateOf(prefs.getBoolean("independent_su_policy", false))
    }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val currentLabel = stringResource(R.string.multiuser_current)
    val personalName = stringResource(R.string.multiuser_personal)
    val personalDesc = stringResource(R.string.multiuser_personal_desc)
    val workName = stringResource(R.string.multiuser_work)
    val workDesc = stringResource(R.string.multiuser_work_desc)
    val isolatedName = stringResource(R.string.multiuser_isolated)
    val isolatedDesc = stringResource(R.string.multiuser_isolated_desc)
    val selectProfile = stringResource(R.string.multiuser_select_profile)
    val suPolicyTitle = stringResource(R.string.multiuser_independent_su)
    val suPolicyDesc = stringResource(R.string.multiuser_independent_su_desc)
    val switchLabel = stringResource(R.string.multiuser_switch)
    val confirmTitle = stringResource(R.string.multiuser_confirm_title)
    val confirmMsg = stringResource(R.string.multiuser_confirm_message)
    val confirmBtn = stringResource(R.string.confirm)
    val cancelBtn = stringResource(R.string.cancel)
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
            // Current profile card
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
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
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
                            imageVector = when (selectedProfile) {
                                WorkspaceProfile.Personal.key -> Icons.Filled.Person
                                WorkspaceProfile.Work.key -> Icons.Filled.Work
                                else -> Icons.Filled.Security
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = when (selectedProfile) {
                                    WorkspaceProfile.Personal.key -> personalName
                                    WorkspaceProfile.Work.key -> workName
                                    else -> isolatedName
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = when (selectedProfile) {
                                    WorkspaceProfile.Personal.key -> personalDesc
                                    WorkspaceProfile.Work.key -> workDesc
                                    else -> isolatedDesc
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Profile selection card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = selectProfile,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    ProfileOptionCard(
                        name = personalName,
                        description = personalDesc,
                        icon = Icons.Filled.Person,
                        isSelected = selectedProfile == WorkspaceProfile.Personal.key,
                        onClick = { selectedProfile = WorkspaceProfile.Personal.key }
                    )

                    ProfileOptionCard(
                        name = workName,
                        description = workDesc,
                        icon = Icons.Filled.Work,
                        isSelected = selectedProfile == WorkspaceProfile.Work.key,
                        onClick = { selectedProfile = WorkspaceProfile.Work.key }
                    )

                    ProfileOptionCard(
                        name = isolatedName,
                        description = isolatedDesc,
                        icon = Icons.Filled.Security,
                        isSelected = selectedProfile == WorkspaceProfile.Isolated.key,
                        onClick = { selectedProfile = WorkspaceProfile.Isolated.key }
                    )
                }
            }

            // SU Policy card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SwitchItem(
                        icon = Icons.Filled.AdminPanelSettings,
                        title = suPolicyTitle,
                        summary = suPolicyDesc,
                        checked = independentSuPolicy,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        prefs.edit { putBoolean("independent_su_policy", it) }
                        independentSuPolicy = it
                    }
                }
            }

            // Switch profile button
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(switchLabel)
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = confirmTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = { Text(text = confirmMsg) },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    prefs.edit { putString("current_workspace", selectedProfile) }
                    scope.launch { snackBarHost.showSnackbar(switchSuccess) }
                }) { Text(text = confirmBtn) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text(text = cancelBtn) }
            }
        )
    }
}

@Composable
private fun ProfileOptionCard(
    name: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(selected = isSelected, onClick = onClick)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
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
private fun WorkspacePreview() {
    WorkspaceScreen(EmptyDestinationsNavigator)
}
