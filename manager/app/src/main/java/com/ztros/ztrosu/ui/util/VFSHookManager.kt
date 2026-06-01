package com.ztros.ztrosu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val TAG = "VFSHookManager"

// Configuration file path
private const val VFS_HOOKS_CONFIG_PATH = "/data/adb/ztrosu/vfs_hooks.json"
private const val VFS_HOOKS_DIR = "/data/adb/ztrosu"

// Kernel sysfs paths for hook control
private const val VFS_SYSFS_PATH = "/sys/kernel/ztrosu/vfs"
private const val VFS_DEBUGFS_PATH = "/sys/kernel/debug/ztrosu/vfs"

/**
 * Hook target type
 */
enum class HookType {
    PID,        // Hook by process ID
    PACKAGE     // Hook by package name
}

/**
 * Hook mode - defines what operations to intercept
 */
enum class HookMode(val displayName: String, val description: String) {
    MONITOR_ONLY("Monitor Only", "Only monitor, no interception"),
    INTERCEPT_READ("Intercept Read", "Intercept read operations"),
    INTERCEPT_WRITE("Intercept Write", "Intercept write operations"),
    INTERCEPT_ALL("Intercept All", "Intercept all operations")
}

/**
 * VFS Hook target data class
 */
data class VFSHookTarget(
    val id: String,           // Unique identifier
    val type: HookType,       // PID or PACKAGE
    val identifier: String,   // PID number or package name
    val uid: Int,             // UID (auto-fetched)
    val mode: HookMode,       // Hook mode
    val enabled: Boolean,     // Is enabled
    val createdAt: Long,      // Creation timestamp
    val processName: String = "",  // Process name (optional, for display)
    val lastPid: Int = -1     // Last known PID (for package type, may change)
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("type", type.name)
            put("identifier", identifier)
            put("uid", uid)
            put("mode", mode.name)
            put("enabled", enabled)
            put("createdAt", createdAt)
            put("processName", processName)
            put("lastPid", lastPid)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): VFSHookTarget {
            return VFSHookTarget(
                id = json.getString("id"),
                type = HookType.valueOf(json.getString("type")),
                identifier = json.getString("identifier"),
                uid = json.getInt("uid"),
                mode = HookMode.valueOf(json.getString("mode")),
                enabled = json.getBoolean("enabled"),
                createdAt = json.getLong("createdAt"),
                processName = json.optString("processName", ""),
                lastPid = json.optInt("lastPid", -1)
            )
        }
    }
}

/**
 * VFS Hook Manager - Manages PID/Package level hooks for VFS monitoring
 */
object VFSHookManager {

    private var cachedTargets: MutableList<VFSHookTarget>? = null

    // ==================== PID Hook Management ====================

    /**
     * Add a PID to the monitoring list
     * @param pid Process ID to monitor
     * @param mode Hook mode
     * @param enabled Whether to enable immediately
     * @return Created hook target, or null if failed
     */
    suspend fun addPidHook(
        pid: Int,
        mode: HookMode = HookMode.MONITOR_ONLY,
        enabled: Boolean = true
    ): VFSHookTarget? = withContext(Dispatchers.IO) {
        try {
            // Validate PID exists
            val procDir = File("/proc/$pid")
            if (!procDir.exists()) {
                Log.w(TAG, "PID $pid does not exist")
                return@withContext null
            }

            // Get UID from /proc/[pid]/status
            val uid = getUidFromPid(pid)
            if (uid < 0) {
                Log.w(TAG, "Failed to get UID for PID $pid")
                return@withContext null
            }

            // Get process name
            val processName = getProcessNameFromPid(pid)

            val target = VFSHookTarget(
                id = UUID.randomUUID().toString(),
                type = HookType.PID,
                identifier = pid.toString(),
                uid = uid,
                mode = mode,
                enabled = enabled,
                createdAt = System.currentTimeMillis(),
                processName = processName,
                lastPid = pid
            )

            if (addTarget(target)) {
                applyHookToKernel(target)
                target
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add PID hook for $pid", e)
            null
        }
    }

    /**
     * Add hook by process name (auto-find PID)
     * @param processName Process name to find
     * @param mode Hook mode
     * @param enabled Whether to enable immediately
     * @return Created hook target, or null if failed
     */
    suspend fun addHookByProcessName(
        processName: String,
        mode: HookMode = HookMode.MONITOR_ONLY,
        enabled: Boolean = true
    ): VFSHookTarget? = withContext(Dispatchers.IO) {
        try {
            val pids = findPidsByProcessName(processName)
            if (pids.isEmpty()) {
                Log.w(TAG, "No running process found with name: $processName")
                return@withContext null
            }

            // Use the first found PID
            val pid = pids.first()
            addPidHook(pid, mode, enabled)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add hook for process $processName", e)
            null
        }
    }

    /**
     * Remove a PID hook
     * @param id Hook target ID
     * @return Whether removal was successful
     */
    suspend fun removePidHook(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val target = getTarget(id)
            if (target == null) {
                Log.w(TAG, "Hook target not found: $id")
                return@withContext false
            }

            if (removeTarget(id)) {
                removeHookFromKernel(target)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove PID hook $id", e)
            false
        }
    }

    /**
     * Remove hook by PID value
     * @param pid Process ID
     * @return Whether removal was successful
     */
    suspend fun removeHookByPid(pid: Int): Boolean = withContext(Dispatchers.IO) {
        val targets = getHookTargets()
        val target = targets.find { it.type == HookType.PID && it.identifier == pid.toString() }
        if (target != null) {
            removePidHook(target.id)
        } else {
            false
        }
    }

    // ==================== Package Hook Management ====================

    /**
     * Add hook by package name
     * @param packageName Android package name
     * @param mode Hook mode
     * @param enabled Whether to enable immediately
     * @return Created hook target, or null if failed
     */
    suspend fun addPackageHook(
        packageName: String,
        mode: HookMode = HookMode.MONITOR_ONLY,
        enabled: Boolean = true
    ): VFSHookTarget? = withContext(Dispatchers.IO) {
        try {
            // Get UID for package
            val uid = getUidForPackage(packageName)
            if (uid < 0) {
                Log.w(TAG, "Failed to get UID for package $packageName")
                return@withContext null
            }

            // Check if already exists
            val existing = getHookTargets().find { 
                it.type == HookType.PACKAGE && it.identifier == packageName 
            }
            if (existing != null) {
                Log.w(TAG, "Package $packageName already has a hook")
                return@withContext null
            }

            // Try to get current PID
            val currentPid = findPidForPackage(packageName)

            val target = VFSHookTarget(
                id = UUID.randomUUID().toString(),
                type = HookType.PACKAGE,
                identifier = packageName,
                uid = uid,
                mode = mode,
                enabled = enabled,
                createdAt = System.currentTimeMillis(),
                processName = packageName,
                lastPid = currentPid
            )

            if (addTarget(target)) {
                applyHookToKernel(target)
                target
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add package hook for $packageName", e)
            null
        }
    }

    /**
     * Remove hook by package name
     * @param packageName Package name
     * @return Whether removal was successful
     */
    suspend fun removeHookByPackage(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val targets = getHookTargets()
        val target = targets.find { it.type == HookType.PACKAGE && it.identifier == packageName }
        if (target != null) {
            removePidHook(target.id)
        } else {
            false
        }
    }

    /**
     * Batch add package hooks
     * @param packageNames List of package names
     * @param mode Hook mode for all
     * @param enabled Whether to enable all
     * @return List of successfully created targets
     */
    suspend fun batchAddPackageHooks(
        packageNames: List<String>,
        mode: HookMode = HookMode.MONITOR_ONLY,
        enabled: Boolean = true
    ): List<VFSHookTarget> = withContext(Dispatchers.IO) {
        val results = mutableListOf<VFSHookTarget>()
        for (packageName in packageNames) {
            addPackageHook(packageName, mode, enabled)?.let { results.add(it) }
        }
        results
    }

    /**
     * Batch remove hooks
     * @param ids List of hook target IDs
     * @return Number of successfully removed targets
     */
    suspend fun batchRemoveHooks(ids: List<String>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (id in ids) {
            if (removePidHook(id)) {
                count++
            }
        }
        count
    }

    // ==================== Hook Target Management ====================

    /**
     * Get all hook targets
     */
    suspend fun getHookTargets(): List<VFSHookTarget> = withContext(Dispatchers.IO) {
        if (cachedTargets != null) {
            return@withContext cachedTargets!!.toList()
        }

        loadTargets()
    }

    /**
     * Get a specific hook target by ID
     */
    suspend fun getTarget(id: String): VFSHookTarget? = withContext(Dispatchers.IO) {
        getHookTargets().find { it.id == id }
    }

    /**
     * Update hook target
     */
    suspend fun updateTarget(target: VFSHookTarget): Boolean = withContext(Dispatchers.IO) {
        try {
            val targets = getHookTargets().toMutableList()
            val index = targets.indexOfFirst { it.id == target.id }
            if (index < 0) {
                return@withContext false
            }

            targets[index] = target
            saveTargets(targets)

            // Re-apply to kernel if enabled
            if (target.enabled) {
                applyHookToKernel(target)
            } else {
                removeHookFromKernel(target)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update target ${target.id}", e)
            false
        }
    }

    /**
     * Toggle hook enabled status
     */
    suspend fun toggleHook(id: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val target = getTarget(id) ?: return@withContext false
        updateTarget(target.copy(enabled = enabled))
    }

    /**
     * Update hook mode
     */
    suspend fun updateHookMode(id: String, mode: HookMode): Boolean = withContext(Dispatchers.IO) {
        val target = getTarget(id) ?: return@withContext false
        updateTarget(target.copy(mode = mode))
    }

    /**
     * Refresh package hooks (update PIDs for running apps)
     */
    suspend fun refreshPackageHooks(): Int = withContext(Dispatchers.IO) {
        var updated = 0
        val targets = getHookTargets().toMutableList()

        for (i in targets.indices) {
            val target = targets[i]
            if (target.type == HookType.PACKAGE) {
                val newPid = findPidForPackage(target.identifier)
                if (newPid != target.lastPid) {
                    targets[i] = target.copy(lastPid = newPid)
                    updated++

                    // Re-apply hook with new PID
                    if (target.enabled && newPid > 0) {
                        applyHookToKernel(targets[i])
                    }
                }
            }
        }

        if (updated > 0) {
            saveTargets(targets)
        }
        updated
    }

    // ==================== PID/UID Helper Functions ====================

    /**
     * Get UID from PID by reading /proc/[pid]/status
     */
    fun getUidFromPid(pid: Int): Int {
        return try {
            val statusFile = File("/proc/$pid/status")
            if (!statusFile.exists()) return -1

            val content = statusFile.readText()
            val uidLine = content.lines().find { it.startsWith("Uid:") }
            uidLine?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull() ?: -1
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get UID for PID $pid", e)
            -1
        }
    }

    /**
     * Get process name from PID
     */
    fun getProcessNameFromPid(pid: Int): String {
        return try {
            val cmdlineFile = File("/proc/$pid/cmdline")
            if (!cmdlineFile.exists()) return ""

            val cmdline = cmdlineFile.readText()
            val parts = cmdline.split('\u0000')
            parts.firstOrNull()?.substringAfterLast('/') ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get process name for PID $pid", e)
            ""
        }
    }

    /**
     * Find PIDs by process name using pidof
     */
    fun findPidsByProcessName(processName: String): List<Int> {
        return try {
            val result = Shell.cmd("pidof '$processName'").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                result.out.first().split("\\s+".toRegex())
                    .mapNotNull { it.trim().toIntOrNull() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find PIDs for process $processName", e)
            emptyList()
        }
    }

    /**
     * Get UID for a package using pm command
     */
    fun getUidForPackage(packageName: String): Int {
        return try {
            val result = Shell.cmd("pm list packages -U $packageName").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                // Format: package:<packageName> uid:<uid>
                val line = result.out.first()
                val uidMatch = Regex("uid:(\\d+)").find(line)
                uidMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1
            } else {
                // Fallback: try dumpsys
                val dumpResult = Shell.cmd("dumpsys package $packageName | grep userId=").exec()
                if (dumpResult.isSuccess && dumpResult.out.isNotEmpty()) {
                    val userIdMatch = Regex("userId=(\\d+)").find(dumpResult.out.first())
                    userIdMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1
                } else {
                    -1
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get UID for package $packageName", e)
            -1
        }
    }

    /**
     * Find PID for a package using pidof
     */
    fun findPidForPackage(packageName: String): Int {
        return try {
            val result = Shell.cmd("pidof '$packageName'").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                result.out.first().split("\\s+".toRegex())
                    .firstOrNull()?.trim()?.toIntOrNull() ?: -1
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Get all running processes
     */
    fun getRunningProcesses(): List<Triple<Int, Int, String>> {
        return try {
            val procDir = File("/proc")
            val processes = mutableListOf<Triple<Int, Int, String>>()

            procDir.listFiles()?.forEach { pidDir ->
                val pid = pidDir.name.toIntOrNull() ?: return@forEach
                val uid = getUidFromPid(pid)
                val name = getProcessNameFromPid(pid)
                if (uid >= 0 && name.isNotEmpty()) {
                    processes.add(Triple(pid, uid, name))
                }
            }

            processes.sortedBy { it.first }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get running processes", e)
            emptyList()
        }
    }

    /**
     * Get installed packages with their UIDs
     */
    fun getInstalledPackages(): List<Pair<String, Int>> {
        return try {
            val result = Shell.cmd("pm list packages -U").exec()
            if (result.isSuccess) {
                result.out.mapNotNull { line ->
                    // Format: package:<packageName> uid:<uid>
                    val packageMatch = Regex("package:([^\\s]+)").find(line)
                    val uidMatch = Regex("uid:(\\d+)").find(line)
                    if (packageMatch != null && uidMatch != null) {
                        val packageName = packageMatch.groupValues[1]
                        val uid = uidMatch.groupValues[1].toIntOrNull() ?: -1
                        if (uid >= 0) packageName to uid else null
                    } else {
                        null
                    }
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed packages", e)
            emptyList()
        }
    }

    // ==================== Persistence ====================

    private fun loadTargets(): List<VFSHookTarget> {
        return try {
            val file = SuFile.open(VFS_HOOKS_CONFIG_PATH)
            if (!file.exists()) {
                cachedTargets = mutableListOf()
                return emptyList()
            }

            val content = file.readText()
            val json = JSONObject(content)
            val targetsArray = json.optJSONArray("targets") ?: JSONArray()
            val targets = (0 until targetsArray.length()).mapNotNull { i ->
                try {
                    VFSHookTarget.fromJson(targetsArray.getJSONObject(i))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse target at index $i", e)
                    null
                }
            }

            cachedTargets = targets.toMutableList()
            targets
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load targets", e)
            cachedTargets = mutableListOf()
            emptyList()
        }
    }

    private fun saveTargets(targets: List<VFSHookTarget>): Boolean {
        return try {
            // Ensure directory exists
            val dir = SuFile.open(VFS_HOOKS_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val json = JSONObject()
            val targetsArray = JSONArray()
            targets.forEach { targetsArray.put(it.toJson()) }
            json.put("targets", targetsArray)
            json.put("version", 1)
            json.put("lastModified", System.currentTimeMillis())

            val file = SuFile.open(VFS_HOOKS_CONFIG_PATH)
            file.writeText(json.toString(2))

            cachedTargets = targets.toMutableList()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save targets", e)
            false
        }
    }

    private fun addTarget(target: VFSHookTarget): Boolean {
        val targets = getHookTargets().toMutableList()
        targets.add(target)
        return saveTargets(targets)
    }

    private fun removeTarget(id: String): Boolean {
        val targets = getHookTargets().toMutableList()
        val removed = targets.removeAll { it.id == id }
        if (removed) {
            return saveTargets(targets)
        }
        return false
    }

    // ==================== Kernel Integration ====================

    /**
     * Apply hook to kernel
     */
    private fun applyHookToKernel(target: VFSHookTarget): Boolean {
        return try {
            val basePath = when {
                SuFile.open(VFS_SYSFS_PATH).exists() -> VFS_SYSFS_PATH
                SuFile.open(VFS_DEBUGFS_PATH).exists() -> VFS_DEBUGFS_PATH
                else -> {
                    Log.w(TAG, "No kernel VFS interface available")
                    return false
                }
            }

            // Write to hook_targets file
            val hookFile = SuFile.open("$basePath/hook_targets")
            if (hookFile.exists()) {
                // Format: add:<type>:<identifier>:<uid>:<mode>
                val command = "add:${target.type.name}:${target.identifier}:${target.uid}:${target.mode.name}"
                hookFile.appendText("$command\n")
                Log.i(TAG, "Applied hook to kernel: $command")
                true
            } else {
                // Fallback: use userspace daemon
                applyHookViaUserspace(target)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply hook to kernel", e)
            false
        }
    }

    /**
     * Remove hook from kernel
     */
    private fun removeHookFromKernel(target: VFSHookTarget): Boolean {
        return try {
            val basePath = when {
                SuFile.open(VFS_SYSFS_PATH).exists() -> VFS_SYSFS_PATH
                SuFile.open(VFS_DEBUGFS_PATH).exists() -> VFS_DEBUGFS_PATH
                else -> {
                    Log.w(TAG, "No kernel VFS interface available")
                    return false
                }
            }

            val hookFile = SuFile.open("$basePath/hook_targets")
            if (hookFile.exists()) {
                // Format: remove:<type>:<identifier>
                val command = "remove:${target.type.name}:${target.identifier}"
                hookFile.appendText("$command\n")
                Log.i(TAG, "Removed hook from kernel: $command")
                true
            } else {
                removeHookViaUserspace(target)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove hook from kernel", e)
            false
        }
    }

    /**
     * Apply hook via userspace daemon
     */
    private fun applyHookViaUserspace(target: VFSHookTarget): Boolean {
        return try {
            val ksudPath = "/data/adb/ksu/ksud"
            val command = "$ksudPath vfs-hook add ${target.type.name} ${target.identifier} ${target.uid} ${target.mode.name}"
            val result = Shell.cmd(command).exec()
            Log.i(TAG, "Applied hook via userspace: ${result.isSuccess}")
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply hook via userspace", e)
            false
        }
    }

    /**
     * Remove hook via userspace daemon
     */
    private fun removeHookViaUserspace(target: VFSHookTarget): Boolean {
        return try {
            val ksudPath = "/data/adb/ksu/ksud"
            val command = "$ksudPath vfs-hook remove ${target.type.name} ${target.identifier}"
            val result = Shell.cmd(command).exec()
            Log.i(TAG, "Removed hook via userspace: ${result.isSuccess}")
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove hook via userspace", e)
            false
        }
    }

    /**
     * Apply all enabled hooks to kernel
     */
    suspend fun applyAllHooks(): Int = withContext(Dispatchers.IO) {
        val targets = getHookTargets().filter { it.enabled }
        var applied = 0
        for (target in targets) {
            if (applyHookToKernel(target)) {
                applied++
            }
        }
        Log.i(TAG, "Applied $applied/${targets.size} hooks to kernel")
        applied
    }

    /**
     * Clear all hooks from kernel
     */
    suspend fun clearAllHooks(): Int = withContext(Dispatchers.IO) {
        val targets = getHookTargets()
        var cleared = 0
        for (target in targets) {
            if (removeHookFromKernel(target)) {
                cleared++
            }
        }
        Log.i(TAG, "Cleared $cleared/${targets.size} hooks from kernel")
        cleared
    }

    // ==================== Utility Functions ====================

    /**
     * Get hooks filtered by type
     */
    suspend fun getHooksByType(type: HookType): List<VFSHookTarget> = withContext(Dispatchers.IO) {
        getHookTargets().filter { it.type == type }
    }

    /**
     * Get hooks filtered by mode
     */
    suspend fun getHooksByMode(mode: HookMode): List<VFSHookTarget> = withContext(Dispatchers.IO) {
        getHookTargets().filter { it.mode == mode }
    }

    /**
     * Get enabled hooks
     */
    suspend fun getEnabledHooks(): List<VFSHookTarget> = withContext(Dispatchers.IO) {
        getHookTargets().filter { it.enabled }
    }

    /**
     * Check if a PID is being monitored
     */
    suspend fun isPidMonitored(pid: Int): Boolean = withContext(Dispatchers.IO) {
        getHookTargets().any { it.type == HookType.PID && it.identifier == pid.toString() && it.enabled }
    }

    /**
     * Check if a package is being monitored
     */
    suspend fun isPackageMonitored(packageName: String): Boolean = withContext(Dispatchers.IO) {
        getHookTargets().any { it.type == HookType.PACKAGE && it.identifier == packageName && it.enabled }
    }

    /**
     * Export hooks configuration
     */
    suspend fun exportConfig(): String? = withContext(Dispatchers.IO) {
        try {
            val file = SuFile.open(VFS_HOOKS_CONFIG_PATH)
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export config", e)
            null
        }
    }

    /**
     * Import hooks configuration
     */
    suspend fun importConfig(jsonContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(jsonContent)
            val targetsArray = json.optJSONArray("targets") ?: return@withContext false
            val targets = (0 until targetsArray.length()).mapNotNull { i ->
                try {
                    VFSHookTarget.fromJson(targetsArray.getJSONObject(i))
                } catch (e: Exception) {
                    null
                }
            }
            saveTargets(targets)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import config", e)
            false
        }
    }

    /**
     * Clear all hooks (both config and kernel)
     */
    suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        try {
            clearAllHooks()
            cachedTargets = null
            val file = SuFile.open(VFS_HOOKS_CONFIG_PATH)
            if (file.exists()) {
                file.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all", e)
            false
        }
    }

    /**
     * Invalidate cache to force reload
     */
    fun invalidateCache() {
        cachedTargets = null
    }
}