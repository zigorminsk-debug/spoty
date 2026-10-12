package io.github.zigorminsk.spoty.ui.artist

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
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.zigorminsk.spoty.data.model.ArtistDetails
import io.github.zigorminsk.spoty.data.repo.WaveSeed
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.AlbumShelf
import io.github.zigorminsk.spoty.ui.components.ArtistAvatar
import io.github.zigorminsk.spoty.ui.components.ArtistShelf
import io.github.zigorminsk.spoty.ui.components.ErrorBox
import io.github.zigorminsk.spoty.ui.components.LoadingBox
import io.github.zigorminsk.spoty.ui.components.SectionHeader
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.components.TrackRow
import io.github.zigorminsk.spoty.ui.nav.Routes
import io.github.zigorminsk.spoty.util.compactCount
import kotlinx.coroutines.launch

@Composable
fun ArtistScreen(artistId: String, navController: NavHostController, menu: TrackMenuController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var details by remember(artistId) { mutableStateOf<ArtistDetails?>(null) }
    var error by remember(artistId) { mutableStateOf(false) }
    var reloadKey by remember(artistId) { mutableIntStateOf(0) }
    var showAllTracks by remember(artistId) { mutableStateOf(false) }

    LaunchedEffect(artistId, reloadKey) {
        error = false
        details = try {
            container.catalog.artistDetails(artistId)
        } catch (e: Exception) {
            error = true
            null
        }
    }

    val playerState by container.player.state.collectAsStateWithLifecycle()
    val likedIds by container.library.likedIds.collectAsStateWithLifecycle()
    val downloaded by container.library.downloadedPaths.collectAsStateWithLifecycle()
    val followedIds by container.library.followedIds.collectAsStateWithLifecycle()

    when {
        error -> ErrorBox(stringResource(R.string.network_error), onRetry = { reloadKey++ })
        details == null -> LoadingBox()
        else -> {
            val d = details!!
            val artist = d.artist
            val followed = artist.id in followedIds
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(painterResource(R.drawable.ic_arrow_back), null)
                        }
                    }
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        ArtistAvatar(artist.imageUrl, Modifier.size(160.dp), seed = artist.id)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 14.dp)) {
                            Text(artist.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            if (artist.verified) {
                                Icon(painterResource(R.drawable.ic_verified), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        val subtitle = listOfNotNull(
                            artist.followers.takeIf { it > 0 }?.let {
                                stringResource(R.string.followers_count, compactCount(it, stringResource(R.string.thousand_suffix), stringResource(R.string.million_suffix)))
                            },
                            artist.location,
                        ).joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    val tracks = container.catalog.playableTracks(d)
                                    container.player.play(tracks)
                                }
                            }) {
                                Icon(painterResource(R.drawable.ic_play_arrow), null, Modifier.size(20.dp))
                                Text(stringResource(R.string.listen), Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(onClick = { scope.launch { container.library.toggleFollow(artist) } }) {
                                Icon(
                                    painterResource(if (followed) R.drawable.ic_check else R.drawable.ic_person_add),
                                    null,
                                    Modifier.size(18.dp),
                                )
                                Text(
                                    stringResource(if (followed) R.string.following else R.string.follow),
                                    Modifier.padding(start = 6.dp),
                                )
                            }
                            IconButton(onClick = { container.wave.start(WaveSeed(artistId = artist.id, artistName = artist.name)) }) {
                                Icon(painterResource(R.drawable.ic_waves), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (!artist.bio.isNullOrBlank()) {
                    item {
                        Text(
                            artist.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                }
                if (d.topTracks.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.popular_tracks)) }
                    val visible = if (showAllTracks) d.topTracks else d.topTracks.take(5)
                    itemsIndexed(visible, key = { _, t -> t.id }) { index, track ->
                        TrackRow(
                            track = track,
                            isCurrent = playerState.current?.id == track.id,
                            isPlaying = playerState.isPlaying,
                            liked = track.id in likedIds,
                            downloaded = track.id in downloaded,
                            onClick = { container.player.play(d.topTracks, d.topTracks.indexOf(track)) },
                            onLike = { scope.launch { container.library.toggleLike(track) } },
                            onMenu = { menu.show(track) },
                            modifier = Modifier.padding(horizontal = 8.dp),
                            leading = {
                                Text(
                                    "${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp).padding(top = 6.dp),
                                    textAlign = TextAlign.Center,
                                )
                            },
                        )
                    }
                    if (d.topTracks.size > 5 && !showAllTracks) {
                        item {
                            OutlinedButton(
                                onClick = { showAllTracks = true },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            ) { Text(stringResource(R.string.show_more)) }
                        }
                    }
                }
                if (d.albums.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.discography)) }
                    item { AlbumShelf(d.albums, onOpen = { navController.navigate(Routes.album(it.id)) }) }
                }
                if (d.related.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.similar_artists)) }
                    item { ArtistShelf(d.related, onOpen = { navController.navigate(Routes.artist(it.id)) }) }
                }
            }
        }
    }
}
