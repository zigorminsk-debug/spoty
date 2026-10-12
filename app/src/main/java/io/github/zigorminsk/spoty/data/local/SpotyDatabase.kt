package io.github.zigorminsk.spoty.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        LikedTrackEntity::class,
        DislikedTrackEntity::class,
        FollowedArtistEntity::class,
        SavedAlbumEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlayHistoryEntity::class,
        DownloadEntity::class,
        SearchHistoryEntity::class,
        QueueItemEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class SpotyDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun library(): LibraryDao

    companion object {
        fun create(context: Context): SpotyDatabase =
            Room.databaseBuilder(context.applicationContext, SpotyDatabase::class.java, "spoty.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
