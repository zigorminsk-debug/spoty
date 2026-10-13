package io.github.zigorminsk.spoty.ui.rutracker

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.data.remote.rutracker.RUTRACKER_SOURCE
import io.github.zigorminsk.spoty.data.remote.rutracker.RuTrackerError
import io.github.zigorminsk.spoty.data.remote.rutracker.RuTrackerParser
import io.github.zigorminsk.spoty.data.remote.rutracker.torrentStreamUrl
import io.github.zigorminsk.spoty.data.torrent.TorrentFileEntry
import io.github.zigorminsk.spoty.data.torrent.TorrentStats
import io.github.zigorminsk.spoty.di.AppContainer
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.EmptyBox
import io.github.zigorminsk.spoty.ui.components.ErrorBox
import io.github.zigorminsk.spoty.ui.components.LoadingBox
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.components.TrackRow
import io.github.zigorminsk.spoty.ui.spotyViewModel
import io.github.zigorminsk.spoty.util.formatBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuTrackerTopicViewModel(
    private val container: AppContainer,
    private val topicId: Long,
    private val topicTitle: String,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val error: Boolean = false,
        val authError: Boolean = false,
        val name: String = "",
        val files: List<TorrentFileEntry> = emptyList(),
        val tracks: List<Track> = emptyList(),
        val stats: TorrentStats? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            while (isActive) {
                val stats = withContext(Dispatchers.IO) {
                    runCatching { container.torrentEngine.stats(RUTRACKER_SOURCE, topicId) }.getOrNull()
                }
                _state.update { it.copy(stats = stats) }
                delay(1_000L)
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = false, authError = false) }
            try {
                val engine = container.torrentEngine
                val info = withContext(Dispatchers.IO) { engine.prepare(RUTRACKER_SOURCE, topicId) }
                val files = withContext(Dispatchers.IO) { engine.audioFiles(info) }
                val (artist, album) = RuTrackerParser.splitTitle(topicTitle)
                val name = info.name().orEmpty()
                val tracks = files.map { file ->
                    Track(
                        id = "$RUTRACKER_SOURCE:$topicId:${file.index}",
                        source = RUTRACKER_SOURCE,
                        title = RuTrackerParser.cleanFileTitle(file.name),
                        artistId = null,
                        artistName = artist ?: name.ifBlank { "RuTracker" },
                        albumId = null,
                        albumTitle = album ?: topicTitle.ifBlank { name },
                        streamUrl = torrentStreamUrl(RUTRACKER_SOURCE, topicId, file.index),
                        webUrl = "${container.settings.value.rutrackerMirror}/forum/viewtopic.php?t=$topicId",
                    )
                }
                _state.update { it.copy(loading = false, name = name, files = files, tracks = tracks) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuTrackerError) {
                _state.update { it.copy(loading = false, authError = true) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = true) }
            }
        }
    }
}

@Composable
fun RuTrackerTopicScreen(
    topicId: Long,
    topicTitle: String,
    navController: NavHostController,
    menu: TrackMenuController,
) {
    val container = LocalAppContainer.current
    val viewModel = spotyViewModel(key = "rutracker-topic-$topicId") {
        RuTrackerTopicViewModel(it, topicId, topicTitle)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playerState by container.player.state.collectAsStateWithLifecycle()
    val likedIds by container.library.likedIds.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(painterResource(R.drawable.ic_arrow_back), null)
            }
            Text(
                topicTitle.ifBlank { state.name.ifBlank { stringResource(R.string.rutracker) } },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
        }

        when {
            state.loading -> LoadingBox()
            state.authError -> ErrorBox(stringResource(R.string.rutracker_login_failed), onRetry = { viewModel.load() })
            state.error -> ErrorBox(stringResource(R.string.network_error), onRetry = { viewModel.load() })
            state.tracks.isEmpty() -> EmptyBox(stringResource(R.string.rutracker_no_audio), icon = R.drawable.ic_music_note)
            else -> {
                val stats = state.stats
                if (stats != null) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            stringResource(
                                R.string.rutracker_progress,
                                formatBytes(stats.totalDoneBytes),
                                formatBytes(stats.downloadRateBps),
                                stats.seeds,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { stats.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                        if (stats.seeds == 0 && stats.totalDoneBytes == 0L) {
                            Text(
                                stringResource(R.string.torrent_no_seeds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                Button(
                    onClick = { container.player.play(state.tracks) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_play_arrow), null)
                    Text(stringResource(R.string.rutracker_play_all), modifier = Modifier.padding(start = 8.dp))
                }
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)) {
                    items(state.tracks.indices.toList(), key = { state.tracks[it].id }) { index ->
                        val track = state.tracks[index]
                        val file = state.files.getOrNull(index)
                        val complete = file != null && state.stats?.fileComplete(file) == true
                        TrackRow(
                            track = track,
                            isCurrent = playerState.current?.id == track.id,
                            isPlaying = playerState.isPlaying,
                            liked = track.id in likedIds,
                            downloaded = complete,
                            onClick = { container.player.playFrom(state.tracks, track) },
                            onLike = { scope.launch { container.library.toggleLike(track) } },
                            onMenu = { menu.show(track) },
                        )
                    }
                }
            }
        }
    }
}
