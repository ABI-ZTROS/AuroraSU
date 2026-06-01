package com.ztros.ztrosu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "VFSPersistenceManager"

/**
 * VFS Persistence Manager
 * Manages persistent storage of VFS configuration data
 */
object VFSPersistenceManager {

    // Persistence base path
    private const val VFS_BASE_PATH = "/data/adb/ztrosu"

    // Configuration file paths
    private const val VFS_CONFIG_FILE = "$VFS_BASE_PATH/vfs_config.json"
    private const val VFS_HOOKS_FILE = "$VFS_BASE_PATH/vfs_hooks.json"
    private const val VFS_RULES_FILE = "$VFS_BASE_PATH/vfs_rules.json"
    private const val VFS_TEMPLATES_FILE = "$VFS_BASE_PATH/vfs_templates.json"
    private const val VFS_STATS_HISTORY_FILE = "$VFS_BASE_PATH/vfs_stats_history.json"

    // Configuration version for migration support
    private const val CONFIG_VERSION = 1

    // Auto-save state tracking
    private val _autoSaveEnabled = MutableStateFlow(true)
    val autoSaveEnabled: StateFlow<Boolean> = _autoSaveEnabled

    // Pending changes tracking
    private var hasPendingChanges = false
    private var lastSaveTime = 0L
    private const val MIN_SAVE_INTERVAL_MS = 1000L // Minimum 1 second between saves

    /**
     * Initialize persistence directory and files
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Create base directory if not exists
            val baseDir = SuFile.open(VFS_BASE_PATH)
            if (!baseDir.exists()) {
                val result = Shell.cmd("mkdir -p $VFS_BASE_PATH").exec()
                if (!result.isSuccess) {
                    Log.e(TAG, "Failed to create base directory")
                    return@withContext false
                }
                // Set proper permissions
                Shell.cmd("chmod 700 $VFS_BASE_PATH").exec()
                Shell.cmd("chown root:root $VFS_BASE_PATH").exec()
            }

            // Initialize configuration files if not exist
            initializeConfigFile(VFS_CONFIG_FILE, createDefaultConfig())
            initializeConfigFile(VFS_HOOKS_FILE, JSONArray().toString())
            initializeConfigFile(VFS_RULES_FILE, JSONArray().toString())
            initializeConfigFile(VFS_TEMPLATES_FILE, JSONArray().toString())
            initializeConfigFile(VFS_STATS_HISTORY_FILE, JSONArray().toString())

            Log.i(TAG, "Persistence manager initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize persistence manager", e)
            false
        }
    }

    /**
     * Initialize a configuration file with default content if not exists
     */
    private fun initializeConfigFile(path: String, defaultContent: String) {
        val file = SuFile.open(path)
        if (!file.exists()) {
            file.writeText(defaultContent)
            Shell.cmd("chmod 600 $path").exec()
            Shell.cmd("chown root:root $path").exec()
            Log.i(TAG, "Created config file: $path")
        }
    }

    /**
     * Create default configuration JSON
     */
    private fun createDefaultConfig(): String {
        return JSONObject().apply {
            put("version", CONFIG_VERSION)
            put("enabled", false)
            put("logLevel", 2)
            put("defaultAction", "allow")
            put("activeTemplateId", "")
            put("lastModified", System.currentTimeMillis())
        }.toString()
    }

    // ==================== Main Configuration ====================

    /**
     * Load main configuration
     */
    suspend fun loadConfig(): VFSConfig? = withContext(Dispatchers.IO) {
        try {
            val file = SuFile.open(VFS_CONFIG_FILE)
            if (!file.exists()) {
                Log.w(TAG, "Config file not found, returning default")
                return@withContext VFSConfig()
            }

            val json = JSONObject(file.readText())
            VFSConfig.fromJSON(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config", e)
            null
        }
    }

    /**
     * Save main configuration
     */
    suspend fun saveConfig(config: VFSConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            checkSaveInterval()

            val file = SuFile.open(VFS_CONFIG_FILE)
            val json = config.toJSON()
            json.put("lastModified", System.currentTimeMillis())
            file.writeText(json.toString())

            lastSaveTime = System.currentTimeMillis()
            Log.i(TAG, "Saved main configuration")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config", e)
            false
        }
    }

    // ==================== Hook Targets ====================

    /**
     * Load hook targets
     */
    suspend fun loadHookTargets(): List<VFSHookTarget> = withContext(Dispatchers.IO) {
        try {
            val file = SuFile.open(VFS_HOOKS_FILE)
            if (!file.exists()) return@withContext emptyList()

            val json = JSONArray(file.readText())
            (0 until json.length()).map { VFSHookTarget.fromJSON(json.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load hook targets", e)
            emptyList()
        }
    }

    /**
     * Save hook targets (trigger auto-save)
     */
    suspend fun saveHookTargets(targets: List<VFSHookTarget>): Boolean = withContext(Dispatchers.IO) {
        try {
            checkSaveInterval()

            val file = SuFile.open(VFS_HOOKS_FILE)
            val json = JSONArray(targets.map { it.toJSON() })
            file.writeText(json.toString())

            lastSaveTime = System.currentTimeMillis()
            triggerAutoSave("hook_targets")
            Log.i(TAG, "Saved ${targets.size} hook targets")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save hook targets", e)
            false
        }
    }

    /**
     * Add a hook target
     */
    suspend fun addHookTarget(target: VFSHookTarget): Boolean = withContext(Dispatchers.IO) {
        val targets = loadHookTargets().toMutableList()
        targets.add(target)
        saveHookTargets(targets)
    }

    /**
     * Update a hook target
     */
    suspend fun updateHookTarget(target: VFSHookTarget): Boolean = withContext(Dispatchers.IO) {
        val targets = loadHookTargets().toMutableList()
        val index = targets.indexOfFirst { it.id == target.id }
        if (index == -1) return@withContext false

        targets[index] = target
        saveHookTargets(targets)
    }

    /**
     * Remove a hook target
     */
    suspend fun removeHookTarget(targetId: String): Boolean = withContext(Dispatchers.IO) {
        val targets = loadHookTargets().toMutableList()
        val removed = targets.removeAll { it.id == targetId }
        if (removed) {
            saveHookTargets(targets)
        } else {
            false
        }
    }

    // ==================== Rules ====================

    /**
     * Load rules
     */
    suspend fun loadRules(): List<VFSRule> = withContext(Dispatchers.IO) {
        try {
            val file = SuFile.open(VFS_RULES_FILE)
            if (!file.exists()) return@withContext emptyList()

            val json = JSONArray(file.readText())
            (0 until json.length()).map { VFSRule.fromJSON(json.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load rules", e)
            emptyList()
        }
    }

    /**
     * Save rules (trigger auto-save)
     */
    suspend fun saveRules(rules: List<VFSRule>): Boolean = withContext(Dispatchers.IO) {
        try {
            checkSaveInterval()

            val file = SuFile.open(VFS_RULES_FILE)
            val json = JSONArray(rules.map { it.toJSON() })
            file.writeText(json.toString())

            lastSaveTime = System.currentTimeMillis()
            triggerAutoSave("rules")
            Log.i(TAG, "Saved ${rules.size} rules")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save rules", e)
            false
        }
    }

    /**
     * Add a rule
     */
    suspend fun addRule(rule: VFSRule): Boolean = withContext(Dispatchers.IO) {
        val rules = loadRules().toMutableList()
        rules.add(rule)
        saveRules(rules)
    }

    /**
     * Update a rule
     */
    suspend fun updateRule(rule: VFSRule): Boolean = withContext(Dispatchers.IO) {
        val rules = loadRules().toMutableList()
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index == -1) return@withContext false

        rules[index] = rule
        saveRules(rules)
    }

    /**
     * Remove a rule
     */
    suspend fun removeRule(ruleId: String): Boolean = withContext(Dispatchers.IO) {
        val rules = loadRules().toMutableList()
        val removed = rules.removeAll { it.id == ruleId }
        if (removed) {
            saveRules(rules)
        } else {
            false
        }
    }

    // ==================== Policy Settings ====================

    /**
     * Load policy settings from main config
     */
    suspend fun loadPolicySettings(): VFSPolicySettings = withContext(Dispatchers.IO) {
        val config = loadConfig()
        config?.policySettings ?: VFSPolicySettings()
    }

    /**
     * Save policy settings (trigger auto-save)
     */
    suspend fun savePolicySettings(settings: VFSPolicySettings): Boolean = withContext(Dispatchers.IO) {
        try {
            checkSaveInterval()

            val config = loadConfig() ?: VFSConfig()
            val updatedConfig = config.copy(policySettings = settings)
            saveConfig(updatedConfig)

            triggerAutoSave("policy_settings")
            Log.i(TAG, "Saved policy settings")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save policy settings", e)
            false
        }
    }

    // ==================== Templates ====================

    /**
     * Load templates
     */
    suspend fun loadTemplates(): List<VFSTemplate> = withContext(Dispatchers.IO) {
        try {
            val file = SuFile.open(VFS_TEMPLATES_FILE)
            if (!file.exists()) return@withContext emptyList()

            val json = JSONArray(file.readText())
            (0 until json.length()).map { VFSTemplate.fromJSON(json.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load templates", e)
            emptyList()
        }
    }

    /**
     * Save templates (trigger auto-save)
     */
    suspend fun saveTemplates(templates: List<VFSTemplate>): Boolean = withContext(Dispatchers.IO) {
        try {
            checkSaveInterval()

            val file = SuFile.open(VFS_TEMPLATES_FILE)
            val json = JSONArray(templates.map { it.toJSON() })
            file.writeText(json.toString())

            lastSaveTime = System.currentTimeMillis()
            triggerAutoSave("templates")
            Log.i(TAG, "Saved ${templates.size} templates")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save templates", e)
            false
        }
    }

    // ==================== Active Template ====================

    /**
     * Load active template ID
     */
    suspend fun loadActiveTemplateId(): String? = withContext(Dispatchers.IO) {
        val config = loadConfig()
        config?.activeTemplateId?.takeIf { it.isNotEmpty() }
    }

    /**
     * Save active template ID
     */
    suspend fun saveActiveTemplateId(templateId: String): Boolean = withContext(Dispatchers.IO) {
        val config = loadConfig() ?: VFSConfig()
        val updatedConfig = config.copy(activeTemplateId = templateId)
        saveConfig(updatedConfig)
    }

    // ==================== Statistics History ====================

    /**
     * Load statistics history
     */
    suspend fun loadStatsHistory(): List<VFSStatsRecord> = withContext(Dispatchers.IO) {
        try {
            val file = SuFile.open(VFS_STATS_HISTORY_FILE)
            if (!file.exists()) return@withContext emptyList()

            val json = JSONArray(file.readText())
            (0 until json.length()).map { VFSStatsRecord.fromJSON(json.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load stats history", e)
            emptyList()
        }
    }

    /**
     * Save statistics history
     */
    suspend fun saveStatsHistory(history: List<VFSStatsRecord>): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = SuFile.open(VFS_STATS_HISTORY_FILE)
            val json = JSONArray(history.map { it.toJSON() })
            file.writeText(json.toString())
            Log.i(TAG, "Saved ${history.size} stats records")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save stats history", e)
            false
        }
    }

    /**
     * Add a stats record to history
     */
    suspend fun addStatsRecord(record: VFSStatsRecord): Boolean = withContext(Dispatchers.IO) {
        val history = loadStatsHistory().toMutableList()

        // Keep only last 1000 records
        if (history.size >= 1000) {
            history.removeAt(0)
        }

        history.add(record)
        saveStatsHistory(history)
    }

    /**
     * Clear statistics history
     */
    suspend fun clearStatsHistory(): Boolean = withContext(Dispatchers.IO) {
        saveStatsHistory(emptyList())
    }

    // ==================== Auto-save Management ====================

    /**
     * Enable/disable auto-save
     */
    fun setAutoSaveEnabled(enabled: Boolean) {
        _autoSaveEnabled.value = enabled
        Log.i(TAG, "Auto-save enabled: $enabled")
    }

    /**
     * Trigger auto-save for specific change type
     */
    private fun triggerAutoSave(changeType: String) {
        if (!_autoSaveEnabled.value) {
            hasPendingChanges = true
            Log.d(TAG, "Auto-save disabled, marking pending changes: $changeType")
            return
        }

        Log.d(TAG, "Auto-save triggered for: $changeType")
        hasPendingChanges = false
    }

    /**
     * Check if there are pending changes
     */
    fun hasPendingChanges(): Boolean = hasPendingChanges

    /**
     * Force save all pending changes
     */
    suspend fun forceSavePendingChanges(): Boolean = withContext(Dispatchers.IO) {
        if (!hasPendingChanges) return@withContext true

        Log.i(TAG, "Force saving pending changes")
        hasPendingChanges = false
        true
    }

    /**
     * Check minimum save interval to prevent excessive writes
     */
    private fun checkSaveInterval() {
        val now = System.currentTimeMillis()
        if (now - lastSaveTime < MIN_SAVE_INTERVAL_MS) {
            Thread.sleep(MIN_SAVE_INTERVAL_MS - (now - lastSaveTime))
        }
    }

    // ==================== Configuration Sync ====================

    /**
     * Sync all configurations from current state
     */
    suspend fun syncAllConfigurations(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Load current policy from kernel/userspace
            val policy = VFSDebugUtil.getVFSPolicy()

            // Sync to config file
            val settings = VFSPolicySettings(
                enabled = policy.enabled,
                logLevel = policy.logLevel,
                defaultAction = policy.defaultAction
            )
            savePolicySettings(settings)

            // Sync rules
            val rules = policy.rules.map { ruleStr ->
                parseRuleString(ruleStr)
            }.filterNotNull()
            saveRules(rules)

            Log.i(TAG, "Synced all configurations")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync configurations", e)
            false
        }
    }

    /**
     * Parse rule string to VFSRule object
     */
    private fun parseRuleString(ruleStr: String): VFSRule? {
        val parts = ruleStr.split(":")
        if (parts.size < 3) return null

        return VFSRule(
            action = parts[0],
            pathPattern = parts[1],
            mode = parts[2],
            enabled = true
        )
    }

    // ==================== Version Management ====================

    /**
     * Get configuration version
     */
    suspend fun getConfigVersion(): Int = withContext(Dispatchers.IO) {
        val config = loadConfig()
        config?.version ?: 0
    }

    /**
     * Migrate configuration if needed
     */
    suspend fun migrateIfNeeded(): Boolean = withContext(Dispatchers.IO) {
        val currentVersion = getConfigVersion()
        if (currentVersion >= CONFIG_VERSION) {
            return@withContext true
        }

        Log.i(TAG, "Migrating configuration from version $currentVersion to $CONFIG_VERSION")

        // Perform migration steps here
        // For now, just update version
        val config = loadConfig() ?: VFSConfig()
        val migratedConfig = config.copy(version = CONFIG_VERSION)
        saveConfig(migratedConfig)

        Log.i(TAG, "Configuration migration completed")
        true
    }

    // ==================== Backup & Restore ====================

    /**
     * Backup all configurations to a single file
     */
    suspend fun backupToFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val backup = JSONObject().apply {
                put("version", CONFIG_VERSION)
                put("timestamp", System.currentTimeMillis())

                // Load and include all configurations
                put("config", loadConfig()?.toJSON() ?: createDefaultConfig())

                val hooks = loadHookTargets()
                put("hooks", JSONArray(hooks.map { it.toJSON() }))

                val rules = loadRules()
                put("rules", JSONArray(rules.map { it.toJSON() }))

                val templates = loadTemplates()
                put("templates", JSONArray(templates.map { it.toJSON() }))

                val statsHistory = loadStatsHistory()
                put("statsHistory", JSONArray(statsHistory.map { it.toJSON() }))
            }

            val file = SuFile.open(filePath)
            file.writeText(backup.toString())

            Log.i(TAG, "Backup saved to: $filePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup configurations", e)
            false
        }
    }

    /**
     * Restore all configurations from a backup file
     */
    suspend fun restoreFromFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = SuFile.open(filePath)
            if (!file.exists()) {
                Log.e(TAG, "Backup file not found: $filePath")
                return@withContext false
            }

            val backup = JSONObject(file.readText())

            // Restore config
            backup.optJSONObject("config")?.let {
                saveConfig(VFSConfig.fromJSON(it) ?: VFSConfig())
            }

            // Restore hooks
            backup.optJSONArray("hooks")?.let { arr ->
                val hooks = (0 until arr.length()).map { VFSHookTarget.fromJSON(arr.getJSONObject(it)) }
                saveHookTargets(hooks)
            }

            // Restore rules
            backup.optJSONArray("rules")?.let { arr ->
                val rules = (0 until arr.length()).map { VFSRule.fromJSON(arr.getJSONObject(it)) }
                saveRules(rules)
            }

            // Restore templates
            backup.optJSONArray("templates")?.let { arr ->
                val templates = (0 until arr.length()).map { VFSTemplate.fromJSON(arr.getJSONObject(it)) }
                saveTemplates(templates)
            }

            // Restore stats history (optional)
            backup.optJSONArray("statsHistory")?.let { arr ->
                val history = (0 until arr.length()).map { VFSStatsRecord.fromJSON(arr.getJSONObject(it)) }
                saveStatsHistory(history)
            }

            Log.i(TAG, "Restored configurations from: $filePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore configurations", e)
            false
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Check if persistence is available
     */
    fun isPersistenceAvailable(): Boolean {
        return SuFile.open(VFS_BASE_PATH).exists()
    }

    /**
     * Get persistence directory info
     */
    fun getPersistenceInfo(): PersistenceInfo {
        val baseDir = SuFile.open(VFS_BASE_PATH)
        return PersistenceInfo(
            basePath = VFS_BASE_PATH,
            exists = baseDir.exists(),
            configFile = SuFile.open(VFS_CONFIG_FILE).exists(),
            hooksFile = SuFile.open(VFS_HOOKS_FILE).exists(),
            rulesFile = SuFile.open(VFS_RULES_FILE).exists(),
            templatesFile = SuFile.open(VFS_TEMPLATES_FILE).exists(),
            statsHistoryFile = SuFile.open(VFS_STATS_HISTORY_FILE).exists()
        )
    }

    /**
     * Clear all configurations
     */
    suspend fun clearAllConfigurations(): Boolean = withContext(Dispatchers.IO) {
        try {
            saveConfig(VFSConfig())
            saveHookTargets(emptyList())
            saveRules(emptyList())
            saveTemplates(emptyList())
            clearStatsHistory()

            Log.i(TAG, "All configurations cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear configurations", e)
            false
        }
    }

    /**
     * Export configuration summary for debugging
     */
    suspend fun exportSummary(): String = withContext(Dispatchers.IO) {
        val summary = JSONObject().apply {
            put("persistenceInfo", JSONObject().apply {
                put("basePath", VFS_BASE_PATH)
                put("available", isPersistenceAvailable())
            })

            put("config", loadConfig()?.toJSON() ?: JSONObject())

            put("hooksCount", loadHookTargets().size)
            put("rulesCount", loadRules().size)
            put("templatesCount", loadTemplates().size)
            put("statsHistoryCount", loadStatsHistory().size)

            put("autoSaveEnabled", _autoSaveEnabled.value)
            put("hasPendingChanges", hasPendingChanges)
            put("lastSaveTime", lastSaveTime)
        }
        summary.toString()
    }
}

/**
 * Main VFS Configuration data class
 */
data class VFSConfig(
    val version: Int = 1,
    val policySettings: VFSPolicySettings = VFSPolicySettings(),
    val activeTemplateId: String = "",
    val lastModified: Long = System.currentTimeMillis()
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("policySettings", policySettings.toJSON())
            put("activeTemplateId", activeTemplateId)
            put("lastModified", lastModified)
        }
    }

    companion object {
        fun fromJSON(json: JSONObject): VFSConfig {
            return VFSConfig(
                version = json.optInt("version", 1),
                policySettings = json.optJSONObject("policySettings")?.let {
                    VFSPolicySettings.fromJSON(it)
                } ?: VFSPolicySettings(),
                activeTemplateId = json.optString("activeTemplateId", ""),
                lastModified = json.optLong("lastModified", System.currentTimeMillis())
            )
        }
    }
}

/**
 * VFS Statistics Record for history tracking
 */
data class VFSStatsRecord(
    val timestamp: Long = System.currentTimeMillis(),
    val openCount: Long = 0,
    val readCount: Long = 0,
    val writeCount: Long = 0,
    val closeCount: Long = 0,
    val deniedCount: Long = 0
) {
    fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("timestamp", timestamp)
            put("openCount", openCount)
            put("readCount", readCount)
            put("writeCount", writeCount)
            put("closeCount", closeCount)
            put("deniedCount", deniedCount)
        }
    }

    companion object {
        fun fromJSON(json: JSONObject): VFSStatsRecord {
            return VFSStatsRecord(
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                openCount = json.optLong("openCount", 0),
                readCount = json.optLong("readCount", 0),
                writeCount = json.optLong("writeCount", 0),
                closeCount = json.optLong("closeCount", 0),
                deniedCount = json.optLong("deniedCount", 0)
            )
        }
    }

    fun toVFSStats(): VFSStats {
        return VFSStats(
            openCount = openCount,
            readCount = readCount,
            writeCount = writeCount,
            closeCount = closeCount,
            deniedCount = deniedCount,
            lastUpdated = timestamp
        )
    }
}

/**
 * Persistence directory information
 */
data class PersistenceInfo(
    val basePath: String,
    val exists: Boolean,
    val configFile: Boolean,
    val hooksFile: Boolean,
    val rulesFile: Boolean,
    val templatesFile: Boolean,
    val statsHistoryFile: Boolean
) {
    fun allFilesExist(): Boolean {
        return exists && configFile && hooksFile && rulesFile && templatesFile
    }
}