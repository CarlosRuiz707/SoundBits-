package com.soundbits.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.soundbits.app.data.MusicRepository
import com.soundbits.app.data.local.FavoritesRepository
import com.soundbits.app.data.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Snapshot inmutable del reproductor. MutableStateFlow descarta emisiones iguales (data class). */
@Immutable
data class PlayerUiState(
    val current: Track? = null,
    val isPlaying: Boolean = false,   // "el usuario quiere sonido" (play o buffering con play pendiente)
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null,
)

/**
 * Se conecta al PlaybackService con un MediaController y expone el estado como StateFlow.
 * - El controller se libera en onCleared() => sin fugas.
 * - La posición NO vive en PlayerUiState (cambiaría 2 veces por segundo y recompondría todo):
 *   se expone aparte en [positionTicks], y solo la recolectan los composables que la dibujan.
 */
class PlayerViewModel(
    context: Context,
    private val repository: MusicRepository,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    val favoriteIds: StateFlow<Set<String>> = favorites.tracks
        .map { list -> list.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Cold flow: cada collector corre su propio bucle y se cancela con el ciclo de vida de la UI. */
    val positionTicks: Flow<Long> = flow {
        while (true) {
            val c = controller
            emit(c?.currentPosition ?: 0L)
            delay(if (c?.isPlaying == true) 500L else 2_000L)
        }
    }.distinctUntilChanged()

    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private val controllerFuture: ListenableFuture<MediaController>

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)

        // Si una pista falla (enlace caducado, backend caído), salta a la siguiente en vez de quedarse mudo.
        override fun onPlayerError(error: PlaybackException) {
            controller?.takeIf { it.hasNextMediaItem() }?.apply {
                seekToNextMediaItem(); prepare(); play()
            }
        }
    }

    init {
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture.addListener({
            val c = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            publish(c)
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(appContext))
    }

    // ---- Comandos ---------------------------------------------------------------------------

    /** Reemplaza la cola por [tracks] y arranca en [startIndex]. Sin red: los enlaces se resuelven al cargar. */
    fun play(tracks: List<Track>, startIndex: Int) = withController { c ->
        c.setMediaItems(tracks.map(repository::toMediaItem), startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() = withController { c ->
        if (c.isActive()) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch { favorites.toggle(track) }
    }

    // ---- Internos ---------------------------------------------------------------------------

    private fun withController(block: (MediaController) -> Unit) {
        controller?.let(block) ?: run { pending += block }
    }

    private fun Player.isActive() =
        playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)

    private fun publish(p: Player) {
        _state.value = PlayerUiState(
            current = p.currentMediaItem?.toTrack(),
            isPlaying = p.isActive(),
            isBuffering = p.playbackState == Player.STATE_BUFFERING,
            durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            hasNext = p.hasNextMediaItem(),
            hasPrevious = p.hasPreviousMediaItem(),
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            error = p.playerError?.localizedMessage,
        )
    }

    private fun MediaItem.toTrack() = Track(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString().orEmpty(),
        art = mediaMetadata.artworkUri?.toString().orEmpty(),
    )

    override fun onCleared() {
        controller?.removeListener(listener)
        controller = null
        pending.clear()
        MediaController.releaseFuture(controllerFuture)
    }
}
