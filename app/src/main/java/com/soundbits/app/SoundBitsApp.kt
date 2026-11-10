package com.soundbits.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.soundbits.app.di.AppContainer

class SoundBitsApp : Application(), ImageLoaderFactory {

    val container by lazy { AppContainer(this) }

    /** Un único ImageLoader: 12 % de RAM, 64 MB de disco y RGB_565 para carátulas ligeras. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.12).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("coil"))
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .allowRgb565(true)
        .respectCacheHeaders(false)
        .crossfade(150)
        .build()
}
