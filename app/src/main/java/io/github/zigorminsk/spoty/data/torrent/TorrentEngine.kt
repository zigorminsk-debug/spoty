package io.github.zigorminsk.spoty.data.torrent

import android.content.Context
import java.io.File
import java.io.IOException
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

    /** Как получить .torrent по идентификатору темы (подставляется из AppContainer). */
    @Volatile
    var torrentFetcher: (suspend (Long) -> ByteArray)? = null

    private val session = SessionManager()
    private val lock = Any()
    private val addMutex = Mutex()

    private val root: File get() = File(context.getExternalFilesDir(null), "torrents")

    /** Куда libtorrent пишет данные раздач. */
    val dataDir: File get() = File(root, "data")

    private val metaDir: File get() = File(root, "meta")

    private fun metaFile(topicId: Long) = File(metaDir, "$topicId.torrent")

    fun ensureStarted() {
        synchronized(lock) {
            if (!session.isRunning) session.start()
        }
    }

    /** Метаданные раздачи из локального кэша .torrent-файлов. */
    fun cachedInfo(topicId: Long): TorrentInfo? =
        metaFile(topicId).takeIf { it.exists() }?.let { f -> runCatching { TorrentInfo(f) }.getOrNull() }

    /**
     * Добавляет раздачу в сессию (скачивая .torrent при необходимости) и возвращает метаданные.
     * Повторный вызов для уже добавленной раздачи безопасен.
     */
    suspend fun prepare(topicId: Long): TorrentInfo = addMutex.withLock {
        ensureStarted()
        val info = cachedInfo(topicId) ?: run {
            val fetch = torrentFetcher ?: throw IOException("torrent fetcher is not configured")
            val bytes = fetch(topicId)
            val parsed = TorrentInfo(bytes)
            metaDir.mkdirs()
            metaFile(topicId).writeBytes(bytes)
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
            withTimeout(20_000L) {
                while (session.find(info.infoHash()) == null) delay(100L)
            }
        }
        info
    }

    /**
     * Блокирующая версия [prepare] для потока загрузки ExoPlayer.
     * Возвращает метаданные и handle раздачи в сессии.
     */
    fun prepareBlocking(topicId: Long): Pair<TorrentInfo, TorrentHandle> = runBlocking {
        val info = prepare(topicId)
        val handle = session.find(info.infoHash()) ?: throw IOException("torrent is not in session")
        info to handle
    }

    fun handle(topicId: Long): TorrentHandle? {
        ensureStarted()
        return cachedInfo(topicId)?.let { session.find(it.infoHash()) }
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
    fun stats(topicId: Long): TorrentStats? {
        val h = handle(topicId) ?: return null
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
            root.deleteRecursively()
        }
    }

    private companion object {
        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "ogg", "oga", "opus", "m4a", "m4b", "aac",
            "wav", "aif", "aiff", "ape", "wv", "mka", "wma", "dsf",
        )
    }
}
