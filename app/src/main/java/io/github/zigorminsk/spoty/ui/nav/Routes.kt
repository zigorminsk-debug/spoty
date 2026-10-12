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

    const val ALBUM_PATTERN = "album/{id}"
    const val ARTIST_PATTERN = "artist/{id}"
    const val PLAYLIST_PATTERN = "playlist/{id}"
    const val GENRE_PATTERN = "genre/{id}"
}
