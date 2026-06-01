package com.ztros.ztrosu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "VFSDebugUtil"
private const val VFS_SYSFS_PATH = "/sys/kernel/ztrosu/vfs"
private const val VFS_DEBUGFS_PATH = "/sys/kernel/debug/ztrosu/vfs"

data class VFSStats(
    val openCount: Long = 0,
    val readCount: Long = 0,
    val writeCount: Long = 0,
    val closeCount: Long = 0,
    val deniedCount: Long = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class VFSPolicy(
    val enabled: Boolean = false,
    val logLevel: Int = 0,
    val defaultAction: String = "allow",
    val rules: List<String> = emptyList()
)

object VFSDebugUtil {

    private var useMockData: Boolean? = null

    private fun isModuleAvailable(): Boolean {
        if (useMockData != null) {
            return !useMockData!!
        }
        return runCatching {
            val sysfsExists = SuFile.open(VFS_SYSFS_PATH).exists()
            val debugfsExists = SuFile.open(VFS_DEBUGFS_PATH).exists()
            useMockData = !(sysfsExists || debugfsExists)
            !useMockData!!
        }.getOrDefault(false).also {
            useMockData = !it
        }
    }

    private fun readSysfsFile(path: String): String {
        if (!isModuleAvailable()) {
            return ""
        }
        return runCatching {
            val file = SuFile.open(path)
            if (file.exists() && file.canRead()) {
                file.readText()
            } else {
                ""
            }
        }.getOrDefault("")
    }

    private fun writeSysfsFile(path: String, content: String): Boolean {
        if (!isModuleAvailable()) {
            return false
        }
        return runCatching {
            val file = SuFile.open(path)
            if (file.exists() && file.canWrite()) {
                file.writeText(content)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    suspend fun getVFSStats(): VFSStats = withContext(Dispatchers.IO) {
        if (!isModuleAvailable()) {
            return@withContext getMockStats()
        }

        var openCount = 0L
        var readCount = 0L
        var writeCount = 0L
        var closeCount = 0L
        var deniedCount = 0L

        val statsPath = "$VFS_SYSFS_PATH/stats"
        val content = readSysfsFile(statsPath)
        
        content.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("open:") -> openCount = trimmed.substringAfter(":").trim().toLongOrNull() ?: 0L
                trimmed.startsWith("read:") -> readCount = trimmed.substringAfter(":").trim().toLongOrNull() ?: 0L
                trimmed.startsWith("write:") -> writeCount = trimmed.substringAfter(":").trim().toLongOrNull() ?: 0L
                trimmed.startsWith("close:") -> closeCount = trimmed.substringAfter(":").trim().toLongOrNull() ?: 0L
                trimmed.startsWith("denied:") -> deniedCount = trimmed.substringAfter(":").trim().toLongOrNull() ?: 0L
            }
        }

        VFSStats(
            openCount = openCount,
            readCount = readCount,
            writeCount = writeCount,
            closeCount = closeCount,
            deniedCount = deniedCount
        )
    }

    suspend fun getVFSPolicy(): VFSPolicy = withContext(Dispatchers.IO) {
        if (!isModuleAvailable()) {
            return@withContext getMockPolicy()
        }

        val enabledPath = "$VFS_SYSFS_PATH/enabled"
        val logLevelPath = "$VFS_SYSFS_PATH/log_level"
        val defaultActionPath = "$VFS_SYSFS_PATH/default_action"
        val rulesPath = "$VFS_SYSFS_PATH/rules"

        val enabled = readSysfsFile(enabledPath).trim() == "1"
        val logLevel = readSysfsFile(logLevelPath).trim().toIntOrNull() ?: 0
        val defaultAction = readSysfsFile(defaultActionPath).trim().ifEmpty { "allow" }
        val rules = readSysfsFile(rulesPath).lines().filter { it.isNotBlank() }

        VFSPolicy(
            enabled = enabled,
            logLevel = logLevel,
            defaultAction = defaultAction,
            rules = rules
        )
    }

    suspend fun setVFSPolicy(policy: VFSPolicy): Boolean = withContext(Dispatchers.IO) {
        if (!isModuleAvailable()) {
            return@withContext true
        }

        val enabledPath = "$VFS_SYSFS_PATH/enabled"
        val logLevelPath = "$VFS_SYSFS_PATH/log_level"
        val defaultActionPath = "$VFS_SYSFS_PATH/default_action"
        val rulesPath = "$VFS_SYSFS_PATH/rules"

        var success = true

        success = success && writeSysfsFile(enabledPath, if (policy.enabled) "1" else "0")
        success = success && writeSysfsFile(logLevelPath, policy.logLevel.toString())
        success = success && writeSysfsFile(defaultActionPath, policy.defaultAction)
        success = success && writeSysfsFile(rulesPath, policy.rules.joinToString("\n"))

        success
    }

    fun validatePolicy(policy: VFSPolicy): Pair<Boolean, String> {
        if (policy.logLevel !in 0..5) {
            return Pair(false, "日志级别必须在 0-5 之间")
        }
        if (policy.defaultAction !in listOf("allow", "deny")) {
            return Pair(false, "默认动作必须是 allow 或 deny")
        }
        policy.rules.forEachIndexed { index, rule ->
            val parts = rule.split(":")
            if (parts.size < 3) {
                return Pair(false, "规则 ${index + 1} 格式错误，应为: 动作:路径:模式")
            }
            if (parts[0] !in listOf("allow", "deny")) {
                return Pair(false, "规则 ${index + 1} 的动作必须是 allow 或 deny")
            }
        }
        return Pair(true, "")
    }

    suspend fun resetStats(): Boolean = withContext(Dispatchers.IO) {
        if (!isModuleAvailable()) {
            return@withContext true
        }
        writeSysfsFile("$VFS_SYSFS_PATH/stats_reset", "1")
    }

    private fun getMockStats(): VFSStats {
        return VFSStats(
            openCount = 1234,
            readCount = 5678,
            writeCount = 901,
            closeCount = 1230,
            deniedCount = 5
        )
    }

    private fun getMockPolicy(): VFSPolicy {
        return VFSPolicy(
            enabled = true,
            logLevel = 2,
            defaultAction = "allow",
            rules = listOf(
                "deny:/data/data/*/databases/:rw",
                "allow:/sdcard/:r",
                "deny:/system/:w"
            )
        )
    }

    fun forceMockMode(enabled: Boolean) {
        useMockData = enabled
    }
}
