package com.ztros.ztrosu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private const val TAG = "VFSPipeComm"

/**
 * VFS Pipe通讯管理器
 *
 * 通过FIFO管道与内核VFS模块进行二进制协议通讯。
 * 使用SuFile和Shell进行root操作（/dev/需要root权限）。
 *
 * 命令协议（与内核约定，little-endian）：
 *   Magic   : UInt32 (0xAF5F)
 *   Version : UInt32 (2)
 *   CmdType : UInt32
 *   CmdLen  : UInt32
 *   Data    : ByteArray
 *
 * 响应协议：
 *   Magic   : UInt32 (0xAF5F)
 *   Version : UInt32 (2)
 *   Status  : UInt32 (0=success, non-zero=error)
 *   RespLen : UInt32
 *   Data    : ByteArray
 */
object VFSPipeComm {

    // ==================== 协议常量 ====================

    const val MAGIC: Int = 0xAF5F
    const val VERSION = 2
    const val PIPE_BASE = "/dev/aurora_vfs_"
    const val PIPE_TIMEOUT_MS = 5000L

    // 命令类型
    const val CMD_ADD_HOOK = 1
    const val CMD_REMOVE_HOOK = 2
    const val CMD_SET_RULES = 3
    const val CMD_CLEAR_RULES = 4
    const val CMD_SET_POLICY = 5
    const val CMD_RESET_STATS = 6
    const val CMD_QUERY_STATUS = 7

    // 响应状态码
    const val RESP_SUCCESS = 0
    const val RESP_ERR_UNKNOWN = 1
    const val RESP_ERR_INVALID_CMD = 2
    const val RESP_ERR_PIPE = 3
    const val RESP_ERR_TIMEOUT = 4
    const val RESP_ERR_PERMISSION = 5

    // 协议头大小: 4 * UInt32 = 16 bytes
    private const val HEADER_SIZE = 16

    // 线程安全锁
    private val commLock = ReentrantLock()

    // ==================== 核心通讯方法 ====================

    /**
     * 发送命令到内核VFS模块
     *
     * @param cmdType 命令类型 (CMD_ADD_HOOK, CMD_REMOVE_HOOK, etc.)
     * @param data 命令数据负载
     * @return true=成功, false=失败
     */
    fun sendCommand(cmdType: Int, data: ByteArray = ByteArray(0)): Boolean {
        commLock.withLock {
            var pipePath: String? = null
            return try {
                // 1. 创建随机命名管道
                pipePath = createPipe()
                if (pipePath == null) {
                    Log.e(TAG, "Failed to create pipe")
                    return false
                }

                // 2. 构建命令包
                val commandPacket = buildCommandPacket(cmdType, data)

                // 3. 通过root shell写入管道
                val writeSuccess = writeToPipe(pipePath, commandPacket)
                if (!writeSuccess) {
                    Log.e(TAG, "Failed to write command to pipe: $pipePath")
                    return false
                }

                // 4. 读取响应
                val responseData = readFromPipe(pipePath)
                if (responseData == null) {
                    Log.e(TAG, "Failed to read response from pipe: $pipePath")
                    return false
                }

                // 5. 解析响应
                parseResponse(responseData)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command type=$cmdType", e)
                false
            } finally {
                // 6. 销毁管道
                if (pipePath != null) {
                    destroyPipe(pipePath)
                }
            }
        }
    }

    /**
     * 发送命令并获取响应数据
     *
     * @param cmdType 命令类型
     * @param data 命令数据负载
     * @return 响应数据，失败返回null
     */
    fun sendCommandWithData(cmdType: Int, data: ByteArray = ByteArray(0)): ByteArray? {
        commLock.withLock {
            var pipePath: String? = null
            return try {
                pipePath = createPipe()
                if (pipePath == null) {
                    Log.e(TAG, "Failed to create pipe")
                    return null
                }

                val commandPacket = buildCommandPacket(cmdType, data)
                val writeSuccess = writeToPipe(pipePath, commandPacket)
                if (!writeSuccess) {
                    Log.e(TAG, "Failed to write command to pipe: $pipePath")
                    return null
                }

                val responseData = readFromPipe(pipePath)
                if (responseData == null) {
                    Log.e(TAG, "Failed to read response from pipe: $pipePath")
                    return null
                }

                parseResponseData(responseData)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command with data type=$cmdType", e)
                null
            } finally {
                if (pipePath != null) {
                    destroyPipe(pipePath)
                }
            }
        }
    }

    // ==================== 高级命令接口 ====================

    /**
     * 添加Hook目标
     *
     * @param type Hook类型 (0=PID, 1=PACKAGE)
     * @param identifier 标识符 (PID数字或包名)
     * @param uid 目标UID
     * @param mode Hook模式 (0=monitor, 1=intercept_read, 2=intercept_write, 3=intercept_all)
     * @return true=成功
     */
    fun addHook(type: Int, identifier: String, uid: Int, mode: Int): Boolean {
        val data = buildHookData(type, identifier, uid, mode)
        return sendCommand(CMD_ADD_HOOK, data)
    }

    /**
     * 移除Hook目标
     *
     * @param type Hook类型 (0=PID, 1=PACKAGE)
     * @param identifier 标识符
     * @return true=成功
     */
    fun removeHook(type: Int, identifier: String): Boolean {
        val data = buildRemoveHookData(type, identifier)
        return sendCommand(CMD_REMOVE_HOOK, data)
    }

    /**
     * 设置规则列表
     *
     * @param rules 规则列表 (格式: "action:path:mode")
     * @return true=成功
     */
    fun setRules(rules: List<String>): Boolean {
        val data = buildRulesData(rules)
        return sendCommand(CMD_SET_RULES, data)
    }

    /**
     * 清空所有规则
     *
     * @return true=成功
     */
    fun clearRules(): Boolean {
        return sendCommand(CMD_CLEAR_RULES)
    }

    /**
     * 设置策略
     *
     * @param enabled 是否启用VFS监控
     * @param logLevel 日志级别 (0-5)
     * @param defaultAction 默认动作 ("allow" 或 "deny")
     * @return true=成功
     */
    fun setPolicy(enabled: Boolean, logLevel: Int, defaultAction: String): Boolean {
        val data = buildPolicyData(enabled, logLevel, defaultAction)
        return sendCommand(CMD_SET_POLICY, data)
    }

    /**
     * 重置统计信息
     *
     * @return true=成功
     */
    fun resetStats(): Boolean {
        return sendCommand(CMD_RESET_STATS)
    }

    /**
     * 查询VFS模块状态
     *
     * @return 状态数据，失败返回null
     */
    fun queryStatus(): ByteArray? {
        return sendCommandWithData(CMD_QUERY_STATUS)
    }

    // ==================== 管道管理 ====================

    /**
     * 创建随机命名的FIFO管道
     *
     * @return 管道路径，失败返回null
     */
    fun createPipe(): String? {
        return try {
            val randomSuffix = generateRandomHex(8)
            val pipePath = "$PIPE_BASE$randomSuffix"

            // 使用root shell创建FIFO管道
            val result = Shell.cmd("mkfifo '$pipePath' 2>/dev/null && chmod 660 '$pipePath'").exec()
            if (result.isSuccess) {
                Log.d(TAG, "Created pipe: $pipePath")
                pipePath
            } else {
                Log.e(TAG, "Failed to create pipe $pipePath: ${result.err.joinToString()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception creating pipe", e)
            null
        }
    }

    /**
     * 销毁管道（unlink删除）
     *
     * @param path 管道路径
     */
    fun destroyPipe(path: String) {
        try {
            val result = Shell.cmd("rm -f '$path'").exec()
            if (result.isSuccess) {
                Log.d(TAG, "Destroyed pipe: $path")
            } else {
                Log.w(TAG, "Failed to destroy pipe $path: ${result.err.joinToString()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception destroying pipe $path", e)
        }
    }

    // ==================== 协议构建与解析 ====================

    /**
     * 构建命令数据包
     */
    private fun buildCommandPacket(cmdType: Int, data: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + data.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Header
        buffer.putInt(MAGIC)
        buffer.putInt(VERSION)
        buffer.putInt(cmdType)
        buffer.putInt(data.size)

        // Data payload
        if (data.isNotEmpty()) {
            buffer.put(data)
        }

        return buffer.array()
    }

    /**
     * 构建Hook数据
     */
    private fun buildHookData(type: Int, identifier: String, uid: Int, mode: Int): ByteArray {
        val identifierBytes = identifier.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + 4 + identifierBytes.size + 4 + 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(type)                          // Hook类型
        buffer.putInt(identifierBytes.size)           // 标识符长度
        buffer.put(identifierBytes)                  // 标识符
        buffer.putInt(uid)                            // UID
        buffer.putInt(mode)                           // Hook模式

        return buffer.array()
    }

    /**
     * 构建移除Hook数据
     */
    private fun buildRemoveHookData(type: Int, identifier: String): ByteArray {
        val identifierBytes = identifier.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + 4 + identifierBytes.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(type)                          // Hook类型
        buffer.putInt(identifierBytes.size)           // 标识符长度
        buffer.put(identifierBytes)                  // 标识符

        return buffer.array()
    }

    /**
     * 构建规则数据
     */
    private fun buildRulesData(rules: List<String>): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        try {
            dos.writeInt(rules.size)  // 规则数量 (little-endian via later conversion)
            for (rule in rules) {
                val ruleBytes = rule.toByteArray(Charsets.UTF_8)
                dos.writeInt(ruleBytes.size)
                dos.write(ruleBytes)
            }
        } finally {
            dos.close()
        }

        // ByteArrayOutputStream已经是大端序，需要转为小端序
        val raw = baos.toByteArray()
        return convertToLittleEndian(raw)
    }

    /**
     * 构建策略数据
     */
    private fun buildPolicyData(enabled: Boolean, logLevel: Int, defaultAction: String): ByteArray {
        val actionBytes = defaultAction.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(4 + 4 + 4 + actionBytes.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(if (enabled) 1 else 0)        // 启用标志
        buffer.putInt(logLevel)                        // 日志级别
        buffer.putInt(actionBytes.size)               // 默认动作字符串长度
        buffer.put(actionBytes)                       // 默认动作

        return buffer.array()
    }

    /**
     * 解析响应（仅检查状态）
     */
    private fun parseResponse(data: ByteArray): Boolean {
        if (data.size < HEADER_SIZE) {
            Log.e(TAG, "Response too short: ${data.size} bytes")
            return false
        }

        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val magic = buffer.getInt()
        val version = buffer.getInt()
        val status = buffer.getInt()

        if (magic != MAGIC) {
            Log.e(TAG, "Invalid response magic: 0x${magic.toString(16)}")
            return false
        }

        if (version != VERSION) {
            Log.e(TAG, "Unsupported response version: $version")
            return false
        }

        if (status != RESP_SUCCESS) {
            Log.e(TAG, "Command failed with status: $status (${statusToString(status)})")
            return false
        }

        return true
    }

    /**
     * 解析响应并返回数据部分
     */
    private fun parseResponseData(data: ByteArray): ByteArray? {
        if (data.size < HEADER_SIZE) {
            Log.e(TAG, "Response too short: ${data.size} bytes")
            return null
        }

        val buffer = ByteBuffer.wrap(data)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val magic = buffer.getInt()
        val version = buffer.getInt()
        val status = buffer.getInt()
        val respLen = buffer.getInt()

        if (magic != MAGIC) {
            Log.e(TAG, "Invalid response magic: 0x${magic.toString(16)}")
            return null
        }

        if (version != VERSION) {
            Log.e(TAG, "Unsupported response version: $version")
            return null
        }

        if (status != RESP_SUCCESS) {
            Log.e(TAG, "Command failed with status: $status (${statusToString(status)})")
            return null
        }

        if (respLen <= 0 || data.size < HEADER_SIZE + respLen) {
            return ByteArray(0)
        }

        val responseData = ByteArray(respLen)
        buffer.get(responseData)
        return responseData
    }

    /**
     * 将大端序的Int数组转为小端序
     */
    private fun convertToLittleEndian(data: ByteArray): ByteArray {
        val result = ByteArray(data.size)
        for (i in data.indices step 4) {
            if (i + 3 < data.size) {
                result[i] = data[i + 3]
                result[i + 1] = data[i + 2]
                result[i + 2] = data[i + 1]
                result[i + 3] = data[i]
            } else {
                // 不足4字节的尾部直接复制
                System.arraycopy(data, i, result, i, data.size - i)
            }
        }
        return result
    }

    // ==================== 管道读写 ====================

    /**
     * 通过root shell将数据写入管道
     */
    private fun writeToPipe(pipePath: String, data: ByteArray): Boolean {
        return try {
            // 将数据转为base64以安全传输
            val base64Data = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)

            // 使用root shell写入管道
            // 先在后台打开管道读取端（防止写入阻塞），然后写入数据
            val script = """
                # 确保管道存在
                if [ ! -p '$pipePath' ]; then
                    echo "ERROR: Pipe not found"
                    exit 1
                fi
                
                # 将base64数据解码并写入管道
                echo '$base64Data' | base64 -d > '$pipePath' &
                WRITE_PID=${'$'}!
                
                # 等待写入完成或超时
                timeout ${PIPE_TIMEOUT_MS}ms wait ${'$'}WRITE_PID 2>/dev/null
                exit 0
            """.trimIndent()

            val result = Shell.cmd(script).exec()
            if (result.isSuccess) {
                Log.d(TAG, "Successfully wrote ${data.size} bytes to pipe")
                true
            } else {
                Log.e(TAG, "Failed to write to pipe: ${result.err.joinToString()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception writing to pipe $pipePath", e)
            false
        }
    }

    /**
     * 通过root shell从管道读取响应
     */
    private fun readFromPipe(pipePath: String): ByteArray? {
        return try {
            // 使用root shell读取管道并base64编码返回
            val script = """
                if [ ! -p '$pipePath' ]; then
                    echo "ERROR: Pipe not found"
                    exit 1
                fi
                
                # 读取管道数据并编码为base64
                timeout ${PIPE_TIMEOUT_MS}ms cat '$pipePath' 2>/dev/null | base64 -w 0
                exit 0
            """.trimIndent()

            val result = Shell.cmd(script).exec()
            if (result.isSuccess && result.out.isNotEmpty()) {
                val base64Output = result.out.first().trim()
                if (base64Output.isNotEmpty() && base64Output != "ERROR: Pipe not found") {
                    val decoded = android.util.Base64.decode(base64Output, android.util.Base64.DEFAULT)
                    Log.d(TAG, "Read ${decoded.size} bytes from pipe")
                    decoded
                } else {
                    null
                }
            } else {
                Log.e(TAG, "Failed to read from pipe: ${result.err.joinToString()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception reading from pipe $pipePath", e)
            null
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 生成指定长度的随机hex字符串
     */
    private fun generateRandomHex(length: Int): String {
        val random = java.security.SecureRandom()
        val bytes = ByteArray(length / 2 + 1)
        random.nextBytes(bytes)
        return bytes.take(length / 2).joinToString("") { "%02x".format(it) }
    }

    /**
     * 状态码转字符串
     */
    private fun statusToString(status: Int): String {
        return when (status) {
            RESP_SUCCESS -> "SUCCESS"
            RESP_ERR_UNKNOWN -> "UNKNOWN_ERROR"
            RESP_ERR_INVALID_CMD -> "INVALID_COMMAND"
            RESP_ERR_PIPE -> "PIPE_ERROR"
            RESP_ERR_TIMEOUT -> "TIMEOUT"
            RESP_ERR_PERMISSION -> "PERMISSION_DENIED"
            else -> "UNKNOWN($status)"
        }
    }

    /**
     * 检查Pipe通讯是否可用
     */
    fun isAvailable(): Boolean {
        return try {
            val result = Shell.cmd("ls -la ${PIPE_BASE}* 2>/dev/null; echo 'PIPE_CHECK_OK'").exec()
            // 检查/dev/目录是否可访问
            val devCheck = Shell.cmd("test -d /dev && echo 'DEV_OK' || echo 'DEV_FAIL'").exec()
            devCheck.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Pipe availability check failed", e)
            false
        }
    }

    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("VFSPipeComm Debug Info:")
            appendLine("  Magic: 0x${MAGIC.toString(16)}")
            appendLine("  Version: $VERSION")
            appendLine("  Pipe Base: $PIPE_BASE")
            appendLine("  Timeout: ${PIPE_TIMEOUT_MS}ms")
            appendLine("  Available: ${isAvailable()}")
        }
    }
}
