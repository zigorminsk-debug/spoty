package io.github.zigorminsk.spoty.ui.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.Genres
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.data.remote.safely
import io.github.zigorminsk.spoty.data.repo.WaveSeed
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.AlbumShelf
import io.github.zigorminsk.spoty.ui.components.EmptyBox
import io.github.zigorminsk.spoty.ui.components.LoadingBox
import io.github.zigorminsk.spoty.ui.components.SectionHeader
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.components.TrackRow
import io.github.zigorminsk.spoty.ui.nav.Routes
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun GenreScreen(genreId: String, navController: NavHostController, menu: TrackMenuController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val genre = remember(genreId) { Genres.byId(genreId) }
    var tracks by remember(genreId) { mutableStateOf<List<Track>?>(null) }
    var albums by remember(genreId) { mutableStateOf<List<Album>>(emptyList()) }
    var reloadKey by remember(genreId) { mutableIntStateOf(0) }

    LaunchedEffect(genreId, reloadKey) {
        if (genre == null) return@LaunchedEffect
        coroutineScope {
            val t = async { safely(emptyList<Track>()) { container.catalog.trendingTracks(genre, 50) } }
            val a = async { safely(emptyList<Album>()) { container.catalog.popularAlbums(genre, 20) } }
            albums = a.await()
            tracks = t.await()
        }
    }

    val playerState by container.player.state.collectAsStateWithLifecycle()
    val likedIds by container.library.likedIds.collectAsStateWithLifecycle()
    val downloaded by container.library.downloadedPaths.collectAsStateWithLifecycle()

    if (genre == null) {
        EmptyBox(stringResource(R.string.nothing_found))
        return
    }

    val trackList = tracks

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Brush.linearGradient(listOf(Color(genre.colorA), Color(genre.colorB)))),
            ) {
                IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.padding(4.dp)) {
                    Icon(painterResource(R.drawable.ic_arrow_back), null, tint = Color.White)
                }
                Text(
                    stringResource(genre.titleRes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = { trackList?.takeIf { it.isNotEmpty() }?.let { container.player.play(it) } },
                    enabled = !trackList.isNullOrEmpty(),
                ) {
                    Icon(painterResource(R.drawable.ic_play_arrow), null, Modifier.size(20.dp))
                    Text(stringResource(R.string.play), Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = { container.wave.start(WaveSeed(genreId = genre.id)) }) {
                    Icon(painterResource(R.drawable.ic_waves), null, Modifier.size(20.dp))
                    Text(stringResource(R.string.genre_wave), Modifier.padding(start = 6.dp))
                }
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.popular_albums)) }
            item { AlbumShelf(albums, onOpen = { navController.navigate(Routes.album(it.id)) }) }
        }
        when {
            trackList == null -> item { Box(Modifier.fillMaxWidth().height(200.dp)) { LoadingBox() } }
            trackList.isEmpty() && albums.isEmpty() -> item { Box(Modifier.fillMaxWidth().height(300.dp)) { EmptyBox(stringResource(R.string.network_error)) } }
            else -> {
                item { SectionHeader(stringResource(R.string.filter_tracks)) }
                itemsIndexed(trackList, key = { _, t -> t.id }) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = playerState.current?.id == track.id,
                        isPlaying = playerState.isPlaying,
                        liked = track.id in likedIds,
                        downloaded = track.id in downloaded,
                        onClick = { container.player.play(trackList, index) },
                        onLike = { scope.launch { container.library.toggleLike(track) } },
                        onMenu = { menu.show(track) },
                        modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }
    }
}
