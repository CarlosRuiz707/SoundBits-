package com.soundbits.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.soundbits.app.navigation.ExploreRoute
import com.soundbits.app.navigation.HomeRoute
import com.soundbits.app.navigation.LibraryRoute
import com.soundbits.app.navigation.PodcastRoute
import com.soundbits.app.navigation.SamplesRoute
import com.soundbits.app.navigation.TopLevelDestination
import com.soundbits.app.playback.PlayerViewModel
import com.soundbits.app.ui.components.MiniPlayer
import com.soundbits.app.ui.screens.ExploreScreen
import com.soundbits.app.ui.screens.HomeScreen
import com.soundbits.app.ui.screens.LibraryScreen
import com.soundbits.app.ui.screens.NowPlayingScreen
import com.soundbits.app.ui.screens.PlaceholderScreen
import kotlin.math.roundToInt

@Composable
fun AppRoot(playerViewModel: PlayerViewModel) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val sheet = remember { PlayerSheetState(scope) }

    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteIds.collectAsStateWithLifecycle()
    val current = player.current

    // derivedStateOf: el botón solo se recompone si cambia el booleano, no cada vez que cambia el Set.
    val isFavorite by remember(current?.id) {
        derivedStateOf { current != null && current.id in favoriteIds }
    }

    // El mismo gesto vertical se usa en el mini-reproductor (expandir) y en la pantalla completa (colapsar).
    val dragModifier = Modifier.draggable(
        state = sheet.draggableState,
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> sheet.settle(velocity) },
    )

    BackHandler(enabled = sheet.isVisible) { sheet.collapse() }

    Box(Modifier.fillMaxSize().onSizeChanged { sheet.heightPx = it.height.toFloat() }) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    // Mini-reproductor anclado JUSTO sobre la NavigationBar.
                    AnimatedVisibility(
                        visible = current != null,
                        enter = slideInVertically { it } + fadeIn(tween(150)),
                        exit = slideOutVertically { it } + fadeOut(tween(150)),
                    ) {
                        if (current != null) {
                            MiniPlayer(
                                track = current,
                                isPlaying = player.isPlaying,
                                isBuffering = player.isBuffering,
                                isFavorite = isFavorite,
                                durationMs = player.durationMs,
                                positionTicks = playerViewModel.positionTicks,
                                onToggleFavorite = { playerViewModel.toggleFavorite(current) },
                                onTogglePlay = playerViewModel::togglePlayPause,
                                onClick = sheet::expand,
                                modifier = dragModifier,
                            )
                        }
                    }
                    BottomBar(navController)
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(120)) },
                exitTransition = { fadeOut(tween(120)) },
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        contentPadding = padding,
                        onPlay = playerViewModel::play,
                        onSearch = { navController.navigate(ExploreRoute()) { launchSingleTop = true } },
                        onGenre = { query -> navController.navigate(ExploreRoute(query)) { launchSingleTop = true } },
                    )
                }
                composable<SamplesRoute> { PlaceholderScreen("Muestras", padding) }
                composable<ExploreRoute> { entry ->
                    ExploreScreen(
                        initialQuery = entry.toRoute<ExploreRoute>().query,
                        contentPadding = padding,
                        onPlay = playerViewModel::play,
                    )
                }
                composable<PodcastRoute> { PlaceholderScreen("Podcast", padding) }
                composable<LibraryRoute> { LibraryScreen(contentPadding = padding, onPlay = playerViewModel::play) }
            }
        }

        // Pantalla completa: solo existe en composición mientras progress > 0.
        // El desplazamiento se aplica en la fase de layout: arrastrar no recompone nada.
        if (sheet.isVisible && current != null) {
            NowPlayingScreen(
                track = current,
                state = player,
                isFavorite = isFavorite,
                positionTicks = playerViewModel.positionTicks,
                onCollapse = sheet::collapse,
                onTogglePlay = playerViewModel::togglePlayPause,
                onNext = playerViewModel::next,
                onPrevious = playerViewModel::previous,
                onSeek = playerViewModel::seekTo,
                onToggleFavorite = { playerViewModel.toggleFavorite(current) },
                onToggleShuffle = playerViewModel::toggleShuffle,
                onCycleRepeat = playerViewModel::cycleRepeat,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, ((1f - sheet.progress.value) * sheet.heightPx).roundToInt()) }
                    .then(dragModifier),
            )
        }
    }
}

@Composable
private fun BottomBar(navController: NavController) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    NavigationBar(Modifier.fillMaxWidth()) {
        TopLevelDestination.entries.forEach { item ->
            NavigationBarItem(
                selected = destination?.hasRoute(item.routeClass) == true,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}
