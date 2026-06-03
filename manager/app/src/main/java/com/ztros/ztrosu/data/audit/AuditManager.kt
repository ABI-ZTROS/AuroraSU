package com.ztros.ztrosu.data.audit

import android.content.Context
import android.util.Log
import com.ztros.ztrosu.ui.util.VFSNetlinkListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private const val TAG = "AuditManager"

/**
 * 审计日志管理器
 *
 * 统一管理审计日志的收集、存储、查询和异常检测
 */
class AuditManager private constructor(context: Context) {

    private val database = AuditDatabase.getInstance(context)
    private val dao = database.auditLogDao()

    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 实时事件流（用于UI观察）
    private val _eventFlow = MutableSharedFlow<AuditLogEntry>(replay = 0, extraBufferCapacity = 100)
    val eventFlow: SharedFlow<AuditLogEntry> = _eventFlow.asSharedFlow()

    // 异常事件流
    private val _anomalyFlow = MutableSharedFlow<AuditLogEntry>(replay = 0, extraBufferCapacity = 50)
    val anomalyFlow: SharedFlow<AuditLogEntry> = _anomalyFlow.asSharedFlow()

    // 统计信息流
    private val _statsFlow = MutableStateFlow(AuditStats())
    val statsFlow: StateFlow<AuditStats> = _statsFlow.asStateFlow()

    // 批量处理缓冲区
    private val batchBuffer = mutableListOf<AuditLogEntry>()
    private val batchLock = Any()
    private var batchJob: Job? = null

    companion object {
        @Volatile
        private var INSTANCE: AuditManager? = null

        fun getInstance(context: Context): AuditManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuditManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ==================== 事件接收 ====================

    /**
     * 从 VFSNetlinkListener 接收事件并处理
     */
    fun onVFSEvent(event: VFSNetlinkListener.VFSEvent) {
        val entry = convertToAuditEntry(event)

        // 实时异常检测
        val anomalyResult = AnomalyDetector.detect(entry)
        val finalEntry = if (anomalyResult.isAnomaly) {
            entry.copy(
                isAnomaly = true,
                anomalyType = anomalyResult.primaryType,
                riskScore = anomalyResult.riskScore
            )
        } else entry

        // 发送到实时流
        scope.launch {
            _eventFlow.emit(finalEntry)
            if (finalEntry.isAnomaly) {
                _anomalyFlow.emit(finalEntry)
            }
        }

        // 加入批量缓冲区
        synchronized(batchLock) {
            batchBuffer.add(finalEntry)
        }

        // 启动批量写入（如果未启动）
        startBatchWrite()
    }

    /**
     * 批量写入数据库
     */
    private fun startBatchWrite() {
        if (batchJob?.isActive == true) return

        batchJob = scope.launch {
            delay(1000) // 1秒批量写入

            val batch = synchronized(batchLock) {
                val copy = batchBuffer.toList()
                batchBuffer.clear()
                copy
            }

            if (batch.isNotEmpty()) {
                try {
                    dao.insertAll(batch)
                    updateStats()
                    Log.d(TAG, "Batch written ${batch.size} entries")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write batch", e)
                }
            }
        }
    }

    // ==================== 查询方法 ====================

    /**
     * 获取最近日志（Flow）
     */
    fun getRecentLogs(limit: Int = 100): Flow<List<AuditLogEntry>> {
        return dao.getRecentLogsFlow(limit)
    }

    /**
     * 获取异常日志
     */
    fun getAnomalyLogs(): Flow<List<AuditLogEntry>> {
        return dao.getAnomalyLogsFlow()
    }

    /**
     * 获取统计信息
     */
    suspend fun getStats(): AuditStats {
        return AuditStats(
            totalCount = dao.getTotalCount(),
            anomalyCount = dao.getAnomalyCount(),
            deniedCount = dao.getDeniedCount()
        )
    }

    /**
     * 按条件搜索
     */
    suspend fun search(
        startTime: Long? = null,
        endTime: Long? = null,
        pid: Int? = null,
        eventType: Int? = null,
        pathPattern: String? = null,
        onlyAnomaly: Boolean = false
    ): List<AuditLogEntry> {
        return when {
            startTime != null && endTime != null -> dao.getLogsByTimeRange(startTime, endTime)
            pid != null -> dao.getLogsByPid(pid)
            eventType != null -> dao.getLogsByEventType(eventType)
            pathPattern != null -> dao.getLogsByPath(pathPattern)
            onlyAnomaly -> dao.getAnomalyLogs()
            else -> dao.getRecentLogs(100)
        }
    }

    /**
     * 运行批量异常检测
     */
    suspend fun runAnomalyDetection(hoursBack: Int = 24) {
        val since = System.currentTimeMillis() - hoursBack * 3600_000L
        val entries = dao.getLogsByTimeRange(since, System.currentTimeMillis())

        val detected = AnomalyDetector.detectBatch(entries)
        detected.filter { it.isAnomaly && it.id > 0 }.forEach { entry ->
            dao.markAsAnomaly(entry.id, entry.anomalyType ?: "UNKNOWN", entry.riskScore)
        }

        Log.i(TAG, "Anomaly detection complete: ${detected.count { it.isAnomaly }} anomalies found")
    }

    /**
     * 清理旧日志
     */
    suspend fun cleanupOldLogs(keepDays: Int = 7): Int {
        val beforeTime = System.currentTimeMillis() - keepDays * 86400_000L
        return dao.deleteOldLogs(beforeTime)
    }

    /**
     * 导出审计日志
     */
    suspend fun exportLogs(format: ExportFormat, startTime: Long, endTime: Long): String {
        val entries = dao.getLogsByTimeRange(startTime, endTime)
        return when (format) {
            ExportFormat.CSV -> exportToCSV(entries)
            ExportFormat.JSON -> exportToJSON(entries)
        }
    }

    // ==================== 私有方法 ====================

    private fun convertToAuditEntry(event: VFSNetlinkListener.VFSEvent): AuditLogEntry {
        return AuditLogEntry(
            timestamp = event.timestamp,
            eventType = event.eventType,
            eventTypeName = event.getEventTypeName(),
            pid = event.pid,
            uid = event.uid,
            packageName = null, // TODO: 解析包名
            path = event.path,
            pathHash = event.path.hashCode().toString(),
            result = event.result,
            resultName = event.getResultName(),
            rawData = event.toString()
        )
    }

    private suspend fun updateStats() {
        _statsFlow.value = getStats()
    }

    private fun exportToCSV(entries: List<AuditLogEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("timestamp,eventType,pid,uid,path,result,isAnomaly,riskScore")
        entries.forEach {
            sb.appendLine("${it.timestamp},${it.eventTypeName},${it.pid},${it.uid},\"${it.path}\",${it.resultName},${it.isAnomaly},${it.riskScore}")
        }
        return sb.toString()
    }

    private fun exportToJSON(entries: List<AuditLogEntry>): String {
        // 简化JSON导出
        val items = entries.map {
            """{"ts":${it.timestamp},"type":"${it.eventTypeName}","pid":${it.pid},"path":"${it.path}","result":"${it.resultName}","anomaly":${it.isAnomaly},"risk":${it.riskScore}}"""
        }
        return "[${items.joinToString(",")}]"
    }

    // ==================== 生命周期 ====================

    fun destroy() {
        batchJob?.cancel()
        scope.cancel()
        INSTANCE = null
    }
}

/**
 * 审计统计信息
 */
data class AuditStats(
    val totalCount: Int = 0,
    val anomalyCount: Int = 0,
    val deniedCount: Int = 0,
    val lastUpdate: Long = System.currentTimeMillis()
)

/**
 * 导出格式
 */
enum class ExportFormat {
    CSV,
    JSON
}
