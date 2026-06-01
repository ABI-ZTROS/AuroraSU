package com.ztros.ztrosu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "VFSKernelInterface"

private const val VFS_SYSFS_PATH = "/sys/kernel/ztrosu/vfs"

/**
 * VFS Kernel Interface - 与内核模块通讯的完整接口
 * 对应文档: docs/VFS_KERNEL_MODULE_SPEC.md v2.0
 *
 * 通讯通道优先级: PIPE > SYSFS > USERSPACE
 */
object VFSKernelInterface {

    // ==================== 通讯通道管理 ====================

    /**
     * 通讯通道枚举 - 定义与内核通讯的优先级
     */
    enum class CommChannel {
        PIPE,       // 优先：一次性pipe（安全、高效）
        SYSFS,      // 备用：sysfs写入（兼容性好）
        USERSPACE   // 兜底：Shell命令（最低保障）
    }

    /**
     * 当前检测到的最佳通讯通道（缓存）
     */
    @Volatile
    private var cachedChannel: CommChannel? = null

    /**
     * 自动检测最佳通讯通道
     * 优先级: PIPE > SYSFS > USERSPACE
     */
    suspend fun detectBestChannel(): CommChannel = withContext(Dispatchers.IO) {
        cachedChannel?.let { return@withContext it }

        val channel = try {
            // 1. 尝试创建pipe，如果成功则使用PIPE
            if (VFSPipeComm.isAvailable()) {
                Log.i(TAG, "Detected PIPE channel as best communication channel")
                CommChannel.PIPE
            } else if (SuFile.open(VFS_SYSFS_PATH).exists()) {
                // 2. 如果pipe失败，使用SYSFS
                Log.i(TAG, "Detected SYSFS channel as best communication channel")
                CommChannel.SYSFS
            } else {
                // 3. 如果sysfs不可用，使用USERSPACE
                Log.i(TAG, "Falling back to USERSPACE channel")
                CommChannel.USERSPACE
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error detecting best channel, falling back to SYSFS", e)
            CommChannel.SYSFS
        }

        cachedChannel = channel
        channel
    }

    /**
     * 重置通道缓存，强制下次重新检测
     */
    fun resetChannelCache() {
        cachedChannel = null
    }

    // ==================== 事件监听集成 ====================

    /**
     * 启动netlink事件监听
     * @param callback 事件回调函数
     */
    fun startEventListening(callback: (VFSEvent) -> Unit) {
        try {
            VFSNetlinkListener.startListening(callback)
            Log.i(TAG, "Started netlink event listening")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start netlink event listening, silently degraded", e)
        }
    }

    /**
     * 停止netlink事件监听
     */
    fun stopEventListening() {
        try {
            VFSNetlinkListener.stopListening()
            Log.i(TAG, "Stopped netlink event listening")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop netlink event listening", e)
        }
    }

    // ==================== 模块版本查询 ====================

    /**
     * 获取模块接口版本
     * @return 版本号 (1或2)，失败返回null
     */
    suspend fun getVersion(): Int? = withContext(Dispatchers.IO) {
        val content = readFile("$VFS_SYSFS_PATH/version")
        content.trim().toIntOrNull()
    }

    /**
     * 检查内核模块是否支持v2接口
     */
    suspend fun isV2Supported(): Boolean {
        return getVersion() == 2
    }

    // ==================== Hook目标管理 (v2接口) ====================

    /**
     * Hook模式枚举
     */
    enum class HookMode(val value: Int) {
        MONITOR_ONLY(0),
        INTERCEPT_READ(1),
        INTERCEPT_WRITE(2),
        INTERCEPT_ALL(3);

        companion object {
            fun fromValue(value: Int): HookMode = values().find { it.value == value } ?: MONITOR_ONLY
            fun fromString(str: String): HookMode = when (str.uppercase()) {
                "MONITOR_ONLY" -> MONITOR_ONLY
                "INTERCEPT_READ" -> INTERCEPT_READ
                "INTERCEPT_WRITE" -> INTERCEPT_WRITE
                "INTERCEPT_ALL" -> INTERCEPT_ALL
                else -> MONITOR_ONLY
            }
        }
    }

    /**
     * Hook类型枚举
     */
    enum class HookType {
        PID, PACKAGE
    }

    /**
     * Hook目标数据结构
     */
    data class HookTarget(
        val type: HookType,
        val identifier: String,  // PID数字或包名
        val uid: Int,
        val mode: HookMode,
        val enabled: Boolean = true
    )

    /**
     * 添加Hook目标
     * 协议: add:<type>:<identifier>:<uid>:<mode>
     * 通讯通道优先级: PIPE > SYSFS > USERSPACE
     */
    suspend fun addHookTarget(target: HookTarget): Boolean = withContext(Dispatchers.IO) {
        val channel = detectBestChannel()
        when (channel) {
            CommChannel.PIPE -> {
                VFSPipeComm.addHook(
                    type = if (target.type == HookType.PID) 0 else 1,
                    identifier = target.identifier,
                    uid = target.uid,
                    mode = target.mode.value
                ).also { success ->
                    if (!success) {
                        Log.w(TAG, "PIPE addHook failed, falling back to SYSFS")
                        cachedChannel = null
                        // Fallback到SYSFS
                        val typeStr = target.type.name
                        val modeStr = target.mode.name
                        val command = "add:${typeStr}:${target.identifier}:${target.uid}:${modeStr}"
                        writeFile("$VFS_SYSFS_PATH/hook_targets", command)
                    } else {
                        Log.i(TAG, "Added hook target via PIPE: $target")
                    }
                }
            }
            CommChannel.SYSFS -> {
                val typeStr = target.type.name
                val modeStr = target.mode.name
                val command = "add:${typeStr}:${target.identifier}:${target.uid}:${modeStr}"
                writeFile("$VFS_SYSFS_PATH/hook_targets", command)
            }
            CommChannel.USERSPACE -> {
                val typeStr = target.type.name
                val modeStr = target.mode.name
                val command = "add:${typeStr}:${target.identifier}:${target.uid}:${modeStr}"
                val result = Shell.cmd("echo '$command' > $VFS_SYSFS_PATH/hook_targets").exec()
                result.isSuccess
            }
        }
    }

    /**
     * 移除Hook目标
     * 协议: remove:<type>:<identifier>
     */
    suspend fun removeHookTarget(type: HookType, identifier: String): Boolean = withContext(Dispatchers.IO) {
        val typeStr = type.name
        val command = "remove:${typeStr}:${identifier}"
        writeFile("$VFS_SYSFS_PATH/hook_targets", command)
    }

    /**
     * 获取当前Hook目标列表
     * 格式: type:identifier:uid:mode:enabled
     */
    suspend fun getHookList(): List<HookTarget> = withContext(Dispatchers.IO) {
        val content = readFile("$VFS_SYSFS_PATH/hook_list")
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
                    val mode = HookMode.fromString(parts[3])
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
     * 批量添加Hook目标
     */
    suspend fun batchAddHookTargets(targets: List<HookTarget>): Boolean = withContext(Dispatchers.IO) {
        var success = true
        targets.forEach { target ->
            if (!addHookTarget(target)) {
                success = false
                Log.e(TAG, "Failed to add hook target: $target")
            }
        }
        success
    }

    /**
     * 清空所有Hook目标
     */
    suspend fun clearAllHooks(): Boolean = withContext(Dispatchers.IO) {
        // 获取所有目标并逐个移除
        val targets = getHookList()
        var success = true
        targets.forEach { target ->
            if (!removeHookTarget(target.type, target.identifier)) {
                success = false
            }
        }
        success
    }

    // ==================== 增强规则管理 ====================

    /**
     * 清空所有规则 (使用rules_clear接口)
     */
    suspend fun clearRules(): Boolean = withContext(Dispatchers.IO) {
        writeFile("$VFS_SYSFS_PATH/rules_clear", "1")
    }

    /**
     * 批量添加规则 (逐行写入，兼容v1和v2)
     */
    suspend fun addRulesBatch(rules: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (rules.isEmpty()) return@withContext true

        // 检查是否支持v2接口
        val isV2 = isV2Supported()

        if (isV2) {
            // v2: 支持多行批量写入
            val content = rules.joinToString("\n")
            writeFile("$VFS_SYSFS_PATH/rules", content)
        } else {
            // v1: 逐条写入 (旧内核兼容性)
            var success = true
            rules.forEach { rule ->
                if (!appendFile("$VFS_SYSFS_PATH/rules", rule)) {
                    success = false
                }
            }
            success
        }
    }

    // ==================== 工具函数 ====================

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

    private fun appendFile(path: String, content: String): Boolean {
        return runCatching {
            val file = SuFile.open(path)
            if (file.exists() && file.canWrite()) {
                val current = file.readText()
                file.writeText(if (current.isBlank()) content else "$current\n$content")
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    // ==================== 高级接口 ====================

    /**
     * 获取完整的模块状态
     */
    suspend fun getModuleStatus(): ModuleStatus = withContext(Dispatchers.IO) {
        val version = getVersion() ?: 0
        val stats = VFSDebugUtil.getVFSStats()
        val policy = VFSDebugUtil.getVFSPolicy()
        val hooks = if (version >= 2) getHookList() else emptyList()

        ModuleStatus(
            version = version,
            stats = stats,
            policy = policy,
            hooks = hooks,
            isV2 = version >= 2
        )
    }

    data class ModuleStatus(
        val version: Int,
        val stats: VFSStats,
        val policy: VFSPolicy,
        val hooks: List<HookTarget>,
        val isV2: Boolean
    )

    /**
     * 应用完整配置 (策略 + 规则 + Hooks)
     */
    suspend fun applyFullConfig(
        policy: VFSPolicy,
        hooks: List<HookTarget>? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var success = true

        // 1. 应用策略
        success = success && VFSDebugUtil.setVFSPolicy(policy)

        // 2. 应用Hooks (如果支持v2)
        if (success && hooks != null && isV2Supported()) {
            // 先清空现有hooks
            clearAllHooks()
            // 批量添加新hooks
            success = success && batchAddHookTargets(hooks)
        }

        success
    }
}
