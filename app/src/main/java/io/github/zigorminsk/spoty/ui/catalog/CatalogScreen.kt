package io.github.zigorminsk.spoty.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.model.SourceId
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.AlbumRow
import io.github.zigorminsk.spoty.ui.components.ArtistRow
import io.github.zigorminsk.spoty.ui.components.EmptyBox
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.components.TrackRow
import io.github.zigorminsk.spoty.ui.nav.Routes
import io.github.zigorminsk.spoty.ui.spotyViewModel
import kotlinx.coroutines.launch

@Composable
fun CatalogScreen(navController: NavHostController, menu: TrackMenuController) {
    val container = LocalAppContainer.current
    val viewModel = spotyViewModel { CatalogViewModel(it) }
    val scope = rememberCoroutineScope()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val likedIds by container.library.likedIds.collectAsStateWithLifecycle()
    val downloaded by container.library.downloadedPaths.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.tab_catalog),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            stringResource(R.string.catalog_subtitle, counts.artists, counts.albums, counts.tracks),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        TabRow(selectedTabIndex = filter.tab.ordinal, modifier = Modifier.padding(top = 8.dp)) {
            CatalogTab.entries.forEach { tab ->
                Tab(
                    selected = filter.tab == tab,
                    onClick = { viewModel.setTab(tab) },
                    text = {
                        Text(
                            when (tab) {
                                CatalogTab.ARTISTS -> stringResource(R.string.filter_artists)
                                CatalogTab.ALBUMS -> stringResource(R.string.filter_albums)
                                CatalogTab.TRACKS -> stringResource(R.string.filter_tracks)
                            },
                        )
                    },
                )
            }
        }
        OutlinedTextField(
            value = filter.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text(stringResource(R.string.catalog_filter_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_filter_list), null) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) { Icon(painterResource(R.drawable.ic_close), null) }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = filter.source.isEmpty(),
                    onClick = { viewModel.setSource("") },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
            }
            items(SourceId.entries.map { it.key }) { source ->
                FilterChip(
                    selected = filter.source == source,
                    onClick = { viewModel.setSource(source) },
                    label = { Text(SourceId.title(source)) },
                )
            }
            item {
                FilterChip(
                    selected = filter.sort == 0,
                    onClick = { viewModel.setSort(0) },
                    label = { Text(stringResource(R.string.sort_new)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_fiber_new), null, Modifier.padding(0.dp)) },
                )
            }
            item {
                FilterChip(
                    selected = filter.sort == 1,
                    onClick = { viewModel.setSort(1) },
                    label = { Text(stringResource(R.string.sort_alpha)) },
                )
            }
            item {
                FilterChip(
                    selected = filter.sort == 2,
                    onClick = { viewModel.setSort(2) },
                    label = { Text(stringResource(R.string.sort_popular)) },
                )
            }
        }
        when (filter.tab) {
            CatalogTab.ARTISTS -> {
                if (artists.isEmpty()) {
                    EmptyBox(stringResource(R.string.catalog_empty))
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        items(artists, key = { it.id }) { artist ->
                            ArtistRow(artist, onClick = { navController.navigate(Routes.artist(artist.id)) })
                        }
                    }
                }
            }
            CatalogTab.ALBUMS -> {
                if (albums.isEmpty()) {
                    EmptyBox(stringResource(R.string.catalog_empty))
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        items(albums, key = { it.id }) { album ->
                            AlbumRow(album, onClick = { navController.navigate(Routes.album(album.id)) })
                        }
                    }
                }
            }
            CatalogTab.TRACKS -> {
                if (tracks.isEmpty()) {
                    EmptyBox(stringResource(R.string.catalog_empty))
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                            TrackRow(
                                track = track,
                                isCurrent = playerState.current?.id == track.id,
                                isPlaying = playerState.isPlaying,
                                liked = track.id in likedIds,
                                downloaded = track.id in downloaded,
                                onClick = { container.player.play(tracks, index) },
                                onLike = { scope.launch { container.library.toggleLike(track) } },
                                onMenu = { menu.show(track) },
                            )
                        }
                    }
                }
            }
        }
    }
}
