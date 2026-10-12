package io.github.zigorminsk.spoty.data.repo

import io.github.zigorminsk.spoty.data.model.Genre
import io.github.zigorminsk.spoty.data.model.Genres
import io.github.zigorminsk.spoty.data.model.Track
import io.github.zigorminsk.spoty.data.model.WaveMoods
import io.github.zigorminsk.spoty.data.remote.safely
import io.github.zigorminsk.spoty.util.interleave
import io.github.zigorminsk.spoty.util.spreadByKey
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Что лежит в основе «Моей волны»: настроение, жанр, исполнитель — или вкусы пользователя. */
data class WaveSeed(
    val moodId: String? = null,
    val genreId: String? = null,
    val artistId: String? = null,
    val artistName: String? = null,
) {
    val isPersonal: Boolean get() = moodId == null && genreId == null && artistId == null
}

/** Рекомендации без сервера: вкусы считаются по лайкам и истории прослушиваний на устройстве. */
class RecommendationRepository(
    private val catalog: CatalogRepository,
    private val library: LibraryRepository,
) {
    private val defaultGenres = listOf("electronic", "pop", "lofi", "rock", "hiphop", "ambient", "indie", "jazz")

    /** Любимые жанры пользователя; если данных мало — добираем популярными. */
    suspend fun personalGenres(max: Int = 4): List<Genre> {
        val own = library.topGenres(6).mapNotNull { Genres.match(it) }.distinct()
        val padding = defaultGenres.shuffled().mapNotNull { Genres.byId(it) }
        return (own + padding).distinct().take(max)
    }

    /** Подборка «Для вас». */
    suspend fun forYou(size: Int = 40): List<Track> = coroutineScope {
        val genres = personalGenres(3)
        val disliked = library.dislikedIdsOnce()
        val pools = genres.map { g -> async { safely(emptyList<Track>()) { catalog.trendingTracks(g, 25) } } }
        val merged = interleave(pools.map { it.await() }).distinctBy { it.id }.filter { it.id !in disliked }
        merged.take(size)
    }

    /** Следующая порция треков для волны. Не повторяет [exclude] и дизлайкнутое. */
    suspend fun nextWaveBatch(seed: WaveSeed, exclude: Set<String>, size: Int = 12): List<Track> = coroutineScope {
        val disliked = library.dislikedIdsOnce()
        val pools = mutableListOf<Deferred<List<Track>>>()
        val mood = WaveMoods.byId(seed.moodId)
        val genre = Genres.byId(seed.genreId)

        when {
            seed.artistId != null -> {
                pools += async {
                    safely(emptyList<Track>()) {
                        val details = catalog.artistDetails(seed.artistId)
                        val similar = details.related.take(3).flatMap { r ->
                            safely(emptyList<Track>()) { catalog.artistDetails(r.id).topTracks.take(8) }
                        }
                        interleave(listOf(details.topTracks, similar))
                    }
                }
            }
            mood != null -> {
                mood.genres.shuffled().take(2).mapNotNull { Genres.byId(it) }.forEach { g ->
                    pools += async { safely(emptyList<Track>()) { catalog.trendingTracks(g, 40) } }
                }
                mood.keywords.shuffled().take(1).forEach { kw ->
                    pools += async { safely(emptyList<Track>()) { catalog.search(kw, 15).tracks } }
                }
            }
            genre != null -> {
                pools += async { safely(emptyList<Track>()) { catalog.trendingTracks(genre, 50) } }
                pools += async { catalog.store.tracksByGenre(genre.audius ?: genre.id, 20) }
            }
            else -> {
                personalGenres(3).forEach { g ->
                    pools += async { safely(emptyList<Track>()) { catalog.trendingTracks(g, 40) } }
                }
                pools += async { library.likedTracksOnce().shuffled().take(6) }
                pools += async { library.followedArtistsOnce().shuffled().take(2).flatMap { a ->
                    safely(emptyList<Track>()) { catalog.artistDetails(a.id).topTracks.take(6) }
                } }
            }
        }
        pools += async { catalog.store.randomTracks(8) }

        val merged = pools.flatMap { it.await() }
            .distinctBy { it.id }
            .filter { it.id !in exclude && it.id !in disliked }
        merged.shuffled().spreadByKey { it.artistId ?: it.artistName }.take(size)
    }
}
