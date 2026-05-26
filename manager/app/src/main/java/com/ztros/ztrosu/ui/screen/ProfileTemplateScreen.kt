package com.ztros.ztrosu.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.ztros.ztrosu.ui.component.rememberCustomDialog
import com.ztros.ztrosu.ui.rememberScrollConnection
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import kotlinx.coroutines.launch

private enum class TemplateType {
    GAME, SOCIAL, SYSTEM_TOOL, CUSTOM
}

private data class ProfileTemplate(
    val type: TemplateType,
    val name: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val enabled: Boolean = false,
    val startTime: String = "",
    val endTime: String = ""
)

private fun getPresetTemplates(): List<ProfileTemplate> = listOf(
    ProfileTemplate(
        type = TemplateType.GAME,
        name = "Game",
        description = "Optimized for gaming apps with performance priority",
        icon = Icons.Filled.SportsEsports
    ),
    ProfileTemplate(
        type = TemplateType.SOCIAL,
        name = "Social",
        description = "Balanced profile for social media applications",
        icon = Icons.Filled.People
    ),
    ProfileTemplate(
        type = TemplateType.SYSTEM_TOOL,
        name = "System Tool",
        description = "Restricted profile for system utility apps",
        icon = Icons.Filled.Build
    ),
    ProfileTemplate(
        type = TemplateType.CUSTOM,
        name = "Custom",
        description = "Create your own custom profile template",
        icon = Icons.Filled.Edit
    )
)

private const val PREFS_NAME = "profile_templates"
private const val KEY_ENABLED = "enabled_"
private const val KEY_START_TIME = "start_time_"
private const val KEY_END_TIME = "end_time_"

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun ProfileTemplateScreen(navigator: DestinationsNavigator) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scrollState = LocalScrollState.current
    val isNavBarHidden = scrollState?.isScrollingDown?.value ?: false
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + if (isNavBarHidden) 0.dp else 112.dp

    val presetLabel = stringResource(R.string.profile_template_preset)
    val timeRuleLabel = stringResource(R.string.profile_template_time_rule)
    val exportLabel = stringResource(R.string.profile_template_export)
    val importLabel = stringResource(R.string.profile_template_import)
    val applyLabel = stringResource(R.string.profile_template_apply)
    val appliedMsg = stringResource(R.string.profile_template_applied)
    val exportedMsg = stringResource(R.string.profile_template_exported)
    val importedMsg = stringResource(R.string.profile_template_imported)
    val startTimeLabel = stringResource(R.string.profile_template_start_time)
    val endTimeLabel = stringResource(R.string.profile_template_end_time)

    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val presetTemplates = remember { getPresetTemplates() }

    var templates by remember {
        mutableStateOf(
            presetTemplates.map { tpl ->
                tpl.copy(
                    enabled = prefs.getBoolean(KEY_ENABLED + tpl.type.name, false),
                    startTime = prefs.getString(KEY_START_TIME + tpl.type.name, "") ?: "",
                    endTime = prefs.getString(KEY_END_TIME + tpl.type.name, "") ?: ""
                )
            }
        )
    }

    var selectedTemplate by remember { mutableStateOf<TemplateType?>(null) }
    var timeRuleEnabledState by remember { mutableStateOf(false) }

    // Time picker dialog
    val timePickerDialog = rememberCustomDialog { dismiss ->
        val currentHour = remember { mutableIntStateOf(0) }
        val currentMinute = remember { mutableIntStateOf(0) }

        AlertDialog(
            onDismissRequest = dismiss,
            title = {
                Text(
                    text = if (selectedTemplate != null) startTimeLabel else endTimeLabel,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Simple hour/minute input
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("HH", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(
                            value = "%02d".format(currentHour.intValue),
                            onValueChange = { v ->
                                v.toIntOrNull()?.let { if (it in 0..23) currentHour.intValue = it }
                            },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                    }
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("MM", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(
                            value = "%02d".format(currentMinute.intValue),
                            onValueChange = { v ->
                                v.toIntOrNull()?.let { if (it in 0..59) currentMinute.intValue = it }
                            },
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val timeStr = "%02d:%02d".format(currentHour.intValue, currentMinute.intValue)
                    selectedTemplate?.let { type ->
                        templates = templates.map {
                            if (it.type == type) it.copy(startTime = timeStr) else it
                        }
                        prefs.edit { putString(KEY_START_TIME + type.name, timeStr) }
                    }
                    dismiss()
                }) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        )
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
            // Preset Templates
            Text(
                text = presetLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            templates.forEach { template ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = template.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = template.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = template.enabled,
                                    onCheckedChange = { checked ->
                                        templates = templates.map {
                                            if (it.type == template.type) it.copy(enabled = checked) else it
                                        }
                                        prefs.edit { putBoolean(KEY_ENABLED + template.type.name, checked) }
                                    }
                                )
                            }
                        )

                        // Time rule section for enabled templates
                        if (template.enabled) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = timeRuleLabel,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                TextButton(onClick = {
                                    selectedTemplate = template.type
                                    timePickerDialog.show()
                                }) {
                                    Text(
                                        text = if (template.startTime.isNotBlank()) {
                                            "$startTimeLabel: ${template.startTime}"
                                        } else {
                                            startTimeLabel
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Export / Import Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val data = templates.filter { it.enabled }.map {
                                "${it.type.name}:${it.startTime}:${it.endTime}"
                            }.joinToString("\n")
                            prefs.edit { putString("exported_templates", data) }
                            snackBarHost.showSnackbar(message = exportedMsg)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(exportLabel)
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val data = prefs.getString("exported_templates", "") ?: ""
                            if (data.isNotBlank()) {
                                data.lines().forEach { line ->
                                    val parts = line.split(":")
                                    if (parts.size >= 1) {
                                        val type = runCatching { TemplateType.valueOf(parts[0]) }.getOrNull()
                                        if (type != null) {
                                            templates = templates.map {
                                                if (it.type == type) it.copy(
                                                    enabled = true,
                                                    startTime = parts.getOrElse(1) { "" },
                                                    endTime = parts.getOrElse(2) { "" }
                                                ) else it
                                            }
                                            prefs.edit {
                                                putBoolean(KEY_ENABLED + type.name, true)
                                                putString(KEY_START_TIME + type.name, parts.getOrElse(1) { "" })
                                                putString(KEY_END_TIME + type.name, parts.getOrElse(2) { "" })
                                            }
                                        }
                                    }
                                }
                                snackBarHost.showSnackbar(message = importedMsg)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(importLabel)
                }
            }

            // Apply Button
            Button(
                onClick = {
                    scope.launch { snackBarHost.showSnackbar(message = appliedMsg) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(applyLabel)
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
                text = stringResource(R.string.profile_template_title),
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
private fun ProfileTemplatePreview() {
    ProfileTemplateScreen(EmptyDestinationsNavigator)
}
