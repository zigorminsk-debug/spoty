package io.github.zigorminsk.spoty.ui.album

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.model.AlbumDetails
import io.github.zigorminsk.spoty.data.model.AlbumKind
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.Artwork
import io.github.zigorminsk.spoty.ui.components.ErrorBox
import io.github.zigorminsk.spoty.ui.components.LoadingBox
import io.github.zigorminsk.spoty.ui.components.SourceBadge
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.components.TrackRow
import io.github.zigorminsk.spoty.ui.nav.Routes
import io.github.zigorminsk.spoty.util.formatTotalDuration
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(albumId: String, navController: NavHostController, menu: TrackMenuController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var details by remember(albumId) { mutableStateOf<AlbumDetails?>(null) }
    var error by remember(albumId) { mutableStateOf(false) }
    var reloadKey by remember(albumId) { mutableStateOf(0) }

    LaunchedEffect(albumId, reloadKey) {
        error = false
        details = try {
            container.catalog.albumDetails(albumId)
        } catch (e: Exception) {
            error = true
            null
        }
    }

    val playerState by container.player.state.collectAsStateWithLifecycle()
    val likedIds by container.library.likedIds.collectAsStateWithLifecycle()
    val downloaded by container.library.downloadedPaths.collectAsStateWithLifecycle()
    val savedAlbums by container.library.savedAlbumIds.collectAsStateWithLifecycle()

    when {
        error -> ErrorBox(stringResource(R.string.network_error), onRetry = { reloadKey++ })
        details == null -> LoadingBox()
        else -> {
            val d = details!!
            val album = d.album
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(painterResource(R.drawable.ic_arrow_back), null)
                        }
                    }
                }
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Artwork(album.artworkUrl, Modifier.size(208.dp), seed = album.id)
                        Text(
                            album.title,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            album.artistName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = if (album.artistId != null) {
                                Modifier
                                    .padding(top = 4.dp)
                                    .clickable { navController.navigate(Routes.artist(album.artistId)) }
                            } else {
                                Modifier.padding(top = 4.dp)
                            },
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            SourceBadge(album.source)
                            Text(
                                listOfNotNull(
                                    stringResource(if (album.kind == AlbumKind.ALBUM) R.string.album else R.string.playlist),
                                    album.year?.toString(),
                                    stringResource(R.string.tracks_count, d.tracks.size),
                                    formatTotalDuration(d.tracks.sumOf { it.durationMs }, stringResource(R.string.hour_short), stringResource(R.string.minute_short))
                                        .takeIf { d.tracks.sumOf { t -> t.durationMs } > 0 },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Button(onClick = { container.player.play(d.tracks) }) {
                                Icon(painterResource(R.drawable.ic_play_arrow), null, Modifier.size(20.dp))
                                Text(stringResource(R.string.play), Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(onClick = { container.player.play(d.tracks, shuffle = true) }) {
                                Icon(painterResource(R.drawable.ic_shuffle), null, Modifier.size(20.dp))
                                Text(stringResource(R.string.shuffle), Modifier.padding(start = 6.dp))
                            }
                            val saved = album.id in savedAlbums
                            IconButton(onClick = { scope.launch { container.library.toggleAlbumSaved(album) } }) {
                                Icon(
                                    painterResource(if (saved) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                                    null,
                                    tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { scope.launch { container.downloads.enqueue(d.tracks) } }) {
                                Icon(painterResource(R.drawable.ic_download), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (!album.description.isNullOrBlank()) {
                    item {
                        Text(
                            album.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
                itemsIndexed(d.tracks, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.id == track.id,
                        isPlaying = playerState.isPlaying,
                        liked = track.id in likedIds,
                        downloaded = track.id in downloaded,
                        onClick = { container.player.play(d.tracks, index) },
                        onLike = { scope.launch { container.library.toggleLike(track) } },
                        onMenu = { menu.show(track) },
                        modifier = Modifier.padding(horizontal = 8.dp),
                        showArtwork = false,
                        leading = {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp).padding(top = 6.dp),
                                textAlign = TextAlign.Center,
                            )
                        },
                    )
                }
                if (album.license != null) {
                    item {
                        Text(
                            stringResource(R.string.license_label, album.license),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
