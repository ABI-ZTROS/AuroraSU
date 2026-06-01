package com.ztros.ztrosu.ui.util

import android.util.Log
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val TAG = "VFSTemplateManager"

/**
 * VFS Hook Target Configuration
 */
data class TemplateHookTarget(
    val id: String = UUID.randomUUID().toString(),
    val path: String,                    // Target path pattern
    val operations: List<String>,        // Operations to hook: open, read, write, close
    val enabled: Boolean = true,         // Whether this hook is enabled
    val priority: Int = 0                // Hook priority (higher = first)
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("path", path)
            put("operations", JSONArray(operations))
            put("enabled", enabled)
            put("priority", priority)
        }
    }

    companion object {
        fun fromJSON(json: JSONObject): TemplateHookTarget {
            return TemplateHookTarget(
                id = json.optString("id", UUID.randomUUID().toString()),
                path = json.getString("path"),
                operations = json.optJSONArray("operations")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList(),
                enabled = json.optBoolean("enabled", true),
                priority = json.optInt("priority", 0)
            )
        }
    }
}

/**
 * VFS Access Rule
 */
data class TemplateRule(
    val id: String = UUID.randomUUID().toString(),
    val action: String,                  // allow or deny
    val pathPattern: String,             // Path pattern (supports wildcards)
    val mode: String,                    // Access mode: r, w, rw
    val uidFilter: Int = -1,             // UID filter (-1 = all)
    val gidFilter: Int = -1,             // GID filter (-1 = all)
    val enabled: Boolean = true,         // Whether this rule is enabled
    val priority: Int = 0                // Rule priority
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("action", action)
            put("pathPattern", pathPattern)
            put("mode", mode)
            put("uidFilter", uidFilter)
            put("gidFilter", gidFilter)
            put("enabled", enabled)
            put("priority", priority)
        }
    }

    companion object {
        fun fromJSON(json: JSONObject): TemplateRule {
            return TemplateRule(
                id = json.optString("id", UUID.randomUUID().toString()),
                action = json.getString("action"),
                pathPattern = json.getString("pathPattern"),
                mode = json.optString("mode", "rw"),
                uidFilter = json.optInt("uidFilter", -1),
                gidFilter = json.optInt("gidFilter", -1),
                enabled = json.optBoolean("enabled", true),
                priority = json.optInt("priority", 0)
            )
        }
    }
}

/**
 * VFS Policy Settings
 */
data class VFSPolicySettings(
    val enabled: Boolean = false,        // Enable VFS monitoring
    val logLevel: Int = 2,               // Log level: 0-5 (Error to Verbose)
    val defaultAction: String = "allow"  // Default action when no rule matches
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("enabled", enabled)
            put("logLevel", logLevel)
            put("defaultAction", defaultAction)
        }
    }

    companion object {
        fun fromJSON(json: JSONObject): VFSPolicySettings {
            return VFSPolicySettings(
                enabled = json.optBoolean("enabled", false),
                logLevel = json.optInt("logLevel", 2),
                defaultAction = json.optString("defaultAction", "allow")
            )
        }
    }
}

/**
 * VFS Template
 */
data class VFSTemplate(
    val id: String,                      // Template ID
    val name: String,                    // Template name
    val description: String,             // Template description
    val hookTargets: List<TemplateHookTarget>,// Hook target list
    val rules: List<TemplateRule>,            // Rule list
    val policySettings: VFSPolicySettings,// Policy settings
    val createdAt: Long,                 // Creation timestamp
    val isBuiltIn: Boolean               // Whether it's a built-in template
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("description", description)
            put("hookTargets", JSONArray(hookTargets.map { it.toJSON() }))
            put("rules", JSONArray(rules.map { it.toJSON() }))
            put("policySettings", policySettings.toJSON())
            put("createdAt", createdAt)
            put("isBuiltIn", isBuiltIn)
        }
    }

    companion object {
        fun fromJSON(json: JSONObject): VFSTemplate {
            return VFSTemplate(
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.optString("description", ""),
                hookTargets = json.optJSONArray("hookTargets")?.let { arr ->
                    (0 until arr.length()).map { TemplateHookTarget.fromJSON(arr.getJSONObject(it)) }
                } ?: emptyList(),
                rules = json.optJSONArray("rules")?.let { arr ->
                    (0 until arr.length()).map { TemplateRule.fromJSON(arr.getJSONObject(it)) }
                } ?: emptyList(),
                policySettings = json.optJSONObject("policySettings")?.let {
                    VFSPolicySettings.fromJSON(it)
                } ?: VFSPolicySettings(),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                isBuiltIn = json.optBoolean("isBuiltIn", false)
            )
        }
    }
}

/**
 * VFS Template Manager
 * Manages VFS configuration templates including built-in and custom templates
 */
object VFSTemplateManager {

    private const val BUILTIN_TEMPLATE_PREFIX = "builtin_"

    // Built-in templates definitions
    private val builtInTemplates: List<VFSTemplate> = listOf(
        // 1. Developer Mode - Monitor all operations with detailed logging
        VFSTemplate(
            id = "${BUILTIN_TEMPLATE_PREFIX}developer",
            name = "Developer Mode",
            description = "Monitor all VFS operations with detailed logging for debugging and development",
            hookTargets = listOf(
                TemplateHookTarget(
                    path = "/",
                    operations = listOf("open", "read", "write", "close"),
                    enabled = true,
                    priority = 100
                )
            ),
            rules = listOf(
                TemplateRule(
                    action = "allow",
                    pathPattern = "*",
                    mode = "rw",
                    priority = 0
                )
            ),
            policySettings = VFSPolicySettings(
                enabled = true,
                logLevel = 5,  // Verbose
                defaultAction = "allow"
            ),
            createdAt = 0,
            isBuiltIn = true
        ),

        // 2. Privacy Protection - Protect sensitive data paths
        VFSTemplate(
            id = "${BUILTIN_TEMPLATE_PREFIX}privacy",
            name = "Privacy Protection",
            description = "Protect sensitive user data paths from unauthorized access",
            hookTargets = listOf(
                TemplateHookTarget(
                    path = "/data/data/*",
                    operations = listOf("open", "read", "write"),
                    enabled = true,
                    priority = 90
                ),
                TemplateHookTarget(
                    path = "/sdcard/*",
                    operations = listOf("read", "write"),
                    enabled = true,
                    priority = 80
                ),
                TemplateHookTarget(
                    path = "/data/media/*",
                    operations = listOf("read", "write"),
                    enabled = true,
                    priority = 80
                )
            ),
            rules = listOf(
                TemplateRule(
                    action = "deny",
                    pathPattern = "/data/data/*/databases/*",
                    mode = "rw",
                    priority = 100
                ),
                TemplateRule(
                    action = "deny",
                    pathPattern = "/data/data/*/shared_prefs/*",
                    mode = "rw",
                    priority = 90
                ),
                TemplateRule(
                    action = "deny",
                    pathPattern = "/data/data/*/files/*",
                    mode = "rw",
                    priority = 80
                ),
                TemplateRule(
                    action = "allow",
                    pathPattern = "/sdcard/DCIM/*",
                    mode = "r",
                    priority = 50
                ),
                TemplateRule(
                    action = "allow",
                    pathPattern = "*",
                    mode = "r",
                    priority = 0
                )
            ),
            policySettings = VFSPolicySettings(
                enabled = true,
                logLevel = 3,  // Info
                defaultAction = "deny"
            ),
            createdAt = 0,
            isBuiltIn = true
        ),

        // 3. App Isolation - Block inter-app data access
        VFSTemplate(
            id = "${BUILTIN_TEMPLATE_PREFIX}isolation",
            name = "App Isolation",
            description = "Prevent applications from accessing other apps' private data directories",
            hookTargets = listOf(
                TemplateHookTarget(
                    path = "/data/data/*",
                    operations = listOf("open", "read", "write"),
                    enabled = true,
                    priority = 100
                ),
                TemplateHookTarget(
                    path = "/data/user/*",
                    operations = listOf("open", "read", "write"),
                    enabled = true,
                    priority = 90
                )
            ),
            rules = listOf(
                TemplateRule(
                    action = "deny",
                    pathPattern = "/data/data/*",
                    mode = "rw",
                    priority = 100
                ),
                TemplateRule(
                    action = "deny",
                    pathPattern = "/data/user/*",
                    mode = "rw",
                    priority = 90
                ),
                TemplateRule(
                    action = "allow",
                    pathPattern = "/system/*",
                    mode = "r",
                    priority = 10
                ),
                TemplateRule(
                    action = "allow",
                    pathPattern = "*",
                    mode = "r",
                    priority = 0
                )
            ),
            policySettings = VFSPolicySettings(
                enabled = true,
                logLevel = 2,  // Warning
                defaultAction = "deny"
            ),
            createdAt = 0,
            isBuiltIn = true
        ),

        // 4. System Protection - Protect system partitions
        VFSTemplate(
            id = "${BUILTIN_TEMPLATE_PREFIX}system",
            name = "System Protection",
            description = "Protect system partitions from unauthorized modifications",
            hookTargets = listOf(
                TemplateHookTarget(
                    path = "/system/*",
                    operations = listOf("write"),
                    enabled = true,
                    priority = 100
                ),
                TemplateHookTarget(
                    path = "/vendor/*",
                    operations = listOf("write"),
                    enabled = true,
                    priority = 90
                ),
                TemplateHookTarget(
                    path = "/product/*",
                    operations = listOf("write"),
                    enabled = true,
                    priority = 80
                ),
                TemplateHookTarget(
                    path = "/system_ext/*",
                    operations = listOf("write"),
                    enabled = true,
                    priority = 70
                )
            ),
            rules = listOf(
                TemplateRule(
                    action = "deny",
                    pathPattern = "/system/*",
                    mode = "w",
                    priority = 100
                ),
                TemplateRule(
                    action = "deny",
                    pathPattern = "/vendor/*",
                    mode = "w",
                    priority = 90
                ),
                TemplateRule(
                    action = "deny",
                    pathPattern = "/product/*",
                    mode = "w",
                    priority = 80
                ),
                TemplateRule(
                    action = "deny",
                    pathPattern = "/system_ext/*",
                    mode = "w",
                    priority = 70
                ),
                TemplateRule(
                    action = "deny",
                    pathPattern = "/boot*",
                    mode = "rw",
                    priority = 60
                ),
                TemplateRule(
                    action = "allow",
                    pathPattern = "*",
                    mode = "rw",
                    priority = 0
                )
            ),
            policySettings = VFSPolicySettings(
                enabled = true,
                logLevel = 1,  // Error
                defaultAction = "allow"
            ),
            createdAt = 0,
            isBuiltIn = true
        ),

        // 5. Custom - Empty template for user configuration
        VFSTemplate(
            id = "${BUILTIN_TEMPLATE_PREFIX}custom",
            name = "Custom",
            description = "Empty template for custom VFS configuration",
            hookTargets = emptyList(),
            rules = emptyList(),
            policySettings = VFSPolicySettings(
                enabled = false,
                logLevel = 2,
                defaultAction = "allow"
            ),
            createdAt = 0,
            isBuiltIn = true
        )
    )

    // Custom templates storage
    private var customTemplates: MutableList<VFSTemplate> = mutableListOf()

    /**
     * Initialize template manager
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Load custom templates from persistence
            val loadedTemplates = VFSPersistenceManager.loadTemplates()
            customTemplates.clear()
            customTemplates.addAll(loadedTemplates.filter { !it.isBuiltIn })
            Log.i(TAG, "Initialized with ${customTemplates.size} custom templates")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize template manager", e)
            false
        }
    }

    /**
     * Get all templates (built-in + custom)
     */
    fun getAllTemplates(): List<VFSTemplate> {
        return builtInTemplates + customTemplates
    }

    /**
     * Get built-in templates only
     */
    fun getBuiltInTemplates(): List<VFSTemplate> = builtInTemplates

    /**
     * Get custom templates only
     */
    fun getCustomTemplates(): List<VFSTemplate> = customTemplates.toList()

    /**
     * Get template by ID
     */
    fun getTemplateById(id: String): VFSTemplate? {
        return getAllTemplates().find { it.id == id }
    }

    /**
     * Create a new custom template
     */
    suspend fun createTemplate(
        name: String,
        description: String,
        hookTargets: List<TemplateHookTarget> = emptyList(),
        rules: List<TemplateRule> = emptyList(),
        policySettings: VFSPolicySettings = VFSPolicySettings()
    ): VFSTemplate? = withContext(Dispatchers.IO) {
        try {
            val id = "custom_${UUID.randomUUID().toString().replace("-", "").take(8)}"
            val template = VFSTemplate(
                id = id,
                name = name,
                description = description,
                hookTargets = hookTargets,
                rules = rules,
                policySettings = policySettings,
                createdAt = System.currentTimeMillis(),
                isBuiltIn = false
            )

            customTemplates.add(template)
            saveTemplates()

            Log.i(TAG, "Created template: $id")
            template
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create template", e)
            null
        }
    }

    /**
     * Apply template - batch set VFS configuration
     */
    suspend fun applyTemplate(templateId: String): Boolean = withContext(Dispatchers.IO) {
        val template = getTemplateById(templateId)
        if (template == null) {
            Log.e(TAG, "Template not found: $templateId")
            return@withContext false
        }

        try {
            // Convert template hook targets to persistence format
            // TemplateHookTarget uses path/operations, persistence uses simple JSON
            val hookTargetsJson = JSONArray(template.hookTargets.map { it.toJSON() })
            VFSPersistenceManager.saveHookTargetsRaw(hookTargetsJson.toString())

            // Convert template rules to persistence format
            val rulesJson = JSONArray(template.rules.map { it.toJSON() })
            VFSPersistenceManager.saveRulesRaw(rulesJson.toString())

            // Save policy settings
            VFSPersistenceManager.savePolicySettings(template.policySettings)

            // Apply to kernel/userspace backend
            val policy = VFSPolicy(
                enabled = template.policySettings.enabled,
                logLevel = template.policySettings.logLevel,
                defaultAction = template.policySettings.defaultAction,
                rules = template.rules.filter { it.enabled }.map { rule ->
                    "${rule.action}:${rule.pathPattern}:${rule.mode}"
                }
            )
            VFSDebugUtil.setVFSPolicy(policy)

            // Mark this template as active
            VFSPersistenceManager.saveActiveTemplateId(templateId)

            Log.i(TAG, "Applied template: $templateId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply template: $templateId", e)
            false
        }
    }

    /**
     * Export template to JSON string
     */
    fun exportTemplate(templateId: String): String? {
        val template = getTemplateById(templateId)
        if (template == null) {
            Log.e(TAG, "Template not found for export: $templateId")
            return null
        }
        return template.toJSON().toString()
    }

    /**
     * Export all custom templates to JSON array string
     */
    fun exportAllCustomTemplates(): String {
        val jsonArray = JSONArray()
        customTemplates.forEach { template ->
            jsonArray.put(template.toJSON())
        }
        return jsonArray.toString()
    }

    /**
     * Import template from JSON string
     */
    suspend fun importTemplate(jsonString: String): VFSTemplate? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(jsonString)
            val template = VFSTemplate.fromJSON(json)

            // Check if template ID already exists
            if (getTemplateById(template.id) != null) {
                // Generate new ID for imported template
                val newId = "custom_${UUID.randomUUID().toString().replace("-", "").take(8)}"
                val importedTemplate = template.copy(
                    id = newId,
                    isBuiltIn = false,
                    createdAt = System.currentTimeMillis()
                )
                customTemplates.add(importedTemplate)
                saveTemplates()
                Log.i(TAG, "Imported template with new ID: $newId")
                importedTemplate
            } else {
                val importedTemplate = template.copy(
                    isBuiltIn = false,
                    createdAt = System.currentTimeMillis()
                )
                customTemplates.add(importedTemplate)
                saveTemplates()
                Log.i(TAG, "Imported template: ${template.id}")
                importedTemplate
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import template", e)
            null
        }
    }

    /**
     * Import multiple templates from JSON array string
     */
    suspend fun importTemplates(jsonArrayString: String): List<VFSTemplate> = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray(jsonArrayString)
            val importedTemplates = mutableListOf<VFSTemplate>()

            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val template = VFSTemplate.fromJSON(json)

                // Generate new ID to avoid conflicts
                val newId = "custom_${UUID.randomUUID().toString().replace("-", "").take(8)}"
                val importedTemplate = template.copy(
                    id = newId,
                    isBuiltIn = false,
                    createdAt = System.currentTimeMillis()
                )
                customTemplates.add(importedTemplate)
                importedTemplates.add(importedTemplate)
            }

            saveTemplates()
            Log.i(TAG, "Imported ${importedTemplates.size} templates")
            importedTemplates
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import templates", e)
            emptyList()
        }
    }

    /**
     * Delete a custom template
     */
    suspend fun deleteTemplate(templateId: String): Boolean = withContext(Dispatchers.IO) {
        // Cannot delete built-in templates
        if (templateId.startsWith(BUILTIN_TEMPLATE_PREFIX)) {
            Log.e(TAG, "Cannot delete built-in template: $templateId")
            return@withContext false
        }

        val removed = customTemplates.removeAll { it.id == templateId }
        if (removed) {
            saveTemplates()
            Log.i(TAG, "Deleted template: $templateId")
            true
        } else {
            Log.e(TAG, "Template not found for deletion: $templateId")
            false
        }
    }

    /**
     * Clone a template (creates a copy with new ID)
     */
    suspend fun cloneTemplate(templateId: String, newName: String? = null): VFSTemplate? = withContext(Dispatchers.IO) {
        val sourceTemplate = getTemplateById(templateId)
        if (sourceTemplate == null) {
            Log.e(TAG, "Source template not found: $templateId")
            return@withContext null
        }

        try {
            val newId = "custom_${UUID.randomUUID().toString().replace("-", "").take(8)}"
            val clonedTemplate = VFSTemplate(
                id = newId,
                name = newName ?: "${sourceTemplate.name} (Copy)",
                description = sourceTemplate.description,
                hookTargets = sourceTemplate.hookTargets.map { it.copy(id = UUID.randomUUID().toString()) },
                rules = sourceTemplate.rules.map { it.copy(id = UUID.randomUUID().toString()) },
                policySettings = sourceTemplate.policySettings,
                createdAt = System.currentTimeMillis(),
                isBuiltIn = false
            )

            customTemplates.add(clonedTemplate)
            saveTemplates()

            Log.i(TAG, "Cloned template: $templateId -> $newId")
            clonedTemplate
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clone template: $templateId", e)
            null
        }
    }

    /**
     * Update an existing custom template
     */
    suspend fun updateTemplate(templateId: String, updates: VFSTemplateUpdate): Boolean = withContext(Dispatchers.IO) {
        // Cannot update built-in templates
        if (templateId.startsWith(BUILTIN_TEMPLATE_PREFIX)) {
            Log.e(TAG, "Cannot update built-in template: $templateId")
            return@withContext false
        }

        val index = customTemplates.indexOfFirst { it.id == templateId }
        if (index == -1) {
            Log.e(TAG, "Template not found for update: $templateId")
            return@withContext false
        }

        try {
            val existing = customTemplates[index]
            val updated = existing.copy(
                name = updates.name ?: existing.name,
                description = updates.description ?: existing.description,
                hookTargets = updates.hookTargets ?: existing.hookTargets,
                rules = updates.rules ?: existing.rules,
                policySettings = updates.policySettings ?: existing.policySettings
            )

            customTemplates[index] = updated
            saveTemplates()

            Log.i(TAG, "Updated template: $templateId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update template: $templateId", e)
            false
        }
    }

    /**
     * Validate template
     */
    fun validateTemplate(template: VFSTemplate): Pair<Boolean, String> {
        // Validate name
        if (template.name.isBlank()) {
            return Pair(false, "Template name cannot be empty")
        }

        // Validate policy settings
        if (template.policySettings.logLevel !in 0..5) {
            return Pair(false, "Log level must be between 0-5")
        }

        if (template.policySettings.defaultAction !in listOf("allow", "deny")) {
            return Pair(false, "Default action must be 'allow' or 'deny'")
        }

        // Validate rules
        template.rules.forEach { rule ->
            if (rule.action !in listOf("allow", "deny")) {
                return Pair(false, "Rule action must be 'allow' or 'deny': ${rule.id}")
            }
            if (rule.pathPattern.isBlank()) {
                return Pair(false, "Rule path pattern cannot be empty: ${rule.id}")
            }
            val validModes = listOf("r", "w", "rw")
            if (rule.mode.isNotBlank() && rule.mode !in validModes) {
                return Pair(false, "Invalid rule mode: ${rule.mode}")
            }
        }

        // Validate hook targets
        template.hookTargets.forEach { target ->
            if (target.path.isBlank()) {
                return Pair(false, "Hook target path cannot be empty: ${target.id}")
            }
            val validOps = listOf("open", "read", "write", "close")
            target.operations.forEach { op ->
                if (op !in validOps) {
                    return Pair(false, "Invalid operation: $op")
                }
            }
        }

        return Pair(true, "")
    }

    /**
     * Get currently active template ID
     */
    suspend fun getActiveTemplateId(): String? = withContext(Dispatchers.IO) {
        VFSPersistenceManager.loadActiveTemplateId()
    }

    /**
     * Save custom templates to persistence
     */
    private suspend fun saveTemplates() = withContext(Dispatchers.IO) {
        VFSPersistenceManager.saveTemplates(customTemplates)
    }

    /**
     * Export template to file
     */
    suspend fun exportTemplateToFile(templateId: String, filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = exportTemplate(templateId)
            if (json == null) return@withContext false

            val file = SuFile.open(filePath)
            file.writeText(json)

            Log.i(TAG, "Exported template to file: $filePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export template to file", e)
            false
        }
    }

    /**
     * Import template from file
     */
    suspend fun importTemplateFromFile(filePath: String): VFSTemplate? = withContext(Dispatchers.IO) {
        try {
            val file = SuFile.open(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found: $filePath")
                return@withContext null
            }

            val json = file.readText()
            importTemplate(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import template from file", e)
            null
        }
    }
}

/**
 * Template update data class for partial updates
 */
data class VFSTemplateUpdate(
    val name: String? = null,
    val description: String? = null,
    val hookTargets: List<TemplateHookTarget>? = null,
    val rules: List<TemplateRule>? = null,
    val policySettings: VFSPolicySettings? = null
)