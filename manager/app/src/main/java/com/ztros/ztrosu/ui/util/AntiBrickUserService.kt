package com.ztros.ztrosu.ui.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.ztros.ztrosu.R
import kotlinx.coroutines.*
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "AntiBrickUser"
private const val CHANNEL_ID = "anti_brick_user"
private const val NOTIFICATION_ID = 1002
private const val LOG_FILE_PATH = "/data/local/tmp/anti_brick_user.log"

/**
 * 用户层防格机守护服务（冗余方案）
 *
 * 使用 /proc 轮询监控 exec 事件，不依赖内核模块。
 * 当内核模块被卸载或失效时，此服务自动接管保护。
 */
class AntiBrickUserService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_START = "com.ztros.ztrosu.ANTIBRICK_USER_START"
        const val ACTION_STOP = "com.ztros.ztrosu.ANTIBRICK_USER_STOP"

        fun start(context: Context) {
            val intent = Intent(context, AntiBrickUserService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AntiBrickUserService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "防格机保护(用户层)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "用户层冗余防格机保护"
                setSound(null, null)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun startMonitoring() {
        val isKernelActive = isKernelModuleActive()
        val mode = if (isKernelActive) "辅助模式" else "独立模式"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("防格机保护 [$mode]")
            .setContentText(if (isKernelActive) "内核模块运行中，用户层辅助监控" else "内核模块未加载，用户层独立保护")
            .setSmallIcon(R.drawable.ztros_shield)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AuroraSU:AntiBrickUser")
            .apply { acquire(10 * 60 * 1000L) }

        monitorJob = scope.launch {
            // 直接使用 proc 轮询监控作为唯一实现
            startPollingMonitor()
        }

        Log.i(TAG, "User-layer anti-brick started in $mode")
    }

    /**
     * 检测内核模块是否活跃
     */
    private fun isKernelModuleActive(): Boolean {
        return try {
            File("/sys/kernel/ztrosu/antibrick/active").exists() &&
                    File("/sys/kernel/ztrosu/antibrick/active").readText().trim() == "1"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * /proc 轮询监控（唯一实现，不依赖 fanotify）
     * 通过扫描 /proc 检测高危命令执行
     */
    private suspend fun startPollingMonitor() = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting proc polling monitor")

        val dangerousPatterns = listOf(
            "rm -rf /" to "RM_RF_ROOT",
            "rm -rf /*" to "RM_RF_ROOT",
            "dd if=/dev/zero of=/dev/block" to "DD_ZERO",
            "dd of=/dev/block" to "DD_BLOCK",
            "mkfs" to "MKFS",
            "mkfs.ext" to "MKFS",
            "mkfs.f2fs" to "MKFS",
            "fastboot flash" to "FLASH",
            "fastboot erase" to "FLASH"
        )

        val knownPids = mutableSetOf<Int>()
        var scanCount = 0

        while (isActive) {
            try {
                // 每10次扫描检测一次内核模块状态
                if (scanCount++ % 10 == 0) {
                    val kernelActive = isKernelModuleActive()
                    if (kernelActive) {
                        // 内核模块活跃时，用户层只做轻量监控
                        delay(2000)
                        continue
                    }
                }

                // 扫描 /proc 目录
                val procDir = File("/proc")
                val currentPids = procDir.listFiles()?.mapNotNull {
                    it.name.toIntOrNull()
                }?.toSet() ?: emptySet()

                // 检测新进程
                val newPids = currentPids - knownPids
                for (pid in newPids) {
                    checkProcess(pid, dangerousPatterns)
                }

                knownPids.clear()
                knownPids.addAll(currentPids)

                delay(500) // 500ms 扫描间隔
            } catch (e: Exception) {
                Log.w(TAG, "Polling error", e)
                delay(1000)
            }
        }
    }

    /**
     * 检查单个进程是否执行高危命令
     */
    private fun checkProcess(pid: Int, patterns: List<Pair<String, String>>) {
        try {
            val cmdlineFile = File("/proc/$pid/cmdline")
            if (!cmdlineFile.exists()) return

            val cmdline = cmdlineFile.readText()
                .replace('\u0000', ' ')
                .trim()

            if (cmdline.isBlank()) return

            for ((pattern, riskType) in patterns) {
                if (cmdline.contains(pattern)) {
                    Log.w(TAG, "DETECTED: pid=$pid cmdline=$cmdline risk=$riskType")

                    // 如果内核模块活跃，让内核处理（避免重复弹窗）
                    if (isKernelModuleActive()) {
                        Log.i(TAG, "Kernel module active, skipping user-layer handling")
                        return
                    }

                    // 用户层独立处理：终止进程 + 弹窗
                    handleDangerousCommand(pid, cmdline, riskType)
                    return
                }
            }
        } catch (e: Exception) {
            // 进程可能已退出，忽略
        }
    }

    /**
     * 处理检测到的危险命令
     */
    private fun handleDangerousCommand(pid: Int, cmdline: String, riskType: String) {
        try {
            // 1. 立即终止进程
            Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -9 $pid"))
            Log.i(TAG, "Killed dangerous process pid=$pid")

            // 2. 记录日志
            File(LOG_FILE_PATH).appendText(
                "${System.currentTimeMillis()}: BLOCKED pid=$pid type=$riskType cmd=$cmdline\n"
            )

            // 3. 弹窗通知（在主线程）
            scope.launch(Dispatchers.Main) {
                showBlockedNotification(riskType, cmdline, pid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle dangerous command", e)
        }
    }

    /**
     * 读取拦截日志内容
     */
    private fun readLogContent(): String {
        return try {
            val logFile = File(LOG_FILE_PATH)
            if (logFile.exists()) {
                logFile.readText().lines().takeLast(50).joinToString("\n")
            } else {
                "暂无日志记录"
            }
        } catch (e: Exception) {
            "读取日志失败: ${e.message}"
        }
    }

    /**
     * 显示被拦截的通知（简化版，不需要悬浮窗权限）
     */
    private fun showBlockedNotification(riskType: String, cmdline: String, pid: Int) {
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val view = LayoutInflater.from(this).inflate(R.layout.dialog_anti_brick, null)

            view.findViewById<TextView>(R.id.tv_risk_title).text = getRiskTitle(riskType)
            view.findViewById<TextView>(R.id.tv_risk_reason).text =
                "用户层拦截：${getRiskReason(riskType)}"
            view.findViewById<TextView>(R.id.tv_cmdline).text = cmdline
            view.findViewById<TextView>(R.id.tv_pid_info).text = "PID: $pid | 已自动终止"

            // 用户层模式下只有"知道了"按钮
            view.findViewById<Button>(R.id.btn_allow).apply {
                text = "知道了"
                setOnClickListener {
                    try { wm.removeView(view) } catch (_: Exception) {}
                }
            }
            view.findViewById<Button>(R.id.btn_deny).apply {
                text = "查看日志"
                setOnClickListener {
                    // 显示日志内容Toast
                    val logContent = readLogContent()
                    Toast.makeText(this@AntiBrickUserService, logContent, Toast.LENGTH_LONG).show()
                    try { wm.removeView(view) } catch (_: Exception) {}
                }
            }

            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.CENTER
                dimAmount = 0.7f
            }

            wm.addView(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification", e)
        }
    }

    private fun getRiskTitle(riskType: String): String = when (riskType) {
        "RM_RF_ROOT" -> "⚠ 已拦截：删除整个文件系统"
        "DD_BLOCK" -> "⚠ 已拦截：写入块设备"
        "DD_ZERO" -> "⚠ 已拦截：擦除块设备"
        "MKFS" -> "⚠ 已拦截：格式化分区"
        "FDISK", "PARTED" -> "⚠ 已拦截：修改分区表"
        "FLASH" -> "⚠ 已拦截：刷写系统分区"
        "RECOVERY" -> "⚠ 已拦截：恢复出厂设置"
        else -> "⚠ 已拦截：高危操作"
    }

    private fun getRiskReason(riskType: String): String = when (riskType) {
        "RM_RF_ROOT" -> "rm -rf / 将删除设备上所有文件"
        "DD_BLOCK" -> "dd 命令正在向块设备写入数据"
        "DD_ZERO" -> "dd 命令正在用零填充块设备"
        "MKFS" -> "格式化命令将删除分区上的所有数据"
        "FDISK", "PARTED" -> "分区操作可能破坏分区表"
        "FLASH" -> "刷写命令可能覆盖系统分区"
        "RECOVERY" -> "恢复出厂设置将清除所有用户数据"
        else -> "检测到可能损坏设备的高危操作"
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorJob?.cancel()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.i(TAG, "User-layer anti-brick stopped")
    }
}
