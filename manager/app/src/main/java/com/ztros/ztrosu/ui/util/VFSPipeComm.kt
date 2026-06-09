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
 * 结构化规则数据，用于与内核VFS模块的二进制协议通讯。
 *
 * @param action   动作类型: 0=allow, 1=deny
 * @param path     路径模式字符串
 * @param modeMask 模式掩码: bit0=read, bit1=write
 */
data class PipeRuleData(
    val action: Int,    // 0=allow, 1=deny
    val path: String,
    val modeMask: Int   // bit0=read, bit1=write
)

/**
 * VFS Misc Device 通讯管理器
 *
 * 通过内核 misc char device (/dev/aurora_vfs) 与内核VFS模块进行二进制协议通讯。
 * 使用SuFile和Shell进行root操作（/dev/需要root权限）。
 *
 * 命令协议（与内核约定，little-endian）：
 *   Magic   : UInt32 (0xAF5F)
 *   Version : UInt32 (2)
 *   CmdType : UInt32
 *   CmdLen  : UInt32
 *   Data    : ByteArray
 *
 * 注意: 内核 misc device 不支持读取（pipe_read 返回 -EINVAL），
 *       因此仅支持写入命令，不支持读取响应。
 */
object VFSPipeComm {

    // ==================== 协议常量 ====================

    const val MAGIC: Int = 0xAF5F
    const val VERSION = 2
    const val DEVICE_PATH = "/dev/aurora_vfs"
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
     * 直接写入 /dev/aurora_vfs misc device，无需创建/销毁管道。
     * 内核不支持读取响应，因此仅返回写入是否成功。
     *
     * @param cmdType 命令类型 (CMD_ADD_HOOK, CMD_REMOVE_HOOK, etc.)
     * @param data 命令数据负载
     * @return true=写入成功, false=失败
     */
    fun sendCommand(cmdType: Int, data: ByteArray = ByteArray(0)): Boolean {
        commLock.withLock {
            return try {
                // 1. 构建命令包
                val commandPacket = buildCommandPacket(cmdType, data)

                // 2. 直接写入 misc device
                val writeSuccess = writeToDevice(commandPacket)
                if (!writeSuccess) {
                    Log.e(TAG, "Failed to write command to $DEVICE_PATH")
                    return false
                }

                // 3. 内核 misc device 不支持读取，写入成功即视为命令成功
                Log.i(TAG, "Command type=$cmdType sent successfully (${commandPacket.size} bytes)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command type=$cmdType", e)
                false
            }
        }
    }

    /**
     * 发送命令并获取响应数据
     *
     * 注意: 内核 misc device 不支持读取（pipe_read 返回 -EINVAL），
     *       因此此方法始终返回 null。保留此接口以兼容调用方。
     *
     * @param cmdType 命令类型
     * @param data 命令数据负载
     * @return 始终返回 null（内核不支持读取响应）
     */
    fun sendCommandWithData(cmdType: Int, data: ByteArray = ByteArray(0)): ByteArray? {
        commLock.withLock {
            return try {
                val commandPacket = buildCommandPacket(cmdType, data)
                val writeSuccess = writeToDevice(commandPacket)
                if (!writeSuccess) {
                    Log.e(TAG, "Failed to write command to $DEVICE_PATH")
                    return null
                }
                Log.i(TAG, "Command type=$cmdType sent successfully (${commandPacket.size} bytes)")
                // 内核 misc device 不支持读取响应
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error sending command with data type=$cmdType", e)
                null
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
     * 设置规则列表（结构化版本）
     *
     * @param rules 结构化规则数据列表
     * @return true=成功
     */
    fun setStructuredRules(rules: List<PipeRuleData>): Boolean {
        val data = buildRulesData(rules)
        return sendCommand(CMD_SET_RULES, data)
    }

    /**
     * 设置规则列表（字符串兼容版本）
     *
     * @param rules 规则列表 (格式: "action:path:mode"，如 "allow:/data:r", "deny:/tmp:rw")
     * @return true=成功
     */
    fun setRules(rules: List<String>): Boolean {
        val data = buildRulesDataFromStrings(rules)
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
     * 设置策略（结构化版本）
     *
     * @param enabled 是否启用VFS监控
     * @param logLevel 日志级别 (0-5)
     * @param defaultAction 默认动作 (0=allow, 1=deny)
     * @return true=成功
     */
    fun setPolicy(enabled: Boolean, logLevel: Int, defaultAction: Int): Boolean {
        val data = buildPolicyData(enabled, logLevel, defaultAction)
        return sendCommand(CMD_SET_POLICY, data)
    }

    /**
     * 设置策略（字符串兼容版本）
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

    // ==================== 设备读写 ====================

    /**
     * 通过root shell将二进制数据直接写入 /dev/aurora_vfs misc device
     */
    private fun writeToDevice(data: ByteArray): Boolean {
        return try {
            // 将数据转为base64以安全传输
            val base64Data = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)

            // 使用root shell将base64数据解码后写入misc device
            val script = """
                # 确保misc device存在
                if [ ! -e '$DEVICE_PATH' ]; then
                    echo "ERROR: Device not found: $DEVICE_PATH"
                    exit 1
                fi

                # 将base64数据解码并写入misc device
                echo '$base64Data' | base64 -d > '$DEVICE_PATH'
                exit ${'$'}?
            """.trimIndent()

            val result = Shell.cmd(script).exec()
            if (result.isSuccess) {
                Log.d(TAG, "Successfully wrote ${data.size} bytes to $DEVICE_PATH")
                true
            } else {
                Log.e(TAG, "Failed to write to $DEVICE_PATH: ${result.err.joinToString()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception writing to $DEVICE_PATH", e)
            false
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
     *
     * 内核格式 (cmd_add_hook):
     *   __u8  hook_type        // 0=PID, 1=PACKAGE
     *   __u32 identifier_len    // identifier字符串长度 (不含\0)
     *   char  identifier[]      // PID字符串或包名 (变长)
     *   __u32 uid               // 目标UID
     *   __u8  hook_mode         // 0=MONITOR_ONLY, 1=INTERCEPT_READ,
     *                          // 2=INTERCEPT_WRITE, 3=INTERCEPT_ALL
     */
    private fun buildHookData(type: Int, identifier: String, uid: Int, mode: Int): ByteArray {
        val identifierBytes = identifier.toByteArray(Charsets.UTF_8)
        // __u8(1) + __u32(4) + identifier(var) + __u32(4) + __u8(1)
        val buffer = ByteBuffer.allocate(1 + 4 + identifierBytes.size + 4 + 1)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(type.toByte())                   // hook_type: __u8
        buffer.putInt(identifierBytes.size)          // identifier_len: __u32
        buffer.put(identifierBytes)                  // identifier: char[]
        buffer.putInt(uid)                            // uid: __u32
        buffer.put(mode.toByte())                     // hook_mode: __u8

        return buffer.array()
    }

    /**
     * 构建移除Hook数据
     *
     * 内核格式 (cmd_remove_hook):
     *   __u8  hook_type        // 0=PID, 1=PACKAGE
     *   __u32 identifier_len    // identifier字符串长度
     *   char  identifier[]      // PID字符串或包名
     */
    private fun buildRemoveHookData(type: Int, identifier: String): ByteArray {
        val identifierBytes = identifier.toByteArray(Charsets.UTF_8)
        // __u8(1) + __u32(4) + identifier(var)
        val buffer = ByteBuffer.allocate(1 + 4 + identifierBytes.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(type.toByte())                   // hook_type: __u8
        buffer.putInt(identifierBytes.size)          // identifier_len: __u32
        buffer.put(identifierBytes)                  // identifier: char[]

        return buffer.array()
    }

    /**
     * 构建规则数据（结构化版本）
     *
     * 内核格式 (cmd_set_rules):
     *   __u32 rule_count
     *   每条规则:
     *     __u8  action       // 0=allow, 1=deny
     *     __u32 path_len     // 路径长度
     *     char  path[]       // 路径模式 (变长)
     *     __u8  mode_mask    // bit0=read, bit1=write
     */
    private fun buildRulesData(rules: List<PipeRuleData>): ByteArray {
        // 计算总大小: __u32(rule_count) + 每条规则(__u8 + __u32 + path + __u8)
        var totalSize = 4 // rule_count: __u32
        for (rule in rules) {
            val pathBytes = rule.path.toByteArray(Charsets.UTF_8)
            totalSize += 1 + 4 + pathBytes.size + 1 // action(u8) + path_len(u32) + path(var) + mode_mask(u8)
        }

        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(rules.size)  // rule_count: __u32

        for (rule in rules) {
            val pathBytes = rule.path.toByteArray(Charsets.UTF_8)
            buffer.put(rule.action.toByte())          // action: __u8
            buffer.putInt(pathBytes.size)             // path_len: __u32
            buffer.put(pathBytes)                      // path: char[]
            buffer.put(rule.modeMask.toByte())         // mode_mask: __u8
        }

        return buffer.array()
    }

    /**
     * 构建规则数据（字符串解析兼容版本）
     *
     * 解析格式: "action:path:mode"
     *   action: "allow" 或 "deny"
     *   path: 路径模式
     *   mode: "r"=read, "w"=write, "rw"=both
     */
    private fun buildRulesDataFromStrings(rules: List<String>): ByteArray {
        val parsedRules = rules.mapNotNull { ruleStr ->
            val parts = ruleStr.split(":", limit = 3)
            if (parts.size != 3) {
                Log.w(TAG, "Skipping malformed rule: $ruleStr")
                return@mapNotNull null
            }
            val action = when (parts[0].lowercase()) {
                "allow" -> 0
                "deny" -> 1
                else -> {
                    Log.w(TAG, "Unknown action in rule: $ruleStr")
                    return@mapNotNull null
                }
            }
            val path = parts[1]
            val modeMask = when (parts[2].lowercase()) {
                "r" -> 0x01  // bit0=read
                "w" -> 0x02  // bit1=write
                "rw" -> 0x03 // bit0=read + bit1=write
                else -> {
                    Log.w(TAG, "Unknown mode in rule: $ruleStr")
                    return@mapNotNull null
                }
            }
            PipeRuleData(action, path, modeMask)
        }
        return buildRulesData(parsedRules)
    }

    /**
     * 构建策略数据
     *
     * 内核格式 (cmd_set_policy):
     *   __u8  enabled          // 0或1
     *   __u8  log_level        // 0-5
     *   __u8  default_action   // 0=allow, 1=deny
     *   __u8  reserved         // 对齐填充
     */
    private fun buildPolicyData(enabled: Boolean, logLevel: Int, defaultAction: Int): ByteArray {
        val buffer = ByteBuffer.allocate(4) // 4 x __u8
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(if (enabled) 1 else 0.toByte())   // enabled: __u8
        buffer.put(logLevel.toByte())                   // log_level: __u8
        buffer.put(defaultAction.toByte())              // default_action: __u8
        buffer.put(0.toByte())                          // reserved: __u8

        return buffer.array()
    }

    /**
     * 构建策略数据（字符串兼容版本）
     *
     * @param enabled 是否启用VFS监控
     * @param logLevel 日志级别 (0-5)
     * @param defaultAction 默认动作 ("allow" 或 "deny")
     */
    private fun buildPolicyData(enabled: Boolean, logLevel: Int, defaultAction: String): ByteArray {
        val actionInt = when (defaultAction.lowercase()) {
            "allow" -> 0
            "deny" -> 1
            else -> {
                Log.w(TAG, "Unknown default action: $defaultAction, defaulting to allow")
                0
            }
        }
        return buildPolicyData(enabled, logLevel, actionInt)
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

    // ==================== 工具方法 ====================

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
     * 检查 misc device 是否可用
     * 检查 /dev/aurora_vfs 是否存在
     */
    fun isAvailable(): Boolean {
        return try {
            val result = Shell.cmd("test -e '$DEVICE_PATH' && echo 'DEVICE_OK' || echo 'DEVICE_FAIL'").exec()
            val available = result.isSuccess && result.out.any { it.contains("DEVICE_OK") }
            if (available) {
                Log.d(TAG, "Misc device $DEVICE_PATH is available")
            } else {
                Log.d(TAG, "Misc device $DEVICE_PATH is not available")
            }
            available
        } catch (e: Exception) {
            Log.e(TAG, "Device availability check failed", e)
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
            appendLine("  Device Path: $DEVICE_PATH")
            appendLine("  Timeout: ${PIPE_TIMEOUT_MS}ms")
            appendLine("  Available: ${isAvailable()}")
        }
    }
}
