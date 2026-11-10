package com.soundbits.app.di

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.soundbits.app.BuildConfig
import com.soundbits.app.data.MusicRepository
import com.soundbits.app.data.local.FavoritesRepository
import com.soundbits.app.data.remote.MusicApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Inyección manual de dependencias: sin Hilt/KSP, arranque rápido y cero magia. */
@OptIn(UnstableApi::class)
class AppContainer(app: Application) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS) // yt-dlp puede tardar unos segundos en extraer
        .build()

    val repository = MusicRepository(MusicApi(httpClient, BuildConfig.BASE_URL, json))
    val favorites = FavoritesRepository(app, json)

    /** Caché LRU de audio en disco (128 MB): menos datos móviles y menos radio encendida. */
    val mediaCache: SimpleCache by lazy {
        SimpleCache(
            File(app.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(128L * 1024 * 1024),
            StandaloneDatabaseProvider(app),
        )
    }
}
