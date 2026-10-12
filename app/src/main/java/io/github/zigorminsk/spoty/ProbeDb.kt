package io.github.zigorminsk.spoty

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "probe")
data class ProbeEntity(@PrimaryKey val id: String, val title: String)

@Dao
interface ProbeDao {
    @Query("SELECT * FROM probe")
    suspend fun all(): List<ProbeEntity>
}

@Database(entities = [ProbeEntity::class], version = 1, exportSchema = false)
abstract class ProbeDb : RoomDatabase() {
    abstract fun dao(): ProbeDao
}
