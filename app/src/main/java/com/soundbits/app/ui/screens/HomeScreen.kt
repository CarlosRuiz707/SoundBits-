package com.soundbits.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soundbits.app.AppViewModelProvider
import com.soundbits.app.data.MusicRepository
import com.soundbits.app.data.model.Track
import com.soundbits.app.ui.components.CenteredMessage
import com.soundbits.app.ui.components.SectionTitle
import com.soundbits.app.ui.components.SongRow
import com.soundbits.app.ui.components.TrackCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

// ---------------------------------------------------------------------------------------------
// Modelo / ViewModel
// ---------------------------------------------------------------------------------------------

enum class MoodChip(val label: String, val startQuery: String, val songsQuery: String) {
    All("Todo", "hits del momento", "música popular"),
    Relax("Relax", "música relajante", "chill acústico"),
    Energy("Energía", "música energética", "hits para entrenar"),
    Focus("Concentración", "lofi para concentrarse", "música instrumental"),
    Party("Fiesta", "reggaeton éxitos", "música para fiesta"),
}

@Immutable
data class HomeUiState(
    val chip: MoodChip = MoodChip.All,
    val loading: Boolean = true,
    val error: String? = null,
    val startHere: List<Track> = emptyList(),
    val songs: List<Track> = emptyList(),
)

class HomeViewModel(private val repository: MusicRepository) : ViewModel() {

    private data class Request(val chip: MoodChip, val nonce: Int)

    private val request = MutableStateFlow(Request(MoodChip.All, 0))

    /** flatMapLatest cancela la carga anterior si el usuario cambia de chip rápidamente. */
    val state: StateFlow<HomeUiState> = request
        .flatMapLatest { req ->
            flow {
                emit(HomeUiState(chip = req.chip, loading = true))
                emit(load(req.chip))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private suspend fun load(chip: MoodChip): HomeUiState = try {
        coroutineScope {
            val start = async { repository.search(chip.startQuery, 12) }
            val songs = async { repository.search(chip.songsQuery, 25) }
            HomeUiState(chip = chip, loading = false, startHere = start.await(), songs = songs.await())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        HomeUiState(chip = chip, loading = false, error = e.message ?: "No se pudo conectar con el servidor")
    }

    fun select(chip: MoodChip) = request.update { it.copy(chip = chip) }
    fun retry() = request.update { it.copy(nonce = it.nonce + 1) }
}

@Immutable
private data class Genre(val name: String, val color: Color, val query: String)

private val Genres = listOf(
    Genre("Pop", Color(0xFFD81B60), "pop hits"),
    Genre("Rock", Color(0xFF6D4C41), "rock clásico"),
    Genre("Hip hop", Color(0xFF3949AB), "hip hop"),
    Genre("Electrónica", Color(0xFF00897B), "electronic music"),
    Genre("Latina", Color(0xFFF4511E), "música latina"),
    Genre("Jazz", Color(0xFF5E35B1), "jazz"),
)

// ---------------------------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onPlay: (List<Track>, Int) -> Unit,
    onSearch: () -> Unit,
    onGenre: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SoundBits", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "Buscar") }
        }

        // Chips de filtro: LazyRow con key explícita.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(MoodChip.entries, key = { it.name }) { chip ->
                FilterChip(
                    selected = chip == state.chip,
                    onClick = { viewModel.select(chip) },
                    label = { Text(chip.label) },
                )
            }
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            when {
                state.loading -> item(key = "loading", contentType = "state") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.error != null -> item(key = "error", contentType = "state") {
                    CenteredMessage(state.error.orEmpty()) {
                        Button(onClick = viewModel::retry) { Text("Reintentar") }
                    }
                }

                else -> {
                    item(key = "start-title", contentType = "title") { SectionTitle("Empieza con esta música") }
                    item(key = "start-row", contentType = "row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(state.startHere, key = { _, t -> t.id }) { index, track ->
                                TrackCard(track, onClick = { onPlay(state.startHere, index) })
                            }
                        }
                    }

                    item(key = "genres-title", contentType = "title") { SectionTitle("Explora por género") }
                    item(key = "genres-row", contentType = "row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(Genres, key = { it.name }) { genre ->
                                Box(
                                    Modifier
                                        .width(140.dp)
                                        .height(72.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(genre.color)
                                        .clickable { onGenre(genre.query) },
                                ) {
                                    Text(genre.name, Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item(key = "songs-title", contentType = "title") { SectionTitle("Canciones para ti") }
                    itemsIndexed(state.songs, key = { _, t -> t.id }, contentType = { _, _ -> "song" }) { index, track ->
                        SongRow(track, onClick = { onPlay(state.songs, index) })
                    }
                }
            }
        }
    }
}
