package io.github.zigorminsk.spoty.ui.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.Artwork
import io.github.zigorminsk.spoty.ui.components.EmptyBox
import io.github.zigorminsk.spoty.ui.components.LoadingBox
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.components.TrackRow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(playlistId: Long, navController: NavHostController, menu: TrackMenuController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val detailsFlow = remember(playlistId) {
        if (playlistId > 0) container.library.playlist(playlistId) else flowOf(null)
    }
    val details by detailsFlow.collectAsStateWithLifecycle(initialValue = null)
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val likedIds by container.library.likedIds.collectAsStateWithLifecycle()
    val downloaded by container.library.downloadedPaths.collectAsStateWithLifecycle()
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var loadedOnce by remember { mutableStateOf(false) }

    if (details != null) loadedOnce = true

    val d = details
    if (d == null) {
        if (!loadedOnce) LoadingBox() else navController.popBackStack()
        return
    }
    val tracks = d.items.map { it.track }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), null)
                }
                Text(d.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { renameDialog = true }) {
                    Icon(painterResource(R.drawable.ic_edit), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { deleteDialog = true }) {
                    Icon(painterResource(R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Artwork(tracks.firstOrNull()?.artworkUrl, Modifier.size(96.dp), seed = "playlist:${d.id}")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.tracks_count, tracks.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { container.player.play(tracks) }, enabled = tracks.isNotEmpty()) {
                            Icon(painterResource(R.drawable.ic_play_arrow), null, Modifier.size(20.dp))
                            Text(stringResource(R.string.play), Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(onClick = { container.player.play(tracks, shuffle = true) }, enabled = tracks.isNotEmpty()) {
                            Icon(painterResource(R.drawable.ic_shuffle), null, Modifier.size(20.dp))
                        }
                        IconButton(onClick = { scope.launch { container.downloads.enqueue(tracks) } }, enabled = tracks.isNotEmpty()) {
                            Icon(painterResource(R.drawable.ic_download), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (tracks.isEmpty()) {
            item { EmptyBox(stringResource(R.string.playlist_empty), Modifier.fillMaxWidth().padding(top = 60.dp)) }
        }
        itemsIndexed(d.items, key = { _, item -> item.itemId }) { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                Column {
                    IconButton(
                        onClick = { scope.launch { container.library.moveInPlaylist(d.id, index, index - 1) } },
                        enabled = index > 0,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_up), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { scope.launch { container.library.moveInPlaylist(d.id, index, index + 1) } },
                        enabled = index < d.items.lastIndex,
                        modifier = Modifier.size(26.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                TrackRow(
                    track = item.track,
                    isCurrent = playerState.current?.id == item.track.id,
                    isPlaying = playerState.isPlaying,
                    liked = item.track.id in likedIds,
                    downloaded = item.track.id in downloaded,
                    onClick = { container.player.play(tracks, index) },
                    onLike = { scope.launch { container.library.toggleLike(item.track) } },
                    onMenu = { menu.show(item.track) },
                )
            }
        }
    }

    if (renameDialog) {
        var name by remember { mutableStateOf(d.name) }
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.library.renamePlaylist(d.id, name) }
                    renameDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renameDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text(stringResource(R.string.delete_playlist_q)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialog = false
                    scope.launch { container.library.deletePlaylist(d.id) }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
