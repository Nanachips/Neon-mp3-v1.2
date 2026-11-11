package com.neonplayer

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class TrackUi(val track: Track, val artists: List<Artist>, val album: Album?) {
    val id: Long get() = track.id
    val title: String get() = track.title
    val artistText: String get() = artists.joinToString(", ") { it.name }
    val cover: String? get() = track.cover ?: album?.cover
}

fun TrackUi.matches(q: String): Boolean {
    if (q.isBlank()) return true
    val s = q.trim()
    return title.contains(s, true) || artists.any { it.name.contains(s, true) } || (album?.name?.contains(s, true) == true)
}

sealed interface Route {
    data class ArtistR(val id: Long) : Route
    data class AlbumR(val id: Long) : Route
    data class PlaylistR(val id: Long) : Route
}

class LibraryVM(app: Application) : AndroidViewModel(app) {
    private val a = app as App
    private val dao = a.db.dao()
    val prefs = a.prefs
    val hub = a.hub
    private val importer = Importer(app, dao, prefs)
    private val backup = Backup(app, a.db, prefs)

    private fun <T> Flow<T>.state(init: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val artists = dao.artists().state(emptyList())
    val albums = dao.albums().state(emptyList())
    val playlists = dao.playlists().state(emptyList())
    val playlistRefs = dao.playlistTracks().state(emptyList())
    val stats = dao.stats().state(emptyList())
    val days = dao.allDays().state(emptyList())
    val exporting = MutableStateFlow<Float?>(null)

    val tracks: StateFlow<List<TrackUi>> = combine(dao.tracks(), dao.trackArtists(), artists, albums) { t, ta, ar, al ->
        val am = ar.associateBy { it.id }
        val alm = al.associateBy { it.id }
        val by = ta.groupBy { it.trackId }
        t.map { tr -> TrackUi(tr, by[tr.id].orEmpty().mapNotNull { am[it.artistId] }, tr.albumId?.let { alm[it] }) }
    }.state(emptyList())

    val message = MutableStateFlow<String?>(null)

    // Navigation
    var tab by mutableIntStateOf(0)
    val stack = mutableStateListOf<Route>()
    var showPlayer by mutableStateOf(false)
    fun open(r: Route) { stack.add(r) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }

    init {
        viewModelScope.launch { tracks.collect { hub.syncMetadata(it) } }
    }

    // Import
    fun importFiles(uris: List<Uri>) = viewModelScope.launch {
        if (uris.isEmpty()) return@launch
        message.value = "Import en cours…"
        val n = importer.importFiles(uris)
        message.value = "$n titre(s) ajouté(s)"
    }

    fun importTree(uri: Uri) = viewModelScope.launch {
        message.value = "Analyse du dossier…"
        val n = importer.importTree(uri)
        message.value = "$n titre(s) ajouté(s)"
    }

    // Titres
    fun saveTrack(t: Track, artistIds: List<Long>) = viewModelScope.launch {
        a.db.withTransaction {
            dao.updateTrack(t)
            dao.clearTrackArtists(t.id)
            dao.insertTrackArtists(artistIds.map { TrackArtist(t.id, it) })
        }
    }

    fun deleteTracks(ids: List<Long>) = viewModelScope.launch {
        val files = tracks.value.filter { it.id in ids && it.track.uri.startsWith("file:") }
            .mapNotNull { Uri.parse(it.track.uri).path }
        dao.deleteTracks(ids)
        hub.removeTracks(ids.toSet())
        withContext(Dispatchers.IO) { files.forEach { runCatching { File(it).delete() } } }
    }

    fun setFavorites(ids: List<Long>, fav: Boolean) = viewModelScope.launch { dao.setFavorites(ids, fav) }

    fun refreshFolders() = viewModelScope.launch {
        val trees = prefs.trees()
        if (trees.isEmpty()) {
            message.value = "Aucun dossier mémorisé. Importez d'abord un dossier entier."
            return@launch
        }
        message.value = "Actualisation des dossiers…"
        var n = 0
        for (t in trees) n += runCatching { importer.scanTree(Uri.parse(t)) }.getOrDefault(0)
        message.value = if (n > 0) "$n nouveau(x) titre(s) ajouté(s)" else "Aucun nouveau titre"
    }

    /** Exporte une version M4A du titre, vitesse et hauteur multipliées par `factor`. */
    fun exportModified(t: TrackUi, factor: Float, dest: Uri?) = viewModelScope.launch {
        if (exporting.value != null) return@launch
        exporting.value = 0f
        val ctx = getApplication<Application>()
        val ok = withContext(Dispatchers.IO) {
            try {
                val dir = File(ctx.filesDir, "exports").apply { mkdirs() }
                val out = if (dest == null) File(dir, UUID.randomUUID().toString() + ".m4a") else File(ctx.cacheDir, "export_out.m4a")
                val good = AudioExport.render(ctx, Uri.parse(t.track.uri), factor, out) { exporting.value = it }
                if (good && dest != null) {
                    ctx.contentResolver.openOutputStream(dest)?.use { os -> out.inputStream().use { it.copyTo(os) } }
                    out.delete()
                } else if (good) {
                    val label = String.format(java.util.Locale.FRANCE, "%.2f", factor)
                    val id = dao.insertTrack(
                        Track(
                            uri = Uri.fromFile(out).toString(), title = "${t.title} (×$label)", cover = t.track.cover,
                            albumId = t.track.albumId, durationMs = (t.track.durationMs / factor).toLong()
                        )
                    )
                    if (id > 0) dao.insertTrackArtists(t.artists.map { TrackArtist(id, it.id) })
                } else out.delete()
                good
            } catch (e: Exception) { false }
        }
        exporting.value = null
        message.value = if (!ok) "Échec de l'export" else if (dest != null) "Fichier enregistré" else "Ajouté à la bibliothèque"
    }

    fun toggleFavorite(t: TrackUi) = viewModelScope.launch { dao.setFavorite(t.id, !t.track.favorite) }

    // Artistes
    fun createArtist(name: String, photo: String?, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        onDone(dao.insertArtist(Artist(name = name, photo = photo)))
    }
    fun updateArtist(x: Artist) = viewModelScope.launch { dao.updateArtist(x) }
    fun deleteArtist(id: Long) = viewModelScope.launch { dao.deleteArtist(id) }
    fun addTracksToArtist(artistId: Long, ids: List<Long>) = viewModelScope.launch {
        dao.insertTrackArtists(ids.map { TrackArtist(it, artistId) })
    }
    fun removeArtistFrom(trackId: Long, artistId: Long) = viewModelScope.launch { dao.removeArtistFrom(trackId, artistId) }

    // Albums
    fun createAlbum(name: String, artistId: Long?, cover: String?, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        onDone(dao.insertAlbum(Album(name = name, artistId = artistId, cover = cover)))
    }
    fun updateAlbum(x: Album) = viewModelScope.launch { dao.updateAlbum(x) }
    fun deleteAlbum(id: Long) = viewModelScope.launch { dao.deleteAlbum(id) }
    fun setAlbum(ids: List<Long>, albumId: Long?) = viewModelScope.launch { dao.setAlbum(ids, albumId) }

    // Playlists
    fun createPlaylist(name: String, cover: String?, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        onDone(dao.insertPlaylist(Playlist(name = name, cover = cover)))
    }
    fun updatePlaylist(x: Playlist) = viewModelScope.launch { dao.updatePlaylist(x) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { dao.deletePlaylist(id) }
    fun addToPlaylist(pid: Long, ids: List<Long>) = viewModelScope.launch {
        var pos = dao.maxPos(pid) + 1
        dao.insertPlaylistTracks(ids.map { PlaylistTrack(pid, it, pos++) })
        message.value = "Ajouté à la playlist"
    }
    fun removeFromPlaylist(pid: Long, tid: Long) = viewModelScope.launch { dao.removeFromPlaylist(pid, tid) }

    fun resetStats() = viewModelScope.launch { dao.clearStats(); dao.clearDays() }

    // Sauvegarde
    fun exportBackup(uri: Uri) = viewModelScope.launch {
        runCatching { backup.export(uri) }
            .onSuccess { message.value = "Sauvegarde exportée" }
            .onFailure { message.value = "Échec de l'export" }
    }
    fun importBackup(uri: Uri) = viewModelScope.launch {
        val ok = runCatching { backup.import(uri) }.getOrDefault(false)
        message.value = if (ok) "Sauvegarde restaurée" else "Fichier de sauvegarde invalide"
    }
}
