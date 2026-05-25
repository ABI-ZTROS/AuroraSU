package com.ztros.ztrosu.ui.util

import com.ztros.ztrosu.Natives

/**
 * Kernel detection utility for ZTR_OS and KSU compatibility mode.
 */
object KernelDetect {

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
     * - Default is KSU_COMPAT.
     */
    fun getKernelMode(): KernelMode {
        return if (isZtrOsKernel()) {
            KernelMode.ZTR_OS
        } else {
            KernelMode.KSU_COMPAT
        }
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
