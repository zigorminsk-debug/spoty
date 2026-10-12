package io.github.zigorminsk.spoty.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.local.DownloadStatus
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.AlbumRow
import io.github.zigorminsk.spoty.ui.components.ArtistRow
import io.github.zigorminsk.spoty.ui.components.Artwork
import io.github.zigorminsk.spoty.ui.components.EmptyBox
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.components.TrackRow
import io.github.zigorminsk.spoty.ui.nav.Routes
import kotlinx.coroutines.launch

private enum class LibTab { PLAYLISTS, LIKED, ALBUMS, ARTISTS, DOWNLOADS, HISTORY }

@Composable
fun LibraryScreen(navController: NavHostController, menu: TrackMenuController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(LibTab.PLAYLISTS) }

    val playlists by container.library.playlists().collectAsStateWithLifecycle(initialValue = emptyList())
    val liked by container.library.likedTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    val savedAlbums by container.library.savedAlbums().collectAsStateWithLifecycle(initialValue = emptyList())
    val artists by container.library.followedArtists().collectAsStateWithLifecycle(initialValue = emptyList())
    val downloadsMap by container.library.downloads.collectAsStateWithLifecycle()
    val downloadedTracks by container.library.downloadedTracks().collectAsStateWithLifecycle(initialValue = emptyList())
    val history by container.library.recentTracks(100).collectAsStateWithLifecycle(initialValue = emptyList())
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val likedIds by container.library.likedIds.collectAsStateWithLifecycle()
    val downloadedPaths by container.library.downloadedPaths.collectAsStateWithLifecycle()

    var createDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp)) {
            Text(stringResource(R.string.tab_library), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { createDialog = true }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.new_playlist_name))
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LibTab.entries) { t ->
                FilterChip(
                    selected = tab == t,
                    onClick = { tab = t },
                    label = {
                        Text(
                            when (t) {
                                LibTab.PLAYLISTS -> stringResource(R.string.lib_playlists)
                                LibTab.LIKED -> stringResource(R.string.liked_tracks)
                                LibTab.ALBUMS -> stringResource(R.string.filter_albums)
                                LibTab.ARTISTS -> stringResource(R.string.filter_artists)
                                LibTab.DOWNLOADS -> stringResource(R.string.downloads)
                                LibTab.HISTORY -> stringResource(R.string.history)
                            },
                        )
                    },
                )
            }
        }

        when (tab) {
            LibTab.PLAYLISTS -> {
                if (playlists.isEmpty()) {
                    EmptyBox(stringResource(R.string.playlists_empty), icon = R.drawable.ic_queue_music)
                } else {
                    LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { navController.navigate(Routes.playlist(playlist.id)) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Artwork(playlist.covers.firstOrNull(), Modifier.size(52.dp), seed = "playlist:${playlist.id}")
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        stringResource(R.string.tracks_count, playlist.trackCount),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            LibTab.LIKED -> {
                if (liked.isEmpty()) {
                    EmptyBox(stringResource(R.string.liked_empty), icon = R.drawable.ic_favorite_border)
                } else {
                    LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                TextButton(onClick = { container.player.play(liked) }) {
                                    Icon(painterResource(R.drawable.ic_play_arrow), null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.play), Modifier.padding(start = 4.dp))
                                }
                                TextButton(onClick = { container.player.play(liked, shuffle = true) }) {
                                    Icon(painterResource(R.drawable.ic_shuffle), null, Modifier.size(18.dp))
                                    Text(stringResource(R.string.shuffle), Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                        itemsIndexed(liked, key = { _, t -> t.id }) { index, track ->
                            TrackRow(
                                track = track,
                                isCurrent = playerState.current?.id == track.id,
                                isPlaying = playerState.isPlaying,
                                liked = true,
                                downloaded = track.id in downloadedPaths,
                                onClick = { container.player.play(liked, index) },
                                onLike = { scope.launch { container.library.toggleLike(track) } },
                                onMenu = { menu.show(track) },
                            )
                        }
                    }
                }
            }
            LibTab.ALBUMS -> {
                if (savedAlbums.isEmpty()) {
                    EmptyBox(stringResource(R.string.albums_empty), icon = R.drawable.ic_album)
                } else {
                    LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        items(savedAlbums, key = { it.id }) { album ->
                            AlbumRow(album, onClick = { navController.navigate(Routes.album(album.id)) })
                        }
                    }
                }
            }
            LibTab.ARTISTS -> {
                if (artists.isEmpty()) {
                    EmptyBox(stringResource(R.string.artists_empty), icon = R.drawable.ic_person)
                } else {
                    LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        items(artists, key = { it.id }) { artist ->
                            ArtistRow(artist, onClick = { navController.navigate(Routes.artist(artist.id)) })
                        }
                    }
                }
            }
            LibTab.DOWNLOADS -> {
                val active = downloadsMap.values.filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
                if (downloadedTracks.isEmpty() && active.isEmpty()) {
                    EmptyBox(stringResource(R.string.downloads_empty), icon = R.drawable.ic_download)
                } else {
                    LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        if (active.isNotEmpty()) {
                            item {
                                Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                    Text(
                                        stringResource(R.string.downloading_count, active.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
                                }
                            }
                        }
                        itemsIndexed(downloadedTracks, key = { _, t -> t.id }) { index, track ->
                            TrackRow(
                                track = track,
                                isCurrent = playerState.current?.id == track.id,
                                isPlaying = playerState.isPlaying,
                                liked = track.id in likedIds,
                                downloaded = true,
                                onClick = { container.player.play(downloadedTracks, index) },
                                onLike = { scope.launch { container.library.toggleLike(track) } },
                                onMenu = { menu.show(track) },
                            )
                        }
                    }
                }
            }
            LibTab.HISTORY -> {
                if (history.isEmpty()) {
                    EmptyBox(stringResource(R.string.history_empty), icon = R.drawable.ic_history)
                } else {
                    LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        item {
                            TextButton(onClick = { scope.launch { container.library.clearHistory() } }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(stringResource(R.string.clear_history))
                            }
                        }
                        itemsIndexed(history, key = { _, t -> t.id }) { index, track ->
                            TrackRow(
                                track = track,
                                isCurrent = playerState.current?.id == track.id,
                                isPlaying = playerState.isPlaying,
                                liked = track.id in likedIds,
                                downloaded = track.id in downloadedPaths,
                                onClick = { container.player.play(history, index) },
                                onLike = { scope.launch { container.library.toggleLike(track) } },
                                onMenu = { menu.show(track) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (createDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text(stringResource(R.string.new_playlist_name)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = {
                        createDialog = false
                        scope.launch { container.library.createPlaylist(name) }
                    },
                    enabled = name.isNotBlank(),
                ) { Text(stringResource(R.string.create)) }
            },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
