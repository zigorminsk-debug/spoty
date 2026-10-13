package io.github.zigorminsk.spoty.ui.search

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.AlbumRow
import io.github.zigorminsk.spoty.ui.components.ArtistRow
import io.github.zigorminsk.spoty.ui.components.EmptyBox
import io.github.zigorminsk.spoty.ui.components.ErrorBox
import io.github.zigorminsk.spoty.ui.components.LoadingBox
import io.github.zigorminsk.spoty.ui.components.SectionHeader
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.components.TrackRow
import io.github.zigorminsk.spoty.ui.nav.Routes
import io.github.zigorminsk.spoty.ui.spotyViewModel
import io.github.zigorminsk.spoty.util.compactCount
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(navController: NavHostController, menu: TrackMenuController) {
    val container = LocalAppContainer.current
    val viewModel = spotyViewModel { SearchViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val likedIds by container.library.likedIds.collectAsStateWithLifecycle()
    val downloaded by container.library.downloadedPaths.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(painterResource(R.drawable.ic_close), null)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                viewModel.submit()
            }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Поиск по RuTracker (отдельный экран; работает после входа в аккаунт).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Routes.rutracker(state.query)) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(painterResource(R.drawable.ic_travel_explore), null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.rutracker_search_on), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.rutracker_entry_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Поиск по Z-Torrents: торренты открыты для гостей, аккаунт не нужен.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate(Routes.ztorrents(state.query)) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Icon(painterResource(R.drawable.ic_explore), null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.ztorrents_search_on), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.ztorrents_entry_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (state.searched && !state.results.isEmpty) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchTab.entries) { tab ->
                    FilterChip(
                        selected = state.tab == tab,
                        onClick = { viewModel.setTab(tab) },
                        label = {
                            Text(
                                when (tab) {
                                    SearchTab.ALL -> stringResource(R.string.filter_all)
                                    SearchTab.TRACKS -> stringResource(R.string.filter_tracks)
                                    SearchTab.ALBUMS -> stringResource(R.string.filter_albums)
                                    SearchTab.ARTISTS -> stringResource(R.string.filter_artists)
                                },
                            )
                        },
                    )
                }
            }
        }

        when {
            state.loading -> LoadingBox()
            state.error -> ErrorBox(stringResource(R.string.network_error), onRetry = { viewModel.submit() })
            !state.searched -> {
                // История поиска
                if (history.isEmpty()) {
                    EmptyBox(stringResource(R.string.search_empty_hint), icon = R.drawable.ic_search)
                } else {
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) {
                                Text(stringResource(R.string.search_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.clearHistory() }) { Text(stringResource(R.string.clear)) }
                            }
                        }
                        items(history, key = { it }) { query ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.submit(query) }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_history),
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(query, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { viewModel.removeFromHistory(query) }) {
                                    Icon(painterResource(R.drawable.ic_close), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            state.results.isEmpty -> EmptyBox(stringResource(R.string.nothing_found), icon = R.drawable.ic_search)
            else -> {
                val results = state.results
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)) {
                    if (results.failedSources.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.sources_failed, results.failedSources.joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    val showTracks = state.tab == SearchTab.ALL || state.tab == SearchTab.TRACKS
                    val showAlbums = state.tab == SearchTab.ALL || state.tab == SearchTab.ALBUMS
                    val showArtists = state.tab == SearchTab.ALL || state.tab == SearchTab.ARTISTS

                    if (showArtists && results.artists.isNotEmpty()) {
                        if (state.tab == SearchTab.ALL) item { SectionHeader(stringResource(R.string.filter_artists)) }
                        items(
                            if (state.tab == SearchTab.ALL) results.artists.take(3) else results.artists,
                            key = { "ar:" + it.id },
                        ) { artist ->
                            ArtistRow(
                                artist,
                                onClick = { navController.navigate(Routes.artist(artist.id)) },
                                subtitle = if (artist.followers > 0) {
                                    stringResource(
                                        R.string.followers_count,
                                        compactCount(artist.followers, stringResource(R.string.thousand_suffix), stringResource(R.string.million_suffix)),
                                    )
                                } else {
                                    null
                                },
                            )
                        }
                    }
                    if (showTracks && results.tracks.isNotEmpty()) {
                        if (state.tab == SearchTab.ALL) item { SectionHeader(stringResource(R.string.filter_tracks)) }
                        items(
                            if (state.tab == SearchTab.ALL) results.tracks.take(8) else results.tracks,
                            key = { "t:" + it.id },
                        ) { track ->
                            TrackRow(
                                track = track,
                                isCurrent = playerState.current?.id == track.id,
                                isPlaying = playerState.isPlaying,
                                liked = track.id in likedIds,
                                downloaded = track.id in downloaded,
                                onClick = { container.player.playFrom(results.tracks, track) },
                                onLike = { scope.launch { container.library.toggleLike(track) } },
                                onMenu = { menu.show(track) },
                            )
                        }
                    }
                    if (showAlbums && results.albums.isNotEmpty()) {
                        if (state.tab == SearchTab.ALL) item { SectionHeader(stringResource(R.string.filter_albums)) }
                        items(
                            if (state.tab == SearchTab.ALL) results.albums.take(6) else results.albums,
                            key = { "al:" + it.id },
                        ) { album ->
                            AlbumRow(album, onClick = { navController.navigate(Routes.album(album.id)) })
                        }
                    }
                }
            }
        }
    }
}
