package io.github.zigorminsk.spoty.data.torrent

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo

/** Аудиофайл внутри раздачи. */
data class TorrentFileEntry(
    val index: Int,
    val path: String,
    /** Имя файла без расширения. */
    val name: String,
    val sizeBytes: Long,
)

/** Снимок состояния раздачи для интерфейса. */
data class TorrentStats(
    val progress: Float,
    val totalDoneBytes: Long,
    val downloadRateBps: Long,
    val seeds: Int,
    val peers: Int,
    /** Сколько байт каждого файла уже скачано (гранулярность — целые куски). */
    val fileDoneBytes: LongArray,
) {
    fun fileComplete(file: TorrentFileEntry): Boolean =
        fileDoneBytes.getOrNull(file.index)?.let { it >= file.sizeBytes } == true
}

/**
 * Встроенный BitTorrent-движок (libtorrent4j) для проигрывания музыки из раздач.
 * Данные складываются в внешний каталог приложения; файлы качаются по требованию:
 * при добавлении раздачи все файлы выключены, нужный включается при воспроизведении.
 */
class TorrentEngine(private val context: Context) {

    /** Как получить .torrent по источнику и идентификатору темы (подставляется из AppContainer). */
    @Volatile
    var torrentFetcher: (suspend (source: String, topicId: Long) -> ByteArray)? = null

    private val session = SessionManager()
    private val lock = Any()
    private val addMutex = Mutex()

    /** Разобранные .torrent в памяти: иначе каждый опрос статистики заново парсит файл с диска. */
    private val infoCache = ConcurrentHashMap<String, TorrentInfo>()

    // Внешнее хранилище может быть недоступно (getExternalFilesDir вернёт null) —
    // тогда File(null, ...) превращается в относительный путь «torrents» в чужой папке,
    // libtorrent не может создать файлы, и раздача молча не качается. Падаем на внутреннее.
    private val root: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "torrents")

    /** Куда libtorrent пишет данные раздач. */
    val dataDir: File get() = File(root, "data")

    private val metaDir: File get() = File(root, "meta")

    private fun metaFile(source: String, topicId: Long) = File(metaDir, "$source-$topicId.torrent")

    fun ensureStarted() {
        synchronized(lock) {
            if (!session.isRunning) {
                session.start()
                // Настройки по умолчанию libtorrent держат в активных загрузках всего
                // 3 раздачи: каждая открытая тема добавляет торрент в сессию и не удаляет,
                // поэтому после нескольких альбомов нужная раздача вставала в очередь
                // авто-менеджера на паузу и не качалась вообще. Поднимаем лимиты.
                runCatching {
                    session.maxActiveDownloads(16)
                    session.maxActiveSeeds(8)
                    session.maxConnections(300)
                }.onFailure { Log.w(TAG, "failed to apply session settings", it) }
            }
        }
    }

    /** Метаданные раздачи из локального кэша .torrent-файлов. */
    fun cachedInfo(source: String, topicId: Long): TorrentInfo? {
        val key = "$source-$topicId"
        infoCache[key]?.let { return it }
        val parsed = metaFile(source, topicId).takeIf { it.exists() }
            ?.let { f -> runCatching { TorrentInfo(f) }.getOrNull() }
        if (parsed != null) infoCache[key] = parsed
        return parsed
    }

    /**
     * Добавляет раздачу в сессию (скачивая .torrent при необходимости) и возвращает метаданные.
     * Повторный вызов для уже добавленной раздачи безопасен.
     */
    suspend fun prepare(source: String, topicId: Long): TorrentInfo = addMutex.withLock {
        ensureStarted()
        val info = cachedInfo(source, topicId) ?: run {
            val fetch = torrentFetcher ?: throw IOException("torrent fetcher is not configured")
            val bytes = fetch(source, topicId)
            val parsed = TorrentInfo(bytes)
            metaDir.mkdirs()
            metaFile(source, topicId).writeBytes(bytes)
            infoCache["$source-$topicId"] = parsed
            parsed
        }
        if (session.find(info.infoHash()) == null) {
            dataDir.mkdirs()
            session.download(
                info,
                dataDir,
                null,
                Priority.array(Priority.IGNORE, info.numFiles()),
                null,
                TorrentFlags.SEQUENTIAL_DOWNLOAD,
            )
            // Торрент добавляется асинхронно — дожидаемся появления в сессии.
            // TimeoutCancellationException — это CancellationException: если дать ей
            // вылететь наружу, загрузчик ExoPlayer и ViewModel примут её за отмену
            // корутины и молча сдадутся без ошибки и без музыки. Нужен честный IOException.
            try {
                withTimeout(20_000L) {
                    while (session.find(info.infoHash()) == null) delay(100L)
                }
            } catch (e: TimeoutCancellationException) {
                throw IOException("timed out adding torrent to session", e)
            }
        }
        info
    }

    /**
     * Блокирующая версия [prepare] для потока загрузки ExoPlayer.
     * Возвращает метаданные и handle раздачи в сессии.
     * Любую ошибку приводит к [IOException] — только её ExoPlayer превращает
     * в понятную ошибку воспроизведения, а не в «неожиданный» сбой плеера.
     */
    fun prepareBlocking(source: String, topicId: Long): Pair<TorrentInfo, TorrentHandle> =
        try {
            runBlocking {
                val info = prepare(source, topicId)
                val handle = session.find(info.infoHash())
                    ?: throw IOException("torrent is not in session")
                info to handle
            }
        } catch (e: IOException) {
            throw e
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted while preparing torrent", e)
        } catch (e: Exception) {
            throw IOException(e.message ?: "failed to prepare torrent", e)
        }

    /**
     * Включает скачивание одного файла раздачи: ему — максимальный приоритет,
     * остальным — «не качать», и выводит раздачу в начало очереди сессии.
     * Ставится именно весь массив приоритетов: одиночный filePriority() на раздаче,
     * которую libtorrent уже посчитал завершённой (все файлы были выключены),
     * мог не пересчитать «нужные» куски, и торрент оставался без пиров и данных.
     */
    fun activateFile(handle: TorrentHandle, info: TorrentInfo, fileIndex: Int) {
        if (!handle.isValid) return
        val priorities = Priority.array(Priority.IGNORE, info.numFiles())
        if (fileIndex in priorities.indices) priorities[fileIndex] = Priority.TOP_PRIORITY
        runCatching {
            handle.prioritizeFiles(priorities)
            handle.queuePositionTop()
            handle.resume()
            // Сразу просим трекеры и DHT дать пиров, а не ждём планового анонса.
            handle.forceReannounce()
            handle.forceDHTAnnounce()
            handle.forceLSDAnnounce()
        }.onFailure { Log.w(TAG, "failed to activate torrent file $fileIndex", it) }
    }

    fun handle(source: String, topicId: Long): TorrentHandle? {
        ensureStarted()
        return cachedInfo(source, topicId)?.let { session.find(it.infoHash()) }
    }

    /** Аудиофайлы раздачи в порядке путей (обычно совпадает с порядком треков). */
    fun audioFiles(info: TorrentInfo): List<TorrentFileEntry> {
        val fs = info.files()
        return (0 until fs.numFiles()).mapNotNull { i ->
            val path = fs.filePath(i)
            val name = path.substringAfterLast('/').substringAfterLast('\\')
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in AUDIO_EXTENSIONS) return@mapNotNull null
            TorrentFileEntry(
                index = i,
                path = path,
                name = name.removeSuffix(".$ext"),
                sizeBytes = fs.fileSize(i),
            )
        }.sortedBy { it.path.lowercase() }
    }

    /** Файл на диске, куда libtorrent пишет содержимое файла раздачи. */
    fun savedFile(info: TorrentInfo, fileIndex: Int): File =
        File(dataDir, info.files().filePath(fileIndex))

    /** Текущее состояние раздачи (null, если она не в сессии). */
    fun stats(source: String, topicId: Long): TorrentStats? {
        val h = handle(source, topicId) ?: return null
        if (!h.isValid) return null
        val st = h.status()
        val fileDone = runCatching { h.fileProgress(TorrentHandle.PIECE_GRANULARITY) }.getOrNull() ?: LongArray(0)
        return TorrentStats(
            progress = st.progress(),
            totalDoneBytes = st.totalDone(),
            downloadRateBps = st.downloadPayloadRate().toLong(),
            seeds = st.numSeeds(),
            peers = st.numPeers(),
            fileDoneBytes = fileDone,
        )
    }

    /** Полная очистка: останавливает сессию и удаляет скачанные данные и .torrent-файлы. */
    suspend fun clearData() = addMutex.withLock {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (session.isRunning) session.stop()
            }
            infoCache.clear()
            root.deleteRecursively()
        }
    }

    companion object {
        const val TAG = "SpotyTorrent"

        /**
         * Только форматы, которые ExoPlayer/Media3 реально умеет разбирать и декодировать.
         * APE, WavPack, WMA, DSF и AIFF раньше попадали в список треков, но плеер
         * гарантированно падал на них с ошибкой «no extractor / unsupported» —
         * такие раздачи честнее вообще не показывать как слушабельные.
         */
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "oga", "opus", "m4a", "m4b", "aac",
            "wav", "mka",
        )
    }
}
