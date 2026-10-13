package io.github.zigorminsk.spoty.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import io.github.zigorminsk.spoty.data.remote.rutracker.TORRENT_URI_SCHEME
import io.github.zigorminsk.spoty.data.remote.rutracker.parseTorrentUriParts
import io.github.zigorminsk.spoty.data.torrent.TorrentEngine
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo

/**
 * Источник данных ExoPlayer для URI вида torrent://<topicId>/<fileIndex>.
 * Читает файл прямо из каталога раздачи: если нужный кусок ещё не скачан,
 * ставит ему максимальный приоритет и ждёт — так музыка стримится из торрента.
 */
class TorrentDataSource(private val engine: TorrentEngine) : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var raf: RandomAccessFile? = null
    private var file: File? = null
    private var info: TorrentInfo? = null
    private var handle: TorrentHandle? = null

    private var fileIndex = 0
    private var fileGlobalOffset = 0L
    private var fileSize = 0L

    /** Позиция чтения внутри файла раздачи. */
    private var position = 0L
    private var bytesRemaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        val u = dataSpec.uri
        // torrent://<источник>/<topicId>/<fileIndex>: источник решает, где брать .torrent.
        val (sourceKey, topicId, index) = parseTorrentUriParts(u.host, u.pathSegments)
            ?: throw IOException("bad torrent uri: $u")
        uri = u
        transferInitializing(dataSpec)

        try {
            // Добавляет раздачу в сессию; .torrent берётся из кэша или скачивается с трекера.
            val (ti, th) = engine.prepareBlocking(sourceKey, topicId)
            if (index < 0 || index >= ti.numFiles()) throw IOException("bad file index: $u")
            info = ti
            handle = th
            fileIndex = index
            val fs = ti.files()
            fileSize = fs.fileSize(index)
            fileGlobalOffset = fs.fileOffset(index)
            file = engine.savedFile(ti, index)

            if (dataSpec.position > fileSize) throw IOException("position out of range: $u")
            position = dataSpec.position
            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length.coerceAtMost(fileSize - position)
            } else {
                fileSize - position
            }

            // Включаем скачивание нужного файла (весь массив приоритетов, не один файл),
            // выводим раздачу в начало очереди и просим куски с текущей позиции.
            engine.activateFile(th, ti, index)
            requestUrgentPieces(pieceFor(position))
            requestTailPieces()
        } catch (e: Exception) {
            Log.w(TorrentEngine.TAG, "failed to open $u", e)
            uri = null
            info = null
            handle = null
            // ExoPlayer понимает только IOException: остальное стало бы «неожиданной» ошибкой.
            if (e is IOException) throw e
            throw IOException(e.message ?: "failed to open torrent stream", e)
        }

        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
        val ti = info ?: throw IOException("source is not open")

        val piece = pieceFor(position)
        waitForPiece(piece)

        // Не читаем дальше конца текущего куска: следующий может быть ещё не скачан.
        val pieceLen = ti.pieceLength().toLong()
        val globalPos = fileGlobalOffset + position
        val bytesLeftInPiece = (piece + 1L) * pieceLen - globalPos
        val toRead = minOf(length.toLong(), bytesRemaining, bytesLeftInPiece).toInt().coerceAtLeast(1)

        var waitedMs = 0L
        while (true) {
            val f = file ?: throw IOException("source is not open")
            // Файл появляется на диске чуть позже, чем кусок проходит проверку хеша.
            val read = try {
                val r = raf ?: RandomAccessFile(f, "r").also { raf = it }
                r.seek(position)
                r.read(buffer, offset, toRead)
            } catch (e: IOException) {
                -1
            }
            if (read > 0) {
                position += read
                bytesRemaining -= read
                bytesTransferred(read)
                return read
            }
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            if (waitedMs >= FLUSH_TIMEOUT_MS) throw IOException("torrent data is not available on disk")
            runCatching { raf?.close() }
            raf = null
            sleep(FLUSH_POLL_MS)
            waitedMs += FLUSH_POLL_MS
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        runCatching { raf?.close() }
        raf = null
        file = null
        info = null
        handle = null
        position = 0L
        bytesRemaining = 0L
        if (uri != null) {
            uri = null
            transferEnded()
        }
    }

    // ───────────── Куски ─────────────

    private fun pieceFor(filePosition: Long): Int {
        val ti = info ?: return 0
        return ((fileGlobalOffset + filePosition) / ti.pieceLength().toLong()).toInt()
    }

    private fun lastPieceOfFile(): Int {
        val ti = info ?: return 0
        if (fileSize <= 0) return pieceFor(0)
        return ((fileGlobalOffset + fileSize - 1) / ti.pieceLength().toLong()).toInt()
    }

    /** Просит libtorrent качать с текущего места: дедлайны на ближайшие куски. */
    private fun requestUrgentPieces(fromPiece: Int) {
        val th = handle ?: return
        if (!th.isValid) return
        val last = lastPieceOfFile()
        runCatching {
            th.setSequentialRange(fromPiece.coerceAtMost(last), last)
            var delay = 0
            for (i in fromPiece..minOf(fromPiece + URGENT_PIECES, last)) {
                th.setPieceDeadline(i, delay)
                delay += 700
            }
        }
    }

    /**
     * Последние куски файла нужны некоторым контейнерам сразу (у M4A/MP4 индекс moov
     * часто лежит в конце файла): просим и их, чтобы экстрактор не ждал весь файл.
     */
    private fun requestTailPieces() {
        val th = handle ?: return
        if (!th.isValid) return
        val last = lastPieceOfFile()
        runCatching {
            var delay = 0
            for (i in maxOf(pieceFor(0), last - URGENT_PIECES) .. last) {
                th.setPieceDeadline(i, delay)
                delay += 700
            }
        }
    }

    private fun waitForPiece(piece: Int) {
        val th = handle ?: throw IOException("source is not open")
        if (th.isValid && th.havePiece(piece)) return
        requestUrgentPieces(piece)
        var waitedMs = 0L
        var lastAnnounceMs = 0L
        while (true) {
            if (!th.isValid) throw IOException("torrent was removed")
            if (th.havePiece(piece)) return
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
            if (waitedMs >= PIECE_TIMEOUT_MS) {
                // В ошибке — состояние раздачи: по ней видно, нет пиров вообще
                // или пиры есть, но кусок просто не успели отдать.
                val detail = runCatching {
                    val st = th.status()
                    "seeds=${st.numSeeds()}, peers=${st.numPeers()}, rate=${st.downloadPayloadRate()} B/s"
                }.getOrDefault("status unavailable")
                throw IOException("timed out waiting for torrent data (piece $piece, $detail)")
            }
            sleep(PIECE_POLL_MS)
            waitedMs += PIECE_POLL_MS
            if (waitedMs % 5_000L == 0L) requestUrgentPieces(piece)
            // Раздача могла добавиться давно и «заснуть»: периодически переанонсируемся.
            if (waitedMs - lastAnnounceMs >= ANNOUNCE_INTERVAL_MS) {
                lastAnnounceMs = waitedMs
                runCatching {
                    th.forceReannounce()
                    th.forceDHTAnnounce()
                }
            }
        }
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException()
        }
    }

    private companion object {
        const val URGENT_PIECES = 6
        const val PIECE_POLL_MS = 250L
        // Холодная раздача может искать пиров минутами: пока ждём кусок, плеер
        // показывает буферизацию, а не ошибку и не пропускает трек.
        const val PIECE_TIMEOUT_MS = 180_000L
        const val ANNOUNCE_INTERVAL_MS = 30_000L
        const val FLUSH_POLL_MS = 100L
        const val FLUSH_TIMEOUT_MS = 15_000L
    }
}

/**
 * Направляет torrent:// в [TorrentDataSource], всё остальное — в обычный источник
 * (сеть с кэшем или локальные файлы).
 */
class RoutingDataSource(
    private val default: DataSource,
    private val torrent: DataSource,
) : DataSource {

    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        default.addTransferListener(transferListener)
        torrent.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(active == null) { "source is already open" }
        val source = if (dataSpec.uri.scheme == TORRENT_URI_SCHEME) torrent else default
        active = source
        return try {
            source.open(dataSpec)
        } catch (e: Exception) {
            // Не оставляем «активный» источник после неудачного открытия:
            // иначе повторная попытка загрузки упиралась бы в check выше.
            active = null
            runCatching { source.close() }
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: throw IOException("source is not open")

    override fun getUri(): Uri? = active?.uri

    override fun close() {
        val source = active
        active = null
        source?.close()
    }
}
