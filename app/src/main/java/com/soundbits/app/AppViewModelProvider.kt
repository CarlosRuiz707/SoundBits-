package com.soundbits.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.soundbits.app.playback.PlayerViewModel
import com.soundbits.app.ui.screens.ExploreViewModel
import com.soundbits.app.ui.screens.HomeViewModel
import com.soundbits.app.ui.screens.LibraryViewModel

fun CreationExtras.soundBitsApp(): SoundBitsApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SoundBitsApp

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(soundBitsApp().container.repository) }
        initializer { ExploreViewModel(soundBitsApp().container.repository) }
        initializer { LibraryViewModel(soundBitsApp().container.favorites) }
        initializer {
            val container = soundBitsApp().container
            PlayerViewModel(soundBitsApp(), container.repository, container.favorites)
        }
    }
}
