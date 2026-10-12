package io.github.zigorminsk.spoty.data.repo

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.zigorminsk.spoty.data.local.DownloadEntity
import io.github.zigorminsk.spoty.data.local.DownloadStatus
import io.github.zigorminsk.spoty.data.local.SpotyDatabase
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.data.settings.SettingsStore
import io.github.zigorminsk.spoty.work.DownloadWorker
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Очередь скачивания треков для офлайн-прослушивания (WorkManager). */
class DownloadRepository(
    private val context: Context,
    db: SpotyDatabase,
    private val store: CatalogStore,
    private val settings: SettingsStore,
) {
    private val dao = db.library()
    private val workManager get() = WorkManager.getInstance(context)

    suspend fun enqueue(tracks: List<Track>) = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext
        store.saveTracks(tracks)
        val network = if (settings.value.wifiOnlyDownloads) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder().setRequiredNetworkType(network).build()
        for (track in tracks.distinctBy { it.id }) {
            val existing = dao.download(track.id)
            if (existing != null && existing.status == DownloadStatus.DONE && existing.filePath?.let { File(it).exists() } == true) continue
            dao.upsertDownload(
                DownloadEntity(track.id, null, 0, DownloadStatus.QUEUED, 0, existing?.createdAt ?: System.currentTimeMillis()),
            )
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf(DownloadWorker.KEY_TRACK_ID to track.id))
                .setConstraints(constraints)
                .addTag(DownloadWorker.TAG)
                .build()
            workManager.enqueueUniqueWork("download:${track.id}", ExistingWorkPolicy.REPLACE, request)
        }
    }

    suspend fun remove(trackId: String) = withContext(Dispatchers.IO) {
        workManager.cancelUniqueWork("download:$trackId")
        dao.download(trackId)?.filePath?.let { runCatching { File(it).delete() } }
        dao.deleteDownload(trackId)
    }

    suspend fun removeAll() = withContext(Dispatchers.IO) {
        workManager.cancelAllWorkByTag(DownloadWorker.TAG)
        dao.finishedDownloads().forEach { d -> d.filePath?.let { runCatching { File(it).delete() } } }
        dao.downloadedTracks().forEach { dao.deleteDownload(it.id) }
        dao.deleteUnfinishedDownloads()
    }

    suspend fun totalSizeBytes(): Long = withContext(Dispatchers.IO) { dao.finishedDownloads().sumOf { it.sizeBytes } }
}
