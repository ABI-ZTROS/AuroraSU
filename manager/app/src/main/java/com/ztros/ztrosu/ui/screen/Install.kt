package com.ztros.ztrosu.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FlashScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.ztros.ztrosu.*
import com.ztros.ztrosu.R
import com.ztros.ztrosu.ui.util.*

/**
 * @author weishu
 * @date 2024/3/12.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun InstallScreen(navigator: DestinationsNavigator) {
    var selectedOption by remember { mutableStateOf<InstallMethod?>(null) }
    var selectedMountMode by remember { mutableStateOf(-1) } // -1 = Default
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val anyKernelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        navigator.navigate(FlashScreenDestination(FlashIt.FlashAnyKernel(uri)))
    }

    val modulePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = result.data ?: return@rememberLauncherForActivityResult
        val clipData = data.clipData

        val uris = mutableListOf<Uri>()
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                clipData.getItemAt(i)?.uri?.let { uris.add(it) }
            }
        } else {
            data.data?.let { uris.add(it) }
        }

        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        // Store the selected mount mode for this installation
        if (selectedMountMode != -1) {
            prefs.edit { putInt("install_mount_mode", selectedMountMode) }
        } else {
            prefs.edit { remove("install_mount_mode") }
        }

        navigator.navigate(FlashScreenDestination(FlashIt.FlashModules(uris)))
    }

    val onInstall = {
        when (selectedOption) {
            is InstallMethod.AnyKernel -> {
                anyKernelPicker.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    addCategory(Intent.CATEGORY_OPENABLE)
                })
            }

            is InstallMethod.Module -> {
                modulePicker.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addCategory(Intent.CATEGORY_OPENABLE)
                })
            }

            else -> {
                // no-op
            }
        }
    }

    Scaffold(
        topBar = {
            TopBar(
                onBack = dropUnlessResumed { navigator.popBackStack() },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SelectInstallMethod(selectedOption = selectedOption) {
                selectedOption = it
            }

            // Show mount mode selector when Module install is selected
            if (selectedOption is InstallMethod.Module) {
                MountModeSelector(
                    selectedMountMode = selectedMountMode,
                    onMountModeSelected = { selectedMountMode = it }
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedOption != null,
                onClick = onInstall
            ) {
                Text(
                    text = stringResource(id = R.string.install_next),
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                )
            }
        }
    }
}

@Composable
private fun MountModeSelector(
    selectedMountMode: Int,
    onMountModeSelected: (Int) -> Unit
) {
    val mountModeOptions = listOf(
        Pair(R.string.module_install_mount_mode_default, -1),
        Pair(R.string.mount_mode_magic, 1),
        Pair(R.string.mount_mode_overlay, 2)
    )

    var showDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.module_install_mount_mode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Box {
                OutlinedButton(
                    onClick = { showDropdown = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = mountModeOptions.firstOrNull { it.second == selectedMountMode }?.let {
                            stringResource(it.first)
                        } ?: stringResource(R.string.module_install_mount_mode_default),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null
                    )
                }

                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false }
                ) {
                    mountModeOptions.forEach { (stringRes, mode) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(stringRes)) },
                            onClick = {
                                onMountModeSelected(mode)
                                showDropdown = false
                            },
                            trailingIcon = {
                                if (selectedMountMode == mode) {
                                    Text(
                                        text = "\u2713",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

sealed class InstallMethod {
    abstract val label: Int

    data object AnyKernel : InstallMethod() {
        @StringRes
        override val label: Int = R.string.flash_anykernel
    }

    data object Module : InstallMethod() {
        @StringRes
        override val label: Int = R.string.flash_module
    }
}

@Composable
private fun SelectInstallMethod(
    selectedOption: InstallMethod?,
    onSelected: (InstallMethod) -> Unit
) {
    val options = listOf(InstallMethod.Module, InstallMethod.AnyKernel)

    Column {
        options.forEach { option ->
            val interactionSource = remember { MutableInteractionSource() }
            val selected = selectedOption == option

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = selected,
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        role = Role.RadioButton,
                        onValueChange = {
                            onSelected(option)
                        }
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(option.label),
                    style = MaterialTheme.typography.bodyLarge
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
                text = stringResource(R.string.install),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
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

@Composable
@Preview
fun SelectInstallPreview() {
    InstallScreen(EmptyDestinationsNavigator)
}
