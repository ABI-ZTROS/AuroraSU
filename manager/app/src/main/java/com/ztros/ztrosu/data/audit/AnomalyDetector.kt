package com.ztros.ztrosu.data.audit

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AnomalyDetector"

/**
 * 异常行为检测引擎
 *
 * 基于规则和行为分析检测可疑的文件访问模式
 */
object AnomalyDetector {

    // ==================== 检测配置 ====================

    /** 高频访问阈值：同一PID在1分钟内访问次数超过此值视为异常 */
    private const val HIGH_FREQUENCY_THRESHOLD = 100

    /** 高频检测时间窗口（毫秒） */
    private const val HIGH_FREQUENCY_WINDOW_MS = 60_000L

    /** 敏感路径列表 */
    private val SENSITIVE_PATHS = listOf(
        "/data/data/",
        "/data/system/",
        "/data/misc/",
        "/sdcard/Android/data/",
        "/proc/self/",
        "/proc/meminfo",
        "/sys/class/",
        "/system/etc/",
        "/vendor/etc/"
    )

    /** 敏感文件扩展名 */
    private val SENSITIVE_EXTENSIONS = listOf(
        ".db", ".sql", ".sqlite", ".key", ".pem",
        ".password", ".credential", ".token"
    )

    /** 异常模式：短时间内访问多个不同敏感路径 */
    private const val PATH_DIVERSITY_THRESHOLD = 5
    private const val PATH_DIVERSITY_WINDOW_MS = 30_000L

    // ==================== 检测方法 ====================

    /**
     * 对单条记录进行实时异常检测
     */
    fun detect(entry: AuditLogEntry): AnomalyResult {
        val checks = mutableListOf<AnomalyCheck>()

        // 检查1：敏感路径访问
        if (isSensitivePath(entry.path)) {
            checks.add(AnomalyCheck(
                type = AuditLogEntry.ANOMALY_SENSITIVE_PATH,
                riskScore = 40,
                description = "访问敏感路径: ${entry.path}"
            ))
        }

        // 检查2：拒绝操作（本身就是异常信号）
        if (entry.result == 1) {
            checks.add(AnomalyCheck(
                type = AuditLogEntry.ANOMALY_UNUSUAL_PATTERN,
                riskScore = 30,
                description = "文件访问被拒绝"
            ))
        }

        // 检查3：敏感文件类型
        if (isSensitiveFileType(entry.path)) {
            checks.add(AnomalyCheck(
                type = AuditLogEntry.ANOMALY_SENSITIVE_PATH,
                riskScore = 35,
                description = "访问敏感文件类型"
            ))
        }

        // 计算总风险分
        val totalRisk = checks.sumOf { it.riskScore }.coerceAtMost(100)

        return if (checks.isNotEmpty()) {
            AnomalyResult(
                isAnomaly = true,
                riskScore = totalRisk,
                primaryType = checks.maxByOrNull { it.riskScore }?.type,
                checks = checks
            )
        } else {
            AnomalyResult(isAnomaly = false, riskScore = 0)
        }
    }

    /**
     * 批量异常检测（基于历史数据模式）
     */
    suspend fun detectBatch(entries: List<AuditLogEntry>): List<AuditLogEntry> =
        withContext(Dispatchers.Default) {
            if (entries.isEmpty()) return@withContext emptyList()

            val results = entries.toMutableList()

            // 按PID分组检测高频访问
            val pidGroups = entries.groupBy { it.pid }
            pidGroups.forEach { (pid, pidEntries) ->
                detectHighFrequency(pidEntries)?.let { anomalyEntries ->
                    anomalyEntries.forEach { anomalyId ->
                        results.find { it.id == anomalyId }?.let { entry ->
                            val index = results.indexOf(entry)
                            results[index] = entry.copy(
                                isAnomaly = true,
                                anomalyType = AuditLogEntry.ANOMALY_HIGH_FREQUENCY,
                                riskScore = (entry.riskScore + 50).coerceAtMost(100)
                            )
                        }
                    }
                }
            }

            // 检测路径多样性异常（短时间内访问多个不同路径）
            detectPathDiversity(entries)?.let { anomalyEntries ->
                anomalyEntries.forEach { anomalyId ->
                    results.find { it.id == anomalyId }?.let { entry ->
                        val index = results.indexOf(entry)
                        if (!results[index].isAnomaly) {
                            results[index] = entry.copy(
                                isAnomaly = true,
                                anomalyType = AuditLogEntry.ANOMALY_UNUSUAL_PATTERN,
                                riskScore = (entry.riskScore + 45).coerceAtMost(100)
                            )
                        }
                    }
                }
            }

            results
        }

    // ==================== 私有检测逻辑 ====================

    private fun isSensitivePath(path: String): Boolean {
        return SENSITIVE_PATHS.any { path.startsWith(it) }
    }

    private fun isSensitiveFileType(path: String): Boolean {
        val lowerPath = path.lowercase()
        return SENSITIVE_EXTENSIONS.any { lowerPath.endsWith(it) }
    }

    /**
     * 检测高频访问：同一PID在短时间内大量访问
     */
    private fun detectHighFrequency(entries: List<AuditLogEntry>): List<Long>? {
        if (entries.size < HIGH_FREQUENCY_THRESHOLD) return null

        val sorted = entries.sortedBy { it.timestamp }
        val anomalyIds = mutableListOf<Long>()

        // 滑动窗口检测
        var windowStart = 0
        for (i in sorted.indices) {
            while (sorted[i].timestamp - sorted[windowStart].timestamp > HIGH_FREQUENCY_WINDOW_MS) {
                windowStart++
            }
            val windowCount = i - windowStart + 1
            if (windowCount >= HIGH_FREQUENCY_THRESHOLD) {
                // 标记窗口内的所有记录
                for (j in windowStart..i) {
                    if (!anomalyIds.contains(sorted[j].id)) {
                        anomalyIds.add(sorted[j].id)
                    }
                }
            }
        }

        return if (anomalyIds.isNotEmpty()) anomalyIds else null
    }

    /**
     * 检测路径多样性异常：短时间内访问大量不同路径
     */
    private fun detectPathDiversity(entries: List<AuditLogEntry>): List<Long>? {
        val sorted = entries.sortedBy { it.timestamp }
        val anomalyIds = mutableListOf<Long>()

        for (i in sorted.indices) {
            val windowEnd = sorted[i].timestamp
            val windowStart = windowEnd - PATH_DIVERSITY_WINDOW_MS

            val windowEntries = sorted.filter {
                it.timestamp in windowStart..windowEnd
            }

            val uniquePaths = windowEntries.map { it.path }.distinct().size
            if (uniquePaths >= PATH_DIVERSITY_THRESHOLD) {
                windowEntries.forEach {
                    if (!anomalyIds.contains(it.id)) {
                        anomalyIds.add(it.id)
                    }
                }
            }
        }

        return if (anomalyIds.isNotEmpty()) anomalyIds else null
    }
}

/**
 * 异常检测结果
 */
data class AnomalyResult(
    val isAnomaly: Boolean,
    val riskScore: Int = 0,
    val primaryType: String? = null,
    val checks: List<AnomalyCheck> = emptyList()
)

/**
 * 单项异常检查
 */
data class AnomalyCheck(
    val type: String,
    val riskScore: Int,
    val description: String
)
