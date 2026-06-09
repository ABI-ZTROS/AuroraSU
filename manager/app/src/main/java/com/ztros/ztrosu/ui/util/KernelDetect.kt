package com.ztros.ztrosu.ui.util

import android.content.Context
import com.ztros.ztrosu.Natives
import com.ztros.ztrosu.ksuApp
import java.io.File

/**
 * Kernel detection utility for ZTR_OS and KSU compatibility mode.
 */
object KernelDetect {

    private const val PREFS_NAME = "kernel_detect"
    private const val PREF_KERNEL_MODE = "kernel_mode"

    private const val SYSFS_VERSION_PATH = "/sys/kernel/ztrosu/vfs/version"

    /**
     * How the kernel module was detected.
     */
    enum class DetectionMethod {
        /** Detected via KSU ioctl (Natives.getVersionTag()) */
        KSU_IOCTL,
        /** Detected via sysfs path (/sys/kernel/ztrosu/vfs/version) */
        SYSFS,
        /** Not detected */
        NONE
    }

    private var _detectionMethod: DetectionMethod = DetectionMethod.NONE

    /**
     * Returns how the kernel module was detected.
     */
    fun getDetectionMethod(): DetectionMethod = _detectionMethod

    /**
     * Check sysfs path for phantom-lkm kernel module.
     * Returns true if /sys/kernel/ztrosu/vfs/version exists and contains a valid version number.
     */
    private fun checkSysfsKernel(): Boolean {
        val file = File(SYSFS_VERSION_PATH)
        if (!file.exists() || !file.canRead()) return false
        return try {
            val content = file.readText().trim()
            content.toIntOrNull() != null || content.toDoubleOrNull() != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if the current kernel is a ZTR_OS kernel.
     * Detection order:
     * 1. KSU ioctl via Natives.getVersionTag() (existing behavior)
     * 2. sysfs fallback: /sys/kernel/ztrosu/vfs/version contains a valid version number
     */
    fun isZtrOsKernel(): Boolean {
        // 1. First check KSU ioctl
        val tag = Natives.getVersionTag()
        if (!tag.isNullOrBlank()) {
            _detectionMethod = DetectionMethod.KSU_IOCTL
            return true
        }

        // 2. Fallback: check sysfs for phantom-lkm
        if (checkSysfsKernel()) {
            _detectionMethod = DetectionMethod.SYSFS
            return true
        }

        _detectionMethod = DetectionMethod.NONE
        return false
    }

    /**
     * Kernel mode enum representing the current operating mode.
     */
    enum class KernelMode {
        /** ZTR_OS kernel with full features */
        ZTR_OS,
        /** KernelSU compatible mode using standard ioctl interface */
        KSU_COMPAT,
        /** Unknown kernel */
        UNKNOWN
    }

    /**
     * Get the current kernel mode.
     * - If ZTR_OS kernel is detected (via KSU ioctl or sysfs), returns ZTR_OS (full features, locked).
     * - Otherwise, reads the user preference from SharedPreferences.
     * - If no preference is set and ZTR_OS is not detected, returns UNKNOWN.
     */
    fun getKernelMode(): KernelMode {
        return if (isZtrOsKernel()) {
            KernelMode.ZTR_OS
        } else {
            // 读取用户保存的偏好设置
            val prefs = ksuApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedMode = prefs.getString(PREF_KERNEL_MODE, null)
            when (savedMode) {
                "ZTR_OS" -> KernelMode.ZTR_OS
                "KSU_COMPAT" -> KernelMode.KSU_COMPAT
                else -> KernelMode.UNKNOWN
            }
        }
    }

    /**
     * Save the kernel mode preference.
     * Only effective when ZTR_OS kernel is NOT detected.
     */
    fun setKernelMode(mode: KernelMode) {
        if (isZtrOsKernel()) {
            // ZTR_OS kernel detected, mode is locked
            return
        }
        val prefs = ksuApp.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KERNEL_MODE, mode.name).apply()
    }

    /**
     * Get the display label for the current kernel mode.
     */
    fun getKernelModeLabel(): String {
        return when (getKernelMode()) {
            KernelMode.ZTR_OS -> "ZTR_OS"
            KernelMode.KSU_COMPAT -> "KSU Compatible"
            KernelMode.UNKNOWN -> "Unknown"
        }
    }

    /**
     * Check if the kernel mode is locked (i.e., ZTR_OS kernel detected).
     * When locked, the user cannot switch modes.
     */
    fun isModeLocked(): Boolean {
        return isZtrOsKernel()
    }
}
