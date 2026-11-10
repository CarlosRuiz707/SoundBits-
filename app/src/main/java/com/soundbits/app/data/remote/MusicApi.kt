package com.soundbits.app.data.remote

import com.soundbits.app.data.model.StreamInfo
import com.soundbits.app.data.model.Track
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

/** Cliente HTTP mínimo (OkHttp + kotlinx.serialization) hacia el microservicio Python. */
class MusicApi(
    private val client: OkHttpClient,
    baseUrl: String,
    private val json: Json,
) {
    private val base = baseUrl.toHttpUrl()

    suspend fun search(query: String, limit: Int = 20): List<Track> {
        val url = base.newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("type", "songs")
            .addQueryParameter("limit", limit.toString())
            .build()
        return json.decodeFromString(ListSerializer(Track.serializer()), client.newCall(get(url)).await())
    }

    /** Variante suspendida (cancelable) para uso desde coroutines. */
    suspend fun stream(id: String): StreamInfo =
        json.decodeFromString(StreamInfo.serializer(), client.newCall(get(streamUrl(id))).await())

    /**
     * Variante BLOQUEANTE: la llama el `ResolvingDataSource` de ExoPlayer, que ya corre en el hilo
     * de carga del reproductor (nunca en el hilo principal).
     */
    fun streamBlocking(id: String): StreamInfo =
        json.decodeFromString(StreamInfo.serializer(), client.newCall(get(streamUrl(id))).execute().bodyOrThrow())

    private fun streamUrl(id: String): HttpUrl =
        base.newBuilder().addPathSegment("stream").addPathSegment(id).build()

    private fun get(url: HttpUrl) = Request.Builder().url(url).get().build()
}

private fun Response.bodyOrThrow(): String = use { r ->
    if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
    r.body?.string().orEmpty()
}

private suspend fun Call.await(): String = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            val result = runCatching { response.bodyOrThrow() }
            if (cont.isActive) cont.resumeWith(result)
        }
    })
}
