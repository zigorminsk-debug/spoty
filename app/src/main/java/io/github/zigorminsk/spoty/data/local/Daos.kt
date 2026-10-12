package io.github.zigorminsk.spoty.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Upsert suspend fun upsertTracks(items: List<TrackEntity>)
    @Upsert suspend fun upsertAlbums(items: List<AlbumEntity>)
    @Upsert suspend fun upsertArtists(items: List<ArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAlbumsIfAbsent(items: List<AlbumEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertArtistsIfAbsent(items: List<ArtistEntity>)

    @Query("SELECT id, discoveredAt FROM tracks WHERE id IN (:ids)")
    suspend fun trackDiscovery(ids: List<String>): List<IdTime>

    @Query("SELECT id, discoveredAt FROM albums WHERE id IN (:ids)")
    suspend fun albumDiscovery(ids: List<String>): List<IdTime>

    @Query("SELECT id, discoveredAt FROM artists WHERE id IN (:ids)")
    suspend fun artistDiscovery(ids: List<String>): List<IdTime>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun track(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun tracksByIds(ids: List<String>): List<TrackEntity>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun album(id: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE id IN (:ids)")
    suspend fun albumsByIds(ids: List<String>): List<AlbumEntity>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun artist(id: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE id IN (:ids)")
    suspend fun artistsByIds(ids: List<String>): List<ArtistEntity>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY trackNumber ASC, title ASC")
    suspend fun tracksOfAlbum(albumId: String): List<TrackEntity>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC, title ASC")
    suspend fun albumsOfArtist(artistId: String): List<AlbumEntity>

    @Query("SELECT * FROM tracks WHERE artistId = :artistId ORDER BY playCount DESC LIMIT :limit")
    suspend fun tracksOfArtist(artistId: String, limit: Int): List<TrackEntity>

    @Query(
        """
        SELECT * FROM artists
        WHERE (:source = '' OR source = :source) AND (:q = '' OR searchKey LIKE '%' || :q || '%')
        ORDER BY
          CASE WHEN :sort = 0 THEN discoveredAt END DESC,
          CASE WHEN :sort = 1 THEN searchKey END ASC,
          CASE WHEN :sort = 2 THEN followers END DESC
        LIMIT :limit
        """
    )
    fun observeArtists(source: String, q: String, sort: Int, limit: Int): Flow<List<ArtistEntity>>

    @Query(
        """
        SELECT * FROM albums
        WHERE (:source = '' OR source = :source) AND (:q = '' OR searchKey LIKE '%' || :q || '%')
        ORDER BY
          CASE WHEN :sort = 0 THEN discoveredAt END DESC,
          CASE WHEN :sort = 1 THEN searchKey END ASC,
          CASE WHEN :sort = 2 THEN COALESCE(year, 0) END DESC
        LIMIT :limit
        """
    )
    fun observeAlbums(source: String, q: String, sort: Int, limit: Int): Flow<List<AlbumEntity>>

    @Query(
        """
        SELECT * FROM tracks
        WHERE (:source = '' OR source = :source) AND (:q = '' OR searchKey LIKE '%' || :q || '%')
        ORDER BY
          CASE WHEN :sort = 0 THEN discoveredAt END DESC,
          CASE WHEN :sort = 1 THEN searchKey END ASC,
          CASE WHEN :sort = 2 THEN playCount END DESC
        LIMIT :limit
        """
    )
    fun observeTracks(source: String, q: String, sort: Int, limit: Int): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM artists") fun countArtists(): Flow<Int>
    @Query("SELECT COUNT(*) FROM albums") fun countAlbums(): Flow<Int>
    @Query("SELECT COUNT(*) FROM tracks") fun countTracks(): Flow<Int>

    @Query("SELECT * FROM artists ORDER BY discoveredAt DESC LIMIT :limit")
    suspend fun latestArtists(limit: Int): List<ArtistEntity>

    @Query("SELECT * FROM albums ORDER BY discoveredAt DESC LIMIT :limit")
    suspend fun latestAlbums(limit: Int): List<AlbumEntity>

    @Query("SELECT * FROM tracks WHERE genre = :genre COLLATE NOCASE ORDER BY playCount DESC LIMIT :limit")
    suspend fun tracksByGenre(genre: String, limit: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY RANDOM() LIMIT :limit")
    suspend fun randomTracks(limit: Int): List<TrackEntity>

    @Query("DELETE FROM tracks WHERE id NOT IN (SELECT trackId FROM liked_tracks) AND id NOT IN (SELECT trackId FROM playlist_items) AND id NOT IN (SELECT trackId FROM downloads) AND id NOT IN (SELECT trackId FROM play_history) AND id NOT IN (SELECT trackId FROM queue_items)")
    suspend fun deleteUnreferencedTracks(): Int
}

@Dao
interface LibraryDao {
    // Лайки
    @Query("SELECT trackId FROM liked_tracks") fun observeLikedIds(): Flow<List<String>>

    @Query("SELECT t.* FROM tracks t INNER JOIN liked_tracks l ON l.trackId = t.id ORDER BY l.likedAt DESC")
    fun observeLikedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT t.* FROM tracks t INNER JOIN liked_tracks l ON l.trackId = t.id ORDER BY l.likedAt DESC")
    suspend fun likedTracks(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun like(item: LikedTrackEntity)
    @Query("DELETE FROM liked_tracks WHERE trackId = :trackId") suspend fun unlike(trackId: String)

    // Дизлайки
    @Query("SELECT trackId FROM disliked_tracks") fun observeDislikedIds(): Flow<List<String>>
    @Query("SELECT trackId FROM disliked_tracks") suspend fun dislikedIds(): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun dislike(item: DislikedTrackEntity)
    @Query("DELETE FROM disliked_tracks WHERE trackId = :trackId") suspend fun undislike(trackId: String)

    // Подписки на исполнителей
    @Query("SELECT artistId FROM followed_artists") fun observeFollowedIds(): Flow<List<String>>

    @Query("SELECT a.* FROM artists a INNER JOIN followed_artists f ON f.artistId = a.id ORDER BY f.followedAt DESC")
    fun observeFollowedArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT a.* FROM artists a INNER JOIN followed_artists f ON f.artistId = a.id ORDER BY f.followedAt DESC")
    suspend fun followedArtists(): List<ArtistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun follow(item: FollowedArtistEntity)
    @Query("DELETE FROM followed_artists WHERE artistId = :artistId") suspend fun unfollow(artistId: String)

    // Сохранённые альбомы
    @Query("SELECT albumId FROM saved_albums") fun observeSavedAlbumIds(): Flow<List<String>>

    @Query("SELECT a.* FROM albums a INNER JOIN saved_albums s ON s.albumId = a.id ORDER BY s.savedAt DESC")
    fun observeSavedAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT a.* FROM albums a INNER JOIN saved_albums s ON s.albumId = a.id ORDER BY s.savedAt DESC")
    suspend fun savedAlbums(): List<AlbumEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAlbum(item: SavedAlbumEntity)
    @Query("DELETE FROM saved_albums WHERE albumId = :albumId") suspend fun unsaveAlbum(albumId: String)

    // Плейлисты
    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS trackCount " +
            "FROM playlists p ORDER BY p.updatedAt DESC"
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS trackCount " +
            "FROM playlists p ORDER BY p.updatedAt DESC"
    )
    suspend fun playlists(): List<PlaylistWithCount>

    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM playlist_items i WHERE i.playlistId = p.id) AS trackCount " +
            "FROM playlists p WHERE p.id = :id"
    )
    fun observePlaylist(id: Long): Flow<PlaylistWithCount?>

    @Query("SELECT * FROM playlists WHERE id = :id") suspend fun playlist(id: Long): PlaylistEntity?

    @Insert suspend fun insertPlaylist(item: PlaylistEntity): Long
    @Update suspend fun updatePlaylist(item: PlaylistEntity)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId") suspend fun deletePlaylistItems(playlistId: Long)

    @Query(
        "SELECT t.*, i.id AS itemId, i.position AS position FROM playlist_items i " +
            "INNER JOIN tracks t ON t.id = i.trackId WHERE i.playlistId = :playlistId ORDER BY i.position ASC"
    )
    fun observePlaylistTracks(playlistId: Long): Flow<List<PlaylistTrackRow>>

    @Query(
        "SELECT t.*, i.id AS itemId, i.position AS position FROM playlist_items i " +
            "INNER JOIN tracks t ON t.id = i.trackId WHERE i.playlistId = :playlistId ORDER BY i.position ASC"
    )
    suspend fun playlistTracks(playlistId: Long): List<PlaylistTrackRow>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)
    @Query("DELETE FROM playlist_items WHERE id = :itemId") suspend fun deletePlaylistItem(itemId: Long)
    @Query("UPDATE playlist_items SET position = :position WHERE id = :itemId") suspend fun setItemPosition(itemId: Long, position: Int)
    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id") suspend fun touchPlaylist(id: Long, now: Long)

    @Query(
        "SELECT t.artworkUrl FROM playlist_items i INNER JOIN tracks t ON t.id = i.trackId " +
            "WHERE i.playlistId = :playlistId AND t.artworkUrl IS NOT NULL ORDER BY i.position ASC LIMIT :limit"
    )
    suspend fun playlistCovers(playlistId: Long, limit: Int): List<String>

    // История
    @Insert suspend fun insertPlay(item: PlayHistoryEntity)

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN (SELECT trackId, MAX(playedAt) AS lastAt FROM play_history GROUP BY trackId) h " +
            "ON h.trackId = t.id ORDER BY h.lastAt DESC LIMIT :limit"
    )
    fun observeRecentTracks(limit: Int): Flow<List<TrackEntity>>

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN (SELECT trackId, MAX(playedAt) AS lastAt FROM play_history GROUP BY trackId) h " +
            "ON h.trackId = t.id ORDER BY h.lastAt DESC LIMIT :limit"
    )
    suspend fun recentTracks(limit: Int): List<TrackEntity>

    @Query("SELECT trackId FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    suspend fun recentPlayedIds(limit: Int): List<String>

    @Query("DELETE FROM play_history") suspend fun clearHistory()

    @Query(
        "SELECT t.genre AS genre, COUNT(*) AS cnt FROM play_history h INNER JOIN tracks t ON t.id = h.trackId " +
            "WHERE t.genre IS NOT NULL AND t.genre != '' GROUP BY t.genre ORDER BY cnt DESC LIMIT :limit"
    )
    suspend fun topGenresFromHistory(limit: Int): List<GenreCount>

    @Query(
        "SELECT t.genre AS genre, COUNT(*) AS cnt FROM liked_tracks l INNER JOIN tracks t ON t.id = l.trackId " +
            "WHERE t.genre IS NOT NULL AND t.genre != '' GROUP BY t.genre ORDER BY cnt DESC LIMIT :limit"
    )
    suspend fun topGenresFromLikes(limit: Int): List<GenreCount>

    @Query(
        "SELECT t.artistId AS id, COUNT(*) AS cnt FROM play_history h INNER JOIN tracks t ON t.id = h.trackId " +
            "WHERE t.artistId IS NOT NULL GROUP BY t.artistId ORDER BY cnt DESC LIMIT :limit"
    )
    suspend fun topArtistsFromHistory(limit: Int): List<IdCount>

    // Загрузки
    @Query("SELECT * FROM downloads") fun observeDownloads(): Flow<List<DownloadEntity>>
    @Query("SELECT * FROM downloads WHERE trackId = :trackId") suspend fun download(trackId: String): DownloadEntity?
    @Query("SELECT * FROM downloads WHERE status = 2") suspend fun finishedDownloads(): List<DownloadEntity>

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN downloads d ON d.trackId = t.id WHERE d.status = 2 ORDER BY d.createdAt DESC"
    )
    fun observeDownloadedTracks(): Flow<List<TrackEntity>>

    @Query(
        "SELECT t.* FROM tracks t INNER JOIN downloads d ON d.trackId = t.id WHERE d.status = 2 ORDER BY d.createdAt DESC"
    )
    suspend fun downloadedTracks(): List<TrackEntity>

    @Upsert suspend fun upsertDownload(item: DownloadEntity)
    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE trackId = :trackId")
    suspend fun setDownloadProgress(trackId: String, status: Int, progress: Int)
    @Query("DELETE FROM downloads WHERE trackId = :trackId") suspend fun deleteDownload(trackId: String)
    @Query("DELETE FROM downloads WHERE status != 2") suspend fun deleteUnfinishedDownloads()

    // История поиска
    @Query("SELECT `query` FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeSearchHistory(limit: Int): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSearch(item: SearchHistoryEntity)
    @Query("DELETE FROM search_history WHERE `query` = :query") suspend fun deleteSearch(query: String)
    @Query("DELETE FROM search_history") suspend fun clearSearchHistory()

    // Очередь воспроизведения (для возобновления после перезапуска)
    @Query("SELECT t.* FROM queue_items q INNER JOIN tracks t ON t.id = q.trackId ORDER BY q.position ASC")
    suspend fun loadQueue(): List<TrackEntity>

    @Query("DELETE FROM queue_items") suspend fun clearQueue()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertQueue(items: List<QueueItemEntity>)
}
