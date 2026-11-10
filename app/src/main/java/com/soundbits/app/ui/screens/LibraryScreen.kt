package com.soundbits.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soundbits.app.AppViewModelProvider
import com.soundbits.app.data.local.FavoritesRepository
import com.soundbits.app.data.model.Track
import com.soundbits.app.ui.components.CenteredMessage
import com.soundbits.app.ui.components.SectionTitle
import com.soundbits.app.ui.components.SongRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(favorites: FavoritesRepository) : ViewModel() {
    val favorites: StateFlow<List<Track>> = favorites.tracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun LibraryScreen(
    contentPadding: PaddingValues,
    onPlay: (List<Track>, Int) -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        SectionTitle("Tus favoritos")
        if (favorites.isEmpty()) {
            CenteredMessage("Toca el corazón del reproductor para guardar canciones aquí.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(favorites, key = { _, t -> t.id }, contentType = { _, _ -> "song" }) { index, track ->
                    SongRow(track, onClick = { onPlay(favorites, index) })
                }
            }
        }
    }
}
