package com.ztros.ztrosu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "VFSCommEngine"

private const val VFS_SYSFS_PATH = "/sys/kernel/ztrosu/vfs"
private const val VFS_DEBUGFS_PATH = "/sys/kernel/debug/ztrosu/vfs"
private const val IOCTL_DEVICE_PATH = "/dev/aurora_vfs_ioctl"

/**
 * VFS Unified Communication Engine
 *
 * 提供多通道通信能力，自动检测并选择最佳通信通道。
 * 解决 Android sandbox 中 File.canRead() 返回 false 导致 sysfs 读取失败的问题。
 *
 * 通道优先级（当前实现）:
 * 1. SHELL  - 最可靠，通过 root shell 执行 cat/echo
 * 2. PIPE   - 二进制协议管道 (/dev/aurora_vfs)
 * 3. SYSFS  - 直接文件访问（在 sandbox 中可能失败）
 * 4. NONE   - 无可用通道
 *
 * 未来扩展:
 * - IOCTL 通道（需要 JNI 包装器）
 */
object VFSCommEngine {

    enum class Channel {
        IOCTL,      // Primary: direct ioctl to /dev/aurora_vfs_ioctl
        SHELL,      // Fallback 1: root shell commands (cat/echo)
        PIPE,       // Fallback 2: binary pipe protocol
        SYSFS,      // Fallback 3: direct sysfs (may fail in sandbox)
        NONE        // No channel available
    }

    /**
     * Hook target type (for ioctl/pipe compatibility)
     */
    enum class HookType {
        PID, PACKAGE
    }

    /**
     * Hook target data structure
     */
    data class HookTarget(
        val type: HookType,
        val identifier: String,
        val uid: Int,
        val mode: VFSKernelInterface.HookMode,
        val enabled: Boolean = true
    )

    /**
     * 当前缓存的最佳通信通道
     */
    @Volatile
    private var cachedChannel: Channel? = null

    /**
     * 通道检测锁，防止并发检测
     */
    private val detectLock = Object()

    // ==================== 通道检测 ====================

    /**
     * 重置缓存的通道检测结果，强制下次重新检测
     */
    fun resetChannelCache() {
        synchronized(detectLock) {
            cachedChannel = null
            Log.i(TAG, "Channel cache reset, will re-detect on next operation")
        }
    }

    /**
     * 自动检测最佳通信通道
     *
     * 检测顺序: SHELL -> PIPE -> SYSFS -> NONE
     * 每个通道通过实际读写测试来验证可用性
     */
    suspend fun detectChannel(): Channel = withContext(Dispatchers.IO) {
        synchronized(detectLock) {
            cachedChannel?.let {
                Log.d(TAG, "Using cached channel: $it")
                return@withContext it
            }

            val channel = try {
                Log.i(TAG, "Starting channel detection...")

                // 1. 测试 SHELL 通道（最可靠）
                if (testShellChannel()) {
                    Log.i(TAG, "Detected SHELL channel as best communication channel")
                    Channel.SHELL
                }
                // 2. 测试 PIPE 通道
                else if (testPipeChannel()) {
                    Log.i(TAG, "Detected PIPE channel as best communication channel")
                    Channel.PIPE
                }
                // 3. 测试 SYSFS 直接访问
                else if (testSysfsChannel()) {
                    Log.i(TAG, "Detected SYSFS channel as best communication channel")
                    Channel.SYSFS
                }
                // 4. 无可用通道
                else {
                    Log.w(TAG, "No kernel communication channel available")
                    Channel.NONE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during channel detection", e)
                Channel.NONE
            }

            cachedChannel = channel
            channel
        }
    }

    /**
     * 测试 SHELL 通道可用性
     * 通过 root shell 读取 sysfs version 文件验证
     */
    private fun testShellChannel(): Boolean {
        return try {
            val testPath = "$VFS_SYSFS_PATH/version"
            val result = Shell.cmd("cat '$testPath' 2>/dev/null").exec()
            val success = result.isSuccess && result.out.isNotEmpty()
            Log.d(TAG, "SHELL channel test: path=$testPath, success=$success")
            success
        } catch (e: Exception) {
            Log.w(TAG, "SHELL channel test failed", e)
            false
        }
    }

    /**
     * 测试 PIPE 通道可用性
     */
    private fun testPipeChannel(): Boolean {
        return try {
            val available = VFSPipeComm.isAvailable()
            Log.d(TAG, "PIPE channel test: available=$available")
            available
        } catch (e: Exception) {
            Log.w(TAG, "PIPE channel test failed", e)
            false
        }
    }

    /**
     * 测试 SYSFS 直接访问通道
     * 注意: 在 Android sandbox 中可能失败
     */
    private fun testSysfsChannel(): Boolean {
        return try {
            // 先检查 sysfs 目录是否存在
            val sysfsDir = SuFile.open(VFS_SYSFS_PATH)
            if (!sysfsDir.exists()) {
                Log.d(TAG, "SYSFS channel test: sysfs directory not found")
                return false
            }

            // 尝试直接读取 version 文件
            val versionFile = File("$VFS_SYSFS_PATH/version")
            val content = if (versionFile.exists()) {
                runCatching { versionFile.readText() }.getOrNull()
            } else null

            val success = !content.isNullOrBlank()
            Log.d(TAG, "SYSFS channel test: success=$success, content='${content?.trim() ?: "null"}'")
            success
        } catch (e: Exception) {
            Log.w(TAG, "SYSFS channel test failed", e)
            false
        }
    }

    // ==================== 统一读写接口 ====================

    /**
     * 统一读取接口
     *
     * 按通道优先级尝试读取，自动回退:
     * SHELL -> PIPE -> SYSFS
     *
     * @param path 要读取的文件路径
     * @return 文件内容，失败返回空字符串
     */
    suspend fun read(path: String): String = withContext(Dispatchers.IO) {
        val channel = detectChannel()
        Log.d(TAG, "read: channel=$channel, path=$path")

        when (channel) {
            Channel.SHELL -> readViaShell(path)
            Channel.PIPE -> readViaPipe(path)
            Channel.SYSFS -> readViaSysfs(path)
            Channel.IOCTL -> {
                Log.w(TAG, "IOCTL channel not yet implemented, falling back")
                readFallback(path)
            }
            Channel.NONE -> {
                Log.w(TAG, "No channel available for read: $path")
                ""
            }
        }
    }

    /**
     * 统一写入接口
     *
     * 按通道优先级尝试写入，自动回退:
     * SHELL -> PIPE -> SYSFS
     *
     * @param path 要写入的文件路径
     * @param content 要写入的内容
     * @return 是否写入成功
     */
    suspend fun write(path: String, content: String): Boolean = withContext(Dispatchers.IO) {
        val channel = detectChannel()
        Log.d(TAG, "write: channel=$channel, path=$path, contentLength=${content.length}")

        when (channel) {
            Channel.SHELL -> writeViaShell(path, content)
            Channel.PIPE -> writeViaPipe(path, content)
            Channel.SYSFS -> writeViaSysfs(path, content)
            Channel.IOCTL -> {
                Log.w(TAG, "IOCTL channel not yet implemented, falling back")
                writeFallback(path, content)
            }
            Channel.NONE -> {
                Log.w(TAG, "No channel available for write: $path")
                false
            }
        }
    }

    // ==================== SHELL 通道实现 ====================

    /**
     * 通过 root shell 读取文件内容
     */
    private fun readViaShell(path: String): String {
        return try {
            val result = Shell.cmd("cat '$path' 2>/dev/null").exec()
            if (result.isSuccess) {
                val content = result.out.joinToString("\n")
                Log.d(TAG, "SHELL read success: path=$path, length=${content.length}")
                content
            } else {
                Log.w(TAG, "SHELL read failed: path=$path, err=${result.err.joinToString()}")
                // 尝试回退
                readFallback(path)
            }
        } catch (e: Exception) {
            Log.e(TAG, "SHELL read exception: path=$path", e)
            readFallback(path)
        }
    }

    /**
     * 通过 root shell 写入文件内容
     */
    private fun writeViaShell(path: String, content: String): Boolean {
        return try {
            // 使用 echo 写入，处理特殊字符
            val escapedContent = content.replace("'", "'\\''")
            val result = Shell.cmd("echo '$escapedContent' > '$path' 2>/dev/null").exec()
            val success = result.isSuccess
            if (success) {
                Log.d(TAG, "SHELL write success: path=$path")
            } else {
                Log.w(TAG, "SHELL write failed: path=$path, err=${result.err.joinToString()}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "SHELL write exception: path=$path", e)
            false
        }
    }

    // ==================== PIPE 通道实现 ====================

    /**
     * 通过 PIPE 协议读取
     * 注意: 当前 PIPE 实现不支持读取响应，回退到其他通道
     */
    private fun readViaPipe(path: String): String {
        Log.d(TAG, "PIPE read: falling back to sysfs for path=$path")
        return readViaSysfs(path)
    }

    /**
     * 通过 PIPE 协议写入
     * 将 sysfs 路径映射为对应的 PIPE 命令
     */
    private fun writeViaPipe(path: String, content: String): Boolean {
        return try {
            // 解析路径，映射到对应的 PIPE 命令
            val relativePath = path.removePrefix("$VFS_SYSFS_PATH/")
            val success = when (relativePath) {
                "rules" -> {
                    val rules = content.lines().filter { it.isNotBlank() }
                    VFSPipeComm.setRules(rules)
                }
                "rules_clear" -> {
                    VFSPipeComm.clearRules()
                }
                "enabled" -> {
                    // 需要读取当前策略再修改，PIPE 不支持读取，回退到 shell
                    writeViaShell(path, content)
                }
                "log_level" -> {
                    writeViaShell(path, content)
                }
                "default_action" -> {
                    writeViaShell(path, content)
                }
                "stats_reset" -> {
                    VFSPipeComm.resetStats()
                }
                "hook_targets" -> {
                    // hook_targets 通过 PIPE 的 addHook/removeHook 处理
                    writeViaShell(path, content)
                }
                else -> {
                    Log.w(TAG, "PIPE write: unmapped path=$path, falling back to shell")
                    writeViaShell(path, content)
                }
            }

            if (success) {
                Log.d(TAG, "PIPE write success: path=$path")
            } else {
                Log.w(TAG, "PIPE write failed: path=$path, falling back")
                writeFallback(path, content)
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "PIPE write exception: path=$path", e)
            writeFallback(path, content)
        }
    }

    // ==================== SYSFS 通道实现 ====================

    /**
     * 直接通过 sysfs 读取文件
     * 注意: 在 Android sandbox 中可能因 canRead() 返回 false 而失败
     */
    private fun readViaSysfs(path: String): String {
        return try {
            // 尝试 java.io.File 直接读取
            val file = File(path)
            if (file.exists()) {
                val content = runCatching { file.readText() }.getOrNull()
                if (!content.isNullOrBlank()) {
                    Log.d(TAG, "SYSFS read success (File): path=$path, length=${content.length}")
                    return content
                }
            }

            // 回退到 SuFile
            val suFile = SuFile.open(path)
            if (suFile.exists()) {
                val content = runCatching { suFile.readText() }.getOrNull()
                if (!content.isNullOrBlank()) {
                    Log.d(TAG, "SYSFS read success (SuFile): path=$path, length=${content.length}")
                    return content
                }
            }

            Log.w(TAG, "SYSFS read failed: path=$path")
            ""
        } catch (e: Exception) {
            Log.e(TAG, "SYSFS read exception: path=$path", e)
            ""
        }
    }

    /**
     * 直接通过 sysfs 写入文件
     */
    private fun writeViaSysfs(path: String, content: String): Boolean {
        return try {
            // 尝试 java.io.File 直接写入
            val file = File(path)
            if (file.exists()) {
                val ok = runCatching {
                    file.writeText(content)
                    true
                }.getOrDefault(false)
                if (ok) {
                    Log.d(TAG, "SYSFS write success (File): path=$path")
                    return true
                }
            }

            // 回退到 SuFile
            val suFile = SuFile.open(path)
            if (suFile.exists()) {
                val ok = runCatching {
                    suFile.writeText(content)
                    true
                }.getOrDefault(false)
                if (ok) {
                    Log.d(TAG, "SYSFS write success (SuFile): path=$path")
                    return true
                }
            }

            Log.w(TAG, "SYSFS write failed: path=$path")
            false
        } catch (e: Exception) {
            Log.e(TAG, "SYSFS write exception: path=$path", e)
            false
        }
    }

    // ==================== 回退机制 ====================

    /**
     * 读取回退: 尝试所有可能的读取方式
     */
    private fun readFallback(path: String): String {
        Log.d(TAG, "readFallback: trying all methods for path=$path")

        // 尝试 shell
        try {
            val result = Shell.cmd("cat '$path' 2>/dev/null").exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                val content = result.out.joinToString("\n")
                Log.d(TAG, "readFallback success via shell: path=$path")
                return content
            }
        } catch (e: Exception) {
            Log.w(TAG, "readFallback shell failed", e)
        }

        // 尝试 SuFile
        try {
            val suFile = SuFile.open(path)
            if (suFile.exists()) {
                val content = runCatching { suFile.readText() }.getOrNull()
                if (!content.isNullOrBlank()) {
                    Log.d(TAG, "readFallback success via SuFile: path=$path")
                    return content
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "readFallback SuFile failed", e)
        }

        Log.w(TAG, "readFallback: all methods failed for path=$path")
        return ""
    }

    /**
     * 写入回退: 尝试所有可能的写入方式
     */
    private fun writeFallback(path: String, content: String): Boolean {
        Log.d(TAG, "writeFallback: trying all methods for path=$path")

        // 尝试 shell
        try {
            val escapedContent = content.replace("'", "'\\''")
            val result = Shell.cmd("echo '$escapedContent' > '$path' 2>/dev/null").exec()
            if (result.isSuccess) {
                Log.d(TAG, "writeFallback success via shell: path=$path")
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "writeFallback shell failed", e)
        }

        // 尝试 SuFile
        try {
            val suFile = SuFile.open(path)
            if (suFile.exists()) {
                val ok = runCatching {
                    suFile.writeText(content)
                    true
                }.getOrDefault(false)
                if (ok) {
                    Log.d(TAG, "writeFallback success via SuFile: path=$path")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "writeFallback SuFile failed", e)
        }

        Log.w(TAG, "writeFallback: all methods failed for path=$path")
        false
    }

    // ==================== IOCTL 包装器 (预留) ====================

    /**
     * 获取模块版本
     * 通过统一读取接口读取 version 文件
     */
    suspend fun getVersion(): Int? = withContext(Dispatchers.IO) {
        val content = read("$VFS_SYSFS_PATH/version").trim()
        Log.d(TAG, "getVersion: content='$content'")
        val version = content.toIntOrNull()
        if (version == null) {
            Log.w(TAG, "getVersion: failed to parse version from '$content'")
        }
        version
    }

    /**
     * 获取 VFS 统计信息
     */
    suspend fun getStats(): VFSStats? = withContext(Dispatchers.IO) {
        val content = read("$VFS_SYSFS_PATH/stats")
        if (content.isBlank()) {
            Log.w(TAG, "getStats: empty content")
            return@withContext null
        }

        var openCount = 0L
        var readCount = 0L
        var writeCount = 0L
        var closeCount = 0L
        var deniedCount = 0L

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

    /**
     * 获取 VFS 策略
     */
    suspend fun getPolicy(): VFSPolicy? = withContext(Dispatchers.IO) {
        val enabled = read("$VFS_SYSFS_PATH/enabled").trim() == "1"
        val logLevel = read("$VFS_SYSFS_PATH/log_level").trim().toIntOrNull() ?: 0
        val defaultAction = read("$VFS_SYSFS_PATH/default_action").trim().ifEmpty { "allow" }
        val rules = read("$VFS_SYSFS_PATH/rules").lines().filter { it.isNotBlank() }

        VFSPolicy(
            enabled = enabled,
            logLevel = logLevel,
            defaultAction = defaultAction,
            rules = rules
        )
    }

    /**
     * 设置 VFS 策略
     */
    suspend fun setPolicy(policy: VFSPolicy): Boolean = withContext(Dispatchers.IO) {
        var success = true

        success = success && write("$VFS_SYSFS_PATH/enabled", if (policy.enabled) "1" else "0")
        success = success && write("$VFS_SYSFS_PATH/log_level", policy.logLevel.toString())
        success = success && write("$VFS_SYSFS_PATH/default_action", policy.defaultAction)

        // 规则通过 VFSKernelInterface 的 addRulesBatch 处理以兼容 v1/v2
        success = success && VFSKernelInterface.addRulesBatch(policy.rules)

        Log.d(TAG, "setPolicy: success=$success")
        success
    }

    /**
     * 获取规则列表
     */
    suspend fun getRules(): List<String> = withContext(Dispatchers.IO) {
        read("$VFS_SYSFS_PATH/rules").lines().filter { it.isNotBlank() }
    }

    /**
     * 设置规则列表
     */
    suspend fun setRules(rules: List<String>): Boolean = withContext(Dispatchers.IO) {
        val content = rules.joinToString("\n")
        write("$VFS_SYSFS_PATH/rules", content)
    }

    /**
     * 清空所有规则
     */
    suspend fun clearRules(): Boolean = withContext(Dispatchers.IO) {
        write("$VFS_SYSFS_PATH/rules_clear", "1")
    }

    /**
     * 获取 Hook 目标列表
     */
    suspend fun getHooks(): List<HookTarget> = withContext(Dispatchers.IO) {
        val content = read("$VFS_SYSFS_PATH/hook_list")
        if (content.isBlank()) {
            return@withContext emptyList()
        }

        val targets = mutableListOf<HookTarget>()
        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach

            val parts = trimmed.split(":")
            if (parts.size >= 5) {
                try {
                    val type = HookType.valueOf(parts[0].uppercase())
                    val identifier = parts[1]
                    val uid = parts[2].toIntOrNull() ?: 0
                    val mode = VFSKernelInterface.HookMode.fromString(parts[3])
                    val enabled = parts[4] == "1" || parts[4].lowercase() == "yes"
                    targets.add(HookTarget(type, identifier, uid, mode, enabled))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse hook target: $line", e)
                }
            }
        }
        targets
    }

    /**
     * 添加 Hook 目标
     */
    suspend fun addHook(target: HookTarget): Boolean = withContext(Dispatchers.IO) {
        val command = "add:${target.type.name}:${target.identifier}:${target.uid}:${target.mode.name}"
        write("$VFS_SYSFS_PATH/hook_targets", command)
    }

    /**
     * 移除 Hook 目标
     */
    suspend fun removeHook(type: HookType, identifier: String): Boolean = withContext(Dispatchers.IO) {
        val command = "remove:${type.name}:$identifier"
        write("$VFS_SYSFS_PATH/hook_targets", command)
    }

    /**
     * 重置统计信息
     */
    suspend fun resetStats(): Boolean = withContext(Dispatchers.IO) {
        write("$VFS_SYSFS_PATH/stats_reset", "1")
    }

    // ==================== 调试工具 ====================

    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("VFSCommEngine Debug Info:")
            appendLine("  Cached Channel: ${cachedChannel ?: "not detected"}")
            appendLine("  SYSFS Path: $VFS_SYSFS_PATH")
            appendLine("  DEBUGFS Path: $VFS_DEBUGFS_PATH")
            appendLine("  IOCTL Device: $IOCTL_DEVICE_PATH")
            appendLine()
            appendLine("  Channel Status:")
            appendLine("    SHELL: ${testShellChannel()}")
            appendLine("    PIPE: ${VFSPipeComm.isAvailable()}")
            appendLine("    SYSFS: ${File(VFS_SYSFS_PATH).exists()}")
        }
    }
}
