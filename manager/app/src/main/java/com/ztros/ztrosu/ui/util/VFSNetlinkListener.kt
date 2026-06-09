package com.ztros.ztrosu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

private const val TAG = "VFSSysfsEventListener"

/**
 * VFS Sysfs事件监听器
 *
 * 通过轮询sysfs节点异步接收内核VFS模块发出的事件通知。
 * 由于Android Java/Kotlin层没有直接的netlink socket API，
 * 本类采用Shell命令 + 线程轮询的sysfs读取方案实现。
 *
 * 事件协议（与内核约定，little-endian）：
 *   Magic     : UInt32 (0xAF5F)
 *   EventType : UInt32
 *   PID       : UInt32
 *   UID       : UInt32
 *   PathLen   : UInt32
 *   Path      : ByteArray
 *   Timestamp : UInt64
 *   Result    : UInt32 (0=allow, 1=deny)
 */
object VFSSysfsEventListener {

    // ==================== 事件类型常量 ====================

    const val EVENT_VFS_OPEN = 1
    const val EVENT_VFS_READ = 2
    const val EVENT_VFS_WRITE = 3
    const val EVENT_VFS_CLOSE = 4
    const val EVENT_VFS_DENY = 5
    const val EVENT_HOOK_ADDED = 10
    const val EVENT_HOOK_REMOVED = 11
    const val EVENT_RULE_CHANGED = 12

    // 协议头大小: 5 * UInt32 + 1 * UInt64 = 28 bytes (不含Path)
    private const val EVENT_HEADER_SIZE = 28

    // 事件Magic
    private const val EVENT_MAGIC: Int = 0xAF5F

    // ==================== 数据类 ====================

    /**
     * VFS事件数据结构
     */
    data class VFSEvent(
        val eventType: Int,
        val pid: Int,
        val uid: Int,
        val path: String,
        val timestamp: Long,
        val result: Int  // 0=allow, 1=deny
    ) {
        /**
         * 获取事件类型的人类可读名称
         */
        fun getEventTypeName(): String {
            return when (eventType) {
                EVENT_VFS_OPEN -> "OPEN"
                EVENT_VFS_READ -> "READ"
                EVENT_VFS_WRITE -> "WRITE"
                EVENT_VFS_CLOSE -> "CLOSE"
                EVENT_VFS_DENY -> "DENY"
                EVENT_HOOK_ADDED -> "HOOK_ADDED"
                EVENT_HOOK_REMOVED -> "HOOK_REMOVED"
                EVENT_RULE_CHANGED -> "RULE_CHANGED"
                else -> "UNKNOWN($eventType)"
            }
        }

        /**
         * 获取结果的人类可读名称
         */
        fun getResultName(): String {
            return if (result == 0) "ALLOW" else "DENY"
        }

        override fun toString(): String {
            return "VFSEvent(type=${getEventTypeName()}, pid=$pid, uid=$uid, " +
                    "path=$path, ts=$timestamp, result=${getResultName()})"
        }
    }

    // ==================== 监听状态 ====================

    private val isListening = AtomicBoolean(false)
    private var listenerThread: Thread? = null
    private var eventCallback: ((VFSEvent) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    // 事件缓冲区（用于轮询模式）
    private val eventBuffer = mutableListOf<VFSEvent>()
    private val bufferLock = Any()

    // 事件统计
    private var totalEventsReceived = 0L
    private var lastEventTime = 0L

    // ==================== 监听控制 ====================

    /**
     * 启动Sysfs事件监听
     *
     * 由于Android没有直接的netlink socket API，采用以下策略：
     * 使用Shell命令 + 线程轮询读取sysfs节点作为PRIMARY方案
     *
     * @param callback 事件回调函数
     * @param errorCallback 错误回调函数（可选）
     */
    fun startListening(callback: (VFSEvent) -> Unit, errorCallback: ((String) -> Unit)? = null) {
        if (isListening.getAndSet(true)) {
            Log.w(TAG, "Already listening, ignoring duplicate start request")
            return
        }

        this.eventCallback = callback
        this.errorCallback = errorCallback

        Log.i(TAG, "Starting VFS Sysfs event listener...")

        // 使用Shell轮询作为主要方案
        if (!startShellPollingListener()) {
            Log.w(TAG, "No kernel event source available, listener not started")
            isListening.set(false)
            eventCallback = null
            this.errorCallback = null
        }
    }

    /**
     * 停止Sysfs事件监听
     */
    fun stopListening() {
        if (!isListening.getAndSet(false)) {
            Log.w(TAG, "Not currently listening")
            return
        }

        Log.i(TAG, "Stopping VFS Sysfs event listener...")

        // 中断监听线程
        listenerThread?.interrupt()
        listenerThread = null

        eventCallback = null
        errorCallback = null

        // 清空缓冲区
        synchronized(bufferLock) {
            eventBuffer.clear()
        }

        Log.i(TAG, "VFS Sysfs event listener stopped. Total events: $totalEventsReceived")
    }

    /**
     * 检查是否正在监听
     */
    fun isListening(): Boolean = isListening.get()

    /**
     * 获取事件统计
     */
    fun getStats(): EventStats {
        return EventStats(
            isListening = isListening.get(),
            totalEvents = totalEventsReceived,
            lastEventTime = lastEventTime
        )
    }

    data class EventStats(
        val isListening: Boolean,
        val totalEvents: Long,
        val lastEventTime: Long
    )

    // ==================== Shell Polling实现 ====================

    /**
     * 使用Shell命令 + 线程轮询作为主要方案
     *
     * 策略：
     * 1. 通过root shell持续读取 /sys/kernel/ztrosu/vfs/events 或内核事件节点
     * 2. 解析输出并转换为VFSEvent
     * 3. 通过回调通知UI层
     *
     * @return 是否成功启动（至少有一个数据源可用）
     */
    private fun startShellPollingListener(): Boolean {
        // 先检查是否有任何可用数据源
        val hasKernelNode = SuFile.open("/sys/kernel/ztrosu/vfs/events").exists()
        val hasDebugFs = SuFile.open("/sys/kernel/debug/ztrosu/vfs/event_log").exists()
        if (!hasKernelNode && !hasDebugFs) {
            Log.w(TAG, "No kernel event sources available")
            return false
        }

        listenerThread = thread(name = "VFSSysfs-Polling") {
            Log.i(TAG, "Shell polling listener started")

            try {
                while (isListening.get() && !Thread.currentThread().isInterrupted) {
                    // 尝试多种数据源
                    val events = pollEventsFromShell()

                    if (events.isNotEmpty()) {
                        events.forEach { event ->
                            dispatchEvent(event)
                        }
                    }

                    // 轮询间隔，避免CPU占用过高
                    Thread.sleep(500)
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Shell polling listener interrupted")
            } catch (e: Exception) {
                Log.e(TAG, "Shell polling listener error", e)
                notifyError("Shell listener error: ${e.message}")
            }

            Log.i(TAG, "Shell polling listener stopped")
        }
        return true
    }

    /**
     * 通过Shell命令轮询事件
     *
     * 尝试以下数据源（按优先级）：
     * 1. /sys/kernel/ztrosu/vfs/events - 内核事件文件
     * 2. /sys/kernel/debug/ztrosu/vfs/event_log - DebugFS事件日志
     */
    private fun pollEventsFromShell(): List<VFSEvent> {
        val events = mutableListOf<VFSEvent>()

        // 方案1：读取内核事件节点
        try {
            val kernelEvents = pollFromKernelEventNode()
            if (kernelEvents.isNotEmpty()) {
                events.addAll(kernelEvents)
                return events
            }
        } catch (e: Exception) {
            Log.d(TAG, "Kernel event node not available")
        }

        // 方案2：读取DebugFS事件日志
        try {
            val debugEvents = pollFromDebugFs()
            if (debugEvents.isNotEmpty()) {
                events.addAll(debugEvents)
                return events
            }
        } catch (e: Exception) {
            Log.d(TAG, "DebugFS event log not available")
        }

        return events
    }

    /**
     * 从内核事件节点读取事件
     *
     * 读取 /sys/kernel/ztrosu/vfs/events
     * 格式为二进制事件流，每条事件按事件协议排列
     */
    private fun pollFromKernelEventNode(): List<VFSEvent> {
        val eventNodePath = "/sys/kernel/ztrosu/vfs/events"
        val eventFile = SuFile.open(eventNodePath)
        if (!eventFile.exists() || !eventFile.canRead()) {
            return emptyList()
        }

        return try {
            // 读取二进制事件数据
            val rawData = eventFile.readBytes()
            if (rawData.isEmpty()) {
                return emptyList()
            }

            parseEventStream(rawData)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading kernel event node", e)
            emptyList()
        }
    }

    /**
     * 从DebugFS读取事件日志
     *
     * 读取 /sys/kernel/debug/ztrosu/vfs/event_log
     * 格式为文本行，每行一个事件
     */
    private fun pollFromDebugFs(): List<VFSEvent> {
        val eventLogPath = "/sys/kernel/debug/ztrosu/vfs/event_log"
        val eventFile = SuFile.open(eventLogPath)
        if (!eventFile.exists() || !eventFile.canRead()) {
            return emptyList()
        }

        return try {
            val content = eventFile.readText()
            if (content.isBlank()) {
                return emptyList()
            }

            parseTextEventLog(content)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading debugfs event log", e)
            emptyList()
        }
    }

    // ==================== 事件解析 ====================

    /**
     * 解析二进制事件流
     *
     * 事件协议（little-endian）：
     *   Magic     : UInt32 (0xAF5F)
     *   EventType : UInt32
     *   PID       : UInt32
     *   UID       : UInt32
     *   PathLen   : UInt32
     *   Path      : ByteArray (PathLen bytes)
     *   Timestamp : UInt64
     *   Result    : UInt32
     */
    private fun parseEventStream(data: ByteArray): List<VFSEvent> {
        val events = mutableListOf<VFSEvent>()
        var offset = 0

        while (offset < data.size) {
            try {
                // 至少需要头部大小
                if (offset + EVENT_HEADER_SIZE > data.size) break

                val buffer = ByteBuffer.wrap(data, offset, data.size - offset)
                buffer.order(ByteOrder.LITTLE_ENDIAN)

                val magic = buffer.getInt()
                if (magic != EVENT_MAGIC) {
                    Log.w(TAG, "Invalid event magic at offset $offset: 0x${magic.toString(16)}")
                    break
                }

                val eventType = buffer.getInt()
                val pid = buffer.getInt()
                val uid = buffer.getInt()
                val pathLen = buffer.getInt()

                // 检查是否有足够的数据包含路径
                if (buffer.remaining() < pathLen + 8 + 4) {
                    Log.w(TAG, "Incomplete event at offset $offset")
                    break
                }

                val pathBytes = ByteArray(pathLen)
                buffer.get(pathBytes)
                val path = String(pathBytes, Charsets.UTF_8)

                val timestamp = buffer.getLong()
                val result = buffer.getInt()

                val event = VFSEvent(
                    eventType = eventType,
                    pid = pid,
                    uid = uid,
                    path = path,
                    timestamp = timestamp,
                    result = result
                )
                events.add(event)

                // 移动偏移量
                offset += EVENT_HEADER_SIZE + pathLen

            } catch (e: Exception) {
                Log.e(TAG, "Error parsing event at offset $offset", e)
                break
            }
        }

        return events
    }

    /**
     * 解析单个二进制事件
     */
    private fun parseEvent(data: ByteArray): VFSEvent? {
        return try {
            if (data.size < EVENT_HEADER_SIZE) {
                Log.w(TAG, "Event data too short: ${data.size} bytes")
                return null
            }

            val buffer = ByteBuffer.wrap(data)
            buffer.order(ByteOrder.LITTLE_ENDIAN)

            val magic = buffer.getInt()
            if (magic != EVENT_MAGIC) {
                Log.w(TAG, "Invalid event magic: 0x${magic.toString(16)}")
                return null
            }

            val eventType = buffer.getInt()
            val pid = buffer.getInt()
            val uid = buffer.getInt()
            val pathLen = buffer.getInt()

            if (buffer.remaining() < pathLen + 8 + 4) {
                Log.w(TAG, "Incomplete event data")
                return null
            }

            val pathBytes = ByteArray(pathLen)
            buffer.get(pathBytes)
            val path = String(pathBytes, Charsets.UTF_8)

            val timestamp = buffer.getLong()
            val result = buffer.getInt()

            VFSEvent(
                eventType = eventType,
                pid = pid,
                uid = uid,
                path = path,
                timestamp = timestamp,
                result = result
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing event", e)
            null
        }
    }

    /**
     * 解析文本格式的事件日志
     *
     * 支持的格式：
     * 格式1: type=<int> pid=<int> uid=<int> path=<string> ts=<long> result=<int>
     * 格式2: <type_name> <pid> <uid> <path> <timestamp> <result>
     * 格式3: JSON格式
     */
    private fun parseTextEventLog(content: String): List<VFSEvent> {
        val events = mutableListOf<VFSEvent>()

        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach

            try {
                val event = parseTextEventLine(trimmed)
                if (event != null) {
                    events.add(event)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to parse event line: $trimmed")
            }
        }

        return events
    }

    /**
     * 解析单行文本事件
     */
    private fun parseTextEventLine(line: String): VFSEvent? {
        // 格式1: key=value 格式
        if (line.contains("=")) {
            return parseKeyValueEvent(line)
        }

        // 格式2: 空格分隔
        val parts = line.split("\\s+".toRegex())
        if (parts.size >= 6) {
            return try {
                val eventType = parseEventTypeName(parts[0])
                val pid = parts[1].toIntOrNull() ?: return null
                val uid = parts[2].toIntOrNull() ?: return null
                val path = parts[3]
                val timestamp = parts[4].toLongOrNull() ?: System.currentTimeMillis()
                val result = parts[5].toIntOrNull() ?: 0

                VFSEvent(
                    eventType = eventType,
                    pid = pid,
                    uid = uid,
                    path = path,
                    timestamp = timestamp,
                    result = result
                )
            } catch (e: Exception) {
                null
            }
        }

        return null
    }

    /**
     * 解析key=value格式的事件行
     */
    private fun parseKeyValueEvent(line: String): VFSEvent? {
        val map = mutableMapOf<String, String>()
        line.split("\\s+".toRegex()).forEach { token ->
            val eqIdx = token.indexOf('=')
            if (eqIdx > 0) {
                val key = token.substring(0, eqIdx)
                val value = token.substring(eqIdx + 1)
                map[key] = value
            }
        }

        val eventType = map["type"]?.toIntOrNull() ?: map["event"]?.let { parseEventTypeName(it) } ?: return null
        val pid = map["pid"]?.toIntOrNull() ?: return null
        val uid = map["uid"]?.toIntOrNull() ?: return null
        val path = map["path"] ?: return null
        val timestamp = map["ts"]?.toLongOrNull() ?: map["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()
        val result = map["result"]?.toIntOrNull() ?: map["res"]?.toIntOrNull() ?: 0

        return VFSEvent(
            eventType = eventType,
            pid = pid,
            uid = uid,
            path = path,
            timestamp = timestamp,
            result = result
        )
    }

    /**
     * 事件类型名称转数值
     */
    private fun parseEventTypeName(name: String): Int {
        return when (name.uppercase()) {
            "OPEN", "VFS_OPEN" -> EVENT_VFS_OPEN
            "READ", "VFS_READ" -> EVENT_VFS_READ
            "WRITE", "VFS_WRITE" -> EVENT_VFS_WRITE
            "CLOSE", "VFS_CLOSE" -> EVENT_VFS_CLOSE
            "DENY", "VFS_DENY" -> EVENT_VFS_DENY
            "HOOK_ADDED" -> EVENT_HOOK_ADDED
            "HOOK_REMOVED" -> EVENT_HOOK_REMOVED
            "RULE_CHANGED" -> EVENT_RULE_CHANGED
            else -> name.toIntOrNull() ?: -1
        }
    }

    // ==================== 事件分发 ====================

    /**
     * 分发事件到回调
     */
    private fun dispatchEvent(event: VFSEvent) {
        totalEventsReceived++
        lastEventTime = System.currentTimeMillis()

        // 添加到缓冲区
        synchronized(bufferLock) {
            eventBuffer.add(event)
            // 限制缓冲区大小
            while (eventBuffer.size > 1000) {
                eventBuffer.removeAt(0)
            }
        }

        // 通知回调
        try {
            eventCallback?.invoke(event)
        } catch (e: Exception) {
            Log.e(TAG, "Error in event callback", e)
        }
    }

    /**
     * 通知错误
     */
    private fun notifyError(message: String) {
        try {
            errorCallback?.invoke(message)
        } catch (e: Exception) {
            Log.e(TAG, "Error in error callback", e)
        }
    }

    // ==================== 缓冲区访问 ====================

    /**
     * 获取缓冲区中的所有事件
     */
    fun getBufferedEvents(): List<VFSEvent> {
        synchronized(bufferLock) {
            return eventBuffer.toList()
        }
    }

    /**
     * 清空事件缓冲区
     */
    fun clearBuffer() {
        synchronized(bufferLock) {
            eventBuffer.clear()
        }
    }

    /**
     * 获取缓冲区中的事件数量
     */
    fun getBufferSize(): Int {
        synchronized(bufferLock) {
            return eventBuffer.size
        }
    }

    // ==================== Sysfs辅助工具 ====================

    /**
     * 检查sysfs事件源是否可用
     */
    fun isSysfsAvailable(): Boolean {
        val hasKernelNode = SuFile.open("/sys/kernel/ztrosu/vfs/events").exists()
        val hasDebugFs = SuFile.open("/sys/kernel/debug/ztrosu/vfs/event_log").exists()
        return hasKernelNode || hasDebugFs
    }

    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("VFSSysfsEventListener Debug Info:")
            appendLine("  Listening: ${isListening.get()}")
            appendLine("  Sysfs Available: ${isSysfsAvailable()}")
            appendLine("  Total Events: $totalEventsReceived")
            appendLine("  Last Event: $lastEventTime")
            appendLine("  Buffer Size: ${getBufferSize()}")
            appendLine("  Listener Thread: ${listenerThread?.name ?: "null"}")
            appendLine("  Listener Thread Alive: ${listenerThread?.isAlive ?: false}")
        }
    }
}

/**
 * 兼容性别名 - 旧代码可能引用VFSNetlinkListener
 * @deprecated 请使用 VFSSysfsEventListener
 */
@Deprecated("Use VFSSysfsEventListener instead", ReplaceWith("VFSSysfsEventListener"))
typealias VFSNetlinkListener = VFSSysfsEventListener
