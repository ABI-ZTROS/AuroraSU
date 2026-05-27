package com.ztros.ztrosu.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.ztros.ztrosu.Natives
import com.ztros.ztrosu.R
import com.ztros.ztrosu.profile.Capabilities
import com.ztros.ztrosu.profile.Groups
import com.ztros.ztrosu.ui.LocalScrollState
import com.ztros.ztrosu.ui.component.rememberCustomDialog
import com.ztros.ztrosu.ui.rememberScrollConnection
import com.ztros.ztrosu.ui.util.LocalSnackbarHost
import com.ztros.ztrosu.ui.util.setAppProfileTemplate
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

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

private data class TemplateProfileConfig(
    val namespace: Int = Natives.Profile.Namespace.INHERITED.ordinal,
    val uid: Int = Natives.ROOT_UID,
    val gid: Int = Natives.ROOT_GID,
    val groups: List<Int> = emptyList(),
    val capabilities: List<Int> = emptyList(),
    val context: String = Natives.KERNEL_SU_DOMAIN,
    val allowSu: Boolean = true,
    val umountModules: Boolean = false
)

private fun getTemplateProfileConfig(type: TemplateType): TemplateProfileConfig = when (type) {
    TemplateType.GAME -> TemplateProfileConfig(
        namespace = Natives.Profile.Namespace.INDIVIDUAL.ordinal,
        groups = listOf(
            Groups.INET.gid,
            Groups.MEDIA_RW.gid,
            Groups.SDCARD_RW.gid
        ),
        capabilities = listOf(
            Capabilities.CAP_NET_RAW.cap,
            Capabilities.CAP_NET_BIND_SERVICE.cap,
            Capabilities.CAP_SYS_NICE.cap
        ),
        umountModules = false
    )
    TemplateType.SOCIAL -> TemplateProfileConfig(
        namespace = Natives.Profile.Namespace.INHERITED.ordinal,
        groups = listOf(
            Groups.INET.gid,
            Groups.SDCARD_RW.gid
        ),
        capabilities = listOf(
            Capabilities.CAP_NET_RAW.cap
        ),
        umountModules = true
    )
    TemplateType.SYSTEM_TOOL -> TemplateProfileConfig(
        namespace = Natives.Profile.Namespace.INHERITED.ordinal,
        groups = listOf(
            Groups.INET.gid
        ),
        capabilities = emptyList(),
        umountModules = true
    )
    TemplateType.CUSTOM -> TemplateProfileConfig(
        namespace = Natives.Profile.Namespace.INHERITED.ordinal,
        groups = listOf(
            Groups.INET.gid,
            Groups.SDCARD_RW.gid
        ),
        capabilities = listOf(
            Capabilities.CAP_NET_RAW.cap
        ),
        umountModules = false
    )
}

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
private const val KEY_TIME_RULE_ENABLED = "time_rule_enabled_"
private const val EXPORT_DIR = "/data/local/tmp/aurorasu_profiles"

/**
 * Apply a preset template configuration to KernelSU's profile system.
 * Creates a template JSON file and registers it via ksud.
 */
private suspend fun applyTemplate(
    template: ProfileTemplate,
    prefs: android.content.SharedPreferences
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val config = getTemplateProfileConfig(template.type)
        val templateId = "preset_${template.type.name.lowercase()}"

        val templateJson = JSONObject().apply {
            put("id", templateId)
            put("name", template.name)
            put("description", template.description)
            put("local", true)
            put("namespace", Natives.Profile.Namespace.entries[config.namespace].name)
            put("uid", config.uid)
            put("gid", config.gid)
            put("allowSu", config.allowSu)
            put("umountModules", config.umountModules)
            put("context", config.context)

            if (config.groups.isNotEmpty()) {
                val groupsArray = org.json.JSONArray()
                config.groups.forEach { gid ->
                    Groups.entries.find { it.gid == gid }?.name?.let { groupsArray.put(it) }
                }
                put("groups", groupsArray)
            }

            if (config.capabilities.isNotEmpty()) {
                val capsArray = org.json.JSONArray()
                config.capabilities.forEach { cap ->
                    Capabilities.entries.find { it.cap == cap }?.name?.let { capsArray.put(it) }
                }
                put("capabilities", capsArray)
            }
        }

        val result = setAppProfileTemplate(templateId, templateJson.toString())

        if (result) {
            prefs.edit { putBoolean("applied_${template.type.name}", true) }
        }
        result
    }.getOrDefault(false)
}

/**
 * Save time rule configuration to SharedPreferences and write a scheduler script
 * that can be used with crond or Tasker.
 */
private suspend fun saveTimeRule(
    template: ProfileTemplate,
    prefs: android.content.SharedPreferences
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val startTime = prefs.getString(KEY_START_TIME + template.type.name, "") ?: ""
        if (startTime.isBlank()) return@runCatching false

        val parts = startTime.split(":")
        if (parts.size != 2) return@runCatching false
        val hour = parts[0].toIntOrNull() ?: return@runCatching false
        val minute = parts[1].toIntOrNull() ?: return@runCatching false

        val templateId = "preset_${template.type.name.lowercase()}"
        val action = if (template.enabled) "enable" else "disable"
        val scriptPath = "/data/adb/ksu/profile_scheduler_${template.type.name.lowercase()}.sh"

        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# Auto-generated profile scheduler for ${template.name}")
            appendLine("# Template: $templateId")
            appendLine("# Schedule: $startTime daily")
            appendLine("# Action: $action")
            appendLine("am broadcast -a com.ztros.ztrosu.PROFILE_SWITCH --es template_id '$templateId' --ez enabled ${template.enabled} 2>/dev/null || true")
        }

        val escapedScript = script.replace("'", "'\\''")
        ShellUtils.fastCmd("echo '$escapedScript' > '$scriptPath' 2>/dev/null")
        ShellUtils.fastCmd("chmod 755 '$scriptPath' 2>/dev/null")

        prefs.edit { putBoolean(KEY_TIME_RULE_ENABLED + template.type.name, true) }
        true
    }.getOrDefault(false)
}

/**
 * Export all enabled template configurations to a JSON file on the filesystem.
 * Returns the file path on success, null on failure.
 */
private suspend fun exportTemplates(
    templates: List<ProfileTemplate>,
    prefs: android.content.SharedPreferences
): String? = withContext(Dispatchers.IO) {
    runCatching {
        val exportJson = JSONObject()
        val enabledTemplates = templates.filter { it.enabled }

        if (enabledTemplates.isEmpty()) return@withContext null

        val templatesArray = org.json.JSONArray()
        enabledTemplates.forEach { template ->
            val config = getTemplateProfileConfig(template.type)
            val templateObj = JSONObject().apply {
                put("type", template.type.name)
                put("name", template.name)
                put("description", template.description)
                put("enabled", template.enabled)
                put("startTime", prefs.getString(KEY_START_TIME + template.type.name, "") ?: "")
                put("endTime", prefs.getString(KEY_END_TIME + template.type.name, "") ?: "")
                put("namespace", Natives.Profile.Namespace.entries[config.namespace].name)
                put("uid", config.uid)
                put("gid", config.gid)
                put("allowSu", config.allowSu)
                put("umountModules", config.umountModules)
                put("context", config.context)

                val groupsArray = org.json.JSONArray()
                config.groups.forEach { gid ->
                    Groups.entries.find { it.gid == gid }?.name?.let { groupsArray.put(it) }
                }
                put("groups", groupsArray)

                val capsArray = org.json.JSONArray()
                config.capabilities.forEach { cap ->
                    Capabilities.entries.find { it.cap == cap }?.name?.let { capsArray.put(it) }
                }
                put("capabilities", capsArray)
            }
            templatesArray.put(templateObj)
        }
        exportJson.put("templates", templatesArray)
        exportJson.put("exported_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        exportJson.put("version", 1)

        ShellUtils.fastCmd("mkdir -p '$EXPORT_DIR' 2>/dev/null")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportFile = "$EXPORT_DIR/profile_backup_$timestamp.json"

        val escapedContent = exportJson.toString(2).replace("'", "'\\''")
        ShellUtils.fastCmd("echo '$escapedContent' > '$exportFile' 2>/dev/null")

        // Also save a copy to SharedPreferences as fallback
        prefs.edit { putString("last_export_path", exportFile) }

        exportFile
    }.getOrNull()
}

/**
 * Import template configurations from a JSON file content string.
 * Returns the number of templates successfully imported.
 */
private suspend fun importTemplates(
    fileContent: String,
    prefs: android.content.SharedPreferences
): Int = withContext(Dispatchers.IO) {
    runCatching {
        val json = JSONObject(fileContent)
        val templatesArray = json.optJSONArray("templates") ?: return@withContext 0

        var importedCount = 0
        for (i in 0 until templatesArray.length()) {
            val templateObj = templatesArray.getJSONObject(i)
            val typeName = templateObj.optString("type", "")
            val type = runCatching { TemplateType.valueOf(typeName) }.getOrNull() ?: continue

            val enabled = templateObj.optBoolean("enabled", true)
            val startTime = templateObj.optString("startTime", "")
            val endTime = templateObj.optString("endTime", "")

            prefs.edit {
                putBoolean(KEY_ENABLED + type.name, enabled)
                putString(KEY_START_TIME + type.name, startTime)
                putString(KEY_END_TIME + type.name, endTime)
            }

            // Also try to register the template with ksud
            val templateId = "preset_${type.name.lowercase()}"
            setAppProfileTemplate(templateId, templateObj.toString())

            importedCount++
        }
        importedCount
    }.getOrDefault(0)
}

/**
 * Read file content from a URI using ContentResolver.
 */
private suspend fun readUriContent(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().readText()
        }
    }.getOrNull()
}

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

    var isApplying by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<TemplateType?>(null) }

    // Import file picker launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri? = result.data?.data
        if (uri == null) {
            scope.launch {
                snackBarHost.showSnackbar(
                    message = "No file selected",
                    duration = SnackbarDuration.Short
                )
            }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val content = readUriContent(context, uri)
            if (content.isNullOrBlank()) {
                snackBarHost.showSnackbar(
                    message = "Failed to read file or file is empty",
                    duration = SnackbarDuration.Short
                )
                return@launch
            }
            val count = importTemplates(content, prefs)
            if (count > 0) {
                // Refresh template states from prefs
                templates = presetTemplates.map { tpl ->
                    tpl.copy(
                        enabled = prefs.getBoolean(KEY_ENABLED + tpl.type.name, false),
                        startTime = prefs.getString(KEY_START_TIME + tpl.type.name, "") ?: "",
                        endTime = prefs.getString(KEY_END_TIME + tpl.type.name, "") ?: ""
                    )
                }
                snackBarHost.showSnackbar(
                    message = "$importedMsg ($count)",
                    duration = SnackbarDuration.Short
                )
            } else {
                snackBarHost.showSnackbar(
                    message = "No valid templates found in file",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

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

                        // Save time rule scheduler script in background
                        val tpl = templates.find { it.type == type }
                        if (tpl != null) {
                            scope.launch {
                                saveTimeRule(tpl, prefs)
                            }
                        }
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
                            // Show hint when time rule is configured
                            if (template.startTime.isNotBlank()) {
                                Text(
                                    text = "Scheduler script saved. For automatic execution, set up crond or use Tasker with the broadcast action: com.ztros.ztrosu.PROFILE_SWITCH",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                            val exportPath = exportTemplates(templates, prefs)
                            if (exportPath != null) {
                                snackBarHost.showSnackbar(
                                    message = "$exportedMsg\n$exportPath",
                                    duration = SnackbarDuration.Long
                                )
                            } else {
                                snackBarHost.showSnackbar(
                                    message = "No enabled templates to export",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(exportLabel)
                }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            Intent(Intent.ACTION_GET_CONTENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                                putExtra(Intent.EXTRA_TITLE, "profile_templates.json")
                            }
                        )
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
                    val enabledTemplates = templates.filter { it.enabled }
                    if (enabledTemplates.isEmpty()) {
                        scope.launch {
                            snackBarHost.showSnackbar(
                                message = "No templates enabled. Please enable at least one template first.",
                                duration = SnackbarDuration.Short
                            )
                        }
                        return@Button
                    }
                    isApplying = true
                    scope.launch {
                        var successCount = 0
                        var failCount = 0
                        enabledTemplates.forEach { template ->
                            val ok = applyTemplate(template, prefs)
                            if (ok) successCount++ else failCount++
                        }
                        isApplying = false
                        val msg = if (failCount == 0) {
                            "$appliedMsg ($successCount/${enabledTemplates.size})"
                        } else {
                            "Applied $successCount/${enabledTemplates.size}, $failCount failed. Check root access."
                        }
                        snackBarHost.showSnackbar(
                            message = msg,
                            duration = SnackbarDuration.Long
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isApplying
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Applying...")
                } else {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(applyLabel)
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
