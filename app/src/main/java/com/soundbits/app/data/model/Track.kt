package com.soundbits.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Canción tal como la devuelve `GET /search` del backend (mismo contrato que tu ytmusic_api_server.py). */
@Immutable
@Serializable
data class Track(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val art: String = "",
    val duration: Int = 0, // segundos
)

/** Respuesta de `GET /stream/{id}`: enlace directo de audio (.m4a / .opus). */
@Serializable
data class StreamInfo(
    val url: String,
    val mime: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
)
