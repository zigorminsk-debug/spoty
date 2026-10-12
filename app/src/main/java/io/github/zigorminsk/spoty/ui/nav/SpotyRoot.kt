package io.github.zigorminsk.spoty.ui.nav

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.data.repo.WaveSeed
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.album.AlbumScreen
import io.github.zigorminsk.spoty.ui.artist.ArtistScreen
import io.github.zigorminsk.spoty.ui.catalog.CatalogScreen
import io.github.zigorminsk.spoty.ui.components.MiniPlayer
import io.github.zigorminsk.spoty.ui.components.PlaylistPickerSheet
import io.github.zigorminsk.spoty.ui.components.TrackMenuActions
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.components.TrackMenuSheet
import io.github.zigorminsk.spoty.ui.genre.GenreScreen
import io.github.zigorminsk.spoty.ui.home.HomeScreen
import io.github.zigorminsk.spoty.ui.library.LibraryScreen
import io.github.zigorminsk.spoty.ui.player.PlayerScreen
import io.github.zigorminsk.spoty.ui.player.QueueScreen
import io.github.zigorminsk.spoty.ui.playlist.PlaylistScreen
import io.github.zigorminsk.spoty.ui.search.SearchScreen
import io.github.zigorminsk.spoty.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class BottomItem(val route: String, val iconRes: Int, val labelRes: Int)

private val bottomItems = listOf(
    BottomItem(Routes.HOME, R.drawable.ic_home, R.string.tab_home),
    BottomItem(Routes.SEARCH, R.drawable.ic_search, R.string.tab_search),
    BottomItem(Routes.CATALOG, R.drawable.ic_category, R.string.tab_catalog),
    BottomItem(Routes.LIBRARY, R.drawable.ic_library_music, R.string.tab_library),
)

/** Корневой экран: нижняя навигация, мини-плеер, меню трека и все маршруты. */
@Composable
fun SpotyRoot() {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val menu = remember { TrackMenuController() }
    var playlistPickFor by remember { mutableStateOf<Track?>(null) }

    val playerState by container.player.state.collectAsStateWithLifecycle()
    val progress by container.player.progress.collectAsStateWithLifecycle()
    val likedIds by container.library.likedIds.collectAsStateWithLifecycle()
    val downloadedPaths by container.library.downloadedPaths.collectAsStateWithLifecycle()

    val playbackErrorText = stringResource(R.string.playback_error)
    val waveErrorText = stringResource(R.string.wave_error)
    val addedText = stringResource(R.string.added_to_playlist)
    val downloadStartedText = stringResource(R.string.download_started)

    LaunchedEffect(Unit) {
        launch {
            container.player.errors.collect { title ->
                snackbar.showSnackbar(if (title.isBlank()) playbackErrorText else "$playbackErrorText: $title")
            }
        }
        launch {
            container.wave.failures.collect { snackbar.showSnackbar(waveErrorText) }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBars = currentRoute != Routes.PLAYER

    fun openTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val menuActions = remember(likedIds, downloadedPaths) {
        TrackMenuActions(
            liked = { it.id in likedIds },
            downloaded = { it.id in downloadedPaths },
            onPlayNext = { container.player.playNext(it) },
            onEnqueue = { container.player.enqueue(listOf(it)) },
            onToggleLike = { t -> scope.launch { container.library.toggleLike(t) } },
            onDislike = { t -> scope.launch { container.library.dislike(t) } },
            onAddToPlaylist = { playlistPickFor = it },
            onDownload = { t ->
                scope.launch {
                    container.downloads.enqueue(listOf(t))
                    snackbar.showSnackbar(downloadStartedText)
                }
            },
            onRemoveDownload = { t -> scope.launch { container.downloads.remove(t.id) } },
            onOpenArtist = { t -> t.artistId?.let { navController.navigate(Routes.artist(it)) } },
            onOpenAlbum = { t -> t.albumId?.let { navController.navigate(Routes.album(it)) } },
            onStartWave = { t ->
                container.wave.start(WaveSeed(artistId = t.artistId, artistName = t.artistName))
            },
            onShare = { t ->
                t.webUrl?.let { url ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${t.artistName} — ${t.title}\n$url")
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBars) {
                Column {
                    MiniPlayer(
                        state = playerState,
                        progress = progress,
                        onOpen = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } },
                        onPlayPause = { container.player.togglePlayPause() },
                        onNext = { container.player.next() },
                    )
                    NavigationBar {
                        bottomItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { openTab(item.route) },
                                icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                                label = { Text(stringResource(item.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.HOME) { HomeScreen(navController, menu) }
            composable(Routes.SEARCH) { SearchScreen(navController, menu) }
            composable(Routes.CATALOG) { CatalogScreen(navController, menu) }
            composable(Routes.LIBRARY) { LibraryScreen(navController, menu) }
            composable(Routes.SETTINGS) { SettingsScreen(navController, snackbar) }
            composable(Routes.PLAYER) { PlayerScreen(navController, menu) }
            composable(Routes.QUEUE) { QueueScreen(navController, menu) }
            composable(Routes.ALBUM_PATTERN) { entry ->
                AlbumScreen(Uri.decode(entry.arguments?.getString("id").orEmpty()), navController, menu)
            }
            composable(Routes.ARTIST_PATTERN) { entry ->
                ArtistScreen(Uri.decode(entry.arguments?.getString("id").orEmpty()), navController, menu)
            }
            composable(Routes.PLAYLIST_PATTERN) { entry ->
                PlaylistScreen(entry.arguments?.getString("id")?.toLongOrNull() ?: -1L, navController, menu)
            }
            composable(Routes.GENRE_PATTERN) { entry ->
                GenreScreen(entry.arguments?.getString("id").orEmpty(), navController, menu)
            }
        }
    }

    TrackMenuSheet(menu, menuActions)

    playlistPickFor?.let { track ->
        var playlists by remember { mutableStateOf(listOf<io.github.zigorminsk.spoty.data.repo.PlaylistSummary>()) }
        LaunchedEffect(track.id) { playlists = container.library.playlists().first() }
        PlaylistPickerSheet(
            playlists = playlists,
            onPick = { id ->
                scope.launch {
                    container.library.addToPlaylist(id, listOf(track))
                    snackbar.showSnackbar(addedText)
                }
            },
            onCreate = { name ->
                scope.launch {
                    container.library.createPlaylist(name, listOf(track))
                    snackbar.showSnackbar(addedText)
                }
            },
            onDismiss = { playlistPickFor = null },
        )
    }
}
