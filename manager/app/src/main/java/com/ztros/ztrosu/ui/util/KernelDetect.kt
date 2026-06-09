package com.ztros.ztrosu.ui.util

import android.content.Context
import com.ztros.ztrosu.Natives
import com.ztros.ztrosu.ksuApp

/**
 * Kernel detection utility for ZTR_OS and KSU compatibility mode.
 */
object KernelDetect {

    private const val PREFS_NAME = "kernel_detect"
    private const val PREF_KERNEL_MODE = "kernel_mode"

    /**
     * Check if the current kernel is a ZTR_OS kernel.
     * Determined by checking if Natives.getVersionTag() returns a non-null, non-blank tag.
     */
    fun isZtrOsKernel(): Boolean {
        return !Natives.getVersionTag().isNullOrBlank()
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
     * - If ZTR_OS kernel is detected, returns ZTR_OS (full features, locked).
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
