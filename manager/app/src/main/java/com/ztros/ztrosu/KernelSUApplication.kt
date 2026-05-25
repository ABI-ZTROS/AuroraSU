package com.ztros.ztrosu

import android.app.Application
import android.system.Os
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import coil.Coil
import coil.ImageLoader
import com.ztros.ztrosu.ui.util.createRootShellBuilder
import com.ztros.ztrosu.ui.viewmodel.ModuleViewModel
import com.ztros.ztrosu.ui.viewmodel.SuperUserViewModel
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.*

lateinit var ksuApp: KernelSUApplication

class KernelSUApplication : Application(), ViewModelStoreOwner {

    lateinit var okhttpClient: OkHttpClient
    private val appViewModelStore by lazy { ViewModelStore() }

    override fun onCreate() {
        super.onCreate()
        ksuApp = this
        Shell.setDefaultBuilder(createRootShellBuilder(true))
        Shell.enableVerboseLogging = BuildConfig.DEBUG

        val context = this
        val iconSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(iconSize, false, context))
                }
                .build()
        )

        val webroot = File(dataDir, "webroot")
        if (!webroot.exists()) {
            webroot.mkdir()
        }

        // Provide working env for rust's temp_dir()
        Os.setenv("TMPDIR", cacheDir.absolutePath, true)

        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "ZTR-OS/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }.build()

        // ZTR_OS SU: Check SuperKey status on first launch (background logging only)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val versionTag = Natives.getVersionTag()
                if (!versionTag.isNullOrBlank()) {
                    // ZTR_OS kernel detected
                    val isSuperKeyActive = Natives.isSuperKeyActive()
                    Log.i(
                        "KernelSUApplication",
                        "ZTR_OS kernel detected (tag=$versionTag), SuperKey active: $isSuperKeyActive"
                    )
                }
            } catch (e: Exception) {
                Log.w("KernelSUApplication", "Failed to check SuperKey status: ${e.message}")
            }
        }
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore


}
