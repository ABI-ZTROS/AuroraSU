package com.ztros.ztrosu.data.audit

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 审计日志数据库访问对象
 */
@Dao
interface AuditLogDao {

    // ==================== 插入操作 ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditLogEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AuditLogEntry>): List<Long>

    // ==================== 查询操作 ====================

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 100): List<AuditLogEntry>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 100): Flow<List<AuditLogEntry>>

    @Query("SELECT * FROM audit_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getLogsByTimeRange(startTime: Long, endTime: Long): List<AuditLogEntry>

    @Query("SELECT * FROM audit_logs WHERE pid = :pid ORDER BY timestamp DESC")
    suspend fun getLogsByPid(pid: Int): List<AuditLogEntry>

    @Query("SELECT * FROM audit_logs WHERE uid = :uid ORDER BY timestamp DESC")
    suspend fun getLogsByUid(uid: Int): List<AuditLogEntry>

    @Query("SELECT * FROM audit_logs WHERE eventType = :eventType ORDER BY timestamp DESC")
    suspend fun getLogsByEventType(eventType: Int): List<AuditLogEntry>

    @Query("SELECT * FROM audit_logs WHERE path LIKE '%' || :pathPattern || '%' ORDER BY timestamp DESC")
    suspend fun getLogsByPath(pathPattern: String): List<AuditLogEntry>

    @Query("SELECT * FROM audit_logs WHERE isAnomaly = 1 ORDER BY timestamp DESC")
    suspend fun getAnomalyLogs(): List<AuditLogEntry>

    @Query("SELECT * FROM audit_logs WHERE isAnomaly = 1 ORDER BY timestamp DESC")
    fun getAnomalyLogsFlow(): Flow<List<AuditLogEntry>>

    @Query("SELECT * FROM audit_logs WHERE riskScore >= :minScore ORDER BY timestamp DESC")
    suspend fun getHighRiskLogs(minScore: Int = 50): List<AuditLogEntry>

    // ==================== 统计查询 ====================

    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM audit_logs WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getCountByTimeRange(startTime: Long, endTime: Long): Int

    @Query("SELECT COUNT(*) FROM audit_logs WHERE isAnomaly = 1")
    suspend fun getAnomalyCount(): Int

    @Query("SELECT COUNT(*) FROM audit_logs WHERE result = 1")
    suspend fun getDeniedCount(): Int

    @Query("SELECT COUNT(*) FROM audit_logs WHERE eventType = :eventType")
    suspend fun getCountByEventType(eventType: Int): Int

    @Query("SELECT DISTINCT pid FROM audit_logs")
    suspend fun getAllPids(): List<Int>

    @Query("SELECT DISTINCT path FROM audit_logs WHERE timestamp >= :since")
    suspend fun getRecentPaths(since: Long): List<String>

    // ==================== 聚合查询 ====================

    @Query("""
        SELECT eventType, COUNT(*) as count 
        FROM audit_logs 
        WHERE timestamp BETWEEN :startTime AND :endTime 
        GROUP BY eventType
    """)
    suspend fun getEventTypeDistribution(startTime: Long, endTime: Long): List<EventTypeCount>

    @Query("""
        SELECT path, COUNT(*) as count 
        FROM audit_logs 
        WHERE timestamp >= :since 
        GROUP BY path 
        ORDER BY count DESC 
        LIMIT :limit
    """)
    suspend fun getTopAccessedPaths(since: Long, limit: Int = 10): List<PathCount>

    @Query("""
        SELECT pid, COUNT(*) as count 
        FROM audit_logs 
        WHERE timestamp >= :since 
        GROUP BY pid 
        ORDER BY count DESC 
        LIMIT :limit
    """)
    suspend fun getTopActivePids(since: Long, limit: Int = 10): List<PidCount>

    // ==================== 删除操作 ====================

    @Query("DELETE FROM audit_logs WHERE timestamp < :beforeTime")
    suspend fun deleteOldLogs(beforeTime: Long): Int

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll(): Int

    @Query("DELETE FROM audit_logs WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    // ==================== 更新操作 ====================

    @Query("UPDATE audit_logs SET isAnomaly = 1, anomalyType = :anomalyType, riskScore = :riskScore WHERE id = :id")
    suspend fun markAsAnomaly(id: Long, anomalyType: String, riskScore: Int): Int
}

/**
 * 事件类型统计
 */
data class EventTypeCount(
    val eventType: Int,
    val count: Int
)

/**
 * 路径访问统计
 */
data class PathCount(
    val path: String,
    val count: Int
)

/**
 * PID活动统计
 */
data class PidCount(
    val pid: Int,
    val count: Int
)
