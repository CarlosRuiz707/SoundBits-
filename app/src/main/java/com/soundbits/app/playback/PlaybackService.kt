@file:OptIn(UnstableApi::class)

package com.soundbits.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.soundbits.app.MainActivity
import com.soundbits.app.SoundBitsApp

/**
 * Dueño ÚNICO del ExoPlayer. Media3 se encarga de:
 *  - promover el servicio a foreground con la MediaNotification (controles, carátula, lock screen),
 *  - botones de auriculares/Bluetooth/Android Auto vía MediaSession,
 *  - mantener la reproducción con la pantalla apagada (WAKE_MODE_NETWORK).
 * ExoPlayer se encarga del audio focus (llamadas, otras apps) y de pausar al desconectar auriculares.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as SoundBitsApp).container

        // Solo renderer de audio: sin video/texto/metadata => menos memoria y arranque más rápido.
        val audioOnly = RenderersFactory { handler, _, audioListener, _, _ ->
            arrayOf<Renderer>(MediaCodecAudioRenderer(this, MediaCodecSelector.DEFAULT, handler, audioListener))
        }

        val player = ExoPlayer.Builder(this, audioOnly)
            .setMediaSourceFactory(buildMediaSourceFactory(this, container.repository, container.mediaCache))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)   // pausa al desenchufar auriculares / perder A2DP
            .setWakeMode(C.WAKE_MODE_NETWORK)    // WifiLock + WakeLock solo mientras reproduce
            .setLoadControl(
                // Buffers cortos: audio comprimido no necesita 50 s de colchón.
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(20_000, 40_000, 1_500, 3_000)
                    .build()
            )
            .build()

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Si el usuario cierra la app desde recientes y no hay nada sonando, el servicio se apaga. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
