package io.github.zigorminsk.spoty.ui.player

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.data.model.SourceId
import io.github.zigorminsk.spoty.player.PlayOrigin
import io.github.zigorminsk.spoty.ui.LocalAppContainer
import io.github.zigorminsk.spoty.ui.components.Artwork
import io.github.zigorminsk.spoty.ui.components.TrackMenuController
import io.github.zigorminsk.spoty.ui.nav.Routes
import io.github.zigorminsk.spoty.util.formatDuration
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(navController: NavHostController, menu: TrackMenuController) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by container.player.state.collectAsStateWithLifecycle()
    val progress by container.player.progress.collectAsStateWithLifecycle()
    val origin by container.player.origin.collectAsStateWithLifecycle()
    val sleepTimer by container.player.sleepTimer.collectAsStateWithLifecycle()
    val likedIds by container.library.likedIds.collectAsStateWithLifecycle()

    val track = state.current
    if (track == null) {
        navController.popBackStack()
        return
    }

    var seekPosition by remember { mutableStateOf<Float?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    val liked = track.id in likedIds

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
    ) {
        // Шапка
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (origin) {
                        is PlayOrigin.Wave -> stringResource(R.string.my_wave)
                        else -> track.albumTitle ?: SourceId.title(track.source)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { menu.show(track) }) {
                Icon(painterResource(R.drawable.ic_more_vert), null)
            }
        }

        Spacer(Modifier.weight(0.06f))

        // Обложка
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Artwork(
                track.artworkUrl,
                Modifier.fillMaxWidth(0.92f).aspectRatio(1f),
                seed = track.id,
            )
            if (state.isBuffering) {
                CircularProgressIndicator(Modifier.size(52.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        // Название и лайк/дизлайк
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (track.artistId != null) {
                        Modifier.clickable {
                            navController.popBackStack()
                            navController.navigate(Routes.artist(track.artistId))
                        }
                    } else Modifier,
                )
            }
            IconButton(onClick = { scope.launch { container.library.dislike(track); container.player.next() } }) {
                Icon(painterResource(R.drawable.ic_thumb_down), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { scope.launch { container.library.toggleLike(track) } }) {
                Icon(
                    painterResource(if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
                    null,
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Прогресс
        val duration = progress.durationMs.coerceAtLeast(0L)
        val sliderValue = seekPosition ?: if (duration > 0) progress.positionMs.toFloat() / duration else 0f
        Slider(
            value = sliderValue.coerceIn(0f, 1f),
            onValueChange = { seekPosition = it },
            onValueChangeFinished = {
                seekPosition?.let { container.player.seekTo((it * duration).toLong()) }
                seekPosition = null
            },
            enabled = duration > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatDuration(((seekPosition ?: (if (duration > 0) progress.positionMs.toFloat() / duration else 0f)) * duration).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Управление
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            IconButton(onClick = { container.player.toggleShuffle() }) {
                Icon(
                    painterResource(R.drawable.ic_shuffle),
                    null,
                    tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { container.player.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(R.drawable.ic_skip_previous), null, modifier = Modifier.size(36.dp))
            }
            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { container.player.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = { container.player.next() }, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                Icon(
                    painterResource(R.drawable.ic_skip_next),
                    null,
                    tint = if (state.hasNext) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { container.player.cycleRepeat() }) {
                Icon(
                    painterResource(if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat),
                    null,
                    tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Нижний ряд: текст, таймер, скорость, эквалайзер, очередь
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            IconButton(onClick = { showLyrics = true }) {
                Icon(painterResource(R.drawable.ic_lyrics), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showSleep = true }) {
                Icon(
                    painterResource(R.drawable.ic_timer),
                    null,
                    tint = if (sleepTimer.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showSpeed = true }) {
                Icon(painterResource(R.drawable.ic_speed), null, tint = if (state.speed != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val sessionId = container.audioSessionId
            IconButton(
                onClick = {
                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    runCatching { context.startActivity(intent) }
                },
                enabled = sessionId != C.AUDIO_SESSION_ID_UNSET && intentResolvable(context, AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL),
            ) {
                Icon(painterResource(R.drawable.ic_equalizer), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { navController.navigate(Routes.QUEUE) }) {
                Icon(painterResource(R.drawable.ic_queue_music), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showLyrics) {
        LyricsSheet(track = track, onDismiss = { showLyrics = false })
    }

    if (showSleep) {
        ModalBottomSheet(onDismissRequest = { showSleep = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    stringResource(R.string.sleep_timer),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                listOf(15, 30, 45, 60, 90).forEach { minutes ->
                    Text(
                        stringResource(R.string.sleep_minutes, minutes),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                container.player.startSleepTimer(minutes)
                                showSleep = false
                            }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                    )
                }
                Text(
                    stringResource(R.string.sleep_after_track),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            container.player.sleepAfterCurrentTrack()
                            showSleep = false
                        }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                )
                if (sleepTimer.active) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.sleep_cancel),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                container.player.cancelSleepTimer()
                                showSleep = false
                            }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                    )
                }
            }
        }
    }

    if (showSpeed) {
        ModalBottomSheet(onDismissRequest = { showSpeed = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    stringResource(R.string.playback_speed),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                container.player.setSpeed(speed)
                                showSpeed = false
                            }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                    ) {
                        Text(
                            "${speed}x",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.speed == speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (state.speed == speed) {
                            Spacer(Modifier.weight(1f))
                            Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun intentResolvable(context: android.content.Context, action: String): Boolean =
    Intent(action).resolveActivity(context.packageManager) != null
