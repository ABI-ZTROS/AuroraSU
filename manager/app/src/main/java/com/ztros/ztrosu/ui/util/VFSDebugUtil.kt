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
     * Uses java.io.File first (sysfs is world-readable), falls back to SuFile for restricted paths.
     */
    fun detectBackend(): VFSBackend? {
        if (backend != null) {
            return backend!!
        }

        // Check kernel sysfs using java.io.File first
        // sysfs directories are typically world-readable (0755), files are 0444/0644
        // Note: canRead() may return false in Android sandbox even if file is readable
        val sysfsFile = File(VFS_SYSFS_PATH)
        Log.d(TAG, "detectBackend: checking $VFS_SYSFS_PATH, exists=${sysfsFile.exists()}, canRead=${sysfsFile.canRead()}")
        if (sysfsFile.exists()) {
            backend = VFSBackend.KERNEL_SYSFS
            Log.i(TAG, "Using kernel sysfs backend (File)")
            return backend!!
        }

        // Fallback: try SuFile (for paths that require root)
        try {
            val suSysfs = SuFile.open(VFS_SYSFS_PATH)
            Log.d(TAG, "detectBackend: SuFile check $VFS_SYSFS_PATH, exists=${suSysfs.exists()}")
            if (suSysfs.exists()) {
                backend = VFSBackend.KERNEL_SYSFS
                Log.i(TAG, "Using kernel sysfs backend (SuFile)")
                return backend!!
            }
        } catch (e: Exception) {
            Log.w(TAG, "SuFile sysfs check failed: ${e.message}")
        }

        // Check kernel debugfs
        val debugfsFile = File(VFS_DEBUGFS_PATH)
        if (debugfsFile.exists() && debugfsFile.canRead()) {
            backend = VFSBackend.KERNEL_DEBUGFS
            Log.i(TAG, "Using kernel debugfs backend")
            return backend!!
        }

        try {
            val suDebugfs = SuFile.open(VFS_DEBUGFS_PATH)
            if (suDebugfs.exists()) {
                backend = VFSBackend.KERNEL_DEBUGFS
                Log.i(TAG, "Using kernel debugfs backend (SuFile)")
                return backend!!
            }
        } catch (e: Exception) {
            Log.w(TAG, "SuFile debugfs check failed: ${e.message}")
        }

        // No backend available
        Log.w(TAG, "No VFS backend available (checked File and SuFile for both sysfs and debugfs)")
        return null
    }

    /**
     * Check if any backend is available
     */
    fun isAvailable(): Boolean {
        return detectBackend() != null
    }

    /**
     * Reset the cached backend detection, forcing re-detection on next call.
     */
    fun resetBackend() {
        backend = null
    }


    /**
     * 读取文件 - 委托给 VFSCommEngine
     */
    private suspend fun readFile(path: String): String {
        return VFSCommEngine.read(path)
    }

    /**
     * 写入文件 - 委托给 VFSCommEngine
     */
    private suspend fun writeFile(path: String, content: String): Boolean {
        return VFSCommEngine.write(path, content)
    }

    /**
     * Get VFS statistics
     * 委托给 VFSCommEngine 处理
     */
    suspend fun getVFSStats(): VFSStats? = withContext(Dispatchers.IO) {
        VFSCommEngine.getStats()
    }

    /**
     * Get VFS policy
     * 委托给 VFSCommEngine 处理
     */
    suspend fun getVFSPolicy(): VFSPolicy? = withContext(Dispatchers.IO) {
        VFSCommEngine.getPolicy()
    }

    /**
     * Set VFS policy
     * 委托给 VFSCommEngine 处理
     */
    suspend fun setVFSPolicy(policy: VFSPolicy): Boolean = withContext(Dispatchers.IO) {
        VFSCommEngine.setPolicy(policy)
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
     * 委托给 VFSCommEngine 处理
     */
    suspend fun resetStats(): Boolean = withContext(Dispatchers.IO) {
        VFSCommEngine.resetStats()
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
