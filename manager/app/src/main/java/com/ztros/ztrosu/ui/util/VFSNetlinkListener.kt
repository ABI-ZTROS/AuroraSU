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

private const val TAG = "VFSNetlinkListener"

/**
 * VFS Netlink事件监听器
 *
 * 通过Netlink socket异步接收内核VFS模块发出的事件通知。
 * 由于Android Java/Kotlin层没有直接的netlink socket API，
 * 本类采用Shell命令 + 线程轮询的fallback方案实现。
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
object VFSNetlinkListener {

    // ==================== 事件类型常量 ====================

    const val EVENT_VFS_OPEN = 1
    const val EVENT_VFS_READ = 2
    const val EVENT_VFS_WRITE = 3
    const val EVENT_VFS_CLOSE = 4
    const val EVENT_VFS_DENY = 5
    const val EVENT_HOOK_ADDED = 10
    const val EVENT_HOOK_REMOVED = 11
    const val EVENT_RULE_CHANGED = 12

    // Netlink多播组
    const val NETLINK_GROUP = 31

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

    // 事件缓冲区（用于Shell轮询模式）
    private val eventBuffer = mutableListOf<VFSEvent>()
    private val bufferLock = Any()

    // 事件统计
    private var totalEventsReceived = 0L
    private var lastEventTime = 0L

    // ==================== 监听控制 ====================

    /**
     * 启动Netlink事件监听
     *
     * 由于Android没有直接的netlink socket API，采用以下策略：
     * 1. 首先尝试通过JNI native代码创建netlink socket
     * 2. 如果JNI不可行，使用Shell命令 + 线程轮询作为fallback
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

        Log.i(TAG, "Starting VFS Netlink event listener...")

        // 尝试JNI方式，失败则使用Shell fallback
        if (!tryStartJniListener()) {
            Log.i(TAG, "JNI listener not available, using Shell fallback")
            startShellFallbackListener()
        }
    }

    /**
     * 停止Netlink事件监听
     */
    fun stopListening() {
        if (!isListening.getAndSet(false)) {
            Log.w(TAG, "Not currently listening")
            return
        }

        Log.i(TAG, "Stopping VFS Netlink event listener...")

        // 中断监听线程
        listenerThread?.interrupt()
        listenerThread = null

        // 清理JNI资源
        tryStopJniListener()

        eventCallback = null
        errorCallback = null

        // 清空缓冲区
        synchronized(bufferLock) {
            eventBuffer.clear()
        }

        Log.i(TAG, "VFS Netlink event listener stopped. Total events: $totalEventsReceived")
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

    // ==================== JNI Netlink实现 ====================

    /**
     * 尝试通过JNI启动native netlink socket监听
     *
     * 注意：这需要对应的native C/C++代码实现。
     * 如果native库中没有对应的JNI函数，此方法将返回false。
     */
    private fun tryStartJniListener(): Boolean {
        return try {
            // 尝试加载native netlink库
            System.loadLibrary("vfsnetlink")

            // 调用native方法创建netlink socket
            val fd = nativeCreateNetlinkSocket(NETLINK_GROUP)
            if (fd < 0) {
                Log.w(TAG, "JNI: Failed to create netlink socket (fd=$fd)")
                return false
            }

            Log.i(TAG, "JNI: Created netlink socket fd=$fd")

            // 启动native读取线程
            listenerThread = thread(name = "VFSNetlink-JNI") {
                try {
                    nativeStartListening(fd) { eventData ->
                        val event = parseEvent(eventData)
                        if (event != null) {
                            dispatchEvent(event)
                        }
                    }
                } catch (e: InterruptedException) {
                    Log.d(TAG, "JNI listener thread interrupted")
                } catch (e: Exception) {
                    Log.e(TAG, "JNI listener error", e)
                    notifyError("JNI listener error: ${e.message}")
                } finally {
                    nativeCloseNetlinkSocket(fd)
                }
            }

            true
        } catch (e: UnsatisfiedLinkError) {
            Log.d(TAG, "JNI netlink library not available: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "JNI listener initialization failed", e)
            false
        }
    }

    /**
     * 停止JNI监听
     */
    private fun tryStopJniListener() {
        try {
            nativeStopListening()
        } catch (e: UnsatisfiedLinkError) {
            // Native库不可用，忽略
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping JNI listener", e)
        }
    }

    // Native方法声明（需要对应的C/C++实现）
    // 如果没有native实现，这些方法会抛出UnsatisfiedLinkError

    private external fun nativeCreateNetlinkSocket(group: Int): Int
    private external fun nativeStartListening(fd: Int, callback: (ByteArray) -> Unit)
    private external fun nativeStopListening()
    private external fun nativeCloseNetlinkSocket(fd: Int)

    // ==================== Shell Fallback实现 ====================

    /**
     * 使用Shell命令 + 线程轮询作为fallback方案
     *
     * 策略：
     * 1. 通过root shell在后台持续读取 /proc/net/netlink 或内核事件节点
     * 2. 解析输出并转换为VFSEvent
     * 3. 通过回调通知UI层
     */
    private fun startShellFallbackListener() {
        listenerThread = thread(name = "VFSNetlink-Shell") {
            Log.i(TAG, "Shell fallback listener started")

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
                Log.d(TAG, "Shell fallback listener interrupted")
            } catch (e: Exception) {
                Log.e(TAG, "Shell fallback listener error", e)
                notifyError("Shell listener error: ${e.message}")
            }

            Log.i(TAG, "Shell fallback listener stopped")
        }
    }

    /**
     * 通过Shell命令轮询事件
     *
     * 尝试以下数据源（按优先级）：
     * 1. /sys/kernel/ztrosu/vfs/events - 内核事件文件
     * 2. /proc/net/netlink - Netlink socket状态
     * 3. /sys/kernel/debug/ztrosu/vfs/event_log - DebugFS事件日志
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

        // 方案3：通过ksud获取事件
        try {
            val ksudEvents = pollFromKsud()
            if (ksudEvents.isNotEmpty()) {
                events.addAll(ksudEvents)
            }
        } catch (e: Exception) {
            Log.d(TAG, "ksud event source not available")
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

    /**
     * 通过ksud守护进程获取事件
     */
    private fun pollFromKsud(): List<VFSEvent> {
        return try {
            val ksudPath = "/data/adb/ksu/ksud"
            val result = Shell.cmd("$ksudPath vfs-events poll 2>/dev/null").exec()

            if (result.isSuccess && result.out.isNotEmpty()) {
                parseTextEventLog(result.out.joinToString("\n"))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
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

    // ==================== Shell Netlink辅助工具 ====================

    /**
     * 检查Netlink是否可用
     */
    fun isNetlinkAvailable(): Boolean {
        return try {
            // 检查netlink模块是否加载
            val result = Shell.cmd(
                """
                # 检查 /proc/net/netlink 是否可读
                if [ -r /proc/net/netlink ]; then
                    # 检查是否有VFS相关的netlink组
                    cat /proc/net/netlink 2>/dev/null | head -5
                    echo "NETLINK_OK"
                else
                    echo "NETLINK_FAIL"
                fi
                """.trimIndent()
            ).exec()

            result.out.any { it.contains("NETLINK_OK") }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取Netlink socket状态信息
     */
    fun getNetlinkStatus(): String {
        return try {
            val result = Shell.cmd("cat /proc/net/netlink 2>/dev/null").exec()
            if (result.isSuccess) {
                result.out.joinToString("\n")
            } else {
                "Not available"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("VFSNetlinkListener Debug Info:")
            appendLine("  Listening: ${isListening.get()}")
            appendLine("  Netlink Group: $NETLINK_GROUP")
            appendLine("  Netlink Available: ${isNetlinkAvailable()}")
            appendLine("  Total Events: $totalEventsReceived")
            appendLine("  Last Event: $lastEventTime")
            appendLine("  Buffer Size: ${getBufferSize()}")
            appendLine("  Listener Thread: ${listenerThread?.name ?: "null"}")
            appendLine("  Listener Thread Alive: ${listenerThread?.isAlive ?: false}")
        }
    }
}
