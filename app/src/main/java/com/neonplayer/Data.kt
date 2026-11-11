package com.neonplayer

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "artists")
data class Artist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val photo: String? = null
)

@Entity(
    tableName = "albums",
    foreignKeys = [ForeignKey(entity = Artist::class, parentColumns = ["id"], childColumns = ["artistId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("artistId")]
)
data class Album(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val artistId: Long? = null,
    val cover: String? = null
)

@Entity(
    tableName = "tracks",
    foreignKeys = [ForeignKey(entity = Album::class, parentColumns = ["id"], childColumns = ["albumId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index(value = ["uri"], unique = true), Index("albumId")]
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val cover: String? = null,
    val albumId: Long? = null,
    val durationMs: Long = 0,
    val favorite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "track_artists",
    primaryKeys = ["trackId", "artistId"],
    foreignKeys = [
        ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Artist::class, parentColumns = ["id"], childColumns = ["artistId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("artistId")]
)
data class TrackArtist(val trackId: Long, val artistId: Long)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cover: String? = null
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("trackId")]
)
data class PlaylistTrack(val playlistId: Long, val trackId: Long, val position: Int)

@Entity(tableName = "stats")
data class Stat(
    @PrimaryKey val trackId: Long,
    val listenMs: Long,
    val plays: Int,
    val lastPlayed: Long
)

@Entity(tableName = "stats_daily", primaryKeys = ["day", "trackId"])
data class DayStat(val day: Long, val trackId: Long, val listenMs: Long, val plays: Int)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM stats_daily") fun allDays(): Flow<List<DayStat>>
    @Query("INSERT OR IGNORE INTO stats_daily(day, trackId, listenMs, plays) VALUES(:day, :id, 0, 0)") suspend fun ensureDay(day: Long, id: Long)
    @Query("UPDATE stats_daily SET listenMs = listenMs + :ms, plays = plays + :plays WHERE day = :day AND trackId = :id") suspend fun addDay(day: Long, id: Long, ms: Long, plays: Int)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDays(l: List<DayStat>)
    @Query("DELETE FROM stats_daily") suspend fun clearDays()
    @Query("UPDATE tracks SET favorite = :f WHERE id IN (:ids)") suspend fun setFavorites(ids: List<Long>, f: Boolean)

    @Query("SELECT * FROM stats") fun stats(): Flow<List<Stat>>
    @Query("INSERT OR IGNORE INTO stats(trackId, listenMs, plays, lastPlayed) VALUES(:id, 0, 0, 0)") suspend fun ensureStat(id: Long)
    @Query("UPDATE stats SET listenMs = listenMs + :ms, plays = plays + :plays, lastPlayed = :now WHERE trackId = :id") suspend fun addStat(id: Long, ms: Long, plays: Int, now: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putStats(l: List<Stat>)
    @Query("DELETE FROM stats") suspend fun clearStats()

    @Query("SELECT * FROM tracks ORDER BY addedAt DESC, id DESC") fun tracks(): Flow<List<Track>>
    @Query("SELECT * FROM artists ORDER BY name COLLATE NOCASE") fun artists(): Flow<List<Artist>>
    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE") fun albums(): Flow<List<Album>>
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE") fun playlists(): Flow<List<Playlist>>
    @Query("SELECT * FROM track_artists") fun trackArtists(): Flow<List<TrackArtist>>
    @Query("SELECT * FROM playlist_tracks ORDER BY position") fun playlistTracks(): Flow<List<PlaylistTrack>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTrack(t: Track): Long
    @Insert suspend fun insertArtist(a: Artist): Long
    @Insert suspend fun insertAlbum(a: Album): Long
    @Insert suspend fun insertPlaylist(p: Playlist): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTrackArtists(l: List<TrackArtist>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPlaylistTracks(l: List<PlaylistTrack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putArtists(l: List<Artist>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putAlbums(l: List<Album>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putTracks(l: List<Track>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPlaylists(l: List<Playlist>)

    @Update suspend fun updateTrack(t: Track)
    @Update suspend fun updateArtist(a: Artist)
    @Update suspend fun updateAlbum(a: Album)
    @Update suspend fun updatePlaylist(p: Playlist)

    @Query("DELETE FROM tracks WHERE id IN (:ids)") suspend fun deleteTracks(ids: List<Long>)
    @Query("DELETE FROM artists WHERE id = :id") suspend fun deleteArtist(id: Long)
    @Query("DELETE FROM albums WHERE id = :id") suspend fun deleteAlbum(id: Long)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM tracks") suspend fun deleteAllTracks()
    @Query("DELETE FROM artists") suspend fun deleteAllArtists()
    @Query("DELETE FROM albums") suspend fun deleteAllAlbums()
    @Query("DELETE FROM playlists") suspend fun deleteAllPlaylists()

    @Query("DELETE FROM track_artists WHERE trackId = :id") suspend fun clearTrackArtists(id: Long)
    @Query("DELETE FROM track_artists WHERE trackId = :t AND artistId = :a") suspend fun removeArtistFrom(t: Long, a: Long)
    @Query("UPDATE tracks SET albumId = :albumId WHERE id IN (:ids)") suspend fun setAlbum(ids: List<Long>, albumId: Long?)
    @Query("UPDATE tracks SET favorite = :f WHERE id = :id") suspend fun setFavorite(id: Long, f: Boolean)
    @Query("SELECT COUNT(*) FROM tracks WHERE uri = :uri") suspend fun countUri(uri: String): Int
    @Query("SELECT * FROM artists WHERE name = :n COLLATE NOCASE LIMIT 1") suspend fun artistByName(n: String): Artist?
    @Query("DELETE FROM playlist_tracks WHERE playlistId = :p AND trackId = :t") suspend fun removeFromPlaylist(p: Long, t: Long)
    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :p") suspend fun maxPos(p: Long): Int
}

@Database(
    entities = [Artist::class, Album::class, Track::class, TrackArtist::class, Playlist::class, PlaylistTrack::class, Stat::class, DayStat::class],
    version = 3,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `stats` (`trackId` INTEGER NOT NULL, `listenMs` INTEGER NOT NULL, `plays` INTEGER NOT NULL, `lastPlayed` INTEGER NOT NULL, PRIMARY KEY(`trackId`))")
            }
        }

val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `stats_daily` (`day` INTEGER NOT NULL, `trackId` INTEGER NOT NULL, `listenMs` INTEGER NOT NULL, `plays` INTEGER NOT NULL, PRIMARY KEY(`day`, `trackId`))")
            }
        }

        fun build(ctx: Context): AppDb =
            Room.databaseBuilder(ctx, AppDb::class.java, "neon.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
