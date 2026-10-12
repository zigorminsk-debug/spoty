package io.github.zigorminsk.spoty.data.local

import io.github.zigorminsk.spoty.data.model.Album
import io.github.zigorminsk.spoty.data.model.AlbumKind
import io.github.zigorminsk.spoty.data.model.Artist
import io.github.zigorminsk.spoty.data.model.Track

private fun key(vararg parts: String?): String =
    parts.filterNotNull().joinToString(" ").lowercase().trim()

fun Track.toEntity(now: Long): TrackEntity = TrackEntity(
    id = id, source = source, title = title, searchKey = key(title, artistName, albumTitle),
    artistId = artistId, artistName = artistName, albumId = albumId, albumTitle = albumTitle,
    trackNumber = trackNumber, durationMs = durationMs, artworkUrl = artworkUrl, streamUrl = streamUrl,
    genre = genre, license = license, playCount = playCount, webUrl = webUrl,
    discoveredAt = now, updatedAt = now,
)

fun TrackEntity.toModel(): Track = Track(
    id = id, source = source, title = title, artistId = artistId, artistName = artistName,
    albumId = albumId, albumTitle = albumTitle, trackNumber = trackNumber, durationMs = durationMs,
    artworkUrl = artworkUrl, streamUrl = streamUrl, genre = genre, license = license,
    playCount = playCount, webUrl = webUrl,
)

fun Album.toEntity(now: Long): AlbumEntity = AlbumEntity(
    id = id, source = source, title = title, searchKey = key(title, artistName),
    artistId = artistId, artistName = artistName, artworkUrl = artworkUrl, year = year, genre = genre,
    trackCount = trackCount, durationMs = durationMs, kind = kind.name, description = description,
    playCount = playCount, license = license, webUrl = webUrl, discoveredAt = now, updatedAt = now,
)

fun AlbumEntity.toModel(): Album = Album(
    id = id, source = source, title = title, artistId = artistId, artistName = artistName,
    artworkUrl = artworkUrl, year = year, genre = genre, trackCount = trackCount, durationMs = durationMs,
    kind = runCatching { AlbumKind.valueOf(kind) }.getOrDefault(AlbumKind.ALBUM),
    description = description, playCount = playCount, license = license, webUrl = webUrl,
)

fun Artist.toEntity(now: Long): ArtistEntity = ArtistEntity(
    id = id, source = source, name = name, searchKey = key(name),
    imageUrl = imageUrl, coverUrl = coverUrl, bio = bio, genre = genre, followers = followers,
    trackCount = trackCount, albumCount = albumCount, verified = verified, location = location,
    webUrl = webUrl, discoveredAt = now, updatedAt = now,
)

fun ArtistEntity.toModel(): Artist = Artist(
    id = id, source = source, name = name, imageUrl = imageUrl, coverUrl = coverUrl, bio = bio,
    followers = followers, trackCount = trackCount, albumCount = albumCount, verified = verified,
    location = location, genre = genre, webUrl = webUrl,
)
