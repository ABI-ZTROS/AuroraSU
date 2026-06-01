package com.ztros.ztrosu.ui.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File

private const val TAG = "VFSTargetSelector"

/**
 * VFS双模式目标选择器
 *
 * 提供两种模式的目标选择：
 * - 模式1：获取运行中的进程列表（扫描 /proc/）
 * - 模式2：获取已安装的应用列表（通过 pm list packages）
 *
 * 用于VFS Hook的目标选择UI。
 */
object VFSTargetSelector {

    // ==================== 枚举与数据类 ====================

    /**
     * 目标选择模式
     */
    enum class TargetMode {
        /** 运行中的进程 */
        RUNNING_PROCESS,
        /** 已安装的应用 */
        INSTALLED_APP
    }

    /**
     * 运行中的进程信息
     */
    data class RunningProcess(
        val pid: Int,
        val name: String,
        val uid: Int,
        val cmdline: String
    ) {
        /**
         * 获取显示名称（优先使用进程名，fallback到cmdline）
         */
        fun getDisplayName(): String {
            return if (name.isNotEmpty()) name else cmdline.substringAfterLast('/').substringBefore('\u0000')
        }
    }

    /**
     * 已安装的应用信息
     */
    data class InstalledApp(
        val packageName: String,
        val uid: Int,
        val label: String  // 应用名称（可选，可能为空）
    ) {
        /**
         * 获取显示名称（优先使用label，fallback到packageName）
         */
        fun getDisplayName(): String {
            return if (label.isNotEmpty()) label else packageName
        }
    }

    /**
     * 可选择的目标项（UI数据结构）
     */
    data class SelectableTarget(
        val mode: TargetMode,
        val pid: Int?,           // PID (运行进程模式)
        val packageName: String?, // 包名 (已安装应用模式)
        val uid: Int,
        val displayName: String,
        val isSelected: Boolean = false
    ) {
        /**
         * 获取唯一标识符
         */
        fun getIdentifier(): String {
            return when (mode) {
                TargetMode.RUNNING_PROCESS -> "pid:$pid"
                TargetMode.INSTALLED_APP -> "pkg:$packageName"
            }
        }

        /**
         * 获取副标题信息
         */
        fun getSubtitle(): String {
            return when (mode) {
                TargetMode.RUNNING_PROCESS -> "PID: $pid | UID: $uid"
                TargetMode.INSTALLED_APP -> "UID: $uid"
            }
        }
    }

    // ==================== 进程扫描 ====================

    /**
     * 获取运行中的进程列表
     *
     * 扫描 /proc/ 目录，读取每个进程的：
     * - /proc/[pid]/status 获取UID
     * - /proc/[pid]/cmdline 获取进程名
     *
     * @return 运行中的进程列表
     */
    fun getRunningProcesses(): List<RunningProcess> {
        return try {
            val procDir = File("/proc")
            val processes = mutableListOf<RunningProcess>()

            // 获取/proc下的所有数字目录（即PID）
            val pidDirs = procDir.listFiles()
                ?.filter { it.isDirectory && it.name.matches("\\d+".toRegex()) }
                ?: emptyList()

            for (pidDir in pidDirs) {
                try {
                    val pid = pidDir.name.toIntOrNull() ?: continue

                    // 跳过自身和内核线程
                    if (pid == android.os.Process.myPid()) continue

                    val uid = readUidFromProc(pid)
                    if (uid < 0) continue  // 无法读取UID，跳过

                    val name = readProcessNameFromProc(pid)
                    val cmdline = readCmdlineFromProc(pid)

                    // 过滤掉空名称的内核线程
                    if (name.isEmpty() && cmdline.isEmpty()) continue

                    processes.add(
                        RunningProcess(
                            pid = pid,
                            name = name,
                            uid = uid,
                            cmdline = cmdline
                        )
                    )
                } catch (e: Exception) {
                    // 单个进程读取失败不影响整体
                    continue
                }
            }

            // 按PID排序
            processes.sortedBy { it.pid }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get running processes", e)
            emptyList()
        }
    }

    /**
     * 通过root shell获取运行中的进程列表（增强版）
     *
     * 使用root权限扫描所有进程，包括其他用户空间的进程
     *
     * @return 运行中的进程列表
     */
    fun getRunningProcessesRoot(): List<RunningProcess> {
        return try {
            val script = """
                for pid_dir in /proc/[0-9]*; do
                    pid=${'$'}{pid_dir##*/}
                    uid=${'$'}(awk '/^Uid:/{print ${'$'}2}' "${'$'}pid_dir/status" 2>/dev/null)
                    if [ -z "${'$'}uid" ]; then continue; fi
                    
                    name=${'$'}(awk '/^Name:/{print ${'$'}2}' "${'$'}pid_dir/status" 2>/dev/null)
                    
                    cmdline=${'$'}(tr '\0' ' ' < "${'$'}pid_dir/cmdline" 2>/dev/null | cut -c1-256)
                    
                    echo "PID:${'$'}pid|UID:${'$'}uid|NAME:${'$'}name|CMDLINE:${'$'}cmdline"
                done
            """.trimIndent()
            val result = Shell.cmd(script).exec()

            if (!result.isSuccess) {
                Log.w(TAG, "Root process scan failed, falling back to non-root")
                return getRunningProcesses()
            }

            result.out.mapNotNull { line ->
                try {
                    val parts = line.split("|")
                    if (parts.size != 4) return@mapNotNull null

                    val pid = parts[0].substringAfter("PID:").toIntOrNull() ?: return@mapNotNull null
                    val uid = parts[1].substringAfter("UID:").toIntOrNull() ?: return@mapNotNull null
                    val name = parts[2].substringAfter("NAME:")
                    val cmdline = parts[3].substringAfter("CMDLINE:")

                    RunningProcess(
                        pid = pid,
                        name = name,
                        uid = uid,
                        cmdline = cmdline
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.pid }
        } catch (e: Exception) {
            Log.e(TAG, "Root process scan failed", e)
            getRunningProcesses()
        }
    }

    // ==================== 应用列表 ====================

    /**
     * 获取已安装的应用列表
     *
     * 执行 pm list packages -U 获取包名和UID
     *
     * @return 已安装的应用列表
     */
    fun getInstalledApps(): List<InstalledApp> {
        return try {
            val result = Shell.cmd("pm list packages -U").exec()
            if (!result.isSuccess) {
                Log.e(TAG, "Failed to list packages")
                return emptyList()
            }

            result.out.mapNotNull { line ->
                try {
                    // 格式: package:<packageName> uid:<uid>
                    val packageMatch = Regex("package:([^\\s]+)").find(line)
                    val uidMatch = Regex("uid:(\\d+)").find(line)

                    if (packageMatch != null && uidMatch != null) {
                        val packageName = packageMatch.groupValues[1]
                        val uid = uidMatch.groupValues[1].toIntOrNull() ?: return@mapNotNull null

                        if (uid >= 0) {
                            InstalledApp(
                                packageName = packageName,
                                uid = uid,
                                label = ""  // 需要Context才能获取label
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.packageName }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed apps", e)
            emptyList()
        }
    }

    /**
     * 获取已安装的应用列表（带应用名称）
     *
     * 需要Context来获取应用标签
     *
     * @param context Android Context
     * @return 已安装的应用列表（包含应用名称）
     */
    fun getInstalledAppsWithLabels(context: Context): List<InstalledApp> {
        val apps = getInstalledApps()
        val pm = context.packageManager

        return apps.map { app ->
            try {
                val appInfo = pm.getApplicationInfo(app.packageName, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                app.copy(label = label)
            } catch (e: Exception) {
                // 无法获取label，使用包名
                app.copy(label = app.packageName)
            }
        }.sortedBy { it.label.lowercase() }
    }

    /**
     * 获取已安装的用户应用列表（排除系统应用）
     *
     * @param context Android Context
     * @return 用户应用列表
     */
    fun getUserInstalledApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val allApps = getInstalledAppsWithLabels(context)

        return allApps.filter { app ->
            try {
                val appInfo = pm.getApplicationInfo(app.packageName, 0)
                // 排除系统应用和更新过的系统应用
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            } catch (e: Exception) {
                false
            }
        }
    }

    // ==================== 目标选择 ====================

    /**
     * 根据模式返回可选目标列表
     *
     * @param mode 目标选择模式
     * @return 可选择的目标列表
     */
    fun getSelectableTargets(mode: TargetMode): List<SelectableTarget> {
        return when (mode) {
            TargetMode.RUNNING_PROCESS -> getProcessTargets()
            TargetMode.INSTALLED_APP -> getAppTargets()
        }
    }

    /**
     * 获取运行进程模式的目标列表
     */
    private fun getProcessTargets(): List<SelectableTarget> {
        val processes = getRunningProcessesRoot()
        return processes.map { proc ->
            SelectableTarget(
                mode = TargetMode.RUNNING_PROCESS,
                pid = proc.pid,
                packageName = null,
                uid = proc.uid,
                displayName = proc.getDisplayName()
            )
        }
    }

    /**
     * 获取已安装应用模式的目标列表
     */
    private fun getAppTargets(): List<SelectableTarget> {
        val apps = getInstalledApps()
        return apps.map { app ->
            SelectableTarget(
                mode = TargetMode.INSTALLED_APP,
                pid = null,
                packageName = app.packageName,
                uid = app.uid,
                displayName = app.getDisplayName()
            )
        }
    }

    /**
     * 获取运行进程模式的目标列表（带应用名称）
     *
     * @param context Android Context
     * @return 可选择的目标列表
     */
    fun getSelectableTargetsWithContext(mode: TargetMode, context: Context): List<SelectableTarget> {
        return when (mode) {
            TargetMode.RUNNING_PROCESS -> {
                val processes = getRunningProcessesRoot()
                val pm = context.packageManager

                processes.map { proc ->
                    // 尝试通过cmdline匹配包名获取应用名称
                    val label = resolveProcessLabel(proc, pm)
                    SelectableTarget(
                        mode = TargetMode.RUNNING_PROCESS,
                        pid = proc.pid,
                        packageName = null,
                        uid = proc.uid,
                        displayName = label
                    )
                }
            }
            TargetMode.INSTALLED_APP -> {
                val apps = getInstalledAppsWithLabels(context)
                apps.map { app ->
                    SelectableTarget(
                        mode = TargetMode.INSTALLED_APP,
                        pid = null,
                        packageName = app.packageName,
                        uid = app.uid,
                        displayName = app.getDisplayName()
                    )
                }
            }
        }
    }

    // ==================== 搜索与过滤 ====================

    /**
     * 搜索目标
     *
     * @param query 搜索关键词
     * @param mode 目标模式
     * @return 匹配的目标列表
     */
    fun searchTargets(query: String, mode: TargetMode): List<SelectableTarget> {
        val allTargets = getSelectableTargets(mode)
        val lowerQuery = query.lowercase()

        return allTargets.filter { target ->
            target.displayName.lowercase().contains(lowerQuery) ||
                    target.uid.toString().contains(lowerQuery) ||
                    (target.pid?.toString()?.contains(lowerQuery) == true) ||
                    (target.packageName?.lowercase()?.contains(lowerQuery) == true)
        }
    }

    /**
     * 按UID过滤目标
     *
     * @param uid 目标UID
     * @param mode 目标模式
     * @return 匹配的目标列表
     */
    fun filterByUid(uid: Int, mode: TargetMode): List<SelectableTarget> {
        return getSelectableTargets(mode).filter { it.uid == uid }
    }

    /**
     * 按UID范围过滤目标
     *
     * @param minUid 最小UID（包含）
     * @param maxUid 最大UID（包含）
     * @param mode 目标模式
     * @return 匹配的目标列表
     */
    fun filterByUidRange(minUid: Int, maxUid: Int, mode: TargetMode): List<SelectableTarget> {
        return getSelectableTargets(mode).filter { it.uid in minUid..maxUid }
    }

    // ==================== 辅助方法 ====================

    /**
     * 从/proc读取UID
     */
    private fun readUidFromProc(pid: Int): Int {
        return try {
            val statusFile = File("/proc/$pid/status")
            if (!statusFile.exists()) return -1

            val content = statusFile.readText()
            val uidLine = content.lines().find { it.startsWith("Uid:") }
            uidLine?.split("\\s+".toRegex())?.getOrNull(1)?.toIntOrNull() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * 从/proc读取进程名
     */
    private fun readProcessNameFromProc(pid: Int): String {
        return try {
            val statusFile = File("/proc/$pid/status")
            if (!statusFile.exists()) return ""

            val content = statusFile.readText()
            val nameLine = content.lines().find { it.startsWith("Name:") }
            nameLine?.substringAfter("Name:")?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 从/proc读取cmdline
     */
    private fun readCmdlineFromProc(pid: Int): String {
        return try {
            val cmdlineFile = File("/proc/$pid/cmdline")
            if (!cmdlineFile.exists()) return ""

            val cmdline = cmdlineFile.readText()
            // cmdline以\0分隔，取第一个参数
            val parts = cmdline.split('\u0000')
            parts.firstOrNull()?.take(256) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 尝试将进程名解析为应用名称
     */
    private fun resolveProcessLabel(proc: RunningProcess, pm: PackageManager): String {
        // 首先尝试直接使用进程名
        if (proc.name.isNotEmpty()) {
            // 检查进程名是否就是包名
            try {
                val appInfo = pm.getApplicationInfo(proc.name, 0)
                return pm.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                // 不是包名，继续
            }

            // 尝试从cmdline中提取包名
            val cmdline = proc.cmdline
            val possiblePackage = cmdline
                .substringAfterLast('/')
                .substringBefore('\u0000')
                .substringBefore(' ')
                .trim()

            if (possiblePackage.contains(".")) {
                try {
                    val appInfo = pm.getApplicationInfo(possiblePackage, 0)
                    return pm.getApplicationLabel(appInfo).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    // 不是包名
                }
            }
        }

        // Fallback: 使用进程名或cmdline
        return proc.getDisplayName()
    }

    /**
     * 获取进程数量统计
     */
    fun getProcessStats(): ProcessStats {
        val processes = getRunningProcessesRoot()
        val uidGroups = processes.groupBy { it.uid }

        return ProcessStats(
            totalProcesses = processes.size,
            uniqueUids = uidGroups.size,
            minUid = uidGroups.keys.minOrNull() ?: 0,
            maxUid = uidGroups.keys.maxOrNull() ?: 0
        )
    }

    data class ProcessStats(
        val totalProcesses: Int,
        val uniqueUids: Int,
        val minUid: Int,
        val maxUid: Int
    )

    /**
     * 获取调试信息
     */
    fun getDebugInfo(): String {
        val stats = getProcessStats()
        return buildString {
            appendLine("VFSTargetSelector Debug Info:")
            appendLine("  Total Processes: ${stats.totalProcesses}")
            appendLine("  Unique UIDs: ${stats.uniqueUids}")
            appendLine("  UID Range: ${stats.minUid} - ${stats.maxUid}")
            appendLine("  Installed Apps: ${getInstalledApps().size}")
        }
    }
}
