package com.ztros.ztrosu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.io.SuFile
import com.ztros.ztrosu.ui.util.VFSEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "VFSDebugUtil"

// Kernel sysfs paths
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

enum class VFSBackend {
    KERNEL_SYSFS,    // Native kernel implementation
    KERNEL_DEBUGFS,  // Debugfs fallback
    MOCK             // No backend available (disconnected)
}

object VFSDebugUtil {

    private var backend: VFSBackend? = null

    /**
     * Detect which backend is available
     */
    fun detectBackend(): VFSBackend {
        if (backend != null) {
            return backend!!
        }

        // Check kernel sysfs
        if (SuFile.open(VFS_SYSFS_PATH).exists()) {
            backend = VFSBackend.KERNEL_SYSFS
            Log.i(TAG, "Using kernel sysfs backend")
            return backend!!
        }

        // Check kernel debugfs
        if (SuFile.open(VFS_DEBUGFS_PATH).exists()) {
            backend = VFSBackend.KERNEL_DEBUGFS
            Log.i(TAG, "Using kernel debugfs backend")
            return backend!!
        }

        // No backend available
        Log.w(TAG, "No VFS backend available")
        return null
    }

    /**
     * Check if any backend is available
     */
    fun isAvailable(): Boolean {
        return detectBackend() != null
    }


    private fun readFile(path: String): String {
        return runCatching {
            val file = SuFile.open(path)
            if (file.exists() && file.canRead()) {
                file.readText()
            } else {
                ""
            }
        }.getOrDefault("")
    }

    private fun writeFile(path: String, content: String): Boolean {
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

    /**
     * Get VFS statistics
     */
    suspend fun getVFSStats(): VFSStats? = withContext(Dispatchers.IO) {
        when (detectBackend()) {
            VFSBackend.KERNEL_SYSFS -> getKernelStats("$VFS_SYSFS_PATH/stats")
            VFSBackend.KERNEL_DEBUGFS -> getKernelStats("$VFS_DEBUGFS_PATH/stats")
            null -> null
        }
    }

    private fun getKernelStats(statsPath: String): VFSStats {
        var openCount = 0L
        var readCount = 0L
        var writeCount = 0L
        var closeCount = 0L
        var deniedCount = 0L

        val content = readFile(statsPath)

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

        return VFSStats(
            openCount = openCount,
            readCount = readCount,
            writeCount = writeCount,
            closeCount = closeCount,
            deniedCount = deniedCount
        )
    }

    /**
     * Get VFS policy
     */
    suspend fun getVFSPolicy(): VFSPolicy? = withContext(Dispatchers.IO) {
        when (detectBackend()) {
            VFSBackend.KERNEL_SYSFS -> getKernelPolicy(VFS_SYSFS_PATH)
            VFSBackend.KERNEL_DEBUGFS -> getKernelPolicy(VFS_DEBUGFS_PATH)
            null -> null
        }
    }

    private fun getKernelPolicy(basePath: String): VFSPolicy {
        val enabled = readFile("$basePath/enabled").trim() == "1"
        val logLevel = readFile("$basePath/log_level").trim().toIntOrNull() ?: 0
        val defaultAction = readFile("$basePath/default_action").trim().ifEmpty { "allow" }
        val rules = readFile("$basePath/rules").lines().filter { it.isNotBlank() }

        return VFSPolicy(
            enabled = enabled,
            logLevel = logLevel,
            defaultAction = defaultAction,
            rules = rules
        )
    }

    /**
     * Set VFS policy
     */
    suspend fun setVFSPolicy(policy: VFSPolicy): Boolean = withContext(Dispatchers.IO) {
        when (detectBackend()) {
            VFSBackend.KERNEL_SYSFS -> setKernelPolicy(VFS_SYSFS_PATH, policy)
            VFSBackend.KERNEL_DEBUGFS -> setKernelPolicy(VFS_DEBUGFS_PATH, policy)
            null -> false
        }
    }

    private suspend fun setKernelPolicy(basePath: String, policy: VFSPolicy): Boolean {
        var success = true

        success = success && writeFile("$basePath/enabled", if (policy.enabled) "1" else "0")
        success = success && writeFile("$basePath/log_level", policy.logLevel.toString())
        success = success && writeFile("$basePath/default_action", policy.defaultAction)
        
        // 使用增强的规则写入逻辑 (支持v1/v2兼容性)
        success = success && VFSKernelInterface.addRulesBatch(policy.rules)

        return success
    }

    /**
     * Validate policy
     */
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

    /**
     * Reset statistics
     */
    suspend fun resetStats(): Boolean = withContext(Dispatchers.IO) {
        when (detectBackend()) {
            VFSBackend.KERNEL_SYSFS -> writeFile("$VFS_SYSFS_PATH/stats_reset", "1")
            VFSBackend.KERNEL_DEBUGFS -> writeFile("$VFS_DEBUGFS_PATH/stats_reset", "1")
            null -> false
        }
    }

    // ==================== 实时事件流 ====================

    /**
     * 获取实时事件流
     * 通过VFSKernelInterface委托给VFSNetlinkListener
     * @param callback 事件回调函数
     */
    fun startEventStream(callback: (VFSEvent) -> Unit) {
        VFSKernelInterface.startEventListening(callback)
    }

    /**
     * 停止实时事件流
     */
    fun stopEventStream() {
        VFSKernelInterface.stopEventListening()
    }

}
