package io.github.zigorminsk.spoty.data.remote

import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.AlbumDetails
import io.github.zigorminsk.spoty.data.model.Artist
import io.github.zigorminsk.spoty.data.model.ArtistDetails
import io.github.zigorminsk.spoty.data.model.Genre
import io.github.zigorminsk.spoty.data.model.SourceId
import io.github.zigorminsk.spoty.data.model.Track

/**
 * Свободный источник музыки. Все идентификаторы наружу — вида «источник:id»,
 * внутрь методов [album]/[artist] приходит «сырой» id без префикса.
 */
interface MusicSource {
    val id: SourceId

    /** false — источник нельзя использовать (например, не задан ключ API). */
    val isAvailable: Boolean get() = true

    suspend fun searchTracks(query: String, limit: Int = 20): List<Track>
    suspend fun searchAlbums(query: String, limit: Int = 20): List<Album>
    suspend fun searchArtists(query: String, limit: Int = 20): List<Artist>

    suspend fun trendingTracks(genre: Genre?, limit: Int = 30): List<Track>
    suspend fun popularAlbums(genre: Genre?, limit: Int = 20): List<Album>
    suspend fun popularArtists(limit: Int = 20): List<Artist> = emptyList()

    suspend fun album(rawId: String): AlbumDetails
    suspend fun artist(rawId: String): ArtistDetails
}

/** Разбор «источник:id». */
fun splitId(fullId: String): Pair<String, String> =
    fullId.substringBefore(':') to fullId.substringAfter(':')
