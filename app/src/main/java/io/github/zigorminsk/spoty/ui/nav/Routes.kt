package io.github.zigorminsk.spoty.ui.nav

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val CATALOG = "catalog"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val QUEUE = "queue"

    fun album(id: String) = "album/${Uri.encode(id)}"
    fun artist(id: String) = "artist/${Uri.encode(id)}"
    fun playlist(id: Long) = "playlist/$id"
    fun genre(id: String) = "genre/$id"
    fun rutracker(query: String = "") = "rutracker?q=${Uri.encode(query)}"
    fun rutrackerTopic(id: Long, title: String) = "rutracker/topic/$id?title=${Uri.encode(title)}"
    fun ztorrents(query: String = "") = "ztorrents?q=${Uri.encode(query)}"
    fun ztorrentsTopic(id: Long, title: String) = "ztorrents/topic/$id?title=${Uri.encode(title)}"

    const val ALBUM_PATTERN = "album/{id}"
    const val ARTIST_PATTERN = "artist/{id}"
    const val PLAYLIST_PATTERN = "playlist/{id}"
    const val GENRE_PATTERN = "genre/{id}"
    const val RUTRACKER_PATTERN = "rutracker?q={q}"
    const val RUTRACKER_TOPIC_PATTERN = "rutracker/topic/{id}?title={title}"
    const val ZTORRENTS_PATTERN = "ztorrents?q={q}"
    const val ZTORRENTS_TOPIC_PATTERN = "ztorrents/topic/{id}?title={title}"
}
