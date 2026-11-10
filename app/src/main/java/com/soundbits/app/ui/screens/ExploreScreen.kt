package com.soundbits.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soundbits.app.AppViewModelProvider
import com.soundbits.app.data.MusicRepository
import com.soundbits.app.data.model.Track
import com.soundbits.app.ui.components.CenteredMessage
import com.soundbits.app.ui.components.SongRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class ExploreUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val tracks: List<Track> = emptyList(),
    val searched: Boolean = false,
)

class ExploreViewModel(private val repository: MusicRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class)
    val state: StateFlow<ExploreUiState> = _query
        .debounce(400)                // no consulta al backend en cada tecla
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.length < 2) flowOf(ExploreUiState()) else flow {
                emit(ExploreUiState(loading = true, searched = true))
                emit(
                    try {
                        ExploreUiState(tracks = repository.search(q, 25), searched = true)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ExploreUiState(error = e.message ?: "Error de red", searched = true)
                    }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExploreUiState())

    fun setQuery(value: String) { _query.value = value }
}

@Composable
fun ExploreScreen(
    initialQuery: String?,
    contentPadding: PaddingValues,
    onPlay: (List<Track>, Int) -> Unit,
    viewModel: ExploreViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    LaunchedEffect(initialQuery) { initialQuery?.let(viewModel::setQuery) }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Buscar canciones, artistas…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        )
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> CenteredMessage(state.error.orEmpty())
            state.searched && state.tracks.isEmpty() -> CenteredMessage("Sin resultados")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(state.tracks, key = { _, t -> t.id }, contentType = { _, _ -> "song" }) { index, track ->
                    SongRow(track, onClick = { onPlay(state.tracks, index) })
                }
            }
        }
    }
}
