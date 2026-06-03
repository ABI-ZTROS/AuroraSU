package com.ztros.ztrosu.data.audit

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 安全审计日志条目
 *
 * 记录所有VFS文件操作事件，用于安全审计和异常检测
 */
@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["pid"]),
        Index(value = ["uid"]),
        Index(value = ["eventType"]),
        Index(value = ["path"]),
        Index(value = ["isAnomaly"])
    ]
)
data class AuditLogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 事件基本信息
    val timestamp: Long,           // 事件发生时间戳（毫秒）
    val eventType: Int,            // 事件类型 (OPEN/READ/WRITE/CLOSE/DENY)
    val eventTypeName: String,     // 事件类型名称（冗余存储便于查询）

    // 进程信息
    val pid: Int,                  // 进程PID
    val uid: Int,                  // 进程UID
    val packageName: String?,      // 应用包名（如果能解析到）

    // 文件路径
    val path: String,              // 访问的文件路径
    val pathHash: String?,         // 路径哈希（用于敏感路径检测）

    // 操作结果
    val result: Int,               // 0=allow, 1=deny
    val resultName: String,        // "ALLOW" or "DENY"

    // 审计标记
    val isAnomaly: Boolean = false,        // 是否标记为异常
    val anomalyType: String? = null,       // 异常类型（如 FREQUENT_ACCESS, SENSITIVE_PATH）
    val riskScore: Int = 0,                // 风险评分 (0-100)

    // 原始数据（用于调试）
    val rawData: String? = null,           // 原始事件数据JSON

    // 元数据
    val createdAt: Long = System.currentTimeMillis(),  // 记录创建时间
    val synced: Boolean = false            // 是否已同步到云端（预留）
) {
    companion object {
        // 事件类型常量（与 VFSNetlinkListener 保持一致）
        const val EVENT_OPEN = 1
        const val EVENT_READ = 2
        const val EVENT_WRITE = 3
        const val EVENT_CLOSE = 4
        const val EVENT_DENY = 5
        const val EVENT_HOOK_ADDED = 10
        const val EVENT_HOOK_REMOVED = 11
        const val EVENT_RULE_CHANGED = 12

        // 异常类型
        const val ANOMALY_FREQUENT_ACCESS = "FREQUENT_ACCESS"
        const val ANOMALY_SENSITIVE_PATH = "SENSITIVE_PATH"
        const val ANOMALY_UNUSUAL_PATTERN = "UNUSUAL_PATTERN"
        const val ANOMALY_HIGH_FREQUENCY = "HIGH_FREQUENCY"
    }

    /**
     * 获取事件类型的可读名称
     */
    fun getEventTypeDisplay(): String = when (eventType) {
        EVENT_OPEN -> "打开"
        EVENT_READ -> "读取"
        EVENT_WRITE -> "写入"
        EVENT_CLOSE -> "关闭"
        EVENT_DENY -> "拒绝"
        EVENT_HOOK_ADDED -> "添加Hook"
        EVENT_HOOK_REMOVED -> "移除Hook"
        EVENT_RULE_CHANGED -> "规则变更"
        else -> "未知($eventType)"
    }

    /**
     * 获取风险等级描述
     */
    fun getRiskLevel(): String = when {
        riskScore >= 80 -> "高危"
        riskScore >= 50 -> "中危"
        riskScore >= 20 -> "低危"
        else -> "正常"
    }

    /**
     * 检查是否是敏感路径访问
     */
    fun isSensitivePath(): Boolean {
        val sensitivePatterns = listOf(
            "/data/data/",
            "/data/system/",
            "/sdcard/Android/data/",
            "/proc/",
            "/sys/",
            "/system/",
            "/vendor/"
        )
        return sensitivePatterns.any { path.startsWith(it) }
    }
}
