package com.ztros.ztrosu.ui.screen

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.system.Os
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dergoogler.mmrl.ui.component.LabelItem
import com.dergoogler.mmrl.ui.component.LabelItemDefaults
import com.dergoogler.mmrl.ui.component.text.TextRow
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.topjohnwu.superuser.ShellUtils
import com.ztros.ztrosu.*
import com.ztros.ztrosu.R
import com.ztros.ztrosu.ui.component.rememberConfirmDialog
import com.ztros.ztrosu.ui.component.ConfirmResult
import com.ztros.ztrosu.ui.theme.ORANGE
import com.ztros.ztrosu.ui.util.*
import com.ztros.ztrosu.ui.util.KernelDetect
import com.ztros.ztrosu.ui.util.KernelDetect.KernelMode
import com.ztros.ztrosu.ui.webui.WebUIActivity
import com.ztros.ztrosu.ui.util.restartActivity
import com.ztros.ztrosu.ui.util.module.LatestVersionInfo
import com.ztros.ztrosu.ui.viewmodel.ModuleViewModel
import com.ztros.ztrosu.ui.LocalScrollState
import com.ztros.ztrosu.ui.screen.BottomBarDestination
import com.ztros.ztrosu.ui.trackScroll
import com.ztros.ztrosu.ui.rememberScrollConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>(start = true)
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    val kernelVersion = getKernelVersion()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val isManager = Natives.isManager
    val fullFeatured = isManager && !Natives.requireNewKernel() && rootAvailable()
    val ksuVersion = if (isManager) Natives.version else null
    val ksuVersionTag = if (isManager) Natives.getVersionTag() else null

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val developerOptionsEnabled = prefs.getBoolean("enable_developer_options", false)
    
    // Get scroll state for bottom bar tracking
    val bottomBarScrollState = LocalScrollState.current

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp
    
    // Create scroll connection for bottom bar
    val bottomBarScrollConnection = if (bottomBarScrollState != null) {
        rememberScrollConnection(
            isScrollingDown = bottomBarScrollState.isScrollingDown,
            scrollOffset = bottomBarScrollState.scrollOffset,
            previousScrollOffset = bottomBarScrollState.previousScrollOffset,
            threshold = 30f
        )
    } else null

    Scaffold(
        topBar = {
            TopBar(
                kernelVersion,
                ksuVersion,
                onInstallClick = {
                    navigator.navigate(InstallScreenDestination)
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                // Chain scroll connections - bottom bar tracking first, then topbar behavior
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
                .padding(top = 16.dp)
                .padding(bottom = navBarPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(kernelVersion, ksuVersion, ksuVersionTag = ksuVersionTag) {
                navigator.navigate(InstallScreenDestination)
            }

            val homeDestination = BottomBarDestination.entries.firstOrNull()
            val startRoute = homeDestination?.direction?.route

            if (fullFeatured) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        SuperuserCard(
                            onClick = {
                                navigator.navigate(SuperUserScreenDestination) {
                                    popUpTo(NavGraphs.root.startRoute) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ModuleCard(
                            onClick = {
                                navigator.navigate(ModuleScreenDestination) {
                                    popUpTo(NavGraphs.root.startRoute) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }

            if (isManager && Natives.requireNewKernel()) {
                WarningCard(
                    stringResource(id = R.string.require_kernel_version).format(
                        ksuVersion, Natives.MINIMAL_SUPPORTED_KERNEL
                    )
                )
            }

            if (ksuVersion != null && !rootAvailable()) {
                WarningCard(
                    stringResource(id = R.string.grant_root_failed),
                    onClick = {
                        restartActivity(context)
                    }
                )
            }

            val checkUpdate =
                LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getBoolean("check_update", true)
            if (checkUpdate) {
                UpdateCard()
            }

            InfoCard(autoExpand = developerOptionsEnabled)
            SystemInfoCard()
            if (fullFeatured) {
                QuickActionsCard()
            }
            ModuleSummaryCard()
            IssueReportCard()
            AboutCard()
            Spacer(Modifier)
        }
    }
}

@Composable
private fun SuperuserCard(onClick: (() -> Unit)? = null) {
    val count = getSuperuserCount()
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (count <= 1) {
                        stringResource(R.string.home_superuser_count_singular)
                    } else {
                        stringResource(R.string.home_superuser_count_plural)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ModuleCard(onClick: (() -> Unit)? = null) {
    val count = getModuleCount()
    val moduleViewModel: ModuleViewModel = viewModel()

    val moduleUpdateCount = moduleViewModel.moduleList.count {
        moduleViewModel.checkUpdate(it).first.isNotEmpty()
    }

    // State machine: 0 = nothing, 1 = show "+ Update!", 2 = show "+ X"
    var step by remember { mutableStateOf(0) }

    LaunchedEffect(moduleUpdateCount) {
        if (moduleUpdateCount > 0) {
            step = 1
            delay(1200) // show "+ Update!" for a moment
            step = 2
        } else {
            step = 0
        }
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
            .height(IntrinsicSize.Min)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (count <= 1) {
                        stringResource(R.string.home_module_count_singular)
                    } else {
                        stringResource(R.string.home_module_count_plural)
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (moduleUpdateCount > 0) {
                        Spacer(Modifier.width(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Keep the "|" static
                            Text(
                                text = "|",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.width(4.dp))

                            // Animate only the right-side text
                            AnimatedContent(
                                targetState = step,
                                transitionSpec = {
                                    slideInHorizontally { -it } + fadeIn() togetherWith
                                            slideOutHorizontally { it } + fadeOut()
                                }
                            ) { target ->
                                when (target) {
                                    1 -> Text(
                                        text = stringResource(id = R.string.home_module_update_available),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    2 -> Text(
                                        text = buildAnnotatedString {
                                            append(moduleUpdateCount.toString())
                                            append("*")
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
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

@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val latestVersionInfo = LatestVersionInfo()
    
    var preferSpoofed by remember { mutableStateOf(false) }
    
    val newVersion by produceState(initialValue = latestVersionInfo, key1 = preferSpoofed) {
        value = withContext(Dispatchers.IO) {
            checkNewVersion(preferSpoofed)
        }
    }

    val currentVersionCode = getManagerVersion(context).second
    val newVersionCode = newVersion.versionCode
    val newVersionUrl = newVersion.downloadUrl
    val changelog = newVersion.changelog
    val newVersionTag = newVersion.versionTag

    val uriHandler = LocalUriHandler.current
    val title = stringResource(id = R.string.module_changelog)
    val updateText = stringResource(id = R.string.module_update)

    AnimatedVisibility(
        visible = newVersionCode > currentVersionCode,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { uriHandler.openUri(newVersionUrl) })
        ElevatedCard(
            modifier = Modifier.clickable {
                if (changelog.isEmpty()) {
                    uriHandler.openUri(newVersionUrl)
                } else {
                    updateDialog.showConfirm(
                        title = title,
                        content = changelog,
                        markdown = true,
                        confirm = updateText
                    )
                }
            },
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Update,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(end = 20.dp)
                    )
                    Text(
                        text = if (!newVersionTag.isNullOrEmpty()) {
                            stringResource(id = R.string.new_version_available, newVersionTag, newVersionCode)
                        } else {
                            stringResource(id = R.string.new_version_available, "", newVersionCode)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(id = R.string.select_build_type),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!preferSpoofed) {
                            FilledTonalButton(
                                onClick = { preferSpoofed = false },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(stringResource(id = R.string.main), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { preferSpoofed = false },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.69f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Verified,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(stringResource(id = R.string.main), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        
                        if (preferSpoofed) {
                            FilledTonalButton(
                                onClick = { preferSpoofed = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(stringResource(id = R.string.spoofed), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { preferSpoofed = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.69f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(stringResource(id = R.string.spoofed), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RebootDropdownItem(@StringRes id: Int, reason: String = "") {
    DropdownMenuItem(text = {
        Text(stringResource(id))
    }, onClick = {
        reboot(reason)
    })
}

// @Composable
// fun getSeasonalIcon(): ImageVector {
//     val month = Calendar.getInstance().get(Calendar.MONTH) // 0-11 for January-December
//     return when (month) {
//         Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> Icons.Filled.AcUnit // Winter
//         Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> Icons.Filled.Spa // Spring
//         Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> Icons.Filled.WbSunny // Summer
//         Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER -> Icons.Filled.Forest // Fall
//         else -> Icons.Filled.Whatshot // Fallback icon
//     }
// }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    kernelVersion: KernelVersion,
    ksuVersion: Int?,
    onInstallClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    var isSpinning by remember { mutableStateOf(false) }
    var rotationTarget by remember { mutableStateOf(0f) }
    val rotation by animateFloatAsState(
        targetValue = rotationTarget,
        animationSpec = tween(
            durationMillis = 1400,
            easing = androidx.compose.animation.core.FastOutSlowInEasing
        ),
        finishedListener = {
            isSpinning = false
        }
    )

    val moduleViewModel: ModuleViewModel = viewModel()
    
    val webUILauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        isSpinning = true
        rotationTarget += 360f * 6
    }

        TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (!isSpinning) {
                        isSpinning = true
                        rotationTarget += 360f * 6
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.cannabis_24),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .graphicsLayer {
                            rotationZ = rotation
                        }
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
        },
        actions = {
            if (ksuVersion != null) {
                IconButton(onClick = onInstallClick) {
                    Icon(
                        imageVector = Icons.Filled.Archive,
                        contentDescription = stringResource(id = R.string.install)
                    )
                }
            }

            if (ksuVersion != null) {
                var showDropdown by remember { mutableStateOf(false) }
                IconButton(onClick = {
                    showDropdown = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = stringResource(id = R.string.reboot)
                    )

                    DropdownMenu(expanded = showDropdown, onDismissRequest = {
                        showDropdown = false
                    }) {
                        RebootDropdownItem(id = R.string.reboot)
                        RebootDropdownItem(id = R.string.reboot_userspace, reason = "soft-reboot")

                        val pm =
                            LocalContext.current.getSystemService(Context.POWER_SERVICE) as PowerManager?
                        @Suppress("DEPRECATION")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pm?.isRebootingUserspaceSupported == true) {
                            RebootDropdownItem(id = R.string.reboot_userspace, reason = "userspace")
                        }
                        RebootDropdownItem(id = R.string.reboot_recovery, reason = "recovery")
                        RebootDropdownItem(id = R.string.reboot_bootloader, reason = "bootloader")
                        RebootDropdownItem(id = R.string.reboot_download, reason = "download")
                        RebootDropdownItem(id = R.string.reboot_edl, reason = "edl")
                    }
                }
            }
        },
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        scrollBehavior = scrollBehavior
    )
}


@Composable
private fun StatusCard(
    kernelVersion: KernelVersion,
    ksuVersion: Int?,
    moduleUpdateCount: Int = 0,
    ksuVersionTag: String? = null,
    onClickInstall: () -> Unit = {}
) {
    val context = LocalContext.current

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = run {
            if (ksuVersion != null) MaterialTheme.colorScheme.primary
            else if (kernelVersion.isGKI()) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.errorContainer
        })
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (ksuVersion == null) {
                        onClickInstall()
                    }
                }
                .padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                ksuVersion != null -> {
                    val workingMode = kernelVersion.getKernelType()

                    Icon(
                        imageVector = Icons.Filled.Mood,
                        contentDescription = null
                    )
                    Column(
                        modifier = Modifier.padding(start = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val labelStyle = LabelItemDefaults.style
                        TextRow(
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    LabelItem(
                                        icon = if (Natives.isSafeMode) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.Security,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    contentDescription = null
                                                )
                                            }
                                        } else {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.VerifiedUser,
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        text = {
                                            Text(
                                                text = workingMode,
                                                style = labelStyle.textStyle.copy(
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                )
                                            )
                                        },
                                        style = LabelItemDefaults.style.copy(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    )
                                    if (isSuCompatDisabled()) {
                                        LabelItem(
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Warning,
                                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    contentDescription = null
                                                )
                                            },
                                            text = {
                                                Text(
                                                    text = stringResource(R.string.sucompat_disabled),
                                                    style = labelStyle.textStyle.copy(
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    )
                                                )
                                            },
                                            style = LabelItemDefaults.style.copy(
                                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                            )
                                        )
                                    }
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(id = R.string.home_working),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        val versionText = if (!ksuVersionTag.isNullOrEmpty()) {
                            stringResource(id = R.string.home_working_version, ksuVersionTag, ksuVersion ?: 0)
                        } else {
                            stringResource(id = R.string.home_working_version, "v0.0.0", ksuVersion ?: 0)
                        }
                        Text(
                            text = versionText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                kernelVersion.isGKI() -> {
                    Icon(Icons.Filled.AutoFixHigh, null)
                    Column(Modifier.padding(start = 20.dp)) {
                        Text(
                            text = stringResource(R.string.home_not_installed),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_click_to_install),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                else -> {
                    Icon(Icons.Filled.MoodBad, null)
                    Column(Modifier.padding(start = 20.dp)) {
                        Text(
                            text = stringResource(R.string.home_failure),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_failure_tip),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WarningCard(
    message: String, color: Color = MaterialTheme.colorScheme.error, onClick: (() -> Unit)? = null
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = color
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier)
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SentimentDissatisfied,
                contentDescription = null,
                modifier = Modifier.padding(end = 20.dp)
            )
            Text(
                text = message, style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun InfoCard(autoExpand: Boolean = false) {
    val context = LocalContext.current

    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val isManager = Natives.isManager
    val ksuVersion = if (isManager) Natives.version else null

    var expanded by rememberSaveable { mutableStateOf(false) }

    val developerOptionsEnabled = prefs.getBoolean("enable_developer_options", false)

    LaunchedEffect(autoExpand) {
        if (autoExpand) {
            expanded = true
        }
    }   

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            // Brand identity header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.cannabis_24),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.brand_info),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            @Composable
            fun InfoCardItem(label: String, content: String, icon: Any? = null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        when (icon) {
                            is ImageVector -> Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 20.dp)
                            )
                            is Painter -> Icon(
                                painter = icon,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Column {
                val managerVersion = getManagerVersion(context)
                InfoCardItem(
                    label = stringResource(R.string.home_manager_version),
                    content = if (
                        developerOptionsEnabled
                    ) {
                        "${managerVersion.first} (${managerVersion.second}) | UID: ${Natives.getManagerAppid()}"
                    } else {
                        "${managerVersion.first} (${managerVersion.second})"
                    },
                    icon = Icons.Filled.AutoAwesomeMotion,
                )

                if (ksuVersion != null) {

                    val hookMode =
                        Natives.getHookMode()
                            .takeUnless { it.isNullOrBlank() }
                            ?: stringResource(R.string.unavailable)

                    Spacer(Modifier.height(16.dp))

                    InfoCardItem(
                        label   = stringResource(R.string.hook_mode),
                        content = hookMode,
                        icon    = Icons.Filled.Phishing,
                    )
                }

                if (ksuVersion != null) {
                    Spacer(Modifier.height(16.dp))
                    
                    val moduleViewModel: ModuleViewModel = viewModel()
                    val meta = moduleViewModel.moduleList.firstOrNull {
                        it.isMetaModule && it.enabled && !it.remove
                    }

                    val mountSystem = currentMountSystem()
                        .ifBlank { stringResource(R.string.unavailable) }

                    val content = listOfNotNull(
                        mountSystem,
                        meta?.name?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.home_not_installed),
                        meta?.version?.takeIf { it.isNotBlank() }
                    ).joinToString(" | ")

                    InfoCardItem(
                        label = stringResource(R.string.home_mount_system),
                        content = content,
                        icon = Icons.Filled.SettingsSuggest
                    )

                    val suSFS = getSuSFS()
                    if (suSFS == "Supported") {
                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.home_susfs_version),
                            content = "${stringResource(R.string.supported)} | ${getSuSFSVersion()} (${getSuSFSVariant()})",
                            icon = painterResource(R.drawable.ic_sus),
                        )
                    }

                    if (Natives.isZygiskEnabled()) {
                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.zygisk_status),
                            content = "${stringResource(R.string.enabled)} | ${getZygiskImplementation("name")} | ${getZygiskImplementation("version")}",
                            icon = Icons.Filled.Vaccines
                        )
                    }

                    // SuperKey status - only show on ZTR_OS kernel
                    val versionTag = Natives.getVersionTag()
                    if (!versionTag.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.superkey_title),
                            content = if (Natives.isSuperKeyActive()) {
                                stringResource(R.string.superkey_active)
                            } else {
                                stringResource(R.string.superkey_not_set)
                            },
                            icon = Icons.Filled.Key
                        )
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    val uname = Os.uname()
                    Column {
                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.home_kernel),
                            content = "${uname.release} (${uname.machine})",
                            icon = painterResource(R.drawable.ic_linux),
                        )

                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.home_android),
                            content = "${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})",
                            icon = Icons.Filled.Android,
                        )

                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.home_abi),
                            content = Build.SUPPORTED_ABIS.joinToString(", "),
                            icon = Icons.Filled.Memory,
                        )

                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.home_selinux_status),
                            content = getSELinuxStatus(),
                            icon = Icons.Filled.Security,
                        )

                        Spacer(Modifier.height(16.dp))
                        InfoCardItem(
                            label = stringResource(R.string.kernel_mode_title),
                            content = when (KernelDetect.getKernelMode()) {
                                KernelMode.ZTR_OS -> stringResource(R.string.kernel_mode_ztr_os)
                                KernelMode.KSU_COMPAT -> stringResource(R.string.kernel_mode_ksu_compat)
                                KernelMode.UNKNOWN -> stringResource(R.string.kernel_mode_unknown)
                            },
                            icon = Icons.Filled.Memory,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val rotationAngle by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = tween(durationMillis = 300)
                    )
                    
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expanded) "Show less" else "Show more",
                            modifier = Modifier.graphicsLayer {
                                rotationZ = rotationAngle
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IssueReportCard() {
    val uriHandler = LocalUriHandler.current
    val githubIssueUrl = stringResource(R.string.issue_report_github_link)
    val telegramUrl = stringResource(R.string.issue_report_telegram_link)

    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.issue_report_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.issue_report_body),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.issue_report_body_2),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconButton(onClick = { uriHandler.openUri(githubIssueUrl) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_github),
                        contentDescription = stringResource(R.string.issue_report_github),
                    )
                }
                IconButton(onClick = { uriHandler.openUri(telegramUrl) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_telegram),
                        contentDescription = stringResource(R.string.issue_report_telegram),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val managerVersion = getManagerVersion(context)

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.about_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            // App name & version
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${managerVersion.first} (${managerVersion.second})",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Package name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Column {
                    Text(
                        text = stringResource(R.string.about_card_package),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "com.ztros.ztrosu",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Based on
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 16.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Column {
                    Text(
                        text = stringResource(R.string.about_card_forked_from),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Wild KSU + SukiSU Ultra + KSU Next",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemInfoCard() {
    val context = LocalContext.current

    data class SystemInfo(
        val deviceModel: String,
        val androidVersion: String,
        val kernelVersion: String,
        val securityPatch: String,
        val cpuArch: String,
        val totalMemory: String,
        val availableMemory: String,
        val totalStorage: String,
        val availableStorage: String
    )

    val systemInfo by produceState<SystemInfo?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val deviceModel = ShellUtils.fastCmd("getprop ro.product.model").trim().ifEmpty { Build.MODEL }
            val androidVersion = ShellUtils.fastCmd("getprop ro.build.version.release").trim().ifEmpty { Build.VERSION.RELEASE }
            val kernelVersion = Os.uname().release
            val securityPatch = ShellUtils.fastCmd("getprop ro.build.version.security_patch").trim().ifEmpty { "Unknown" }
            val cpuArch = ShellUtils.fastCmd("getprop ro.product.cpu.abi").trim().ifEmpty { Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown" }

            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            val totalMemory = formatSizeZtrsu(memInfo.totalMem)
            val availableMemory = formatSizeZtrsu(memInfo.availMem)

            val dataPath = Environment.getDataDirectory()
            val statFs = StatFs(dataPath.path)
            val totalStorage = formatSizeZtrsu(statFs.totalBytes)
            val availableStorage = formatSizeZtrsu(statFs.availableBytes)

            SystemInfo(
                deviceModel = deviceModel,
                androidVersion = androidVersion,
                kernelVersion = kernelVersion,
                securityPatch = securityPatch,
                cpuArch = cpuArch,
                totalMemory = totalMemory,
                availableMemory = availableMemory,
                totalStorage = totalStorage,
                availableStorage = availableStorage
            )
        }
    }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.system_info_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            if (systemInfo != null) {
                val info = systemInfo!!
                @Composable
                fun InfoRow(label: String, value: String, icon: ImageVector) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                InfoRow(
                    label = stringResource(R.string.system_info_device_model),
                    value = info.deviceModel,
                    icon = Icons.Filled.PhoneAndroid
                )

                InfoRow(
                    label = stringResource(R.string.system_info_android_version),
                    value = info.androidVersion,
                    icon = Icons.Filled.Android
                )

                InfoRow(
                    label = stringResource(R.string.system_info_kernel_version),
                    value = info.kernelVersion,
                    icon = Icons.Filled.DeveloperBoard
                )

                InfoRow(
                    label = stringResource(R.string.system_info_security_patch),
                    value = info.securityPatch,
                    icon = Icons.Filled.Security
                )

                InfoRow(
                    label = stringResource(R.string.system_info_cpu_arch),
                    value = info.cpuArch,
                    icon = Icons.Filled.Memory
                )

                InfoRow(
                    label = stringResource(R.string.system_info_memory),
                    value = "${info.availableMemory} / ${info.totalMemory}",
                    icon = Icons.Filled.Storage
                )

                InfoRow(
                    label = stringResource(R.string.system_info_storage),
                    value = "${info.availableStorage} / ${info.totalStorage}",
                    icon = Icons.Filled.SdCard
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionsCard() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val rebootConfirmDialog = rememberConfirmDialog()
    val recoveryConfirmDialog = rememberConfirmDialog()
    val bootloaderConfirmDialog = rememberConfirmDialog()
    val systemUiConfirmDialog = rememberConfirmDialog()

    // Pre-resolve strings outside of non-composable contexts
    val rebootTitle = stringResource(R.string.quick_action_reboot)
    val rebootContent = stringResource(R.string.quick_action_reboot_confirm)
    val recoveryTitle = stringResource(R.string.quick_action_reboot_recovery)
    val recoveryContent = stringResource(R.string.quick_action_reboot_recovery_confirm)
    val bootloaderTitle = stringResource(R.string.quick_action_reboot_bootloader)
    val bootloaderContent = stringResource(R.string.quick_action_reboot_bootloader_confirm)
    val systemUiTitle = stringResource(R.string.quick_action_restart_systemui)
    val systemUiContent = stringResource(R.string.quick_action_restart_systemui_confirm)
    val confirmText = stringResource(R.string.confirm)
    val successText = stringResource(R.string.quick_action_success)
    val failedText = stringResource(R.string.quick_action_failed)

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_actions_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Reboot
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            val result = rebootConfirmDialog.awaitConfirm(
                                title = rebootTitle,
                                content = rebootContent,
                                confirm = confirmText
                            )
                            if (result == ConfirmResult.Confirmed) {
                                reboot()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = rebootTitle,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Reboot to Recovery
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            val result = recoveryConfirmDialog.awaitConfirm(
                                title = recoveryTitle,
                                content = recoveryContent,
                                confirm = confirmText
                            )
                            if (result == ConfirmResult.Confirmed) {
                                reboot("recovery")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Healing,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = recoveryTitle,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Reboot to Bootloader
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            val result = bootloaderConfirmDialog.awaitConfirm(
                                title = bootloaderTitle,
                                content = bootloaderContent,
                                confirm = confirmText
                            )
                            if (result == ConfirmResult.Confirmed) {
                                reboot("bootloader")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeveloperBoard,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = bootloaderTitle,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Restart SystemUI
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            val result = systemUiConfirmDialog.awaitConfirm(
                                title = systemUiTitle,
                                content = systemUiContent,
                                confirm = confirmText
                            )
                            if (result == ConfirmResult.Confirmed) {
                                val success = ShellUtils.fastCmdResult("killall com.android.systemui")
                                Toast.makeText(
                                    context,
                                    if (success) successText else failedText,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = systemUiTitle,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleSummaryCard() {
    val moduleViewModel: ModuleViewModel = viewModel()
    val moduleList = moduleViewModel.moduleList

    val installedCount = moduleList.size
    val enabledCount = moduleList.count { it.enabled }
    val disabledCount = installedCount - enabledCount

    // Find the most recently updated module (highest versionCode)
    val recentModule = moduleList
        .filter { !it.remove }
        .maxByOrNull { it.versionCode }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = stringResource(R.string.module_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Installed count
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = installedCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.module_summary_installed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }

                // Enabled count
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = enabledCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.module_summary_enabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }

                // Disabled count
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = disabledCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.module_summary_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            if (recentModule != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Update,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stringResource(R.string.module_summary_recent_update),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${recentModule.name} v${recentModule.version}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatSizeZtrsu(bytes: Long): String {
    return when {
        bytes >= 1 shl 30 -> "%.1f GB".format(bytes.toDouble() / (1 shl 30))
        bytes >= 1 shl 20 -> "%.0f MB".format(bytes.toDouble() / (1 shl 20))
        bytes >= 1 shl 10 -> "%.0f KB".format(bytes.toDouble() / (1 shl 10))
        else -> "$bytes B"
    }
}

@SuppressLint("RestrictedApi")
fun handleDynamicShortcuts(context: Context, moduleConfigs: List <Pair<ModuleViewModel.ModuleInfo, Int>>) {
    ShortcutManagerCompat.removeAllDynamicShortcuts(context)

    moduleConfigs.forEach { (module, iconRes) ->
        val shortcut = ShortcutInfoCompat.Builder(context, module.id)
            .setShortLabel(module.name)
            .setLongLabel(module.name)
            .setIcon(IconCompat.createWithResource(context, iconRes))
            .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
            .setIntent(
                Intent(context, WebUIActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = "kernelsu://webui/${module.id}".toUri()
                    putExtra("id", module.id)
                    putExtra("name", module.name)
                }
            )
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}

fun getManagerVersion(context: Context): Pair<String, Long> {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)!!
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    return Pair(packageInfo.versionName!!, versionCode)
}

@Preview
@Composable
private fun StatusCardPreview() {
    Column {
        StatusCard(KernelVersion(5, 10, 101), 1)
        StatusCard(KernelVersion(5, 10, 101), 20000)
        StatusCard(KernelVersion(5, 10, 101), null)
        StatusCard(KernelVersion(4, 10, 101), null)
    }
}

@Preview
@Composable
private fun WarningCardPreview() {
    Column {
        WarningCard(message = "Warning message")
        WarningCard(
            message = "Warning message",
            MaterialTheme.colorScheme.outlineVariant,
            onClick = {})
    }
}
