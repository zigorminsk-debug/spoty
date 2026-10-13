package io.github.zigorminsk.spoty.di

import android.app.Application
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.zigorminsk.spoty.BuildConfig
import io.github.zigorminsk.spoty.data.local.SpotyDatabase
import io.github.zigorminsk.spoty.data.remote.ArchiveSource
import io.github.zigorminsk.spoty.data.remote.AudiusSource
import io.github.zigorminsk.spoty.data.remote.Http
import io.github.zigorminsk.spoty.data.remote.JamendoSource
import io.github.zigorminsk.spoty.data.remote.LrcLibClient
import io.github.zigorminsk.spoty.data.remote.MusicSource
import io.github.zigorminsk.spoty.data.remote.rutracker.RuTrackerClient
import io.github.zigorminsk.spoty.data.torrent.TorrentEngine
import io.github.zigorminsk.spoty.data.repo.BackupRepository
import io.github.zigorminsk.spoty.data.repo.CatalogRepository
import io.github.zigorminsk.spoty.data.repo.CatalogStore
import io.github.zigorminsk.spoty.data.repo.DownloadRepository
import io.github.zigorminsk.spoty.data.repo.LibraryRepository
import io.github.zigorminsk.spoty.data.repo.RecommendationRepository
import io.github.zigorminsk.spoty.data.settings.SettingsStore
import io.github.zigorminsk.spoty.player.PlayerController
import io.github.zigorminsk.spoty.player.RoutingDataSource
import io.github.zigorminsk.spoty.player.TorrentDataSource
import io.github.zigorminsk.spoty.player.WaveController
import io.github.zigorminsk.spoty.work.CatalogSyncWorker
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/** Ручной DI-контейнер: всё приложение собирается здесь, без фреймворков. */
class AppContainer(val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val userAgent: String = "Spoty/${BuildConfig.VERSION_NAME} (Android; ${BuildConfig.REPO_URL})"

    val settings = SettingsStore(app, appScope)

    /** Клиент для JSON API: ограниченные таймауты. */
    val apiClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        })
        .build()

    /** Клиент для потоков музыки, обложек и загрузок: без общего лимита на вызов. */
    val mediaClient: OkHttpClient = apiClient.newBuilder()
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val http = Http(apiClient)

    val database: SpotyDatabase = SpotyDatabase.create(app)
    val catalogStore = CatalogStore(database)

    val sources: List<MusicSource> = listOf(
        AudiusSource(http),
        ArchiveSource(http),
        JamendoSource(http) { settings.value.jamendoClientId },
    )

    val catalog = CatalogRepository(sources, settings, catalogStore, appScope)
    val library = LibraryRepository(database, catalogStore, appScope)
    val recommendations = RecommendationRepository(catalog, library)
    val downloads = DownloadRepository(app, database, catalogStore, settings)
    val lyrics = LrcLibClient(http)
    val backup = BackupRepository(library, catalogStore)

    /** RuTracker: вход по логину/паролю, поиск раздач, скачивание .torrent. */
    val rutracker = RuTrackerClient(apiClient, settings)

    /** Встроенный BitTorrent-движок: проигрывание музыки прямо из раздач. */
    val torrentEngine = TorrentEngine(app).also { engine ->
        engine.torrentFetcher = { topicId -> rutracker.torrentBytes(topicId) }
    }

    val player = PlayerController(app, library)
    val wave = WaveController(player, recommendations, appScope)

    /** Идентификатор аудиосессии ExoPlayer — нужен, чтобы открыть системный эквалайзер. */
    @Volatile
    var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET

    /** Кэш аудио на диске: повторное прослушивание не тратит трафик. Создаётся один раз. */
    val mediaCache: SimpleCache by lazy {
        val bytes = settings.value.cacheMb.toLong().coerceAtLeast(64L) * 1024L * 1024L
        SimpleCache(File(app.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(bytes), StandaloneDatabaseProvider(app))
    }

    fun mediaDataSourceFactory(): DataSource.Factory {
        val upstream = DefaultDataSource.Factory(app, OkHttpDataSource.Factory(mediaClient).setUserAgent(userAgent))
        val cached = CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        // torrent:// играет напрямую из каталога раздачи (кэш не нужен — данные уже на диске).
        return DataSource.Factory {
            RoutingDataSource(cached.createDataSource(), TorrentDataSource(torrentEngine))
        }
    }

    fun clearMediaCache() {
        runCatching { mediaCache.keys.toList().forEach { mediaCache.removeResource(it) } }
    }

    /** Запускается при старте приложения. */
    fun start() {
        player.connect()
        appScope.launch {
            settings.state.collect { s -> scheduleSync(s.autoSync) }
        }
    }

    private fun scheduleSync(enabled: Boolean) {
        val wm = WorkManager.getInstance(app)
        if (!enabled) {
            wm.cancelUniqueWork(CatalogSyncWorker.UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(CatalogSyncWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
