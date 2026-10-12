package io.github.zigorminsk.spoty.work

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.zigorminsk.spoty.MainActivity
import io.github.zigorminsk.spoty.R
import io.github.zigorminsk.spoty.SpotyApp
import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.remote.safely
import io.github.zigorminsk.spoty.util.NotificationChannels
import kotlinx.coroutines.CancellationException

/**
 * Периодически пополняет локальный каталог: тренды по вашим жанрам, популярные альбомы и исполнители,
 * новые релизы тех, на кого вы подписаны (о них приходит уведомление).
 */
class CatalogSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpotyApp).container
        val catalog = container.catalog
        val library = container.library
        return try {
            container.catalog.clearCache()
            val genres = container.recommendations.personalGenres(3)
            genres.forEach { g -> quiet { catalog.trendingTracks(g, 40) } }
            quiet { catalog.trendingTracks(null, 40) }
            quiet { catalog.popularAlbums(null, 30) }
            quiet { catalog.popularArtists(30) }

            // Новые релизы у тех, на кого подписан пользователь
            val fresh = mutableListOf<Pair<String, Album>>()
            for (artist in library.followedArtistsOnce().take(25)) {
                val known = catalog.store.localArtistAlbums(artist.id).map { it.id }.toSet()
                val details = safely(null) { catalog.artistDetails(artist.id) } ?: continue
                if (known.isNotEmpty()) {
                    details.albums.filter { it.id !in known }.forEach { fresh += artist.name to it }
                }
            }
            if (fresh.isNotEmpty()) notifyNewReleases(fresh)
            container.settings.setLastSync(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun quiet(block: suspend () -> Any?) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Источник недоступен — продолжаем с остальными.
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyNewReleases(fresh: List<Pair<String, Album>>) {
        if (!NotificationChannels.canPost(applicationContext)) return
        val text = fresh.take(3).joinToString("; ") { (artist, album) -> "$artist — ${album.title}" }
        val intent = PendingIntent.getActivity(
            applicationContext, 0, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.RELEASES)
            .setSmallIcon(R.drawable.ic_album)
            .setContentTitle(applicationContext.resources.getQuantityString(R.plurals.new_releases, fresh.size, fresh.size))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(3001, notification)
    }

    companion object {
        const val UNIQUE_NAME = "catalog-sync"
    }
}
