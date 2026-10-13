package io.github.zigorminsk.spoty.player

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.data.repo.LibraryRepository
import io.github.zigorminsk.spoty.data.repo.WaveSeed
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Что именно сейчас играет: обычная очередь или «Моя волна». */
sealed interface PlayOrigin {
    data object None : PlayOrigin
    data class Wave(val seed: WaveSeed) : PlayOrigin
}

data class PlayerState(
    val connected: Boolean = false,
    val current: Track? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
) {
    val hasMedia: Boolean get() = current != null
}

data class Progress(val positionMs: Long = 0L, val durationMs: Long = 0L, val bufferedMs: Long = 0L)

data class SleepTimerState(val endsAtMs: Long = 0L, val afterTrack: Boolean = false) {
    val active: Boolean get() = endsAtMs > 0L || afterTrack
}

/**
 * Мост между интерфейсом и фоновым сервисом воспроизведения ([PlaybackService]).
 * Вся музыка играет в сервисе, поэтому продолжается при выключенном экране и закрытом приложении.
 */
class PlayerController(
    private val context: Context,
    private val library: LibraryRepository,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private val _origin = MutableStateFlow<PlayOrigin>(PlayOrigin.None)
    val origin: StateFlow<PlayOrigin> = _origin.asStateFlow()

    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Сообщения о сбоях воспроизведения (название трека) для показа пользователю. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var sleepJob: Job? = null
    private var recordedPlayFor: String? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val c = controller ?: return
            val rebuild = events.contains(Player.EVENT_TIMELINE_CHANGED) || !_state.value.connected
            refresh(c, rebuild)
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) recordedPlayFor = null
        }

        override fun onPlayerError(error: PlaybackException) {
            val title = controller?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
            // Причина (например, «no peers» у торрента) важна не меньше названия трека.
            val reason = error.cause?.message ?: error.message ?: error.errorCodeName
            _errors.tryEmit(listOfNotNull(title.takeIf { it.isNotBlank() }, reason).joinToString(" — "))
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun whenConnected(block: (MediaController) -> Unit) = onMain {
        val c = controller
        if (c != null) block(c) else pending.addLast(block)
    }

    fun connect() = onMain {
        if (future != null) return@onMain
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = try {
                f.get()
            } catch (e: Exception) {
                future = null
                return@addListener
            }
            controller = c
            c.addListener(listener)
            refresh(c, true)
            while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
            startTicker()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun refresh(c: MediaController, rebuildQueue: Boolean) {
        val old = _state.value
        _state.value = PlayerState(
            connected = true,
            current = c.currentMediaItem?.toTrack(),
            isPlaying = c.isPlaying,
            playWhenReady = c.playWhenReady,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            speed = c.playbackParameters.speed,
            queue = if (rebuildQueue) (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toTrack() } else old.queue,
            currentIndex = c.currentMediaItemIndex,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = c.hasPreviousMediaItem(),
        )
    }

    private fun startTicker() {
        mainScope.launch {
            while (isActive) {
                val c = controller
                if (c != null) {
                    val rawDuration = c.duration
                    val duration = if (rawDuration != C.TIME_UNSET && rawDuration > 0) rawDuration else _state.value.current?.durationMs ?: 0L
                    val position = c.currentPosition.coerceAtLeast(0L)
                    _progress.value = Progress(position, duration, c.bufferedPosition.coerceAtLeast(0L))
                    if (c.isPlaying) {
                        maybeRecordPlay(position, duration)
                        maybeSleepAfterTrack(c, position, duration)
                    }
                }
                delay(if (c?.isPlaying == true) 300L else 1000L)
            }
        }
    }

    private fun maybeRecordPlay(position: Long, duration: Long) {
        val track = _state.value.current ?: return
        if (recordedPlayFor == track.id) return
        val threshold = if (duration > 0) minOf(30_000L, duration / 2) else 30_000L
        if (position >= threshold) {
            recordedPlayFor = track.id
            mainScope.launch { runCatching { library.recordPlay(track) } }
        }
    }

    private fun maybeSleepAfterTrack(c: MediaController, position: Long, duration: Long) {
        if (!_sleepTimer.value.afterTrack || duration <= 0) return
        if (duration - position < 900L) {
            c.pause()
            _sleepTimer.value = SleepTimerState()
        }
    }

    // ───────────── Управление очередью ─────────────

    private fun itemsFor(tracks: List<Track>): List<MediaItem> {
        val paths = library.downloadedPaths.value
        return tracks.map { it.toMediaItem(paths[it.id]) }
    }

    /** Заменяет очередь и начинает воспроизведение. */
    fun play(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false, origin: PlayOrigin = PlayOrigin.None) {
        if (tracks.isEmpty()) return
        val items = itemsFor(tracks)
        val start = if (shuffle && startIndex == 0) Random.nextInt(items.size) else startIndex.coerceIn(0, items.lastIndex)
        whenConnected { c ->
            _origin.value = origin
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(items, start, 0L)
            c.prepare()
            c.play()
        }
    }

    /** Играет [track] из контекста [queue] (альбом, плейлист, результаты поиска). */
    fun playFrom(queue: List<Track>, track: Track) {
        val index = queue.indexOfFirst { it.id == track.id }
        if (index >= 0) play(queue, index) else play(listOf(track))
    }

    fun playNext(track: Track) {
        val item = itemsFor(listOf(track)).first()
        whenConnected { c ->
            if (c.mediaItemCount == 0) {
                play(listOf(track))
            } else {
                c.addMediaItem(c.currentMediaItemIndex + 1, item)
            }
        }
    }

    fun enqueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val items = itemsFor(tracks)
        whenConnected { c ->
            if (c.mediaItemCount == 0) {
                play(tracks)
            } else {
                c.addMediaItems(items)
            }
        }
    }

    /** Дописывает в конец очереди (для «Моей волны»), не прерывая воспроизведения. */
    fun append(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val items = itemsFor(tracks)
        whenConnected { c -> c.addMediaItems(items) }
    }

    fun togglePlayPause() = whenConnected { c ->
        if (c.isPlaying || c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0, 0L)
            c.play()
        }
    }

    fun pause() = whenConnected { it.pause() }

    fun resume() = whenConnected { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun next() = whenConnected { c ->
        if (c.hasNextMediaItem()) c.seekToNextMediaItem()
    }

    fun previous() = whenConnected { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = whenConnected { it.seekTo(positionMs) }

    fun seekToQueueIndex(index: Int) = whenConnected { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun toggleShuffle() = whenConnected { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = whenConnected { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun removeFromQueue(index: Int) = whenConnected { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveInQueue(from: Int, to: Int) = whenConnected { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount) c.moveMediaItem(from, to)
    }

    fun clearUpcoming() = whenConnected { c ->
        val last = c.mediaItemCount
        val from = c.currentMediaItemIndex + 1
        if (from in 1 until last) c.removeMediaItems(from, last)
    }

    fun setSpeed(speed: Float) = whenConnected { it.setPlaybackSpeed(speed) }

    fun stop() = whenConnected { c ->
        c.stop()
        c.clearMediaItems()
        _origin.value = PlayOrigin.None
    }

    // ───────────── Таймер сна ─────────────

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _sleepTimer.value = SleepTimerState(endsAtMs = endsAt)
        sleepJob = mainScope.launch {
            delay(minutes * 60_000L)
            fadeOutAndPause()
            _sleepTimer.value = SleepTimerState()
        }
    }

    fun sleepAfterCurrentTrack() {
        sleepJob?.cancel()
        _sleepTimer.value = SleepTimerState(afterTrack = true)
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _sleepTimer.value = SleepTimerState()
    }

    private suspend fun fadeOutAndPause() {
        val c = controller ?: return
        if (c.isCommandAvailable(Player.COMMAND_SET_VOLUME)) {
            val original = c.volume
            for (step in 9 downTo 0) {
                c.volume = original * step / 10f
                delay(500L)
            }
            c.pause()
            c.volume = original
        } else {
            c.pause()
        }
    }
}
