package com.soundbits.app.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Estado del panel "mini-reproductor <-> pantalla completa".
 *
 * [progress] va de 0 (colapsado) a 1 (expandido). Solo se LEE en lambdas de layout (`Modifier.offset { }`)
 * o en `derivedStateOf`, de modo que arrastrar el panel no recompone ningún composable y el trabajo
 * por frame es un simple cambio de offset (sin capas offscreen ni RenderEffects en la GPU).
 */
@Stable
class PlayerSheetState(private val scope: CoroutineScope) {

    val progress = Animatable(0f)
    var heightPx by mutableFloatStateOf(1f)

    /** Cambia solo al cruzar 0 => la UI se recompone dos veces por gesto, no por frame. */
    val isVisible by derivedStateOf { progress.value > 0f }

    val draggableState = DraggableState { deltaPx ->
        scope.launch { progress.snapTo((progress.value - deltaPx / heightPx).coerceIn(0f, 1f)) }
    }

    fun expand() { scope.launch { progress.animateTo(1f, SNAP_SPEC) } }
    fun collapse() { scope.launch { progress.animateTo(0f, SNAP_SPEC) } }

    /** Velocidad en px/s: negativa = hacia arriba. */
    suspend fun settle(velocityPxPerSec: Float) {
        val target = when {
            velocityPxPerSec < -FLING_VELOCITY -> 1f
            velocityPxPerSec > FLING_VELOCITY -> 0f
            progress.value > 0.5f -> 1f
            else -> 0f
        }
        progress.animateTo(target, SNAP_SPEC)
    }

    private companion object {
        const val FLING_VELOCITY = 900f
        val SNAP_SPEC = spring<Float>(stiffness = Spring.StiffnessMediumLow)
    }
}
