package io.github.zigorminsk.spoty.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.EmptyBox
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.components.TrackRow
import kotlinx.coroutines.launch

/** Очередь воспроизведения: переход к треку, перестановка и удаление. */
@Composable
fun QueueScreen(navController: NavHostController, menu: TrackMenuController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val state by container.player.state.collectAsStateWithLifecycle()
    val likedIds by container.library.likedIds.collectAsStateWithLifecycle()
    val downloaded by container.library.downloadedPaths.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(painterResource(R.drawable.ic_arrow_back), null)
            }
            Text(stringResource(R.string.queue), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { container.player.clearUpcoming() }, enabled = state.currentIndex < state.queue.lastIndex) {
                Text(stringResource(R.string.clear_upcoming))
            }
        }
        if (state.queue.isEmpty()) {
            EmptyBox(stringResource(R.string.queue_empty), icon = R.drawable.ic_queue_music)
        } else {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                itemsIndexed(state.queue, key = { index, t -> "$index:${t.id}" }) { index, track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            IconButton(
                                onClick = { container.player.moveInQueue(index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_keyboard_arrow_up), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = { container.player.moveInQueue(index, index + 1) },
                                enabled = index < state.queue.lastIndex,
                                modifier = Modifier.size(26.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                        TrackRow(
                            track = track,
                            isCurrent = index == state.currentIndex,
                            isPlaying = state.isPlaying,
                            liked = track.id in likedIds,
                            downloaded = track.id in downloaded,
                            onClick = { container.player.seekToQueueIndex(index) },
                            onLike = { scope.launch { container.library.toggleLike(track) } },
                            onMenu = { menu.show(track) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { container.player.removeFromQueue(index) }, enabled = index != state.currentIndex) {
                            Icon(painterResource(R.drawable.ic_close), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
