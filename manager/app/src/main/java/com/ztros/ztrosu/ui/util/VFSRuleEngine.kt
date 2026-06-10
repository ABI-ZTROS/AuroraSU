package com.ztros.ztrosu.ui.util

import android.util.Log
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val TAG = "VFSRuleEngine"

/**
 * VFS操作类型枚举
 */
enum class VFSOp {
    OPEN,       // 打开文件
    READ,       // 读取文件
    WRITE,      // 写入文件
    CLOSE;      // 关闭文件

    companion object {
        /**
         * 从模式字符串解析操作类型集合
         * @param mode 模式字符串，如 "rw", "r", "w"
         * @return 操作类型集合
         */
        fun fromMode(mode: String): Set<VFSOp> {
            val ops = mutableSetOf<VFSOp>()
            if (mode.contains('r', ignoreCase = true)) {
                ops.add(READ)
                ops.add(OPEN) // 读取通常需要先打开
            }
            if (mode.contains('w', ignoreCase = true)) {
                ops.add(WRITE)
                ops.add(OPEN) // 写入通常需要先打开
            }
            return ops
        }

        /**
         * 从字符串解析单个操作类型
         */
        fun fromString(str: String): VFSOp? {
            return when (str.uppercase()) {
                "OPEN" -> OPEN
                "READ", "R" -> READ
                "WRITE", "W" -> WRITE
                "CLOSE" -> CLOSE
                else -> null
            }
        }
    }
}

/**
 * 规则动作枚举
 */
enum class RuleAction {
    ALLOW,      // 允许操作
    DENY,       // 拒绝操作
    LOG_ONLY;   // 仅记录日志，不阻止

    companion object {
        fun fromString(str: String): RuleAction? {
            return when (str.lowercase()) {
                "allow", "a" -> ALLOW
                "deny", "d" -> DENY
                "log", "log_only", "l" -> LOG_ONLY
                else -> null
            }
        }
    }
}

/**
 * 规则类型枚举
 */
enum class RuleType {
    PATH_RULE,   // 路径匹配规则 (支持glob通配符)
    UID_RULE,    // UID匹配规则
    COMBO_RULE   // 组合规则 (路径+UID+操作类型)
}

/**
 * VFS规则数据结构
 * 
 * @param id 唯一标识
 * @param action 规则动作 (ALLOW, DENY, LOG_ONLY)
 * @param pathPattern 路径模式 (支持 * ? ** 通配符)
 * @param uidFilter UID过滤 (null表示所有UID)
 * @param opTypes 操作类型集合 (READ, WRITE, OPEN, CLOSE)
 * @param priority 优先级 (数字越大优先级越高)
 * @param enabled 是否启用
 * @param description 规则描述
 * @param createdAt 创建时间
 */
data class VFSRule(
    val id: String = UUID.randomUUID().toString(),
    val action: RuleAction = RuleAction.DENY,
    val pathPattern: String = "/*",
    val uidFilter: Int? = null,
    val opTypes: Set<VFSOp> = setOf(VFSOp.READ, VFSOp.WRITE),
    val priority: Int = 0,
    val enabled: Boolean = true,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取规则类型
     */
    val ruleType: RuleType
        get() = when {
            uidFilter != null -> RuleType.COMBO_RULE
            pathPattern.contains("*") || pathPattern.contains("?") -> RuleType.PATH_RULE
            else -> RuleType.PATH_RULE
        }

    /**
     * 转换为简单规则格式 (兼容旧版)
     * 格式: action:path:mode 或 action:path:mode:uid
     *
     * 注意: LOG_ONLY规则无法推送到内核(内核只接受allow/deny)，
     * 返回null表示应跳过该规则。
     */
    fun toSimpleFormat(): String? {
        // 内核只接受 allow 或 deny，LOG_ONLY 无法推送
        if (action == RuleAction.LOG_ONLY) return null

        val mode = buildString {
            if (VFSOp.READ in opTypes) append('r')
            if (VFSOp.WRITE in opTypes) append('w')
        }
        val base = "${action.name.lowercase()}:$pathPattern:$mode"
        // 内核 vfs_rule_parse 支持第4字段作为UID过滤
        return if (uidFilter != null) "$base:$uidFilter" else base
    }

    /**
     * 转换为JSON对象
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("action", action.name)
            put("pathPattern", pathPattern)
            put("uidFilter", uidFilter)
            put("opTypes", JSONArray(opTypes.map { it.name }))
            put("priority", priority)
            put("enabled", enabled)
            put("description", description)
            put("createdAt", createdAt)
        }
    }

    /** Alias for toJson() for consistency with other VFS data classes */
    fun toJSON(): JSONObject = toJson()

    companion object {
        /**
         * 从JSON对象解析规则
         */
        fun fromJson(json: JSONObject): VFSRule {
            val opTypesArray = json.optJSONArray("opTypes")
            val opTypes = mutableSetOf<VFSOp>()
            if (opTypesArray != null) {
                for (i in 0 until opTypesArray.length()) {
                    VFSOp.fromString(opTypesArray.getString(i))?.let { opTypes.add(it) }
                }
            } else {
                // 默认读写
                opTypes.add(VFSOp.READ)
                opTypes.add(VFSOp.WRITE)
            }

            return VFSRule(
                id = json.optString("id", UUID.randomUUID().toString()),
                action = RuleAction.fromString(json.optString("action", "DENY")) ?: RuleAction.DENY,
                pathPattern = json.optString("pathPattern", "/*"),
                uidFilter = if (json.has("uidFilter") && !json.isNull("uidFilter")) json.getInt("uidFilter") else null,
                opTypes = opTypes,
                priority = json.optInt("priority", 0),
                enabled = json.optBoolean("enabled", true),
                description = if (json.has("description") && !json.isNull("description")) json.getString("description") else null,
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }

        /**
         * 从简单规则格式解析
         * 格式: action:path:mode 或 action:path:mode:uid
         */
        fun fromSimpleFormat(ruleStr: String): VFSRule? {
            val parts = ruleStr.split(":")
            if (parts.size < 3) return null

            val action = RuleAction.fromString(parts[0]) ?: return null
            val pathPattern = parts[1]
            val opTypes = VFSOp.fromMode(parts[2])
            val uidFilter = if (parts.size > 3) parts[3].toIntOrNull() else null

            return VFSRule(
                action = action,
                pathPattern = pathPattern,
                opTypes = opTypes,
                uidFilter = uidFilter
            )
        }

        /** Alias for fromJson() for consistency with other VFS data classes */
        fun fromJSON(json: JSONObject): VFSRule = fromJson(json)
    }
}

/**
 * 规则匹配结果
 */
data class RuleMatchResult(
    val matched: Boolean,
    val rule: VFSRule? = null,
    val action: RuleAction = RuleAction.ALLOW,
    val matchDetails: String? = null
)

/**
 * VFS规则引擎
 * 
 * 提供增强的规则匹配功能:
 * - 按优先级排序匹配
 * - 支持路径glob匹配 (*, ?, **)
 * - 支持UID精确匹配
 * - 支持操作类型过滤
 * - 规则持久化
 */
object VFSRuleEngine {

    private const val RULES_FILE_PATH = "/data/adb/ztrosu/vfs_rules.json"
    private const val LEGACY_RULES_FILE_PATH = "/data/adb/ztrosu/vfs_rules.txt"

    private var rules: MutableList<VFSRule> = mutableListOf()
    private var initialized = false

    /**
     * 初始化规则引擎
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (initialized) {
            return@withContext true
        }

        try {
            loadRules()
            initialized = true
            Log.i(TAG, "VFS Rule Engine initialized with ${rules.size} rules")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VFS Rule Engine", e)
            false
        }
    }

    /**
     * 加载规则
     */
    suspend fun loadRules(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 尝试加载JSON格式规则
            val jsonFile = SuFile.open(RULES_FILE_PATH)
            if (jsonFile.exists() && jsonFile.canRead()) {
                val content = jsonFile.readText()
                val jsonArray = JSONArray(content)
                rules.clear()
                for (i in 0 until jsonArray.length()) {
                    val ruleJson = jsonArray.getJSONObject(i)
                    rules.add(VFSRule.fromJson(ruleJson))
                }
                Log.i(TAG, "Loaded ${rules.size} rules from JSON")
                return@withContext true
            }

            // 尝试加载旧版文本格式规则
            val legacyFile = SuFile.open(LEGACY_RULES_FILE_PATH)
            if (legacyFile.exists() && legacyFile.canRead()) {
                val lines = legacyFile.readText().lines()
                rules.clear()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                        VFSRule.fromSimpleFormat(trimmed)?.let { rules.add(it) }
                    }
                }
                Log.i(TAG, "Loaded ${rules.size} rules from legacy format")
                // 迁移到JSON格式
                saveRules()
                return@withContext true
            }

            // 没有规则文件，加载预设规则
            loadPresetRules()
            Log.i(TAG, "Loaded preset rules")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load rules", e)
            false
        }
    }

    /**
     * 保存规则
     */
    suspend fun saveRules(): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonFile = SuFile.open(RULES_FILE_PATH)
            
            // 确保父目录存在
            val parentDir = File(RULES_FILE_PATH).parentFile
            if (parentDir != null && !parentDir.exists()) {
                Shell.cmd("mkdir -p '${parentDir.absolutePath}'").exec()
            }

            val jsonArray = JSONArray()
            rules.forEach { rule -> jsonArray.put(rule.toJson()) }
            jsonFile.writeText(jsonArray.toString(2))
            Log.i(TAG, "Saved ${rules.size} rules")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save rules", e)
            false
        }
    }

    /**
     * 获取所有规则 (按优先级排序)
     */
    fun getRules(): List<VFSRule> {
        return rules.sortedByDescending { it.priority }
    }

    /**
     * 添加规则
     */
    suspend fun addRule(rule: VFSRule): Boolean {
        rules.add(rule)
        return saveRules()
    }

    /**
     * 更新规则
     */
    suspend fun updateRule(rule: VFSRule): Boolean {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) {
            rules[index] = rule
            return saveRules()
        }
        return false
    }

    /**
     * 删除规则
     */
    suspend fun deleteRule(ruleId: String): Boolean {
        val removed = rules.removeAll { it.id == ruleId }
        if (removed) {
            return saveRules()
        }
        return false
    }

    /**
     * 清空所有规则
     */
    suspend fun clearRules(): Boolean {
        rules.clear()
        return saveRules()
    }

    /**
     * 导入规则
     */
    suspend fun importRules(jsonContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray(jsonContent)
            val importedRules = mutableListOf<VFSRule>()
            for (i in 0 until jsonArray.length()) {
                val ruleJson = jsonArray.getJSONObject(i)
                importedRules.add(VFSRule.fromJson(ruleJson))
            }
            rules.clear()
            rules.addAll(importedRules)
            saveRules()
            Log.i(TAG, "Imported ${importedRules.size} rules")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import rules", e)
            false
        }
    }

    /**
     * 导出规则
     */
    fun exportRules(): String {
        val jsonArray = JSONArray()
        rules.forEach { rule -> jsonArray.put(rule.toJson()) }
        return jsonArray.toString(2)
    }

    /**
     * 加载预设规则模板
     */
    private fun loadPresetRules(): Boolean {
        rules.clear()
        rules.addAll(getPresetRules())
        return true
    }

    /**
     * 获取预设规则模板
     */
    fun getPresetRules(): List<VFSRule> {
        val currentTime = System.currentTimeMillis()
        return listOf(
            // 保护系统分区 - 禁止写入
            VFSRule(
                id = "preset-system-protect",
                action = RuleAction.DENY,
                pathPattern = "/system/**",
                opTypes = setOf(VFSOp.WRITE),
                priority = 100,
                enabled = true,
                description = "保护系统分区，禁止任何写入操作",
                createdAt = currentTime
            ),
            // 保护系统分区 - 禁止写入 (vendor分区)
            VFSRule(
                id = "preset-vendor-protect",
                action = RuleAction.DENY,
                pathPattern = "/vendor/**",
                opTypes = setOf(VFSOp.WRITE),
                priority = 100,
                enabled = true,
                description = "保护vendor分区，禁止任何写入操作",
                createdAt = currentTime
            ),
            // 保护系统分区 - 禁止写入 (product分区)
            VFSRule(
                id = "preset-product-protect",
                action = RuleAction.DENY,
                pathPattern = "/product/**",
                opTypes = setOf(VFSOp.WRITE),
                priority = 100,
                enabled = true,
                description = "保护product分区，禁止任何写入操作",
                createdAt = currentTime
            ),
            // 保护应用数据 - 禁止非自身UID访问其他应用数据
            VFSRule(
                id = "preset-app-data-protect",
                action = RuleAction.DENY,
                pathPattern = "/data/data/*",
                opTypes = setOf(VFSOp.READ, VFSOp.WRITE),
                priority = 90,
                enabled = true,
                description = "保护应用私有数据目录，禁止跨UID访问",
                createdAt = currentTime
            ),
            // 允许公共存储读取
            VFSRule(
                id = "preset-sdcard-read",
                action = RuleAction.ALLOW,
                pathPattern = "/sdcard/**",
                opTypes = setOf(VFSOp.READ, VFSOp.OPEN),
                priority = 50,
                enabled = true,
                description = "允许读取公共存储目录",
                createdAt = currentTime
            ),
            // 允许公共存储写入 (可配置)
            VFSRule(
                id = "preset-sdcard-write",
                action = RuleAction.ALLOW,
                pathPattern = "/sdcard/**",
                opTypes = setOf(VFSOp.WRITE),
                priority = 50,
                enabled = false, // 默认禁用，需要手动启用
                description = "允许写入公共存储目录",
                createdAt = currentTime
            ),
            // 日志敏感路径 - /proc读取记录
            VFSRule(
                id = "preset-proc-log",
                action = RuleAction.LOG_ONLY,
                pathPattern = "/proc/**",
                opTypes = setOf(VFSOp.READ),
                priority = 30,
                enabled = true,
                description = "记录/proc目录下的读取操作",
                createdAt = currentTime
            ),
            // 日志敏感路径 - /sys读取记录
            VFSRule(
                id = "preset-sys-log",
                action = RuleAction.LOG_ONLY,
                pathPattern = "/sys/**",
                opTypes = setOf(VFSOp.READ, VFSOp.WRITE),
                priority = 30,
                enabled = true,
                description = "记录/sys目录下的读写操作",
                createdAt = currentTime
            ),
            // 保护SELinux策略文件
            VFSRule(
                id = "preset-selinux-protect",
                action = RuleAction.DENY,
                pathPattern = "/sys/fs/selinux/**",
                opTypes = setOf(VFSOp.READ, VFSOp.WRITE),
                priority = 95,
                enabled = true,
                description = "保护SELinux策略文件",
                createdAt = currentTime
            ),
            // 保护su二进制文件
            VFSRule(
                id = "preset-su-protect",
                action = RuleAction.LOG_ONLY,
                pathPattern = "/data/adb/ksu/**",
                opTypes = setOf(VFSOp.READ, VFSOp.WRITE),
                priority = 80,
                enabled = true,
                description = "记录对KSU目录的访问",
                createdAt = currentTime
            )
        )
    }

    /**
     * 检查访问权限
     * 
     * @param path 文件路径
     * @param op 操作类型
     * @param uid 进程UID (可选)
     * @return 规则匹配结果
     */
    fun checkAccess(path: String, op: VFSOp, uid: Int? = null): RuleMatchResult {
        // 按优先级排序规则
        val sortedRules = rules.sortedByDescending { it.priority }

        for (rule in sortedRules) {
            if (!rule.enabled) continue

            // 检查操作类型
            if (op !in rule.opTypes) continue

            // 检查UID过滤
            if (rule.uidFilter != null && rule.uidFilter != uid) continue

            // 检查路径匹配
            if (matchPath(path, rule.pathPattern)) {
                val details = buildString {
                    append("Rule '${rule.id}' matched: ")
                    append("path=$path, ")
                    append("op=$op, ")
                    append("uid=$uid, ")
                    append("action=${rule.action}")
                }
                Log.d(TAG, details)

                return RuleMatchResult(
                    matched = true,
                    rule = rule,
                    action = rule.action,
                    matchDetails = details
                )
            }
        }

        // 没有匹配的规则，默认允许
        return RuleMatchResult(
            matched = false,
            action = RuleAction.ALLOW,
            matchDetails = "No matching rule for path=$path, op=$op, uid=$uid"
        )
    }

    /**
     * 批量检查访问权限
     */
    fun checkAccessBatch(requests: List<Triple<String, VFSOp, Int?>>): List<RuleMatchResult> {
        return requests.map { (path, op, uid) -> checkAccess(path, op, uid) }
    }

    /**
     * 路径匹配算法
     * 
     * 支持的通配符:
     * - * : 匹配任意非/字符序列
     * - ? : 匹配单个非/字符
     * - ** : 匹配任意字符序列 (包括/)
     * 
     * @param path 实际路径
     * @param pattern 模式字符串
     * @return 是否匹配
     */
    fun matchPath(path: String, pattern: String): Boolean {
        // 标准化路径
        val normalizedPath = normalizePath(path)
        val normalizedPattern = normalizePath(pattern)

        return matchPathInternal(normalizedPath, normalizedPattern)
    }

    /**
     * 内部路径匹配实现
     */
    private fun matchPathInternal(path: String, pattern: String): Boolean {
        val pathLen = path.length
        val patternLen = pattern.length

        // 动态规划表: dp[i][j] 表示 pattern[0..i-1] 是否匹配 path[0..j-1]
        // 优化: 只用两行来节省空间
        var prev = BooleanArray(pathLen + 1) { false }
        var curr = BooleanArray(pathLen + 1) { false }

        // 空模式匹配空路径
        prev[0] = true

        // 处理模式开头的 ** (可以匹配空路径)
        var i = 1
        while (i <= patternLen) {
            val pc = pattern[i - 1]
            curr = BooleanArray(pathLen + 1) { false }

            if (pc == '*') {
                // 检查是否是 **
                if (i < patternLen && pattern[i] == '*') {
                    // ** 可以匹配任意字符序列
                    // dp[i+1][j] = dp[i][j] || dp[i+1][j-1] (匹配一个或多个字符)
                    // 或者 dp[i+1][j] = dp[i][j] (匹配零个字符)
                    for (j in 0..pathLen) {
                        if (prev[j] || (j > 0 && curr[j - 1])) {
                            curr[j] = true
                        }
                    }
                    i++ // 跳过第二个 *
                } else {
                    // 单个 * 匹配非/字符序列
                    for (j in 0..pathLen) {
                        if (prev[j]) {
                            // * 匹配零个字符
                            curr[j] = true
                            // * 匹配多个非/字符
                            var k = j
                            while (k < pathLen && path[k] != '/') {
                                k++
                                curr[k] = true
                            }
                        }
                    }
                }
            } else if (pc == '?') {
                // ? 匹配单个非/字符
                for (j in 1..pathLen) {
                    if (path[j - 1] != '/' && prev[j - 1]) {
                        curr[j] = true
                    }
                }
            } else {
                // 普通字符精确匹配
                for (j in 1..pathLen) {
                    if (path[j - 1] == pc && prev[j - 1]) {
                        curr[j] = true
                    }
                }
            }

            prev = curr.clone()
            i++
        }

        return prev[pathLen]
    }

    /**
     * 标准化路径
     */
    private fun normalizePath(path: String): String {
        var normalized = path

        // 移除末尾的斜杠 (除非是根路径)
        if (normalized.length > 1 && normalized.endsWith('/')) {
            normalized = normalized.dropLast(1)
        }

        // 确保以/开头
        if (!normalized.startsWith('/')) {
            normalized = "/$normalized"
        }

        // 处理 ./ 和 ../
        val segments = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        val stack = mutableListOf<String>()
        for (segment in segments) {
            if (segment == "..") {
                if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            } else {
                stack.add(segment)
            }
        }

        return if (stack.isEmpty()) "/" else "/${stack.joinToString("/")}"
    }

    /**
     * 验证规则
     */
    fun validateRule(rule: VFSRule): Pair<Boolean, String> {
        // 验证路径模式
        if (rule.pathPattern.isBlank()) {
            return Pair(false, "路径模式不能为空")
        }

        // 验证操作类型
        if (rule.opTypes.isEmpty()) {
            return Pair(false, "必须指定至少一个操作类型")
        }

        // 验证优先级范围
        if (rule.priority < 0 || rule.priority > 1000) {
            return Pair(false, "优先级必须在 0-1000 之间")
        }

        // 验证路径模式语法
        try {
            // 检查是否有不完整的 ** (单个 *)
            val doubleStarPattern = Regex("\\*\\*")
            val singleStarPattern = Regex("(?<!\\*)\\*(?!\\*)")
            
            // 路径模式基本验证
            val path = rule.pathPattern
            if (path.contains("//")) {
                return Pair(false, "路径模式不能包含连续的斜杠")
            }
        } catch (e: Exception) {
            return Pair(false, "路径模式语法错误: ${e.message}")
        }

        return Pair(true, "")
    }

    /**
     * 获取规则统计信息
     */
    fun getRuleStats(): Map<String, Any> {
        val enabledCount = rules.count { it.enabled }
        val disabledCount = rules.count { !it.enabled }
        val byAction = rules.groupingBy { it.action.name }.eachCount()
        val byType = rules.groupingBy { it.ruleType.name }.eachCount()

        return mapOf(
            "total" to rules.size,
            "enabled" to enabledCount,
            "disabled" to disabledCount,
            "byAction" to byAction,
            "byType" to byType
        )
    }

    /**
     * 搜索规则
     */
    fun searchRules(query: String): List<VFSRule> {
        val lowerQuery = query.lowercase()
        return rules.filter { rule ->
            rule.pathPattern.lowercase().contains(lowerQuery) ||
            rule.description?.lowercase()?.contains(lowerQuery) == true ||
            rule.id.lowercase().contains(lowerQuery) ||
            rule.action.name.lowercase().contains(lowerQuery)
        }
    }

    /**
     * 按条件过滤规则
     */
    fun filterRules(
        action: RuleAction? = null,
        enabled: Boolean? = null,
        opType: VFSOp? = null,
        hasUidFilter: Boolean? = null
    ): List<VFSRule> {
        return rules.filter { rule ->
            (action == null || rule.action == action) &&
            (enabled == null || rule.enabled == enabled) &&
            (opType == null || opType in rule.opTypes) &&
            (hasUidFilter == null || (rule.uidFilter != null) == hasUidFilter)
        }.sortedByDescending { it.priority }
    }

    /**
     * 将规则转换为旧版VFSPolicy格式
     */
    fun toLegacyPolicy(): VFSPolicy {
        return VFSPolicy(
            enabled = true,
            logLevel = 2,
            defaultAction = "allow",
            rules = rules.filter { it.enabled }.mapNotNull { it.toSimpleFormat() }
        )
    }

    /**
     * 从旧版VFSPolicy导入规则
     */
    suspend fun fromLegacyPolicy(policy: VFSPolicy): Boolean {
        val importedRules = policy.rules.mapNotNull { ruleStr ->
            VFSRule.fromSimpleFormat(ruleStr)
        }
        
        if (importedRules.isEmpty()) return false
        
        rules.clear()
        rules.addAll(importedRules)
        return saveRules()
    }
}