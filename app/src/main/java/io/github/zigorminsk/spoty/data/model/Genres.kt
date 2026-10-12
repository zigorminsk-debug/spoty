package io.github.zigorminsk.spoty.data.model

import androidx.annotation.StringRes
import io.github.zigorminsk.spoty.R

/**
 * Жанр с описанием того, как искать его в каждом источнике.
 * [audius] — точное значение поля genre в Audius; [jamendo] — тег Jamendo;
 * [archive] — ключевое слово для поиска по Internet Archive.
 */
data class Genre(
    val id: String,
    @StringRes val titleRes: Int,
    val colorA: Long,
    val colorB: Long,
    val audius: String?,
    val jamendo: String?,
    val archive: String,
)

object Genres {
    val all: List<Genre> = listOf(
        Genre("electronic", R.string.genre_electronic, 0xFF5B2BE0, 0xFF1FA2FF, "Electronic", "electronic", "electronic"),
        Genre("pop", R.string.genre_pop, 0xFFFF5F6D, 0xFFFFC371, "Pop", "pop", "pop"),
        Genre("rock", R.string.genre_rock, 0xFFD31027, 0xFFEA384D, "Rock", "rock", "rock"),
        Genre("hiphop", R.string.genre_hiphop, 0xFFF7971E, 0xFFFFD200, "Hip-Hop/Rap", "hiphop", "hip hop"),
        Genre("lofi", R.string.genre_lofi, 0xFF7F7FD5, 0xFF86A8E7, "Lo-Fi", "lofi", "lofi"),
        Genre("ambient", R.string.genre_ambient, 0xFF11998E, 0xFF38EF7D, "Ambient", "ambient", "ambient"),
        Genre("jazz", R.string.genre_jazz, 0xFF614385, 0xFF516395, "Jazz", "jazz", "jazz"),
        Genre("classical", R.string.genre_classical, 0xFF8E9EAB, 0xFFEEF2F3, "Classical", "classical", "classical"),
        Genre("metal", R.string.genre_metal, 0xFF232526, 0xFF414345, "Metal", "metal", "metal"),
        Genre("folk", R.string.genre_folk, 0xFF9D50BB, 0xFF6E48AA, "Folk", "folk", "folk"),
        Genre("indie", R.string.genre_indie, 0xFF00B4DB, 0xFF0083B0, "Alternative", "indie", "indie"),
        Genre("house", R.string.genre_house, 0xFFFC466B, 0xFF3F5EFB, "House", "house", "house"),
        Genre("techno", R.string.genre_techno, 0xFF0F2027, 0xFF2C5364, "Techno", "techno", "techno"),
        Genre("dnb", R.string.genre_dnb, 0xFFFF416C, 0xFFFF4B2B, "Drum & Bass", "drumnbass", "drum and bass"),
        Genre("rnb", R.string.genre_rnb, 0xFFDA22FF, 0xFF9733EE, "R&B/Soul", "rnb", "soul"),
        Genre("reggae", R.string.genre_reggae, 0xFF56AB2F, 0xFFA8E063, "Reggae", "reggae", "reggae"),
        Genre("world", R.string.genre_world, 0xFFFF9966, 0xFFFF5E62, "World", "world", "world music"),
        Genre("soundtrack", R.string.genre_soundtrack, 0xFF334D50, 0xFFCBCAA5, "Soundtrack", "soundtrack", "soundtrack"),
        Genre("acoustic", R.string.genre_acoustic, 0xFFC79081, 0xFFDFA579, "Acoustic", "acoustic", "acoustic"),
        Genre("blues", R.string.genre_blues, 0xFF2193B0, 0xFF6DD5ED, "Blues", "blues", "blues"),
        Genre("funk", R.string.genre_funk, 0xFFF953C6, 0xFFB91D73, "Funk", "funk", "funk"),
        Genre("punk", R.string.genre_punk, 0xFFE52D27, 0xFFB31217, "Punk", "punk", "punk"),
        Genre("country", R.string.genre_country, 0xFFB79891, 0xFF94716B, "Country", "country", "country"),
        Genre("experimental", R.string.genre_experimental, 0xFF4776E6, 0xFF8E54E9, "Experimental", "experimental", "experimental"),
    )

    fun byId(id: String?): Genre? = all.firstOrNull { it.id == id }

    /** Подбирает жанр по произвольной строке (поле genre из источника). */
    fun match(raw: String?): Genre? {
        val s = raw?.lowercase()?.trim().orEmpty()
        if (s.isEmpty()) return null
        return all.firstOrNull { g ->
            g.audius?.lowercase() == s || g.jamendo == s || g.id == s || g.archive == s
        } ?: all.firstOrNull { g -> s.contains(g.id) || (g.audius != null && s.contains(g.audius.lowercase())) }
    }
}

/** Настроение/занятие для «Моей волны». */
data class WaveMood(
    val id: String,
    val emoji: String,
    @StringRes val titleRes: Int,
    val genres: List<String>,
    val keywords: List<String>,
    val jamendoTags: List<String>,
)

object WaveMoods {
    val all: List<WaveMood> = listOf(
        WaveMood("energetic", "⚡", R.string.mood_energetic, listOf("electronic", "hiphop", "rock"), listOf("energetic", "upbeat", "dance"), listOf("energetic", "dance")),
        WaveMood("happy", "😊", R.string.mood_happy, listOf("pop", "funk", "reggae"), listOf("happy", "summer", "fun"), listOf("happy", "summer")),
        WaveMood("calm", "🌿", R.string.mood_calm, listOf("ambient", "acoustic", "lofi"), listOf("calm", "relax", "chill"), listOf("relaxing", "chillout")),
        WaveMood("sad", "🌧", R.string.mood_sad, listOf("indie", "acoustic", "soundtrack"), listOf("sad", "melancholy", "emotional"), listOf("sad", "melancholic")),
        WaveMood("focus", "🎯", R.string.mood_focus, listOf("ambient", "lofi", "classical"), listOf("focus", "study", "instrumental"), listOf("instrumental", "ambient")),
        WaveMood("workout", "💪", R.string.mood_workout, listOf("electronic", "hiphop", "metal"), listOf("workout", "gym", "power"), listOf("powerful", "sport")),
        WaveMood("sleep", "🌙", R.string.mood_sleep, listOf("ambient", "classical"), listOf("sleep", "meditation", "ambient"), listOf("meditation", "sleep")),
        WaveMood("party", "🎉", R.string.mood_party, listOf("house", "pop", "hiphop"), listOf("party", "club", "remix"), listOf("party", "club")),
    )

    fun byId(id: String?): WaveMood? = all.firstOrNull { it.id == id }
}
