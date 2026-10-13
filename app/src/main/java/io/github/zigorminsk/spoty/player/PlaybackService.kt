package io.github.zigorminsk.spoty.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.media.audiofx.AudioEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.github.zigorminsk.spoty.MainActivity
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.SpotyApp
import io.github.zigorminsk.spoty.data.remote.rutracker.torrentStreamUrlFromTrackId
import io.github.zigorminsk.spoty.data.settings.PlaybackSnapshot
import java.io.File
import io.github.zigorminsk.spoty.util.NotificationChannels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Фоновый сервис воспроизведения. Держит ExoPlayer и медиа-сессию, показывает уведомление
 * с управлением (экран блокировки, Bluetooth, Android Auto) и продолжает играть,
 * когда приложение свернуто или закрыто.
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as SpotyApp).container

    private var consecutiveErrors = 0
    private var saveJob: Job? = null
    private var tickerJob: Job? = null
    private var announcedAudioSession = C.AUDIO_SESSION_ID_UNSET

    override fun onCreate() {
        super.onCreate()
        val c = container

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(c.mediaDataSourceFactory()))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(20_000, 90_000, 1_500, 4_000)
                    .build(),
            )
            .build()
        player.addListener(playerListener)
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                announceAudioSession(audioSessionId)
            }
        })

        val activityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(activityIntent)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NotificationChannels.PLAYBACK)
                .setChannelName(R.string.channel_playback)
                .build()
                .also { it.setSmallIcon(R.drawable.ic_stat_music) },
        )

        restoreQueue()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        tickerJob?.cancel()
        saveJob?.cancel()
        announceAudioSession(C.AUDIO_SESSION_ID_UNSET)
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // ───────────── Ошибки и сохранение состояния ─────────────

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            consecutiveErrors++
            // Трек не загрузился (удалён, нет сети…) — пробуем следующий, но не зацикливаемся.
            if (consecutiveErrors <= 5 && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                consecutiveErrors = 0
                startTicker()
            } else {
                tickerJob?.cancel()
                scheduleSave(queueChanged = false)
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            scheduleSave(queueChanged = true)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            scheduleSave(queueChanged = false)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = scheduleSave(queueChanged = false)

        override fun onRepeatModeChanged(repeatMode: Int) = scheduleSave(queueChanged = false)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(15_000L)
                saveState(includeQueue = false)
            }
        }
    }

    private fun scheduleSave(queueChanged: Boolean) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(500L)
            saveState(queueChanged)
        }
    }

    private suspend fun saveState(includeQueue: Boolean) {
        val p = player
        try {
            if (includeQueue) {
                val tracks = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).toTrack() }
                container.library.saveQueue(tracks)
            }
            container.settings.savePlayback(
                PlaybackSnapshot(p.currentMediaItemIndex.coerceAtLeast(0), p.currentPosition.coerceAtLeast(0L), p.shuffleModeEnabled, p.repeatMode),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Не критично: состояние просто не сохранится.
        }
    }

    /** После перезапуска приложения возвращаем последнюю очередь (на паузе, без обращения к сети). */
    private fun restoreQueue() {
        scope.launch {
            try {
                val tracks = container.library.loadQueue()
                if (tracks.isEmpty() || player.mediaItemCount > 0) return@launch
                val snapshot = container.settings.loadPlayback()
                val paths = container.library.downloadedPaths.value
                val items = tracks.map { it.toMediaItem(paths[it.id]) }
                player.shuffleModeEnabled = snapshot.shuffle
                player.repeatMode = snapshot.repeatMode
                player.setMediaItems(items, snapshot.index.coerceIn(0, items.lastIndex), snapshot.positionMs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Очередь не восстановилась — не страшно.
            }
        }
    }

    // ───────────── Системный эквалайзер ─────────────

    private fun announceAudioSession(audioSessionId: Int) {
        if (announcedAudioSession != C.AUDIO_SESSION_ID_UNSET && announcedAudioSession != audioSessionId) {
            sendBroadcast(effectIntent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, announcedAudioSession))
        }
        announcedAudioSession = audioSessionId
        container.audioSessionId = audioSessionId
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            sendBroadcast(effectIntent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, audioSessionId))
        }
    }

    private fun effectIntent(action: String, audioSessionId: Int): Intent =
        Intent(action)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

    // ───────────── Каталог для Android Auto и голосовых помощников ─────────────

    private fun <T> futureOf(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            try {
                future.set(block())
            } catch (e: CancellationException) {
                future.cancel(false)
                throw e
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    private fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build(),
        )
        .build()

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, getString(R.string.app_name)), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = futureOf {
            val library = container.library
            val paths = library.downloadedPaths.value
            val items: List<MediaItem> = when (parentId) {
                ROOT -> listOf(
                    folder(LIKED, getString(R.string.liked_tracks)),
                    folder(RECENT, getString(R.string.recently_played)),
                    folder(DOWNLOADS, getString(R.string.downloads)),
                )
                LIKED -> library.likedTracksOnce().map { it.toMediaItem(paths[it.id]) }
                RECENT -> library.recentTracksOnce(100).map { it.toMediaItem(paths[it.id]) }
                DOWNLOADS -> library.downloadedTracksOnce().map { it.toMediaItem(paths[it.id]) }
                else -> emptyList()
            }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = futureOf {
            val item = when (mediaId) {
                ROOT -> folder(ROOT, getString(R.string.app_name))
                LIKED -> folder(LIKED, getString(R.string.liked_tracks))
                RECENT -> folder(RECENT, getString(R.string.recently_played))
                DOWNLOADS -> folder(DOWNLOADS, getString(R.string.downloads))
                else -> container.catalogStore.track(mediaId)?.toMediaItem(container.library.downloadedPaths.value[mediaId])
            }
            if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = futureOf {
            val paths = container.library.downloadedPaths.value
            mediaItems.map { item ->
                val path = paths[item.mediaId]
                val streamUrl = item.mediaMetadata.extras?.getString(MediaKeys.STREAM_URL)
                // Торрентный адрес надёжнее всего собирать из самого mediaId
                // («rutracker:<тема>:<файл>»): он переживает любую передачу через сессию,
                // даже если URI и extras по дороге потерялись. Без URI ExoPlayer падает
                // мгновенно, ещё до обращения к торрент-движку.
                val torrentUrl = torrentStreamUrlFromTrackId(item.mediaId)
                when {
                    item.localConfiguration != null -> item
                    torrentUrl != null -> item.buildUpon().setUri(Uri.parse(torrentUrl)).build()
                    // URI вырезается при передаче через медиасессию — восстанавливаем из метаданных.
                    path != null -> item.buildUpon().setUri(Uri.fromFile(File(path))).build()
                    streamUrl != null -> item.buildUpon().setUri(Uri.parse(streamUrl)).build()
                    else -> container.catalogStore.track(item.mediaId)?.toMediaItem(null) ?: item
                }
            }.toMutableList()
        }
    }

    private companion object {
        const val ROOT = "root"
        const val LIKED = "liked"
        const val RECENT = "recent"
        const val DOWNLOADS = "downloads"
    }
}
