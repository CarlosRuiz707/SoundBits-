package com.soundbits.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Rutas tipadas (Navigation 2.8): sin strings, los argumentos viajan serializados.
@Serializable data object HomeRoute
@Serializable data object SamplesRoute
@Serializable data class ExploreRoute(val query: String? = null)
@Serializable data object PodcastRoute
@Serializable data object LibraryRoute

enum class TopLevelDestination(val route: Any, val routeClass: kotlin.reflect.KClass<*>, val label: String, val icon: ImageVector) {
    Home(HomeRoute, HomeRoute::class, "Inicio", Icons.Filled.Home),
    Samples(SamplesRoute, SamplesRoute::class, "Muestras", Icons.Filled.GraphicEq),
    Explore(ExploreRoute(), ExploreRoute::class, "Explorar", Icons.Filled.Explore),
    Podcast(PodcastRoute, PodcastRoute::class, "Podcast", Icons.Filled.Podcasts),
    Library(LibraryRoute, LibraryRoute::class, "Biblioteca", Icons.Filled.LibraryMusic),
}
