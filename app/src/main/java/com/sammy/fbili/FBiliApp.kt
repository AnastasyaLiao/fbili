package com.sammy.fbili

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.sammy.fbili.data.Account
import com.sammy.fbili.data.Settings
import com.sammy.fbili.net.Net
import kotlinx.coroutines.launch
import java.io.File

class FBiliApp : Application(), ImageLoaderFactory {
    companion object {
        lateinit var ctx: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        Net.init(ctx)
        Account.AppScope.launch {
            runCatching { Account.init(ctx) }
            runCatching { Settings.init(ctx) }
            runCatching { Net.ensureBuvid() }
            runCatching { Account.refreshNav() }
        }
    }

    /** 低端机适配：图片内存缓存按设备堆上限 ~8% 收紧，磁盘缓存限 300MB，不与应用抢内存 */
    override fun newImageLoader(): ImageLoader {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val heapMb = am.memoryClass.takeIf { it > 0 } ?: 128
        return ImageLoader.Builder(this)
            .crossfade(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes((heapMb * 0.08 * 1024 * 1024).toInt())
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "img_cache"))
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
