package io.github.zigorminsk.spoty.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.model.Genres
import io.github.zigorminsk.spoty.data.model.WaveMoods
import io.github.zigorminsk.spoty.data.repo.WaveSeed
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.AlbumShelf
import io.github.zigorminsk.spoty.ui.components.ArtistShelf
import io.github.zigorminsk.spoty.ui.components.Artwork
import io.github.zigorminsk.spoty.ui.components.LoadingBox
import io.github.zigorminsk.spoty.ui.components.SectionHeader
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.nav.Routes
import io.github.zigorminsk.spoty.ui.spotyViewModel

@Composable
fun HomeScreen(navController: NavHostController, menu: TrackMenuController) {
    val container = LocalAppContainer.current
    val viewModel = spotyViewModel { HomeViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val waveLoading by container.wave.loading.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()

    if (state.loading) {
        LoadingBox()
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                }
            }
        }

        if (state.offline) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(12.dp)
                        .fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.ic_cloud_off), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.offline_mode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // «Моя волна»
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF2E3618), Color(0xFF101114), Color(0xFF1E3250))),
                    )
                    .clickable(enabled = !waveLoading) { container.wave.start(WaveSeed()) },
            ) {
                Column(Modifier.align(Alignment.CenterStart).padding(20.dp)) {
                    Text(stringResource(R.string.my_wave), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(
                        stringResource(if (waveLoading) R.string.wave_loading else R.string.my_wave_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(20.dp)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_play_arrow),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        // Настроения волны
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(WaveMoods.all, key = { it.id }) { mood ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { container.wave.start(WaveSeed(moodId = mood.id)) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(mood.emoji)
                        Text(stringResource(mood.titleRes), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (state.recent.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.recently_played)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(state.recent, key = { it.id }) { track ->
                        Column(
                            modifier = Modifier
                                .width(112.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { container.player.playFrom(state.recent, track) }
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Artwork(track.artworkUrl, Modifier.size(100.dp), seed = track.id)
                            Text(track.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (state.forYou.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.for_you)) }
            item {
                TrackGrid(
                    tracks = state.forYou.take(12),
                    currentId = playerState.current?.id,
                    onPlay = { container.player.playFrom(state.forYou, it) },
                    onMenu = { menu.show(it) },
                )
            }
        }

        if (state.trending.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.trending_now)) }
            item {
                TrackGrid(
                    tracks = state.trending.take(12),
                    currentId = playerState.current?.id,
                    onPlay = { container.player.playFrom(state.trending, it) },
                    onMenu = { menu.show(it) },
                )
            }
        }

        if (state.newAlbums.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.popular_albums)) }
            item { AlbumShelf(state.newAlbums, onOpen = { navController.navigate(Routes.album(it.id)) }) }
        }

        if (state.popularArtists.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.popular_artists)) }
            item { ArtistShelf(state.popularArtists, onOpen = { navController.navigate(Routes.artist(it.id)) }) }
        }

        // Жанры
        item { SectionHeader(stringResource(R.string.genres_moods)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(Genres.all, key = { it.id }) { genre ->
                    Box(
                        modifier = Modifier
                            .size(width = 140.dp, height = 72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Color(genre.colorA), Color(genre.colorB))))
                            .clickable { navController.navigate(Routes.genre(genre.id)) },
                    ) {
                        Text(
                            stringResource(genre.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Сетка 2×N коротких строк треков в горизонтальной прокрутке (как в Я.Музыке). */
@Composable
private fun TrackGrid(
    tracks: List<io.github.zigorminsk.spoty.data.model.Track>,
    currentId: String?,
    onPlay: (io.github.zigorminsk.spoty.data.model.Track) -> Unit,
    onMenu: (io.github.zigorminsk.spoty.data.model.Track) -> Unit,
) {
    val columns = tracks.chunked(3)
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(columns.size) { index ->
            Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                columns[index].forEach { track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (track.id == currentId) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                            .clickable { onPlay(track) }
                            .padding(4.dp),
                    ) {
                        Artwork(track.artworkUrl, Modifier.size(46.dp), seed = track.id)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (track.id == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                track.artistName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onMenu(track) }) {
                            Icon(
                                painterResource(R.drawable.ic_more_vert),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
