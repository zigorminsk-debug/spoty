package io.github.zigorminsk.spoty.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.data.remote.Lyrics
import io.github.zigorminsk.spoty.ui.LocalAppContainer

/** Текст песни (LRCLIB): подсветка строки в такт, автопрокрутка, перемотка по нажатию. */
@Composable
fun LyricsSheet(track: Track, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    var lyrics by remember(track.id) { mutableStateOf<Lyrics?>(null) }
    var loading by remember(track.id) { mutableStateOf(true) }
    val progress by container.player.progress.collectAsStateWithLifecycle()

    LaunchedEffect(track.id) {
        loading = true
        lyrics = try {
            container.lyrics.lyrics(track)
        } catch (e: Exception) {
            null
        }
        loading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                track.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            val l = lyrics
            when {
                loading -> Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                l == null || l.isEmpty -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_lyrics),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                l.instrumental && l.lines.isEmpty() && l.plain.isNullOrBlank() ->
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("🎻 " + stringResource(R.string.instrumental), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                l.isSynced -> {
                    val listState = rememberLazyListState()
                    val activeIndex = l.lines.indexOfLast { it.timeMs <= progress.positionMs }.coerceAtLeast(0)
                    LaunchedEffect(activeIndex) {
                        if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 3).coerceAtLeast(0))
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(420.dp).padding(top = 10.dp)) {
                        itemsIndexed(l.lines) { index, line ->
                            Text(
                                line.text.ifBlank { "•" },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (index == activeIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { container.player.seekTo(line.timeMs) }
                                    .padding(horizontal = 20.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        l.plain.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
