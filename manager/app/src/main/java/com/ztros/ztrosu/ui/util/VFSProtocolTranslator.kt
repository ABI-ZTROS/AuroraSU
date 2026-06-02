@file:Suppress("TooManyFunctions")

package com.ztros.ztrosu.ui.util

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * VFS 内核模块协议双向翻译器。
 *
 * 作为"调试桥"使用：当用户以字符串模式手动操作时，将人类可读格式翻译为内核所需的二进制协议格式；
 * 当内核返回二进制数据时，翻译为人类可读的字符串以便用户查看配置。
 *
 * 二进制协议参考：VFS_KERNEL_MODULE_SPEC.md v3.0
 */
object VFSProtocolTranslator {

    private const val TAG = "VFSProtocolTranslator"

    // ==================== 常量 ====================

    /** 事件魔数 */
    const val EVENT_MAGIC: Int = 0xAF5F

    /** 允许动作 */
    const val ACTION_ALLOW: Byte = 0x00

    /** 拒绝动作 */
    const val ACTION_DENY: Byte = 0x01

    /** 模式掩码：读 */
    const val MODE_READ: Byte = 0x01

    /** 模式掩码：写 */
    const val MODE_WRITE: Byte = 0x02

    /** Hook 类型：PID */
    const val HOOK_TYPE_PID: Byte = 0x00

    /** Hook 类型：PACKAGE */
    const val HOOK_TYPE_PACKAGE: Byte = 0x01

    /** Hook 模式：仅监控 */
    const val HOOK_MODE_MONITOR_ONLY: Byte = 0x00

    /** Hook 模式：拦截读 */
    const val HOOK_MODE_INTERCEPT_READ: Byte = 0x01

    /** Hook 模式：拦截写 */
    const val HOOK_MODE_INTERCEPT_WRITE: Byte = 0x02

    /** Hook 模式：拦截全部 */
    const val HOOK_MODE_INTERCEPT_ALL: Byte = 0x03

    /** 策略二进制格式长度（4 字节） */
    const val POLICY_BINARY_SIZE: Int = 4

    /** 事件头部固定长度（magic + event_type + pid + uid + path_len = 5 * 4 = 20 字节） */
    private const val EVENT_HEADER_SIZE = 20

    /** 事件尾部固定长度（timestamp 8 字节 + result 4 字节 = 12 字节） */
    private const val EVENT_TRAILER_SIZE = 12

    // ==================== 数据类 ====================

    /**
     * Hook 列表条目。
     *
     * @property type Hook 类型（"PID" 或 "PACKAGE"）
     * @property identifier 标识符（PID 数字或包名）
     * @property uid 关联的 UID
     * @property mode Hook 模式（"MONITOR_ONLY"、"INTERCEPT_READ"、"INTERCEPT_WRITE"、"INTERCEPT_ALL"）
     * @property enabled 是否启用
     */
    data class HookEntry(
        val type: String,
        val identifier: String,
        val uid: Int,
        val mode: String,
        val enabled: Boolean
    )

    /**
     * 验证结果。
     *
     * @property valid 是否通过验证
     * @property error 错误信息（验证通过时为 null）
     * @property parsedFields 解析出的字段映射（验证通过时可用）
     */
    data class ValidationResult(
        val valid: Boolean,
        val error: String? = null,
        val parsedFields: Map<String, String>? = null
    )

    // ==================== 规则翻译 ====================

    /**
     * 将单条规则字符串转换为二进制格式。
     *
     * 字符串格式："action:path_pattern:mode"，例如 "deny:/system/**:rw"、"allow:/sdcard/:r"
     *
     * 二进制格式：
     * - `__u8 action`（0=allow, 1=deny）
     * - `__u32 path_len`（小端序）
     * - `char path[]`（UTF-8 字节，path_len 长度）
     * - `__u8 mode_mask`（bit0=read, bit1=write）
     *
     * @param ruleString 规则字符串
     * @return 二进制字节数组，解析失败返回 null
     */
    fun ruleToBinary(ruleString: String): ByteArray? {
        val parts = ruleString.split(":", limit = 3)
        if (parts.size != 3) {
            Log.w(TAG, "ruleToBinary: 格式无效，期望 'action:path:mode'，实际: $ruleString")
            return null
        }

        val actionStr = parts[0].lowercase()
        val path = parts[1]
        val modeStr = parts[2].lowercase()

        val action: Byte = when (actionStr) {
            "allow" -> ACTION_ALLOW
            "deny" -> ACTION_DENY
            else -> {
                Log.w(TAG, "ruleToBinary: 未知动作 '$actionStr'")
                return null
            }
        }

        val modeMask = parseModeMask(modeStr)
        if (modeMask < 0) {
            Log.w(TAG, "ruleToBinary: 无效模式 '$modeStr'")
            return null
        }

        val pathBytes = path.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(1 + 4 + pathBytes.size + 1)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(action)
        buffer.putInt(pathBytes.size)
        buffer.put(pathBytes)
        buffer.put(modeMask.toByte())

        return buffer.array()
    }

    /**
     * 将单条规则二进制数据转换为字符串格式。
     *
     * @param data 规则二进制数据
     * @return 规则字符串，解析失败返回 null
     */
    fun ruleToString(data: ByteArray): String? {
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            val action = buffer.get()
            val pathLen = buffer.getInt()

            if (pathLen < 0 || pathLen > data.size - 6) {
                Log.w(TAG, "ruleToString: path_len=$pathLen 超出数据范围，数据总长=${data.size}")
                return null
            }

            val pathBytes = ByteArray(pathLen)
            buffer.get(pathBytes)
            val path = String(pathBytes, Charsets.UTF_8)

            val modeMask = buffer.get().toInt()

            val actionStr = when (action) {
                ACTION_ALLOW -> "allow"
                ACTION_DENY -> "deny"
                else -> {
                    Log.w(TAG, "ruleToString: 未知动作值 $action")
                    return null
                }
            }

            val modeStr = modeMaskToString(modeMask)

            "$actionStr:$path:$modeStr"
        } catch (e: Exception) {
            Log.w(TAG, "ruleToString: 解析失败", e)
            null
        }
    }

    /**
     * 将多条规则字符串批量转换为二进制数据包。
     *
     * 数据包格式：
     * - `__u32 rule_count`（小端序，规则数量）
     * - 后续为各条规则的二进制数据（依次拼接）
     *
     * @param ruleStrings 规则字符串列表
     * @return 二进制数据包，任一规则解析失败返回 null
     */
    fun rulesToBinaryPacket(ruleStrings: List<String>): ByteArray? {
        val binaries = mutableListOf<ByteArray>()
        for (ruleString in ruleStrings) {
            val binary = ruleToBinary(ruleString) ?: run {
                Log.w(TAG, "rulesToBinaryPacket: 规则解析失败: $ruleString")
                return null
            }
            binaries.add(binary)
        }

        val totalSize = 4 + binaries.sumOf { it.size }
        val buffer = ByteBuffer.allocate(totalSize)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(binaries.size)
        for (binary in binaries) {
            buffer.put(binary)
        }

        return buffer.array()
    }

    /**
     * 将二进制数据包解析为规则字符串列表。
     *
     * @param data 包含 rule_count 头部的二进制数据包
     * @return 规则字符串列表，解析失败时返回尽可能多的规则
     */
    fun binaryPacketToRules(data: ByteArray): List<String> {
        val rules = mutableListOf<String>()
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val ruleCount = buffer.getInt()

            if (ruleCount < 0 || ruleCount > 10000) {
                Log.w(TAG, "binaryPacketToRules: 不合理的规则数量 $ruleCount")
                return rules
            }

            for (i in 0 until ruleCount) {
                if (!buffer.hasRemaining()) {
                    Log.w(TAG, "binaryPacketToRules: 数据在第 $i 条规则处提前结束")
                    break
                }

                val action = buffer.get()
                if (!buffer.hasRemaining()) {
                    Log.w(TAG, "binaryPacketToRules: 第 $i 条规则缺少 path_len")
                    break
                }
                val pathLen = buffer.getInt()

                if (pathLen < 0 || pathLen > buffer.remaining() - 1) {
                    Log.w(TAG, "binaryPacketToRules: 第 $i 条规则 path_len=$pathLen 超出剩余数据")
                    break
                }

                val pathBytes = ByteArray(pathLen)
                buffer.get(pathBytes)
                val path = String(pathBytes, Charsets.UTF_8)

                if (!buffer.hasRemaining()) {
                    Log.w(TAG, "binaryPacketToRules: 第 $i 条规则缺少 mode_mask")
                    break
                }
                val modeMask = buffer.get().toInt()

                val actionStr = when (action) {
                    ACTION_ALLOW -> "allow"
                    ACTION_DENY -> "deny"
                    else -> {
                        Log.w(TAG, "binaryPacketToRules: 第 $i 条规则未知动作 $action")
                        break
                    }
                }

                rules.add("$actionStr:$path:${modeMaskToString(modeMask)}")
            }

            rules
        } catch (e: Exception) {
            Log.w(TAG, "binaryPacketToRules: 解析异常", e)
            rules
        }
    }

    // ==================== 策略翻译 ====================

    /**
     * 将策略参数转换为 4 字节二进制格式。
     *
     * 二进制格式（CMD_SET_POLICY）：
     * - `__u8 enabled`（0 或 1）
     * - `__u8 log_level`（0-5）
     * - `__u8 default_action`（0=allow, 1=deny）
     * - `__u8 reserved`（0）
     *
     * @param enabled 是否启用策略
     * @param logLevel 日志级别（0-5）
     * @param defaultAction 默认动作（"allow" 或 "deny"）
     * @return 4 字节二进制数据
     */
    fun policyToBinary(enabled: Boolean, logLevel: Int, defaultAction: String): ByteArray {
        val buffer = ByteBuffer.allocate(POLICY_BINARY_SIZE)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        buffer.put(if (enabled) 1 else 0)
        buffer.put(logLevel.coerceIn(0, 5).toByte())

        val actionByte: Byte = when (defaultAction.lowercase()) {
            "allow" -> ACTION_ALLOW
            "deny" -> ACTION_DENY
            else -> {
                Log.w(TAG, "policyToBinary: 未知默认动作 '$defaultAction'，使用 deny")
                ACTION_DENY
            }
        }
        buffer.put(actionByte)
        buffer.put(0) // reserved

        return buffer.array()
    }

    /**
     * 将策略二进制数据转换为人类可读的多行字符串。
     *
     * @param data 4 字节策略二进制数据
     * @return 多行字符串，例如：
     * ```
     * enabled: 1
     * log_level: 3
     * default_action: deny
     * ```
     */
    fun policyToString(data: ByteArray): String {
        return try {
            val map = policyToMap(data)
            val sb = StringBuilder()
            sb.appendLine("enabled: ${if (map["enabled"] as Boolean) 1 else 0}")
            sb.appendLine("log_level: ${map["log_level"]}")
            sb.append("default_action: ${map["default_action"]}")
            sb.toString()
        } catch (e: Exception) {
            Log.w(TAG, "policyToString: 解析失败", e)
            "enabled: 0\nlog_level: 0\ndefault_action: deny"
        }
    }

    /**
     * 将策略二进制数据转换为结构化 Map。
     *
     * @param data 4 字节策略二进制数据
     * @return 包含 "enabled"（Boolean）、"log_level"（Int）、"default_action"（String）的 Map
     */
    fun policyToMap(data: ByteArray): Map<String, Any> {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        val enabled = buffer.get().toInt() != 0
        val logLevel = buffer.get().toInt() and 0xFF
        val defaultActionByte = buffer.get()
        // reserved byte
        // buffer.get()

        val defaultAction = when (defaultActionByte) {
            ACTION_ALLOW -> "allow"
            ACTION_DENY -> "deny"
            else -> {
                Log.w(TAG, "policyToMap: 未知默认动作值 $defaultActionByte，使用 deny")
                "deny"
            }
        }

        return mapOf(
            "enabled" to enabled,
            "log_level" to logLevel,
            "default_action" to defaultAction
        )
    }

    // ==================== Hook 翻译 ====================

    /**
     * 将 Hook 命令字符串转换为二进制格式。
     *
     * 支持的字符串格式：
     * - 添加：`"add:PID:12345:10086:MONITOR_ONLY"` 或 `"add:PACKAGE:com.example.app:10086:INTERCEPT_ALL"`
     * - 移除：`"remove:PID:12345"` 或 `"remove:PACKAGE:com.example.app"`
     *
     * 添加命令的二进制格式（CMD_ADD_HOOK）：
     * - `__u8 hook_type`（0=PID, 1=PACKAGE）
     * - `__u32 identifier_len`（小端序）
     * - `char identifier[]`（UTF-8 字节）
     * - `__u32 uid`（小端序）
     * - `__u8 hook_mode`（0=MONITOR_ONLY, 1=INTERCEPT_READ, 2=INTERCEPT_WRITE, 3=INTERCEPT_ALL）
     *
     * 移除命令的二进制格式：
     * - `__u8 hook_type`（0=PID, 1=PACKAGE）
     * - `__u32 identifier_len`（小端序）
     * - `char identifier[]`（UTF-8 字节）
     *
     * @param command Hook 命令字符串
     * @return 二进制字节数组，解析失败返回 null
     */
    fun hookCommandToBinary(command: String): ByteArray? {
        val parts = command.split(":", limit = 5)
        if (parts.isEmpty()) {
            Log.w(TAG, "hookCommandToBinary: 空命令")
            return null
        }

        return when (parts[0].lowercase()) {
            "add" -> {
                if (parts.size != 5) {
                    Log.w(TAG, "hookCommandToBinary: add 命令格式无效，期望 'add:type:id:uid:mode'，实际: $command")
                    return null
                }

                val typeStr = parts[1].uppercase()
                val identifier = parts[2]
                val uidStr = parts[3]
                val modeStr = parts[4].uppercase()

                val hookType: Byte = when (typeStr) {
                    "PID" -> HOOK_TYPE_PID
                    "PACKAGE" -> HOOK_TYPE_PACKAGE
                    else -> {
                        Log.w(TAG, "hookCommandToBinary: 未知 Hook 类型 '$typeStr'")
                        return null
                    }
                }

                val uid = uidStr.toIntOrNull()
                if (uid == null || uid < 0) {
                    Log.w(TAG, "hookCommandToBinary: 无效 UID '$uidStr'")
                    return null
                }

                val hookMode: Byte = when (modeStr) {
                    "MONITOR_ONLY" -> HOOK_MODE_MONITOR_ONLY
                    "INTERCEPT_READ" -> HOOK_MODE_INTERCEPT_READ
                    "INTERCEPT_WRITE" -> HOOK_MODE_INTERCEPT_WRITE
                    "INTERCEPT_ALL" -> HOOK_MODE_INTERCEPT_ALL
                    else -> {
                        Log.w(TAG, "hookCommandToBinary: 未知 Hook 模式 '$modeStr'")
                        return null
                    }
                }

                val idBytes = identifier.toByteArray(Charsets.UTF_8)
                val buffer = ByteBuffer.allocate(1 + 4 + idBytes.size + 4 + 1)
                buffer.order(ByteOrder.LITTLE_ENDIAN)

                buffer.put(hookType)
                buffer.putInt(idBytes.size)
                buffer.put(idBytes)
                buffer.putInt(uid)
                buffer.put(hookMode)

                buffer.array()
            }

            "remove" -> {
                if (parts.size != 3) {
                    Log.w(TAG, "hookCommandToBinary: remove 命令格式无效，期望 'remove:type:id'，实际: $command")
                    return null
                }

                val typeStr = parts[1].uppercase()
                val identifier = parts[2]

                val hookType: Byte = when (typeStr) {
                    "PID" -> HOOK_TYPE_PID
                    "PACKAGE" -> HOOK_TYPE_PACKAGE
                    else -> {
                        Log.w(TAG, "hookCommandToBinary: 未知 Hook 类型 '$typeStr'")
                        return null
                    }
                }

                val idBytes = identifier.toByteArray(Charsets.UTF_8)
                val buffer = ByteBuffer.allocate(1 + 4 + idBytes.size)
                buffer.order(ByteOrder.LITTLE_ENDIAN)

                buffer.put(hookType)
                buffer.putInt(idBytes.size)
                buffer.put(idBytes)

                buffer.array()
            }

            else -> {
                Log.w(TAG, "hookCommandToBinary: 未知命令操作 '${parts[0]}'")
                null
            }
        }
    }

    /**
     * 将 Hook 二进制数据转换为命令字符串。
     *
     * 根据数据长度自动判断是 add 命令（包含 uid 和 hook_mode）还是 remove 命令。
     *
     * @param data Hook 二进制数据
     * @return 命令字符串，解析失败返回 null
     */
    fun hookBinaryToCommand(data: ByteArray): String? {
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            val hookType = buffer.get()
            val idLen = buffer.getInt()

            if (idLen < 0 || idLen > buffer.remaining()) {
                Log.w(TAG, "hookBinaryToCommand: identifier_len=$idLen 超出数据范围")
                return null
            }

            val idBytes = ByteArray(idLen)
            buffer.get(idBytes)
            val identifier = String(idBytes, Charsets.UTF_8)

            val typeStr = when (hookType) {
                HOOK_TYPE_PID -> "PID"
                HOOK_TYPE_PACKAGE -> "PACKAGE"
                else -> {
                    Log.w(TAG, "hookBinaryToCommand: 未知 Hook 类型 $hookType")
                    return null
                }
            }

            // 如果还有 uid (4字节) + hook_mode (1字节) 的剩余数据，则为 add 命令
            if (buffer.remaining() >= 5) {
                val uid = buffer.getInt()
                val hookMode = buffer.get()
                val modeStr = hookModeToString(hookMode.toInt())
                "add:$typeStr:$identifier:$uid:$modeStr"
            } else {
                "remove:$typeStr:$identifier"
            }
        } catch (e: Exception) {
            Log.w(TAG, "hookBinaryToCommand: 解析失败", e)
            null
        }
    }

    /**
     * 解析 Hook 列表条目字符串。
     *
     * 字符串格式（来自内核）：
     * - `"PID:12345:10086:MONITOR_ONLY:1"` 或 `"PACKAGE:com.example.app:10086:INTERCEPT_ALL:1"`
     *
     * @param line Hook 列表条目字符串
     * @return 解析后的 [HookEntry]，失败返回 null
     */
    fun parseHookListEntry(line: String): HookEntry? {
        val parts = line.split(":", limit = 5)
        if (parts.size != 5) {
            Log.w(TAG, "parseHookListEntry: 格式无效，期望 'type:id:uid:mode:enabled'，实际: $line")
            return null
        }

        val type = parts[0].uppercase()
        if (type != "PID" && type != "PACKAGE") {
            Log.w(TAG, "parseHookListEntry: 未知类型 '$type'")
            return null
        }

        val identifier = parts[1]

        val uid = parts[2].toIntOrNull()
        if (uid == null || uid < 0) {
            Log.w(TAG, "parseHookListEntry: 无效 UID '${parts[2]}'")
            return null
        }

        val mode = parts[3].uppercase()
        if (mode !in listOf("MONITOR_ONLY", "INTERCEPT_READ", "INTERCEPT_WRITE", "INTERCEPT_ALL")) {
            Log.w(TAG, "parseHookListEntry: 未知模式 '$mode'")
            return null
        }

        val enabled = when (parts[4].trim()) {
            "1" -> true
            "0" -> false
            else -> {
                Log.w(TAG, "parseHookListEntry: 无效启用标志 '${parts[4]}'")
                return null
            }
        }

        return HookEntry(
            type = type,
            identifier = identifier,
            uid = uid,
            mode = mode,
            enabled = enabled
        )
    }

    // ==================== 事件翻译 ====================

    /**
     * 将二进制事件数据转换为人类可读字符串。
     *
     * 二进制事件格式（来自 netlink）：
     * - `__u32 magic`（0xAF5F）
     * - `__u32 event_type`
     * - `__u32 pid`
     * - `__u32 uid`
     * - `__u32 path_len`
     * - `char path[]`
     * - `__u64 timestamp`
     * - `__u32 result`（0=allow, 1=deny）
     *
     * 字符串格式：
     * `"OPEN pid=1234 uid=10086 path=/system/bin/su result=DENY ts=1717200000"`
     *
     * @param data 事件二进制数据
     * @return 人类可读字符串，解析失败返回 null
     */
    fun eventToString(data: ByteArray): String? {
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            val magic = buffer.getInt()
            if (magic != EVENT_MAGIC) {
                Log.w(TAG, "eventToString: 魔数不匹配，期望 0x${EVENT_MAGIC.toString(16)}，实际 0x${magic.toString(16)}")
                return null
            }

            val eventType = buffer.getInt()
            val pid = buffer.getInt()
            val uid = buffer.getInt()
            val pathLen = buffer.getInt()

            if (pathLen < 0 || pathLen > buffer.remaining() - EVENT_TRAILER_SIZE) {
                Log.w(TAG, "eventToString: path_len=$pathLen 超出数据范围")
                return null
            }

            val pathBytes = ByteArray(pathLen)
            buffer.get(pathBytes)
            val path = String(pathBytes, Charsets.UTF_8)

            val timestamp = buffer.getLong()
            val result = buffer.getInt()

            val eventTypeStr = eventTypeName(eventType)
            val resultStr = if (result == 0) "ALLOW" else "DENY"

            "$eventTypeStr pid=$pid uid=$uid path=$path result=$resultStr ts=$timestamp"
        } catch (e: Exception) {
            Log.w(TAG, "eventToString: 解析失败", e)
            null
        }
    }

    /**
     * 将二进制事件数据转换为结构化 Map。
     *
     * @param data 事件二进制数据
     * @return 包含事件各字段的结构化 Map，解析失败返回 null
     */
    fun eventToMap(data: ByteArray): Map<String, Any>? {
        return try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            val magic = buffer.getInt()
            if (magic != EVENT_MAGIC) {
                Log.w(TAG, "eventToMap: 魔数不匹配，期望 0x${EVENT_MAGIC.toString(16)}，实际 0x${magic.toString(16)}")
                return null
            }

            val eventType = buffer.getInt()
            val pid = buffer.getInt()
            val uid = buffer.getInt()
            val pathLen = buffer.getInt()

            if (pathLen < 0 || pathLen > buffer.remaining() - EVENT_TRAILER_SIZE) {
                Log.w(TAG, "eventToMap: path_len=$pathLen 超出数据范围")
                return null
            }

            val pathBytes = ByteArray(pathLen)
            buffer.get(pathBytes)
            val path = String(pathBytes, Charsets.UTF_8)

            val timestamp = buffer.getLong()
            val result = buffer.getInt()

            mapOf(
                "magic" to magic,
                "event_type" to eventType,
                "event_type_name" to eventTypeName(eventType),
                "pid" to pid,
                "uid" to uid,
                "path" to path,
                "timestamp" to timestamp,
                "result" to result,
                "result_name" to if (result == 0) "ALLOW" else "DENY"
            )
        } catch (e: Exception) {
            Log.w(TAG, "eventToMap: 解析失败", e)
            null
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 生成二进制数据的十六进制转储（用于调试显示）。
     *
     * 输出格式示例：
     * ```
     * 0000: 00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F
     * 0010: 10 11 12 13 14 15 16 17  18 19 1A 1B 1C 1D 1E 1F
     * ```
     *
     * @param data 二进制数据
     * @param maxBytes 最多显示的字节数，默认 64
     * @return 格式化的十六进制转储字符串
     */
    fun hexDump(data: ByteArray, maxBytes: Int = 64): String {
        val len = minOf(data.size, maxBytes)
        if (len == 0) return "<empty>"

        val sb = StringBuilder()
        var offset = 0
        while (offset < len) {
            // 偏移量
            sb.append(String.format("%04X: ", offset))

            // 十六进制部分（每行 16 字节，分两组各 8 字节）
            val rowEnd = minOf(offset + 16, len)
            for (i in offset until rowEnd) {
                if (i == offset + 8) sb.append(' ')
                sb.append(String.format("%02X ", data[i]))
            }

            // 填充空格以对齐
            val padding = (16 - (rowEnd - offset)) * 3 + if (rowEnd - offset <= 8) 1 else 0
            for (i in 0 until padding) {
                sb.append(' ')
            }

            // ASCII 部分
            sb.append(" |")
            for (i in offset until rowEnd) {
                val b = data[i].toInt() and 0xFF
                sb.append(if (b in 32..126) b.toChar() else '.')
            }
            sb.append('|')
            sb.append('\n')

            offset = rowEnd
        }

        if (data.size > maxBytes) {
            sb.append("... (${data.size - maxBytes} more bytes)\n")
        }

        return sb.toString().trimEnd('\n')
    }

    /**
     * 验证规则字符串格式。
     *
     * 期望格式：`"action:path_pattern:mode"`
     * - action: "allow" 或 "deny"
     * - path_pattern: 非空路径
     * - mode: "r"、"w"、"rw"、"wr" 之一
     *
     * @param rule 规则字符串
     * @return [ValidationResult]，包含验证结果和解析出的字段
     */
    fun validateRuleString(rule: String): ValidationResult {
        val parts = rule.split(":", limit = 3)
        if (parts.size != 3) {
            return ValidationResult(
                valid = false,
                error = "格式无效，期望 'action:path:mode'，实际有 ${parts.size} 个字段"
            )
        }

        val actionStr = parts[0].lowercase()
        if (actionStr != "allow" && actionStr != "deny") {
            return ValidationResult(
                valid = false,
                error = "无效动作 '$actionStr'，期望 'allow' 或 'deny'"
            )
        }

        val path = parts[1]
        if (path.isEmpty()) {
            return ValidationResult(
                valid = false,
                error = "路径不能为空"
            )
        }

        val modeStr = parts[2].lowercase()
        if (parseModeMask(modeStr) < 0) {
            return ValidationResult(
                valid = false,
                error = "无效模式 '$modeStr'，期望 'r'、'w' 或 'rw'"
            )
        }

        return ValidationResult(
            valid = true,
            parsedFields = mapOf(
                "action" to actionStr,
                "path" to path,
                "mode" to modeStr
            )
        )
    }

    /**
     * 验证 Hook 命令字符串格式。
     *
     * 支持的格式：
     * - 添加：`"add:PID:12345:10086:MONITOR_ONLY"` 或 `"add:PACKAGE:com.example.app:10086:INTERCEPT_ALL"`
     * - 移除：`"remove:PID:12345"` 或 `"remove:PACKAGE:com.example.app"`
     *
     * @param command Hook 命令字符串
     * @return [ValidationResult]，包含验证结果和解析出的字段
     */
    fun validateHookCommand(command: String): ValidationResult {
        val parts = command.split(":", limit = 5)
        if (parts.isEmpty() || parts[0].isEmpty()) {
            return ValidationResult(
                valid = false,
                error = "空命令"
            )
        }

        val operation = parts[0].lowercase()
        if (operation != "add" && operation != "remove") {
            return ValidationResult(
                valid = false,
                error = "无效操作 '$operation'，期望 'add' 或 'remove'"
            )
        }

        if (parts.size < 3) {
            return ValidationResult(
                valid = false,
                error = "字段不足，期望至少 'operation:type:identifier'"
            )
        }

        val typeStr = parts[1].uppercase()
        if (typeStr != "PID" && typeStr != "PACKAGE") {
            return ValidationResult(
                valid = false,
                error = "无效 Hook 类型 '$typeStr'，期望 'PID' 或 'PACKAGE'"
            )
        }

        val identifier = parts[2]
        if (identifier.isEmpty()) {
            return ValidationResult(
                valid = false,
                error = "标识符不能为空"
            )
        }

        val fields = mutableMapOf(
            "operation" to operation,
            "type" to typeStr,
            "identifier" to identifier
        )

        if (operation == "add") {
            if (parts.size != 5) {
                return ValidationResult(
                    valid = false,
                    error = "add 命令需要 5 个字段：'add:type:id:uid:mode'"
                )
            }

            val uid = parts[3].toIntOrNull()
            if (uid == null || uid < 0) {
                return ValidationResult(
                    valid = false,
                    error = "无效 UID '${parts[3]}'"
                )
            }

            val modeStr = parts[4].uppercase()
            val validModes = listOf("MONITOR_ONLY", "INTERCEPT_READ", "INTERCEPT_WRITE", "INTERCEPT_ALL")
            if (modeStr !in validModes) {
                return ValidationResult(
                    valid = false,
                    error = "无效 Hook 模式 '$modeStr'，期望 ${validModes.joinToString(", ")}"
                )
            }

            fields["uid"] = parts[3]
            fields["mode"] = modeStr
        }

        return ValidationResult(
            valid = true,
            parsedFields = fields
        )
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 将模式字符串解析为位掩码。
     *
     * @param modeStr 模式字符串（"r"、"w"、"rw"、"wr"）
     * @return 位掩码值，无效输入返回 -1
     */
    private fun parseModeMask(modeStr: String): Int {
        return when (modeStr.lowercase()) {
            "r" -> MODE_READ.toInt()
            "w" -> MODE_WRITE.toInt()
            "rw", "wr" -> (MODE_READ.toInt() or MODE_WRITE.toInt())
            else -> -1
        }
    }

    /**
     * 将模式位掩码转换为字符串。
     *
     * @param modeMask 模式位掩码
     * @return 模式字符串（"r"、"w"、"rw"）
     */
    private fun modeMaskToString(modeMask: Int): String {
        val hasRead = (modeMask and MODE_READ.toInt()) != 0
        val hasWrite = (modeMask and MODE_WRITE.toInt()) != 0
        return when {
            hasRead && hasWrite -> "rw"
            hasRead -> "r"
            hasWrite -> "w"
            else -> ""
        }
    }

    /**
     * 将 Hook 模式字节值转换为字符串名称。
     *
     * @param hookMode Hook 模式字节值
     * @return 模式名称字符串
     */
    private fun hookModeToString(hookMode: Int): String {
        return when (hookMode) {
            HOOK_MODE_MONITOR_ONLY.toInt() -> "MONITOR_ONLY"
            HOOK_MODE_INTERCEPT_READ.toInt() -> "INTERCEPT_READ"
            HOOK_MODE_INTERCEPT_WRITE.toInt() -> "INTERCEPT_WRITE"
            HOOK_MODE_INTERCEPT_ALL.toInt() -> "INTERCEPT_ALL"
            else -> {
                Log.w(TAG, "hookModeToString: 未知模式值 $hookMode")
                "UNKNOWN($hookMode)"
            }
        }
    }

    /**
     * 将事件类型 ID 转换为可读名称。
     *
     * @param eventType 事件类型 ID
     * @return 事件类型名称字符串
     */
    private fun eventTypeName(eventType: Int): String {
        return when (eventType) {
            0 -> "OPEN"
            1 -> "READ"
            2 -> "WRITE"
            3 -> "CLOSE"
            4 -> "CREATE"
            5 -> "DELETE"
            6 -> "RENAME"
            7 -> "ATTR"
            else -> "EVENT($eventType)"
        }
    }
}
