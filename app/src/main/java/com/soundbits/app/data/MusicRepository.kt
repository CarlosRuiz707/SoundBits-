package com.soundbits.app.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.soundbits.app.data.model.Track
import com.soundbits.app.data.remote.MusicApi
import java.util.concurrent.ConcurrentHashMap

const val STREAM_SCHEME = "soundbits"

/**
 * Puente entre el backend Python y Media3.
 *
 *  1. [search] pide canciones al backend.
 *  2. [toMediaItem] crea un MediaItem SOLO con metadatos (sin red) y un URI lógico `soundbits://stream/{id}`.
 *  3. Cuando ExoPlayer necesita bytes, el ResolvingDataSource llama a [resolveStreamUrl], que obtiene
 *     el enlace directo `.m4a/.opus` del backend y ExoPlayer lo abre.
 *
 * Así una cola de 25 canciones no dispara 25 extracciones de yt-dlp, y los enlaces (que caducan en
 * horas y suelen ir atados a una IP) se resuelven justo antes de reproducir.
 */
class MusicRepository(private val api: MusicApi) {

    private class CachedStream(val url: String, val expiresAtMs: Long)

    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    suspend fun search(query: String, limit: Int = 20): List<Track> =
        api.search(query, limit).distinctBy { it.id }

    fun toMediaItem(track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.id)
            .setUri("$STREAM_SCHEME://stream/${track.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(track.art.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    /** Bloqueante a propósito: se invoca desde el hilo de carga de ExoPlayer. */
    fun resolveStreamUrl(id: String): String {
        val now = System.currentTimeMillis()
        streamCache[id]?.takeIf { it.expiresAtMs > now }?.let { return it.url }
        val info = api.streamBlocking(id)
        val ttlMs = (info.expiresIn - SAFETY_MARGIN_S).coerceAtLeast(60) * 1000
        streamCache[id] = CachedStream(info.url, now + ttlMs)
        return info.url
    }

    private companion object {
        const val SAFETY_MARGIN_S = 300L
    }
}
