package com.aurora.su

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * @author weishu
 * @date 2022/12/8.
 */
object Natives {
    const val MINIMAL_SUPPORTED_KERNEL = 32377

    external fun getFullVersion(): String
    const val MINIMAL_SUPPORTED_KERNEL_FULL = "v4.0.0"

    const val KERNEL_SU_DOMAIN = "u:r:ksu:s0"
    const val ROOT_UID = 0
    const val ROOT_GID = 0

    fun isVersionLessThan(v1Full: String, v2Full: String): Boolean {
        fun extractVersionParts(version: String): List<Int> {
            val match = Regex("""v\d+(\.\d+)*""").find(version)
            val simpleVersion = match?.value ?: version
            return simpleVersion.trimStart('v').split('.').map { it.toIntOrNull() ?: 0 }
        }
        val v1Parts = extractVersionParts(v1Full)
        val v2Parts = extractVersionParts(v2Full)
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        for (i in 0 until maxLength) {
            val num1 = v1Parts.getOrElse(i) { 0 }
            val num2 = v2Parts.getOrElse(i) { 0 }
            if (num1 != num2) return num1 < num2
        }
        return false
    }

    var libraryLoaded = false
        private set

    init {
        try {
            System.loadLibrary("kernelsu")
            libraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.w("Natives", "kernelsu library not available: ${e.message}")
            libraryLoaded = false
        }
    }

    /**
     * Safe call wrapper - returns default value if library is not loaded or call fails.
     */
    inline fun <T> safe(default: T, block: () -> T): T {
        if (!libraryLoaded) return default
        return try { block() } catch (_: UnsatisfiedLinkError) { default } catch (_: Exception) { default }
    }

    val version: Int
        external get

    val isSafeMode: Boolean
        external get

    val isLkmMode: Boolean
        external get

    val isLateLoadMode: Boolean
        external get

    val isManager: Boolean
        external get

    val isPrBuild: Boolean
        external get

    external fun uidShouldUmount(uid: Int): Boolean
    external fun getAppProfile(key: String?, uid: Int): Profile
    external fun setAppProfile(profile: Profile?): Boolean
    external fun isSuEnabled(): Boolean
    external fun setSuEnabled(enabled: Boolean): Boolean
    external fun isKernelUmountEnabled(): Boolean
    external fun setKernelUmountEnabled(enabled: Boolean): Boolean
    external fun isSelinuxHideEnabled(): Boolean
    external fun setSelinuxHideEnabled(enabled: Boolean): Int
    external fun getUserName(uid: Int): String?
    external fun getSuperuserCount(): Int
    external fun getHookType(): String

    private const val NON_ROOT_DEFAULT_PROFILE_KEY = "$"
    private const val NOBODY_UID = 9999

    fun setDefaultUmountModules(umountModules: Boolean): Boolean {
        return setAppProfile(Profile(NON_ROOT_DEFAULT_PROFILE_KEY, NOBODY_UID, false, umountModules = umountModules))
    }

    fun isDefaultUmountModules(): Boolean {
        return getAppProfile(NON_ROOT_DEFAULT_PROFILE_KEY, NOBODY_UID).umountModules
    }

    fun requireNewKernel(): Boolean {
        if (version != -1 && version < MINIMAL_SUPPORTED_KERNEL) return true
        return isVersionLessThan(getFullVersion(), MINIMAL_SUPPORTED_KERNEL_FULL)
    }

    @Keep
    @Immutable
    @Parcelize
    @Serializable
    data class Profile(
        val name: String,
        val currentUid: Int = 0,
        val allowSu: Boolean = false,
        val rootUseDefault: Boolean = true,
        val rootTemplate: String? = null,
        val uid: Int = ROOT_UID,
        val gid: Int = ROOT_GID,
        val groups: List<Int> = mutableListOf(),
        val capabilities: List<Int> = mutableListOf(),
        val context: String = KERNEL_SU_DOMAIN,
        val namespace: Int = Namespace.INHERITED.ordinal,
        val nonRootUseDefault: Boolean = true,
        val umountModules: Boolean = true,
        var rules: String = "",
    ) : Parcelable {
        enum class Namespace {
            INHERITED, GLOBAL, INDIVIDUAL,
        }
        constructor() : this("")
    }
}
