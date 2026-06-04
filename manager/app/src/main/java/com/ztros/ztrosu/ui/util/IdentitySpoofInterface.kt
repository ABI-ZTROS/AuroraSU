package com.ztros.ztrosu.ui.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "IdentitySpoof"

/**
 * 身份伪装数据模型
 */

enum class SpoofIdType(val code: Int, val displayName: String) {
    MAC_WIFI(0, "WiFi MAC"),
    MAC_BT(1, "蓝牙 MAC"),
    ANDROID_ID(2, "Android ID"),
    BUILD_SERIAL(3, "序列号"),
    IMEI(4, "IMEI"),
    IMSI(5, "IMSI"),
    AD_ID(6, "广告 ID"),
    GSF_ID(7, "GSF ID"),
    WIDEWINE(8, "Widevine"),
    FINGERPRINT(9, "指纹")
}

enum class SpoofStrategy(val code: Int, val displayName: String) {
    FIXED(0, "固定值"),
    RANDOM(1, "随机"),
    RANDOM_PER_APP(2, "每应用随机"),
    ROTATE(3, "定时轮换")
}

/**
 * 伪装规则
 */
data class SpoofRule(
    val id: Int,
    val packageName: String,
    val idType: SpoofIdType,
    val strategy: SpoofStrategy,
    val currentValue: String,
    val enabled: Boolean = true
)

/**
 * 身份伪装接口
 *
 * 通过 sysfs 与内核身份伪装模块通信
 */
object IdentitySpoofInterface {

    private val spoofBase = "/sys/kernel/ztrosu/spoof"

    // ==================== 规则管理 ====================

    /**
     * 获取所有伪装规则
     */
    suspend fun getRules(): List<SpoofRule> = withContext(Dispatchers.IO) {
        try {
            val content = File("$spoofBase/rules").readText()
            content.lines().drop(1).mapNotNull { parseRuleLine(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read rules", e)
            emptyList()
        }
    }

    /**
     * 添加伪装规则
     */
    suspend fun addRule(
        packageName: String,
        idType: SpoofIdType,
        strategy: SpoofStrategy,
        fakeValue: String = "",
        rotateIntervalSec: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val value = if (fakeValue.isBlank()) "RANDOM" else fakeValue
            val cmd = "$packageName:${idType.name}:${strategy.code}:$value:$rotateIntervalSec"
            File("$spoofBase/add").writeText(cmd)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add rule", e)
            false
        }
    }

    /**
     * 删除伪装规则
     */
    suspend fun removeRule(ruleId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            File("$spoofBase/remove").writeText(ruleId.toString())
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove rule", e)
            false
        }
    }

    /**
     * 手动轮换所有 ROTATE 规则
     */
    suspend fun rotateAll(): Boolean = withContext(Dispatchers.IO) {
        try {
            File("$spoofBase/rotate").writeText("1")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to rotate", e)
            false
        }
    }

    /**
     * 获取模块开关状态
     */
    suspend fun isEnabled(): Boolean = withContext(Dispatchers.IO) {
        try {
            File("$spoofBase/enabled").readText().trim() == "1"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 设置模块开关
     */
    suspend fun setEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            File("$spoofBase/enabled").writeText(if (enabled) "1" else "0")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set enabled", e)
            false
        }
    }

    // ==================== 私有方法 ====================

    private fun parseRuleLine(line: String): SpoofRule? {
        val parts = line.split(":")
        if (parts.size < 6) return null

        return try {
            val id = parts[0].toIntOrNull() ?: return null
            val packageName = parts[1]
            val idType = SpoofIdType.values().find { it.name == parts[2] } ?: return null
            val strategy = SpoofStrategy.values().find { it.code == parts[3].toIntOrNull() } ?: return null
            val value = parts[4]
            val enabled = parts[5] == "1"

            SpoofRule(id, packageName, idType, strategy, value, enabled)
        } catch (e: Exception) {
            null
        }
    }
}
