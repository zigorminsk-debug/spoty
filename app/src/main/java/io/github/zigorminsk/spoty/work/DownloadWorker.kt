package io.github.zigorminsk.spoty.work

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.SpotyApp
import io.github.zigorminsk.spoty.data.local.DownloadEntity
import io.github.zigorminsk.spoty.data.local.DownloadStatus
import io.github.zigorminsk.spoty.util.NotificationChannels
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Скачивает один трек в хранилище приложения и отмечает его в БД как доступный офлайн. */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(applicationContext.getString(R.string.downloading), 0)

    override suspend fun doWork(): Result {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return Result.failure()
        val container = (applicationContext as SpotyApp).container
        val dao = container.database.library()
        val track = container.catalogStore.track(trackId) ?: return Result.failure()
        val createdAt = dao.download(trackId)?.createdAt ?: System.currentTimeMillis()

        dao.upsertDownload(DownloadEntity(trackId, null, 0, DownloadStatus.RUNNING, 0, createdAt))
        try {
            setForeground(foregroundInfo("${track.artistName} — ${track.title}", 0))
        } catch (_: Exception) {
            // Запуск foreground из фона может быть запрещён системой — качаем без уведомления.
        }

        val dir = File(
            applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: applicationContext.filesDir,
            "downloads",
        ).apply { mkdirs() }
        val name = sha1(trackId)
        val part = File(dir, "$name.part")

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(track.streamUrl).header("User-Agent", container.userAgent).build()
                container.mediaClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty body")
                    val total = body.contentLength()
                    val extension = extensionFor(response.header("Content-Type"), track.streamUrl)
                    var done = 0L
                    var lastReported = -1
                    part.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                done += read
                                if (total > 0) {
                                    val percent = (done * 100 / total).toInt()
                                    if (percent >= lastReported + 3) {
                                        lastReported = percent
                                        dao.setDownloadProgress(trackId, DownloadStatus.RUNNING, percent)
                                        runCatching { setForeground(foregroundInfo("${track.artistName} — ${track.title}", percent)) }
                                    }
                                }
                            }
                        }
                    }
                    val finalFile = File(dir, "$name.$extension")
                    if (finalFile.exists()) finalFile.delete()
                    if (!part.renameTo(finalFile)) throw IOException("Cannot rename file")
                    dao.upsertDownload(DownloadEntity(trackId, finalFile.absolutePath, finalFile.length(), DownloadStatus.DONE, 100, createdAt))
                }
                Result.success()
            } catch (e: CancellationException) {
                part.delete()
                throw e
            } catch (e: Exception) {
                part.delete()
                if (runAttemptCount < 2) {
                    dao.setDownloadProgress(trackId, DownloadStatus.QUEUED, 0)
                    Result.retry()
                } else {
                    dao.setDownloadProgress(trackId, DownloadStatus.FAILED, 0)
                    Result.failure()
                }
            }
        }
    }

    private fun foregroundInfo(title: String, percent: Int): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(applicationContext, NotificationChannels.DOWNLOADS)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(applicationContext.getString(R.string.downloading))
            .setContentText(title)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun extensionFor(contentType: String?, url: String): String {
        val ct = contentType?.lowercase().orEmpty()
        return when {
            ct.contains("mpeg") || ct.contains("mp3") -> "mp3"
            ct.contains("ogg") -> "ogg"
            ct.contains("flac") -> "flac"
            ct.contains("wav") -> "wav"
            ct.contains("mp4") || ct.contains("aac") || ct.contains("m4a") -> "m4a"
            else -> {
                val path = url.substringBefore('?').substringAfterLast('.', "mp3").lowercase()
                if (path in setOf("mp3", "ogg", "m4a", "flac", "wav", "aac")) path else "mp3"
            }
        }
    }

    companion object {
        const val KEY_TRACK_ID = "trackId"
        const val TAG = "downloads"
        private const val NOTIFICATION_ID = 2001
    }
}
