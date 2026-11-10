@file:OptIn(UnstableApi::class)

package com.soundbits.app.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.soundbits.app.data.MusicRepository
import com.soundbits.app.data.STREAM_SCHEME
import java.io.IOException

/**
 * Cadena: CacheDataSource -> ResolvingDataSource -> DefaultDataSource(HTTP).
 *
 * La clave de caché se calcula con el URI lógico `soundbits://stream/{id}` (estable), así que un
 * enlace directo caducado no invalida el audio ya cacheado.
 */
fun buildMediaSourceFactory(context: Context, repository: MusicRepository, cache: Cache): MediaSource.Factory {
    val http = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(15_000)
        .setAllowCrossProtocolRedirects(true)

    val resolver = ResolvingDataSource.Resolver { dataSpec ->
        if (dataSpec.uri.scheme == STREAM_SCHEME) {
            val id = dataSpec.uri.lastPathSegment ?: throw IOException("MediaItem sin id: ${dataSpec.uri}")
            dataSpec.withUri(Uri.parse(repository.resolveStreamUrl(id)))
        } else dataSpec
    }

    val resolving = ResolvingDataSource.Factory(DefaultDataSource.Factory(context, http), resolver)
    val cached = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(resolving)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    return DefaultMediaSourceFactory(cached)
}
