package com.ztros.ztrosu.ui.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SecurityAudit"

/**
 * 系统安全审计数据模型
 */

/**
 * Shell 执行统计
 */
data class ShellExecStats(
    val totalExecCount: Long = 0,
    val scriptExecCount: Long = 0,
    val interactiveCount: Long = 0,
    val deniedCount: Long = 0,
    val lastInterpreter: String = "",
    val lastCaller: String = "",
    val lastTimestamp: Long = 0
)

/**
 * Shell 执行记录
 */
data class ShellExecRecord(
    val interpreter: String,
    val callerPid: Int,
    val callerUid: Int,
    val callerName: String,
    val scriptPath: String,
    val execType: String,  // "script" or "interactive"
    val timestamp: Long
)

/**
 * 分区保护状态
 */
data class PartitionStatus(
    val enabled: Boolean = false,
    val autoReject: Boolean = false,
    val alertOnly: Boolean = false,
    val checkInterval: Int = 300,
    val partitions: List<PartitionInfo> = emptyList()
)

/**
 * 单个分区信息
 */
data class PartitionInfo(
    val mountPoint: String,
    val isProtected: Boolean = true,
    val isModified: Boolean = false,
    val modificationCount: Long = 0
)

/**
 * 系统安全审计接口
 *
 * 通过 sysfs 与内核安全审计模块通信
 */
object SecurityAuditInterface {

    private val auditBase = "/sys/kernel/ztrosu/audit"

    // ==================== Shell 执行统计 ====================

    /**
     * 获取 Shell 执行统计
     */
    suspend fun getShellStats(): ShellExecStats? = withContext(Dispatchers.IO) {
        try {
            val content = File("$auditBase/shell_stats").readText()
            val map = mutableMapOf<String, String>()
            content.lines().filter { it.contains(":") }.forEach { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) map[parts[0].trim()] = parts[1].trim()
            }
            ShellExecStats(
                totalExecCount = map["total"]?.toLongOrNull() ?: 0,
                scriptExecCount = map["scripts"]?.toLongOrNull() ?: 0,
                interactiveCount = map["interactive"]?.toLongOrNull() ?: 0,
                deniedCount = map["denied"]?.toLongOrNull() ?: 0,
                lastInterpreter = map["last_interpreter"] ?: "",
                lastCaller = map["last_caller"] ?: "",
                lastTimestamp = map["last_timestamp"]?.toLongOrNull() ?: 0
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read shell stats", e)
            null
        }
    }

    /**
     * 获取最近的 Shell 执行记录
     */
    suspend fun getRecentShellExecs(): List<ShellExecRecord>? = withContext(Dispatchers.IO) {
        try {
            val content = File("$auditBase/shell_recent").readText()
            content.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                parseShellExecRecord(line)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read shell recent", e)
            null
        }
    }

    /**
     * 清空 Shell 执行记录
     */
    suspend fun clearShellHistory(): Boolean = withContext(Dispatchers.IO) {
        try {
            File("$auditBase/shell_clear").writeText("1")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear shell history", e)
            false
        }
    }

    // ==================== 分区保护 ====================

    /**
     * 获取分区保护状态
     */
    suspend fun getPartitionStatus(): PartitionStatus? = withContext(Dispatchers.IO) {
        try {
            val content = File("$auditBase/partition_status").readText()
            val lines = content.lines()
            val map = mutableMapOf<String, String>()
            val partitionLines = mutableListOf<String>()
            var inPartitions = false

            for (line in lines) {
                if (line == "---") {
                    inPartitions = true
                    continue
                }
                if (inPartitions) {
                    partitionLines.add(line)
                } else if (line.contains(":")) {
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) map[parts[0].trim()] = parts[1].trim()
                }
            }

            val partitions = partitionLines.mapNotNull { parsePartitionLine(it) }

            PartitionStatus(
                enabled = map["enabled"] == "1",
                autoReject = map["auto_reject"] == "1",
                alertOnly = map["alert_only"] == "1",
                checkInterval = map["check_interval"]?.toIntOrNull() ?: 300,
                partitions = partitions
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read partition status", e)
            null
        }
    }

    /**
     * 设置分区保护策略
     */
    suspend fun setPartitionPolicy(
        enabled: Boolean,
        autoReject: Boolean,
        alertOnly: Boolean,
        checkInterval: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val value = "${if (enabled) 1 else 0}:${if (autoReject) 1 else 0}:${if (alertOnly) 1 else 0}:$checkInterval"
            File("$auditBase/partition_policy").writeText(value)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set partition policy", e)
            false
        }
    }

    /**
     * 重置分区修改标记
     */
    suspend fun resetPartitionModification(): Boolean = withContext(Dispatchers.IO) {
        try {
            File("$auditBase/partition_reset").writeText("1")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset partition modification", e)
            false
        }
    }

    // ==================== 私有方法 ====================

    private fun parseShellExecRecord(line: String): ShellExecRecord? {
        val parts = line.split(":")
        if (parts.size < 7) return null
        return try {
            ShellExecRecord(
                interpreter = parts[0],
                callerPid = parts[1].toIntOrNull() ?: 0,
                callerUid = parts[2].toIntOrNull() ?: 0,
                callerName = parts[3],
                scriptPath = parts[4],
                execType = parts[5],
                timestamp = parts[6].toLongOrNull() ?: 0
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePartitionLine(line: String): PartitionInfo? {
        // 格式: /system: protected=1 modified=0 mods=0
        val mountPoint = line.substringBefore(":").trim()
        val isProtected = line.contains("protected=1")
        val isModified = line.contains("modified=1")
        val modsMatch = Regex("mods=(\\d+)").find(line)
        val modCount = modsMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0

        return if (mountPoint.isNotBlank()) {
            PartitionInfo(mountPoint, isProtected, isModified, modCount)
        } else null
    }
}
