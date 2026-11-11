package com.neonplayer

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class Backup(private val ctx: Context, private val db: AppDb, private val prefs: AppPrefs) {

    suspend fun export(out: Uri) = withContext(Dispatchers.IO) {
        val dao = db.dao()
        val images = linkedSetOf<String>()
        fun img(p: String?): Any {
            if (p == null) return JSONObject.NULL
            val f = File(p)
            if (!f.exists()) return JSONObject.NULL
            images.add(f.absolutePath)
            return f.name
        }
        fun nul(v: Long?): Any = v ?: JSONObject.NULL

        val root = JSONObject()
        root.put("version", 1)
        root.put("artists", JSONArray().also { a ->
            dao.artists().first().forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("photo", img(it.photo))) }
        })
        root.put("albums", JSONArray().also { a ->
            dao.albums().first().forEach {
                a.put(JSONObject().put("id", it.id).put("name", it.name).put("artistId", nul(it.artistId)).put("cover", img(it.cover)))
            }
        })
        root.put("tracks", JSONArray().also { a ->
            dao.tracks().first().forEach {
                a.put(
                    JSONObject().put("id", it.id).put("uri", it.uri).put("title", it.title).put("cover", img(it.cover))
                        .put("albumId", nul(it.albumId)).put("durationMs", it.durationMs)
                        .put("favorite", it.favorite).put("addedAt", it.addedAt)
                )
            }
        })
        root.put("trackArtists", JSONArray().also { a ->
            dao.trackArtists().first().forEach { a.put(JSONArray().put(it.trackId).put(it.artistId)) }
        })
        root.put("playlists", JSONArray().also { a ->
            dao.playlists().first().forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("cover", img(it.cover))) }
        })
        root.put("playlistTracks", JSONArray().also { a ->
            dao.playlistTracks().first().forEach { a.put(JSONArray().put(it.playlistId).put(it.trackId).put(it.position)) }
        })
        root.put("stats", JSONArray().also { a ->
            dao.stats().first().forEach { a.put(JSONArray().put(it.trackId).put(it.listenMs).put(it.plays).put(it.lastPlayed)) }
        })
        root.put("days", JSONArray().also { a ->
            dao.allDays().first().forEach { a.put(JSONArray().put(it.day).put(it.trackId).put(it.listenMs).put(it.plays)) }
        })
        root.put("customThemes", prefs.customThemesJson)
        root.put("profile", JSONObject().put("name", prefs.name.value).put("bio", prefs.bio.value).put("photo", img(prefs.photo.value)))

        ctx.contentResolver.openOutputStream(out)?.use { os ->
            ZipOutputStream(os.buffered()).use { z ->
                z.putNextEntry(ZipEntry("library.json"))
                z.write(root.toString().toByteArray())
                z.closeEntry()
                for (p in images) {
                    z.putNextEntry(ZipEntry("images/" + File(p).name))
                    File(p).inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        } ?: throw IllegalStateException("Impossible d'écrire le fichier")
    }

    suspend fun import(src: Uri): Boolean = withContext(Dispatchers.IO) {
        val dir = Media.imagesDir(ctx)
        var json: JSONObject? = null
        ctx.contentResolver.openInputStream(src)?.use { ins ->
            ZipInputStream(ins.buffered()).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (e.name == "library.json") json = JSONObject(String(z.readBytes()))
                    else if (e.name.startsWith("images/") && !e.isDirectory) {
                        File(dir, File(e.name).name).outputStream().use { z.copyTo(it) }
                    }
                    z.closeEntry()
                    e = z.nextEntry
                }
            }
        }
        val j = json ?: return@withContext false

        fun path(o: JSONObject, k: String): String? = if (o.isNull(k)) null else File(dir, o.getString(k)).absolutePath
        fun lng(o: JSONObject, k: String): Long? = if (o.isNull(k)) null else o.getLong(k)

        val artists = j.getJSONArray("artists").let { a -> List(a.length()) { a.getJSONObject(it) } }
            .map { Artist(it.getLong("id"), it.getString("name"), path(it, "photo")) }
        val albums = j.getJSONArray("albums").let { a -> List(a.length()) { a.getJSONObject(it) } }
            .map { Album(it.getLong("id"), it.getString("name"), lng(it, "artistId"), path(it, "cover")) }
        val tracks = j.getJSONArray("tracks").let { a -> List(a.length()) { a.getJSONObject(it) } }
            .map {
                Track(
                    it.getLong("id"), it.getString("uri"), it.getString("title"), path(it, "cover"),
                    lng(it, "albumId"), it.getLong("durationMs"), it.getBoolean("favorite"), it.getLong("addedAt")
                )
            }
        val ta = j.getJSONArray("trackArtists").let { a -> List(a.length()) { a.getJSONArray(it) } }
            .map { TrackArtist(it.getLong(0), it.getLong(1)) }
        val playlists = j.getJSONArray("playlists").let { a -> List(a.length()) { a.getJSONObject(it) } }
            .map { Playlist(it.getLong("id"), it.getString("name"), path(it, "cover")) }
        val pt = j.getJSONArray("playlistTracks").let { a -> List(a.length()) { a.getJSONArray(it) } }
            .map { PlaylistTrack(it.getLong(0), it.getLong(1), it.getInt(2)) }

        val stats = j.optJSONArray("stats")?.let { a -> List(a.length()) { a.getJSONArray(it) } }
            ?.map { Stat(it.getLong(0), it.getLong(1), it.getInt(2), it.getLong(3)) } ?: emptyList()

        val days = j.optJSONArray("days")?.let { a -> List(a.length()) { a.getJSONArray(it) } }
            ?.map { DayStat(it.getLong(0), it.getLong(1), it.getLong(2), it.getInt(3)) } ?: emptyList()

        val dao = db.dao()
        db.withTransaction {
            dao.deleteAllTracks(); dao.deleteAllAlbums(); dao.deleteAllArtists(); dao.deleteAllPlaylists(); dao.clearStats(); dao.clearDays()
            dao.putArtists(artists); dao.putAlbums(albums); dao.putPlaylists(playlists); dao.putTracks(tracks)
            dao.insertTrackArtists(ta); dao.insertPlaylistTracks(pt); dao.putStats(stats); dao.putDays(days)
        }
        j.optString("customThemes", "").takeIf { it.isNotEmpty() }?.let {
            prefs.customThemesJson = it
            ThemeState.customs.clear()
            ThemeState.customs.addAll(customThemesFromJson(it))
        }
        j.optJSONObject("profile")?.let {
            prefs.setName(it.optString("name", "Utilisateur"))
            prefs.setBio(it.optString("bio", ""))
            prefs.setPhoto(path(it, "photo"))
        }
        true
    }
}
