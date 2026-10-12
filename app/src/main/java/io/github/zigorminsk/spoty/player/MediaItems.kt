package io.github.zigorminsk.spoty.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import io.github.zigorminsk.spoty.data.model.Track
import java.io.File

/** Ключи дополнительных данных трека в MediaMetadata.extras. */
object MediaKeys {
    const val SOURCE = "spoty.source"
    const val ARTIST_ID = "spoty.artistId"
    const val ALBUM_ID = "spoty.albumId"
    const val GENRE = "spoty.genre"
    const val LICENSE = "spoty.license"
    const val STREAM_URL = "spoty.streamUrl"
    const val WEB_URL = "spoty.webUrl"
    const val DURATION = "spoty.duration"
    const val PLAY_COUNT = "spoty.playCount"
    const val TRACK_NUMBER = "spoty.trackNumber"
    const val OFFLINE = "spoty.offline"
}

/** Трек → элемент очереди. Если файл скачан — играем локальную копию. */
fun Track.toMediaItem(localPath: String? = null): MediaItem {
    val extras = Bundle().apply {
        putString(MediaKeys.SOURCE, source)
        putString(MediaKeys.ARTIST_ID, artistId)
        putString(MediaKeys.ALBUM_ID, albumId)
        putString(MediaKeys.GENRE, genre)
        putString(MediaKeys.LICENSE, license)
        putString(MediaKeys.STREAM_URL, streamUrl)
        putString(MediaKeys.WEB_URL, webUrl)
        putLong(MediaKeys.DURATION, durationMs)
        putLong(MediaKeys.PLAY_COUNT, playCount)
        putInt(MediaKeys.TRACK_NUMBER, trackNumber)
        putBoolean(MediaKeys.OFFLINE, localPath != null)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artistName)
        .setAlbumTitle(albumTitle)
        .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
        .setIsPlayable(true)
        .setIsBrowsable(false)
        .setExtras(extras)
        .build()
    val uri = if (localPath != null) Uri.fromFile(File(localPath)) else Uri.parse(streamUrl)
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}

/** Элемент очереди → трек (для интерфейса; без обращения к БД). */
fun MediaItem.toTrack(): Track {
    val md = mediaMetadata
    val ex = md.extras
    return Track(
        id = mediaId,
        source = ex?.getString(MediaKeys.SOURCE).orEmpty(),
        title = md.title?.toString().orEmpty(),
        artistId = ex?.getString(MediaKeys.ARTIST_ID),
        artistName = md.artist?.toString().orEmpty(),
        albumId = ex?.getString(MediaKeys.ALBUM_ID),
        albumTitle = md.albumTitle?.toString(),
        trackNumber = ex?.getInt(MediaKeys.TRACK_NUMBER) ?: 0,
        durationMs = ex?.getLong(MediaKeys.DURATION) ?: 0L,
        artworkUrl = md.artworkUri?.toString(),
        streamUrl = ex?.getString(MediaKeys.STREAM_URL) ?: localConfiguration?.uri?.toString().orEmpty(),
        genre = ex?.getString(MediaKeys.GENRE),
        license = ex?.getString(MediaKeys.LICENSE),
        playCount = ex?.getLong(MediaKeys.PLAY_COUNT) ?: 0L,
        webUrl = ex?.getString(MediaKeys.WEB_URL),
    )
}
