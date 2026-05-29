package com.ztros.ztrosu.ui.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * Helper class for vibration feedback
 */
object VibrationHelper {
    
    private const val TAG = "VibrationHelper"
    
    /**
     * Trigger a short vibration feedback
     * @param context The context to get the Vibrator service
     * @param enabled Whether vibration feedback is enabled
     */
    fun vibrate(context: Context, enabled: Boolean) {
        if (!enabled) return
        
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Use VibrationEffect for API 26+
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            10L,  // 10ms short vibration
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    // Legacy vibration for older API
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(10L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger vibration", e)
        }
    }
    
    /**
     * Trigger a click vibration feedback (slightly stronger)
     * @param context The context to get the Vibrator service
     * @param enabled Whether vibration feedback is enabled
     */
    fun vibrateClick(context: Context, enabled: Boolean) {
        if (!enabled) return
        
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            15L,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(15L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger click vibration", e)
        }
    }
    
    /**
     * Trigger a long vibration feedback for important actions
     * @param context The context to get the Vibrator service
     * @param enabled Whether vibration feedback is enabled
     */
    fun vibrateLong(context: Context, enabled: Boolean) {
        if (!enabled) return
        
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            50L,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50L)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger long vibration", e)
        }
    }
}